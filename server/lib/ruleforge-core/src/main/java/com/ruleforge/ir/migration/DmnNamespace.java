package com.ruleforge.ir.migration;

/**
 * V5.40.5 — DMN 1.3 namespace 常量(给一次性迁移工具用)。
 *
 * <p>基于 V5.40.1 实战确认:Kie 10.1.0 实测稳定加载的 namespace 是 DMN 1.3
 * ({@code 20191111/MODEL/}),不是 1.4 (20211108)。
 *
 * @since 5.40
 */
public final class DmnNamespace {
    private DmnNamespace() {
        // utility class
    }

    public static final String DMN_1_3_MODEL = "https://www.omg.org/spec/DMN/20191111/MODEL/";
    public static final String DMN_1_3_DMNDI = "https://www.omg.org/spec/DMN/20191111/DMNDI/";
}
