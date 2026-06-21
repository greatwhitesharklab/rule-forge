package com.ruleforge.runtime.cache;

import com.ruleforge.runtime.KnowledgePackage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryKnowledgeCache implements KnowledgeCache {

    private final Map<String, KnowledgePackage> map = new ConcurrentHashMap<>();
    private final Map<String, Boolean> dirtyFlags = new ConcurrentHashMap<>();

    @Override
    public KnowledgePackage getKnowledge(String packageId) {
        return this.map.get(stripLeadingSlash(packageId));
    }

    @Override
    public void putKnowledge(String packageId, KnowledgePackage knowledgePackage) {
        this.map.put(stripLeadingSlash(packageId), knowledgePackage);
    }

    @Override
    public void removeKnowledge(String packageId) {
        this.map.remove(packageId);
    }

    @Override
    public void removeKnowledgeByProjectName(String projectName) {
        this.map.keySet().forEach(key -> {
            if (key.startsWith(projectName)) {
                removeKnowledge(key);
            }
        });
    }

    @Override
    public void markKnowledgeDirty(String fullPackageId) {
        dirtyFlags.put(stripLeadingSlash(fullPackageId), true);
    }

    @Override
    public boolean isKnowledgeDirty(String fullPackageId) {
        return dirtyFlags.getOrDefault(stripLeadingSlash(fullPackageId), false);
    }

    @Override
    public void clearKnowledgeDirty(String fullPackageId) {
        dirtyFlags.remove(stripLeadingSlash(fullPackageId));
    }

    // V6.9.27 — V6.9.14 helper extract: 5 method 3 行 100% 同构 leading-slash strip
    private static String stripLeadingSlash(String id) {
        return id.startsWith("/") ? id.substring(1) : id;
    }
}
