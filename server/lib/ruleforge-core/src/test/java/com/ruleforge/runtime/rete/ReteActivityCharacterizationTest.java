package com.ruleforge.runtime.rete;
import com.ruleforge.engine.NodeActivityFactory;
import com.ruleforge.engine.Path;
import com.ruleforge.engine.Activity;

import com.ruleforge.model.GeneralEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TD-2.1 — RETE Activity 原语行为锁。
 *
 * <p>RETE node/activity 融合是 grandfathered 架构(model.rete 的节点自带 runtime.rete
 * 行为),后续 TD-2.2 分离时不能改变这些语义。本文件为分离前的现状快照:
 * <ul>
 *   <li>ObjectTypeActivity.support:对 GeneralEntity 比 targetClass,对裸对象比 typeClass</li>
 *   <li>AndActivity:多 Path 全部 isPassed 才 joinNodeIsPassed(需至少 1 path;无 path 抛 NPE 是已知缺陷,见 test)</li>
 *   <li>OrActivity:joinNodeIsPassed 在 passed=false 时递归 path.getTo;无 path 抛 NPE 同上</li>
 *   <li>ObjectTypeNode.newActivity:为每条 Line 调 line.newPath 把下游 Activity 串起来</li>
 *   <li>TerminalNode.newActivity:同一节点第二次调应从 context 复用 TerminalActivity</li>
 * </ul>
 */
@DisplayName("TD-2.1 — RETE Activity 原语行为锁")
class ReteActivityCharacterizationTest {

    @Nested
    @DisplayName("ObjectTypeActivity.support — 类匹配判定")
    class ObjectTypeSupport {

        @Test
        @DisplayName("String 构造的 OTN 应匹配同 targetClass 的 GeneralEntity")
        void shouldMatchGeneralEntityByTargetClass() {
            ObjectTypeActivity activity = new ObjectTypeActivity("User");
            GeneralEntity entity = new GeneralEntity("User");
            assertThat(activity.support(entity)).isTrue();
        }

        @Test
        @DisplayName("String 构造的 OTN 不应匹配 targetClass 不同的 GeneralEntity")
        void shouldNotMatchDifferentTargetClass() {
            ObjectTypeActivity activity = new ObjectTypeActivity("User");
            GeneralEntity entity = new GeneralEntity("Admin");
            assertThat(activity.support(entity)).isFalse();
        }

        @Test
        @DisplayName("String 构造的 OTN 应对裸 String 不匹配(裸对象走 typeClass 支路)")
        void stringClazzShouldNotMatchPlainObject() {
            ObjectTypeActivity activity = new ObjectTypeActivity("User");
            Object plainObject = new Object();
            assertThat(activity.support(plainObject)).isFalse();
        }

        @Test
        @DisplayName("Class 构造的 OTN 应匹配该类及其子类(assignableFrom)")
        void classClazzShouldMatchAssignable() {
            ObjectTypeActivity activity = new ObjectTypeActivity(java.util.HashMap.class);
            java.util.LinkedHashMap child = new java.util.LinkedHashMap();
            assertThat(activity.support(child)).isTrue();
        }
    }

    @Nested
    @DisplayName("AndActivity — 多 Path 全部通过才 joinNodeIsPassed")
    class AndJoin {

        @Test
        @DisplayName("AndActivity.joinNodeIsPassed 无 path 时返 false (V6.9.2 — 修 pre-existing NPE, 收口 state machine 顺手 null safety)")
        void noPathsReturnsFalse() {
            AndActivity and = new AndActivity();
            assertThat(and.joinNodeIsPassed()).isFalse();
        }

        @Test
        @DisplayName("AndActivity addFromPath 后 joinNodeIsPassed 受 each fromPath.isPassed 控制")
        void fromPathsGateJoin() {
            AndActivity and = new AndActivity();
            // 1 path 通过 + 自身 passed=false → 仍 false
            Path p1 = new Path(new ObjectTypeActivity("X"));
            p1.setPassed(true);
            and.addPath(p1);
            and.addFromPath(p1);
            assertThat(and.joinNodeIsPassed()).isFalse();
            // 触发 passAndNode → passed=true
            and.passAndNode();
            assertThat(and.joinNodeIsPassed()).isTrue();
        }

