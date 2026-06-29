package com.ruleforge.console.repository.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * V7.7.2:ResourcePackage stub — 老 .rp 知识包元数据类已废弃(原属 .rp 加载/审批管线)。
 * 保留为 POJO 壳以维持 ProjectStorageService / DatabaseProjectStorageServiceImpl /
 * RepositoryReader 等历史接口的编译。**新代码不应再依赖此类型** —
 * 任何"加载项目下资源包"的语义都已被 V1 publish bundle(V7.6+)替代。
 *
 * <p>字段保留以匹配接口契约(全部 getter/setter),不参与任何运行时逻辑。
 */
@Data
public class ResourcePackage {
    private String id;
    private String name;
    private String version;
    private String testVersion;
    private String project;
    private Date createDate;
    private List<ResourceItem> resourceItems;
}
