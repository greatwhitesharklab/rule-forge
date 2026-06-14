/**
 * Shared constants for the React ruleforge editor.
 *
 * Pure data — no React, no DOM. Consumed by every editor component below.
 * Mirrors the original jquery editor's ComparisonOperator / RuleProperty /
 * InputType label tables so the React rewrite shows the same Chinese labels
 * the existing BA workflow expects.
 *
 * Source of truth (do not edit freely — these must match backend op + attr
 * names byte-for-byte, since serialize.ts writes them straight into XML):
 *   - op values        ← server ComparisonOperator (GreaterThen / Equals / …)
 *   - property names   ← <rule> XML attributes (salience / loop / …)
 *   - value type names ← <value type="…"> attribute (Input / Variable / …)
 */

/** Comparison operator option for the atom `op` dropdown. */
export interface OpOption {
  /** The XML value written into `op="…"`. */
  value: string;
  /** Chinese label shown in the dropdown. */
  label: string;
  /** When true, the right-hand `<value>` is suppressed (Null / NotNull). */
  noInput?: boolean;
}

/**
 * The full operator set. Order is the dropdown order. `noInput` flags the two
 * operators that take no right operand (serialize.ts drops the `<value>`
 * child for them).
 */
export const OPERATOR_OPTIONS: OpOption[] = [
  { value: 'GreaterThen', label: '大于' },
  { value: 'GreaterThenEquals', label: '大于或等于' },
  { value: 'LessThen', label: '小于' },
  { value: 'LessThenEquals', label: '小于或等于' },
  { value: 'Equals', label: '等于' },
  { value: 'NotEquals', label: '不等于' },
  { value: 'EqualsIgnoreCase', label: '等于(不分大小写)' },
  { value: 'NotEqualsIgnoreCase', label: '不等于(不分大小写)' },
  { value: 'Contain', label: '包含' },
  { value: 'NotContain', label: '不包含' },
  { value: 'StartWith', label: '开始于' },
  { value: 'NotStartWith', label: '不开始于' },
  { value: 'EndWith', label: '结束于' },
  { value: 'NotEndWith', label: '不结束于' },
  { value: 'In', label: '在集合' },
  { value: 'NotIn', label: '不在集合' },
  { value: 'Null', label: '为空', noInput: true },
  { value: 'NotNull', label: '不为空', noInput: true },
  { value: 'Match', label: '匹配正则表达式' },
  { value: 'NotMatch', label: '不匹配正则表达式' },
];

/** True when the operator takes no right-hand `<value>`. */
export function opHasNoInput(op: string): boolean {
  return op === 'Null' || op === 'NotNull';
}

/** Look up the Chinese label for an operator value (falls back to the value). */
export function opLabel(op: string): string {
  const found = OPERATOR_OPTIONS.find((o) => o.value === op);
  return found ? found.label : op;
}

// ---------------------------------------------------------------------------
// Left-value types
// ---------------------------------------------------------------------------

/** `<left type="…">` attribute options. */
export const LEFT_TYPE_OPTIONS: { value: 'variable' | 'parameter' | 'method' | 'commonfunction'; label: string }[] = [
  { value: 'variable', label: '变量' },
  { value: 'parameter', label: '参数' },
  { value: 'method', label: '方法' },
  { value: 'commonfunction', label: '通用函数' },
];

// ---------------------------------------------------------------------------
// Value expression types
// ---------------------------------------------------------------------------

/** `<value type="…">` attribute options. */
export const VALUE_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'Input', label: '输入值' },
  { value: 'Variable', label: '变量' },
  { value: 'Parameter', label: '参数' },
  { value: 'Constant', label: '常量' },
  { value: 'VariableCategory', label: '变量分类' },
  { value: 'Method', label: '方法' },
  { value: 'CommonFunction', label: '通用函数' },
  { value: 'NamedReference', label: '命名引用' },
];

// ---------------------------------------------------------------------------
// Junction types
// ---------------------------------------------------------------------------

export const JUNCTION_TYPE_OPTIONS: { value: 'and' | 'or'; label: string }[] = [
  { value: 'and', label: '并且' },
  { value: 'or', label: '或者' },
];

// ---------------------------------------------------------------------------
// Rule properties (the 9 `<rule>` attributes other than name)
// ---------------------------------------------------------------------------

/** Editor widget kind for a rule property. */
export type PropEditorType = 'text' | 'date' | 'boolean';

/** A rule attribute spec — name, label, default, and editor widget. */
export interface RulePropertyDef {
  /** XML attribute name on `<rule>`. */
  name: string;
  /** Chinese label shown in the property selector + display. */
  label: string;
  /** Default value used when the property is added. */
  defaultValue: string | boolean;
  /** Which editor widget to render. */
  editorType: PropEditorType;
}

/**
 * The 9 supported `<rule>` properties. Order is the "add property" dropdown
 * order. Mirrors RuleProperty.js (jquery) exactly so labels match the BA UX.
 */
export const RULE_PROPERTY_DEFS: RulePropertyDef[] = [
  { name: 'salience', label: '优先级', defaultValue: '', editorType: 'text' },
  { name: 'loop', label: '允许循环触发', defaultValue: false, editorType: 'boolean' },
  { name: 'effective-date', label: '生效日期', defaultValue: '', editorType: 'date' },
  { name: 'expires-date', label: '失效日期', defaultValue: '', editorType: 'date' },
  { name: 'enabled', label: '是否启用', defaultValue: true, editorType: 'boolean' },
  { name: 'debug', label: '允许调试信息输出', defaultValue: false, editorType: 'boolean' },
  { name: 'activation-group', label: '互斥组', defaultValue: '', editorType: 'text' },
  { name: 'agenda-group', label: '执行组', defaultValue: '', editorType: 'text' },
  { name: 'auto-focus', label: '自动获取焦点', defaultValue: false, editorType: 'boolean' },
  { name: 'ruleflow-group', label: '规则流组', defaultValue: '', editorType: 'text' },
];

/** Look up the def for a property by XML name (undefined if unknown). */
export function findPropertyDef(name: string): RulePropertyDef | undefined {
  return RULE_PROPERTY_DEFS.find((d) => d.name === name);
}

/** The Chinese label for a property name (falls back to the raw name). */
export function propertyLabel(name: string): string {
  return findPropertyDef(name)?.label ?? name;
}

// ---------------------------------------------------------------------------
// Action kinds
// ---------------------------------------------------------------------------

export const ACTION_KIND_OPTIONS: { value: string; label: string }[] = [
  { value: 'console-print', label: '打印输出' },
  { value: 'var-assign', label: '变量赋值' },
  { value: 'execute-method', label: '执行方法' },
  { value: 'execute-function', label: '执行通用函数' },
];

/** var-assign `<var-assign type="…">` valueType options. */
export const VAR_ASSIGN_TYPE_OPTIONS: { value: 'variable' | 'parameter' | 'reference'; label: string }[] = [
  { value: 'variable', label: '变量' },
  { value: 'parameter', label: '参数' },
  { value: 'reference', label: '命名引用' },
];

/** Arithmetic operator options for simple-arith / complex-arith chains. */
export const ARITH_OP_OPTIONS: { value: 'Add' | 'Sub' | 'Mul' | 'Div' | 'Mod'; label: string }[] = [
  { value: 'Add', label: '+ 加' },
  { value: 'Sub', label: '- 减' },
  { value: 'Mul', label: '* 乘' },
  { value: 'Div', label: '/ 除' },
  { value: 'Mod', label: '% 取模' },
];
