package com.ruleforge.console.storage.impl;

import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.PackageRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.entity.*;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.model.GitOperationException;
import com.ruleforge.console.storage.model.MergeResult;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.storage.ProjectStorageService;
import com.ruleforge.console.util.VersionUtils;
import com.ruleforge.exception.RuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseProjectStorageServiceImpl implements ProjectStorageService {

    private final ProjectRepository projectRepository;
    private final FileRepository fileRepository;
    private final PackageRepository packageRepository;
    private final GitStorageService gitStorageService;
    public static final String SNAPSHOT_VERSION = "snapshot";
    public static final Long SNAPSHOT_VERSION_REAL = 1000_000_000L;

    @Override
    public String createProjectVersion(String projectName, String projectVersion, String createUser, String comment, Integer status) throws Exception {
        // 1. 查找项目
        ProjectEntity project = projectRepository.findByName(projectName);
        if (project == null) {
            log.error("Project [{}] not found.", projectName);
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        Long projectId = project.getId();

        // 2. 确定新版本的版本号和名称
        if (StringUtils.isEmpty(projectVersion)) {
            ProjectVersionEntity latestExistingVersion = projectRepository.findLatestVersionByProjectId(projectId);

            projectVersion = VersionUtils.incrementVersion(latestExistingVersion.getVersionName());
        }
        Long newVersionNumReal = VersionUtils.convertVersionToLong(projectVersion);
        log.info("Determined new version (Real: {}) for project [{}]", newVersionNumReal, projectName);
        String finalComment = StringUtils.isNotBlank(comment) ? comment : "Version created on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // 3. 创建 gr_project_version 记录 (使用传入的参数)
        ProjectVersionEntity projectVersionEntity = new ProjectVersionEntity();
        projectVersionEntity.setProjectId(projectId);
        projectVersionEntity.setVersionName(projectVersion);
        projectVersionEntity.setVersionNumReal(newVersionNumReal);
        projectVersionEntity.setAuditStatus(status);
        projectVersionEntity.setCreateUser(createUser);
        projectVersionEntity.setComment(finalComment);
        projectVersionEntity.setCreateTime(new Date());
        projectVersionEntity = projectRepository.insertVersion(projectVersionEntity);
        // 检查插入是否成功以及 ID 是否已生成
        if (projectVersionEntity.getId() == null) {
            log.error("Failed to insert project version record for project ID [{}], version name [{}].", projectId, projectVersion);
            throw new RuleException("Failed to insert project version record for project ID [" + projectId + "]");
        }
        Long projectVersionId = projectVersionEntity.getId();
        log.info("Storage service: Successfully created project version record with ID [{}] for project ID [{}].", projectVersionId, projectId);

        String updateStr = updateSnapshotToRelease(projectId, newVersionNumReal);
        log.info("updateStr {}", updateStr);

        // 5. 返回成功创建的版本名称 (从参数中获取)
        return projectVersion;
    }

    @Override
    public String createProjectPackageVersion(String projectName, String packageId, String packageVersion, String createUser, String comment, Integer status) throws Exception {
        // 1. 查找项目
        ProjectEntity project = projectRepository.findByName(projectName);
        if (project == null) {
            log.error("Project [{}] not found.", projectName);
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        Long projectId = project.getId();

        // 2. 确定新版本的版本号和名称
        if (StringUtils.isEmpty(packageVersion)) {
            ProjectVersionEntity latestExistingVersion = projectRepository.findLatestVersionByProjectId(projectId);

            packageVersion = VersionUtils.incrementVersion(latestExistingVersion.getVersionName());
        }
        Long newVersionNumReal = VersionUtils.convertVersionToLong(packageVersion);
        log.info("Determined new version (Real: {}) for project [{}]", newVersionNumReal, projectName);
        String finalComment = StringUtils.isNotBlank(comment) ? comment : "Version created on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // 3. 创建 gr_project_version 记录 (使用传入的参数)
        ProjectVersionEntity projectVersionEntity = new ProjectVersionEntity();
        projectVersionEntity.setProjectId(projectId);
        projectVersionEntity.setPackageId(packageId);
        projectVersionEntity.setVersionName(packageVersion);
        projectVersionEntity.setVersionNumReal(newVersionNumReal);
        projectVersionEntity.setAuditStatus(status);
        projectVersionEntity.setCreateUser(createUser);
        projectVersionEntity.setComment(finalComment);
        projectVersionEntity.setCreateTime(new Date());
        projectVersionEntity = projectRepository.insertVersion(projectVersionEntity);
        // 检查插入是否成功以及 ID 是否已生成
        if (projectVersionEntity.getId() == null) {
            log.error("Failed to insert project version record for project ID [{}], version name [{}].", projectId, packageVersion);
            throw new RuleException("Failed to insert project version record for project ID [" + projectId + "]");
        }
        Long projectVersionId = projectVersionEntity.getId();
        log.info("Storage service: Successfully created project version record with ID [{}] for project ID [{}].", projectVersionId, projectId);

        // 4. 插入项目所有latest版本
        fileRepository.updateProjectVersionNumReal(projectId, SNAPSHOT_VERSION_REAL, newVersionNumReal);
        String updateStr = updateSnapshotToRelease(projectId, newVersionNumReal);
        log.info("updateStr {}", updateStr);

        // 6. Git integration: merge user branch to main + create tag + populate mapping
        String gitCommitSha = null;
        if (gitStorageService.repoExists(projectName)) {
            try {
                // Determine the source branch (user who created the version)
                String sourceBranch = "user/" + createUser;

                // Merge user branch into main (if the user branch exists)
                List<String> branches = gitStorageService.listBranches(projectName);
                if (branches.contains(sourceBranch)) {
                    MergeResult mergeResult = gitStorageService.merge(projectName, sourceBranch, "main");
                    if (mergeResult.getStatus() == MergeResult.Status.CONFLICTING) {
                        log.warn("Merge conflict when creating package version for [{}]: {}",
                                projectName, mergeResult.getConflictingFiles());
                        // Continue — the version is created in DB, but Git tag will point to current main
                    } else {
                        gitCommitSha = mergeResult.getMergeCommitSha();
                        log.info("Merged branch [{}] to main for project [{}] (sha={})",
                                sourceBranch, projectName, gitCommitSha);
                    }
                }

                // Create Git tag for this package version
                String tagName = "pkg/" + packageId + "/" + packageVersion;
                gitStorageService.createTag(projectName, tagName, "main");

                // Get the commit SHA for the tag
                if (gitCommitSha == null) {
                    gitCommitSha = gitStorageService.getRevisionSha(projectName, tagName);
                }

                // Update project_version with git info
                projectVersionEntity.setGitCommitSha(gitCommitSha);
                projectVersionEntity.setGitBranch(sourceBranch);
                projectRepository.updateVersion(projectVersionEntity);

                // Populate package_version_mapping with Git blob SHAs for all files in this package
                populatePackageVersionMapping(projectVersionId, projectName, tagName);

                // Push main + tags to remote
                gitStorageService.push(projectName);
                log.info("Git tag [{}] created and pushed for project [{}]", tagName, projectName);
            } catch (GitOperationException e) {
                log.error("Git integration failed during package version creation for [{}]", projectName, e);
                // Don't fail the version creation — DB write succeeded
            }
        }

        // 5. 返回成功创建的版本名称 (从参数中获取)
        return packageVersion;
    }

    private String updateSnapshotToRelease(Long projectId, Long projectVersion) {
        List<FileVersionEntity> tmpFileVersionList = this.fileRepository.findSnapshotsByProjectId(projectId);

        StringBuilder sb = new StringBuilder();
        for (FileVersionEntity tmpFile : tmpFileVersionList) {
            // 获取最新的release版本
            FileVersionEntity latestReleaseVersion = this.fileRepository.findLatestReleaseByFileId(tmpFile.getFileId());

            VersionUtils.incrementVersionFileVersion(latestReleaseVersion, tmpFile);
            this.fileRepository.updateVersionNumAndReal(tmpFile.getId(), tmpFile.getVersionNum(), tmpFile.getVersionNumReal(), projectVersion);

            sb.append(tmpFile.getFilePath()).append(":").append(tmpFile.getVersionNum())
                    .append(",").append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取指定项目下的所有文件版本实体。
     *
     * @param projectId 项目ID
     * @return 文件版本实体列表，如果找不到则返回空列表。
     */
    private List<FileEntity> getAllFilesForProject(Long projectId) {
        if (projectId == null) {
            log.warn("Attempted to get file versions with null project ID.");
            return Collections.emptyList(); // 或者根据需要抛出异常
        }
        log.debug("Fetching all file versions for project ID [{}].", projectId);
        List<FileEntity> fileVersions = fileRepository.findByProjectId(projectId);
        return fileVersions == null ? Collections.emptyList() : fileVersions;
    }

    /**
     * 获取指定项目下所有文件的最新版本ID列表。
     *
     * @param projectId 项目ID
     * @return 最新版本ID列表
     */
    private List<FileVersionEntity> getAllLatestFileVersions(Long projectId) {
        if (projectId == null) {
            log.warn("Attempted to get latest file version ids with null project ID.");
            return Collections.emptyList();
        }
        List<FileEntity> files = getAllFilesForProject(projectId);
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        // 把files里面的latest version id组成要给list，然后用这个去查询所有的version
        List<Long> latestVersionIds = files.stream()
                .map(FileEntity::getLatestVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (latestVersionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return fileRepository.selectBatchByIds(latestVersionIds);
    }

    @Override
    public boolean exists(String path) throws Exception {
        return false;
    }

    @Override
    public InputStream readFile(String path, String version) throws Exception {
        return null;
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project) throws Exception {
        return Collections.emptyList();
    }

    @Override
    public Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName) throws Exception {
        return null;
    }

    @Override
    public void delete(String path, User user) throws Exception {

    }

    @Override
    public String saveFile(String path, String content, User user, boolean newVersion, String versionComment) throws Exception {
        return "";
    }

    @Override
    public void createDirectory(String path, User user) throws Exception {

    }

    /**
     * Populate gr_package_version_mapping with Git blob SHAs for all files
     * referenced by this package version.
     */
    private void populatePackageVersionMapping(Long packageVersionId, String projectName, String tagName) {
        try {
            // Get all file versions that belong to this package version
            ProjectVersionEntity pve = projectRepository.findVersionById(packageVersionId);
            if (pve == null) return;

            List<FileVersionEntity> fileVersions = fileRepository.findByProjectIdAndProjectVersionNumReal(pve.getProjectId(), pve.getVersionNumReal());

            List<PackageVersionMappingEntity> mappings = new ArrayList<>();
            for (FileVersionEntity fv : fileVersions) {
                String gitPath = fv.getFilePath();
                if (gitPath != null && gitPath.startsWith("/")) {
                    gitPath = gitPath.substring(1);
                }
                String blobSha = gitStorageService.getRevisionSha(projectName, tagName);
                if (gitPath != null && blobSha != null) {
                    PackageVersionMappingEntity mapping = new PackageVersionMappingEntity();
                    mapping.setPackageVersionId(packageVersionId);
                    mapping.setFilePath(gitPath);
                    mapping.setGitBlobSha(blobSha);
                    mapping.setCreateTime(new Date());
                    mappings.add(mapping);
                }
            }

            if (!mappings.isEmpty()) {
                for (PackageVersionMappingEntity m : mappings) {
                    packageRepository.insertMapping(m);
                }
                log.info("Inserted {} mapping records for package version [{}]", mappings.size(), packageVersionId);
            }
        } catch (Exception e) {
            log.error("Failed to populate package version mapping for [{}]", packageVersionId, e);
        }
    }

    @Override
    public List<String> listProjectVersions(String projectName) throws Exception {
        ProjectEntity project = projectRepository.findByName(projectName);
        if (project == null) {
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        List<ProjectVersionEntity> versions = projectRepository.findVersionsByProjectIdOrderByCreateTime(project.getId());

        return versions.stream().map(ProjectVersionEntity::getVersionName).collect(Collectors.toList());
    }

    @Override
    public Object checkoutProjectVersion(String projectName, String version) throws Exception {
        ProjectEntity project = projectRepository.findByName(projectName);
        if (project == null) {
            throw new RuleException("Project [" + projectName + "] not found.");
        }

        ProjectVersionEntity projectVersion = projectRepository.findVersionByProjectIdAndVersionName(project.getId(), version);
        if (projectVersion == null) {
            throw new RuleException("Project version [" + version + "] for project [" + projectName + "] not found.");
        }

        List<ProjectVersionMappingEntity> mappings = projectRepository.findMappingsByProjectVersionId(projectVersion.getId());

        if (CollectionUtils.isEmpty(mappings)) {
            return Collections.emptyMap(); // 空版本
        }

        List<Long> fileVersionIds = mappings.stream()
                .map(ProjectVersionMappingEntity::getFileVersionId)
                .collect(Collectors.toList());

        // 批量查询文件版本内容
        List<FileVersionEntity> fileVersions = fileRepository.selectBatchByIds(fileVersionIds);

        return fileVersions.stream()
                .collect(Collectors.toMap(FileVersionEntity::getFilePath, FileVersionEntity::getFileContent));

    }
}
