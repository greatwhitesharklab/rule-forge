package com.ruleforge.model.rete;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.12 — {@link JsonUtils#parseParameters} 行为契约 BDD。
 *
 * <p>锁 V6.9.12 收口 (L77-93 Iterator + while → enhanced for + 删 L88-89 死代码:
 * `if (valueTypeText != null) { param.setValue(parseValue(...)); }` 立即被 L91 无条件
 * `param.setValue(parseValue(...))` 覆盖) 的行为不变性:
 * <ul>
 *   <li><b>parametersNode == null</b>: 返 {@code null}</li>
 *   <li><b>parametersNode 是空数组</b>: 返空 list</li>
 *   <li><b>parametersNode 有 N 个元素</b>: 返 N 个 Parameter, name/type/value 按 JSON 设</li>
 *   <li><b>type 缺失</b>: param.type 保持 null (没 set)</li>
 *   <li><b>value 缺失</b>: param.value 仍被 parseValue 调用 (返 null) — 跟 L91 行为一致</li>
 * </ul>
 *
 * <p><b>Why V6.9.12 选这条</b>: 跟 V5.96 (Iterator var123 → enhanced for) 同档;
 * L88-89 是经典 "dead-then-overwrite" 反编译 artifact, 既然 L91 无条件覆盖, L88-89 完全 dead,
 * 删掉是 pure code elegance + minor dead-code removal。 Build-time per-JSON-parse JFR 0 sample 预期。
 */
@DisplayName("V6.9.12 — JsonUtils.parseParameters 行为契约")
class JsonUtilsParseParametersTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    private JsonNode node(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Nested
    @DisplayName("null / empty / single")
    class Basic {

        @Test
        @DisplayName("parameters 缺失 → 返 null")
        void missingParametersReturnsNull() throws Exception {
            JsonNode root = node("{}");

            List<Parameter> result = JsonUtils.parseParameters(root);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("parameters 空数组 → 返空 list")
        void emptyParametersReturnsEmptyList() throws Exception {
            JsonNode root = node("{\"parameters\": []}");

            List<Parameter> result = JsonUtils.parseParameters(root);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("parameters 1 个元素 → 返 1 个 Parameter")
        void singleParameterReturnsOne() throws Exception {
            JsonNode root = node("{\"parameters\": ["
                + "{\"name\": \"p1\", \"type\": \"String\", \"value\": {\"valueType\": \"Constant\", \"content\": \"hello\"}}"
                + "]}");

            List<Parameter> result = JsonUtils.parseParameters(root);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("p1");
            assertThat(result.get(0).getType()).isEqualTo(Datatype.String);
            assertThat(result.get(0).getValue()).isNotNull();
        }
    }

    @Nested
    @DisplayName("多 parameter + 字段缺失")
    class MultiAndMissing {

        @Test
        @DisplayName("parameters 2 个元素 → 返 2 个 Parameter, 顺序保留")
        void multipleParametersPreserveOrder() throws Exception {
            JsonNode root = node("{\"parameters\": ["
                + "{\"name\": \"p1\", \"type\": \"Integer\", \"value\": {\"valueType\": \"Constant\", \"content\": \"1\"}},"
                + "{\"name\": \"p2\", \"type\": \"String\", \"value\": {\"valueType\": \"Constant\", \"content\": \"x\"}}"
                + "]}");

            List<Parameter> result = JsonUtils.parseParameters(root);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("p1");
            assertThat(result.get(1).getName()).isEqualTo("p2");
            assertThat(result.get(0).getType()).isEqualTo(Datatype.Integer);
            assertThat(result.get(1).getType()).isEqualTo(Datatype.String);
        }

        @Test
        @DisplayName("type 缺失 → param.type 保持 null, 不抛")
        void typeMissingDoesNotThrow() throws Exception {
            JsonNode root = node("{\"parameters\": ["
                + "{\"name\": \"p1\", \"value\": {\"valueType\": \"Constant\", \"content\": \"x\"}}"
                + "]}");

            List<Parameter> result = JsonUtils.parseParameters(root);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isNull();
            assertThat(result.get(0).getName()).isEqualTo("p1");
        }

        @Test
        @DisplayName("value 缺失 → parseValue 返 null, param.value 保持 null")
        void valueMissingReturnsNullValue() throws Exception {
            JsonNode root = node("{\"parameters\": ["
                + "{\"name\": \"p1\", \"type\": \"String\"}"
                + "]}");

            List<Parameter> result = JsonUtils.parseParameters(root);

            assertThat(result).hasSize(1);
            // V6.9.12 — parseValue(node) 内 L135-140: node.get("value") == null → 返 null,
            // 所以 param.setValue(null) 被调, param.value 永为 null。 无 NPE 路径。
            assertThat(result.get(0).getName()).isEqualTo("p1");
            assertThat(result.get(0).getValue()).isNull();
        }
    }
}