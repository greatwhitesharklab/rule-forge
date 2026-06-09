package com.ruleforge.console.app.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.draft.DraftEntity;
import com.ruleforge.console.app.draft.DraftMapper;
import com.ruleforge.datasource.BaseApiDataSource;
import com.ruleforge.datasource.DataSourceRegistry;
import com.ruleforge.datasource.JavaSourceCompiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V5.23 — Apply an approved data source draft.
 *
 * <p>Flow: APPROVED draft → JavaSourceCompiler → load .class → DataSourceRegistry.register.
 * Compiled .class bytes are persisted to the project git repo under
 * {@code data_sources/<draftId>/<className>.class} for executor-app to load at startup.
 *
 * <h2>Class naming convention</h2>
 * <p>Data source class FQCN is hardcoded to {@code com.ruleforge.console.datasource.generated.<ClassName>}
 * (LLM prompt enforces this). ClassName is derived from the draftId (e.g. {@code ds_abc123}
 * → {@code DsAbc123DataSource}). This makes the class name predictable and the git path stable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceApplyService {

    /** Hardcoded package for all LLM-generated data source classes. */
    public static final String GENERATED_PACKAGE = "com.ruleforge.console.datasource.generated";

    private static final String RULE_TYPE_DATA_SOURCE = "data_source";

    private final JavaSourceCompiler compiler;
    private final DataSourceRegistry registry;
    private final DraftMapper draftMapper;
    private final DataSourceGitWriter gitWriter;
    private final com.ruleforge.console.datasource.DataSourceController manifestController;
    private final ObjectMapper objectMapper;

    /**
     * Apply an approved data source draft. Compiles the source, writes the .class to git,
     * and registers the loaded bean into the in-process DataSourceRegistry.
     *
     * @return updated draft with appliedVersion / appliedAt set, or COMPILE_FAILED status
     *         if compilation failed.
     */
    public DraftEntity apply(String draftId) {
        DraftEntity draft = draftMapper.selectByDraftId(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("draft not found: " + draftId);
        }
        if (!RULE_TYPE_DATA_SOURCE.equals(draft.getRuleType())) {
            throw new IllegalArgumentException("not a data_source draft: " + draftId);
        }
        if (!DraftEntity.STATUS_APPROVED.equals(draft.getStatus())) {
            throw new IllegalStateException("draft must be APPROVED, current: " + draft.getStatus());
        }

        String javaCode = draft.getContent();
        String className = deriveClassName(draft.getDraftId());
        String fqcn = GENERATED_PACKAGE + "." + className;

        log.info("Compiling data source draft {} (fqcn={})", draftId, fqcn);
        JavaSourceCompiler.CompileResult result = compiler.compile(javaCode);
        if (!result.success) {
            log.warn("Compile failed for draft {}: {}", draftId, result.error);
            draft.setStatus("COMPILE_FAILED");
            draft.setReviewComment("compile error: " + truncate(result.error, 480));
            draft.setUpdatedAt(new Date());
            draftMapper.updateById(draft);
            return draft;
        }

        // 重命名/拷贝(LLM 写的 public class 名字可能跟我们 derive 的不一样,这里以 javac 找到的为准)
        String actualClassName = result.publicClassName;
        String actualFqcn = GENERATED_PACKAGE + "." + actualClassName;
        byte[] classBytes = result.classBytes;

        try {
            // 1. 写 git
            String gitPath = "data_sources/" + draft.getDraftId() + "/" + actualClassName + ".class";
            gitWriter.writeCompiledClass(draft.getProject(), gitPath, classBytes);

            // 2. 加载到 registry(从 git 拉回来,模拟 executor-app 行为 — 真实部署是从 git 拉字节)
            BaseApiDataSource ds = loadBean(actualFqcn, classBytes);
            registry.register(ds);

            // 3. 更新 manifest(executor-app 启动时拉)
            updateManifest(draft.getProject(), actualClassName, gitPath, draft.getDraftId());

            // 4. 标 applied
            draft.setStatus(DraftEntity.STATUS_APPROVED);  // 保持 APPROVED,避免跟现有状态机冲突
            draft.setAppliedVersion("v" + (System.currentTimeMillis() / 1000));
            draft.setAppliedAt(new Date());
            draft.setUpdatedAt(new Date());
            // 把 className 记到 sourceMeta 字段,给后续 executor 加载用
            draft.setSourceMeta(serializeSourceMeta(actualClassName, fqcn(actualClassName), gitPath));
            draftMapper.updateById(draft);
            return draft;
        } catch (Exception e) {
            log.error("Apply failed for draft {}: {}", draftId, e.getMessage(), e);
            draft.setStatus("APPLY_FAILED");
            draft.setReviewComment("apply error: " + truncate(e.getMessage(), 480));
            draft.setUpdatedAt(new Date());
            draftMapper.updateById(draft);
            return draft;
        }
    }

    /**
     * Class name from draftId: {@code ds_abc123def456} → {@code DsAbc123def456DataSource}
     * (preserves the hex tail for traceability).
     */
    static String deriveClassName(String draftId) {
        String tail = draftId.startsWith("ds_") ? draftId.substring(3) : draftId;
        // capitalize first char
        String cap = tail.isEmpty() ? "Anon" : Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
        return cap + "DataSource";
    }

    /** FQCN from class name (assumes generated package). */
    public static String fqcn(String className) {
        return GENERATED_PACKAGE + "." + className;
    }

    private BaseApiDataSource loadBean(String fqcn, byte[] classBytes) throws Exception {
        // 用 URLClassLoader 隔离加载 — defineClass 是 protected, 子类暴露
        URL[] urls = collectClasspathJars();
        DefiningClassLoader loader = new DefiningClassLoader(urls, this.getClass().getClassLoader());
        try {
            Class<?> clazz = loader.define(fqcn, classBytes);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof BaseApiDataSource)) {
                throw new IllegalStateException(fqcn + " does not extend BaseApiDataSource");
            }
            return (BaseApiDataSource) instance;
        } finally {
            loader.close();
        }
    }

    /** Exposes protected {@code defineClass} for runtime class definition. */
    static final class DefiningClassLoader extends URLClassLoader {
        DefiningClassLoader(URL[] urls, ClassLoader parent) { super(urls, parent); }
        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private URL[] collectClasspathJars() {
        ClassLoader cl = this.getClass().getClassLoader();
        if (cl instanceof URLClassLoader ucl) {
            return ucl.getURLs();
        }
        // Spring Boot fatjar: 从 java.class.path 拿
        String cp = System.getProperty("java.class.path");
        return java.util.Arrays.stream(cp.split(java.io.File.pathSeparator))
            .filter(s -> !s.isEmpty())
            .map(s -> Paths.get(s).toUri().toString())
            .map(s -> {
                try { return new URL(s); } catch (Exception e) { return null; }
            })
            .filter(java.util.Objects::nonNull)
            .toArray(URL[]::new);
    }

    private String serializeSourceMeta(String className, String fqcn, String gitPath) {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("className", className);
            m.put("fqcn", fqcn);
            m.put("gitPath", gitPath);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 更新 data_sources/manifest.json — executor-app 启动时拉这个文件知道要加载哪些类。
     * 用 draftId 作去重 key(同一 draft 重复 apply 不会重复注册)。
     */
    private void updateManifest(String project, String className, String gitPath, String draftId) {
        List<Map<String, Object>> entries = new java.util.ArrayList<>(manifestController.readManifestEntries(project));
        // 移除同 draftId 的旧条目(允许重新 apply 同一草稿,旧条目覆盖)
        entries.removeIf(e -> draftId.equals(String.valueOf(e.get("draftId"))));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("draftId", draftId);
        entry.put("className", className);
        entry.put("fqcn", fqcn(className));
        entry.put("gitPath", gitPath);
        entry.put("appliedAt", new Date().toInstant().toString());
        entries.add(entry);
        manifestController.writeManifest(project, entries);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
