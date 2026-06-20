package com.ruleforge.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.model.rule.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * V6.9.13 — {@link RuleJsonDeserializer#deserialize} 行为契约 BDD。
 *
 * <p>锁 V6.9.13 收口 (L24-29 Iterator + while → enhanced for) 的行为不变性:
 * <ul>
 *   <li><b>JSON 是空数组</b>: 返空 list</li>
 *   <li><b>JSON 是 N 个 rule object 数组</b>: 返 N 个 Rule, 顺序保留</li>
 *   <li><b>每个 rule</b>: 走 parseRule(jp, childNode) → AbstractJsonDeserializer.parseRule</li>
 * </ul>
 *
 * <p><b>Why V6.9.13 选这条</b>: V5.96 skip 模式延续 (跟 V6.9.12 JsonUtils.parseParameters 同模式)。
 * Build-time per-JSON-parse 调用, JFR 0 sample 预期。
 */
@DisplayName("V6.9.13 — RuleJsonDeserializer 行为契约")
class RuleJsonDeserializerTest {

    private RuleJsonDeserializer deserializer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        deserializer = new RuleJsonDeserializer();
        mapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("rule list 解析")
    class Deserialize {

        @Test
        @DisplayName("空 rule 数组 → 返空 list")
        void emptyArrayReturnsEmptyList() throws Exception {
            JsonParser jp = mapper.getFactory().createParser("[]");
            DeserializationContext ctxt = mock(DeserializationContext.class);

            List<Rule> result = deserializer.deserialize(jp, ctxt);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("单个 rule → 返 1 Rule (name='r1')")
        void singleRuleReturnsOne() throws Exception {
            JsonParser jp = mapper.getFactory().createParser("[{\"rule\":{\"name\":\"r1\"}}]");
            DeserializationContext ctxt = mock(DeserializationContext.class);

            List<Rule> result = deserializer.deserialize(jp, ctxt);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("r1");
        }

        @Test
        @DisplayName("2 个 rule → 返 2 Rule, 顺序保留")
        void twoRulesPreserveOrder() throws Exception {
            JsonParser jp = mapper.getFactory().createParser(
                "[{\"rule\":{\"name\":\"r1\"}},{\"rule\":{\"name\":\"r2\"}}]");
            DeserializationContext ctxt = mock(DeserializationContext.class);

            List<Rule> result = deserializer.deserialize(jp, ctxt);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("r1");
            assertThat(result.get(1).getName()).isEqualTo("r2");
        }
    }
}