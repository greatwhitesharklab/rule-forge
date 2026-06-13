package com.ruleforge.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.43.2 — 删老 rule 链 parser + RuleSetDeserializer 后的"空 class 消失"快照测试。
 *
 * <p>本测试**不**测功能,只测源码目录结构:
 * <ul>
 *   <li>V5.43.2 删 {@code SpringBeanParser} + {@code NamedJunctionParser} +
 *       {@code RuleSetResourceBuilder} + {@code RuleSetDeserializer} 后,这些 class
 *       **不能**再被 classloader 找到(防止以后某次 refactor 又把它们救回来)</li>
 *   <li>{@code com.ruleforge.parse.deserializer} 目录**保留** 4 个 library deserializer
 *       + 1 个 base {@code Deserializer.java} + 6 个 table/scorecard/crosstab/decisiontree/script
 *       deserializer(V5.40 / V5.41 后 .xml 兜底仍需)</li>
 * </ul>
 *
 * <p>本测试通过 {@code Class.forName} 验证 class 物理上消失,跟 BDD 解耦
 * (不依赖具体规则语义,只测"删干净")。
 *
 * @since 5.43
 */
@DisplayName("V5.43.2 — 删老 rule 链 parser class 不再存在")
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
    @DisplayName("com.ruleforge.parse.deserializer 仍保留 11 个 deserializer(4 library + 1 base + 6 table/scorecard/crosstab/decisiontree/script)")
    void libraryAndTableDeserializersKept() {
        // V5.43.2 保留(运维 + V5.40 / V5.41 兜底需要)
        List<String> keptClasses = List.of(
            // library 4
            "com.ruleforge.parse.deserializer.ActionLibraryDeserializer",
            "com.ruleforge.parse.deserializer.ConstantLibraryDeserializer",
            "com.ruleforge.parse.deserializer.VariableLibraryDeserializer",
            "com.ruleforge.parse.deserializer.ParameterLibraryDeserializer",
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
                    "V5.43.2 误删保留的 deserializer class: " + fqn, e);
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
