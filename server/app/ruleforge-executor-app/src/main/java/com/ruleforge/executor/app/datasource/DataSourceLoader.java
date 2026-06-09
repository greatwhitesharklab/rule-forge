package com.ruleforge.executor.app.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.datasource.BaseApiDataSource;
import com.ruleforge.datasource.DataSourceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * V5.23 — Loads compiled data source .class bytes from console git on startup.
 *
 * <p>Flow:
 * <ol>
 *   <li>On {@link ApplicationReadyEvent} (Spring context fully up), iterate
 *       {@code ruleforge.datasource.projects} (comma-separated in application.yml).</li>
 *   <li>For each project, hit {@code GET <console>/ruleforge/datasource/manifest?project=X}
 *       — returns the list of compiled .class entries.</li>
 *   <li>For each entry, hit {@code GET <console>/ruleforge/datasource/load?project=X&gitPath=...}
 *       to fetch the .class bytes.</li>
 *   <li>Define the class via an isolated {@link URLClassLoader} (defineClass is
 *       protected, so we use the same {@code DefiningClassLoader} trick as the
 *       console-side apply service).</li>
 *   <li>Instantiate, verify it's a {@link BaseApiDataSource}, register in
 *       {@link DataSourceRegistry}.</li>
 * </ol>
 *
 * <p>Failures are logged and skipped — a single bad class doesn't block the rest.
 * This is best-effort startup; the {@code DataSourceRegistry} can be re-populated
 * by restarting the executor or via a future admin endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceLoader {

    private final DataSourceRegistry registry;
    private final RestTemplate consoleRestTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ruleforge.console.url:}")
    private String consoleUrl;

    /** Comma-separated project names to load. Empty = skip. */
    @Value("${ruleforge.datasource.projects:}")
    private String projectsCsv;

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (projectsCsv == null || projectsCsv.isBlank()) {
            log.info("[DataSource] no projects configured (ruleforge.datasource.projects empty); skipping");
            return;
        }
        if (consoleUrl == null || consoleUrl.isBlank()) {
            log.warn("[DataSource] console.url not set; cannot load data sources");
            return;
        }
        String base = consoleUrl.endsWith("/") ? consoleUrl.substring(0, consoleUrl.length() - 1) : consoleUrl;
        String[] projects = projectsCsv.split(",");
        int totalLoaded = 0;
        for (String p : projects) {
            String project = p.trim();
            if (project.isEmpty()) continue;
            try {
                totalLoaded += loadProject(base, project);
            } catch (Exception e) {
                log.error("[DataSource] failed to load project {}: {}", project, e.getMessage(), e);
            }
        }
        log.info("[DataSource] startup load complete: {} data sources registered from {} projects",
            totalLoaded, projects.length);
    }

    private int loadProject(String consoleBase, String project) {
        log.info("[DataSource] loading project {} from console {}", project, consoleBase);
        String manifestUrl = consoleBase + "/ruleforge/datasource/manifest?project="
            + java.net.URLEncoder.encode(project, StandardCharsets.UTF_8);
        ResponseEntity<String> resp;
        try {
            resp = consoleRestTemplate.exchange(manifestUrl, HttpMethod.GET, null, String.class);
        } catch (RestClientException e) {
            log.warn("[DataSource] manifest fetch failed for project {}: {}", project, e.getMessage());
            return 0;
        }
        if (resp.getBody() == null || resp.getBody().isBlank()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode arr = root.path("dataSources");
            if (!arr.isArray() || arr.isEmpty()) {
                log.info("[DataSource] no data sources in manifest for project {}", project);
                return 0;
            }
            int loaded = 0;
            for (JsonNode entry : arr) {
                String className = entry.path("className").asText("");
                String fqcn = entry.path("fqcn").asText("");
                String gitPath = entry.path("gitPath").asText("");
                if (className.isEmpty() || gitPath.isEmpty()) continue;
                try {
                    if (loadOne(consoleBase, project, fqcn, gitPath)) loaded++;
                } catch (Exception e) {
                    log.warn("[DataSource] failed to load class={} from {}: {}",
                        className, gitPath, e.getMessage());
                }
            }
            return loaded;
        } catch (Exception e) {
            log.error("[DataSource] manifest parse failed for project {}: {}", project, e.getMessage());
            return 0;
        }
    }

    private boolean loadOne(String consoleBase, String project, String fqcn, String gitPath) throws Exception {
        String url = consoleBase + "/ruleforge/datasource/load?project="
            + java.net.URLEncoder.encode(project, StandardCharsets.UTF_8)
            + "&gitPath=" + java.net.URLEncoder.encode(gitPath, StandardCharsets.UTF_8);
        ResponseEntity<byte[]> resp = consoleRestTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
        byte[] bytes = resp.getBody();
        if (bytes == null || bytes.length == 0) {
            log.warn("[DataSource] empty bytes for fqcn={} gitPath={}", fqcn, gitPath);
            return false;
        }
        // 字节首部不是 .class magic (CA FE BA BE) — 防御性检查
        if (bytes.length < 4
            || (bytes[0] & 0xFF) != 0xCA
            || (bytes[1] & 0xFF) != 0xFE
            || (bytes[2] & 0xFF) != 0xBA
            || (bytes[3] & 0xFF) != 0xBE) {
            log.warn("[DataSource] bad magic bytes for fqcn={} gitPath={}", fqcn, gitPath);
            return false;
        }
        URL[] urls = collectClasspathJars();
        DefiningClassLoader loader = new DefiningClassLoader(urls, this.getClass().getClassLoader());
        try {
            Class<?> clazz = loader.define(fqcn, bytes);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof BaseApiDataSource)) {
                log.warn("[DataSource] fqcn={} is not a BaseApiDataSource subclass", fqcn);
                return false;
            }
            BaseApiDataSource ds = (BaseApiDataSource) instance;
            registry.register(ds);
            log.info("[DataSource] registered: fqcn={} name={}", fqcn, ds.getName());
            return true;
        } finally {
            try { loader.close(); } catch (Exception ignored) {}
        }
    }

    private URL[] collectClasspathJars() {
        ClassLoader cl = this.getClass().getClassLoader();
        if (cl instanceof URLClassLoader ucl) {
            return ucl.getURLs();
        }
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

    /** Exposes protected {@code defineClass} for runtime class definition. */
    static final class DefiningClassLoader extends URLClassLoader {
        DefiningClassLoader(URL[] urls, ClassLoader parent) { super(urls, parent); }
        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
