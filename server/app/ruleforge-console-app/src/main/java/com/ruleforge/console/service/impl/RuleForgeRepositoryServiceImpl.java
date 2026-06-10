package com.ruleforge.console.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruleforge.Utils;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.GitDualwriteFailureRepository;
import com.ruleforge.console.repository.data.LockRepository;
import com.ruleforge.console.repository.data.PackageRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.repository.model.*;
import com.ruleforge.console.servlet.common.RefFile;
import com.ruleforge.console.servlet.frame.ExportProject;
import com.ruleforge.exception.RuleException;
import com.ruleforge.console.entity.*;
import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.model.PackageConfig;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.service.PermissionService;
import com.ruleforge.console.service.RepositoryInterceptor;
import com.ruleforge.console.storage.BranchContext;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.ProjectStorageService;
import com.ruleforge.console.storage.XmlCanonicalizer;
import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.storage.model.GitOperationException;
import com.ruleforge.console.util.FileTypeUtils;
import com.ruleforge.console.util.VersionFileUtils;
import com.ruleforge.console.util.VersionUtils;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.ruleforge.console.repository.BaseRepositoryService.CLIENT_CONFIG_FILE;
import static com.ruleforge.console.repository.BaseRepositoryService.RES_PACKGE_FILE;
import static com.ruleforge.console.storage.RuleForgeBaseRepositoryService.PACKAGE_CONFIG_FILE;
import static com.ruleforge.console.storage.RuleForgeBaseRepositoryService.RES_PACKAGE_FILE;
import static com.ruleforge.console.storage.impl.DatabaseProjectStorageServiceImpl.SNAPSHOT_VERSION;
import static com.ruleforge.console.storage.impl.DatabaseProjectStorageServiceImpl.SNAPSHOT_VERSION_REAL;


@Slf4j
@Component("ruleforge.repositoryService")
@RequiredArgsConstructor
public class RuleForgeRepositoryServiceImpl implements RuleForgeRepositoryService, RepositoryService {

    private final PermissionService permissionService;
    private final ProjectRepository projectRepository;
    private final FileRepository fileRepository;
    private final LockRepository lockRepository;
    private final PackageRepository packageRepository;
    private final RuntimeRepository runtimeRepository;
    private final ProjectStorageService projectStorageService;
    private final RepositoryInterceptor repositoryInterceptor;
    private final GitStorageService gitStorageService;
    private final GitConfig gitConfig;
    private final XmlCanonicalizer xmlCanonicalizer;
    // V5.10-C: dualWrite 失败 audit log + Micrometer counter
    private final GitDualwriteFailureRepository dualwriteFailureRepository;
    private final MeterRegistry meterRegistry;

    private static final String DUALWRITE_COUNTER = "ruleforge_git_dualwrite_total";
    private static final String DUALDELETE_COUNTER = "ruleforge_git_dualdelete_total";



    @Override
    public List<RepositoryFile> loadProjects(String companyId) throws Exception {
        return null;
    }

    @Override
    public List<String> loadProjectNames() throws Exception {
        List<ProjectEntity> projects = projectRepository.findAll();
        List<String> names = new ArrayList<>();
        for (ProjectEntity project : projects) {
            names.add(project.getName());
        }
        return names;
    }

    @Override
    public List<com.ruleforge.console.model.ClientConfig> loadClientConfigs(String project) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public List<com.ruleforge.console.servlet.permission.UserPermission> loadResourceSecurityConfigs(String companyId) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public InputStream readFile(String path) throws Exception {
        return readFile(path, null);
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project) throws Exception {
        return loadProjectResourcePackages(project, null);
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project, String env) throws Exception {
        String[] projectArray = project.split(":");
        String version = null;
        ProjectEntity projectEntity = this.projectRepository.findByName(projectArray[0]);
        if (projectEntity == null) {
            log.warn("loadProjectResourcePackages: project [{}] not found, return empty list", projectArray[0]);
            return new ArrayList<>();
        }

        if (projectArray.length > 1) {
            project = projectArray[0];
            version = projectArray[1];
        } else if (org.springframework.util.StringUtils.hasText(env)) {
            ProjectRuntimeConfigEntity projectRuntime = this.runtimeRepository.findConfigByProjectIdAndEnv(projectEntity.getId(), env);
            if (projectRuntime != null) {
                version = projectRuntime.getProjectVersion();
            }
        }

        String filePath = processPath(project) + "/" + RES_PACKAGE_FILE;
        InputStream inputStream = readFile(filePath, version);
        if (inputStream == null) {
            log.warn("loadProjectResourcePackages: res-package file [{}] version [{}] not found, return empty list", filePath, version);
            return new ArrayList<>();
        }
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();

        // todo
        List<ProjectRuntimeConfigEntity> projectRuntimeConfigEntityList = this.runtimeRepository.findConfigsByProjectId(projectEntity.getId());
        Map<String, String> packageRuntimeMap = new HashMap<>();
        for (ProjectRuntimeConfigEntity projectRuntimeConfigEntity : projectRuntimeConfigEntityList) {
            packageRuntimeMap.put(projectRuntimeConfigEntity.getPackageId() + "_" + projectRuntimeConfigEntity.getExecEnv(), projectRuntimeConfigEntity.getProjectVersion());
        }

        if (content == null || content.trim().isEmpty()) {
            log.warn("loadProjectResourcePackages: res-package file [{}] is empty, return empty list", filePath);
            return new ArrayList<>();
        }
        Document document;
        try {
            document = DocumentHelper.parseText(content);
        } catch (org.dom4j.DocumentException e) {
            log.warn("loadProjectResourcePackages: failed to parse res-package file [{}]: {}, return empty list", filePath, e.getMessage());
            return new ArrayList<>();
        }
        Element rootElement = document.getRootElement();
        SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<ResourcePackage> packages = new ArrayList<>();
        for (Object obj : rootElement.elements()) {
            if (!(obj instanceof Element)) {
                continue;
            }
            Element element = (Element) obj;
            if (!element.getName().equals("res-package")) {
                continue;
            }
            ResourcePackage p = new ResourcePackage();
            String dateStr = element.attributeValue("create_date");
            if (dateStr != null) {
                p.setCreateDate(sd.parse(dateStr));
            }
            p.setId(element.attributeValue("id"));
            p.setName(element.attributeValue("name"));
            p.setVersion(packageRuntimeMap.get(p.getId() + "_" + "prod"));
            p.setTestVersion(packageRuntimeMap.get(p.getId() + "_" + "test"));
            p.setProject(project);
            List<ResourceItem> items = new ArrayList<>();
            for (Object o : element.elements()) {
                if (!(o instanceof Element)) {
                    continue;
                }
                Element ele = (Element) o;
                if (!ele.getName().equals("res-package-item")) {
                    continue;
                }
                ResourceItem item = new ResourceItem();
                item.setName(ele.attributeValue("name"));
                item.setPackageId(p.getId());
                item.setPath(ele.attributeValue("path"));
                item.setVersion(ele.attributeValue("version"));
                items.add(item);
            }
            p.setResourceItems(items);
            packages.add(p);
        }
        return packages;
    }

    @Override
    public boolean fileExistCheck(String filePath) throws Exception {
        filePath = processPath(filePath);
        if (filePath.contains(" ") || filePath.isEmpty()) {
            return true;
        }

        FileEntity file = this.fileRepository.findByFilePathSelectId(filePath);
        return file != null;
    }

    @Override
    public RepositoryFile createProject(String projectName, User user, boolean classify) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        this.repositoryInterceptor.createProject(projectName);
        String projectRootPath = processPath(projectName);
        if (fileExistCheck(projectRootPath)) {
            throw new RuleException("Project [" + projectName + "] already exist.");
        }

        ProjectEntity project = new ProjectEntity();
        project.setName(projectName);
        project.setCreateTime(new Date());
        this.projectRepository.insert(project);

        createResourcePackageFile(project.getId(), projectName, user);
        createAllResourceFolder(project.getId(), projectRootPath);
        createPackageConfigFile(projectName, user);
        createClientConfigFile(projectName, user);

        // Initialize Git repository for the new project
        try {
            gitStorageService.initRepo(projectName);
            gitStorageService.addRemote(projectName, gitConfig.getRemoteUrl());
            log.info("Initialized Git repo for new project [{}]", projectName);
        } catch (GitOperationException e) {
            log.error("Failed to initialize Git repo for project [{}]", projectName, e);
        }

