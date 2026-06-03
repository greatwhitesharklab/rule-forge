package com.ruleforge.console.service.impl;

import com.ruleforge.console.entity.DeploymentConfigEntity;
import com.ruleforge.console.entity.ExecutorNodeEntity;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {

    private final RuntimeRepository runtimeRepository;

    @Override
    public ExecutorNodeEntity registerNode(String nodeName, String nodeUrl, String execEnv) {
        // Check if node already exists by name
        ExecutorNodeEntity existing = runtimeRepository.findNodeByName(nodeName);
        if (existing != null) {
            // Update URL and env
            existing.setNodeUrl(nodeUrl);
            existing.setExecEnv(execEnv);
            existing.setStatus("active");
            existing.setUpdateTime(new Date());
            runtimeRepository.updateNode(existing);
            return existing;
        }

        ExecutorNodeEntity node = new ExecutorNodeEntity();
        node.setNodeName(nodeName);
        node.setNodeUrl(nodeUrl);
        node.setExecEnv(execEnv);
        node.setStatus("active");
        node.setCreateTime(new Date());
        runtimeRepository.insertNode(node);
        log.info("Registered executor node [{}] at [{}] for env [{}]", nodeName, nodeUrl, execEnv);
        return node;
    }

    @Override
    public List<ExecutorNodeEntity> listNodes(String execEnv) {
        return runtimeRepository.findActiveNodes(execEnv);
    }

    @Override
    public void updateHeartbeat(Long nodeId) {
        runtimeRepository.updateHeartbeat(nodeId);
    }

    @Override
    public DeploymentConfigEntity deploy(Long projectId, String packageId, String gitTag,
                                          String version, String execEnv,
                                          Long executorNodeId, String deployUser) {
        // Mark previous deployment as superseded
        runtimeRepository.supersedePreviousDeployments(projectId, packageId, execEnv, executorNodeId);

        DeploymentConfigEntity config = new DeploymentConfigEntity();
        config.setProjectId(projectId);
        config.setPackageId(packageId);
        config.setExecutorNodeId(executorNodeId);
        config.setGitTag(gitTag);
        config.setProjectVersion(version);
        config.setExecEnv(execEnv);
        config.setDeployStatus("deployed");
        config.setDeployTime(new Date());
        config.setDeployUser(deployUser);
        config.setCreateTime(new Date());
        runtimeRepository.insertDeployment(config);

        log.info("Deployed package [{}/{}] version [{}] to env [{}] (tag={})",
                projectId, packageId, version, execEnv, gitTag);
        return config;
    }

    @Override
    public DeploymentConfigEntity getCurrentDeployment(Long projectId, String packageId, String execEnv) {
        return runtimeRepository.findCurrentDeployment(projectId, packageId, envOrDefault(execEnv));
    }

    @Override
    public List<DeploymentConfigEntity> getDeployments(Long projectId, String packageId) {
        return runtimeRepository.findDeploymentsByProjectAndPackage(projectId, packageId);
    }

    @Override
    public DeploymentConfigEntity rollback(Long projectId, String packageId,
                                            String targetGitTag, String targetVersion,
                                            String execEnv, String deployUser) {
        return deploy(projectId, packageId, targetGitTag, targetVersion,
                envOrDefault(execEnv), null, deployUser);
    }

    @Override
    public List<DeploymentConfigEntity> deployToGroup(Long projectId, String packageId, String gitTag,
                                                       String version, String execEnv,
                                                       String nodeGroup, String deployUser) {
        List<ExecutorNodeEntity> nodes = runtimeRepository.findActiveNodesByGroup(execEnv, nodeGroup);
        if (nodes.isEmpty()) {
            throw new IllegalStateException("No active nodes found in group [" + nodeGroup + "] for env [" + execEnv + "]");
        }

        String env = envOrDefault(execEnv);
        List<DeploymentConfigEntity> results = new ArrayList<>();
        for (ExecutorNodeEntity node : nodes) {
            DeploymentConfigEntity config = deploy(projectId, packageId, gitTag, version,
                    env, node.getId(), deployUser);
            results.add(config);
        }
        log.info("Deployed package [{}/{}] version [{}] to group [{}] in env [{}] ({} nodes)",
                projectId, packageId, version, nodeGroup, env, nodes.size());
        return results;
    }

    private String envOrDefault(String execEnv) {
        return (execEnv != null && !execEnv.isEmpty()) ? execEnv : "default";
    }
}
