package com.ruleforge.console.app.agent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rule schema 加载 + 缓存服务
 *
 * V5.22 — AI Rule Authoring 用的规则元数据。LLM 在生成规则前先查 schema,知道结构 / 字段 / 例子。
 *
 * 数据源:classpath:rule-schema/ 下的 JSON 文件
 * - _index.json:列表(轻量,启动时一次加载)
 * - {type}.json:每个类型一份(按需懒加载,缓存)
 *
 * 为什么不放 DB:这是产品定义而非用户数据,改了要重发版,放 classpath 更直观。
 */
@Slf4j
@Service
public class RuleSchemaService {

    private static final String SCHEMA_BASE = "rule-schema/";
    private static final String INDEX_FILE = "_index.json";

    private final ObjectMapper objectMapper;

    /** 启动时一次加载的 type index(typeId → 元信息) */
    private final ConcurrentMap<String, TypeInfo> typeIndex = new ConcurrentHashMap<>();

    /** 按需懒加载的 schema(完整 JSON) */
    private final ConcurrentMap<String, JsonNode> schemaCache = new ConcurrentHashMap<>();

    public RuleSchemaService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadIndex() {
        try (InputStream in = new ClassPathResource(SCHEMA_BASE + INDEX_FILE).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode types = root.get("types");
            if (types == null || !types.isArray()) {
                log.warn("[RuleSchema] {} 缺少 types 数组,索引为空", INDEX_FILE);
                return;
            }
            for (JsonNode entry : types) {
                String typeId = textOrNull(entry, "type");
                if (typeId == null) continue;
                // v522Supported 可能是 boolean (true/false) 或 string ("experimental")
                JsonNode supportedNode = entry.get("v522Supported");
                Object supported = null;
                if (supportedNode != null && !supportedNode.isNull()) {
                    if (supportedNode.isBoolean()) supported = supportedNode.asBoolean();
                    else if (supportedNode.isTextual()) supported = supportedNode.asText();
                }
                typeIndex.put(typeId, new TypeInfo(
                        typeId,
                        textOrNull(entry, "name"),
                        textOrNull(entry, "description"),
                        supported,
                        entry.get("priority") != null ? entry.get("priority").asInt(99) : 99
                ));
            }
            log.info("[RuleSchema] 加载 {} 个 rule type 索引: {}", typeIndex.size(),
                    new ArrayList<>(typeIndex.keySet()));
        } catch (IOException e) {
            log.error("[RuleSchema] 加载 {} 失败", INDEX_FILE, e);
        }
    }

    /**
     * 列出所有支持的 rule type(LLM 看到的第一份 schema)
     *
     * 返回结构:
     * {
     *   "types": [{ "type": "decision_table", "name": "决策表", "v522Supported": true, "priority": 1, "description": "..." }, ...]
     * }
     */
    public ObjectNode listTypes() {
        ObjectNode out = objectMapper.createObjectNode();
        ArrayNode arr = out.putArray("types");
        typeIndex.values().stream()
                .sorted((a, b) -> Integer.compare(a.priority, b.priority))
                .forEach(info -> {
                    ObjectNode node = arr.addObject();
                    node.put("type", info.typeId);
                    node.put("name", info.name);
                    node.set("v522Supported", objectMapper.valueToTree(info.v522Supported));
                    node.put("priority", info.priority);
                    node.put("description", info.description);
                });
        return out;
    }

    /**
     * 取某个 rule type 的完整 schema
     *
     * @param type rule type,比如 "decision_table"
     * @return Optional.empty() 表示 type 不存在
     */
    public Optional<JsonNode> getSchema(String type) {
        if (type == null || type.isEmpty()) return Optional.empty();
        if (!typeIndex.containsKey(type)) return Optional.empty();

        return Optional.ofNullable(schemaCache.computeIfAbsent(type, this::loadSchemaFile));
    }

    /**
     * 列出所有支持的 v522 规则 type 名字(LLM tool 用)
     */
    public List<String> supportedV522Types() {
        List<String> out = new ArrayList<>();
        typeIndex.forEach((k, v) -> {
            if (v.v522Supported != null && (Boolean.TRUE.equals(v.v522Supported) || "experimental".equals(v.v522Supported))) {
                out.add(k);
            }
        });
        Collections.sort(out);
        return out;
    }

    // ========== 内部 ==========

    private JsonNode loadSchemaFile(String type) {
        String path = SCHEMA_BASE + type + ".json";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            log.error("[RuleSchema] 加载 {} 失败", path, e);
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private record TypeInfo(
            String typeId,
            String name,
            String description,
            Object v522Supported,
            int priority
    ) {}
}
