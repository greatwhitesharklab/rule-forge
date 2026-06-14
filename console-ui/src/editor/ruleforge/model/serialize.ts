/**
 * serialize.ts — Ruleset state → legacy `<rule-set>` XML string.
 *
 * Pure function. No React / DOM / jquery. The output is byte-for-byte
 * compatible with the original jquery editor's `toXml()` chain
 * (RuleFactory → Rule → Join / NamedJoin / Condition / NamedCondition /
 * InputType → value widgets), so the React rewrite can persist into the
 * exact same storage format the backend already parses.
 *
 * XML escaping rules (matching SimpleValueWidget.getValue):
 *   &  →  &amp;
 *   <  →  &lt;
 *   >  →  &gt;
 *   '  →  &apos;
 *   "  →  &quot;
 *
 * The model stores RAW (unescaped) strings; this module is the only place
 * that escapes — content fields, attribute values, and remark CDATA payload
 * are all escaped here.
 */

import type {
  Action,
  ComplexArith,
  ConditionNode,
  FunctionParam,
  LeftValue,
  MethodParam,
  NamedCriteria,
  Rule,
  RuleProperty,
  Ruleset,
  SimpleArith,
  ValueExpr,
} from './types';

/** XML-escape a string for use in an attribute value or text content. */
function esc(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/'/g, '&apos;')
    .replace(/"/g, '&quot;');
}

/** XML-escape only the CDATA terminator (`]]>`) so CDATA stays well-formed. */
function escCdata(s: string): string {
  return s.replace(/]]>/g, ']]&gt;');
}

// ---------------------------------------------------------------------------
// Ruleset + Rule
// ---------------------------------------------------------------------------

/** Serialize the full ruleset document (including the XML declaration). */
export function serializeRuleset(rs: Ruleset): string {
  let xml = '<?xml version="1.0" encoding="UTF-8"?>';
  xml += '<rule-set>';
  for (const p of rs.parameterLibraries) {
    xml += '<import-parameter-library path="' + esc(p) + '"/>';
  }
  for (const v of rs.variableLibraries) {
    xml += '<import-variable-library path="' + esc(v) + '"/>';
  }
  for (const c of rs.constantLibraries) {
    xml += '<import-constant-library path="' + esc(c) + '"/>';
  }
  for (const a of rs.actionLibraries) {
    xml += '<import-action-library path="' + esc(a) + '"/>';
  }
  xml += serializeRemark(rs.remark);
  for (const rule of rs.rules) {
    xml += serializeRule(rule);
  }
  xml += '</rule-set>';
  return xml;
}

/** `<remark><![CDATA[...]]></remark>` (empty remark still emits an empty CDATA). */
function serializeRemark(remark: string): string {
  return '<remark><![CDATA[' + escCdata(remark) + ']]></remark>';
}

/** `<rule name="…" … attr …>…</rule>`. */
function serializeRule(rule: Rule): string {
  let xml = '<rule name="' + esc(rule.name) + '"';
  for (const prop of rule.properties) {
    xml += ' ' + serializeProperty(prop);
  }
  xml += '>';
  xml += serializeRemark(rule.remark);
  xml += '<if>';
  xml += serializeCondition(rule.if);
  xml += '</if>';
  xml += '<then>';
  for (const a of rule.then) {
    xml += serializeAction(a);
  }
  xml += '</then>';
  xml += '<else>';
  for (const a of rule.else) {
    xml += serializeAction(a);
  }
  xml += '</else>';
  xml += '</rule>';
  return xml;
}

/**
 * Serialize a rule attribute property as `name="value"` or `name="true"/"false"`.
 * (Matches RuleProperty.toXml.)
 */
function serializeProperty(prop: RuleProperty): string {
  if (typeof prop.value === 'boolean') {
    return prop.name + '="' + (prop.value ? 'true' : 'false') + '"';
  }
  return prop.name + '="' + esc(prop.value) + '"';
}

