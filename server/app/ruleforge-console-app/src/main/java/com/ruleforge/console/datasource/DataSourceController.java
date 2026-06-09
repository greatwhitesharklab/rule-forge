package com.ruleforge.console.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.storage.GitStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V5.23 — Console-side endpoints for executor-app to load compiled data source .class bytes.
 *
 * <p>Three endpoints under {@code /ruleforge/datasource/}:
 * <ul>
 *   <li>{@code GET /ruleforge/datasource/manifest?project=X} — returns JSON manifest of
 *       all data sources for the project (one entry per compiled .class). Executor-app
 *       calls this on startup; manifest is updated by {@link DataSourceApplyService}
 *       when a new data source is applied.</li>
 *   <li>{@code GET /ruleforge/datasource/load?project=X&gitPath=...} — returns raw
 *       .class bytes ({@code application/octet-stream}) for executor-app to defineClass.</li>
 *   <li>{@code GET /ruleforge/datasource/ping?project=X} — health check; returns
 *       200 if project repo exists, 404 otherwise.</li>
 * </ul>
 *
 * <p>Why a manifest file rather than git tree-walk: GitStorageService doesn't expose
 * tree-listing, and a small JSON manifest is much cheaper to maintain + read on every
 * executor startup. Manifest is written by apply (1 git commit per apply) so it's
 * always consistent with the latest compiled classes.
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/datasource")
@RequiredArgsConstructor
public class DataSourceController {

    static final String BRANCH = "main";
    static final String MANIFEST_PATH = "data_sources/manifest.json";
    static final String DATA_SOURCE_PREFIX = "data_sources/";

    private final GitStorageService gitStorage;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Read the project's data source manifest. Empty list if no data sources yet
     * (no manifest file or no project repo).
     */
    @GetMapping(value = "/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getManifest(@RequestParam String project) {
        if (project == null || project.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!gitStorage.repoExists(project)) {
            return ResponseEntity.ok(Map.of("project", project, "dataSources", List.of()));
        }
        String json = gitStorage.readFile(project, BRANCH, MANIFEST_PATH);
        if (json == null || json.isBlank()) {
            return ResponseEntity.ok(Map.of("project", project, "dataSources", List.of()));
        }
        try {
            Map<String, Object> manifest = objectMapper.readValue(json, Map.class);
            return ResponseEntity.ok(Map.of(
                "project", project,
                "dataSources", manifest.getOrDefault("dataSources", List.of())
            ));
        } catch (Exception e) {
            log.warn("[DataSource] manifest parse failed for project {}: {}", project, e.getMessage());
            return ResponseEntity.ok(Map.of("project", project, "dataSources", List.of()));
        }
    }

    /**
     * Read compiled .class bytes for a given git path.
     */
    @GetMapping(value = "/load", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> loadDataSource(@RequestParam String project,
                                                 @RequestParam String gitPath) {
        if (project == null || project.isEmpty() || gitPath == null || gitPath.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!gitPath.startsWith(DATA_SOURCE_PREFIX) || !gitPath.endsWith(".class")) {
            log.warn("[DataSource] refusing to load non-data_source path: {}", gitPath);
            return ResponseEntity.badRequest().build();
        }
        try {
            InputStream is = gitStorage.readFileStream(project, BRANCH, gitPath);
            if (is == null) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = is.readAllBytes();
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
        } catch (Exception e) {
            log.error("[DataSource] load failed: project={}, gitPath={}: {}",
                project, gitPath, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check — does the project repo exist?
     */
    @GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ping(@RequestParam String project) {
        boolean exists = project != null && !project.isEmpty() && gitStorage.repoExists(project);
        return ResponseEntity.ok(Map.of(
            "project", project == null ? "" : project,
            "exists", exists
        ));
    }

    /**
     * Read the current manifest entries as a Java list (used by
     * {@link DataSourceApplyService} to add a new entry and write back).
     */
    public List<Map<String, Object>> readManifestEntries(String project) {
        if (!gitStorage.repoExists(project)) {
            return new ArrayList<>();
        }
        String json = gitStorage.readFile(project, BRANCH, MANIFEST_PATH);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            Map<String, Object> manifest = objectMapper.readValue(json, Map.class);
            Object arr = manifest.get("dataSources");
            if (arr instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object e : list) {
                    if (e instanceof Map<?, ?> m) {
                        Map<String, Object> casted = new LinkedHashMap<>();
                        m.forEach((k, v) -> casted.put(String.valueOf(k), v));
                        out.add(casted);
                    }
                }
                return out;
            }
        } catch (Exception e) {
            log.warn("[DataSource] readManifestEntries parse failed: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Write the manifest back to git with the given entries (used by
     * {@link DataSourceApplyService} after a successful apply).
     */
    public void writeManifest(String project, List<Map<String, Object>> entries) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", 1);
        doc.put("project", project);
        doc.put("updatedAt", java.time.Instant.now().toString());
        doc.put("dataSources", entries);
        try {
            String json = objectMapper.writeValueAsString(doc);
            gitStorage.writeFile(project, BRANCH, MANIFEST_PATH, json);
        } catch (Exception e) {
            log.error("[DataSource] writeManifest failed for project {}: {}", project, e.getMessage());
        }
    }
}
