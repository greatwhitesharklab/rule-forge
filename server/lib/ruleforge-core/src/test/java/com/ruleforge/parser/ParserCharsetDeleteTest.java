package com.ruleforge.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.43.2 / V5.44.3 — 删老 rule 链 parser + 4 library deserializer 后的"空 class 消失"快照测试。
 *
 * <p>本测试**不**测功能,只测源码目录结构:
 * <ul>
 *   <li>V5.43.2 删 {@code SpringBeanParser} + {@code NamedJunctionParser} +
 *       {@code RuleSetResourceBuilder} + {@code RuleSetDeserializer} 后,这些 class
 *       **不能**再被 classloader 找到(防止以后某次 refactor 又把它们救回来)</li>
 *   <li>V5.44.3 删 4 library deserializer( {@code ActionLibraryDeserializer} /
 *       {@code ConstantLibraryDeserializer} / {@code VariableLibraryDeserializer} /
 *       {@code ParameterLibraryDeserializer} )+ 4 library parser + 4 library
 *       ResourceBuilder + 1 个 {@code VariableLibraryResource}。library 走 DRL
 *       顶层 import 段(grammar V5.44.3 加 DRL_IMPORT 关键字,见 DrlLexer.g4)</li>
 *   <li>V5.47 删老资源级 {@code RuleSetParser}(向导式 .xml 根解析)— 配套删
 *       Spring bean {@code ruleforge.ruleSetParser}。{@code com.ruleforge.parse/}
 *       49 个字段级 XML 解析器(ActionParser / LhsParser / RhsParser / *CellParser
 *       等)**保留**(向导式规则体 XML 字段级,跟资源级不是同一层)</li>
 *   <li>{@code com.ruleforge.parse.deserializer} 目录**保留** 1 个 base
 *       {@code Deserializer.java} + 6 个 table/scorecard/crosstab/decisiontree/script
 *       deserializer(V5.40 / V5.41 后 .xml 兜底仍需)</li>
 * </ul>
 *
 * <p>本测试通过 {@code Class.forName} 验证 class 物理上消失,跟 BDD 解耦
 * (不依赖具体规则语义,只测"删干净")。
 *
 * @since 5.43
 */
@DisplayName("V5.43.2 / V5.44.3 — 删老 rule 链 + 4 library deserializer class 不再存在")
class ParserCharsetDeleteTest {

    @Test
    @DisplayName("SpringBeanParser / NamedJunctionParser / RuleSetDeserializer / RuleSetResourceBuilder 已删")
    void deletedClassesAreGone() {
        // 这些 class 在 V5.43.2 删,classloader 应抛 ClassNotFoundException
        List<String> deletedClasses = List.of(
            "com.ruleforge.parse.SpringBeanParser",
            "com.ruleforge.parse.NamedJunctionParser",
            "com.ruleforge.parse.deserializer.RuleSetDeserializer",
            "com.ruleforge.builder.resource.RuleSetResourceBuilder"
        );
        for (String fqn : deletedClasses) {
            try {
                Class.forName(fqn);
                throw new AssertionError(
                    "V5.43.2 删的 class '" + fqn + "' 仍可被 classloader 找到 — 删不彻底");
            } catch (ClassNotFoundException expected) {
                // 期望:删干净
            }
        }
    }

    @Test
    @DisplayName("V5.44.3 — 4 library deserializer + 4 library parser + 4 library ResourceBuilder 已删")
    void libraryChainGone() {
        // V5.44.3 删:library 走 DRL import 段,不再走 .xml deserializer 链
        List<String> deletedClasses = List.of(
            // 4 library deserializer
            "com.ruleforge.parse.deserializer.ActionLibraryDeserializer",
            "com.ruleforge.parse.deserializer.ConstantLibraryDeserializer",
            "com.ruleforge.parse.deserializer.VariableLibraryDeserializer",
            "com.ruleforge.parse.deserializer.ParameterLibraryDeserializer",
            // 4 library parser
            "com.ruleforge.parse.ActionLibraryParser",
            "com.ruleforge.parse.ConstantLibraryParser",
            "com.ruleforge.parse.VariableLibraryParser",
            "com.ruleforge.parse.ParameterLibraryParser",
            // 4 library ResourceBuilder + 1 helper Resource
            "com.ruleforge.builder.resource.ActionLibraryResourceBuilder",
            "com.ruleforge.builder.resource.ConstantLibraryResourceBuilder",
            "com.ruleforge.builder.resource.VariableLibraryResourceBuilder",
            "com.ruleforge.builder.resource.ParameterLibraryResourceBuilder",
            "com.ruleforge.builder.resource.VariableLibraryResource"
        );
        for (String fqn : deletedClasses) {
            try {
                Class.forName(fqn);
                throw new AssertionError(
                    "V5.44.3 删的 class '" + fqn + "' 仍可被 classloader 找到 — 删不彻底");
            } catch (ClassNotFoundException expected) {
                // 期望:删干净
            }
        }
    }

