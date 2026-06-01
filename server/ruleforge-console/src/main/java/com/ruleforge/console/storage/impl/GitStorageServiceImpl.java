package com.ruleforge.console.storage.impl;

import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.model.FileDiff;
import com.ruleforge.console.storage.model.GitOperationException;
import com.ruleforge.console.storage.model.MergeResult;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JGit-based implementation of GitStorageService.
 * One Git repository per project under {gitBase}/{projectName}/.
 * Thread-safe via per-project ReentrantLock.
 */
@Slf4j
@Service
public class GitStorageServiceImpl implements GitStorageService {

    private final GitConfig gitConfig;

    /** Per-project write locks to serialize Git operations on the same repo. */
    private final ConcurrentHashMap<String, Object> projectLocks = new ConcurrentHashMap<>();

    public GitStorageServiceImpl(GitConfig gitConfig) {
        this.gitConfig = gitConfig;
    }

    // ========== Repository lifecycle ==========

    @Override
    public void initRepo(String projectName) {
        Path repoPath = getRepoPath(projectName);
        Object lock = projectLocks.computeIfAbsent(projectName, k -> new Object());

        synchronized (lock) {
            try {
                if (Files.exists(repoPath.resolve(".git"))) {
                    log.debug("Repo already exists for project [{}]", projectName);
                    return;
                }
                Files.createDirectories(repoPath);
                try (Git git = Git.init().setDirectory(repoPath.toFile()).call()) {
                    // Create initial commit on main so the branch exists
                    String author = "ruleforge";
                    PersonIdent ident = new PersonIdent(author, "ruleforge@ruleforge.dev");
                    // Need at least one file to make a commit
                    Path readme = repoPath.resolve("README.md");
                    Files.writeString(readme, "# " + projectName + "\n");
                    git.add().addFilepattern("README.md").call();
                    git.commit().setMessage("Initialize repository for " + projectName)
                            .setAuthor(ident).setCommitter(ident).call();
                    // Ensure branch is named "main"
                    git.branchRename().setOldName("master").setNewName("main").call();
                }
                log.info("Initialized Git repo for project [{}] at {}", projectName, repoPath);
            } catch (Exception e) {
                throw new GitOperationException(projectName, "Failed to init repo", e);
            }
        }
    }

    @Override
    public boolean repoExists(String projectName) {
        return Files.exists(getRepoPath(projectName).resolve(".git"));
    }

    // ========== Branch operations ==========

    @Override
    public void createBranch(String projectName, String branchName, String parentBranch) {
        withLock(projectName, () -> {
            try (Git git = Git.open(getRepoPath(projectName).toFile())) {
                // Check if branch already exists
                boolean exists = git.branchList().call().stream()
                        .anyMatch(ref -> ref.getName().equals("refs/heads/" + branchName));
                if (exists) {
                    log.debug("Branch [{}] already exists for project [{}]", branchName, projectName);
                    return;
                }
                String parentRef = parentBranch != null ? parentBranch : "main";
                git.branchCreate().setName(branchName).setStartPoint(parentRef).call();
                log.info("Created branch [{}] from [{}] for project [{}]", branchName, parentRef, projectName);
            } catch (Exception e) {
                throw new GitOperationException(projectName,
                        "Failed to create branch " + branchName, e);
            }
        });
    }

    @Override
    public List<String> listBranches(String projectName) {
        try (Git git = Git.open(getRepoPath(projectName).toFile())) {
            return git.branchList().call().stream()
                    .map(ref -> {
                        String full = ref.getName(); // refs/heads/main or refs/heads/user/testuser
                        // Strip refs/heads/ prefix (always 11 chars)
                        return full.startsWith("refs/heads/") ? full.substring("refs/heads/".length()) : full;
                    })
                    .toList();
        } catch (Exception e) {
            throw new GitOperationException(projectName, "Failed to list branches", e);
        }
    }

    @Override
    public void deleteBranch(String projectName, String branchName) {
        if ("main".equals(branchName)) {
            throw new GitOperationException(projectName, "Cannot delete main branch");
        }
        withLock(projectName, () -> {
            try (Git git = Git.open(getRepoPath(projectName).toFile())) {
                git.branchDelete().setBranchNames(branchName).setForce(true).call();
                log.info("Deleted branch [{}] for project [{}]", branchName, projectName);
            } catch (Exception e) {
                throw new GitOperationException(projectName,
                        "Failed to delete branch " + branchName, e);
            }
        });
    }

