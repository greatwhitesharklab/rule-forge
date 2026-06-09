package com.ruleforge.runtime.cache;

import com.ruleforge.runtime.KnowledgePackage;

/**
 * @author Jacky.gao
 * 2015年1月28日
 */
public interface KnowledgeCache {
    String BEAN_ID = "ruleforge.knowledgeCache";

    /**
     * 根据环境和包ID获取知识包
     *
     * @param fullPackageId 完整包ID
     * @return 知识包，如果不存在则返回null
     */
    KnowledgePackage getKnowledge(String fullPackageId);

    /**
     * 向指定环境缓存知识包
     *
     * @param fullPackageId    完整包ID
     * @param knowledgePackage 知识包
     */
    void putKnowledge(String fullPackageId, KnowledgePackage knowledgePackage);

    /**
     * 从指定环境移除知识包
     *
     * @param fullPackageId 完整包ID
     */
    void removeKnowledge(String fullPackageId);

    /**
     * 根据项目名称从指定环境移除所有相关知识包
     *
     * @param projectName 项目名称
     */
    void removeKnowledgeByProjectName(String projectName);

    /**
     * 标记指定环境下的知识包需要更新
     * 新增：标记知识包需要更新
     *
     * @param fullPackageId 完整包ID
     */
    void markKnowledgeDirty(String fullPackageId);

    /**
     * 检查指定环境下的知识包是否被标记为需要更新
     * 新增：检查知识包是否需要更新
     *
     * @param fullPackageId 完整包ID
     * @return true 如果需要更新, false 则不需要
     */
    boolean isKnowledgeDirty(String fullPackageId);

    /**
     * Clear the dirty flag for a knowledge package after refresh.
     *
     * @param fullPackageId full package ID
     */
    void clearKnowledgeDirty(String fullPackageId);
}
