/**
 * parse.ts — legacy `<rule-set>` XML string → Ruleset state.
 *
 * Pure function. Uses DOMParser (jsdom in tests, the browser in the editor).
 * The inverse of serialize.ts: `parse(serialize(state))` deep-equals `state`.
 *
 * All text returned to the model is RAW (XML-unescaped). The DOM already
 * resolves entities (`&amp;` → `&`) for both attribute values and text
 * content, so we just read them back verbatim.
 */

import type {
  Action,
  ComplexArith,
  ConditionNode,
  FunctionParam,
  JunctionType,
  LeftValue,
  MethodParam,
  NamedCriteria,
  Rule,
  RuleProperty,
  Ruleset,
  SimpleArith,
  ValueExpr,
} from './types';

const XML_MIME = 'text/xml';

/** Parse a full ruleset XML string into a Ruleset. */
export function parseRuleset(xml: string): Ruleset {
  const doc = new DOMParser().parseFromString(xml, XML_MIME);
  assertNoParserError(doc);
  const root = doc.documentElement;
  if (!root || root.tagName !== 'rule-set') {
    throw new Error('parseRuleset: root element is not <rule-set>');
  }
  const parameterLibraries: string[] = [];
  const variableLibraries: string[] = [];
  const constantLibraries: string[] = [];
  const actionLibraries: string[] = [];
  let remark = '';
  const rules: Rule[] = [];

  for (const child of Array.from(root.children)) {
    switch (child.tagName) {
      case 'import-parameter-library':
        parameterLibraries.push(child.getAttribute('path') ?? '');
        break;
      case 'import-variable-library':
        variableLibraries.push(child.getAttribute('path') ?? '');
        break;
      case 'import-constant-library':
        constantLibraries.push(child.getAttribute('path') ?? '');
        break;
      case 'import-action-library':
        actionLibraries.push(child.getAttribute('path') ?? '');
        break;
      case 'remark':
        remark = readCdata(child);
        break;
      case 'rule':
        rules.push(parseRule(child));
        break;
      default:
        // Unknown top-level element — ignore (forward-compat).
        break;
    }
  }

  return { parameterLibraries, variableLibraries, constantLibraries, actionLibraries, remark, rules };
}

/** DOMParser can surface a `<parsererror>` element when the XML is malformed. */
function assertNoParserError(doc: Document): void {
  const err = doc.getElementsByTagName('parsererror')[0];
  if (err) {
    throw new Error('parseRuleset: malformed XML — ' + (err.textContent ?? '').slice(0, 200));
  }
}

/** Read the text inside a `<remark><![CDATA[...]]></remark>` (raw). */
function readCdata(el: Element): string {
  // textContent already unwraps CDATA sections into plain text.
  return el.textContent ?? '';
}

// ---------------------------------------------------------------------------
// Rule
// ---------------------------------------------------------------------------

function parseRule(el: Element): Rule {
  const name = el.getAttribute('name') ?? '';
  const properties: RuleProperty[] = [];
  const BOOLEAN_PROPS = new Set(['loop', 'enabled', 'debug', 'auto-focus']);
  for (const attr of Array.from(el.attributes)) {
    if (attr.name === 'name') continue;
    if (BOOLEAN_PROPS.has(attr.name)) {
      properties.push({ name: attr.name, value: attr.value === 'true' });
    } else {
      properties.push({ name: attr.name, value: attr.value });
    }
  }
  let remark = '';
  let ifNode: ConditionNode = { kind: 'junction', type: 'and', children: [] };
  const thenActions: Action[] = [];
  const elseActions: Action[] = [];

  for (const child of Array.from(el.children)) {
    switch (child.tagName) {
      case 'remark':
        remark = readCdata(child);
        break;
      case 'if': {
        const only = firstElementChild(child);
        ifNode = only ? parseCondition(only) : { kind: 'junction', type: 'and', children: [] };
        break;
      }
      case 'then':
        for (const a of Array.from(child.children)) thenActions.push(parseAction(a));
        break;
      case 'else':
        for (const a of Array.from(child.children)) elseActions.push(parseAction(a));
        break;
      default:
        break;
    }
  }

  return { name, properties, remark, if: ifNode, then: thenActions, else: elseActions };
}