// ---------------------------------------------------------------------------
// Condition tree
// ---------------------------------------------------------------------------

function serializeCondition(node: ConditionNode): string {
  switch (node.kind) {
    case 'junction': {
      let xml = '<' + node.type + '>';
      for (const child of node.children) {
        xml += serializeCondition(child);
      }
      xml += '</' + node.type + '>';
      return xml;
    }
    case 'named':
      return serializeNamedAtom(node);
    case 'atom':
      return serializeAtom(node);
  }
}

/**
 * `<and>`/`<or>` children recursion for nested junctions is handled by
 * serializeCondition above. Named atoms emit their own element.
 */
function serializeNamedAtom(node: Extract<ConditionNode, { kind: 'named' }>): string {
  let xml =
    '<named-atom junction-type="' +
    esc(node.type) +
    '" reference-name="' +
    esc(node.referenceName) +
    '" var-category="' +
    esc(node.varCategory) +
    '">';
  for (const item of node.items) {
    xml += serializeNamedCriteria(item);
  }
  xml += '</named-atom>';
  return xml;
}

/**
 * `<named-criteria op var var-label datatype>…<value/>…</named-criteria>`.
 */
function serializeNamedCriteria(c: NamedCriteria): string {
  let xml =
    '<named-criteria op="' +
    esc(c.op) +
    '" var="' +
    esc(c.var) +
    '" var-label="' +
    esc(c.varLabel) +
    '" datatype="' +
    esc(c.datatype) +
    '">';
  if (c.right) {
    xml += serializeValue(c.right);
  }
  xml += '</named-criteria>';
  return xml;
}

/**
 * `<atom op="…"><left …/>…<value/></atom>`.
 *
 * `<value>` is omitted entirely when op is Null / NotNull (the original
 * ComparisonOperator marks those as `noInput`).
 */
function serializeAtom(node: Extract<ConditionNode, { kind: 'atom' }>): string {
  const noValue = node.op === 'Null' || node.op === 'NotNull';
  let xml = '<atom op="' + esc(node.op) + '">';
  xml += serializeLeft(node.left);
  if (!noValue && node.right) {
    xml += serializeValue(node.right);
  }
  xml += '</atom>';
  return xml;
}

/**
 * `<left … type="…">…<simple-arith/></left>`.
 *
 * For method/commonfunction lefts the parameters/parameter children go inside
 * the `<left>` element, before any `<simple-arith>`.
 *
 * Exported because the decisiontree editor reuses the exact same `<left>`
 * wire format inside <variable-tree-node> — sharing this function keeps the
 * two editors byte-for-byte consistent.
 */
export function serializeLeft(left: LeftValue): string {
  let attrs = '';
  switch (left.type) {
    case 'variable':
    case 'parameter':
      if (left.varCategory !== undefined) attrs += ' var-category="' + esc(left.varCategory) + '"';
      if (left.var !== undefined) attrs += ' var="' + esc(left.var) + '"';
      if (left.varLabel !== undefined) attrs += ' var-label="' + esc(left.varLabel) + '"';
      if (left.datatype !== undefined) attrs += ' datatype="' + esc(left.datatype) + '"';
      break;
    case 'method':
      if (left.beanName !== undefined) attrs += ' bean-name="' + esc(left.beanName) + '"';
      if (left.beanLabel !== undefined) attrs += ' bean-label="' + esc(left.beanLabel) + '"';
      if (left.methodName !== undefined) attrs += ' method-name="' + esc(left.methodName) + '"';
      if (left.methodLabel !== undefined) attrs += ' method-label="' + esc(left.methodLabel) + '"';
      break;
    case 'commonfunction':
      if (left.functionName !== undefined) attrs += ' function-name="' + esc(left.functionName) + '"';
      if (left.functionLabel !== undefined) attrs += ' function-label="' + esc(left.functionLabel) + '"';
      break;
  }
  attrs += ' type="' + left.type + '"';

  let inner = '';
  if (left.type === 'method' && left.parameters) {
    for (const p of left.parameters) {
      inner += serializeMethodParam(p);
    }
  } else if (left.type === 'commonfunction' && left.functionParameter) {
    inner += serializeFunctionParam(left.functionParameter);
  }
  if (left.arithmetic) {
    inner += serializeSimpleArith(left.arithmetic);
  }
  return '<left' + attrs + '>' + inner + '</left>';
}

