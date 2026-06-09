package com.ruleforge.console.storage;

import com.ruleforge.console.storage.model.FileDiff;
import com.ruleforge.console.storage.model.MergeResult;

import java.io.InputStream;
import java.util.List;

/**
 * Git-based version storage service.
 * Operates on per-project Git repositories at {gitBase}/{projectName}/.
 * <p>
 * Branch conventions:
 * - "main" = production baseline
 * - "user/{username}" = per-user working branches
 * <p>
 * Tag conventions:
 * - "pkg/{packageId}/{version}" = package version tags
 */
public interface GitStorageService {

    // ---- Repository lifecycle ----

    /**
     * Initialize a new Git repository for a project.
     * Creates the directory and runs git init with an initial empty commit on "main".
     */
    void initRepo(String projectName);

    /**
     * Check if a Git repository exists for a project.
     */
    boolean repoExists(String projectName);

    // ---- Branch operations ----

    /**
     * Create a new branch from a parent branch.
     * If the branch already exists, this is a no-op.
     */
    void createBranch(String projectName, String branchName, String parentBranch);

    /**
     * List all branches in the project repository.
     */
    List<String> listBranches(String projectName);

    /**
     * Delete a branch (typically after merge).
     */
    void deleteBranch(String projectName, String branchName);

    // ---- File operations ----

    /**
     * Write a file to a branch's working tree.
     * Does NOT auto-commit; caller must call commit() separately.
     *
     * @param projectName project name
     * @param branch      target branch name
     * @param filePath    path relative to repo root (e.g., "projectA/folder/file.xml")
     * @param content     file content (should be pre-canonicalized)
     */
    void writeFile(String projectName, String branch, String filePath, String content);

    /**
     * Read a file from a specific revision (branch name, tag, or commit SHA).
     * Reads directly from the Git object store without checking out a working tree.
     *
     * @return file content as string, or null if the file does not exist at the given revision
     */
    String readFile(String projectName, String revision, String filePath);

    /**
     * Read a file as InputStream from a specific revision.
     */
    InputStream readFileStream(String projectName, String revision, String filePath);

    /**
     * Delete a file from a branch's working tree.
     * Does NOT auto-commit.
     */
    void deleteFile(String projectName, String branch, String filePath);

    // ---- Commit and tag ----

    /**
     * Stage all changes on a branch and commit.
     *
     * @return the commit SHA
     */
    String commit(String projectName, String branch, String message, String author);

    /**
     * Create a lightweight tag at the current HEAD of a branch.
     */
    void createTag(String projectName, String tagName, String branch);

    /**
     * Get the commit SHA for a given revision (branch, tag, or SHA).
     */
    String getRevisionSha(String projectName, String revision);

    // ---- Merge ----

    /**
     * Merge a source branch into a target branch.
     * Tries fast-forward first, then three-way merge.
     *
     * @return MergeResult with status (FAST_FORWARD, MERGED, or CONFLICTING)
     */
    MergeResult merge(String projectName, String source, String target);

    // ---- Diff ----

    /**
     * Compare two revisions and return per-file diffs.
     */
    List<FileDiff> diff(String projectName, String fromRevision, String toRevision);

    // ---- Remote ----

    /**
     * Push all branches and tags to the configured remote.
     * No-op if no remote is configured.
     */
    void push(String projectName);

    /**
     * Add a remote URL for the project repository.
     */
    void addRemote(String projectName, String remoteUrl);
}
