package com.ruleforge.runtime.service;

import com.ruleforge.runtime.KnowledgePackage;

/**
 * V7.7.2:KnowledgePackageService stub 接口 — 老 .rp 知识包构建服务已废弃。
 * 保留接口签名以维持 KnowledgeServiceImpl(已简化为 cache-only)的注释完整性。
 * 实际生产代码不应再引用此类型。
 */
public interface KnowledgePackageService {
    KnowledgePackage buildKnowledgePackage(String packageId);
    boolean isKnowledgePackageNeedUpdate(String packageId);
}
