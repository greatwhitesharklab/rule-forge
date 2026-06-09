package com.ruleforge.console.repository.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruleforge.console.entity.DeploymentConfigEntity;
import com.ruleforge.console.entity.ExecutorNodeEntity;
import com.ruleforge.console.entity.ProjectRuntimeConfigEntity;
import com.ruleforge.console.entity.ProjectRuntimeFlowEntity;
import com.ruleforge.console.mapper.DeploymentConfigMapper;
import com.ruleforge.console.mapper.ExecutorNodeMapper;
import com.ruleforge.console.mapper.ProjectRuntimeConfigMapper;
import com.ruleforge.console.mapper.ProjectRuntimeFlowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeRepositoryImpl implements RuntimeRepository {

    private final ProjectRuntimeConfigMapper projectRuntimeConfigMapper;
    private final ProjectRuntimeFlowMapper projectRuntimeFlowMapper;
    private final DeploymentConfigMapper deploymentConfigMapper;
    private final ExecutorNodeMapper executorNodeMapper;

    // ---- ProjectRuntimeConfigEntity ----

    @Override
    public ProjectRuntimeConfigEntity findConfigByPackage(Long projectId, String packageId, String execEnv) {
        return projectRuntimeConfigMapper.selectOne(new LambdaQueryWrapper<ProjectRuntimeConfigEntity>()
                .eq(ProjectRuntimeConfigEntity::getProjectId, projectId)
                .eq(ProjectRuntimeConfigEntity::getPackageId, packageId)
                .eq(ProjectRuntimeConfigEntity::getExecEnv, execEnv)
                .last("limit 1"));
    }

    @Override
    public ProjectRuntimeConfigEntity findConfigByProjectIdAndEnv(Long projectId, String execEnv) {
        return projectRuntimeConfigMapper.selectOne(new LambdaQueryWrapper<ProjectRuntimeConfigEntity>()
                .eq(ProjectRuntimeConfigEntity::getProjectId, projectId)
                .eq(ProjectRuntimeConfigEntity::getExecEnv, execEnv)
                .last("limit 1"));
    }

    @Override
    public List<ProjectRuntimeConfigEntity> findConfigsByProjectId(Long projectId) {
        return projectRuntimeConfigMapper.selectList(new LambdaQueryWrapper<ProjectRuntimeConfigEntity>()
                .eq(ProjectRuntimeConfigEntity::getProjectId, projectId));
    }

    @Override
    public void upsertConfig(Long projectId, String packageId, String execEnv, String version, String updateUser) {
        ProjectRuntimeConfigEntity existing = findConfigByPackage(projectId, packageId, execEnv);
        if (existing != null) {
            existing.setProjectVersion(version);
            existing.setUpdateUser(updateUser);
            existing.setUpdateTime(new Date());
            projectRuntimeConfigMapper.updateById(existing);
        } else {
            ProjectRuntimeConfigEntity entity = new ProjectRuntimeConfigEntity();
            entity.setProjectId(projectId);
            entity.setPackageId(packageId);
            entity.setExecEnv(execEnv);
            entity.setProjectVersion(version);
            entity.setCreateUser(updateUser);
            entity.setCreateTime(new Date());
            entity.setUpdateUser(updateUser);
            entity.setUpdateTime(new Date());
            projectRuntimeConfigMapper.insert(entity);
        }
    }

    // ---- ProjectRuntimeFlowEntity ----

    @Override
    public long countActiveFlows(Long projectId, String version, String execEnv) {
        return projectRuntimeFlowMapper.selectCount(new LambdaQueryWrapper<ProjectRuntimeFlowEntity>()
                .in(ProjectRuntimeFlowEntity::getAuditStatus, 20, 90, 91)
                .eq(ProjectRuntimeFlowEntity::getProjectVersion, version)
                .eq(ProjectRuntimeFlowEntity::getExecEnv, execEnv)
                .eq(ProjectRuntimeFlowEntity::getProjectId, projectId));
    }

    @Override
    public ProjectRuntimeFlowEntity findFlowByProjectVersionAndEnv(Long projectId, String version, String execEnv, Collection<Integer> auditStatuses) {
        LambdaQueryWrapper<ProjectRuntimeFlowEntity> wrapper = new LambdaQueryWrapper<ProjectRuntimeFlowEntity>()
                .eq(ProjectRuntimeFlowEntity::getProjectId, projectId)
                .eq(ProjectRuntimeFlowEntity::getProjectVersion, version)
                .eq(ProjectRuntimeFlowEntity::getExecEnv, execEnv);
        if (auditStatuses != null && !auditStatuses.isEmpty()) {
            wrapper.in(ProjectRuntimeFlowEntity::getAuditStatus, auditStatuses);
        }
        return projectRuntimeFlowMapper.selectOne(wrapper.last("limit 1"));
    }

    @Override
    public ProjectRuntimeFlowEntity findFlowByProjectVersionAndAuditStatus(Long projectId, String version, Integer auditStatus) {
        return projectRuntimeFlowMapper.selectOne(new LambdaQueryWrapper<ProjectRuntimeFlowEntity>()
                .eq(ProjectRuntimeFlowEntity::getProjectId, projectId)
                .eq(ProjectRuntimeFlowEntity::getProjectVersion, version)
                .eq(ProjectRuntimeFlowEntity::getAuditStatus, auditStatus)
                .last("limit 1"));
    }

    @Override
    public List<ProjectRuntimeFlowEntity> findFlowsByProjectIdAndVersions(Long projectId, Collection<String> versions) {
        return projectRuntimeFlowMapper.selectList(new LambdaQueryWrapper<ProjectRuntimeFlowEntity>()
                .eq(ProjectRuntimeFlowEntity::getProjectId, projectId)
                .in(ProjectRuntimeFlowEntity::getProjectVersion, versions));
    }

    @Override
    public void updateFlowStatus(Long projectId, String version, String execEnv, Integer auditStatus, String updateUser) {
        projectRuntimeFlowMapper.update(null, new LambdaUpdateWrapper<ProjectRuntimeFlowEntity>()
                .eq(ProjectRuntimeFlowEntity::getProjectId, projectId)
                .eq(ProjectRuntimeFlowEntity::getProjectVersion, version)
                .eq(ProjectRuntimeFlowEntity::getExecEnv, execEnv)
                .set(ProjectRuntimeFlowEntity::getAuditStatus, auditStatus)
                .set(ProjectRuntimeFlowEntity::getUpdateTime, new Date())
                .set(ProjectRuntimeFlowEntity::getUpdateUser, updateUser));
    }

    @Override
    public void insertFlow(ProjectRuntimeFlowEntity entity) {
        projectRuntimeFlowMapper.insert(entity);
    }

    @Override
    public boolean upsertFlow(Long projectId, String projectVersion, String execEnv,
                              Integer auditStatus, Integer proportion,
                              Date startTime, Date endTime,
                              Date updateTime, String updateUser) {
        int updated = projectRuntimeFlowMapper.update(null, new LambdaUpdateWrapper<ProjectRuntimeFlowEntity>()
                .eq(ProjectRuntimeFlowEntity::getProjectId, projectId)
                .eq(ProjectRuntimeFlowEntity::getExecEnv, execEnv)
                .eq(ProjectRuntimeFlowEntity::getProjectVersion, projectVersion)
                .set(ProjectRuntimeFlowEntity::getAuditStatus, auditStatus)
                .set(ProjectRuntimeFlowEntity::getProportion, proportion)
                .set(ProjectRuntimeFlowEntity::getStartTime, startTime)
                .set(ProjectRuntimeFlowEntity::getEndTime, endTime)
                .set(ProjectRuntimeFlowEntity::getUpdateTime, updateTime)
                .set(ProjectRuntimeFlowEntity::getUpdateUser, updateUser));
        return updated > 0;
    }

    // ---- DeploymentConfigEntity ----

    @Override
    public List<DeploymentConfigEntity> findDeploymentsByProjectAndPackage(Long projectId, String packageId) {
        return deploymentConfigMapper.selectList(new LambdaQueryWrapper<DeploymentConfigEntity>()
                .eq(DeploymentConfigEntity::getProjectId, projectId)
                .eq(DeploymentConfigEntity::getPackageId, packageId)
                .orderByDesc(DeploymentConfigEntity::getDeployTime));
    }

    @Override
    public DeploymentConfigEntity findCurrentDeployment(Long projectId, String packageId, String execEnv) {
        String env = (execEnv != null && !execEnv.isEmpty()) ? execEnv : "default";
        return deploymentConfigMapper.selectOne(new LambdaQueryWrapper<DeploymentConfigEntity>()
                .eq(DeploymentConfigEntity::getProjectId, projectId)
                .eq(DeploymentConfigEntity::getPackageId, packageId)
                .eq(DeploymentConfigEntity::getExecEnv, env)
                .eq(DeploymentConfigEntity::getDeployStatus, "deployed")
                .orderByDesc(DeploymentConfigEntity::getDeployTime)
                .last("limit 1"));
    }

    @Override
    public void supersedePreviousDeployments(Long projectId, String packageId, String execEnv, Long executorNodeId) {
        String env = (execEnv != null && !execEnv.isEmpty()) ? execEnv : "default";
        LambdaUpdateWrapper<DeploymentConfigEntity> wrapper = new LambdaUpdateWrapper<DeploymentConfigEntity>()
                .eq(DeploymentConfigEntity::getProjectId, projectId)
                .eq(DeploymentConfigEntity::getPackageId, packageId)
                .eq(DeploymentConfigEntity::getExecEnv, env)
                .eq(DeploymentConfigEntity::getDeployStatus, "deployed")
                .set(DeploymentConfigEntity::getDeployStatus, "superseded");
        if (executorNodeId != null) {
            wrapper.eq(DeploymentConfigEntity::getExecutorNodeId, executorNodeId);
        }
        deploymentConfigMapper.update(null, wrapper);
    }

    @Override
    public DeploymentConfigEntity insertDeployment(DeploymentConfigEntity entity) {
        deploymentConfigMapper.insert(entity);
        return entity;
    }

    // ---- ExecutorNodeEntity ----

    @Override
    public List<ExecutorNodeEntity> findActiveNodes(String execEnv) {
        LambdaQueryWrapper<ExecutorNodeEntity> query = new LambdaQueryWrapper<>();
        if (execEnv != null && !execEnv.isEmpty()) {
            query.eq(ExecutorNodeEntity::getExecEnv, execEnv);
        }
        query.eq(ExecutorNodeEntity::getStatus, "active");
        return executorNodeMapper.selectList(query);
    }

    @Override
    public List<ExecutorNodeEntity> findActiveNodesByGroup(String execEnv, String nodeGroup) {
        LambdaQueryWrapper<ExecutorNodeEntity> query = new LambdaQueryWrapper<>();
        if (execEnv != null && !execEnv.isEmpty()) {
            query.eq(ExecutorNodeEntity::getExecEnv, execEnv);
        }
        query.eq(ExecutorNodeEntity::getNodeGroup, nodeGroup)
             .eq(ExecutorNodeEntity::getStatus, "active");
        return executorNodeMapper.selectList(query);
    }

    @Override
    public ExecutorNodeEntity findNodeByName(String nodeName) {
        return executorNodeMapper.selectOne(new LambdaQueryWrapper<ExecutorNodeEntity>()
                .eq(ExecutorNodeEntity::getNodeName, nodeName));
    }

    @Override
    public void updateNode(ExecutorNodeEntity entity) {
        executorNodeMapper.updateById(entity);
    }

    @Override
    public void updateNodeGroup(Long nodeId, String nodeGroup) {
        executorNodeMapper.update(null, new LambdaUpdateWrapper<ExecutorNodeEntity>()
                .eq(ExecutorNodeEntity::getId, nodeId)
                .set(ExecutorNodeEntity::getNodeGroup, nodeGroup)
                .set(ExecutorNodeEntity::getUpdateTime, new Date()));
    }

    @Override
    public void updateHeartbeat(Long nodeId) {
        executorNodeMapper.update(null, new LambdaUpdateWrapper<ExecutorNodeEntity>()
                .eq(ExecutorNodeEntity::getId, nodeId)
                .set(ExecutorNodeEntity::getLastHeartbeat, new Date()));
    }

    @Override
    public ExecutorNodeEntity insertNode(ExecutorNodeEntity entity) {
        executorNodeMapper.insert(entity);
        return entity;
    }
}
