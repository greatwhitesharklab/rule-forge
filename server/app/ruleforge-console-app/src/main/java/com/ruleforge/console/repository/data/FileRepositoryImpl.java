package com.ruleforge.console.repository.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruleforge.console.entity.FileEntity;
import com.ruleforge.console.entity.FileRelationEntity;
import com.ruleforge.console.entity.FileVersionEntity;
import com.ruleforge.console.mapper.FileMapper;
import com.ruleforge.console.mapper.FileRelationMapper;
import com.ruleforge.console.mapper.FileVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

import static com.ruleforge.console.storage.impl.DatabaseProjectStorageServiceImpl.SNAPSHOT_VERSION;
import static com.ruleforge.console.storage.impl.DatabaseProjectStorageServiceImpl.SNAPSHOT_VERSION_REAL;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileRepositoryImpl implements FileRepository {

    private final FileMapper fileMapper;
    private final FileVersionMapper fileVersionMapper;
    private final FileRelationMapper fileRelationMapper;

    // ---- FileEntity ----

    @Override
    public FileEntity findByFilePath(String filePath) {
        return fileMapper.selectOne(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getFilePath, filePath)
                .last("limit 1"));
    }

    @Override
    public FileEntity findByFilePathWithType(String filePath, Integer fileType) {
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<FileEntity>()
                .select(FileEntity::getId, FileEntity::getFileType)
                .eq(FileEntity::getFilePath, filePath)
                .last("limit 1");
        if (fileType != null) {
            wrapper.eq(FileEntity::getFileType, fileType);
        }
        return fileMapper.selectOne(wrapper);
    }

    @Override
    public FileEntity findByFilePathSelectId(String filePath) {
        return fileMapper.selectOne(new LambdaQueryWrapper<FileEntity>()
                .select(FileEntity::getId)
                .eq(FileEntity::getFilePath, filePath)
                .last("limit 1"));
    }

    @Override
    public boolean existsByFilePath(String filePath) {
        FileEntity file = fileMapper.selectOne(new LambdaQueryWrapper<FileEntity>()
                .select(FileEntity::getId)
                .eq(FileEntity::getFilePath, filePath)
                .last("limit 1"));
        return file != null;
    }

    @Override
    public List<FileEntity> findByProjectId(Long projectId) {
        return fileMapper.selectList(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getProjectId, projectId));
    }

    @Override
    public List<FileEntity> findByProjectIdExcludingTypes(Long projectId, Integer... excludeTypes) {
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getProjectId, projectId);
        for (Integer type : excludeTypes) {
            wrapper.ne(FileEntity::getFileType, type);
        }
        return fileMapper.selectList(wrapper);
    }

    @Override
    public List<FileEntity> findByFilePathIn(Collection<String> paths) {
        return fileMapper.selectList(new LambdaQueryWrapper<FileEntity>()
                .in(FileEntity::getFilePath, paths));
    }

    @Override
    public FileEntity findByFilePathNeType(String filePath, Integer... excludeTypes) {
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getFilePath, filePath);
        for (Integer type : excludeTypes) {
            wrapper.ne(FileEntity::getFileType, type);
        }
        wrapper.last("limit 1");
        return fileMapper.selectOne(wrapper);
    }

    @Override
    public List<FileEntity> findChildrenByAncestor(Long ancestorId) {
        LambdaQueryWrapper<FileRelationEntity> wrapper = new LambdaQueryWrapper<FileRelationEntity>()
                .eq(FileRelationEntity::getAncestor, ancestorId)
                .eq(FileRelationEntity::getDistance, 1);
        return fileMapper.selectListByAncestor(wrapper);
    }

    @Override
    public FileEntity insert(FileEntity entity) {
        fileMapper.insert(entity);
        return entity;
    }

    @Override
    public void updateById(FileEntity entity) {
        fileMapper.updateById(entity);
    }

    @Override
    public void deleteById(Long id) {
        fileMapper.deleteById(id);
    }

    @Override
    public void deleteByProjectId(Long projectId) {
        fileMapper.delete(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getProjectId, projectId));
    }

    // ---- FileVersionEntity ----

    @Override
    public FileVersionEntity findSnapshotByFilePath(String filePath) {
        return fileVersionMapper.selectOne(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath)
                .eq(FileVersionEntity::getVersionNum, SNAPSHOT_VERSION)
                .last("limit 1"));
    }

    @Override
    public FileVersionEntity findSnapshotByFileId(Long fileId) {
        return fileVersionMapper.selectOne(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFileId, fileId)
                .eq(FileVersionEntity::getVersionNum, SNAPSHOT_VERSION)
                .last("limit 1"));
    }

    @Override
    public FileVersionEntity findLatestReleaseByFileId(Long fileId) {
        return fileVersionMapper.selectOne(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFileId, fileId)
                .lt(FileVersionEntity::getVersionNumReal, SNAPSHOT_VERSION_REAL)
                .orderByDesc(FileVersionEntity::getVersionNumReal)
                .last("limit 1"));
    }

    @Override
    public FileVersionEntity findLatestReleaseByFilePath(String filePath) {
        return fileVersionMapper.selectOne(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFileId, filePath)
                .lt(FileVersionEntity::getVersionNumReal, SNAPSHOT_VERSION_REAL)
                .orderByDesc(FileVersionEntity::getVersionNumReal)
                .last("limit 1"));
    }

    @Override
    public FileVersionEntity findByFilePathAndVersion(String filePath, String versionNum) {
        return fileVersionMapper.selectOne(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath)
                .eq(FileVersionEntity::getVersionNum, versionNum)
                .last("limit 1"));
    }

    @Override
    public FileVersionEntity findLatestByFileId(Long fileId, boolean packageFile) {
        LambdaQueryWrapper<FileVersionEntity> wrapper = new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFileId, fileId);
        if (packageFile) {
            wrapper.orderByDesc(FileVersionEntity::getVersionNumReal).last("limit 1");
        } else {
            wrapper.eq(FileVersionEntity::getVersionNum, SNAPSHOT_VERSION).last("limit 1");
        }
        return fileVersionMapper.selectOne(wrapper);
    }

    @Override
    public FileVersionEntity findLatestReleaseByFilePathFull(String filePath) {
        return fileVersionMapper.selectOne(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath)
                .lt(FileVersionEntity::getVersionNumReal, SNAPSHOT_VERSION_REAL)
                .orderByDesc(FileVersionEntity::getVersionNumReal, FileVersionEntity::getId)
                .last("limit 1"));
    }

    @Override
    public FileVersionEntity findByFileIdAndVersionNum(Long fileId, String versionNum) {
        return fileVersionMapper.selectOne(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFileId, fileId)
                .eq(FileVersionEntity::getVersionNum, versionNum)
                .last("limit 1"));
    }

    @Override
    public FileVersionEntity findByFilePathForRead(String filePath, String version, String projectVersion, boolean containSnapshot) {
        LambdaQueryWrapper<FileVersionEntity> wrapper = new LambdaQueryWrapper<FileVersionEntity>()
                .select(FileVersionEntity::getFileContent, FileVersionEntity::getVersionNum)
                .eq(FileVersionEntity::getFilePath, filePath)
                .last("limit 1");
        if (!containSnapshot) {
            wrapper.lt(FileVersionEntity::getVersionNumReal, SNAPSHOT_VERSION_REAL);
            if (projectVersion != null && !projectVersion.isEmpty()) {
                wrapper.le(FileVersionEntity::getProjectVersionNumReal,
                        com.ruleforge.console.util.VersionUtils.convertVersionToLong(projectVersion));
            }
        }
        if (version != null && !version.isEmpty() && !version.equalsIgnoreCase("latest")) {
            wrapper.eq(FileVersionEntity::getVersionNum, version);
        } else {
            wrapper.orderByDesc(FileVersionEntity::getVersionNumReal);
        }
        return fileVersionMapper.selectOne(wrapper);
    }

    @Override
    public List<FileVersionEntity> findVersionsByFilePath(String filePath, boolean desc, int page, int row, boolean containLatest) {
        LambdaQueryWrapper<FileVersionEntity> wrapper = new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath);
        if (!containLatest) {
            wrapper.ne(FileVersionEntity::getVersionNum, "latest");
        }
        if (desc) {
            wrapper.orderByDesc(FileVersionEntity::getCreateDate);
        } else {
            wrapper.orderByAsc(FileVersionEntity::getCreateDate);
        }
        if (row > 0 && page > 0) {
            wrapper.last("limit " + (page - 1) * row + "," + row);
        }
        return fileVersionMapper.selectList(wrapper);
    }

    @Override
    public long countVersionsByFilePath(String filePath) {
        return fileVersionMapper.selectCount(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath)
                .ne(FileVersionEntity::getVersionNum, "latest"));
    }

    @Override
    public List<FileVersionEntity> findSnapshotsByProjectId(Long projectId) {
        return fileVersionMapper.selectList(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getProjectId, projectId)
                .eq(FileVersionEntity::getVersionNum, SNAPSHOT_VERSION));
    }

    @Override
    public List<FileVersionEntity> findVersionsByProjectId(Long projectId) {
        return fileVersionMapper.selectList(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getProjectId, projectId)
                .orderByAsc(FileVersionEntity::getVersionNumReal));
    }

    @Override
    public List<FileVersionEntity> findByProjectIdAndProjectVersionNumReal(Long projectId, Long versionNumReal) {
        return fileVersionMapper.selectList(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getProjectId, projectId)
                .eq(FileVersionEntity::getProjectVersionNumReal, versionNumReal));
    }

    @Override
    public FileVersionEntity insert(FileVersionEntity entity) {
        fileVersionMapper.insert(entity);
        return entity;
    }

    @Override
    public void updateVersionNumAndReal(Long id, String versionNum, Long versionNumReal, Long projectVersionNumReal) {
        fileVersionMapper.update(null, new LambdaUpdateWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getId, id)
                .set(FileVersionEntity::getVersionNum, versionNum)
                .set(FileVersionEntity::getVersionNumReal, versionNumReal)
                .set(FileVersionEntity::getProjectVersionNumReal, projectVersionNumReal)
                .set(FileVersionEntity::getUpdateTime, new java.util.Date()));
    }

    @Override
    public void updateProjectVersionNumReal(Long projectId, Long fromVersion, Long toVersion) {
        fileVersionMapper.update(null, new LambdaUpdateWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getProjectId, projectId)
                .eq(FileVersionEntity::getProjectVersionNumReal, fromVersion)
                .set(FileVersionEntity::getProjectVersionNumReal, toVersion));
    }

    @Override
    public void updateGitCommitSha(String filePath, String versionNum, String gitCommitSha) {
        fileVersionMapper.update(null, new LambdaUpdateWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath)
                .eq(FileVersionEntity::getVersionNum, versionNum)
                .set(FileVersionEntity::getGitCommitSha, gitCommitSha));
    }

    @Override
    public void deleteByFilePath(String filePath) {
        fileVersionMapper.delete(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath));
    }

    @Override
    public void updateContentByVersionNum(String filePath, String versionNum, String fileContent) {
        fileVersionMapper.update(null, new LambdaUpdateWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath)
                .eq(FileVersionEntity::getVersionNum, versionNum)
                .set(FileVersionEntity::getFileContent, fileContent)
                .set(FileVersionEntity::getUpdateTime, new java.util.Date()));
    }

    @Override
    public int batchInsert(List<FileVersionEntity> entities) {
        return fileVersionMapper.insertBatchSomeColumn(entities);
    }

    @Override
    public List<FileVersionEntity> selectLatestVersionByFileIds(List<Long> fileIds) {
        return fileVersionMapper.selectLatestVersionByFileIds(fileIds);
    }

    @Override
    public List<FileVersionEntity> selectLatestFileByProjectId(Long projectId, Long maxVersion) {
        return fileVersionMapper.selectLatestFileByProjectId(projectId, maxVersion);
    }

    @Override
    public List<FileVersionEntity> selectBatchByIds(Collection<Long> ids) {
        return fileVersionMapper.selectBatchIds(ids);
    }

    // ---- FileRelationEntity ----

    @Override
    public List<FileRelationEntity> findRelationsByAncestor(Long ancestorId) {
        return fileRelationMapper.selectList(new LambdaQueryWrapper<FileRelationEntity>()
                .eq(FileRelationEntity::getAncestor, ancestorId));
    }

    @Override
    public List<FileRelationEntity> findRelationsByDescendant(Long descendantId) {
        return fileRelationMapper.selectList(new LambdaQueryWrapper<FileRelationEntity>()
                .eq(FileRelationEntity::getDescendant, descendantId));
    }

    @Override
    public List<FileRelationEntity> findRelationsByProjectId(Long projectId) {
        return fileRelationMapper.selectList(new LambdaQueryWrapper<FileRelationEntity>()
                .in(FileRelationEntity::getProjectId, projectId));
    }

    @Override
    public void insertRelation(FileRelationEntity entity) {
        fileRelationMapper.insert(entity);
    }

    @Override
    public int batchInsertRelations(List<FileRelationEntity> entities) {
        return fileRelationMapper.insertBatchSomeColumn(entities);
    }

    @Override
    public void deleteRelationsByDescendant(Long descendantId) {
        fileRelationMapper.delete(new LambdaQueryWrapper<FileRelationEntity>()
                .eq(FileRelationEntity::getDescendant, descendantId));
    }

    @Override
    public void deleteRelationsByAncestor(Long ancestorId) {
        fileRelationMapper.delete(new LambdaQueryWrapper<FileRelationEntity>()
                .eq(FileRelationEntity::getAncestor, ancestorId));
    }

    @Override
    public void deleteRelationsByProjectId(Long projectId) {
        fileRelationMapper.delete(new LambdaQueryWrapper<FileRelationEntity>()
                .in(FileRelationEntity::getProjectId, projectId));
    }

    // ---- FileEntity delete operations ----

    @Override
    public void deleteFileById(Long id) {
        fileMapper.deleteById(id);
    }

    @Override
    public void deleteFilesByIdOrProjectId(Long projectId) {
        fileMapper.delete(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getId, projectId)
                .or()
                .eq(FileEntity::getProjectId, projectId));
    }

    @Override
    public void deleteFileVersionsByProjectId(Long projectId) {
        fileVersionMapper.delete(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getProjectId, projectId));
    }

    @Override
    public void deleteFileVersionsByFileId(Long fileId) {
        fileVersionMapper.delete(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFileId, fileId));
    }

    @Override
    public void deleteFileVersionsByFilePath(String filePath) {
        fileVersionMapper.delete(new LambdaQueryWrapper<FileVersionEntity>()
                .eq(FileVersionEntity::getFilePath, filePath));
    }
}