        return buildProjectFile(project, null, classify, null);
    }

    private void createAllResourceFolder(Long projectId, String projectRootPath) {
        FileEntity allResource = new FileEntity();
        allResource.setName("资源");
        allResource.setFileType(Type.all.ordinal());
        allResource.setProjectId(projectId);
        allResource.setFilePath(projectRootPath);
        allResource.setCreateTime(new Date());
        this.fileRepository.insert(allResource);

        FileRelationEntity fileRelation = new FileRelationEntity();
        fileRelation.setAncestor(projectId);
        fileRelation.setDescendant(allResource.getId());
        fileRelation.setDistance(1);
        fileRelation.setProjectId(projectId);
        this.fileRepository.insertRelation(fileRelation);
    }

    private void createResourcePackageFile(Long projectId, String project, User user) throws Exception {
        String filePath = processPath(project) + "/" + RES_PACKGE_FILE;
        if (!fileExistCheck(filePath)) {
            FileEntity file = new FileEntity();
            file.setName("知识包.rp");
            file.setFileType(Type.resourcePackage.ordinal());
            file.setProjectId(projectId);
            file.setFilePath(filePath);
            file.setCreateTime(new Date());
            this.fileRepository.insert(file);

            FileVersionEntity fileVersionEntity = new FileVersionEntity();
            fileVersionEntity.setFilePath(filePath);
            fileVersionEntity.setFileName(filePath);
            fileVersionEntity.setFileContent("<?xml version=\"1.0\" encoding=\"utf-8\"?><res-packages></res-packages>");
            fileVersionEntity.setVersionNum("latest");
            fileVersionEntity.setProjectId(projectId);
            fileVersionEntity.setCreateUser(user.getUsername());
            fileVersionEntity.setCreateDate(new Date());
            this.fileRepository.insert(fileVersionEntity);

            FileRelationEntity fileRelation = new FileRelationEntity();
            fileRelation.setAncestor(projectId);
            fileRelation.setDescendant(file.getId());
            fileRelation.setDistance(1);
            fileRelation.setProjectId(projectId);
            this.fileRepository.insertRelation(fileRelation);
        }
    }

    private void createPackageConfigFile(String project, User user) throws Exception {
        String filePath = processPath(project) + "/" + PACKAGE_CONFIG_FILE;
        if (!fileExistCheck(filePath)) {
            createFile(filePath, "<?xml version=\"1.0\" encoding=\"utf-8\"?><package-config></package-config>", user);
        }
    }

    private void createClientConfigFile(String project, User user) throws Exception {
        String filePath = processPath(project) + "/" + CLIENT_CONFIG_FILE;
        if (!fileExistCheck(filePath)) {
            createFile(filePath, "<?xml version=\"1.0\" encoding=\"utf-8\"?><client-config></client-config>", user);
        }
    }

    @Override
    public void createDir(String path, User user) throws Exception {
        if (!this.permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        createFileNode(path, null, user, false);
    }

    @Override
    public void createFile(String path, String content, User user) throws Exception {
        if (!this.permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        createFileNode(path, content, user, true);
    }

    @Override
    public String saveFile(String path, String content, boolean newVersion, String versionComment, User user) throws Exception {
        return saveFile(path, content, newVersion, versionComment, null, null, user);
    }

    @Override
    public String saveFile(String path, String content, boolean newVersion, String versionComment, String beforeComment, String afterComment, User user) throws Exception {
        return saveFile(path, content, newVersion, versionComment, beforeComment, afterComment, user, null);
    }

    @Override
    public String saveFile(String path, String content, boolean newVersion, String versionComment, String beforeComment, String afterComment, User user, Date createTime) throws Exception {
        path = Utils.decodeURL(path);
        boolean packageFile = false;
        if (path.contains(RES_PACKAGE_FILE)) {
            packageFile = true;
            if (!this.permissionService.projectPackageHasWritePermission(path)) {
                throw new NoPermissionException();
            }
        }
        if (!this.permissionService.fileHasWritePermission(path)) {
            throw new NoPermissionException();
        }

        path = processPath(path);
        int pos = path.indexOf(":");
        if (pos != -1) {
            path = path.substring(0, pos);
        }

        Long lockVersion = lockPath(path, user);
        if (lockVersion == null) {
            return null;
        }

        String versionNum = null;
        try {
            FileEntity file = this.fileRepository.findByFilePath(path);
            if (file == null) {
                throw new RuleException("File [" + path + "] not exist.");
            }
            lockCheck(file, user);

            Calendar calendar = Calendar.getInstance();
            if (createTime == null) {
                calendar.setTime(new Date());
            } else {
                calendar.setTime(createTime);
            }

            if (newVersion) {
                FileVersionEntity tmpFileVersion = this.fileRepository.findLatestByFileId(file.getId(), packageFile);

                if (tmpFileVersion != null) {
                    // 获取最新的release版本
                    FileVersionEntity latestVersion = this.fileRepository.findLatestReleaseByFilePathFull(path);

                    String tmpOldVersionNum = tmpFileVersion.getVersionNum();
                    tmpFileVersion = VersionUtils.incrementVersionFileVersion(latestVersion, tmpFileVersion);
                    if (packageFile && !SNAPSHOT_VERSION.equals(tmpOldVersionNum)) {
                        tmpFileVersion.setId(null);
                        tmpFileVersion.setFileContent(null);
                        tmpFileVersion.setAfterComment(null);
                        tmpFileVersion.setCreateUser(user.getUsername());
                        tmpFileVersion.setCreateDate(calendar.getTime());
                        tmpFileVersion.setUpdateTime(null);
                        this.fileRepository.insert(tmpFileVersion);
                    } else {
                        this.fileRepository.updateVersionNumAndReal(tmpFileVersion.getId(), tmpFileVersion.getVersionNum(), tmpFileVersion.getVersionNumReal(), tmpFileVersion.getProjectVersionNumReal());
                    }
                    versionNum = tmpFileVersion.getVersionNum();
                } else {
                    // 获取最新的release版本
                    FileVersionEntity latestVersion = this.fileRepository.findLatestReleaseByFilePathFull(path);

                    // Content diff not stored in DB — Git is the content source
                    String contentDiff = "";
                    log.info("\n{}", contentDiff);

                    tmpFileVersion = new FileVersionEntity();
                    tmpFileVersion = VersionUtils.incrementVersionFileVersion(latestVersion, tmpFileVersion);
                    tmpFileVersion.setFileId(file.getId());
                    tmpFileVersion.setFileName(file.getFilePath());
                    tmpFileVersion.setFilePath(file.getFilePath());
                    tmpFileVersion.setFileContent(null);
                    tmpFileVersion.setProjectId(file.getProjectId());
                    tmpFileVersion.setAfterComment(null);
                    tmpFileVersion.setCreateUser(user.getUsername());
                    tmpFileVersion.setCreateDate(calendar.getTime());
                    tmpFileVersion.setUpdateTime(null);
                    tmpFileVersion.setProjectVersionNumReal(SNAPSHOT_VERSION_REAL);
                    this.fileRepository.insert(tmpFileVersion);
                    versionNum = tmpFileVersion.getVersionNum();
                }
            } else {
                // 对比差异 — content diff not stored in DB when Git is source
                String contentDiff = "";
                log.info("\n{}", contentDiff);

                String targetVersionNum = (file.getProjectId() == 0) ? "latest" : SNAPSHOT_VERSION;
                FileVersionEntity existingVersion = this.fileRepository.findByFileIdAndVersionNum(file.getId(), targetVersionNum);
                if (existingVersion != null) {
                    this.fileRepository.updateVersionNumAndReal(existingVersion.getId(), existingVersion.getVersionNum(), existingVersion.getVersionNumReal(), existingVersion.getProjectVersionNumReal());
                } else {
                    FileVersionEntity newFile = new FileVersionEntity();
                    newFile.setFileId(file.getId());
                    newFile.setFilePath(file.getFilePath());
                    newFile.setFileName(file.getName());
                    newFile.setProjectId(file.getProjectId());
                    newFile.setFileContent(null);
                    newFile.setAfterComment(null);
                    newFile.setVersionNum(SNAPSHOT_VERSION);
                    newFile.setVersionNumReal(SNAPSHOT_VERSION_REAL);
                    newFile.setProjectVersionNumReal(SNAPSHOT_VERSION_REAL);
                    newFile.setCreateUser(user.getUsername());
                    newFile.setCreateDate(calendar.getTime());
                    this.fileRepository.insert(newFile);
                }
                versionNum = SNAPSHOT_VERSION;
            }
        } finally {
            unlockPath(path, user, lockVersion);
        }

        this.repositoryInterceptor.saveFile(path, content);

        // Write to Git — this is the primary content store
        String gitCommitSha = dualWriteToGit(path, content, user != null ? user.getUsername() : null);

        // Record git commit SHA in DB metadata
        if (gitCommitSha != null && versionNum != null) {
            try {
                this.fileRepository.updateGitCommitSha(path, versionNum, gitCommitSha);
            } catch (Exception e) {
                log.debug("Failed to update gitCommitSha for [{}]: {}", path, e.getMessage());
            }
        }

        return versionNum;
    }

    @Override
    public List<RefFile> getFlowRefs(List<String> pathList) {
        // 遍历项目
        List<FileEntity> fileEntityList = this.fileRepository.findByFilePathIn(pathList);

        List<RefFile> repositoryFileList = new ArrayList<>(fileEntityList.size());
        for (FileEntity fileEntity : fileEntityList) {
            RefFile refFile = new RefFile();
            refFile.setName(fileEntity.getName());
            refFile.setPath(fileEntity.getFilePath());
            refFile.setVersion("LATEST");
            // todo
            List<String> versionList = new ArrayList<>(25);
            List<FileVersionEntity> fileVersionEntityList = this.fileRepository.findVersionsByFilePath(fileEntity.getFilePath(), true, 1, 25, true);
            for (FileVersionEntity fileVersion : fileVersionEntityList) {
                versionList.add(fileVersion.getVersionNum());
            }
            refFile.setVersionHistory(versionList);
            if (fileEntity.getFilePath().endsWith(FileType.Ruleset.toString())) {
                refFile.setType("决策集");
            } else if (fileEntity.getFilePath().endsWith(FileType.UL.toString())) {
                refFile.setType("脚本决策集");
            } else if (fileEntity.getFilePath().endsWith(FileType.DecisionTable.toString())) {
                refFile.setType("决策表");
            } else if (fileEntity.getFilePath().endsWith(FileType.ScriptDecisionTable.toString())) {
                refFile.setType("脚本决策表");
            } else if (fileEntity.getFilePath().endsWith(FileType.DecisionTree.toString())) {
                refFile.setType("决策树");
            } else if (fileEntity.getFilePath().endsWith(FileType.RuleFlow.toString())) {
                refFile.setType("决策流");
            } else if (fileEntity.getFilePath().endsWith(FileType.Scorecard.toString())) {
                refFile.setType("评分卡");
            } else if (fileEntity.getFilePath().endsWith(FileType.ComplexScorecard.toString())) {
                refFile.setType("复杂评分卡");
            }

            repositoryFileList.add(refFile);
        }
        return repositoryFileList;
    }

    @Override
    public String getPackageVersionDiff(String project, String version) {
        // Git-based diff: compare current version tag with the previous version tag
        if (gitStorageService.repoExists(project)) {
            try {
                // Find previous version
                ProjectEntity projectEntity = this.projectRepository.findByName(project);
                if (projectEntity != null) {
                    ProjectVersionEntity currentPVE = this.projectRepository.findVersionByProjectIdAndVersionName(projectEntity.getId(), version);
                    if (currentPVE != null) {
                        // Find previous version
                        ProjectVersionEntity prevPVE = this.projectRepository.findPreviousVersion(projectEntity.getId(), currentPVE.getVersionNumReal());

                        String packageId = currentPVE.getPackageId();
                        if (packageId != null) {
                            String currentTag = "pkg/" + packageId + "/" + version;
                            String prevTag = prevPVE != null ? "pkg/" + packageId + "/" + prevPVE.getVersionName() : null;

                            if (prevTag != null) {
                                List<com.ruleforge.console.storage.model.FileDiff> diffs =
                                        gitStorageService.diff(project, prevTag, currentTag);
                                StringBuilder sb = new StringBuilder();
                                for (com.ruleforge.console.storage.model.FileDiff d : diffs) {
                                    sb.append(d.getFilePath()).append(" [").append(d.getDiffType()).append("]\n");
                                    if (d.getPatch() != null) {
                                        sb.append(d.getPatch()).append("\n");
                                    }
                                    sb.append("\n");
                                }
                                return sb.toString();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Git diff failed, falling back to DB: {}", e.getMessage());
            }
        }

        // Fallback to DB-based diff
        try {
            ProjectEntity projectEntity = this.projectRepository.findByName(project);
            List<FileVersionEntity> fileVersionEntityList = this.fileRepository.findByProjectIdAndProjectVersionNumReal(projectEntity.getId(), VersionUtils.convertVersionToLong(version));
            StringBuilder sb = new StringBuilder();
            for (FileVersionEntity fileVersion : fileVersionEntityList) {
                sb.append(fileVersion.getFilePath()).append("\n")
                        .append(fileVersion.getAfterComment()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("getPackageVersionDiff", e);
        }

        return null;
    }

    @Override
    public String getFileVersionDiff(String filePath, String targetVersion) {
        FileVersionEntity fileVersion = this.fileRepository.findLatestReleaseByFilePathFull(filePath);
        return fileVersion.getFilePath() + "\n" +
                fileVersion.getAfterComment() + "\n\n";
    }

    @Override
    public List<com.ruleforge.console.storage.model.FileDiff> getPackageVersionDiffStructured(String project, String fromVersion, String toVersion) throws Exception {
        if (!gitStorageService.repoExists(project)) {
            throw new IllegalStateException("Git repository not found for project: " + project);
        }

        // Resolve package ID from the toVersion by looking up the project version entity
        ProjectEntity projectEntity = this.projectRepository.findByName(project);
        if (projectEntity == null) {
            throw new IllegalArgumentException("Project not found: " + project);
        }

        ProjectVersionEntity toPVE = this.projectRepository.findVersionByProjectIdAndVersionName(projectEntity.getId(), toVersion);
        if (toPVE == null) {
            throw new IllegalArgumentException("Target version not found: " + toVersion);
        }

        String packageId = toPVE.getPackageId();
        if (packageId == null) {
            throw new IllegalStateException("Package ID not found for version: " + toVersion);
        }

        String fromTag = "pkg/" + packageId + "/" + fromVersion;
        String toTag = "pkg/" + packageId + "/" + toVersion;

        return gitStorageService.diff(project, fromTag, toTag);
    }

    @Override
    public com.ruleforge.console.storage.model.FileDiff getFileVersionDiffStructured(String filePath, String fromVersion, String toVersion) throws Exception {
        // Extract project name from the file path (first path segment)
        String project = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        int slashIdx = project.indexOf('/');
        if (slashIdx > 0) {
            project = project.substring(0, slashIdx);
        }

        if (!gitStorageService.repoExists(project)) {
            throw new IllegalStateException("Git repository not found for project: " + project);
        }

        // Resolve package ID from the toVersion
        ProjectEntity projectEntity = this.projectRepository.findByName(project);
        if (projectEntity == null) {
            throw new IllegalArgumentException("Project not found: " + project);
        }

        ProjectVersionEntity toPVE = this.projectRepository.findVersionByProjectIdAndVersionName(projectEntity.getId(), toVersion);
        if (toPVE == null) {
            throw new IllegalArgumentException("Target version not found: " + toVersion);
        }

        String packageId = toPVE.getPackageId();
        if (packageId == null) {
            throw new IllegalStateException("Package ID not found for version: " + toVersion);
        }

        String fromTag = "pkg/" + packageId + "/" + fromVersion;
        String toTag = "pkg/" + packageId + "/" + toVersion;

        List<com.ruleforge.console.storage.model.FileDiff> diffs = gitStorageService.diff(project, fromTag, toTag);

        // Normalize the file path for comparison (remove leading slash)
        String normalizedPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;

        for (com.ruleforge.console.storage.model.FileDiff d : diffs) {
            if (normalizedPath.equals(d.getFilePath())) {
                return d;
            }
        }

        return null;
    }

    @Override
    public void deleteFile(String path, User user) throws Exception {
        deleteFile(path, user, null);
    }

    @Override
    public void deleteFile(String path, User user, Type type) throws Exception {
        // 获取所有file
        FileEntity file = this.fileRepository.findByFilePathWithType(path, type != null ? type.ordinal() : null);
        if (file == null) {
            return;
        }

        long fileId = file.getId();

        // 判断下级是否为空，为空则终止删除
        List<FileRelationEntity> relationFileList = this.fileRepository.findRelationsByAncestor(fileId);
        if (!relationFileList.isEmpty()) {
            return;
        }

        // 删除relation
        this.fileRepository.deleteRelationsByDescendant(fileId);

        // 删除file
        this.fileRepository.deleteFileById(fileId);

        // 删除file version
        this.fileRepository.deleteFileVersionsByFileId(fileId);

        this.repositoryInterceptor.deleteFile(path);
        dualDeleteFromGit(path);
    }

    @Override
    public void deleteProject(String projectName, User user) throws Exception {
        // 获取所有file
        ProjectEntity project = this.projectRepository.findByNameSelectId(projectName);
        if (project == null) {
            return;
        }
        long projectId = project.getId();

        // 删除project
        this.projectRepository.deleteByName(projectName);

        // 删除relation
        this.fileRepository.deleteRelationsByProjectId(projectId);

        // 删除file
        this.fileRepository.deleteFilesByIdOrProjectId(projectId);

        // 删除file version
        this.fileRepository.deleteFileVersionsByProjectId(projectId);

        // 删除project version
        this.projectRepository.deleteVersionsByProjectId(projectId);

        // 删除project version mapping
        this.projectRepository.deleteMappingsByProjectId(projectId);

        this.repositoryInterceptor.deleteFile("/" + projectName);
    }

    @Override
    public Long lockPath(String project, User user) throws Exception {
        // For package files, lock at package level instead of file level
        String lockResource = project;
        if (project.contains(RES_PACKAGE_FILE)) {
            // Extract project + package path for package-level locking
            // e.g., "/projectA/资源包.rp/pkg1" → "/projectA/资源包.rp"
            int pkgIdx = project.indexOf(RES_PACKAGE_FILE);
            int nextSlash = project.indexOf('/', pkgIdx + RES_PACKAGE_FILE.length());
            if (nextSlash > 0) {
                lockResource = project.substring(0, nextSlash);
            }
        }

        LockEntity lockEntity = this.lockRepository.findByResource(lockResource);
        if (lockEntity != null) {
            return null;
        }

        lockEntity = new LockEntity();
        lockEntity.setLockResource(lockResource);
        lockEntity.setCreateTime(new Date());
        return this.lockRepository.insert(lockEntity).getId();
    }

    @Override
    public boolean unlockPath(String project, User user, Long versionNum) throws Exception {
        return this.lockRepository.deleteById(versionNum);
    }

    @Override
    public Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName) throws Exception {
        return loadRepository(project, user, classify, types, searchFileName, true);
    }

    @Override
    public Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName, Boolean detailed) throws Exception {
        if (project != null && project.startsWith("/")) {
            project = project.substring(1);
        }
        Repository repo = new Repository();

        // 遍历项目
        List<ProjectEntity> projectEntityList;
        if (!StringUtils.isEmpty(project)) {
            ProjectEntity pe = this.projectRepository.findByName(project);
            projectEntityList = pe != null ? List.of(pe) : List.of();
        } else {
            projectEntityList = this.projectRepository.findAll();
        }
        if (projectEntityList != null) {
            List<String> projectNames = new ArrayList<>(projectEntityList.size());
            RepositoryFile rootFile = new RepositoryFile();
            rootFile.setFullPath("/");
            rootFile.setName("项目列表");
            rootFile.setType(Type.root);
            for (ProjectEntity file : projectEntityList) {
                try {
                    projectNames.add(file.getName());
                    RepositoryFile projectFile;
                    if (detailed) {
                        projectFile = buildProjectFile(file, types, classify, searchFileName);
                    } else {
                        projectFile = new RepositoryFile();
                        projectFile.setType(Type.project);
                        projectFile.setName(file.getName());
                        projectFile.setFullPath("/" + file.getName());
                    }
                    rootFile.addChild(projectFile, false);
                } catch (Exception e) {
                    log.error("loadRepository projectEntityList.forEach", e);
                }
            }
            repo.setRootFile(rootFile);

            // 添加公共资源
            RepositoryFile publicResourceFile = new RepositoryFile();
            publicResourceFile.setFullPath("/__public__");
            publicResourceFile.setName("公共资源");
            publicResourceFile.setType(Type.folder);
            RepositoryFile subLib = new RepositoryFile();
            subLib.setFullPath("/__public__");
            subLib.setName("库");
            subLib.setLibType(LibType.res);
            subLib.setType(Type.lib);
            FileType[] librarySubTypes = types;
            if (types == null || types.length == 0) {
                librarySubTypes = new FileType[]{FileType.VariableLibrary, FileType.ParameterLibrary, FileType.ConstantLibrary, FileType.ActionLibrary};
            }
            FileEntity fileEntity = new FileEntity();
            fileEntity.setId(0L);
            buildNodes(fileEntity, subLib, librarySubTypes, Type.lib, null);
            publicResourceFile.setChildren(subLib.getChildren());
            // 插入默认公共资源
            repo.setPublicResource(publicResourceFile);
            repo.setProjectNames(projectNames);
        }

        return repo;
    }

    @Override
    public void fileRename(String path, String newPath) throws Exception {
    }

    @Override
    public List<String> getReferenceFiles(String targetProject, String path, String searchText, String searchTextScript) throws Exception {
        List<String> referenceFiles = new ArrayList<>();

        List<ProjectEntity> projectEntityList = this.projectRepository.findByIdGreaterThanZero(
                org.springframework.util.StringUtils.hasText(targetProject) ? targetProject : null);

        for (ProjectEntity projectEntity : projectEntityList) {
            List<FileEntity> fileEntityList = this.fileRepository.findByProjectIdExcludingTypes(projectEntity.getId(),
                    Type.resourcePackage.ordinal(), Type.packageConfig.ordinal());

            List<Long> fileIdList = new ArrayList<>();
            for (FileEntity fileEntity : fileEntityList) {
                fileIdList.add(fileEntity.getId());
            }

            List<FileVersionEntity> fileVersionEntityList = this.fileRepository.selectLatestVersionByFileIds(fileIdList);
            for (FileVersionEntity fileVersionEntity : fileVersionEntityList) {
                String content = fileVersionEntity.getFileContent();
                boolean containPath = content.contains(path);
                boolean containText = content.contains(searchText);
                boolean containScriptText = content.contains(searchTextScript);
                if ((containPath && containText)
                        || (containPath && containScriptText)) {
                    referenceFiles.add(fileVersionEntity.getFilePath());
                }
            }
        }

        return referenceFiles;
    }

    @Override
    public InputStream readFile(String path, String version) throws Exception {
        return readFile(path, version, null, true);
    }

    @Override
    public InputStream readFile(String path, String version, String projectVersion) throws Exception {
        return readFile(path, version, projectVersion, true);
    }

    @Override
    public InputStream readFile(String path, String version, String projectVersion, boolean containSnapshot) throws Exception {
        path = Utils.decodeURL(path);
        String[] pathArray = path.split(":");
        if (pathArray.length > 1) {
            path = pathArray[0];
            version = pathArray[1];
        }

        // Git-first read: try Git before falling back to DB
        InputStream gitStream = tryReadFromGit(path, version, projectVersion, containSnapshot);
        if (gitStream != null) {
            return gitStream;
        }
        // Fall through to DB read

        FileVersionEntity fileVersionEntity = this.fileRepository.findByFilePathForRead(path,
                (version != null && !version.equalsIgnoreCase("latest")) ? version : null,
                projectVersion, containSnapshot);
        if (fileVersionEntity != null && fileVersionEntity.getFileContent() != null) {
            log.info(String.format("readFile path: %s, project version: %s, input version: %s, real version: %s containSnapshot：%s", path, projectVersion, version, fileVersionEntity.getVersionNum(), containSnapshot));
            return IOUtils.toInputStream(fileVersionEntity.getFileContent(), StandardCharsets.UTF_8);
        } else {
            // V5.9.x: Git 不可用 + saveFile 不存 content 到 DB (内容走 Git) 时, fileVersionEntity 找到但 content 为 null
            // 之前会 NPE 跳到 GlobalExceptionHandler 返 400。改返 null 让 caller 决定走 404 (跟 notFound.xml 一致)
            if (fileVersionEntity != null) {
                log.info(String.format("readFile path: %s, version: %s found but fileContent is null (Git-first storage fallback), return null", path, version));
            } else {
                log.info(String.format("readFile none path: %s, input version: %s, containSnapshot：%s", path, version, containSnapshot));
            }
            return null;
        }
    }

    /**
     * Try to read a file from Git storage.
     * Returns null if Git read is not possible (no repo, no tag, etc.).
     */
    private InputStream tryReadFromGit(String path, String version, String projectVersion, boolean containSnapshot) {
        try {
            String projectName = extractProjectName(path);
            if (projectName == null || !gitStorageService.repoExists(projectName)) {
                return null;
            }

            String gitPath = path.startsWith("/") ? path.substring(1) : path;
            String revision = null;

            if (org.springframework.util.StringUtils.hasText(projectVersion)) {
                // Try package version tag: pkg/{packageId}/{version}
                // For now, use the projectVersion as a tag directly
                revision = projectVersion;
            } else if (org.springframework.util.StringUtils.hasText(version)
                    && !version.equalsIgnoreCase("latest") && !version.equals(SNAPSHOT_VERSION)) {
                // Try specific file version as a tag
                revision = version;
            }

            if (revision != null) {
                InputStream stream = gitStorageService.readFileStream(projectName, revision, gitPath);
                if (stream != null) {
                    log.debug("Read file [{}] from Git at revision [{}]", path, revision);
                    return stream;
                }
            }

            // For "latest" or snapshot — read from user branch or main
            String branch = BranchContext.getBranch() != null ? BranchContext.getBranch() : "main";
            InputStream stream = gitStorageService.readFileStream(projectName, branch, gitPath);
            if (stream != null) {
                log.debug("Read file [{}] from Git on branch [{}]", path, branch);
            }
            return stream;
        } catch (Exception e) {
            log.debug("Git read failed for [{}], falling back to DB: {}", path, e.getMessage());
            return null;
        }
    }

    @Override
    public VersionFile loadFileProperty(String path, String version) throws Exception {
        path = processPath(path);
        FileVersionEntity fileVersionEntity = this.fileRepository.findByFilePathAndVersion(path, version);

        VersionFile versionFile = new VersionFile();
        versionFile.setName(fileVersionEntity.getFileName());
        versionFile.setPath(path);
        versionFile.setComment(fileVersionEntity.getFileComment());
        versionFile.setBeforeComment(fileVersionEntity.getBeforeComment());
        versionFile.setAfterComment(fileVersionEntity.getAfterComment());
        return versionFile;
    }

    @Override
    public List<VersionFile> getVersionFiles(String path) throws Exception {
        return getVersionFiles(path, false, 0, 0, false, false);
    }

    @Override
    public List<VersionFile> getVersionFiles(String path, boolean desc, int page, int row, boolean containContent, boolean containLatest) throws Exception {
        path = processPath(path);
        FileEntity fileEntity = this.fileRepository.findByFilePath(path);
        if (fileEntity == null) {
            throw new RuleException("File [" + path + "] not exist.");
        }

        List<VersionFile> files = new ArrayList<>();
        List<FileVersionEntity> versionEntityList = this.fileRepository.findVersionsByFilePath(path, desc, page, row, containLatest);

        versionEntityList.forEach(version -> {
            String versionName = version.getVersionNum();
            if (versionName == null || versionName.isEmpty()) {
                return;
            }

            VersionFile file = new VersionFile();
            file.setName(versionName);
            file.setVersionNumReal(version.getVersionNumReal());
            file.setProjectVersionNumReal(version.getProjectVersionNumReal());
            file.setPath(version.getFilePath());
            file.setCreateUser(version.getCreateUser());
            file.setCreateDate(version.getCreateDate());
            file.setComment(version.getFileComment());
            file.setBeforeComment(version.getBeforeComment());
            file.setAfterComment(version.getAfterComment());
            file.setProjectId(fileEntity.getProjectId());
            if (containContent) {
                file.setContent(version.getFileContent());
            }

            files.add(file);
        });

        return files;
    }

    @Override
    public Long countVersionFiles(String path) throws Exception {
        path = processPath(path);
        FileEntity fileEntity = this.fileRepository.findByFilePath(path);
        if (fileEntity == null) {
            throw new RuleException("File [" + path + "] not exist.");
        }

        return this.fileRepository.countVersionsByFilePath(path);
    }

    @Override
    public Long importFromZip(User user, MultipartFile importFile, RepositoryFile repositoryFile, Map<String, ExportProject> exportProjectMap, Boolean loadLatest) throws Exception {
        // 插入项目
        ProjectEntity project = new ProjectEntity();
        project.setName(repositoryFile.getName());
        project.setCreateTime(new Date());
        this.projectRepository.insert(project);

        Map<String, Long> fileIdMap = importFile(repositoryFile.getChildren(), Lists.newArrayList(project.getId()), loadLatest, false);

        // 处理版本
        for (String file : exportProjectMap.keySet()) {
            ExportProject exportProject = exportProjectMap.get(file);
            int size = exportProject.getVersionFileMap().size();
            // 获取最近内容
            String filePath = null;
            for (int i = 0; i < size; i++) {
                VersionFile versionFile = exportProject.getVersionFileMap().get(String.valueOf(i));
                filePath = versionFile.getPath();
                break;
            }

            Long fileId = fileIdMap.get(filePath);
            List<VersionFile> versionFileList = new ArrayList<>(exportProject.getVersionFileMap().values());
            saveVersionFileList(file, filePath, project.getId(), versionFileList, fileId);
            log.debug("version file path: {}", filePath);
            Thread.sleep(10);
        }

        return project.getId();
    }

    /**
     * V5.24: 自动建 parent folder chain。
     *
     * <p>给定一个不存在的 parent path(形如 {@code /project/a/b/c}),
     * 递归向上找第一个存在的 ancestor(项目根目录或中间 folder),
     * 然后从那里向下逐层建 folder。已存在则跳过(idempotent)。
     *
     * <p>不在 createFileNode 内部直接递归的原因:createFileNode 有
     * "已存在 → 抛 RuleException" 的语义,folder 递归建需要 idempotent
     * 语义(并发场景下另一个请求可能已经建好了)。所以单独抽
     * ensureParentFolders + idempotent 文件存在检查。
     */
    private FileEntity ensureParentFolders(String parentPath, User user) throws Exception {
        if (parentPath == null || parentPath.isEmpty() || !parentPath.contains("/")) {
            // 已到 project root,这种情况是 bug,父链断了
            throw new RuleException("Cannot resolve ancestor for path: " + parentPath);
        }
        FileEntity existing = this.fileRepository.findByFilePathNeType(parentPath, Type.project.ordinal());
        if (existing != null) {
            return existing; // 已存在,跳过
        }
        // 递归向上(先建祖先,再建自己)
        String ancestor = parentPath.substring(0, parentPath.lastIndexOf("/"));
        FileEntity ancestorFile;
        if (!ancestor.isEmpty() && ancestor.contains("/")) {
            ancestorFile = ensureParentFolders(ancestor, user);
        } else {
            // ancestor 是 project root(如 "/proj")
            ancestorFile = this.fileRepository.findByFilePathNeType(ancestor, Type.project.ordinal());
            if (ancestorFile == null) {
                throw new RuleException("Cannot resolve ancestor: " + ancestor);
            }
        }
        // 跳过再次 race(并发场景)
        existing = this.fileRepository.findByFilePathNeType(parentPath, Type.project.ordinal());
        if (existing != null) {
            return existing;
        }
        long projectId = ancestorFile.getProjectId();
        String folderName = parentPath.substring(parentPath.lastIndexOf("/") + 1);

        FileEntity folder = new FileEntity();
        folder.setName(folderName);
        folder.setFileType(Type.folder.ordinal());
        folder.setProjectId(projectId);
        folder.setFilePath(parentPath);
        folder.setCreateTime(new Date());
        this.fileRepository.insert(folder);

        List<FileRelationEntity> parentRelationList = this.fileRepository.findRelationsByDescendant(ancestorFile.getId());
        List<FileRelationEntity> fileRelationList = new ArrayList<>(parentRelationList.size() + 1);
        parentRelationList.forEach(parentRelation -> {
            FileRelationEntity fileRelation = new FileRelationEntity();
            fileRelation.setAncestor(parentRelation.getAncestor());
            fileRelation.setDescendant(folder.getId());
            fileRelation.setDistance(parentRelation.getDistance() + 1);
            fileRelation.setProjectId(projectId);
            fileRelationList.add(fileRelation);
        });
        FileRelationEntity fileRelation = new FileRelationEntity();
        fileRelation.setAncestor(ancestorFile.getId());
        fileRelation.setDescendant(folder.getId());
        fileRelation.setDistance(1);
        fileRelation.setProjectId(projectId);
        fileRelationList.add(fileRelation);
        this.fileRepository.batchInsertRelations(fileRelationList);

        this.repositoryInterceptor.createFile(parentPath, null);
        return folder; // 把刚建的 folder 直接返给 caller(避免再 query)
    }

    private void createFileNode(String path, String content, User user, boolean isFile) throws Exception {
        path = processPath(path);
        try {
            if (fileExistCheck(path)) {
                throw new RuleException("File [" + path + "] already exist.");
            }

            String parentPath = path.substring(0, path.lastIndexOf("/"));
            FileEntity parentFile = this.fileRepository.findByFilePathNeType(parentPath, Type.project.ordinal());
            // V5.24: 自动建 parent folder(沿用 uruleV1 JCR-style 体验)。
            // parent 不存在 → 递归向上建 folder,直到 hit project root。
            // ensureParentFolders 内部走权限校验,避免循环触发 NPE。
            if (parentFile == null) {
                parentFile = ensureParentFolders(parentPath, user);
            }

            String fileName = path.substring(path.lastIndexOf("/") + 1);
            String createUser = user.getUsername();
            long projectId = parentFile.getProjectId();

            FileEntity file = new FileEntity();
            file.setName(fileName);
            Type type = FileTypeUtils.mapFileNameToType(fileName);
            if (type != null) {
                file.setFileType(type.ordinal());
            } else if (!isFile) {
                file.setFileType(Type.folder.ordinal());
            } else {
                file.setFileType(-127);
            }
            file.setProjectId(projectId);
            file.setFilePath(path);
            file.setCreateTime(new Date());
            this.fileRepository.insert(file);

            if (isFile) {
                FileVersionEntity fileVersionEntity = new FileVersionEntity();
                fileVersionEntity.setFileId(file.getId());
                fileVersionEntity.setFilePath(path);
                fileVersionEntity.setFileName(fileName);
                fileVersionEntity.setFileContent(content);
                fileVersionEntity.setVersionNum("latest");
                fileVersionEntity.setVersionNumReal(1L);
                fileVersionEntity.setProjectId(projectId);
                fileVersionEntity.setCreateUser(createUser);
                fileVersionEntity.setCreateDate(new Date());
                this.fileRepository.insert(fileVersionEntity);
            }

            List<FileRelationEntity> parentRelationList = this.fileRepository.findRelationsByDescendant(parentFile.getId());
            List<FileRelationEntity> fileRelationList = new ArrayList<>(parentRelationList.size() + 1);
            parentRelationList.forEach(parentRelation -> {
                FileRelationEntity fileRelation = new FileRelationEntity();
                fileRelation.setAncestor(parentRelation.getAncestor());
                fileRelation.setDescendant(file.getId());
                fileRelation.setDistance(parentRelation.getDistance() + 1);
                fileRelation.setProjectId(projectId);
                fileRelationList.add(fileRelation);
            });
            FileRelationEntity fileRelation = new FileRelationEntity();
            fileRelation.setAncestor(parentFile.getId());
            fileRelation.setDescendant(file.getId());
            fileRelation.setDistance(1);
            fileRelation.setProjectId(projectId);
            fileRelationList.add(fileRelation);
            this.fileRepository.batchInsertRelations(fileRelationList);

            this.repositoryInterceptor.createFile(path, content);
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }


    private void lockCheck(FileEntity file, User user) throws Exception {
//        if (lockManager.isLocked(node.getPath())) {
//            String lockOwner = lockManager.getLock(node.getPath()).getLockOwner();
//            if (lockOwner.equals(user.getUsername())) {
//                return;
//            }
//    }
//        throw new NodeLockException("【" + file.getName() + "】已被" + "lockOwner" + "锁定!");
    }

    private Map<String, Long> importFile(List<RepositoryFile> repositoryFileList, List<Long> parentIdList, Boolean loadLatest, Boolean loadRemoteVersionFile) {
        if (repositoryFileList == null || repositoryFileList.isEmpty()) {
            return null;
        }

        Map<String, Long> fileIdMap = new HashMap<>();
        repositoryFileList.forEach(repositoryFile -> {
            List<Long> parentIdListCopy = Lists.newCopyOnWriteArrayList(parentIdList);

            FileEntity file = new FileEntity();
            file.setName(repositoryFile.getName());
            file.setFilePath(repositoryFile.getFullPath());
            file.setFileType(repositoryFile.getType().ordinal());
            file.setProjectId(parentIdListCopy.get(0));
            this.fileRepository.insert(file);

            // 记录文件ID
            fileIdMap.put(file.getFilePath(), file.getId());

            if (!Arrays.asList(Type.folder, Type.all).contains(repositoryFile.getType())
                    && loadLatest) {
                if (loadRemoteVersionFile) {
                    // todo
//                    syncVersionFileListFromRemote(repositoryFile, parentIdListCopy.get(0));
                } else {
                    syncVersionFileLatestFromLocal(repositoryFile, parentIdListCopy.get(0));
                }
            }

            // 插入关系表
            List<FileRelationEntity> relationList = new ArrayList<>(parentIdList.size());
            for (int i = 0; i < parentIdListCopy.size(); i++) {
                FileRelationEntity relation = new FileRelationEntity();
                relation.setProjectId(parentIdList.get(0));
                relation.setAncestor(parentIdListCopy.get(i));
                relation.setDescendant(file.getId());
                relation.setDistance(parentIdListCopy.size() - i);
                relationList.add(relation);
            }
            this.fileRepository.batchInsertRelations(relationList);

            parentIdListCopy.add(file.getId());
            Map<String, Long> fileIdMapChild = importFile(repositoryFile.getChildren(), parentIdListCopy, loadLatest, loadRemoteVersionFile);
            if (fileIdMapChild != null) {
                fileIdMap.putAll(fileIdMapChild);
            }
        });

        return fileIdMap;
    }

    protected String processPath(String path) {
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        return path;
    }

    /**
     * Extract project name from a path like "/projectName/folder/file.xml".
     * Returns the first path segment.
     *
     * V5.11: 委托给 GitPathUtils.extractProjectName,与 FrameController 共用.
     */
    private String extractProjectName(String path) {
        return com.ruleforge.console.util.GitPathUtils.extractProjectName(path);
    }

    /**
     * Dual-write a file to Git. Called after successful DB write.
     * Uses per-user branch if BranchContext is set, otherwise writes to main.
     * Non-blocking: logs errors but does not throw.
     *
     * V5.10-C: also records Micrometer counter (success/failure by errorType)
     * + persists a row in gr_git_dualwrite_failure when JGit throws.
     *
     * @return the Git commit SHA, or null if Git write was skipped/failed
     */
    private String dualWriteToGit(String path, String content, String author) {
        String branch = resolveBranch(author);
        try {
            String projectName = extractProjectName(path);
            if (projectName == null || !gitStorageService.repoExists(projectName)) return null;

            String gitPath = path.startsWith("/") ? path.substring(1) : path;
            String canonical = xmlCanonicalizer.canonicalize(content);

            // Ensure branch exists (create from main if needed)
            if (!"main".equals(branch)) {
                try {
                    gitStorageService.createBranch(projectName, branch, "main");
                } catch (GitOperationException ignored) {
                    // Branch may already exist, that's fine
                }
            }

            gitStorageService.writeFile(projectName, branch, gitPath, canonical);
            String commitSha = gitStorageService.commit(projectName, branch,
                    "Save: " + path, author != null ? author : "system");
            gitStorageService.push(projectName);
            log.debug("Dual-write to Git succeeded: {} on branch {} (sha={})", path, branch, commitSha);
            recordDualwriteSuccess("save");
            return commitSha;
        } catch (GitOperationException e) {
            log.error("Git dual-write failed (DB write succeeded): {}", path, e);
            recordDualwriteFailure("save", path, extractProjectName(path), null, branch, e);
            return null;
        } catch (RuntimeException e) {
            log.error("Git dual-write unexpected failure (DB write succeeded): {}", path, e);
            recordDualwriteFailure("save", path, extractProjectName(path), null, branch, e);
            return null;
        }
    }

    /**
     * Dual-delete a file from Git. Called after successful DB delete.
     * Uses BranchContext branch if set, otherwise main.
     * Non-blocking: logs errors but does not throw.
     */
    private void dualDeleteFromGit(String path) {
        String branch = resolveBranch(null);
        try {
            String projectName = extractProjectName(path);
            if (projectName == null || !gitStorageService.repoExists(projectName)) return;

            String gitPath = path.startsWith("/") ? path.substring(1) : path;
            gitStorageService.deleteFile(projectName, branch, gitPath);
            gitStorageService.commit(projectName, branch, "Delete: " + path, "system");
            gitStorageService.push(projectName);
            log.debug("Dual-delete from Git succeeded: {} on branch {}", path, branch);
            recordDualDeleteSuccess();
        } catch (GitOperationException e) {
            log.error("Git dual-delete failed (DB delete succeeded): {}", path, e);
            recordDualwriteFailure("delete", path, extractProjectName(path), null, branch, e);
        } catch (RuntimeException e) {
            log.error("Git dual-delete unexpected failure (DB delete succeeded): {}", path, e);
            recordDualwriteFailure("delete", path, extractProjectName(path), null, branch, e);
        }
    }

    /** V5.10-C: branch 选择逻辑提到方法级,save/delete/failure-record 共享 */
    private String resolveBranch(String author) {
        String branch = BranchContext.getBranch();
        if (branch == null && author != null) {
            branch = BranchContext.forUser(author);
        }
        if (branch == null) {
            branch = "main";
        }
        return branch;
    }

    // ----- V5.10-C observability helpers -----

    private void recordDualwriteSuccess(String op) {
        Counter.builder(DUALWRITE_COUNTER)
                .tag("op", op)
                .tag("result", "success")
                .register(meterRegistry)
                .increment();
    }

    private void recordDualDeleteSuccess() {
        Counter.builder(DUALDELETE_COUNTER)
                .tag("result", "success")
                .register(meterRegistry)
                .increment();
    }

    private void recordDualwriteFailure(String op, String path, String projectName, Long fileId,
                                        String branch, Throwable t) {
        String errorType = t == null ? "Unknown" : t.getClass().getSimpleName();
        Counter.builder(DUALWRITE_COUNTER)
                .tag("op", op)
                .tag("result", "failure")
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();
        if (op.equals("delete")) {
            Counter.builder(DUALDELETE_COUNTER)
                    .tag("result", "failure")
                    .tag("error_type", errorType)
                    .register(meterRegistry)
                    .increment();
        }
        try {
            GitDualwriteFailureEntity row = new GitDualwriteFailureEntity();
            row.setFilePath(path);
            row.setProjectId(resolveProjectId(projectName));
            row.setFileId(fileId);
            row.setErrorType(errorType);
            row.setErrorMessage(truncate(t == null ? "" : safeMessage(t), 2048));
            row.setBranch(branch);
            dualwriteFailureRepository.insert(row);
        } catch (RuntimeException dbErr) {
            // 失败行的写入也不能让上层感知,只能 log.
            log.error("Failed to persist dualwrite failure row for {}", path, dbErr);
        }
    }

    private Long resolveProjectId(String projectName) {
        if (projectName == null) return null;
        try {
            ProjectEntity p = projectRepository.findByName(projectName);
            return p == null ? null : p.getId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null) m = t.getClass().getName();
        Throwable c = t.getCause();
        if (c != null && c.getMessage() != null) {
            m = m + " | caused by: " + c.getClass().getSimpleName() + ": " + c.getMessage();
        }
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void syncVersionFileLatestFromLocal(RepositoryFile repositoryFile, long projectId) {
        try {
            String fileContent = IOUtils.toString(this.readFile(repositoryFile.getFullPath(), null));
            VersionFile latestFile = new VersionFile();
            latestFile.setPath(repositoryFile.getFullPath());
            latestFile.setName("latest");
            latestFile.setContent(fileContent);
            // todo
            saveVersionFileList(repositoryFile.getName(), repositoryFile.getFullPath(), projectId, Lists.newArrayList(latestFile), null);
        } catch (Exception e) {
            log.error("syncVersionFileLatestFromLocal {}", repositoryFile.getFullPath(), e);
        }
    }

    private void saveVersionFileList(String fileName, String filePath, Long projectId, List<VersionFile> versionFileList, Long fileId) {
        FileVersionEntity fileVersionEntity;
        List<FileVersionEntity> fileVersionEntityList = null;

        if (versionFileList != null && !versionFileList.isEmpty()) {
            log.info("saveVersionFileList versionFileList {} {}", filePath, versionFileList.size());
            fileVersionEntityList = new ArrayList<>(versionFileList.size() + 1);

            for (VersionFile versionFile : versionFileList) {
                fileVersionEntity = new FileVersionEntity();
                fileVersionEntity.setFileId(fileId);
                fileVersionEntity.setFileName(fileName);
                fileVersionEntity.setFilePath(versionFile.getPath());
                fileVersionEntity.setFileComment(versionFile.getComment());
                fileVersionEntity.setVersionNum(versionFile.getName());
                if (versionFile.getVersionNumReal() != null && versionFile.getVersionNumReal() > 0) {
                    fileVersionEntity.setVersionNumReal(versionFile.getVersionNumReal());
                } else {
                    fileVersionEntity.setVersionNumReal(VersionUtils.convertVersionToLong(versionFile.getName()));
                }
                fileVersionEntity.setProjectVersionNumReal(versionFile.getProjectVersionNumReal());
                fileVersionEntity.setProjectId(projectId);
                fileVersionEntity.setAfterComment(versionFile.getAfterComment());
                fileVersionEntity.setBeforeComment(versionFile.getBeforeComment());
                fileVersionEntity.setCreateUser(versionFile.getCreateUser());
                fileVersionEntity.setCreateDate(versionFile.getCreateDate());
                fileVersionEntity.setFileContent(versionFile.getContent());

                fileVersionEntityList.add(fileVersionEntity);
            }
        }

        int batchSize = 50;
        if (fileVersionEntityList.size() > batchSize) {
            Lists.partition(fileVersionEntityList, batchSize).forEach(this.fileRepository::batchInsert);
        } else {
            this.fileRepository.batchInsert(fileVersionEntityList);
        }
    }

    @Override
    public PackageConfig loadPackageConfigs(String project) throws Exception {
        if (project == null || project.trim().isEmpty()) {
            log.warn("loadPackageConfigs called with empty project name, returning empty config");
            return new PackageConfig();
        }
        String filePath = processPath(project) + "/" + PACKAGE_CONFIG_FILE;

        InputStream inputStream = readFile(filePath);
        if (inputStream == null) {
            log.warn("loadPackageConfigs: package config file not found at {}, returning empty config", filePath);
            return new PackageConfig();
        }
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();

        if (content == null || content.trim().isEmpty()) {
            log.warn("loadPackageConfigs: empty content for {}, returning empty config", filePath);
            return new PackageConfig();
        }
        Document document;
        try {
            document = DocumentHelper.parseText(content);
        } catch (org.dom4j.DocumentException e) {
            log.warn("loadPackageConfigs: failed to parse {}: {}, returning empty config", filePath, e.getMessage());
            return new PackageConfig();
        }
        Element rootElement = document.getRootElement();

        PackageConfig packageConfig = new PackageConfig();
        packageConfig.setVersion(rootElement.attributeValue("version"));
        packageConfig.setLock(Boolean.parseBoolean(rootElement.attributeValue("lock")));
//        Map<String, Integer> auditStatusMap = (Map<String, Integer>) JSON.parse(rootElement.attributeValue("audit"));
//        if (auditStatusMap == null) {
//            auditStatusMap = new HashMap<>();
//        }
//        packageConfig.setAuditStatusMap(auditStatusMap);

        // todo diff
        Map<String, String> versionDiffMap = (Map<String, String>) JSON.parse(rootElement.attributeValue("diff"));
        if (versionDiffMap == null) {
            versionDiffMap = new HashMap<>();
        }
        packageConfig.setVersionDiffMap(versionDiffMap);

        // todo runtime config

        return packageConfig;
    }

    @Override
    public void updatePackageConfigs(String project, PackageConfig packageConfig) throws Exception {
        String filePath = processPath(project) + "/" + PACKAGE_CONFIG_FILE;

        InputStream inputStream = readFile(filePath);
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        inputStream.close();

        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();
        rootElement.setAttributeValue("version", packageConfig.getVersion());
        rootElement.setAttributeValue("lock", packageConfig.getLock().toString());

        // todo diff
        rootElement.setAttributeValue("diff", JSON.toJSON(packageConfig.getVersionDiffMap()).toString());

        // todo runtime config

        DefaultUser defaultUser = new DefaultUser();
        defaultUser.setUsername("system");
        defaultUser.setAdmin(true);
        this.fileRepository.updateContentByVersionNum(filePath, "latest", document.asXML());
    }

    @Override
    public boolean fileExist(String var1) throws Exception {
        return false;
    }

    @Override
    public String getProject(String path) {
        if (path == null) {
            return null;
        }
        String processedPath = path;
        if (processedPath.startsWith("/")) {
            processedPath = processedPath.substring(1);
        }
        int slashPos = processedPath.indexOf("/");
        if (slashPos == -1) {
            return processedPath;
        }
        return processedPath.substring(0, slashPos);
    }

    @Override
    public List<RepositoryFile> loadTemplates(String project) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public String saveTemplateFile(String path, String content) throws Exception {
        // Template save not implemented in DB-backed storage
        return null;
    }

    private RepositoryFile buildProjectFile(ProjectEntity projectNode, FileType[] types, boolean classify, String searchFileName) throws Exception {
        RepositoryFile projectFile = new RepositoryFile();
        projectFile.setType(Type.project);
        projectFile.setName(projectNode.getName());
        projectFile.setFullPath("/" + projectNode.getName());

        List<FileEntity> fileEntityList = this.fileRepository.findChildrenByAncestor(projectNode.getId());
        log.info("{}: fileEntityList result:{}", projectNode.getName(), JSON.toJSONString(fileEntityList));
        if (CollectionUtils.isEmpty(fileEntityList)) {
            log.info("{}: fileEntityList file is null", projectNode.getName());
            return projectFile;
        }

        for (FileEntity file : fileEntityList) {
            if (Objects.isNull(file)) {
                log.info("{}: buildProjectFile file is null", projectNode.getName());
                continue;
            }
            Type type;
            try {
                type = Type.values()[file.getFileType()];
            } catch (ArrayIndexOutOfBoundsException e) {
                log.warn("{}: skipping file {} with invalid fileType {}", projectNode.getName(), file.getName(), file.getFileType());
                continue;
            }
            switch (type) {
                case all:
                    RepositoryFile resDir = new RepositoryFile();
                    resDir.setFullPath(projectFile.getFullPath());
                    resDir.setName(file.getName());

                    try {
                        if (classify) {
                            resDir.setType(Type.resource);
                            createResourceCategory(file, resDir, types, searchFileName);
                        } else {
                            resDir.setType(Type.all);
                            buildResources(file, resDir, types, searchFileName);
                        }
                    } catch (Exception e) {
                        log.error("buildProjectFile error", e);
                    }

                    projectFile.addChild(resDir, false);
                    break;
                case resourcePackage:
                    if ((types == null || types.length == 0) && this.permissionService.projectPackageHasReadPermission("projectNode.getFilePath()")) {
                        RepositoryFile packageFile = new RepositoryFile();
                        packageFile.setName(file.getName());
                        packageFile.setType(Type.resourcePackage);
                        packageFile.setFullPath(file.getFilePath());
                        projectFile.addChild(packageFile, false);
                    }
                    break;
                default:
            }
        }

        return projectFile;
    }

    private void createResourceCategory(FileEntity projectNode, RepositoryFile libDir, FileType[] types, String searchFileName) throws Exception {
        // 遍历库文件
        RepositoryFile subLib = buildLibFile(libDir, "库", LibType.res);
        subLib.setType(Type.lib);
        libDir.addChild(subLib, false);
        FileType[] librarySubTypes = types;
        if (types == null || types.length == 0) {
            librarySubTypes = new FileType[]{FileType.VariableLibrary, FileType.ParameterLibrary, FileType.ConstantLibrary, FileType.ActionLibrary};
        }
        buildNodes(projectNode, subLib, librarySubTypes, Type.lib, searchFileName);

        // 遍历决策集
        RepositoryFile rulesLib = buildLibFile(libDir, "决策集", LibType.ruleset);
        rulesLib.setFullPath(libDir.getFullPath());
        rulesLib.setType(Type.ruleLib);

        RepositoryFile decisionTableLib = buildLibFile(libDir, "决策表", LibType.decisiontable);
        decisionTableLib.setFullPath(libDir.getFullPath());
        decisionTableLib.setType(Type.decisionTableLib);

        RepositoryFile decisionTreeLib = buildLibFile(libDir, "决策树", LibType.decisiontree);
        decisionTreeLib.setFullPath(libDir.getFullPath());
        decisionTreeLib.setType(Type.decisionTreeLib);

        RepositoryFile scorecardLib = buildLibFile(libDir, "评分卡", LibType.scorecard);
        scorecardLib.setFullPath(libDir.getFullPath());
        scorecardLib.setType(Type.scorecardLib);

        RepositoryFile flowLib = buildLibFile(libDir, "决策流", LibType.ruleflow);
        flowLib.setFullPath(libDir.getFullPath());
        flowLib.setType(Type.flowLib);

        libDir.addChild(rulesLib, false);
        libDir.addChild(decisionTableLib, false);
        libDir.addChild(decisionTreeLib, false);
        libDir.addChild(scorecardLib, false);
        libDir.addChild(flowLib, false);

        FileType[] libraryRuleTypes = types;
        if (types == null || types.length == 0) {
            libraryRuleTypes = new FileType[]{FileType.Ruleset, FileType.RulesetLib, FileType.UL};
        }

        FileType[] libraryDecisionTypes = types;
        if (types == null || types.length == 0) {
            libraryDecisionTypes = new FileType[]{FileType.DecisionTable, FileType.ScriptDecisionTable, FileType.Crosstab};
        }
        FileType[] libraryDecisionTreeTypes = types;
        if (types == null || types.length == 0) {
            libraryDecisionTreeTypes = new FileType[]{FileType.DecisionTree};
        }

        FileType[] libraryFlowTypes = types;
        if (types == null || types.length == 0) {
            libraryFlowTypes = new FileType[]{FileType.RuleFlow};
        }

        FileType[] libraryScorecardTypes = types;
        if (types == null || types.length == 0) {
            libraryScorecardTypes = new FileType[]{FileType.Scorecard, FileType.ComplexScorecard};
        }

        buildNodes(projectNode, rulesLib, libraryRuleTypes, Type.ruleLib, searchFileName);
        buildNodes(projectNode, decisionTableLib, libraryDecisionTypes, Type.decisionTableLib, searchFileName);
        buildNodes(projectNode, decisionTreeLib, libraryDecisionTreeTypes, Type.decisionTreeLib, searchFileName);
        buildNodes(projectNode, scorecardLib, libraryScorecardTypes, Type.scorecardLib, searchFileName);
        buildNodes(projectNode, flowLib, libraryFlowTypes, Type.flowLib, searchFileName);
    }

    private RepositoryFile buildLibFile(RepositoryFile libraryDir, String name, LibType libType) {
        RepositoryFile subLib = new RepositoryFile();
        subLib.setFullPath(libraryDir.getFullPath());
        subLib.setName(name);
        subLib.setLibType(libType);
        return subLib;
    }

    private void buildNodes(FileEntity parentFile, RepositoryFile parent, FileType[] types, Type folderType, String searchFileName) throws Exception {
        LibType libType = parent.getLibType();

        List<FileEntity> fileEntityList = this.fileRepository.findChildrenByAncestor(parentFile.getId());
        fileEntityList.forEach(fileNode -> {
            if (fileNode.getFileType() < 0) {
                FileType detected = FileTypeUtils.getFileTypeByFileName(fileNode.getName());
                if (detected != null) {
                    Type mappedType = FileTypeUtils.mapFileNameToType(fileNode.getName());
                    if (mappedType != null) {
                        fileNode.setFileType(mappedType.ordinal());
                        this.fileRepository.updateById(fileNode);
                    }
                } else {
                    return;
                }
            }

            Type type = Type.values()[fileNode.getFileType()];
            String name = fileNode.getName();

            // TODO: 2023/6/30
//            if (!fileNode.hasProperty(FILE)) {
//                return;
//            }
            RepositoryFile file = new RepositoryFile();
            file.setLibType(libType);
            // TODO: 2023/6/30
//            if (name.toLowerCase().contains(RES_PACKGE_FILE)
//                    || name.toLowerCase().contains(PACKAGE_CONFIG_FILE)
//                    || name.toLowerCase().contains(CLIENT_CONFIG_FILE)
//                    || name.toLowerCase().contains(RESOURCE_SECURITY_CONFIG_FILE)) {
//                return;
//            }

            if (type != Type.folder) {
                if (!this.permissionService.fileHasReadPermission(fileNode.getFilePath())) {
                    return;
                }
                FileType fileType = com.ruleforge.console.util.FileTypeUtils.getFileTypeByFileName(name);
                boolean add = false;
                if (fileType != null) {
                    for (FileType typeItem : types) {
                        if (fileType == typeItem) {
                            add = true;
                            break;
                        }
                    }
                }
                if (!add) {
                    return;
                }

                if (libType.equals(LibType.res)) {
                    if (!fileType.equals(FileType.ActionLibrary) && !fileType.equals(FileType.ParameterLibrary) && !fileType.equals(FileType.ConstantLibrary) && !fileType.equals(FileType.VariableLibrary)) {
                        return;
                    }
                }

                if (libType.equals(LibType.decisiontable)) {
                    if (!fileType.equals(FileType.ScriptDecisionTable) && !fileType.equals(FileType.DecisionTable) && !fileType.equals(FileType.Crosstab)) {
                        return;
                    }
                }

                if (libType.equals(LibType.decisiontree)) {
                    if (!fileType.equals(FileType.DecisionTree)) {
                        return;
                    }
                }

                if (libType.equals(LibType.ruleflow)) {
                    if (!fileType.equals(FileType.RuleFlow)) {
                        return;
                    }
                }

                if (libType.equals(LibType.scorecard)) {
                    if (!fileType.equals(FileType.Scorecard) && !fileType.equals(FileType.ComplexScorecard)) {
                        return;
                    }
                }

                if (libType.equals(LibType.ruleset)) {
                    if (!fileType.equals(FileType.Ruleset) && !fileType.equals(FileType.UL) && !fileType.equals(FileType.RulesetLib)) {
                        return;
                    }
                }

                if (StringUtils.isNotBlank(searchFileName)) {
                    boolean fileNameContain = name.toLowerCase().contains(searchFileName.toLowerCase());
                    if (name.toLowerCase().endsWith(FileType.Ruleset.toString())) {
                        // 搜索文件本身
                        try {
                            InputStream inputStream = null;
                            inputStream = readFile(fileNode.getFilePath());

                            byte[] bytes;
                            bytes = new byte[inputStream.available()];
                            inputStream.read(bytes);
                            String ruleContent = new String(bytes);

                            if (!ruleContent.toLowerCase().contains(searchFileName.toLowerCase()) && !fileNameContain) {
                                return;
                            }
                        } catch (Exception ex) {
                        }
                    } else {
                        // 搜索文件名
                        if (!fileNameContain) {
                            return;
                        }
                    }
                }

                Type mapType = FileTypeUtils.mapFileNameToType(name);
                if (mapType != null) {
                    file.setType(mapType);
                }
                file.setFullPath(fileNode.getFilePath());
                file.setName(name);
                try {
                    buildNodeLockInfo(fileNode, file);
                    parent.addChild(file, false);
                    buildNodes(fileNode, file, types, folderType, searchFileName);

                } catch (Exception e) {
                    log.error("buildNodes not folder error", e);
                }

            } else {
                file.setFullPath(fileNode.getFilePath());
                file.setName(name);
                file.setType(Type.folder);
                try {
                    buildNodeLockInfo(fileNode, file);
                    file.setFolderType(folderType);
                    parent.addChild(file, true);
                    buildNodes(fileNode, file, types, folderType, searchFileName);
                } catch (Exception e) {
                    log.error("buildNodes  folder error", e);
                }
            }

        });
    }

    private void buildNodeLockInfo(FileEntity node, RepositoryFile file) throws Exception {
    }

    private void buildResources(FileEntity projectNode, RepositoryFile libDir, FileType[] types, String searchFileName) throws Exception {
        FileType[] fileTypes = types;
        if (types == null || types.length == 0) {
            fileTypes = new FileType[]{FileType.VariableLibrary,
                    FileType.ParameterLibrary, FileType.ConstantLibrary,
                    FileType.ActionLibrary, FileType.Ruleset, FileType.RulesetLib,
                    FileType.RuleFlow, FileType.DecisionTable,
                    FileType.DecisionTree, FileType.ScriptDecisionTable,
                    FileType.UL, FileType.Scorecard, FileType.ComplexScorecard, FileType.Crosstab};
        }
        libDir.setLibType(LibType.all);
        buildNodes(projectNode, libDir, fileTypes, Type.all, searchFileName);
    }

    @Override
    public String createProjectVersion(String projectName, String packageId, String projectVersion, User user, String comment, Integer status) throws Exception {
        log.info("Attempting to create a new version for project [{}] with comment: {}", projectName, comment);
        try {
            // 将数据库插入操作委托给 ProjectStorageService
            String createdVersionName = projectStorageService.createProjectPackageVersion(projectName, packageId, projectVersion, user.getUsername(), comment, status);
            log.info("Successfully requested storage service to create project version [{}] for project [{}].", createdVersionName, projectName);
            return createdVersionName;
        } catch (Exception e) {
            log.error("Storage service failed to create project version for project [{}]", projectName, e);
            // 抛出异常以触发事务回滚
            throw new RuleException("Failed to create project version for " + projectName + " due to storage error.", e);
        }
    }

    @Override
    public String createProjectVersion(String projectName, User user, String comment) throws Exception {
        return createProjectVersion(projectName, null, null, user, comment, 0);
    }

    @Override
    public List<VersionFile> getProjectVersions(String projectName, boolean desc, int page, int row) throws Exception {
        ProjectEntity project = projectRepository.findByName(projectName);
        if (project == null) {
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        Long projectId = project.getId();

        List<ProjectVersionEntity> projectVersionEntities = projectRepository.findVersionsByProjectIdPaged(projectId, desc, page, row);
        List<VersionFile> versionFiles = new ArrayList<>();
        if (!CollectionUtils.isEmpty(projectVersionEntities)) {
            for (ProjectVersionEntity entity : projectVersionEntities) {
                versionFiles.add(VersionFileUtils.getVersionFile(projectName, entity));
            }
        }
        return versionFiles;
    }

    @Override
    public List<VersionFile> getProjectPackageVersions(String projectName, String packageId) throws Exception {
        ProjectEntity project = projectRepository.findByName(projectName);
        if (project == null) {
            throw new RuleException("Project [" + projectName + "] not found.");
        }
        Long projectId = project.getId();

        List<ProjectVersionEntity> projectVersionEntities = projectRepository.findVersionsByProjectId(projectId, packageId, true);
        List<VersionFile> versionFiles = new ArrayList<>();
        if (!CollectionUtils.isEmpty(projectVersionEntities)) {
            for (ProjectVersionEntity entity : projectVersionEntities) {
                versionFiles.add(VersionFileUtils.getVersionFile(projectName, entity));
            }
        }
        return versionFiles;
    }

}
