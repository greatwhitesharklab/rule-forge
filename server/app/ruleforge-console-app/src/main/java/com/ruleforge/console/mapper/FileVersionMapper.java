package com.ruleforge.console.mapper;

import com.ruleforge.console.entity.FileVersionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FileVersionMapper extends MyBaseMapper<FileVersionEntity> {
    @Select({
            "<script>",
            "SELECT v.file_id, v.version_num_real, v.file_content, v.file_path",
            "FROM rf_file_version v",
            "JOIN (",
            "  SELECT file_id, MAX(version_num_real) AS max_version",
            "  FROM rf_file_version",
            "  WHERE file_id IN",
            "  <foreach collection='fileIds' item='id' open='(' separator=',' close=')'>",
            "    #{id}",
            "  </foreach>",
            "  GROUP BY file_id",
            ") mv ON v.file_id = mv.file_id AND v.version_num_real = mv.max_version",
            "</script>"
    })
    List<FileVersionEntity> selectLatestVersionByFileIds(@Param("fileIds") List<Long> fileIds);

    @Select({
            "SELECT v.id, v.file_id, v.version_num_real, v.file_content, v.file_path",
            "FROM rf_file_version v",
            "JOIN (",
            "  SELECT file_id, MAX(version_num_real) AS max_version",
            "  FROM rf_file_version",
            "  WHERE project_id = ${projectId}",
            "  AND version_num_real < ${maxVersion}",
            "  GROUP BY file_id",
            ") mv ON v.file_id = mv.file_id AND v.version_num_real = mv.max_version",
    })
    List<FileVersionEntity> selectLatestFileByProjectId(@Param("projectId") Long projectId, @Param("maxVersion") Long maxVersion);
}
