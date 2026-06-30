/**
 * V7.18 — DRL → V1 RuleSet 导入适配器(极简子集)。
 *
 * <p>DRL(Drools Rule Language)是老 urule/规则引擎的事实标准。V1 极简愿景
 * (project_v70_minimal_vision)明确 DRL/DMN/PMML = Import/Export Adapter。
 * 本适配器处理 DRL 的<b>最小公共子集</b>,用于把老 DRL 规则迁到 V1 RuleSet。
 *
 * <p><b>支持的 DRL 子集</b>:
 * <pre>
 *   package x.y;          ← 忽略(warning)
 *   import x.Y;           ← 忽略(warning)
 *   global X x;           ← 忽略(warning)
 *
 *   rule "name" [salience N]
 *   when
 *     <field1> (==|!=|>=|<=|>|<) <literal> [, <field2> ...]   ← 逗号/`&&` 表 AND
 *   then
 *     setDecision("value") | reject("reason") | set <field>(<value>)
 *   end
 * </pre>
 *
 * <p><b>不支持(返 warning,跳过)</b>: `||` (OR)、{@code modify($fact){...}} 块、
 * {@code $var : Type(...)} 绑定、函数调用、accumulate、salience 之外的
 * attributes (no-loop/activation-group/duration/...)、query、declare。
 *
 * <p>条件映射: DRL {@code age >= 18} → CEL {@code age >= 18}。字面量
 * (数字/布尔/字符串)直接转 CEL 字面量。条件用 {@code &&} 连接(DRL 逗号
 * 在 Pattern 内也是 AND)。
 *
 * <p>规则: 解析失败的整条 rule 进 warnings 列表,不中断其他规则;多条
 * rule 全部尝试。返 {@link DrlImportResult},UI 可预览 ruleSet + warnings
 * 后决定是否应用。
 */
import type {Rule, Action, ActionType} from './ruleAsset';

/** DRL 导入产出的 RuleSet 形状(结构兼容 RuleSetEditor 本地 RuleSet)。 */
export interface ImportedRuleSet {
    id: string;
    type: 'RuleSet';
    name: string;
    hitPolicy: string;
    rules: Rule[];
}

export interface DrlImportResult {
    ruleSet: ImportedRuleSet;
    /** 解析过程中跳过/降级的项,UI 展示给用户。 */
    warnings: string[];
}

const RULE_RE = /rule\s+"([^"]+)"(?:\s+salience\s+(\d+))?\s+when([\s\S]*?)then([\s\S]*?)end/g;
const CONDS_RE = /\b([A-Za-z_]\w*)\s*(==|!=|>=|<=|>|<)\s*("(?:[^"\\]|\\.)*"|true|false|-?\d+(?:\.\d+)?)\b/g;
const ACTION_SETDECISION_RE = /setDecision\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)/;
const ACTION_REJECT_RE = /reject\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)/;
const ACTION_SET_RE = /\bset\s+([A-Za-z_]\w*)\s*\(\s*("(?:[^"\\]|\\.)*"|true|false|-?\d+(?:\.\d+)?)\s*\)/g;

/** 把 DRL 字符串解析成 V1 RuleSet。schemaName 作为 fact 模型名(写入 ruleSet id 便于辨识)。 */
export function parseDrlToRuleSet(drl: string, schemaName = 'Fact'): DrlImportResult {
    const warnings: string[] = [];
    const rules: Rule[] = [];
    const text = drl || '';
    let m: RegExpExecArray | null;
    let i = 0;
    while ((m = RULE_RE.exec(text)) !== null) {
        i++;
        const ruleName = m[1];
        const salience = m[2] ? Number(m[2]) : 0;
        const whenBlock = m[3];
        const thenBlock = m[4];
        const condExpr = buildCondition(whenBlock, warnings, i);
        const actions = buildActions(thenBlock, warnings, i) as Action[];
        if (condExpr === null || actions.length === 0) {
            warnings.push(`rule[${i}] "${ruleName}": 解析失败或无 action,跳过`);
            continue;
        }
        const rule: Rule = {
            id: `r${i}_${ruleName.replace(/[^A-Za-z0-9_]/g, '_')}`,
            name: ruleName,
            priority: salience,
            condition: condExpr,
            actions,
        };
        rules.push(rule);
    }
    if (rules.length === 0) {
        warnings.push('未解析到任何 rule(检查 DRL 语法或是否在支持子集内,schema=' + schemaName + ')');
    }
    return {
        ruleSet: {
            id: `rs_${schemaName.toLowerCase()}_drl_imported`,
            type: 'RuleSet',
            name: '导入自 DRL',
            hitPolicy: 'FIRST_MATCH',
            rules,
        },
        warnings,
    };
}

function buildCondition(whenBlock: string, warnings: string[], idx: number): string | null {
    const conds: string[] = [];
    let m: RegExpExecArray | null;
    CONDS_RE.lastIndex = 0;
    while ((m = CONDS_RE.exec(whenBlock)) !== null) {
        conds.push(`${m[1]} ${m[2]} ${m[3]}`);
    }
    // 探测不支持的 ||
    if (/\|\|/.test(whenBlock)) {
        warnings.push(`rule[${idx}]: 条件含 '||' (OR),本适配器不支持,降级为 AND 全条件`);
    }
    if (conds.length === 0) {
        return null;
    }
    return conds.join(' && ');
}

function buildActions(thenBlock: string, warnings: string[], idx: number) {
    const actions: {type: ActionType; target?: string; value?: string; reason?: string}[] = [];
    const d = thenBlock.match(ACTION_SETDECISION_RE);
    if (d) {
        actions.push({type: 'SET_DECISION', value: unquote(d[1])});
    }
    const r = thenBlock.match(ACTION_REJECT_RE);
    if (r) {
        actions.push({type: 'REJECT', reason: unquote(r[1])});
    }
    let m: RegExpExecArray | null;
    ACTION_SET_RE.lastIndex = 0;
    while ((m = ACTION_SET_RE.exec(thenBlock)) !== null) {
        actions.push({type: 'SET_VARIABLE', target: m[1], value: unquote(m[2])});
    }
    if (/\bmodify\s*\(/.test(thenBlock)) {
        warnings.push(`rule[${idx}]: 'modify($fact){...}' 块未支持,set 语句已尽力提取`);
    }
    return actions;
}

function unquote(s: string): string {
    if (s.startsWith('"') && s.endsWith('"')) {
        return s.slice(1, -1).replace(/\\"/g, '"').replace(/\\\\/g, '\\');
    }
    return s;
}