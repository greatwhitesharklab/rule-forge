package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.console.ExternalProcessService;
import com.ruleforge.console.entity.ApprovalTaskEntity;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectVersionEntity;
import com.ruleforge.console.model.PackageConfig;
import com.ruleforge.console.repository.data.ApprovalRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.runtime.cache.CacheUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/approval")
@RequiredArgsConstructor
public class ApprovalController extends BaseController {

    private final ApprovalRepository approvalRepository;
    private final ProjectRepository projectRepository;
    private final RuntimeRepository runtimeRepository;
    private final RuleForgeRepositoryService ruleforgeRepositoryService;
    private final ExternalProcessService externalProcessService;
    private final GitStorageService gitStorageService;

    @PostMapping("/listPending")
    public Map<String, Object> listPending(@RequestParam String projectName) {
        Map<String, Object> result = new HashMap<>();
        try {
            projectName = Utils.decodeURL(projectName);
            ProjectEntity projectEntity = projectRepository.findByName(projectName);
            if (projectEntity == null) {
                result.put("status", false);
                result.put("message", "Project not found: " + projectName);
                return result;
            }
            List<ApprovalTaskEntity> tasks = approvalRepository.findPendingByProjectId(projectEntity.getId());
            result.put("status", true);
            result.put("data", tasks);
        } catch (Exception e) {
            log.error("listPending error", e);
            result.put("status", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/approve")
    public Map<String, Object> approve(@RequestParam Long taskId,
                                       @RequestParam(required = false) String approveRemark) {
        Map<String, Object> result = new HashMap<>();
        try {
            String approver = EnvironmentUtils.getLoginUser(null).getUsername();

            ApprovalTaskEntity task = approvalRepository.findById(taskId);
            if (task == null) {
                result.put("status", false);
                result.put("message", "Approval task not found: " + taskId);
                return result;
            }
            if (!"pending".equals(task.getStatus())) {
                result.put("status", false);
                result.put("message", "Task is not in pending status");
                return result;
            }

            // Update task status
            approvalRepository.updateStatus(taskId, "approved", approver, approveRemark);

            // Load project and version
            ProjectEntity projectEntity = projectRepository.findById(task.getProjectId());
            if (projectEntity == null) {
                result.put("status", false);
                result.put("message", "Project not found for task");
                return result;
            }
            String projectName = projectEntity.getName();
            ProjectVersionEntity projectVersion = projectRepository.findVersionByProjectIdAndVersionName(
                    projectEntity.getId(), task.getProjectVersion());

            if (projectVersion != null) {
                if (projectVersion.getAuditStatus() > 89) {
                    result.put("status", false);
                    result.put("message", "该版本已完成审批");
                    return result;
                }

                // Upsert runtime config for production
                runtimeRepository.upsertConfig(projectEntity.getId(), projectVersion.getPackageId(),
                        "prod", task.getProjectVersion(), projectVersion.getCreateUser());

                // Clear knowledge cache
                CacheUtils.getKnowledgeCache().removeKnowledgeByProjectName(projectName);

                // Git integration: create tag + push
                if (gitStorageService.repoExists(projectName)) {
                    try {
                        String packageId = projectVersion.getPackageId();
                        if (packageId != null) {
                            String tagName = "pkg/" + packageId + "/" + task.getProjectVersion();
                            gitStorageService.createTag(projectName, tagName, "main");
                            gitStorageService.push(projectName);
                            log.info("Created Git tag [{}] for approved version [{}] in project [{}]",
                                    tagName, task.getProjectVersion(), projectName);
                        }
                    } catch (Exception e) {
                        log.error("Git tag creation failed during approval for project [{}]", projectName, e);
                    }
                }

                // Notify executor to refresh knowledge cache
                try {
                    String fullPackageId = projectName + "/" + projectVersion.getPackageId();
                    externalProcessService.syncExec(fullPackageId, "prod",
                            projectVersion.getCreateUser(), null, null, null);
                } catch (Exception e) {
                    log.error("Failed to notify executor for project [{}]", projectName, e);
                }

                // Update version audit status to approved (90)
                projectVersion.setAuditStatus(90);
                projectRepository.updateVersion(projectVersion);
            }

            // Unlock the package config
            try {
                PackageConfig packageConfig = ruleforgeRepositoryService.loadPackageConfigs(projectName);
                packageConfig.setLock(false);
                ruleforgeRepositoryService.updatePackageConfigs(projectName, packageConfig);
            } catch (Exception e) {
                log.error("Failed to unlock package config for project [{}]", projectName, e);
            }

            result.put("status", true);
        } catch (Exception e) {
            log.error("approve error", e);
            result.put("status", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/reject")
    public Map<String, Object> reject(@RequestParam Long taskId,
                                      @RequestParam(required = false) String approveRemark) {
        Map<String, Object> result = new HashMap<>();
        try {
            String approver = EnvironmentUtils.getLoginUser(null).getUsername();

            ApprovalTaskEntity task = approvalRepository.findById(taskId);
            if (task == null) {
                result.put("status", false);
                result.put("message", "Approval task not found: " + taskId);
                return result;
            }
            if (!"pending".equals(task.getStatus())) {
                result.put("status", false);
                result.put("message", "Task is not in pending status");
                return result;
            }

            // Update task status
            approvalRepository.updateStatus(taskId, "rejected", approver, approveRemark);

            // Update version audit status to rejected (91)
            ProjectEntity projectEntity = projectRepository.findById(task.getProjectId());
            if (projectEntity != null) {
                ProjectVersionEntity projectVersion = projectRepository.findVersionByProjectIdAndVersionName(
                        projectEntity.getId(), task.getProjectVersion());
                if (projectVersion != null) {
                    projectVersion.setAuditStatus(91);
                    projectRepository.updateVersion(projectVersion);
                }

                // Unlock the package config
                try {
                    PackageConfig packageConfig = ruleforgeRepositoryService.loadPackageConfigs(projectEntity.getName());
                    packageConfig.setLock(false);
                    ruleforgeRepositoryService.updatePackageConfigs(projectEntity.getName(), packageConfig);
                } catch (Exception e) {
                    log.error("Failed to unlock package config for project [{}]", projectEntity.getName(), e);
                }
            }

            result.put("status", true);
        } catch (Exception e) {
            log.error("reject error", e);
            result.put("status", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/listByProject")
    public Map<String, Object> listByProject(@RequestParam String projectName) {
        Map<String, Object> result = new HashMap<>();
        try {
            projectName = Utils.decodeURL(projectName);
            ProjectEntity projectEntity = projectRepository.findByName(projectName);
            if (projectEntity == null) {
                result.put("status", false);
                result.put("message", "Project not found: " + projectName);
                return result;
            }
            List<ApprovalTaskEntity> tasks = approvalRepository.findByProjectId(projectEntity.getId());
            result.put("status", true);
            result.put("data", tasks);
        } catch (Exception e) {
            log.error("listByProject error", e);
            result.put("status", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
