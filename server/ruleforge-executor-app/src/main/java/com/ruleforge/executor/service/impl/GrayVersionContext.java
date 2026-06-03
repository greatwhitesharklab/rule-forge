package com.ruleforge.executor.service.impl;

/**
 * 灰度版本上下文 — ThreadLocal 传递灰度 gitTag 到 KnowledgePackageServiceImpl
 *
 * 使用方式:
 * <pre>
 *   try {
 *       GrayVersionContext.set("1.0.5");
 *       knowledgeService.getKnowledge(packagePath);
 *   } finally {
 *       GrayVersionContext.clear();
 *   }
 * </pre>
 */
public class GrayVersionContext {

    private static final ThreadLocal<String> GIT_TAG = new ThreadLocal<>();

    public static void set(String gitTag) {
        GIT_TAG.set(gitTag);
    }

    public static String get() {
        return GIT_TAG.get();
    }

    public static void clear() {
        GIT_TAG.remove();
    }
}
