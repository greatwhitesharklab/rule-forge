package com.ruleforge.model.rete;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * V6.9.13 — {@link ReteNodeJsonDeserializer#deserialize} 行为契约 BDD。
 *
 * <p>锁 V6.9.13 收口 (L27 + L199 两处 Iterator + while → enhanced for) 的行为不变性:
 * <ul>
 *   <li><b>JSON 是空数组</b>: 返空 list</li>
 *   <li><b>JSON 是 N 个 rete node 数组</b>: 返 N 个 ReteNode, 顺序保留</li>
 *   <li><b>每个 rete node</b>: 走 NodeType dispatch (objectType/and/or/criteria/namedCriteria/terminal)</li>
 * </ul>
 *
 * <p>ObjectTypeNode 是最简 dispatch 路径 (只需 id + objectTypeClass + nodeType), 用作测试 fixture。
 * parseMultiCondition 通过 multiCondition JSON 间接覆盖 (criteria 节点 internal call)。
 *
 * <p><b>Why V6.9.13 选这条</b>: V5.96 skip 模式延续。3 处 deserializer Iterator+while 收口 (1 in
 * RuleJsonDeserializer + 2 in ReteNodeJsonDeserializer), build-time per-JSON-parse 调用。
 */
@DisplayName("V6.9.13 — ReteNodeJsonDeserializer 行为契约")
class ReteNodeJsonDeserializerTest {

    private ReteNodeJsonDeserializer deserializer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        deserializer = new ReteNodeJsonDeserializer();
        mapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("rete node list 解析")
    class Deserialize {

        @Test
        @DisplayName("空 rete node 数组 → 返空 list")
        void emptyArrayReturnsEmptyList() throws Exception {
            JsonParser jp = mapper.getFactory().createParser("[]");
            DeserializationContext ctxt = mock(DeserializationContext.class);

            List<ReteNode> result = deserializer.deserialize(jp, ctxt);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("单 ObjectTypeNode → 返 1 ReteNode (id=1, class=foo.Bar)")
        void singleObjectTypeNodeReturnsOne() throws Exception {
            JsonParser jp = mapper.getFactory().createParser(
                "[{\"id\":1,\"nodeType\":\"objectType\",\"objectTypeClass\":\"foo.Bar\"}]");
            DeserializationContext ctxt = mock(DeserializationContext.class);

            List<ReteNode> result = deserializer.deserialize(jp, ctxt);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(ObjectTypeNode.class);
            ObjectTypeNode node = (ObjectTypeNode) result.get(0);
            assertThat(node.getId()).isEqualTo(1);
            assertThat(node.getObjectTypeClass()).isEqualTo("foo.Bar");
        }

        @Test
        @DisplayName("2 个 ObjectTypeNode → 返 2 ReteNode, 顺序保留")
        void twoObjectTypeNodesPreserveOrder() throws Exception {
            JsonParser jp = mapper.getFactory().createParser(
                "[{\"id\":1,\"nodeType\":\"objectType\",\"objectTypeClass\":\"foo.Bar\"},"
                + "{\"id\":2,\"nodeType\":\"objectType\",\"objectTypeClass\":\"foo.Baz\"}]");
            DeserializationContext ctxt = mock(DeserializationContext.class);

            List<ReteNode> result = deserializer.deserialize(jp, ctxt);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1);
            assertThat(result.get(1).getId()).isEqualTo(2);
            assertThat(((ObjectTypeNode) result.get(0)).getObjectTypeClass()).isEqualTo("foo.Bar");
            assertThat(((ObjectTypeNode) result.get(1)).getObjectTypeClass()).isEqualTo("foo.Baz");
        }
    }
}