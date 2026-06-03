package com.ruleforge.console.controller;

import com.ruleforge.console.EnvironmentProvider;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectVersionEntity;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.XmlCanonicalizer;
import com.ruleforge.console.config.GitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController("ruleforgeApiController")
@RequestMapping("/${ruleforge.root.path}/api")
@RequiredArgsConstructor
public class ApiController {

    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final EnvironmentProvider environmentProvider;
    private final ProjectRepository projectRepository;
    private final GitStorageService gitStorageService;
    private final GitConfig gitConfig;
    private final XmlCanonicalizer xmlCanonicalizer;

    /**
     * Migrate a project's version history from DB to Git.
     * Initializes a Git repo (if not exists), iterates all project versions,
     * writes files for each version, commits, and creates tags.
     */
    @GetMapping("/fix-git")
    public ResponseEntity<?> fixGit(@RequestParam String projectName) {
        log.info("Received request to migrate project [{}] to Git", projectName);
        try {
            User user = this.environmentProvider.getLoginUser(null);

            // Initialize Git repo for the project
            gitStorageService.initRepo(projectName);
            gitStorageService.addRemote(projectName, gitConfig.getRemoteUrl());

            // Load repository file tree
            Repository repository = this.ruleforgeRepositoryService.loadRepository(
                    projectName, user, false, null, null);

            // Get all project versions ordered by version number
            ProjectEntity projectEntity = this.projectRepository.findByName(projectName);
            if (projectEntity == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Project not found: " + projectName);
            }

            List<ProjectVersionEntity> versionList = this.projectRepository.findVersionsByProjectId(projectEntity.getId(), null, false);

            RepositoryFile rootFile = repository.getRootFile().getChildren().get(0);

            for (ProjectVersionEntity projectVersion : versionList) {
                String version = projectVersion.getVersionName();
                String tagName = version;

                // Skip if tag already exists
                String existingSha = gitStorageService.getRevisionSha(projectName, tagName);
                if (existingSha != null) {
                    log.info("Tag [{}] already exists for project [{}]. Skipping.", tagName, projectName);
                    continue;
                }

                // Write all files for this version
                iterateAndWriteFiles(rootFile, projectName, "main", version);

                // Commit
                String author = projectVersion.getCreateUser() != null
                        ? projectVersion.getCreateUser() : "system";
                String commitSha = gitStorageService.commit(
                        projectName, "main", projectVersion.getComment(), author);

                // Create tag
                gitStorageService.createTag(projectName, tagName, "main");
                log.info("Migrated version [{}] for project [{}] (sha={})",
                        version, projectName, commitSha);
            }

            // Push everything to remote
            gitStorageService.push(projectName);

            return ResponseEntity.ok("Migration completed. " + versionList.size() + " versions processed.");
        } catch (Exception e) {
            log.error("Git migration failed for project [{}]", projectName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Migration failed: " + e.getMessage());
        }
    }

    /**
     * Push a project's Git repository to the configured remote.
     */
    @GetMapping("/fix-git-push")
    public ResponseEntity<?> fixGitPush(@RequestParam String projectName) {
        log.info("Received request to push project [{}] to remote", projectName);
        try {
            gitStorageService.addRemote(projectName, gitConfig.getRemoteUrl());
            gitStorageService.push(projectName);

            return ResponseEntity.ok("Push completed.");
        } catch (Exception e) {
            log.error("Git push failed for project [{}]", projectName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Push failed: " + e.getMessage());
        }
    }

    private void iterateAndWriteFiles(RepositoryFile repositoryFile, String projectName,
                                       String branch, String version) throws Exception {
        List<RepositoryFile> children = repositoryFile.getChildren();
        if (children == null || children.isEmpty()) return;

        for (RepositoryFile child : children) {
            if (child.getChildren() != null && !child.getChildren().isEmpty()) {
                iterateAndWriteFiles(child, projectName, branch, version);
            } else {
                writeFileToGit(child, projectName, branch, version);
            }
        }
    }

    private void writeFileToGit(RepositoryFile repositoryFile, String projectName,
                                 String branch, String version) throws Exception {
        String fullPath = repositoryFile.getFullPath();
        try (InputStream is = this.ruleforgeRepositoryService.readFile(fullPath, "latest", version, false)) {
            if (is == null) {
                log.debug("No content for file [{}] at version [{}]", fullPath, version);
                return;
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String canonical = xmlCanonicalizer.canonicalize(content);

            String gitPath = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
            gitStorageService.writeFile(projectName, branch, gitPath, canonical);
            log.debug("Wrote file [{}] for version [{}]", gitPath, version);
        }
    }
}
