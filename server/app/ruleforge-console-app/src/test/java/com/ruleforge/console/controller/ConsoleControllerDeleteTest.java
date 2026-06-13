package com.ruleforge.console.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.43.3 — console-app 删老 .ul / .xml rule 路径 controller 后的"类消失"快照测试。
 *
 * <p>本测试**不**测功能,只测源码结构:
 * <ul>
 *   <li>V5.43.3 删 {@code ULEditorController} / {@code XmlController} 整文件 — 这两个 class
 *       **不能**再被 classloader 找到</li>
 *   <li>{@code CommonController.scriptValidation} / {@code findRuleByKey} 整方法被删 —
 *       防止以后 PR 又把这些老 .ul / 老 .xml rule 路径加回来</li>
 *   <li>同时验证 {@code CommonController} 本身的"非 .ul 业务"(loadResourceTreeData 等)
 *       **未**被误删</li>
 * </ul>
 *
 * <p>本测试用反射确认方法在 class 上消失(防止"删方法但留空 stub"假删除)。
 *
 * @since 5.43
 */
@DisplayName("V5.43.3 — 删老 .ul / .xml rule 路径 controller 后 class/method 不再存在")
class ConsoleControllerDeleteTest {

    @Test
    @DisplayName("ULEditorController / XmlController 整文件已删")
    void oldControllersGone() {
        for (String fqn : List.of(
            "com.ruleforge.console.controller.ULEditorController",
            "com.ruleforge.console.controller.XmlController"
        )) {
            try {
                Class.forName(fqn);
                throw new AssertionError(
                    "V5.43.3 删的 controller class '" + fqn + "' 仍可被 classloader 找到 — 删不彻底");
            } catch (ClassNotFoundException expected) {
                // 期望:删干净
            }
        }
    }

    @Test
    @DisplayName("CommonController.scriptValidation / scriptValidationText 整方法已删(scriptValidation 走老 RuleParserLexer,V5.43.6 删)")
    void oldScriptValidationGone() throws Exception {
        Class<?> cls = Class.forName("com.ruleforge.console.controller.CommonController");
        for (String methodName : List.of("scriptValidation", "scriptValidationText")) {
            try {
                cls.getDeclaredMethod(methodName, getParamTypes(methodName));
                throw new AssertionError(
                    "V5.43.3 应删的 CommonController." + methodName + "() 仍存在 — 删不彻底");
            } catch (NoSuchMethodException expected) {
                // 期望:删干净
            }
        }
    }

    @Test
    @DisplayName("CommonController.findRuleByKey 留 stub(method 签名保留)— 防 console-ui 老 build 缓存 404")
    void findRuleByKeyStubbed() throws Exception {
        Class<?> cls = Class.forName("com.ruleforge.console.controller.CommonController");
        // /findRuleByKey 留 method 签名返空 list,V5.43.7 删 console-ui 调用后再彻底删
        java.lang.reflect.Method m = cls.getDeclaredMethod("findRuleByKey", String.class, String.class);
        // 不真 invoke(CommonController @RequiredArgsConstructor,无 default ctor),
        // 只验 method 签名存在 + 返 List 类型
        assertThat(m.getReturnType()).isEqualTo(List.class);
    }

    @Test
    @DisplayName("CommonController 非 .ul 业务(loadResourceTreeData / checkFileDirty)仍保留")
    void commonControllerNonUlBusinessKept() throws Exception {
        Class<?> cls = Class.forName("com.ruleforge.console.controller.CommonController");
        // loadResourceTreeData 跟 checkFileDirty 跟 .ul / .xml rule 无关,应保留
        assertThat(cls.getDeclaredMethod("loadResourceTreeData",
                String.class, String.class, String.class, String.class))
            .as("CommonController.loadResourceTreeData 应保留(非 .ul 业务)")
            .isNotNull();
        assertThat(cls.getDeclaredMethod("checkFileDirty", String.class, String.class))
            .as("CommonController.checkFileDirty 应保留(非 .ul 业务)")
            .isNotNull();
    }

    private static Class<?>[] getParamTypes(String methodName) {
        return switch (methodName) {
            case "scriptValidation" -> new Class<?>[]{String.class, String.class};
            case "scriptValidationText" -> new Class<?>[]{String.class, String.class};
            default -> new Class<?>[0];
        };
    }
}
