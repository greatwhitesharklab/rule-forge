package com.ruleforge.console.controller;

import com.ruleforge.console.ExternalProcessService;
import com.ruleforge.console.entity.DeploymentConfigEntity;
import com.ruleforge.console.entity.ExecutorNodeEntity;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectRuntimeFlowEntity;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.service.DeploymentService;
import com.ruleforge.console.util.EnvironmentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/${ruleforge.root.path}/deployment")
@RequiredArgsConstructor
@Slf4j
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final ProjectRepository projectRepository;
    private final RuntimeRepository runtimeRepository;
    private final ExternalProcessService externalProcessService;

    @PostMapping("/deploy")
    public Map<String, Object> deploy(@RequestParam String projectName,
                                      @RequestParam String packageId,
                                      @RequestParam String version,
                                      @RequestParam(defaultValue = "prod") String execEnv,
                                      @RequestParam(required = false) String deployUser) {
        Map<String, Object> result = new HashMap<>();
        Long projectId = resolveProjectId(projectName);
        String user = resolveDeployUser(deployUser);
        String gitTag = "pkg/" + packageId + "/" + version;

        deploymentService.deploy(projectId, packageId, gitTag, version, execEnv, null, user);
        runtimeRepository.upsertConfig(projectId, packageId, execEnv, version, user);
        externalProcessService.syncExec(projectName + "/" + packageId, execEnv, user, null, null, null);

        result.put("status", true);
        return result;
    }

    @PostMapping("/current")
    public DeploymentConfigEntity current(@RequestParam String projectName,
                                          @RequestParam String packageId,
                                          @RequestParam(defaultValue = "prod") String execEnv) {
        Long projectId = resolveProjectId(projectName);
        return deploymentService.getCurrentDeployment(projectId, packageId, execEnv);
    }

    @PostMapping("/history")
    public List<DeploymentConfigEntity> history(@RequestParam String projectName,
                                                @RequestParam String packageId) {
        Long projectId = resolveProjectId(projectName);
        return deploymentService.getDeployments(projectId, packageId);
    }

    @PostMapping("/environments")
    public List<?> environments(@RequestParam String projectName) {
        Long projectId = resolveProjectId(projectName);
        return runtimeRepository.findConfigsByProjectId(projectId);
    }

    @PostMapping("/promote")
    public Map<String, Object> promote(@RequestParam String projectName,
                                       @RequestParam String packageId,
                                       @RequestParam String version,
                                       @RequestParam(required = false) String deployUser) {
        Map<String, Object> result = new HashMap<>();
        Long projectId = resolveProjectId(projectName);
        String user = resolveDeployUser(deployUser);

        // Validate: version must have audit_status=90 (approved) in test
        ProjectRuntimeFlowEntity flow = runtimeRepository.findFlowByProjectVersionAndAuditStatus(projectId, version, 90);
        if (flow == null) {
            result.put("status", false);
            result.put("message", "该版本尚未通过测试审批");
            return result;
        }

        String gitTag = "pkg/" + packageId + "/" + version;
        deploymentService.deploy(projectId, packageId, gitTag, version, "prod", null, user);
        runtimeRepository.upsertConfig(projectId, packageId, "prod", version, user);
        externalProcessService.syncExec(projectName + "/" + packageId, "prod", user, null, null, null);

        result.put("status", true);
        return result;
    }

    @PostMapping("/rollback")
    public Map<String, Object> rollback(@RequestParam String projectName,
                                        @RequestParam String packageId,
                                        @RequestParam String targetVersion,
                                        @RequestParam(defaultValue = "prod") String execEnv,
                                        @RequestParam(required = false) String deployUser) {
        Map<String, Object> result = new HashMap<>();
        Long projectId = resolveProjectId(projectName);
        String user = resolveDeployUser(deployUser);
        String gitTag = "pkg/" + packageId + "/" + targetVersion;

        deploymentService.rollback(projectId, packageId, gitTag, targetVersion, execEnv, user);
        runtimeRepository.upsertConfig(projectId, packageId, execEnv, targetVersion, user);
        externalProcessService.syncExec(projectName + "/" + packageId, execEnv, user, null, null, null);

        result.put("status", true);
        return result;
    }

    @PostMapping("/registerNode")
    public ExecutorNodeEntity registerNode(@RequestParam String nodeName,
                                           @RequestParam String nodeUrl,
                                           @RequestParam(defaultValue = "default") String execEnv) {
        return deploymentService.registerNode(nodeName, nodeUrl, execEnv);
    }

    @PostMapping("/listNodes")
    public List<ExecutorNodeEntity> listNodes(@RequestParam(required = false) String execEnv) {
        return deploymentService.listNodes(execEnv);
    }

    @PostMapping("/updateNodeGroup")
    public Map<String, Object> updateNodeGroup(@RequestParam Long nodeId,
                                                @RequestParam String nodeGroup) {
        runtimeRepository.updateNodeGroup(nodeId, nodeGroup);
        Map<String, Object> result = new HashMap<>();
        result.put("status", true);
        return result;
    }

    @PostMapping("/deployToGroup")
    public Map<String, Object> deployToGroup(@RequestParam String projectName,
                                              @RequestParam String packageId,
                                              @RequestParam String version,
                                              @RequestParam(defaultValue = "prod") String execEnv,
                                              @RequestParam String nodeGroup,
                                              @RequestParam(required = false) String deployUser) {
        Map<String, Object> result = new HashMap<>();
        Long projectId = resolveProjectId(projectName);
        String user = resolveDeployUser(deployUser);
        String gitTag = "pkg/" + packageId + "/" + version;

        List<DeploymentConfigEntity> deployments = deploymentService.deployToGroup(
                projectId, packageId, gitTag, version, execEnv, nodeGroup, user);

        // Notify executors to pick up the new deployment config
        externalProcessService.syncExec(projectName + "/" + packageId, execEnv, user, null, null, null);

        result.put("status", true);
        result.put("deployedCount", deployments.size());
        return result;
    }

    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(@RequestParam Long nodeId) {
        deploymentService.updateHeartbeat(nodeId);
        Map<String, Object> result = new HashMap<>();
        result.put("status", true);
        return result;
    }

    private Long resolveProjectId(String projectName) {
        ProjectEntity project = projectRepository.findByName(projectName);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }
        return project.getId();
    }

    private String resolveDeployUser(String deployUser) {
        if (deployUser != null && !deployUser.isEmpty()) {
            return deployUser;
        }
        try {
            return EnvironmentUtils.getLoginUser(null).getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
