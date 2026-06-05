package com.ruleforge.console.util;

/**
 * V5.11: Git 路径 / 项目名 工具方法,集中原本散落在
 * {@code RuleForgeRepositoryServiceImpl.extractProjectName} 和
 * {@code FrameController.extractProjectNameFromPath} 的两份重复实现.
 */
public final class GitPathUtils {

    private GitPathUtils() {
    }

    /**
     * 从形如 {@code /projectName/folder/file.xml} 的路径里抠 project 名(第一段).
     *
     * <ul>
     *   <li>{@code null} / 空串 → {@code null}</li>
     *   <li>{@code "/"} → {@code null}(没 project)</li>
     *   <li>{@code "/foo"} → {@code "foo"}</li>
     *   <li>{@code "/foo/bar"} → {@code "foo"}</li>
     *   <li>{@code "foo/bar"} → {@code "foo"}(允许无前导斜杠)</li>
     * </ul>
     */
    public static String extractProjectName(String path) {
        if (path == null || path.isEmpty()) return null;
        String cleaned = path.startsWith("/") ? path.substring(1) : path;
        if (cleaned.isEmpty()) return null;
        int slash = cleaned.indexOf('/');
        return slash > 0 ? cleaned.substring(0, slash) : cleaned;
    }
}
