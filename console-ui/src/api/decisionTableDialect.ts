/**
 * V5.40.6 — DecisionTable IR 来源方言 enum(console-ui 层,跟后端 com.ruleforge.model.table.TableDialect 镜像)。
 *
 * <p>目的:让前端组件能根据 dialect 决定:
 * <ul>
 *   <li>显示的 schema 类型(老 RuleForge XML vs DMN 1.3)</li>
 *   <li>编辑器可用的 action 集合(DMN 表不能编辑 hit policy aggregation 等 RuleForge 扩展字段)</li>
 *   <li>"Source format" toggle 状态(只读指示器,V5.40+ 单向:XML → DMN,不回退)</li>
 * </ul>
 *
 * <p>值跟后端 {@code com.ruleforge.model.table.TableDialect} 100% 对齐,序列化走 {@code .name()}
 * (Java 端输出 RULEFORGE_NATIVE / DMN 字符串)。
 */
export type TableDialect = 'RULEFORGE_NATIVE' | 'DMN';

/**
 * Default dialect for V5.39-and-earlier decision tables (no dialect field set).
 * 老 .xml 决策表反序列化后 dialect=null,前端统一显示为 RULEFORGE_NATIVE。
 */
export const DEFAULT_DIALECT: TableDialect = 'RULEFORGE_NATIVE';

/**
 * V5.40.6 — Detect dialect from file extension(.dmn → DMN, .xml → RULEFORGE_NATIVE).
 * 这是 console-ui 一侧 "Source format toggle" 显示逻辑的源头(后端 KnowledgeBuilder
 * 在 dispatch 时会按真实文件内容覆盖,前端这个推断只在后端响应未就绪时用)。
 */
export function detectDialectFromFilePath(filePath: string): TableDialect {
    if (!filePath) return DEFAULT_DIALECT;
    const lower = filePath.toLowerCase();
    if (lower.endsWith('.dmn')) {
        return 'DMN';
    }
    return DEFAULT_DIALECT;
}

/**
 * V5.40.6 — Human-readable label for each dialect(给 console-ui 顶部 "Source format" 指示器用)。
 */
export function dialectLabel(dialect: TableDialect | null | undefined): string {
    if (!dialect) return 'RuleForge XML (legacy)';
    switch (dialect) {
        case 'RULEFORGE_NATIVE':
            return 'RuleForge XML (legacy)';
        case 'DMN':
            return 'DMN 1.3 (V5.40+)';
        default:
            // exhaustiveness check — should never hit
            return 'Unknown';
    }
}
