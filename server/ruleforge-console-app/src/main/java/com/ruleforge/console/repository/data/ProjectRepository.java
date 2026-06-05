package com.ruleforge.console.repository.data;

import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.entity.ProjectVersionEntity;
import com.ruleforge.console.entity.ProjectVersionMappingEntity;

import java.util.List;

/**
 * Data access repository for project, project version, and project version mapping entities.
 */
public interface ProjectRepository {

    // ---- ProjectEntity ----

    ProjectEntity findById(Long id);

    ProjectEntity findByName(String name);

    List<ProjectEntity> findAll();

    List<ProjectEntity> findByIdGreaterThanZero(String name);

    ProjectEntity findByNameSelectId(String name);

    ProjectEntity insert(ProjectEntity entity);

    // ---- ProjectVersionEntity ----

    ProjectVersionEntity findVersionByProjectIdAndVersionName(Long projectId, String versionName);

    ProjectVersionEntity findLatestVersionByProjectId(Long projectId);

    List<ProjectVersionEntity> findVersionsByProjectId(Long projectId, String packageId, boolean desc);

    List<ProjectVersionEntity> findVersionsByProjectIdPaged(Long projectId, boolean desc, int page, int row);

    List<ProjectVersionEntity> findVersionsByProjectIdOrderByCreateTime(Long projectId);

    ProjectVersionEntity findPreviousVersion(Long projectId, Long versionNumReal);

    ProjectVersionEntity findVersionById(Long id);

    ProjectVersionEntity insertVersion(ProjectVersionEntity entity);

    void updateVersion(ProjectVersionEntity entity);

    int batchInsertVersions(List<ProjectVersionEntity> entities);

    void deleteByName(String name);

    void deleteVersionsByProjectId(Long projectId);

    // ---- ProjectVersionMappingEntity ----

    List<ProjectVersionMappingEntity> findMappingsByProjectVersionId(Long projectVersionId);

    int batchInsertMappings(List<ProjectVersionMappingEntity> entities);

    void deleteMappingsByProjectId(Long projectId);
}
