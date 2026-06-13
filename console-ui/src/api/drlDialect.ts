/**
 * V5.42.7 — Rule/DSL IR 来源方言 enum(console-ui 层,跟后端 Rule 字段镜像)。
 *
 * <p>目的:让前端组件能根据 dialect 决定:
 * <ul>
 *   <li>显示的 schema 类型(DRL 4 only,V5.43+ legacy-free)</li>
 *   <li>编辑器顶部的 "Source format" 指示器(只读,V5.43+ 单向:DRL 4 唯一)</li>
 * </ul>
 *
 * <p>Rule + DSL(.drl / .dsl / .dslrd)共用此 enum(都走 DRL 4 IR)。
 * 值跟后端 Java {@code com.ruleforge.ir.drl.DrlResourceDispatcher#dispatch} 检测逻辑一致
 * (详见 dispatcher:文件后缀 + dialect 字段判断)。
 *
 * <p><b>V5.42.7 限制</b>:console-ui 编辑器当前**只展示** dialect + 顶部 "Source format" badge,
 * 不解析 DRL 内容做完整编辑。DRL 完整编辑支持(双向 round-trip UI)是 V5.50+ 单独 PR(本 task 不含)。
 *
 * <p>三层 dialect 跟 DMN / PMML 平行 — RuleForge "路线 B" 三个 PR(V5.40 / V5.41 / V5.42)
 * 各自一层 dialect,consistency 是 console-ui 决定"显示哪个 source format badge"的唯一来源。
 *
 * <p><b>V5.43 — 路线 B 收口</b>:RULEFORGE_NATIVE 仍保留为 enum 值(兼容历史 response 字段),
 * 但 V5.43 起 production 不再生成 / 不再接受老 .ul / .xml rule 格式;DEFAULT 改 DRL,
 * detectDrlDialectFromFilePath 不再回退 RULEFORGE_NATIVE。
 *
 * @since 5.42
 */
export type DrlDialect = 'RULEFORGE_NATIVE' | 'DRL';

/**
 * Default dialect for V5.43+ rules。V5.43 路线 B 收口后 production 仅支持 DRL 4,
 * RULEFORGE_NATIVE 仅用于老 response 字段(运维不写新)。
 */
export const DEFAULT_DIALECT: DrlDialect = 'DRL';

/**
 * V5.42.7 — Detect dialect from file extension。V5.43+ 收口后仅 .drl / .drlrd / .dslr
 * 走 DRL,其它(老 .ul / .xml rule)不再回退 RULEFORGE_NATIVE(返回 DEFAULT = 'DRL')。
 * 这是 console-ui 一侧 "Source format" 指示器显示逻辑的源头(后端 KnowledgeBuilder
 * 在 dispatch 时会按真实文件内容覆盖,前端这个推断只在后端响应未就绪时用)。
 */
export function detectDrlDialectFromFilePath(filePath: string): DrlDialect {
    if (!filePath) return DEFAULT_DIALECT;
    const lower = filePath.toLowerCase();
    if (lower.endsWith('.drl') || lower.endsWith('.drlrd') || lower.endsWith('.dslr')) {
        return 'DRL';
    }
    return DEFAULT_DIALECT;
}

/**
 * V5.42.7 — Human-readable label for each dialect(给 console-ui 顶部 "Source format" 指示器用)。
 * V5.43+ 调整:RULEFORGE_NATIVE 文案改"V5.43 deprecated",DRL 文案强调 V5.43 legacy-free。
 */
export function drlDialectLabel(dialect: DrlDialect | null | undefined): string {
    if (!dialect) return 'DRL 4 (V5.43+ legacy-free)';
    switch (dialect) {
        case 'RULEFORGE_NATIVE':
            return 'RULEFORGE_NATIVE (V5.43 deprecated)';
        case 'DRL':
            return 'DRL 4 (V5.43+ legacy-free)';
        default:
            return 'Unknown';
    }
}

/**
 * V5.42.7 — Source format badge 颜色(跟 V5.40.6 / V5.41.6 一致:
 * legacy = 灰(中性,提醒不写新),standard = 蓝绿(主推方向))。
 * 实际渲染走 antd Tag,这里只返回 antd 预设颜色名,组件自己转成 <Tag color="...">。
 */
export function drlDialectBadgeColor(dialect: DrlDialect | null | undefined): string {
    if (dialect === 'DRL') return 'geekblue';
    return 'default';
}
