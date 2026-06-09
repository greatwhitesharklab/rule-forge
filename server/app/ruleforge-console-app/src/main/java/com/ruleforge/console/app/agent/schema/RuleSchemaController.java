package com.ruleforge.console.app.agent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Rule schema REST API
 *
 * 给 LLM / 业务系统 / CLI 取规则元数据用。
 *
 * V5.22 — Phase 0
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/rule-schema")
@RequiredArgsConstructor
public class RuleSchemaController {

    private final RuleSchemaService schemaService;

    /**
     * 列所有 rule type。LLM 在写规则前先调这个看支持哪些类型。
     *
     * GET /ruleforge/rule-schema/types
     */
    @GetMapping("/types")
    public ResponseEntity<?> listTypes() {
        return ResponseEntity.ok(schemaService.listTypes());
    }

    /**
     * 取某类型的完整 schema(JSON 结构 + operators + example + tips)。
     *
     * GET /ruleforge/rule-schema/{type}
     *
     * @return 200 + schema; 404 表示 type 不存在
     */
    @GetMapping("/{type}")
    public ResponseEntity<?> getSchema(@PathVariable String type) {
        Optional<JsonNode> schema = schemaService.getSchema(type);
        return schema.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "error", "type_not_found",
                        "type", type,
                        "supportedTypes", schemaService.supportedV522Types()
                )));
    }
}