// ---------------------------------------------------------------------------
// Condition tree
// ---------------------------------------------------------------------------

function parseCondition(el: Element): ConditionNode {
  switch (el.tagName) {
    case 'and':
    case 'or':
      return parseJunction(el);
    case 'named-atom':
      return parseNamedAtom(el);
    case 'atom':
      return parseAtom(el);
    default:
      throw new Error('parseCondition: unexpected element <' + el.tagName + '>');
  }
}

function parseJunction(el: Element): ConditionNode {
  const type = el.tagName as JunctionType;
  const children: ConditionNode[] = [];
  for (const child of Array.from(el.children)) {
    children.push(parseCondition(child));
  }
  return { kind: 'junction', type, children };
}

function parseNamedAtom(el: Element): Extract<ConditionNode, { kind: 'named' }> {
  const type = (el.getAttribute('junction-type') as JunctionType) ?? 'and';
  const referenceName = el.getAttribute('reference-name') ?? '';
  const varCategory = el.getAttribute('var-category') ?? '';
  const items: NamedCriteria[] = [];
  for (const child of Array.from(el.children)) {
    if (child.tagName === 'named-criteria') {
      items.push(parseNamedCriteria(child));
    }
  }
  return { kind: 'named', type, referenceName, varCategory, items };
}

function parseNamedCriteria(el: Element): NamedCriteria {
  const op = el.getAttribute('op') ?? '';
  const variable = el.getAttribute('var') ?? '';
  const varLabel = el.getAttribute('var-label') ?? '';
  const datatype = el.getAttribute('datatype') ?? '';
  const valueEl = firstElementChild(el);
  const criteria: NamedCriteria = { op, var: variable, varLabel, datatype };
  if (valueEl && valueEl.tagName === 'value') {
    criteria.right = parseValue(valueEl);
  }
  return criteria;
}

function parseAtom(el: Element): Extract<ConditionNode, { kind: 'atom' }> {
  const op = el.getAttribute('op') ?? '';
  let left: LeftValue | undefined;
  let right: ValueExpr | undefined;
  for (const child of Array.from(el.children)) {
    if (child.tagName === 'left') {
      left = parseLeft(child);
    } else if (child.tagName === 'value') {
      right = parseValue(child);
    }
  }
  if (!left) {
    throw new Error('parseAtom: <atom> is missing a <left> child');
  }
  const atom: Extract<ConditionNode, { kind: 'atom' }> = { kind: 'atom', op, left };
  if (right) {
    atom.right = right;
  }
  return atom;
}

export function parseLeft(el: Element): LeftValue {
  const type = (el.getAttribute('type') ?? 'variable') as LeftValue['type'];
  const left: LeftValue = { type };
  switch (type) {
    case 'variable':
    case 'parameter': {
      const vc = el.getAttribute('var-category');
      if (vc !== null) left.varCategory = vc;
      const v = el.getAttribute('var');
      if (v !== null) left.var = v;
      const vl = el.getAttribute('var-label');
      if (vl !== null) left.varLabel = vl;
      const dt = el.getAttribute('datatype');
      if (dt !== null) left.datatype = dt;
      break;
    }
    case 'method': {
      const bn = el.getAttribute('bean-name');
      if (bn !== null) left.beanName = bn;
      const bl = el.getAttribute('bean-label');
      if (bl !== null) left.beanLabel = bl;
      const mn = el.getAttribute('method-name');
      if (mn !== null) left.methodName = mn;
      const ml = el.getAttribute('method-label');
      if (ml !== null) left.methodLabel = ml;
      break;
    }
    case 'commonfunction': {
      const fn = el.getAttribute('function-name');
      if (fn !== null) left.functionName = fn;
      const fl = el.getAttribute('function-label');
      if (fl !== null) left.functionLabel = fl;
      break;
    }
  }

  const parameters: MethodParam[] = [];
  let arithmetic: SimpleArith | undefined;
  for (const child of Array.from(el.children)) {
    if (child.tagName === 'parameter') {
      parameters.push(parseMethodParam(child));
    } else if (child.tagName === 'function-parameter') {
      left.functionParameter = parseFunctionParam(child);
    } else if (child.tagName === 'simple-arith') {
      arithmetic = parseSimpleArith(child);
    }
  }
  if (parameters.length > 0) left.parameters = parameters;
  if (arithmetic) left.arithmetic = arithmetic;
  return left;
}

