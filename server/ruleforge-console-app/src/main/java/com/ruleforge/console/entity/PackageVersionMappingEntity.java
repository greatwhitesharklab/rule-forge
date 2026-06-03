package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_package_version_mapping")
public class PackageVersionMappingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long packageVersionId;
    private String filePath;
    private String gitBlobSha;
    private Date createTime;
}
