package com.ruleforge.parse;

import com.ruleforge.model.rule.ArithmeticType;
import com.ruleforge.model.rule.ComplexArithmetic;
import com.ruleforge.model.rule.SimpleArithmetic;
import com.ruleforge.model.rule.SimpleArithmeticValue;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.7 — {@link LeftParser} 行为契约 BDD。
 *
 * <p>锁 V6.9.7 收口 (2 处 Fernflower 反编译 if/else state machine) 的行为不变性:
 * <ul>
 *   <li>{@link LeftParser#parse} L36-40: {@code type} 属性为空时 → {@code LeftType.variable}</li>
 *   <li>{@code convertSimpleArithmetic} L87-100: {@code simpleArith == null} → 返 {@code null}</li>
 * </ul>
 *
 * <p><b>Why V6.9.7 选这条</b>: 跟 V6.2-V6.4-V6.9.2-V6.9.6 同档 Fernflower 反编译
 * state machine 收口。L36-40 → ternary (跟 V6.9.4 topJunctionOf 同模式);L87-100 → early return
 * (跟 V6.9.3 OrBuilder buildCriterion 同模式)。build-time per-DRL-parse 调用 JFR 0 sample,
 * pure code elegance。
 */
@DisplayName("V6.9.7 — LeftParser 行为契约")
class LeftParserTest {

    private LeftParser parser;

    @BeforeEach
    void setUp() {
        parser = new LeftParser();
    }

    @Nested
    @DisplayName("parse(Element) — type 属性处理")
    class ParseTypeHandling {

        @Test
        @DisplayName("type 属性为空 → 默 LeftType.variable (V6.9.7 ternary 收口)")
        void missingTypeAttributeDefaultsToVariable() {
            Document doc = DocumentHelper.createDocument();
            Element element = doc.addElement("left");
            // type attribute not set

            Left left = parser.parse(element);

            assertThat(left.getType()).isEqualTo(LeftType.variable);
        }

        @Test
        @DisplayName("type=variable 显式 → LeftType.variable + buildVariableLeftPart")
        void explicitTypeVariable() {
            Document doc = DocumentHelper.createDocument();
            Element element = doc.addElement("left");
            element.addAttribute("type", "variable");

            Left left = parser.parse(element);

            assertThat(left.getType()).isEqualTo(LeftType.variable);
            assertThat(left.getLeftPart()).isNotNull();
        }
    }

    @Nested
    @DisplayName("convertSimpleArithmetic — null 早返 (V6.9.7 early return 收口)")
    class ConvertSimpleArithmetic {

        // private method — reflection
        private Object invokeConvert(SimpleArithmetic simpleArith) throws Exception {
            Method m = LeftParser.class.getDeclaredMethod("convertSimpleArithmetic", SimpleArithmetic.class);
            m.setAccessible(true);
            return m.invoke(parser, simpleArith);
        }

        @Test
        @DisplayName("simpleArith == null → 返 null (V6.9.7 early return)")
        void nullReturnsNull() throws Exception {
            assertThat(invokeConvert(null)).isNull();
        }

        @Test
        @DisplayName("simpleArith 非 null → 返 ComplexArithmetic")
        void nonNullReturnsComplexArithmetic() throws Exception {
            SimpleArithmetic sa = new SimpleArithmetic();
            sa.setType(ArithmeticType.Add);
            SimpleArithmeticValue sv = new SimpleArithmeticValue();
            sv.setContent("42");
            sv.setArithmetic(null);
            sa.setValue(sv);

            Object result = invokeConvert(sa);

            assertThat(result).isInstanceOf(ComplexArithmetic.class);
            ComplexArithmetic complex = (ComplexArithmetic) result;
            assertThat(complex.getValue()).isInstanceOf(SimpleValue.class);
            assertThat(((SimpleValue) complex.getValue()).getContent()).isEqualTo("42");
        }
    }
}