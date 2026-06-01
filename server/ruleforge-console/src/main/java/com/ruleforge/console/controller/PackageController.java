package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.console.ExternalProcessService;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectRuntimeConfigEntity;
import com.ruleforge.console.entity.ProjectRuntimeFlowEntity;
import com.ruleforge.console.entity.ProjectVersionEntity;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.servlet.common.RefFile;
import com.ruleforge.console.servlet.respackage.HttpSessionKnowledgeCache;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.console.model.PackageConfig;
import com.ruleforge.console.model.User;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.model.FileDiff;
import com.ruleforge.console.storage.model.MergeResult;
import com.ruleforge.console.util.EnvironmentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ruleforge.console.repository.BaseRepositoryService.RES_PACKGE_FILE;

@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/packageeditor")
@RequiredArgsConstructor
public class PackageController extends BaseController {

    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final GitStorageService gitStorageService;
    private final ExternalProcessService externalProcessService;
    private final HttpSessionKnowledgeCache httpSessionKnowledgeCache;
    private final KnowledgeBuilder knowledgeBuilder;
    public static final String VCS_KEY = "_vcs";
    public static final String KB_KEY = "_kb";
    private final ProjectRepository projectRepository;
    private final RuntimeRepository runtimeRepository;

    @PostMapping("/loadPackages")
    public List<ResourcePackage> loadPackages(@RequestParam("project") String project,
                                              @RequestParam(value = "env", required = false) String env) throws Exception {
        project = project.replace(".rp", "");
        project = Utils.decodeURL(project);

        // 根据环境变量获取版本号
        if (org.springframework.util.StringUtils.hasText(env)) {
            return this.ruleforgeRepositoryService.loadProjectResourcePackages(project, env);
        }

        return this.ruleforgeRepositoryService.loadProjectResourcePackages(project);
    }

    @PostMapping("/loadPackageConfig")
    public PackageConfig loadPackageConfig(@RequestParam("project") String projectOrigin) throws Exception {
        projectOrigin = Utils.decodeURL(projectOrigin);
        String project = projectOrigin.split(":")[0];
        project = project.replace(".rp", "");
        return this.ruleforgeRepositoryService.loadPackageConfigs(project);
    }

    @PostMapping("/loadForTestVariableCategories")
    public List<VariableCategory> loadForTestVariableCategories(HttpServletRequest req, @RequestParam String files) throws RuleException {
        KnowledgeBase knowledgeBase = buildKnowledgeBase(req, files);
        List<VariableCategory> vcs = knowledgeBase.getResourceLibrary().getVariableCategories();
        this.httpSessionKnowledgeCache.put(req, VCS_KEY, vcs);
        return vcs;
    }

    @PostMapping("/saveResourcePackages")
    public void saveResourcePackages(@RequestBody Map<String, Object> map) throws Exception {
        String project = ((String) map.get("project")).split(":")[0];
        project = project.replace(".rp", "");
        project = Utils.decodeURL(project);
        User user = EnvironmentUtils.getLoginUser(null);
        String path = project + "/" + RES_PACKGE_FILE;
        String xml = Utils.decodeURL((String) map.get("xml"));
        xml = Utils.decodeURL(xml);
        Boolean newVersion = (Boolean) map.get("newVersion");
        String packageId = (String) map.get("packageId");
        String beforeComment = (String) map.get("beforeComment");
        String afterComment = (String) map.get("afterComment");
        String versionComment = (String) map.get("versionComment");
//        List<String> associatedFiles = (List<String>) map.get("associatedFiles");

        // todo 为知识包生成新版本
//        ProjectEntity projectEntity = this.projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
//                .eq(ProjectEntity::getName, project)
//                .last("limit 1"));
//        PackageEntity packageEntity = this.packageMapper.selectOne(new LambdaQueryWrapper<PackageEntity>()
//                .eq(PackageEntity::getProjectId, projectEntity.getId())
//                .eq(PackageEntity::getPackageId, packageId));
//        if (packageEntity == null) {
//            packageEntity = new PackageEntity();
//            packageEntity.setProjectId(projectEntity.getId());
//            packageEntity.setPackageId(packageId);
//            packageEntity.setCreateDate(new Date());
//            this.packageMapper.insert(packageEntity);
//        }
        String projectVersion = this.ruleforgeRepositoryService.saveFile(path, xml, newVersion, versionComment, beforeComment, afterComment, user);

        if (newVersion) {
            this.ruleforgeRepositoryService.createProjectVersion(project, packageId, projectVersion, user, "", 0);
        }
    }

