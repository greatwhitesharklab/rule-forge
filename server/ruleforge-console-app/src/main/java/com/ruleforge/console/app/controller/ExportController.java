package com.ruleforge.console.app.controller;

import com.ruleforge.console.repository.model.ResourceItem;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.service.impl.RuleForgeRepositoryServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则内容导出 REST API — 为外部 Agent 提供结构化规则数据
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/export")
@RequiredArgsConstructor
public class ExportController {

    private final RuleForgeRepositoryServiceImpl repoService;

    /**
     * 列出项目下所有规则包及其资源文件
     * GET /export/project/{project}/packages
     */
    @GetMapping("/project/{project}/packages")
    public ResponseEntity<?> listPackages(@PathVariable String project) {
        try {
            List<ResourcePackage> packages = repoService.loadProjectResourcePackages(project);
            List<Map<String, Object>> result = new ArrayList<>();
            for (ResourcePackage pkg : packages) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", pkg.getId());
                entry.put("name", pkg.getName());
                entry.put("version", pkg.getVersion());

                List<Map<String, Object>> items = new ArrayList<>();
                if (pkg.getResourceItems() != null) {
                    for (ResourceItem item : pkg.getResourceItems()) {
                        Map<String, Object> itemMap = new LinkedHashMap<>();
                        itemMap.put("name", item.getName());
                        itemMap.put("path", item.getPath());
                        itemMap.put("version", item.getVersion());
                        items.add(itemMap);
                    }
                }
                entry.put("resourceItems", items);
                result.add(entry);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("加载规则包列表失败", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /**
     * 导出规则包完整内容 — 所有文件的原始内容
     * GET /export/project/{project}/package/{packageId}
     */
    @GetMapping("/project/{project}/package/{packageId}")
    public ResponseEntity<?> exportPackage(@PathVariable String project,
                                           @PathVariable String packageId) {
        try {
            List<ResourcePackage> packages = repoService.loadProjectResourcePackages(project);
            ResourcePackage targetPkg = packages.stream()
                    .filter(p -> packageId.equals(p.getId()) || packageId.equals(p.getName()))
                    .findFirst().orElse(null);

            if (targetPkg == null) {
                return ResponseEntity.ok(Collections.singletonMap("error",
                        "Package not found: " + packageId));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("packageName", targetPkg.getName());
            result.put("packageId", targetPkg.getId());
            result.put("version", targetPkg.getVersion());

            List<Map<String, Object>> files = new ArrayList<>();
            if (targetPkg.getResourceItems() != null) {
                for (ResourceItem item : targetPkg.getResourceItems()) {
                    Map<String, Object> fileEntry = new LinkedHashMap<>();
                    fileEntry.put("name", item.getName());
                    fileEntry.put("path", item.getPath());

                    try {
                        String content = readFileContent(item.getPath(), item.getVersion());
                        fileEntry.put("content", content);
                        fileEntry.put("type", detectFileType(item.getName()));
                    } catch (Exception e) {
                        fileEntry.put("content", null);
                        fileEntry.put("error", "Failed to read: " + e.getMessage());
                    }
                    files.add(fileEntry);
                }
            }
            result.put("files", files);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("导出规则包失败: {}", packageId, e);
            return ResponseEntity.internalServerError().body(
                    Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 导出单个文件内容
     * GET /export/file?path={path}&version={version}
     */
    @GetMapping("/file")
    public ResponseEntity<?> exportFile(@RequestParam String path,
                                        @RequestParam(required = false) String version) {
        try {
            String content = readFileContent(path, version);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("version", version);
            result.put("content", content);
            result.put("type", detectFileType(path));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("导出文件失败: {}", path, e);
            return ResponseEntity.ok(Collections.singletonMap("error",
                    "Failed to read file: " + e.getMessage()));
        }
    }

    /**
     * 列出所有项目名
     * GET /export/projects
     */
    @GetMapping("/projects")
    public ResponseEntity<?> listProjects() {
        try {
            List<String> projects = repoService.loadProjectNames();
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("加载项目列表失败", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    // === 工具方法 ===

    private String readFileContent(String path, String version) throws Exception {
        try (InputStream is = repoService.readFile(path, version)) {
            if (is == null) return null;
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        }
    }

    private String detectFileType(String name) {
        if (name == null) return "unknown";
        String lower = name.toLowerCase();
        if (lower.endsWith(".flow.xml")) return "flow";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".drl")) return "drl";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".ul")) return "ul";
        return "unknown";
    }

}
