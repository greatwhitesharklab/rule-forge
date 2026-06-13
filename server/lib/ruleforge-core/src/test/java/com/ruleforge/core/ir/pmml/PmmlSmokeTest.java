package com.ruleforge.core.ir.pmml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pmml4s.data.Series;
import org.pmml4s.model.Model;
import org.pmml4s.model.Scorecard;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.41 — pmml4s 1.5.6 加载验证 (Sample BDD)。
 *
 * <p>5 BDD 分 3 组:基本加载 / Scorecard 提取 / 求值可执行。
 *
 * <p>目的:V5.41 PR 切评分卡→PMML 4.4 的"前置条件"——pmml4s 1.5.6 能
 * 加载 PMML 4.4 Scorecard 求值。失败立刻告知,免得后续 PmmlScorecardDeserializer
 * 写完才发现底层跑不动。
 *
 * <p>实测 1.5.6 + Scala 2.13 + Java 17 OK。Scala → Java 桥:
 * <ul>
 *   <li>{@code org.pmml4s.model.Model.fromInputStream(InputStream)} — Scala companion 静态加载</li>
 *   <li>{@code Scorecard} — Scorecard 子类,继承 {@code Model.predict(Series)}</li>
 *   <li>{@code org.pmml4s.data.Series.fromMap(Map<String,Object>)} — Scala companion 静态构造输入</li>
 * </ul>
 *
 * <p><b>Scorecard 求值语义</b>(PMML 4.4 spec,pmml4s 1.5.6 实现):
 * <ul>
 *   <li>每个 Characteristic 内 attribute 命中取 <b>max</b>(PMML 4.4 定义,跟 Sum 区分)</li>
 *   <li>多个 Characteristic 之间 baselineMethod 决定聚合:{@code max / min / sum / none}</li>
 *   <li>最后 + initialScore 才是最终 score</li>
 *   <li>所以本 fixture(2 characteristic 各 max 10/5,initialScore=0,baselineMethod=max)
 *       期望 score = max(10, 5) + 0 = 10</li>
 * </ul>
 */
@DisplayName("V5.41 — pmml4s 1.5.6 Sample BDD")
class PmmlSmokeTest {

    @Nested
    @DisplayName("Group 1 — 基本加载")
    class LoadPmml {

        @Test
        @DisplayName("Given PMML 4.4 Scorecard XML 文件,When Model.fromInputStream 加载,Then 拿到非 null Scorecard")
        void loadsPmml4_4Scorecard() throws Exception {
            try (InputStream is = getClass().getResourceAsStream(
                "/ir/fixtures/simple-scorecard.pmml")) {
                assertNotNull(is, "PMML fixture must be on classpath");
                Model model = Model.fromInputStream(is);
                assertNotNull(model, "Model must not be null");
                assertTrue(model instanceof Scorecard,
                    "Expected Scorecard, got " + model.getClass().getName());
            }
        }

        @Test
        @DisplayName("Given 加载的 Scorecard,When 取 modelName,Then 是 'customer_score'")
        void scorecardExposesModelName() throws Exception {
            try (InputStream is = getClass().getResourceAsStream(
                "/ir/fixtures/simple-scorecard.pmml")) {
                Scorecard sc = (Scorecard) Model.fromInputStream(is);
                // modelName() returns Option<String> in Scala
                scala.Option<String> name = sc.modelName();
                assertTrue(name.isDefined(), "modelName must be defined");
                assertEquals("customer_score", name.get());
            }
        }
    }

    @Nested
    @DisplayName("Group 2 — Scorecard 求值")
    class Score {

        private Series inputs(double age) {
            Map<String, Object> m = new HashMap<>();
            m.put("age", age);
            return Series.fromMap(m);
        }

        @Test
        @DisplayName("Given age=40 (1 char 1 attr, age>30 命中),When predict,Then 10")
        void middleScores10() throws Exception {
            try (InputStream is = getClass().getResourceAsStream(
                "/ir/fixtures/simple-scorecard.pmml")) {
                Scorecard sc = (Scorecard) Model.fromInputStream(is);
                Series result = sc.predict(inputs(40.0));
                int scoreIdx = result.schema().fieldIndex("predicted_score");
                assertEquals(10.0, result.getDouble(scoreIdx), 0.001,
                    "40 hits age>30 (10), baselineMethod=max char.value=10, +initialScore=0 -> 10");
            }
        }

        @Test
        @DisplayName("Given age=20 (0 attr 命中),When predict,Then 0")
        void youngScores0() throws Exception {
            try (InputStream is = getClass().getResourceAsStream(
                "/ir/fixtures/simple-scorecard.pmml")) {
                Scorecard sc = (Scorecard) Model.fromInputStream(is);
                Series result = sc.predict(inputs(20.0));
                int scoreIdx = result.schema().fieldIndex("predicted_score");
                assertEquals(0.0, result.getDouble(scoreIdx), 0.001,
                    "20 hits 0 attrs, char.score=0, +initialScore=0 -> 0");
            }
        }

        @Test
        @DisplayName("Given age=60 (1 attr 命中 partialScore=10),When predict,Then 10")
        void olderScores10() throws Exception {
            try (InputStream is = getClass().getResourceAsStream(
                "/ir/fixtures/simple-scorecard.pmml")) {
                Scorecard sc = (Scorecard) Model.fromInputStream(is);
                Series result = sc.predict(inputs(60.0));
                int scoreIdx = result.schema().fieldIndex("predicted_score");
                assertEquals(10.0, result.getDouble(scoreIdx), 0.001,
                    "60 hits age>30 (10), +initialScore=0 -> 10");
            }
        }
    }
}
