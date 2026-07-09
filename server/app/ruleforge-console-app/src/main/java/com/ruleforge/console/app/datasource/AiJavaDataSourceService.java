package com.ruleforge.console.app.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruleforge.datasource.connector.AiJavaDataSourceConnector;
import com.ruleforge.datasource.jcompiler.IJavaDataSource;
import com.ruleforge.datasource.jcompiler.JavaSourceCompiler;
import com.ruleforge.datasource.entity.Datasource;
import com.ruleforge.datasource.service.IDatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

/**
 * V5.23 — Console-side service that compiles and stores an LLM-generated
 * Java {@link IJavaDataSource} into a {@code nd_datasource} row.
 *
 * <p>Flow:
 * <ol>
 *   <li>Validate source contains {@code implements IJavaDataSource}</li>
 *   <li>{@link JavaSourceCompiler#compile(String)} → bytes</li>
 *   <li>Update {@code nd_datasource.config_json} to include
 *       {@code className} + {@code classBytesBase64}</li>
 *   <li>Evict the runtime classloader cache (next fetch reloads)</li>
 * </ol>
 *
 * <p>No schema change required — bytes fit in existing {@code config_json TEXT}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiJavaDataSourceService {

    private final IDatasourceService datasourceService;
    private final AiJavaDataSourceConnector aiJavaConnector; // 注入 — 用来 evict cache
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Hard cap on raw source size (防御). */
    public static final int MAX_SOURCE_BYTES = 64 * 1024;

    @Transactional
    public ApplyResult apply(Long datasourceId, String javaSource) {
        if (datasourceId == null) {
            return ApplyResult.failure("datasourceId is required");
        }
        if (javaSource == null || javaSource.isBlank()) {
            return ApplyResult.failure("javaSource is required");
        }
        if (javaSource.getBytes().length > MAX_SOURCE_BYTES) {
            return ApplyResult.failure("javaSource exceeds " + MAX_SOURCE_BYTES + " bytes");
        }
        if (!javaSource.contains("implements IJavaDataSource")
            && !javaSource.contains("implements com.ruleforge.datasource.jcompiler.IJavaDataSource")) {
            return ApplyResult.failure(
                "Java source must implement com.ruleforge.datasource.jcompiler.IJavaDataSource");
        }

        Datasource ds = datasourceService.getDatasourceById(datasourceId);
        if (ds == null) {
            return ApplyResult.failure("datasource not found: id=" + datasourceId);
        }
        if (!AiJavaDataSourceConnector.CONNECTOR_TYPE.equalsIgnoreCase(ds.getType())) {
            return ApplyResult.failure(
                "datasource type must be " + AiJavaDataSourceConnector.CONNECTOR_TYPE
                + " (current: " + ds.getType() + ")");
        }

        JavaSourceCompiler.CompileResult cr = new JavaSourceCompiler().compile(javaSource);
        if (!cr.success) {
            return ApplyResult.failure("compile failed: " + cr.error);
        }

        // 构造新 config_json: { className, classBytesBase64 }
        String base64 = Base64.getEncoder().encodeToString(cr.classBytes);
        ObjectNode config = objectMapper.createObjectNode();
        config.put(AiJavaDataSourceConnector.CFG_CLASS_NAME, cr.fqcn);
        config.put(AiJavaDataSourceConnector.CFG_CLASS_BYTES, base64);

        ds.setConfigJson(config.toString());
        datasourceService.updateDatasource(ds);

        // 清掉 runtime classloader cache — 下次 fetch 重新加载新 .class
        aiJavaConnector.evict(datasourceId);

        log.info("AI_JAVA datasource {} applied: fqcn={} bytes={}",
            datasourceId, cr.fqcn, cr.classBytes.length);
        return ApplyResult.success(cr.fqcn, cr.classBytes.length);
    }

    public static final class ApplyResult {
        public final boolean success;
        public final String className;
        public final int classBytes;
        public final String message;

        private ApplyResult(boolean success, String className, int classBytes, String message) {
            this.success = success;
            this.className = className;
            this.classBytes = classBytes;
            this.message = message;
        }

        static ApplyResult success(String className, int classBytes) {
            return new ApplyResult(true, className, classBytes, "ok");
        }

        static ApplyResult failure(String message) {
            return new ApplyResult(false, null, 0, message);
        }
    }
}
