package com.ruleforge.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("gr_package_version")
public class PackageVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String packageId;
    private String name;
    private String resourceItems;
    private long projectId;
    private String versionNum;
    private Long versionNumReal;
    private Date createDate;
    private Date updateDate;
}
