package com.ruleforge.engine;

import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.plugin.EnginePluginRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V6.9.20 — {@link EngineContext#init} duplicate-name 行为契约 BDD。
 *
 * <p>锁 V6.9.20 收口 (EngineContext.java L36-52: containsKey + put → putIfAbsent + null check,
 * V5.93 mode) 的行为不变性:
 * <ul>
 *   <li><b>无重复</b>: functionDescriptorMap / functionDescriptorLabelMap 正确填充 (all
 *       FunctionDescriptor.isDisabled() == false 的都进 map)</li>
 *   <li><b>name 重复</b>: 抛 RuntimeException, message 含 "Duplicate function [name]"</li>
 *   <li><b>label 可重复</b>: byLabel 直接 put (当前契约 — V6.9.20 不动, 仍可重复)</li>
 *   <li><b>disabled 全跳过</b>: disabled=true 的 FunctionDescriptor 不进 map, 不触发重复检查</li>
 * </ul>
 *
 * <p><b>Why V6.9.20</b>: v69_pipeline P0-2 — Engine init path 最后一个 containsKey+put,
 * V5.93 mode 直接套, -1 行净, pure code elegance + 微 perf (HashMap.putIfAbsent 单 lookup)。
 */
@DisplayName("V6.9.20 — EngineContext.init putIfAbsent 契约")
class EngineContextInitDuplicateTest {

    private static FunctionDescriptor fd(String name, String label, boolean disabled) {
        FunctionDescriptor f = mock(FunctionDescriptor.class);
        when(f.getName()).thenReturn(name);
        when(f.getLabel()).thenReturn(label);
        when(f.isDisabled()).thenReturn(disabled);
        return f;
    }

    private static EnginePluginRegistry registry(FunctionDescriptor... fds) {
        EnginePluginRegistry r = mock(EnginePluginRegistry.class);
        Collection<FunctionDescriptor> c = List.of(fds);
        when(r.getFunctionDescriptors()).thenReturn(c);
        return r;
    }

    @Test
    @DisplayName("无重复 — functionDescriptorMap 应含所有非 disabled FunctionDescriptor")
    void noDuplicatePopulatesMap() {
        FunctionDescriptor f1 = fd("fn1", "label1", false);
        FunctionDescriptor f2 = fd("fn2", "label2", false);
        EngineContext.init(registry(f1, f2));

        assertThat(EngineContext.getFunctionDescriptorMap()).containsKeys("fn1", "fn2");
        assertThat(EngineContext.getFunctionDescriptorLabelMap()).containsKeys("label1", "label2");
    }

    @Test
    @DisplayName("name 重复 — 抛 RuntimeException 含 'Duplicate function [name]'")
    void duplicateNameThrows() {
        FunctionDescriptor f1 = fd("dup", "label1", false);
        FunctionDescriptor f2 = fd("dup", "label2", false);
        EnginePluginRegistry r = registry(f1, f2);

        assertThatThrownBy(() -> EngineContext.init(r))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Duplicate function [dup]");
    }

    @Test
    @DisplayName("disabled=true 全跳过 — 多个 disabled 同名不触发重复")
    void disabledAreSkipped() {
        FunctionDescriptor f1 = fd("fn1", "label1", true);
        FunctionDescriptor f2 = fd("fn1", "label2", true);
        EngineContext.init(registry(f1, f2));

        assertThat(EngineContext.getFunctionDescriptorMap()).isEmpty();
        assertThat(EngineContext.getFunctionDescriptorLabelMap()).isEmpty();
    }

    @Test
    @DisplayName("disabled 后 enabled 同名 — 不抛 (disabled 已跳过, 第二次 enabled 是首次进 map)")
    void disabledThenEnabledSameNameDoesNotThrow() {
        FunctionDescriptor f1 = fd("fn1", "label1", true);
        FunctionDescriptor f2 = fd("fn1", "label2", false);
        EngineContext.init(registry(f1, f2));

        assertThat(EngineContext.getFunctionDescriptorMap()).containsKey("fn1");
        assertThat(EngineContext.getFunctionDescriptorLabelMap()).containsKey("label2");
    }
}