function parseSimpleArith(el: Element): SimpleArith {
  const type = (el.getAttribute('type') ?? 'Add') as SimpleArith['type'];
  const value = el.getAttribute('value') ?? '';
  const arith: SimpleArith = { type, value };
  const child = firstElementChild(el);
  if (child && child.tagName === 'simple-arith') {
    arith.next = parseSimpleArith(child);
  }
  return arith;
}

// ---------------------------------------------------------------------------
// Value expression
// ---------------------------------------------------------------------------

export function parseValue(el: Element): ValueExpr {
  const type = (el.getAttribute('type') ?? 'Input') as ValueExpr['type'];
  const v: ValueExpr = { type };
  switch (type) {
    case 'Input':
      v.content = el.getAttribute('content') ?? '';
      break;
    case 'Variable':
    case 'VariableCategory': {
      const vc = el.getAttribute('var-category');
      if (vc !== null) v.varCategory = vc;
      if (type === 'Variable') {
        const vn = el.getAttribute('var');
        if (vn !== null) v.var = vn;
        const vl = el.getAttribute('var-label');
        if (vl !== null) v.varLabel = vl;
        const dt = el.getAttribute('datatype');
        if (dt !== null) v.datatype = dt;
      }
      break;
    }
    case 'Parameter': {
      v.varCategory = '参数';
      const vn = el.getAttribute('var');
      if (vn !== null) v.var = vn;
      const vl = el.getAttribute('var-label');
      if (vl !== null) v.varLabel = vl;
      const dt = el.getAttribute('datatype');
      if (dt !== null) v.datatype = dt;
      break;
    }
    case 'Constant': {
      const cc = el.getAttribute('const-category');
      if (cc !== null) v.constCategory = cc;
      const c = el.getAttribute('const');
      if (c !== null) v.const = c;
      const cl = el.getAttribute('const-label');
      if (cl !== null) v.constLabel = cl;
      break;
    }
    case 'Method': {
      const bn = el.getAttribute('bean-name');
      if (bn !== null) v.beanName = bn;
      const bl = el.getAttribute('bean-label');
      if (bl !== null) v.beanLabel = bl;
      const mn = el.getAttribute('method-name');
      if (mn !== null) v.methodName = mn;
      const ml = el.getAttribute('method-label');
      if (ml !== null) v.methodLabel = ml;
      break;
    }
    case 'CommonFunction': {
      const fn = el.getAttribute('function-name');
      if (fn !== null) v.functionName = fn;
      const fl = el.getAttribute('function-label');
      if (fl !== null) v.functionLabel = fl;
      break;
    }
    case 'NamedReference': {
      const rn = el.getAttribute('reference-name');
      if (rn !== null) v.referenceName = rn;
      const pn = el.getAttribute('property-name');
      if (pn !== null) v.propertyName = pn;
      const pl = el.getAttribute('property-label');
      if (pl !== null) v.propertyLabel = pl;
      const dt = el.getAttribute('datatype');
      if (dt !== null) v.datatype = dt;
      break;
    }
  }

  const parameters: MethodParam[] = [];
  for (const child of Array.from(el.children)) {
    if (child.tagName === 'complex-arith') {
      v.arithmetic = parseComplexArith(child);
    } else if (child.tagName === 'parameter') {
      parameters.push(parseMethodParam(child));
    } else if (child.tagName === 'function-parameter') {
      v.functionParameter = parseFunctionParam(child);
    }
  }
  if (parameters.length > 0) v.parameters = parameters;
  return v;
}