    @PostMapping("/getPackageDiff")
    public Map<String, Object> getPackageDiff(@RequestParam String project,
                                              @RequestParam(required = false) String path,
                                              @RequestParam(required = false) String targetVersion) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("status", true);

        if (org.springframework.util.StringUtils.hasText(path)) {
            List<String> pathList = new ArrayList<>();
            Map<String, String> pathNodeNameMap = new HashMap<>();

            if (path.endsWith(FileType.RuleFlow.toString())) {
                // Flow files are now BPMN XML, no old-style flow definition parsing
                pathList.add(path);
            } else {
                pathList.add(path);
            }

            List<RefFile> versionDiff = this.ruleforgeRepositoryService.getFlowRefs(pathList);
            for (RefFile refFile : versionDiff) {
                refFile.setName(pathNodeNameMap.getOrDefault(refFile.getPath(), ""));
            }
            map.put("data", versionDiff);
        } else {
            map.put("data", this.ruleforgeRepositoryService.getPackageVersionDiff(project, targetVersion));
        }

        return map;
    }

    @PostMapping("/getFileDiff")
    public Map<String, Object> getFileDiff(@RequestParam String filePath,
                                           @RequestParam String targetVersion) throws Exception {
        String versionDiff = this.ruleforgeRepositoryService.getFileVersionDiff(filePath, targetVersion);

        Map<String, Object> map = new HashMap<>();
        map.put("status", true);
        map.put("data", versionDiff);
        return map;
    }

    @PostMapping("/getPackageDiffStructured")
    public List<FileDiff> getPackageDiffStructured(
            @RequestParam String project,
            @RequestParam String fromVersion,
            @RequestParam String toVersion) throws Exception {
        return this.ruleforgeRepositoryService.getPackageVersionDiffStructured(project, fromVersion, toVersion);
    }

    @PostMapping("/getFileDiffStructured")
    public FileDiff getFileDiffStructured(
            @RequestParam String filePath,
            @RequestParam String fromVersion,
            @RequestParam String toVersion) throws Exception {
        return this.ruleforgeRepositoryService.getFileVersionDiffStructured(filePath, fromVersion, toVersion);
    }

    @PostMapping("/refreshKnowledgeCache")
    public String deployTestPackage(@RequestParam String project,
                                    @RequestParam(required = false) String title,
                                    @RequestParam(required = false) String remark,
                                    @RequestParam(required = false) Integer rate,
                                    @RequestParam String originVersion,
                                    @RequestParam String targetVersion,
                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") Date startTime,
                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") Date endTime) throws Exception {
        User user = EnvironmentUtils.getLoginUser(null);
        // todo 修改使用标记
        ProjectEntity projectEntity = projectRepository.findByName(project);
        long count = runtimeRepository.countActiveFlows(projectEntity.getId(), targetVersion, "test");
        if (count > 0) {
            return "当前审批状态不支持发起测试审批";
        }
        if (StringUtils.isBlank(title)) {
            title = project.concat("新决策发布测试审批");
        }
        String explain = this.ruleforgeRepositoryService.getPackageVersionDiff(project, targetVersion);
        String processId = this.externalProcessService.testStart(title, project, project + ".rp", startTime, endTime, targetVersion, rate, remark, explain);
        log.info(String.format("{%s}{%s}{%s}:externalProcessService processId", project, targetVersion, user.getUsername()));
        if (StringUtils.isBlank(processId)) {
            log.info("processId is null");
            return "发起测试审批失败";
        } else {
            Date date = new Date();
            boolean updated = runtimeRepository.upsertFlow(projectEntity.getId(), targetVersion, "test",
                    20, rate, startTime, endTime, date, user.getUsername());
            if (!updated) {
                ProjectRuntimeFlowEntity projectRuntimeFlowEntity = new ProjectRuntimeFlowEntity();
                projectRuntimeFlowEntity.setProjectId(projectEntity.getId());
                projectRuntimeFlowEntity.setProjectVersion(targetVersion);
                projectRuntimeFlowEntity.setExecEnv("test");
                projectRuntimeFlowEntity.setAuditStatus(20);
                projectRuntimeFlowEntity.setProportion(rate);
                projectRuntimeFlowEntity.setStartTime(startTime);
                projectRuntimeFlowEntity.setEndTime(endTime);
                projectRuntimeFlowEntity.setCreateTime(date);
                projectRuntimeFlowEntity.setCreateUser(user.getUsername());
                projectRuntimeFlowEntity.setUpdateTime(date);
                projectRuntimeFlowEntity.setUpdateUser(user.getUsername());
                runtimeRepository.insertFlow(projectRuntimeFlowEntity);
            }
            // todo 测试环境自动通过
            if ("autoProcess".equals(processId)) {
                // 自动通过
                Map<String, Object> auditMap = new HashMap<>();
                auditMap.put("status", 4);
                Map<String, String> params = new HashMap<>();
                params.put("projectName", project);
                params.put("versionCode", targetVersion);
                auditMap.put("params", params);
                callbackTestUruleResult(auditMap);
            }
        }
        return "刷新知识包操作成功!";
    }

    @PostMapping("/callbackTestUruleResult")
    public Map<String, Object> callbackTestUruleResult(@RequestBody Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("callbackTestUruleResult params: {}", map);
            Integer status = (Integer) map.get("status");
            Map<String, String> params = (Map<String, String>) map.get("params");
            String project = params.get("projectName");
            String version = params.get("versionCode");
            boolean passAudit = Objects.equals(4, status);
            // todo
            ProjectEntity projectEntity = projectRepository.findByName(project);
            ProjectVersionEntity projectVersionEntity = projectRepository.findVersionByProjectIdAndVersionName(projectEntity.getId(), version);
            ProjectRuntimeFlowEntity projectRuntimeFlowEntity = runtimeRepository.findFlowByProjectVersionAndAuditStatus(projectEntity.getId(), version, 20);
            String createUser = projectRuntimeFlowEntity.getCreateUser();
            Date startTime = projectRuntimeFlowEntity.getStartTime();
            Integer proportion = projectRuntimeFlowEntity.getProportion();
            Date endTime = projectRuntimeFlowEntity.getEndTime();
            runtimeRepository.updateFlowStatus(projectEntity.getId(), version, "test", passAudit ? 90 : 91, createUser);
            log.info("callbackTestUruleResult projectRuntimeFlowMapper update: {}", true);
            if (passAudit) {
                // 通知client
                this.externalProcessService.syncExec(project + "/" + projectVersionEntity.getPackageId(), "test", createUser, proportion, startTime, endTime);
                runtimeRepository.upsertConfig(projectEntity.getId(), projectVersionEntity.getPackageId(), "test", version, createUser);
            }
            result.put("status", true);
        } catch (Exception e) {
            log.error("callbackTestUruleResult error", e);
            result.put("status", false);
        }
        return result;
    }

    @PostMapping(value = "/loadFlows", produces = "text/json;charset=UTF-8")
    public String loadFlows(HttpServletRequest req) throws Exception {
        // Flow definitions are now managed by Flowable BPMN engine
        return "[]";
    }

    private KnowledgeBase buildKnowledgeBase(HttpServletRequest req, String files) throws RuleException {
        files = Utils.decodeURL(files);
        ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
        String[] paths = files.split(";");
        for (String path : paths) {
            String[] subPaths = path.split(",");
            path = subPaths[0];
            String version = null;
            if (subPaths.length > 1) {
                version = subPaths[1];
            }
            resourceBase.addResource(path, version, true);
        }
        KnowledgeBase knowledgeBase = this.knowledgeBuilder.buildKnowledgeBase(resourceBase);
        this.httpSessionKnowledgeCache.remove(req, KB_KEY);
        this.httpSessionKnowledgeCache.put(req, KB_KEY, knowledgeBase);
        return knowledgeBase;
    }

    /**
     * Merge a user branch into main.
     * Used when a user wants to integrate their changes with the main branch.
     * Returns merge status (success, conflicts) with details.
     */
    @PostMapping("/mergeBranch")
    public Map<String, Object> mergeBranch(@RequestParam String project,
                                            @RequestParam String sourceBranch) throws Exception {
        Map<String, Object> result = new HashMap<>();

        if (!gitStorageService.repoExists(project)) {
            result.put("success", false);
            result.put("message", "Git repository not found for project: " + project);
            return result;
        }

        MergeResult mergeResult = gitStorageService.merge(project, sourceBranch, "main");
        result.put("status", mergeResult.getStatus().name());
        result.put("mergeCommitSha", mergeResult.getMergeCommitSha());

        switch (mergeResult.getStatus()) {
            case FAST_FORWARD, MERGED -> {
                result.put("success", true);
                result.put("message", "Merge completed successfully");
                gitStorageService.push(project);
            }
            case CONFLICTING -> {
                result.put("success", false);
                result.put("message", "Merge conflict detected. Please resolve conflicts manually.");
                result.put("conflictingFiles", mergeResult.getConflictingFiles());
            }
        }
        return result;
    }

    /**
     * List all branches for a project.
     */
    @PostMapping("/listBranches")
    public Map<String, Object> listBranches(@RequestParam String project) throws Exception {
        Map<String, Object> result = new HashMap<>();
        if (!gitStorageService.repoExists(project)) {
            result.put("branches", List.of());
            return result;
        }
        result.put("branches", gitStorageService.listBranches(project));
        return result;
    }

    /**
     * Load the file tree for a specific package and version.
     * Returns the list of files with their versions in the package,
     * optionally at a specific Git tag (version).
     */
    @PostMapping("/loadPackageTree")
    public Map<String, Object> loadPackageTree(@RequestParam String project,
                                                 @RequestParam String packageId,
                                                 @RequestParam(required = false) String version) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        // Find the project
        ProjectEntity projectEntity = projectRepository.findByName(project);
        if (projectEntity == null) {
            result.put("success", false);
            result.put("message", "Project not found: " + project);
            return result;
        }

        // Get project versions for this package
        List<ProjectVersionEntity> versions = projectRepository.findVersionsByProjectId(projectEntity.getId(), packageId, true);

        // If version is specified, find that version; otherwise use the latest
        ProjectVersionEntity targetVersion = null;
        if (version != null && !version.isEmpty()) {
            for (ProjectVersionEntity pve : versions) {
                if (version.equals(pve.getVersionName())) {
                    targetVersion = pve;
                    break;
                }
            }
        }
        if (targetVersion == null && !versions.isEmpty()) {
            targetVersion = versions.get(0);
        }

        result.put("versions", versions.stream().map(v -> {
            Map<String, Object> vMap = new HashMap<>();
            vMap.put("version", v.getVersionName());
            vMap.put("comment", v.getComment());
            vMap.put("createUser", v.getCreateUser());
            vMap.put("createTime", v.getCreateTime());
            vMap.put("auditStatus", v.getAuditStatus());
            vMap.put("gitCommitSha", v.getGitCommitSha());
            return vMap;
        }).toList());

        // Get files for the target version
        if (targetVersion != null) {
            String gitTag = "pkg/" + packageId + "/" + targetVersion.getVersionName();

            // Load resource items for this package from the package file
            List<Map<String, Object>> resourceItems = new ArrayList<>();
            try {
                String packagePath = "/" + project + "/" + RES_PACKGE_FILE + "/" + packageId + "/package.xml";
                InputStream is = ruleforgeRepositoryService.readFile(packagePath);
                if (is != null) {
                    String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    org.dom4j.Document doc = org.dom4j.DocumentHelper.parseText(content);
                    List<org.dom4j.Element> items = doc.getRootElement().elements("item");
                    for (org.dom4j.Element item : items) {
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("path", item.attributeValue("path"));
                        itemMap.put("name", item.attributeValue("name"));
                        itemMap.put("version", item.attributeValue("version"));
                        itemMap.put("gitTag", gitTag);
                        resourceItems.add(itemMap);
                    }
                    is.close();
                }
            } catch (Exception e) {
                log.debug("Failed to load package items: {}", e.getMessage());
            }

            result.put("currentVersion", targetVersion.getVersionName());
            result.put("gitTag", gitTag);
            result.put("resourceItems", resourceItems);
        }

        return result;
    }

}
