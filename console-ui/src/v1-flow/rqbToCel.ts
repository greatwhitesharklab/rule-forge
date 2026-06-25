/**
 * React Query Builder 规则树 → CEL 字符串(W3-4 可视化条件编辑)。
 *
 * <p>RQB 的 formatQuery 不原生支持 CEL,这里自己写递归 codegen。
 * 一向:RQB 编辑 → 生成 CEL(CEL 是 source of truth,存进 RuleAsset)。
 * CEL→RQB 反向解析需 CEL AST(后端 cel-java 有,前端无),MVP 不做;
 * 切到可视化模式若已有复杂 CEL,RQB 从空开始(简单 field op value 可手填)。
 *
 * <p>支持的操作符(RQB → CEL):
 * = → ==  != → !=  < ≤ > ≥ → 同  beginsWith/endsWith/contains → matches()
 * null/notNull → x == null / x != null  in/notIn → x in [...] / !(x in [...])
 */
export interface RqbRule {
    field?: string;
    operator?: string;
    value?: unknown;
}
export interface RqbGroup {
    combinator?: 'and' | 'or';
    rules?: (RqbRule | RqbGroup)[];
    not?: boolean;
}

function isGroup(r: RqbRule | RqbGroup): r is RqbGroup {
    return 'rules' in r;
}

const CEL_VALUE_STRING = /^(true|false|null|-?\d+(\.\d+)?)$/;

/** 字面量 → CEL token:string 加引号,数字/布尔/null 原样,其他加引号。 */
function celValue(value: unknown): string {
    if (value === null || value === undefined || value === '') return 'null';
    const s = String(value);
    if (CEL_VALUE_STRING.test(s)) return s; // 数字/布尔/null 原样
    return `'${s.replace(/'/g, "\\'")}'`; // 字符串加引号
}

const OP_MAP: Record<string, (f: string, v: string) => string> = {
    '=': (f, v) => `${f} == ${v}`,
    '!=': (f, v) => `${f} != ${v}`,
    '<': (f, v) => `${f} < ${v}`,
    '<=': (f, v) => `${f} <= ${v}`,
    '>': (f, v) => `${f} > ${v}`,
    '>=': (f, v) => `${f} >= ${v}`,
    beginsWith: (f, v) => `${f}.startsWith(${v})`,
    endsWith: (f, v) => `${f}.endsWith(${v})`,
    contains: (f, v) => `${f}.matches(${v})`,
    doesNotContain: (f, v) => `!${f}.matches(${v})`,
    null: (f) => `${f} == null`,
    notNull: (f) => `${f} != null`,
    in: (f, v) => `${v} in ${f}`,
    notIn: (f, v) => `!(${v} in ${f})`,
};

/** RQB 规则树 → CEL 表达式。 */
export function rqbToCel(query: RqbGroup | null | undefined): string {
    if (!query || !query.rules || query.rules.length === 0) return '';
    const parts = query.rules.map((r) => {
        if (isGroup(r)) return `(${rqbToCel(r)})`;
        const field = r.field || '?';
        const op = r.operator || '=';
        // in/notIn 的 value 是预格式化 CEL token(列表/已引号),原样用,不 celValue
        const v = (op === 'in' || op === 'notIn') ? String(r.value ?? '') : celValue(r.value);
        const fn = OP_MAP[op];
        if (!fn) return `${field} == ${celValue(r.value)}`;
        return fn(field, v);
    }).filter((p) => p && !p.includes('(undefined)'));
    if (parts.length === 0) return '';
    const combinator = query.combinator === 'or' ? ' || ' : ' && ';
    const joined = parts.join(combinator);
    return query.not ? `!(${joined})` : joined;
}
