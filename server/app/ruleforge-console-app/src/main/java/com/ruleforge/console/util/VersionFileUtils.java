package com.ruleforge.console.util;

import com.ruleforge.console.repository.model.VersionFile;
import com.ruleforge.console.entity.ProjectVersionEntity;

/**
 * 版本文件工具类
 */
public class VersionFileUtils {

    /**
     * 将项目版本实体转换为版本文件对象
     *
     * @param projectName 项目名称
     * @param entity 项目版本实体
     * @return 版本文件对象
     */
    public static VersionFile getVersionFile(String projectName, ProjectVersionEntity entity) {
        VersionFile file = new VersionFile();
        file.setName(entity.getVersionName()); // 使用项目版本名
        file.setAuditStatus(entity.getAuditStatus() == null ? "" : entity.getAuditStatus().toString());
        file.setPath(projectName); // 路径设为项目名
        file.setVersionNumReal(entity.getVersionNumReal());
        file.setCreateUser(entity.getCreateUser());
        file.setCreateDate(entity.getCreateTime());
        file.setComment(entity.getComment());
        // VersionFile 没有 before/after comment, 设为 null 或空字符串
        file.setBeforeComment(null);
        file.setAfterComment(null);
        // VersionFile 没有 content, 设为 null 或空字符串
        file.setContent(null);
        return file;
    }
}