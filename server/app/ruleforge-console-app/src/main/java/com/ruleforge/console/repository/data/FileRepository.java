package com.ruleforge.console.repository.data;

import com.ruleforge.console.entity.FileEntity;
import com.ruleforge.console.entity.FileRelationEntity;
import com.ruleforge.console.entity.FileVersionEntity;

import java.util.Collection;
import java.util.List;

/**
 * Data access repository for file and file version entities.
 */
public interface FileRepository {

    // ---- FileEntity ----

    FileEntity findByFilePath(String filePath);

    FileEntity findByFilePathWithType(String filePath, Integer fileType);

    FileEntity findByFilePathSelectId(String filePath);

    boolean existsByFilePath(String filePath);

    List<FileEntity> findByProjectId(Long projectId);

    List<FileEntity> findByProjectIdExcludingTypes(Long projectId, Integer... excludeTypes);

    List<FileEntity> findByFilePathIn(Collection<String> paths);

    FileEntity findByFilePathNeType(String filePath, Integer... excludeTypes);

    List<FileEntity> findChildrenByAncestor(Long ancestorId);

    FileEntity insert(FileEntity entity);

    void updateById(FileEntity entity);

    void deleteById(Long id);

    void deleteByProjectId(Long projectId);

    // ---- FileVersionEntity ----

    FileVersionEntity findSnapshotByFilePath(String filePath);

    FileVersionEntity findSnapshotByFileId(Long fileId);

    FileVersionEntity findLatestReleaseByFileId(Long fileId);

    FileVersionEntity findLatestReleaseByFilePath(String filePath);

    FileVersionEntity findByFilePathAndVersion(String filePath, String versionNum);

    FileVersionEntity findLatestByFileId(Long fileId, boolean packageFile);

    FileVersionEntity findLatestReleaseByFilePathFull(String filePath);

    FileVersionEntity findByFileIdAndVersionNum(Long fileId, String versionNum);

    FileVersionEntity findByFilePathForRead(String filePath, String version, String projectVersion, boolean containSnapshot);

    List<FileVersionEntity> findVersionsByFilePath(String filePath, boolean desc, int page, int row, boolean containLatest);

    long countVersionsByFilePath(String filePath);

    List<FileVersionEntity> findSnapshotsByProjectId(Long projectId);

    /**
     * 5.10-B: 列出项目下所有版本(升序 versionNumReal),用于 Git migration backfill.
     * 与 findSnapshotsByProjectId 不同:这个返所有版本号,不限 SNAPSHOT.
     */
    List<FileVersionEntity> findVersionsByProjectId(Long projectId);

    List<FileVersionEntity> findByProjectIdAndProjectVersionNumReal(Long projectId, Long versionNumReal);

    FileVersionEntity insert(FileVersionEntity entity);

    void updateVersionNumAndReal(Long id, String versionNum, Long versionNumReal, Long projectVersionNumReal);

    void updateProjectVersionNumReal(Long projectId, Long fromVersion, Long toVersion);

    void updateGitCommitSha(String filePath, String versionNum, String gitCommitSha);

    void deleteByFilePath(String filePath);

    void updateContentByVersionNum(String filePath, String versionNum, String fileContent);

    int batchInsert(List<FileVersionEntity> entities);

    // ---- Delegates to FileVersionMapper custom @Select methods ----

    List<FileVersionEntity> selectLatestVersionByFileIds(List<Long> fileIds);

    List<FileVersionEntity> selectLatestFileByProjectId(Long projectId, Long maxVersion);

    List<FileVersionEntity> selectBatchByIds(Collection<Long> ids);

    // ---- FileRelationEntity ----

    List<FileRelationEntity> findRelationsByAncestor(Long ancestorId);

    List<FileRelationEntity> findRelationsByDescendant(Long descendantId);

    List<FileRelationEntity> findRelationsByProjectId(Long projectId);

    void insertRelation(FileRelationEntity entity);

    int batchInsertRelations(List<FileRelationEntity> entities);

    void deleteRelationsByDescendant(Long descendantId);

    void deleteRelationsByAncestor(Long ancestorId);

    void deleteRelationsByProjectId(Long projectId);

    // ---- FileEntity delete operations ----

    void deleteFileById(Long id);

    void deleteFilesByIdOrProjectId(Long projectId);

    void deleteFileVersionsByProjectId(Long projectId);

    void deleteFileVersionsByFileId(Long fileId);

    void deleteFileVersionsByFilePath(String filePath);
}
