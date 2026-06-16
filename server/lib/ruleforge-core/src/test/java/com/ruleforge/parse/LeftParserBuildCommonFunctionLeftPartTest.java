package com.ruleforge.parse;

import com.ruleforge.model.rule.lhs.CommonFunctionLeftPart;
import com.ruleforge.model.rule.lhs.CommonFunctionParameter;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.rete.test.EngineContextWirer;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.4 — {@link LeftParser#parse(Element)} route to {@code buildCommonFunctionLeftPart} 契约 BDD。
 *
 * <p>锁 V6.4 修法(2-level nested do-while find-first → enhanced for + 2 个 continue)
 * 的行为不变性:
 * <ul>
 *   <li>{@code type="commonfunction"} 时路由到 buildCommonFunctionLeftPart</li>
 *   <li>part name = {@code function-name} attribute</li>
 *   <li>part label = {@code function-label} attribute</li>
 *   <li>对 element.elements() 内的每个 item: skip non-Element, skip non-`function-parameter` Element</li>
 *   <li>对 `function-parameter` Element: 创建 CommonFunctionParameter, 设 name/property/propertyLabel</li>
 *   <li>对 `function-parameter` Element 内的子 Elements: 找 name=`value` 的, 调 valueParser.parse</li>
 *   <li><b>single-writer 契约</b>: 多个 `function-parameter` 时, last-wins (setParameter 每次覆盖, 不累积)</li>
 * </ul>
 *
 * <p><b>Why V6.4 选这条</b>: V5.96 doc 立的 skip-pattern 之一, 22 文件主 cleanup 时
 * {@code LeftParser.buildCommonFunctionLeftPart:109-141} 2-level nested do-while
 * 被显式 skip ("build-time 调用 + private 方法 + 无 characterization test 覆盖不能
 * 安全重写 state machine")。 V6.0 立的 "重新审计内层独立性" 原则 + V6.3 KnowledgeBase
 * 3-level do-while flatten 经验 + 写 characterization test 锁契约 = V6.4 安全 flatten。
 *
 * <p>行为关键: 2-level do-while find-first pattern 实际行为是 "process all matching
 * items" — 等价 enhanced for + 2 个 continue (skip non-Element, skip non-function-parameter
 * Element)。 iterator 状态由 List.iterator 决定, 两种写法一致。 single-writer 契约
 * (last-wins) 由 setParameter 每次覆盖保证, 两种写法都保留。
 *
 * <p><b>Why V6.4 选 0 perf 信号档</b>: build-time 调用, 不在 rete hot path。 JFR
 * 0 sample 预期, 跟 V6.3 同档 pure code elegance closure。
 *
 * @see com.ruleforge.docs.notes.v644-leftparser-commonfunction-flatten V6.4 完整 doc
 * @since 6.4
 */
@DisplayName("V6.4 — LeftParser.buildCommonFunctionLeftPart 契约 (2-level do-while flatten)")
class LeftParserBuildCommonFunctionLeftPartTest {

    private LeftParser parser;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @BeforeEach
    void setUp() {
        parser = new LeftParser();
        ValueParser valueParser = new ValueParser();
        valueParser.setArithmeticParser(new ComplexArithmeticParser());
        parser.setValueParser(valueParser);
        parser.setComplexArithmeticParser(new ComplexArithmeticParser());
        parser.setSimpleArithmeticParser(new SimpleArithmeticParser());
    }

    /** Build a commonfunction Element with function-name / function-label attrs and arbitrary child params. */
    private static Element buildCommonFunctionElement(String name, String label) {
        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement("left");
        root.addAttribute("type", "commonfunction");
        root.addAttribute("function-name", name);
        root.addAttribute("function-label", label);
        return root;
    }

    private static void addFunctionParameter(Element parent, String name, String property, String propertyLabel) {
        Element p = parent.addElement("function-parameter");
        p.addAttribute("name", name);
        p.addAttribute("property-name", property);
        p.addAttribute("property-label", propertyLabel);
    }

    private static void addValueChild(Element parent) {
        Element v = parent.addElement("value");
        v.addAttribute("type", "Input");
        v.addAttribute("content", "42");
    }

    private CommonFunctionLeftPart doParse(Element element) {
        Left left = parser.parse(element);
        return (CommonFunctionLeftPart) left.getLeftPart();
    }

    // ─── Basic: function-name + function-label attrs ───────────────────────

    @Nested
    @DisplayName("basic: function-name + function-label attrs 复制到 part")
    class BasicAttributes {

        // Given: 1 commonfunction Element, no function-parameter children
        // When:  parser.parse(element)
        // Then:  part.getName() == function-name
        //        part.getLabel() == function-label
        //        part.getParameter() == null (没 function-parameter child)
        @Test
        @DisplayName("无 function-parameter children → part 有 name/label, parameter 为 null")
        void emptyCommonFunctionPart() {
            Element root = buildCommonFunctionElement("myFunc", "My Function");
            CommonFunctionLeftPart part = doParse(root);

            assertThat(part.getName()).isEqualTo("myFunc");
            assertThat(part.getLabel()).isEqualTo("My Function");
            assertThat(part.getParameter()).isNull();
        }
    }

    // ─── Single function-parameter happy path ──────────────────────────────

    @Nested
    @DisplayName("happy path: 单 function-parameter → part.parameter 装上")
    class SingleFunctionParameter {

        // Given: 1 function-parameter child (name + property + propertyLabel + value)
        // When:  parser.parse(element)
        // Then:  part.parameter 存在
        //        parameter.name = "p1"
        //        parameter.property = "prop1"
        //        parameter.propertyLabel = "Prop 1"
        //        parameter.objectParameter != null (valueParser.parse 跑过)
        @Test
        @DisplayName("单 function-parameter (with value 子 Element) → parameter 全字段装上, value parsed")
        void singleFunctionParameterWithValue() {
            Element root = buildCommonFunctionElement("fn", "Fn");
            Element p = root.addElement("function-parameter");
            p.addAttribute("name", "p1");
            p.addAttribute("property-name", "prop1");
            p.addAttribute("property-label", "Prop 1");
            addValueChild(p);

            CommonFunctionLeftPart part = doParse(root);

            CommonFunctionParameter param = part.getParameter();
            assertThat(param).isNotNull();
            assertThat(param.getName()).isEqualTo("p1");
            assertThat(param.getProperty()).isEqualTo("prop1");
            assertThat(param.getPropertyLabel()).isEqualTo("Prop 1");
            assertThat(param.getObjectParameter()).isNotNull();
        }

        // Given: 1 function-parameter child without value sub-Element
        // When:  parser.parse(element)
        // Then:  parameter exists with name/property/propertyLabel
        //        parameter.objectParameter == null (没 value 子 Element, 没 parse)
        @Test
        @DisplayName("单 function-parameter (no value 子 Element) → parameter 装上, objectParameter 为 null")
        void singleFunctionParameterWithoutValue() {
            Element root = buildCommonFunctionElement("fn", "Fn");
            addFunctionParameter(root, "p1", "prop1", "Prop 1");

            CommonFunctionLeftPart part = doParse(root);

            CommonFunctionParameter param = part.getParameter();
            assertThat(param).isNotNull();
            assertThat(param.getName()).isEqualTo("p1");
            assertThat(param.getProperty()).isEqualTo("prop1");
            assertThat(param.getPropertyLabel()).isEqualTo("Prop 1");
            assertThat(param.getObjectParameter()).isNull();
        }
    }

    // ─── Multi function-parameter: single-writer last-wins contract ────────

    @Nested
    @DisplayName("multi function-parameter: single-writer 契约 (last-wins)")
    class MultiFunctionParameterSingleWriter {

        // Given: 3 function-parameter children (各不同 name)
        // When:  parser.parse(element)
        // Then:  part.parameter == LAST (single-writer, setParameter 每次覆盖)
        //        part.parameter.name == "p3" (最后一次 setParameter 的)
        //        ⚠️ 现有行为是 "last-wins", 不是 "累积" — V5.96 doc 显式保留
        @Test
        @DisplayName("3 function-parameter → part.parameter 是 LAST (single-writer 契约)")
        void threeFunctionParametersLastWins() {
            Element root = buildCommonFunctionElement("fn", "Fn");
            addFunctionParameter(root, "p1", "prop1", "Prop 1");
            addFunctionParameter(root, "p2", "prop2", "Prop 2");
            addFunctionParameter(root, "p3", "prop3", "Prop 3");

            CommonFunctionLeftPart part = doParse(root);

            CommonFunctionParameter param = part.getParameter();
            assertThat(param).isNotNull();
            assertThat(param.getName()).isEqualTo("p3");
            assertThat(param.getProperty()).isEqualTo("prop3");
            assertThat(param.getPropertyLabel()).isEqualTo("Prop 3");
        }
    }

    // ─── Filter: skip non-Element + skip non-function-parameter ────────────

    @Nested
    @DisplayName("filter: skip non-Element + skip non-`function-parameter` Element")
    class FilterNonMatching {

        // Given: 混合 children — text/comment/non-function-parameter Element + 1 function-parameter
        // When:  parser.parse(element)
        // Then:  part.parameter == 唯一那个 function-parameter (其他全 skip)
        @Test
        @DisplayName("混合 (other-Element + text + 1 function-parameter) → 只处理 function-parameter")
        void mixedChildrenFilterToFunctionParameter() {
            Element root = buildCommonFunctionElement("fn", "Fn");
            // non-function-parameter Element (应被 skip)
            root.addElement("other-thing").addAttribute("foo", "bar");
            // function-parameter (应被处理)
            addFunctionParameter(root, "p1", "prop1", "Prop 1");
            // 另一个 non-function-parameter Element (应被 skip)
            root.addElement("another-thing").addAttribute("baz", "qux");

            CommonFunctionLeftPart part = doParse(root);

            assertThat(part.getParameter()).isNotNull();
            assertThat(part.getParameter().getName()).isEqualTo("p1");
        }

        // Given: 只有 non-function-parameter Element children
        // When:  parser.parse(element)
        // Then:  part.parameter == null (没匹配上)
        @Test
        @DisplayName("全 non-function-parameter Element children → part.parameter 为 null")
        void noMatchingElementProducesNullParameter() {
            Element root = buildCommonFunctionElement("fn", "Fn");
            root.addElement("other-thing").addAttribute("foo", "bar");
            root.addElement("another-thing").addAttribute("baz", "qux");

            CommonFunctionLeftPart part = doParse(root);

            assertThat(part.getParameter()).isNull();
        }
    }

    // ─── V6.4 死代码验证: name match 是精确 "function-parameter" 字符串 ──────

    @Nested
    @DisplayName("V6.4 死代码验证: name match 是精确 \"function-parameter\" 字符串,不是 contains/startsWith")
    class NameMatchIsExact {

        // Given: 1 Element named "function-parameters" (含 "function-parameter" 前缀)
        // When:  parser.parse(element)
        // Then:  part.parameter == null (name != "function-parameter", 跳过)
        @Test
        @DisplayName("name = \"function-parameters\" (含前缀) → parameter 为 null, 验证 equals() 不是 startsWith()")
        void nameWithPrefixIsNotMatched() {
            Element root = buildCommonFunctionElement("fn", "Fn");
            Element p = root.addElement("function-parameters");
            p.addAttribute("name", "p1");
            p.addAttribute("property-name", "prop1");
            p.addAttribute("property-label", "Prop 1");

            CommonFunctionLeftPart part = doParse(root);

            assertThat(part.getParameter()).isNull();
        }

        // Given: 1 Element named "common-function-parameter" (含 "function-parameter" 子串)
        // When:  parser.parse(element)
        // Then:  part.parameter == null (name != "function-parameter", 跳过)
        @Test
        @DisplayName("name = \"common-function-parameter\" (含子串) → parameter 为 null, 验证 equals() 不是 contains()")
        void nameWithSubstringIsNotMatched() {
            Element root = buildCommonFunctionElement("fn", "Fn");
            Element p = root.addElement("common-function-parameter");
            p.addAttribute("name", "p1");
            p.addAttribute("property-name", "prop1");
            p.addAttribute("property-label", "Prop 1");

            CommonFunctionLeftPart part = doParse(root);

            assertThat(part.getParameter()).isNull();
        }
    }
}