    // ========== File operations ==========

    @Override
    public void writeFile(String projectName, String branch, String filePath, String content) {
        withLock(projectName, () -> {
            try (Git git = Git.open(getRepoPath(projectName).toFile())) {
                // Checkout the target branch
                git.checkout().setName(branch).call();

                Path fullPath = getRepoPath(projectName).resolve(filePath);
                Files.createDirectories(fullPath.getParent());
                Files.writeString(fullPath, content, StandardCharsets.UTF_8);
                log.debug("Wrote file [{}] on branch [{}] for project [{}]", filePath, branch, projectName);
            } catch (Exception e) {
                throw new GitOperationException(projectName,
                        "Failed to write file " + filePath, e);
            }
        });
    }

    @Override
    public String readFile(String projectName, String revision, String filePath) {
        try (Git git = Git.open(getRepoPath(projectName).toFile());
             RevWalk revWalk = new RevWalk(git.getRepository())) {

            ObjectId revId = resolve(git, revision);
            if (revId == null) {
                log.debug("Revision [{}] not found for project [{}]", revision, projectName);
                return null;
            }

            RevCommit commit = revWalk.parseCommit(revId);
            ObjectId treeId = commit.getTree();

            try (ObjectReader reader = git.getRepository().newObjectReader()) {
                // Walk into sub-trees for nested paths (e.g., "folder/file.xml")
                String[] parts = filePath.split("/");
                org.eclipse.jgit.lib.ObjectId currentTree = treeId;
                for (int i = 0; i < parts.length; i++) {
                    CanonicalTreeParser treeParser = new CanonicalTreeParser();
                    treeParser.reset(reader, currentTree);
                    boolean found = false;
                    while (!treeParser.eof()) {
                        String entryPath = treeParser.getEntryPathString();
                        if (entryPath.equals(parts[i])) {
                            if (i == parts.length - 1) {
                                // Leaf — this is the file
                                ObjectId blobId = treeParser.getEntryObjectId();
                                ObjectLoader loader = reader.open(blobId);
                                return new String(loader.getBytes(), StandardCharsets.UTF_8);
                            } else {
                                // Sub-directory — descend
                                currentTree = treeParser.getEntryObjectId();
                                found = true;
                                break;
                            }
                        }
                        treeParser = treeParser.next();
                    }
                    if (!found && i < parts.length - 1) {
                        return null; // intermediate directory not found
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw new GitOperationException(projectName,
                    "Failed to read file " + filePath + " at " + revision, e);
        }
    }

    @Override
    public InputStream readFileStream(String projectName, String revision, String filePath) {
        String content = readFile(projectName, revision, filePath);
        if (content == null) return null;
        return new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void deleteFile(String projectName, String branch, String filePath) {
        withLock(projectName, () -> {
            try (Git git = Git.open(getRepoPath(projectName).toFile())) {
                git.checkout().setName(branch).call();
                git.rm().addFilepattern(filePath).call();
                log.debug("Deleted file [{}] on branch [{}] for project [{}]", filePath, branch, projectName);
            } catch (Exception e) {
                throw new GitOperationException(projectName,
                        "Failed to delete file " + filePath, e);
            }
        });
    }

    // ========== Commit and tag ==========

    @Override
    public String commit(String projectName, String branch, String message, String author) {
        return withLockReturn(projectName, () -> {
            try (Git git = Git.open(getRepoPath(projectName).toFile())) {
                git.checkout().setName(branch).call();
                git.add().addFilepattern(".").call();

                // Check if there's anything to commit
                var status = git.status().call();
                boolean hasChanges = !status.getUncommittedChanges().isEmpty()
                        || !status.getUntracked().isEmpty()
                        || !status.getAdded().isEmpty()
                        || !status.getChanged().isEmpty()
                        || !status.getRemoved().isEmpty();

                if (!hasChanges) {
                    // Return current HEAD
                    Ref head = git.getRepository().findRef("HEAD");
                    return head.getObjectId().getName();
                }

                PersonIdent ident = new PersonIdent(author, author + "@ruleforge.dev",
                        new java.util.Date(), TimeZone.getTimeZone("Asia/Shanghai"));
                RevCommit commit = git.commit()
                        .setMessage(message)
                        .setAuthor(ident)
                        .setCommitter(ident)
                        .call();
                log.info("Committed on branch [{}] for project [{}]: {}",
                        branch, projectName, commit.getName());
                return commit.getName();
            } catch (Exception e) {
                throw new GitOperationException(projectName, "Failed to commit", e);
            }
        });
    }

    @Override
    public void createTag(String projectName, String tagName, String branch) {
        withLock(projectName, () -> {
            try (Git git = Git.open(getRepoPath(projectName).toFile())) {
                // Check if tag already exists
                boolean exists = git.tagList().call().stream()
                        .anyMatch(ref -> ref.getName().equals("refs/tags/" + tagName));
                if (exists) {
                    log.debug("Tag [{}] already exists for project [{}]", tagName, projectName);
                    return;
                }
                git.tag().setName(tagName).call();
                log.info("Created tag [{}] for project [{}]", tagName, projectName);
            } catch (Exception e) {
                throw new GitOperationException(projectName,
                        "Failed to create tag " + tagName, e);
            }
        });
    }

    @Override
    public String getRevisionSha(String projectName, String revision) {
        try (Git git = Git.open(getRepoPath(projectName).toFile())) {
            ObjectId id = resolve(git, revision);
            return id != null ? id.getName() : null;
        } catch (Exception e) {
            throw new GitOperationException(projectName,
                    "Failed to resolve revision " + revision, e);
        }
    }

    // ========== Merge ==========

    @Override
    public MergeResult merge(String projectName, String source, String target) {
        return withLockReturn(projectName, () -> {
            try (Git git = Git.open(getRepoPath(projectName).toFile())) {
                // Checkout target branch
                git.checkout().setName(target).call();

                // Save current HEAD before merge for potential abort
                ObjectId preMergeHead = git.getRepository().findRef("HEAD").getObjectId();

                // Find source branch HEAD
                ObjectId sourceHead = resolve(git, source);
                if (sourceHead == null) {
                    throw new GitOperationException(projectName,
                            "Source branch " + source + " not found");
                }

                var mergeResult = git.merge()
                        .include(sourceHead)
                        .setCommit(true)
                        .setMessage("Merge " + source + " into " + target)
                        .call();

                return switch (mergeResult.getMergeStatus()) {
                    case FAST_FORWARD, FAST_FORWARD_SQUASHED, ALREADY_UP_TO_DATE -> {
                        Ref head = git.getRepository().findRef("HEAD");
                        yield MergeResult.fastForward(head.getObjectId().getName());
                    }
                    case MERGED, MERGED_SQUASHED, MERGED_NOT_COMMITTED, MERGED_SQUASHED_NOT_COMMITTED -> {
                        Ref head = git.getRepository().findRef("HEAD");
                        yield MergeResult.merged(head.getObjectId().getName());
                    }
                    case CONFLICTING -> {
                        List<String> conflicts = new ArrayList<>(mergeResult.getConflicts().keySet());
                        // Abort the merge: reset target branch to pre-merge HEAD
                        git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
                                .setRef(preMergeHead.getName()).call();
                        yield MergeResult.conflicting(conflicts);
                    }
                    default -> MergeResult.conflicting(Collections.emptyList());
                };
            } catch (GitOperationException e) {
                throw e;
            } catch (Exception e) {
                throw new GitOperationException(projectName,
                        "Failed to merge " + source + " into " + target, e);
            }
        });
    }

    // ========== Diff ==========

    @Override
    public List<FileDiff> diff(String projectName, String fromRevision, String toRevision) {
        try (Git git = Git.open(getRepoPath(projectName).toFile())) {
            var repo = git.getRepository();

            ObjectId oldId = resolve(git, fromRevision);
            ObjectId newId = resolve(git, toRevision);
            if (oldId == null || newId == null) {
                return Collections.emptyList();
            }

            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            CanonicalTreeParser newTree = new CanonicalTreeParser();

            try (ObjectReader reader = repo.newObjectReader();
                 RevWalk revWalk = new RevWalk(repo)) {
                RevCommit oldCommit = revWalk.parseCommit(oldId);
                RevCommit newCommit = revWalk.parseCommit(newId);
                oldTree.reset(reader, oldCommit.getTree());
                newTree.reset(reader, newCommit.getTree());
            }

            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTree)
                    .setNewTree(newTree)
                    .call();

            List<FileDiff> result = new ArrayList<>();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setRepository(repo);
                for (DiffEntry entry : diffs) {
                    out.reset();
                    formatter.format(entry);
                    String patch = out.toString(StandardCharsets.UTF_8);

                    FileDiff.DiffType type = switch (entry.getChangeType()) {
                        case ADD -> FileDiff.DiffType.ADDED;
                        case DELETE -> FileDiff.DiffType.DELETED;
                        default -> FileDiff.DiffType.MODIFIED;
                    };

                    result.add(new FileDiff(entry.getNewPath(), type, patch));
                }
            }
            return result;
        } catch (Exception e) {
            throw new GitOperationException(projectName,
                    "Failed to diff " + fromRevision + ".." + toRevision, e);
        }
    }

    // ========== Remote ==========

    @Override
    public void push(String projectName) {
        String remoteUrl = gitConfig.getRemoteUrl();
        if (remoteUrl == null || remoteUrl.isBlank()) {
            log.debug("No remote URL configured, skipping push for project [{}]", projectName);
            return;
        }

        withLock(projectName, () -> {
            try (Git git = Git.open(getRepoPath(projectName).toFile())) {
                // Push all branches
                git.push().setRemote("origin").setPushAll().call();
                // Push all tags
                git.push().setRemote("origin").setPushTags().call();
                log.info("Pushed to remote for project [{}]", projectName);
            } catch (Exception e) {
                log.error("Failed to push for project [{}]: {}", projectName, e.getMessage());
                // Don't throw — push failure should not block the main workflow
            }
        });
    }

    @Override
    public void addRemote(String projectName, String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) return;
        String fullUrl = remoteUrl.endsWith("/") ? remoteUrl + projectName + ".git" : remoteUrl + "/" + projectName + ".git";

        try (Git git = Git.open(getRepoPath(projectName).toFile())) {
            // Check if remote already exists
            var config = git.getRepository().getConfig();
            String existingUrl = config.getString("remote", "origin", "url");
            if (fullUrl.equals(existingUrl)) return;

            if (existingUrl != null) {
                git.remoteSetUrl().setRemoteName("origin").setRemoteUri(new URIish(fullUrl)).call();
            } else {
                git.remoteAdd().setName("origin").setUri(new URIish(fullUrl)).call();
            }
            log.info("Set remote origin to [{}] for project [{}]", fullUrl, projectName);
        } catch (Exception e) {
            log.warn("Failed to add remote for project [{}]: {}", projectName, e.getMessage());
        }
    }

    // ========== Private helpers ==========

    private Path getRepoPath(String projectName) {
        return Paths.get(gitConfig.getBase(), projectName);
    }

    private ObjectId resolve(Git git, String revision) throws IOException {
        var repo = git.getRepository();
        // Try as branch
        try {
            Ref ref = repo.findRef(revision);
            if (ref != null) return ref.getObjectId();
        } catch (Exception ignored) {}
        // Try as tag
        try {
            Ref tag = repo.findRef("refs/tags/" + revision);
            if (tag != null) {
                // Peel tag to underlying commit
                try (RevWalk rw = new RevWalk(repo)) {
                    ObjectId objId = tag.getObjectId();
                    var revObj = rw.parseAny(objId);
                    if (revObj instanceof org.eclipse.jgit.revwalk.RevTag revTag) {
                        return revTag.getObject();
                    }
                    return objId;
                } catch (Exception e) {
                    return tag.getObjectId();
                }
            }
        } catch (Exception ignored) {}
        // Try as raw SHA
        try {
            return repo.resolve(revision);
        } catch (Exception e) {
            return null;
        }
    }

    private void withLock(String projectName, Runnable action) {
        Object lock = projectLocks.computeIfAbsent(projectName, k -> new Object());
        synchronized (lock) {
            action.run();
        }
    }

    private <T> T withLockReturn(String projectName, java.util.concurrent.Callable<T> action) {
        Object lock = projectLocks.computeIfAbsent(projectName, k -> new Object());
        synchronized (lock) {
            try {
                return action.call();
            } catch (GitOperationException e) {
                throw e;
            } catch (Exception e) {
                throw new GitOperationException(projectName, "Git operation failed", e);
            }
        }
    }
}
