package com.ruleforge.console.app.v1;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Data;

import java.util.Date;

/**
 * V1 已发布决策流登记(V7.6)— 对应 {@code rf_v1_publish} 表。
 *
 * <p>每个 V1 决策流({@code .v1flow.json})一行:记录当前发布版本号 + 不可变执行闭包
 * ({@code publish_bundle} = {@code {asset, libraries, ruleFiles}} JSON)。无行 = draft,有行 = published。
 *
 * <p>替代老 urule 的 {@code .rp} 知识包管线(组装/审批/影子/批测),V1 原生发布只两态:
 * draft / published,无审批。发布时 git 打 tag {@code v1pub/{flow}/{version}} 做源码追溯
 * (best-effort,无 git 仓则 {@code current_git_tag} 留空,bundle 仍在 DB)。
 *
 * <p>权限域:ruleforge_db(跟 {@code rf_file} / {@code rf_draft} 同源)。
 *
 * @see com.ruleforge.console.controller.v1.V1PublishController
 */
@Data
@TableName("rf_v1_publish")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
                getterVisibility = JsonAutoDetect.Visibility.NONE,
                isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class V1PublishEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("project")
    private String project;

    @TableField("flow_path")
    private String flowPath;

    @TableField("current_version")
    private String currentVersion;

    @TableField("current_git_tag")
    private String currentGitTag;

    /** {asset, libraries, ruleFiles} JSON — 发布时刻冻结的执行闭包,不可变。 */
    @TableField("publish_bundle")
    private String publishBundle;

    @TableField("publish_user")
    private String publishUser;

    @TableField("publish_time")
    private Date publishTime;
}