function parseComplexArith(el: Element): ComplexArith {
  const type = (el.getAttribute('type') ?? 'Add') as ComplexArith['type'];
  const arith: ComplexArith = { type };
  for (const child of Array.from(el.children)) {
    if (child.tagName === 'value') {
      arith.value = parseValue(child);
    } else if (child.tagName === 'paren') {
      // A <paren> holds a <value> and an optional trailing <complex-arith>.
      let parenValue: ValueExpr | undefined;
      let parenArith: ComplexArith | undefined;
      for (const inner of Array.from(child.children)) {
        if (inner.tagName === 'value') {
          parenValue = parseValue(inner);
        } else if (inner.tagName === 'complex-arith') {
          parenArith = parseComplexArith(inner);
        }
      }
      const paren: ComplexArith['paren'] = {
        value: parenValue ?? { type: 'Input', content: '' },
      };
      if (parenArith) paren.arithmetic = parenArith;
      arith.paren = paren;
    }
  }
  return arith;
}

function parseMethodParam(el: Element): MethodParam {
  const name = el.getAttribute('name') ?? '';
  const type = el.getAttribute('type') ?? '';
  const valueEl = firstElementChild(el, 'value');
  return {
    name,
    type,
    value: valueEl ? parseValue(valueEl) : { type: 'Input', content: '' },
  };
}

function parseFunctionParam(el: Element): FunctionParam {
  const name = el.getAttribute('name') ?? undefined;
  const propertyName = el.getAttribute('property-name') ?? undefined;
  const propertyLabel = el.getAttribute('property-label') ?? undefined;
  const valueEl = firstElementChild(el, 'value');
  const param: FunctionParam = {
    value: valueEl ? parseValue(valueEl) : { type: 'Input', content: '' },
  };
  if (name !== undefined) param.name = name;
  if (propertyName !== undefined) param.propertyName = propertyName;
  if (propertyLabel !== undefined) param.propertyLabel = propertyLabel;
  return param;
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

export function parseAction(el: Element): Action {
  switch (el.tagName) {
    case 'console-print': {
      const valueEl = firstElementChild(el, 'value');
      return {
        kind: 'console-print',
        value: valueEl ? parseValue(valueEl) : { type: 'Input', content: '' },
      };
    }
    case 'var-assign': {
      const valueEl = firstElementChild(el, 'value');
      const valueType = (el.getAttribute('type') ?? 'variable') as
        | 'variable'
        | 'parameter'
        | 'reference';
      return {
        kind: 'var-assign',
        var: el.getAttribute('var') ?? '',
        varLabel: el.getAttribute('var-label') ?? '',
        varCategory: el.getAttribute('var-category') ?? '',
        valueType,
        value: valueEl ? parseValue(valueEl) : { type: 'Input', content: '' },
      };
    }
    case 'execute-method': {
      const parameters: MethodParam[] = [];
      for (const child of Array.from(el.children)) {
        if (child.tagName === 'parameter') parameters.push(parseMethodParam(child));
      }
      return {
        kind: 'execute-method',
        bean: el.getAttribute('bean-name') ?? '',
        beanLabel: el.getAttribute('bean-label') ?? '',
        methodName: el.getAttribute('method-name') ?? '',
        methodLabel: el.getAttribute('method-label') ?? '',
        parameters,
      };
    }
    case 'execute-function': {
      let parameter: FunctionParam | undefined;
      for (const child of Array.from(el.children)) {
        if (child.tagName === 'function-parameter') {
          parameter = parseFunctionParam(child);
        }
      }
      return {
        kind: 'execute-function',
        functionName: el.getAttribute('function-name') ?? '',
        functionLabel: el.getAttribute('function-label') ?? '',
        parameter,
      };
    }
    default:
      throw new Error('parseAction: unexpected element <' + el.tagName + '>');
  }
}

// ---------------------------------------------------------------------------
// DOM helpers
// ---------------------------------------------------------------------------

function firstElementChild(el: Element, expectedTag?: string): Element | null {
  for (const child of Array.from(el.children)) {
    if (!expectedTag || child.tagName === expectedTag) {
      return child;
    }
  }
  return null;
}
