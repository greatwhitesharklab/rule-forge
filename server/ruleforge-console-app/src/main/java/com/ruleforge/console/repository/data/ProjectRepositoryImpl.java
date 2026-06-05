package com.ruleforge.console.repository.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectVersionEntity;
import com.ruleforge.console.entity.ProjectVersionMappingEntity;
import com.ruleforge.console.mapper.ProjectMapper;
import com.ruleforge.console.mapper.ProjectVersionMapper;
import com.ruleforge.console.mapper.ProjectVersionMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectRepositoryImpl implements ProjectRepository {

    private final ProjectMapper projectMapper;
    private final ProjectVersionMapper projectVersionMapper;
    private final ProjectVersionMappingMapper projectVersionMappingMapper;

    // ---- ProjectEntity ----

    @Override
    public ProjectEntity findById(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public ProjectEntity findByName(String name) {
        return projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getName, name)
                .last("limit 1"));
    }

    @Override
    public List<ProjectEntity> findAll() {
        return projectMapper.selectList(null);
    }

    @Override
    public List<ProjectEntity> findByIdGreaterThanZero(String name) {
        LambdaQueryWrapper<ProjectEntity> wrapper = new LambdaQueryWrapper<ProjectEntity>()
                .select(ProjectEntity::getId)
                .gt(ProjectEntity::getId, 0);
        if (name != null && !name.isEmpty()) {
            wrapper.eq(ProjectEntity::getName, name);
        }
        return projectMapper.selectList(wrapper);
    }

    @Override
    public ProjectEntity findByNameSelectId(String name) {
        return projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .select(ProjectEntity::getId)
                .eq(ProjectEntity::getName, name)
                .last("limit 1"));
    }

    @Override
    public ProjectEntity insert(ProjectEntity entity) {
        projectMapper.insert(entity);
        return entity;
    }

    // ---- ProjectVersionEntity ----

    @Override
    public ProjectVersionEntity findVersionByProjectIdAndVersionName(Long projectId, String versionName) {
        return projectVersionMapper.selectOne(new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId)
                .eq(ProjectVersionEntity::getVersionName, versionName)
                .last("limit 1"));
    }

    @Override
    public ProjectVersionEntity findLatestVersionByProjectId(Long projectId) {
        return projectVersionMapper.selectOne(new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId)
                .orderByDesc(ProjectVersionEntity::getVersionNumReal)
                .last("limit 1"));
    }

    @Override
    public List<ProjectVersionEntity> findVersionsByProjectId(Long projectId, String packageId, boolean desc) {
        LambdaQueryWrapper<ProjectVersionEntity> wrapper = new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId);
        if (packageId != null) {
            wrapper.eq(ProjectVersionEntity::getPackageId, packageId);
        }
        if (desc) {
            wrapper.orderByDesc(ProjectVersionEntity::getVersionNumReal);
        } else {
            wrapper.orderByAsc(ProjectVersionEntity::getVersionNumReal);
        }
        return projectVersionMapper.selectList(wrapper);
    }

    @Override
    public List<ProjectVersionEntity> findVersionsByProjectIdPaged(Long projectId, boolean desc, int page, int row) {
        LambdaQueryWrapper<ProjectVersionEntity> wrapper = new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId);
        if (desc) {
            wrapper.orderByDesc(ProjectVersionEntity::getVersionNumReal);
        } else {
            wrapper.orderByAsc(ProjectVersionEntity::getVersionNumReal);
        }
        if (row > 0 && page > 0) {
            wrapper.last("limit " + (page - 1) * row + "," + row);
        }
        return projectVersionMapper.selectList(wrapper);
    }

    @Override
    public List<ProjectVersionEntity> findVersionsByProjectIdOrderByCreateTime(Long projectId) {
        return projectVersionMapper.selectList(new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId)
                .orderByDesc(ProjectVersionEntity::getCreateTime));
    }

    @Override
    public ProjectVersionEntity findPreviousVersion(Long projectId, Long versionNumReal) {
        return projectVersionMapper.selectOne(new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId)
                .lt(ProjectVersionEntity::getVersionNumReal, versionNumReal)
                .orderByDesc(ProjectVersionEntity::getVersionNumReal)
                .last("limit 1"));
    }

    @Override
    public ProjectVersionEntity findVersionById(Long id) {
        return projectVersionMapper.selectById(id);
    }

    @Override
    public ProjectVersionEntity insertVersion(ProjectVersionEntity entity) {
        projectVersionMapper.insert(entity);
        return entity;
    }

    @Override
    public void updateVersion(ProjectVersionEntity entity) {
        projectVersionMapper.updateById(entity);
    }

    @Override
    public int batchInsertVersions(List<ProjectVersionEntity> entities) {
        return projectVersionMapper.insertBatchSomeColumn(entities);
    }

    @Override
    public void deleteByName(String name) {
        projectMapper.delete(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getName, name));
    }

    @Override
    public void deleteVersionsByProjectId(Long projectId) {
        projectVersionMapper.delete(new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId));
    }

    // ---- ProjectVersionMappingEntity ----

    @Override
    public List<ProjectVersionMappingEntity> findMappingsByProjectVersionId(Long projectVersionId) {
        return projectVersionMappingMapper.selectList(new LambdaQueryWrapper<ProjectVersionMappingEntity>()
                .eq(ProjectVersionMappingEntity::getProjectVersionId, projectVersionId));
    }

    @Override
    public int batchInsertMappings(List<ProjectVersionMappingEntity> entities) {
        return projectVersionMappingMapper.insertBatchSomeColumn(entities);
    }

    @Override
    public void deleteMappingsByProjectId(Long projectId) {
        projectVersionMappingMapper.delete(new LambdaQueryWrapper<ProjectVersionMappingEntity>()
                .eq(ProjectVersionMappingEntity::getProjectId, projectId));
    }
}