/**
 * `<simple-arith type value>…<simple-arith/></simple-arith>` (linked chain).
 */
function serializeSimpleArith(arith: SimpleArith): string {
  let xml = '<simple-arith type="' + arith.type + '" value="' + esc(arith.value) + '">';
  if (arith.next) {
    xml += serializeSimpleArith(arith.next);
  }
  xml += '</simple-arith>';
  return xml;
}

// ---------------------------------------------------------------------------
// Value expression
// ---------------------------------------------------------------------------

/**
 * `<value … type="…">…children…</value>`.
 *
 * Attribute table (mirrors InputType.toXml + the value widgets):
 *   Input          → content
 *   Variable       → var-category / var / var-label / datatype
 *   VariableCategory → var-category
 *   Constant       → const-category / const / const-label
 *   Parameter      → var-category="参数" / var / var-label / datatype
 *   Method         → bean-name / bean-label / method-name / method-label   (+ <parameter> children)
 *   CommonFunction → function-name / function-label                        (+ <function-parameter> child)
 *   NamedReference → reference-name / property-name / property-label / datatype
 *
 * Children (after attributes): optional `<complex-arith>`, then for Method the
 * `<parameter>` list, for CommonFunction the `<function-parameter>`. (Order
 * matches InputType.toXml: arithmetic first, then method/function children.)
 */
export function serializeValue(v: ValueExpr): string {
  let attrs = '';
  switch (v.type) {
    case 'Input':
      if (v.content !== undefined) attrs += ' content="' + esc(v.content) + '"';
      break;
    case 'Variable':
    case 'VariableCategory':
      if (v.varCategory !== undefined) attrs += ' var-category="' + esc(v.varCategory) + '"';
      if (v.type === 'Variable') {
        if (v.var !== undefined) attrs += ' var="' + esc(v.var) + '"';
        if (v.varLabel !== undefined) attrs += ' var-label="' + esc(v.varLabel) + '"';
        if (v.datatype !== undefined) attrs += ' datatype="' + esc(v.datatype) + '"';
      }
      break;
    case 'Parameter':
      attrs += ' var-category="参数"';
      if (v.var !== undefined) attrs += ' var="' + esc(v.var) + '"';
      if (v.varLabel !== undefined) attrs += ' var-label="' + esc(v.varLabel) + '"';
      if (v.datatype !== undefined) attrs += ' datatype="' + esc(v.datatype) + '"';
      break;
    case 'Constant':
      if (v.constCategory !== undefined) attrs += ' const-category="' + esc(v.constCategory) + '"';
      if (v.const !== undefined) attrs += ' const="' + esc(v.const) + '"';
      if (v.constLabel !== undefined) attrs += ' const-label="' + esc(v.constLabel) + '"';
      break;
    case 'Method':
      if (v.beanName !== undefined) attrs += ' bean-name="' + esc(v.beanName) + '"';
      if (v.beanLabel !== undefined) attrs += ' bean-label="' + esc(v.beanLabel) + '"';
      if (v.methodName !== undefined) attrs += ' method-name="' + esc(v.methodName) + '"';
      if (v.methodLabel !== undefined) attrs += ' method-label="' + esc(v.methodLabel) + '"';
      break;
    case 'CommonFunction':
      if (v.functionName !== undefined) attrs += ' function-name="' + esc(v.functionName) + '"';
      if (v.functionLabel !== undefined) attrs += ' function-label="' + esc(v.functionLabel) + '"';
      break;
    case 'NamedReference':
      if (v.referenceName !== undefined) attrs += ' reference-name="' + esc(v.referenceName) + '"';
      if (v.propertyName !== undefined) attrs += ' property-name="' + esc(v.propertyName) + '"';
      if (v.propertyLabel !== undefined) attrs += ' property-label="' + esc(v.propertyLabel) + '"';
      if (v.datatype !== undefined) attrs += ' datatype="' + esc(v.datatype) + '"';
      break;
  }
  attrs += ' type="' + v.type + '"';

  let inner = '';
  if (v.arithmetic) {
    inner += serializeComplexArith(v.arithmetic);
  }
  if (v.type === 'Method' && v.parameters) {
    for (const p of v.parameters) {
      inner += serializeMethodParam(p);
    }
  } else if (v.type === 'CommonFunction' && v.functionParameter) {
    inner += serializeFunctionParam(v.functionParameter);
  }
  return '<value' + attrs + '>' + inner + '</value>';
}