    @Test
    @DisplayName("V5.47 — 资源级 RuleSetParser 已删 + ruleforge.ruleSetParser Spring bean wiring 已移除")
    void ruleSetParserDeleted() throws Exception {
        // V5.47 删 com.ruleforge.parse.RuleSetParser(向导式 .xml 根解析,71 行);
        // 配套删 ruleforge-core-context.xml 里的 <bean id="ruleforge.ruleSetParser"> 块
        // (3 个 <property>: ruleParser / loopRuleParser / rulesRebuilder)。
        // 保留:com.ruleforge.parse/ 49 个字段级 XML 解析器(向导式规则体字段级)
        //      + com.ruleforge.parse.deserializer/ 1 base + 6 table-family deserializer
        String fqn = "com.ruleforge.parse.RuleSetParser";
        try {
            Class.forName(fqn);
            throw new AssertionError(
                "V5.47 删的 class '" + fqn + "' 仍可被 classloader 找到 — 删不彻底");
        } catch (ClassNotFoundException expected) {
            // 期望:删干净
        }

        // 验证 XML 资源文件不再含 bean id "ruleforge.ruleSetParser" 块
        // (xml 里残留的 <property> 引用若没清,Spring 启动报 NoSuchBeanDefinitionException)
        Path ctxXml = Path.of("src", "main", "resources", "ruleforge-core-context.xml");
        String xml = Files.readString(ctxXml);
        assertThat(xml)
            .as("ruleforge-core-context.xml 不应再含 ruleSetParser bean")
            .doesNotContain("ruleforge.ruleSetParser")
            .doesNotContain("com.ruleforge.parse.RuleSetParser");
    }

    @Test
    @DisplayName("com.ruleforge.parse.deserializer 仍保留 7 个 deserializer(1 base + 6 table/scorecard/crosstab/decisiontree/script)")
    void tableDeserializersKept() {
        // V5.43.2 保留 11 个(4 library + 1 base + 6 table);
        // V5.44.3 删 4 library 后剩 7 个(1 base + 6 table)
        List<String> keptClasses = List.of(
            // base 1
            "com.ruleforge.parse.deserializer.Deserializer",
            // table / crosstab / scorecard / decisiontree / script 6(V5.40 / V5.41 兜底)
            "com.ruleforge.parse.deserializer.DecisionTableDeserializer",
            "com.ruleforge.parse.deserializer.CrosstableDeserializer",
            "com.ruleforge.parse.deserializer.ScorecardDeserializer",
            "com.ruleforge.parse.deserializer.ComplexScorecardDeserializer",
            "com.ruleforge.parse.deserializer.DecisionTreeDeserializer",
            "com.ruleforge.parse.deserializer.ScriptDecisionTableDeserializer"
        );
        for (String fqn : keptClasses) {
            try {
                Class<?> cls = Class.forName(fqn);
                assertThat(cls).as("保留的 deserializer class 应存在:" + fqn).isNotNull();
            } catch (ClassNotFoundException e) {
                throw new AssertionError(
                    "V5.44.3 误删保留的 deserializer class: " + fqn, e);
            }
        }
    }

    @Test
    @DisplayName("com.ruleforge.parse/ 4 子目录(decisiontree / scorecard / table / crosstab)仍存在")
    void subdirsKept() throws Exception {
        // V5.43.2 不删 4 子目录
        Path parseDir = Path.of("src", "main", "java", "com", "ruleforge", "parse");
        List<String> expectedSubdirs = List.of("decisiontree", "scorecard", "table", "crosstab");
        for (String sub : expectedSubdirs) {
            Path p = parseDir.resolve(sub);
            assertThat(Files.isDirectory(p))
                .as("parse/" + sub + "/ 目录应保留").isTrue();
        }
    }
}
