package com.ruleforge.runtime.cache;

import com.ruleforge.runtime.KnowledgePackage;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MemoryKnowledgeCache implements KnowledgeCache {

    private final Map<String, KnowledgePackage> map = new ConcurrentHashMap<>();
    private final Map<String, Boolean> dirtyFlags = new ConcurrentHashMap<>();

    @Override
    public KnowledgePackage getKnowledge(String packageId) {
        if (packageId.startsWith("/")) {
            packageId = packageId.substring(1);
        }
        return this.map.get(packageId);
    }

    @Override
    public void putKnowledge(String packageId, KnowledgePackage knowledgePackage) {
        if (packageId.startsWith("/")) {
            packageId = packageId.substring(1);
        }
        this.map.put(packageId, knowledgePackage);
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
        if (fullPackageId.startsWith("/")) {
            fullPackageId = fullPackageId.substring(1);
        }
        dirtyFlags.put(fullPackageId, true);
    }

    @Override
    public boolean isKnowledgeDirty(String fullPackageId) {
        if (fullPackageId.startsWith("/")) {
            fullPackageId = fullPackageId.substring(1);
        }
        return dirtyFlags.getOrDefault(fullPackageId, false);
    }

    @Override
    public void clearKnowledgeDirty(String fullPackageId) {
        if (fullPackageId.startsWith("/")) {
            fullPackageId = fullPackageId.substring(1);
        }
        dirtyFlags.remove(fullPackageId);
    }
}