        @Test
        @DisplayName("AndActivity reset 把 passed 置回 false")
        void resetClearsPassed() {
            AndActivity and = new AndActivity();
            and.passAndNode();
            and.reset();
            Path p1 = new Path(new ObjectTypeActivity("X"));
            and.addPath(p1);
            assertThat(and.joinNodeIsPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("OrActivity — joinNodeIsPassed 递归语义")
    class OrJoin {

        @Test
        @DisplayName("OrActivity.joinNodeIsPassed 无 path 时返 false (V6.9.2 — 修 pre-existing NPE, 收口 state machine 顺手 null safety)")
        void noPathsReturnsFalse() {
            OrActivity or = new OrActivity();
            assertThat(or.joinNodeIsPassed()).isFalse();
        }

        @Test
        @DisplayName("OrActivity passAndNode 是空实现,passed 不变")
        void passAndNodeIsNoop() {
            OrActivity or = new OrActivity();
            or.passAndNode();
            // 仍需加 path 才能调,否则 NPE;此处验证 passAndNode 不改 passed
            Path p1 = new Path(new ObjectTypeActivity("X"));
            or.addPath(p1);
            assertThat(or.joinNodeIsPassed()).isFalse();
        }

        @Test
        @DisplayName("OrActivity reset 把 passed 置回 false")
        void resetClearsPassed() {
            OrActivity or = new OrActivity();
            or.reset();
            Path p1 = new Path(new ObjectTypeActivity("X"));
            or.addPath(p1);
            assertThat(or.joinNodeIsPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("NodeActivityFactory — 节点→Activity 工厂")
    class ObjectTypeNodeFactory {

        @Test
        @DisplayName("factory.create 应返 ObjectTypeActivity + 包含 1 条 Path(对每条 Line)")
        void shouldCreateActivityWithPaths() {
            com.ruleforge.model.rete.ObjectTypeNode node =
                new com.ruleforge.model.rete.ObjectTypeNode("User", 1);
            com.ruleforge.model.rete.TerminalNode terminal =
                new com.ruleforge.model.rete.TerminalNode(new com.ruleforge.model.rule.Rule(), 2);
            node.addLine(terminal);

            java.util.Map<Object, Object> ctx = new java.util.HashMap<>();
            Activity activity = NodeActivityFactory.create(node, ctx);
            assertThat(activity).isInstanceOf(ObjectTypeActivity.class);
            assertThat(((ObjectTypeActivity) activity).getPaths()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("NodeActivityFactory — 复用语义")
    class TerminalNodeFactory {

        @Test
        @DisplayName("同一节点第二次 factory.create 应返同 instance(context 缓存)")
        void shouldReuseFromContext() {
            com.ruleforge.model.rete.TerminalNode node =
                new com.ruleforge.model.rete.TerminalNode(new com.ruleforge.model.rule.Rule(), 1);

            java.util.Map<Object, Object> ctx = new java.util.HashMap<>();
            Activity a1 = NodeActivityFactory.create(node, ctx);
            Activity a2 = NodeActivityFactory.create(node, ctx);
            assertThat(a1).isSameAs(a2);
        }

        @Test
        @DisplayName("不同 context 应各自新建 TerminalActivity")
        void shouldNotShareAcrossContexts() {
            com.ruleforge.model.rete.TerminalNode node =
                new com.ruleforge.model.rete.TerminalNode(new com.ruleforge.model.rule.Rule(), 1);

            Activity a1 = NodeActivityFactory.create(node, new java.util.HashMap<>());
            Activity a2 = NodeActivityFactory.create(node, new java.util.HashMap<>());
            assertThat(a1).isNotSameAs(a2);
        }
    }
}
