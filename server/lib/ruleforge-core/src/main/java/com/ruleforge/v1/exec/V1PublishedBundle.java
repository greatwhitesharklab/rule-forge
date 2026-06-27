package com.ruleforge.v1.exec;

import com.ruleforge.v1.ast.NodeBase;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.library.Libraries;

import java.util.Map;

/**
 * V1 决策流的发布闭包(V7.6)— 发布时刻冻结的完整可执行单元。
 *
 * <p>放在 core(非 console-app)因它是 **生产端(console 发布)+ 消费端(executor 执行)的共享契约**:
 * 两个平行 Spring Boot app 都依赖 core,但互不依赖(CLAUDE.md 模块边界),共享类型必须在 core。
 *
 * <p>{@code {asset, libraries, ruleFiles}} 三件套,正好是 {@link V1FlowRunner} 的全量执行输入
 * (去掉 per-request 的 fact/parameters)。executor 拉到 bundle 后直接
 * {@code V1FlowRunner.execute(asset, fact, libraries, ruleFiles)} 即可,无需懂 V1 引用解析。
 *
 * <p>不可变快照:发布那一刻的 flow + 各 ruleRef 规则文件 + 项目库(vl/cl/pl)整体冻结,
 * 后续编辑源文件不影响已发布 bundle(可复现)。
 */
public class V1PublishedBundle {
    private RuleAsset asset;
    private Libraries libraries;
    /** ruleRef → 规则文件顶层 NodeBase(RuleSet/DecisionTable/ScoreCard)。 */
    private Map<String, NodeBase> ruleFiles;

    public V1PublishedBundle() {
    }

    public V1PublishedBundle(RuleAsset asset, Libraries libraries, Map<String, NodeBase> ruleFiles) {
        this.asset = asset;
        this.libraries = libraries;
        this.ruleFiles = ruleFiles;
    }

    public RuleAsset getAsset() {
        return asset;
    }

    public void setAsset(RuleAsset asset) {
        this.asset = asset;
    }

    public Libraries getLibraries() {
        return libraries;
    }

    public void setLibraries(Libraries libraries) {
        this.libraries = libraries;
    }

    public Map<String, NodeBase> getRuleFiles() {
        return ruleFiles;
    }

    public void setRuleFiles(Map<String, NodeBase> ruleFiles) {
        this.ruleFiles = ruleFiles;
    }
}
