package com.ruleforge.console.app.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.Datasource;
import com.ruleforge.console.app.entity.DatasourceEntityMapping;
import com.ruleforge.console.app.entity.DatasourceFieldMapping;
import com.ruleforge.console.app.service.IDatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据源管理 REST API
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforgeV2.root.path}/datasource")
@RequiredArgsConstructor
public class DatasourceController {

    private final IDatasourceService datasourceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===== 数据源 CRUD =====

    @GetMapping
    public ResponseEntity<?> listDatasources() {
        return ResponseEntity.ok(datasourceService.listDatasources());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDatasource(@PathVariable Long id) {
        Datasource ds = datasourceService.getDatasourceById(id);
        if (ds == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ds);
    }

    @PostMapping
    public ResponseEntity<?> createDatasource(@RequestBody Datasource datasource) {
        return ResponseEntity.ok(datasourceService.createDatasource(datasource));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDatasource(@PathVariable Long id, @RequestBody Datasource datasource) {
        datasource.setId(id);
        return ResponseEntity.ok(datasourceService.updateDatasource(datasource));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDatasource(@PathVariable Long id) {
        datasourceService.deleteDatasource(id);
        return ResponseEntity.ok().build();
    }

    // ===== 连接测试 =====

    @PostMapping("/{id}/test")
    public ResponseEntity<?> testConnection(@PathVariable Long id) {
        boolean success = datasourceService.testConnection(id);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "连接成功" : "连接失败"
        ));
    }

    // ===== 实体类映射 =====

    @GetMapping("/entity-mapping")
    public ResponseEntity<?> listEntityMappings() {
        return ResponseEntity.ok(datasourceService.listEntityMappings());
    }

    @PutMapping("/entity-mapping")
    public ResponseEntity<?> saveEntityMapping(@RequestBody Map<String, Object> body) {
        String clazz = (String) body.get("clazz");
        Number datasourceId = (Number) body.get("datasourceId");
        if (clazz == null || datasourceId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "clazz 和 datasourceId 不能为空"));
        }
        datasourceService.saveEntityMapping(clazz, datasourceId.longValue());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/entity-mapping/{clazz}")
    public ResponseEntity<?> deleteEntityMapping(@PathVariable String clazz) {
        datasourceService.deleteEntityMapping(clazz);
        return ResponseEntity.ok().build();
    }

    // ===== 字段映射 =====

    @GetMapping("/{id}/field-mappings")
    public ResponseEntity<?> getFieldMappings(@PathVariable Long id,
                                              @RequestParam(required = false) String clazz) {
        if (clazz == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "clazz 参数必填"));
        }
        return ResponseEntity.ok(datasourceService.getFieldMappings(id, clazz));
    }

    @PutMapping("/{id}/field-mappings")
    public ResponseEntity<?> saveFieldMappings(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        String clazz = (String) body.get("clazz");
        if (clazz == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "clazz 参数必填"));
        }
        List<DatasourceFieldMapping> mappings = objectMapper.convertValue(
                body.get("mappings"), new TypeReference<List<DatasourceFieldMapping>>() {});
        datasourceService.saveFieldMappings(id, clazz, mappings != null ? mappings : List.of());
        return ResponseEntity.ok().build();
    }
}