/**
 * `<complex-arith type="…"><value/> | <paren>…</paren></complex-arith>`.
 */
function serializeComplexArith(arith: ComplexArith): string {
  let inner = '';
  if (arith.paren) {
    inner += '<paren>';
    inner += serializeValue(arith.paren.value);
    if (arith.paren.arithmetic) {
      inner += serializeComplexArith(arith.paren.arithmetic);
    }
    inner += '</paren>';
  } else if (arith.value) {
    inner += serializeValue(arith.value);
  }
  return '<complex-arith type="' + arith.type + '">' + inner + '</complex-arith>';
}

/**
 * `<parameter name type><value/></parameter>` (method parameter).
 */
function serializeMethodParam(p: MethodParam): string {
  return (
    '<parameter name="' + esc(p.name) + '" type="' + esc(p.type) + '">' +
    serializeValue(p.value) +
    '</parameter>'
  );
}

/**
 * `<function-parameter name property-name property-label><value/></function-parameter>`.
 * The property-name/property-label attributes are omitted when absent.
 */
function serializeFunctionParam(p: FunctionParam): string {
  let attrs = '';
  if (p.name !== undefined) attrs += ' name="' + esc(p.name) + '"';
  if (p.propertyName !== undefined) attrs += ' property-name="' + esc(p.propertyName) + '"';
  if (p.propertyLabel !== undefined) attrs += ' property-label="' + esc(p.propertyLabel) + '"';
  return '<function-parameter' + attrs + '>' + serializeValue(p.value) + '</function-parameter>';
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

export function serializeAction(action: Action): string {
  switch (action.kind) {
    case 'console-print':
      return '<console-print>' + serializeValue(action.value) + '</console-print>';
    case 'var-assign': {
      let attrs =
        ' var="' + esc(action.var) +
        '" var-label="' + esc(action.varLabel) +
        '" var-category="' + esc(action.varCategory) +
        '" type="' + action.valueType + '"';
      return '<var-assign' + attrs + '>' + serializeValue(action.value) + '</var-assign>';
    }
    case 'execute-method': {
      let attrs =
        ' bean-name="' + esc(action.bean) +
        '" bean-label="' + esc(action.beanLabel) +
        '" method-name="' + esc(action.methodName) +
        '" method-label="' + esc(action.methodLabel) + '"';
      let inner = '';
      for (const p of action.parameters) {
        inner += serializeMethodParam(p);
      }
      return '<execute-method' + attrs + '>' + inner + '</execute-method>';
    }
    case 'execute-function': {
      let attrs =
        ' function-name="' + esc(action.functionName) +
        '" function-label="' + esc(action.functionLabel) + '"';
      let inner = '';
      if (action.parameter) {
        inner += serializeFunctionParam(action.parameter);
      }
      return '<execute-function' + attrs + '>' + inner + '</execute-function>';
    }
  }
}
