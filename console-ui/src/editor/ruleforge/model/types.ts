/**
 * Pure data model for the ruleforge (规则集 / ruleset) editor.
 *
 * This module is intentionally framework-free: no React, no DOM, no jquery.
 * It defines the TypeScript types that the React rewrite (react-flow canvas)
 * uses as its single source of truth, and that serialize.ts / parse.ts
 * round-trip to/from the legacy `<rule-set>` XML.
 *
 * The model is a tagged-union replacement for the original imperative DOM
 * classes (Join / NamedJoin / Condition / NamedCondition / ActionType ...).
 * Each discriminated `kind` maps to exactly one XML element kind:
 *
 *   ConditionNode.junction  →  <and> / <or>
 *   ConditionNode.named     →  <named-atom>
 *   ConditionNode.atom      →  <atom>
 *
 *   Action.kind             →  <console-print> / <var-assign> /
 *                              <execute-method> / <execute-function>
 */

/** Logical junction (并且 / 或者). */
export type JunctionType = 'and' | 'or';

/** Left-hand side arithmetic operators (链式 simple-arith). */
export type ArithOp = 'Add' | 'Sub' | 'Mul' | 'Div' | 'Mod';

/**
 * Recursive condition tree node. The root of `Rule.if` is always a junction.
 */
export type ConditionNode =
  | { kind: 'junction'; type: JunctionType; children: ConditionNode[] }
  | {
      kind: 'named';
      type: JunctionType;
      referenceName: string;
      varCategory: string;
      items: NamedCriteria[];
    }
  | { kind: 'atom'; op: string; left: LeftValue; right?: ValueExpr };

/**
 * A single criterion inside a <named-atom>. Mirrors NamedCondition.toXml
 * (`op` / `var` / `var-label` / `datatype` + optional `<value>`).
 */
export interface NamedCriteria {
  op: string;
  var: string;
  varLabel: string;
  datatype: string;
  /** Omitted when op is Null / NotNull. */
  right?: ValueExpr;
}

/**
 * The `<left>` element of an `<atom>`.
 *
 * `type` selects which attributes are populated:
 *   - variable       → var-category / var / var-label / datatype
 *   - parameter      → var-category / var / var-label / datatype (var-category = "参数")
 *   - method         → bean-name / bean-label / method-name / method-label + <parameter>
 *   - commonfunction → function-name / function-label + <function-parameter>
 */
export interface LeftValue {
  type: 'variable' | 'parameter' | 'method' | 'commonfunction';
  varCategory?: string;
  var?: string;
  varLabel?: string;
  datatype?: string;
  beanName?: string;
  beanLabel?: string;
  methodName?: string;
  methodLabel?: string;
  parameters?: MethodParam[];
  functionLabel?: string;
  functionName?: string;
  functionParameter?: FunctionParam;
  /** Optional chained simple-arith applied to the left value. */
  arithmetic?: SimpleArith;
}

/**
 * The `<value>` element — the right-hand side of an atom, a named criterion,
 * an action argument, a method/function parameter value, or a paren payload.
 *
 * `type` selects which attributes are populated (see serialize.ts for the
 * full attribute table).
 */
export interface ValueExpr {
  type:
    | 'Input'
    | 'Variable'
    | 'VariableCategory'
    | 'Constant'
    | 'Parameter'
    | 'Method'
    | 'CommonFunction'
    | 'NamedReference';
  /** Input raw text (NOT XML-escaped in the model; escaping happens at serialize time). */
  content?: string;
  varCategory?: string;
  var?: string;
  varLabel?: string;
  datatype?: string;
  constCategory?: string;
  const?: string;
  constLabel?: string;
  beanName?: string;
  beanLabel?: string;
  methodName?: string;
  methodLabel?: string;
  functionLabel?: string;
  functionName?: string;
  referenceName?: string;
  propertyName?: string;
  propertyLabel?: string;
  /** Only for Method values. */
  parameters?: MethodParam[];
  /** Only for CommonFunction values. */
  functionParameter?: FunctionParam;
  /** Optional trailing complex-arith chain. */
  arithmetic?: ComplexArith;
}

/**
 * Simple arithmetic applied to a left value (a single linked chain).
 * Each node is one `<simple-arith type="..." value="...">`.
 */
export interface SimpleArith {
  type: ArithOp;
  value: string;
  next?: SimpleArith;
}

/**
 * Complex arithmetic applied to a `<value>` (a single linked node, but each
 * node can carry either a bare `<value>` or a `<paren>…</paren>` payload).
 */
export interface ComplexArith {
  type: ArithOp;
  value?: ValueExpr;
  paren?: { value: ValueExpr; arithmetic?: ComplexArith };
}

/** A `<parameter>` inside a Method value or an execute-method action. */
export interface MethodParam {
  name: string;
  type: string;
  value: ValueExpr;
}

/** A `<function-parameter>` inside a CommonFunction value or execute-function action. */
export interface FunctionParam {
  name?: string;
  propertyName?: string;
  propertyLabel?: string;
  value: ValueExpr;
}

/**
 * A single action under <then> / <else>. Discriminated by `kind`:
 *   - console-print    → <console-print><value/></console-print>
 *   - var-assign       → <var-assign var var-label var-category type><value/></var-assign>
 *   - execute-method   → <execute-method bean-name bean-label method-name method-label>…<parameter/></…>
 *   - execute-function → <execute-function function-name function-label>…<function-parameter/></…>
 */
export type Action =
  | { kind: 'console-print'; value: ValueExpr }
  | {
      kind: 'var-assign';
      var: string;
      varLabel: string;
      varCategory: string;
      valueType: 'variable' | 'parameter' | 'reference';
      value: ValueExpr;
    }
  | {
      kind: 'execute-method';
      bean: string;
      beanLabel: string;
      methodName: string;
      methodLabel: string;
      parameters: MethodParam[];
    }
  | {
      kind: 'execute-function';
      functionLabel: string;
      functionName: string;
      parameter?: FunctionParam;
    };

/**
 * A `<rule>` attribute (other than name).
 *
 * `name` is the XML attribute name as it appears on `<rule>`:
 * salience / loop / effective-date / expires-date / enabled / debug /
 * activation-group / agenda-group / auto-focus / ruleflow-group.
 *
 * `value` is either a string or a boolean. Booleans serialize as "true"/"false".
 */
export interface RuleProperty {
  name: string;
  value: string | boolean;
}

/** A single rule. */
export interface Rule {
  name: string;
  properties: RuleProperty[];
  remark: string;
  if: ConditionNode;
  then: Action[];
  else: Action[];
}

/** The full `<rule-set>` document state. */
export interface Ruleset {
  parameterLibraries: string[];
  variableLibraries: string[];
  constantLibraries: string[];
  actionLibraries: string[];
  remark: string;
  rules: Rule[];
}
