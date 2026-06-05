package com.ruleforge.console.repository.data;

import com.ruleforge.console.entity.DeploymentConfigEntity;
import com.ruleforge.console.entity.ExecutorNodeEntity;
import com.ruleforge.console.entity.ProjectRuntimeConfigEntity;
import com.ruleforge.console.entity.ProjectRuntimeFlowEntity;

import java.util.Collection;
import java.util.List;

/**
 * Data access repository for runtime config, runtime flow, deployment config, and executor node entities.
 */
public interface RuntimeRepository {

    // ---- ProjectRuntimeConfigEntity ----

    ProjectRuntimeConfigEntity findConfigByPackage(Long projectId, String packageId, String execEnv);

    ProjectRuntimeConfigEntity findConfigByProjectIdAndEnv(Long projectId, String execEnv);

    List<ProjectRuntimeConfigEntity> findConfigsByProjectId(Long projectId);

    void upsertConfig(Long projectId, String packageId, String execEnv, String version, String updateUser);

    // ---- ProjectRuntimeFlowEntity ----

    long countActiveFlows(Long projectId, String version, String execEnv);

    ProjectRuntimeFlowEntity findFlowByProjectVersionAndEnv(Long projectId, String version, String execEnv, Collection<Integer> auditStatuses);

    ProjectRuntimeFlowEntity findFlowByProjectVersionAndAuditStatus(Long projectId, String version, Integer auditStatus);

    List<ProjectRuntimeFlowEntity> findFlowsByProjectIdAndVersions(Long projectId, Collection<String> versions);

    void updateFlowStatus(Long projectId, String version, String execEnv, Integer auditStatus, String updateUser);

    void insertFlow(ProjectRuntimeFlowEntity entity);

    /**
     * Upsert a test flow: update matching flow or insert new one.
     * @return true if updated, false if inserted
     */
    boolean upsertFlow(Long projectId, String projectVersion, String execEnv,
                       Integer auditStatus, Integer proportion,
                       java.util.Date startTime, java.util.Date endTime,
                       java.util.Date updateTime, String updateUser);

    // ---- DeploymentConfigEntity ----

    DeploymentConfigEntity findCurrentDeployment(Long projectId, String packageId, String execEnv);

    List<DeploymentConfigEntity> findDeploymentsByProjectAndPackage(Long projectId, String packageId);

    void supersedePreviousDeployments(Long projectId, String packageId, String execEnv, Long executorNodeId);

    DeploymentConfigEntity insertDeployment(DeploymentConfigEntity entity);

    // ---- ExecutorNodeEntity ----

    List<ExecutorNodeEntity> findActiveNodes(String execEnv);

    List<ExecutorNodeEntity> findActiveNodesByGroup(String execEnv, String nodeGroup);

    ExecutorNodeEntity findNodeByName(String nodeName);

    void updateNode(ExecutorNodeEntity entity);

    void updateNodeGroup(Long nodeId, String nodeGroup);

    void updateHeartbeat(Long nodeId);

    ExecutorNodeEntity insertNode(ExecutorNodeEntity entity);
}
