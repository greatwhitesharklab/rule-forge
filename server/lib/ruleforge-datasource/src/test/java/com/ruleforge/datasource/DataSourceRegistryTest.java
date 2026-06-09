package com.ruleforge.datasource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataSourceRegistry — bean 容器 + 审计")
class DataSourceRegistryTest {

    // ====== 测试用 mock DS ======

    static class TestDs extends BaseApiDataSource {
        private final String name;
        private final Vars output;
        TestDs(String name, Vars output) { this.name = name; this.output = output; }
        @Override public String getName() { return name; }
        @Override public Map<String, String> getSchema() { return Map.of("ok", "BOOL"); }
        @Override public Vars fetch(Vars inputs) {
            if (Boolean.TRUE.equals(inputs.getBool("fail"))) {
                throw new ApiCallException(name, "intentional fail", null);
            }
            return output;
        }
    }

    @Nested
    @DisplayName("Scenario: 注册 / 查找")
    class RegisterLookup {

        @Test
        @DisplayName("register 后 listNames 包含")
        void shouldRegister() {
            DataSourceRegistry reg = new DataSourceRegistry();
            reg.register(new TestDs("a", new Vars().put("ok", true)));
            assertThat(reg.listNames()).containsExactly("a");
            assertThat(reg.contains("a")).isTrue();
        }

        @Test
        @DisplayName("register 同名 = 覆盖(热加载用)")
        void shouldReplaceOnDuplicateName() {
            DataSourceRegistry reg = new DataSourceRegistry();
            reg.register(new TestDs("a", new Vars().put("v", 1)));
            reg.register(new TestDs("a", new Vars().put("v", 2)));
            assertThat(reg.listNames()).hasSize(1);
            Vars out = reg.fetch("a", new Vars());
            assertThat(out.getInt("v")).isEqualTo(2);
        }

        @Test
        @DisplayName("unregister 移除")
        void shouldUnregister() {
            DataSourceRegistry reg = new DataSourceRegistry();
            reg.register(new TestDs("a", new Vars()));
            reg.unregister("a");
            assertThat(reg.contains("a")).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario: fetch 调用")
    class Fetch {

        @Test
        @DisplayName("fetch(name, inputs) 调 DS.fetch 并返结果")
        void shouldDelegateToDs() {
            DataSourceRegistry reg = new DataSourceRegistry();
            reg.register(new TestDs("a", new Vars().put("score", 720)));
            Vars out = reg.fetch("a", new Vars().put("id", "110101"));
            assertThat(out.getInt("score")).isEqualTo(720);
        }

        @Test
        @DisplayName("fetch 未注册 name 抛 IllegalArgumentException")
        void shouldThrowOnUnknownName() {
            DataSourceRegistry reg = new DataSourceRegistry();
            assertThatThrownBy(() -> reg.fetch("missing", new Vars()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("fetch DS 抛 ApiCallException 时 registry 不吞,直接传出去")
        void shouldPropagateApiException() {
            DataSourceRegistry reg = new DataSourceRegistry();
            reg.register(new TestDs("a", new Vars()));
            assertThatThrownBy(() -> reg.fetch("a", new Vars().put("fail", true)))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("intentional fail");
        }
    }

    @Nested
    @DisplayName("Scenario: 审计日志")
    class AuditLog {

        static class InMemoryAudit implements DataSourceAuditLog {
            final List<Record> records = new ArrayList<>();
            @Override public void record(String ds, Vars in, Vars out, long ms, boolean ok, String err) {
                records.add(new Record(ds, in, out, ms, ok, err));
            }
            record Record(String ds, Vars in, Vars out, long ms, boolean ok, String err) {}
        }

        @Test
        @DisplayName("成功调用写一条 audit record(success=true, error=null)")
        void shouldAuditOnSuccess() {
            InMemoryAudit audit = new InMemoryAudit();
            DataSourceRegistry reg = new DataSourceRegistry(audit);
            reg.register(new TestDs("a", new Vars().put("v", 1)));
            reg.fetch("a", new Vars());
            assertThat(audit.records).hasSize(1);
            assertThat(audit.records.get(0).ok()).isTrue();
            assertThat(audit.records.get(0).err()).isNull();
            assertThat(audit.records.get(0).ms()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("失败调用写 audit record(success=false, error=msg)")
        void shouldAuditOnFailure() {
            InMemoryAudit audit = new InMemoryAudit();
            DataSourceRegistry reg = new DataSourceRegistry(audit);
            reg.register(new TestDs("a", new Vars()));
            try { reg.fetch("a", new Vars().put("fail", true)); } catch (Exception ignored) {}
            assertThat(audit.records).hasSize(1);
            assertThat(audit.records.get(0).ok()).isFalse();
            assertThat(audit.records.get(0).err()).contains("intentional fail");
        }

        @Test
        @DisplayName("audit 实现抛异常不破坏主调用")
        void shouldTolerateAuditFailure() {
            DataSourceAuditLog throwing = (ds, in, out, ms, ok, err) -> {
                throw new RuntimeException("audit db down");
            };
            DataSourceRegistry reg = new DataSourceRegistry(throwing);
            reg.register(new TestDs("a", new Vars().put("v", 1)));
            Vars out = reg.fetch("a", new Vars());  // 不能因为 audit 失败就挂
            assertThat(out.getInt("v")).isEqualTo(1);
        }
    }
}
