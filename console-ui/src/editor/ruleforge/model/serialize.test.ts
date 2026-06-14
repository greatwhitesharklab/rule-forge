/**
 * BDD tests for the ruleforge model serialize/parse round-trip.
 *
 * vitest + jsdom (DOMParser is globally available). Each test follows the
 * Given/When/Then structure and asserts both the produced XML (via substring
 * expectations, so a human can eyeball the wire format) and a deep-equal
 * round-trip: parse(serialize(state)) must reproduce the input state exactly.
 */

import { describe, it, expect } from 'vitest';
import { serializeRuleset } from './serialize';
import { parseRuleset } from './parse';
import type {
  Action,
  ConditionNode,
  Rule,
  Ruleset,
  SimpleArith,
  ValueExpr,
} from './types';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function emptyRuleset(remark = ''): Ruleset {
  return {
    parameterLibraries: [],
    variableLibraries: [],
    constantLibraries: [],
    actionLibraries: [],
    remark,
    rules: [],
  };
}

function variableLeft(varCategory: string, variable: string, varLabel: string, datatype: string) {
  return {
    type: 'variable' as const,
    varCategory,
    var: variable,
    varLabel,
    datatype,
  };
}

/** A minimal `if` junction wrapping one or more condition nodes. */
function junction(type: 'and' | 'or', ...children: ConditionNode[]): ConditionNode {
  return { kind: 'junction', type, children };
}

/** A simple `<atom op="…"><left variable …/><value Input …/></atom>`. */
function simpleAtom(
  op: string,
  varCategory: string,
  variable: string,
  varLabel: string,
  datatype: string,
  right: ValueExpr,
): ConditionNode {
  return { kind: 'atom', op, left: variableLeft(varCategory, variable, varLabel, datatype), right };
}

function rule(name: string, ifNode: ConditionNode, then: Action[] = [], els: Action[] = []): Rule {
  return { name, properties: [], remark: '', if: ifNode, then, else: els };
}

// ---------------------------------------------------------------------------
// 1. Empty ruleset
// ---------------------------------------------------------------------------

describe('serialize/parse: empty ruleset', () => {
  it(`
    GIVEN an empty ruleset with libraries and a remark
    WHEN serialized
    THEN it produces a <rule-set> with import-* children and a CDATA remark,
         AND parse(serialize(state)) deep-equals the original state
  `, () => {
    // Given
    const state: Ruleset = {
      parameterLibraries: ['p.jxml'],
      variableLibraries: ['v.jxml'],
      constantLibraries: ['c.jxml'],
      actionLibraries: ['a.jxml'],
      remark: '备注内容',
      rules: [],
    };

    // When
    const xml = serializeRuleset(state);

    // Then — wire format (substring checks so a human can verify the layout)
    expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>');
    expect(xml).toContain('<rule-set>');
    expect(xml).toContain('<import-parameter-library path="p.jxml"/>');
    expect(xml).toContain('<import-variable-library path="v.jxml"/>');
    expect(xml).toContain('<import-constant-library path="c.jxml"/>');
    expect(xml).toContain('<import-action-library path="a.jxml"/>');
    // library order: parameter → variable → constant → action
    expect(xml.indexOf('import-parameter-library')).toBeLessThan(xml.indexOf('import-variable-library'));
    expect(xml.indexOf('import-variable-library')).toBeLessThan(xml.indexOf('import-constant-library'));
    expect(xml.indexOf('import-constant-library')).toBeLessThan(xml.indexOf('import-action-library'));
    expect(xml).toContain('<remark><![CDATA[备注内容]]></remark>');
    expect(xml.trim().endsWith('</rule-set>')).toBe(true);

    // Then — round-trip
    expect(parseRuleset(xml)).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 2. Single rule with atom + then action, round-trip
// ---------------------------------------------------------------------------

describe('serialize/parse: single rule with atom + action', () => {
  it(`
    GIVEN a rule whose if is an <and> with one atom and whose then is one
         console-print action
    WHEN serialized then parsed
    THEN the parsed state deep-equals the input
  `, () => {
    // Given
    const state: Ruleset = {
      ...emptyRuleset('规则集备注'),
      rules: [
        rule(
          '规则1',
          junction('and', simpleAtom('GreaterThen', '客户.客户', 'age', '年龄', 'Integer', { type: 'Input', content: '18' })),
          [{ kind: 'console-print', value: { type: 'Input', content: '命中' } }],
        ),
      ],
    };

    // When
    const xml = serializeRuleset(state);
    const back = parseRuleset(xml);

    // Then — wire format
    expect(xml).toContain('<rule name="规则1">');
    expect(xml).toContain('<if><and>');
    expect(xml).toContain('<atom op="GreaterThen">');
    expect(xml).toContain('<left var-category="客户.客户" var="age" var-label="年龄" datatype="Integer" type="variable">');
    expect(xml).toContain('<value content="18" type="Input">');
    expect(xml).toContain('</left><value content="18" type="Input"></value></atom>');
    expect(xml).toContain('<then><console-print><value content="命中" type="Input"></value></console-print></then>');
    expect(xml).toContain('<else></else>');

    // Then — round-trip
    expect(back).toEqual(state);
  });

  it(`
    GIVEN a rule with a Null op atom (no right value)
    WHEN serialized then parsed
    THEN the atom has no <value> child AND round-trips
  `, () => {
    // Given — op="Null" suppresses the <value> child (ComparisonOperator noInput)
    const state: Ruleset = {
      ...emptyRuleset(),
      rules: [
        rule('规则N', junction('and', {
          kind: 'atom',
          op: 'Null',
          left: variableLeft('客户.客户', 'name', '姓名', 'String'),
        })),
      ],
    };

    // When
    const xml = serializeRuleset(state);
    const back = parseRuleset(xml);

    // Then — no <value> emitted for Null ops
    expect(xml).toContain('<atom op="Null">');
    const atomStart = xml.indexOf('<atom op="Null">');
    const atomEnd = xml.indexOf('</atom>', atomStart);
    expect(xml.slice(atomStart, atomEnd)).not.toContain('<value');
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 3. Nested condition tree (and → or → atom + named-atom)
// ---------------------------------------------------------------------------

describe('serialize/parse: nested condition tree', () => {
  it(`
    GIVEN a rule whose if is an <and> containing a nested <or> junction,
         an atom, and a <named-atom> with named-criteria
    WHEN serialized then parsed
    THEN the recursive structure round-trips deep-equal
  `, () => {
    // Given
    const ifNode: ConditionNode = junction(
      'and',
      simpleAtom('GreaterThen', '客户.客户', 'age', '年龄', 'Integer', { type: 'Input', content: '18' }),
      junction('or', simpleAtom('Equals', '申请', 'channel', '渠道', 'String', { type: 'Input', content: 'APP' })),
      {
        kind: 'named',
        type: 'and',
        referenceName: 'c',
        varCategory: '客户.客户',
        items: [
          {
            op: 'Equals',
            var: 'gender',
            varLabel: '性别',
            datatype: 'String',
            right: { type: 'Constant', constCategory: '性别', const: 'MALE', constLabel: '男' },
          },
        ],
      },
    );
    const state: Ruleset = { ...emptyRuleset(), rules: [rule('嵌套规则', ifNode)] };

    // When
    const xml = serializeRuleset(state);
    const back = parseRuleset(xml);

    // Then — named-atom + nested or both present
    expect(xml).toContain('<named-atom junction-type="and" reference-name="c" var-category="客户.客户">');
    expect(xml).toContain('<named-criteria op="Equals" var="gender" var-label="性别" datatype="String">');
    expect(xml).toContain('<value const-category="性别" const="MALE" const-label="男" type="Constant">');
    expect(xml).toContain('<or>');
    // recursion round-trip
    expect(back).toEqual(state);
    expect(back.rules[0].if).toEqual(ifNode);
  });
});

// ---------------------------------------------------------------------------
// 4. XML escaping of special characters in content
// ---------------------------------------------------------------------------

describe('serialize/parse: XML escaping', () => {
  it(`
    GIVEN a rule whose Input content, attribute values, and remark contain
         the special characters & < > " '
    WHEN serialized then parsed
    THEN the XML is well-formed (DOMParser accepts it), the serialized form
         contains the entity escapes, and parse restores the raw characters
  `, () => {
    // Given — content with all five XML-special characters
    const nasty = 'a & b < c > d " e \' f';
    const state: Ruleset = {
      ...emptyRuleset('备注 & < > " \' 符号'),
      rules: [
        rule(
          '规则"特殊"',
          junction('and', simpleAtom('Equals', '客<户', 'v"x', '标<签', 'String', { type: 'Input', content: nasty })),
          [{ kind: 'console-print', value: { type: 'Input', content: nasty } }],
        ),
      ],
    };

    // When
    const xml = serializeRuleset(state);
    const back = parseRuleset(xml);

    // Then — content is entity-escaped in the wire form
    expect(xml).toContain('content="a &amp; b &lt; c &gt; d &quot; e &apos; f"');
    expect(xml).toContain('var-category="客&lt;户"');
    expect(xml).toContain('var="v&quot;x"');
    expect(xml).toContain('var-label="标&lt;签"');
    // Then — raw characters restored by parse, full round-trip
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 5. All four action kinds round-trip
// ---------------------------------------------------------------------------

describe('serialize/parse: four action kinds', () => {
  it(`
    GIVEN a rule whose then contains all four action kinds (console-print,
         var-assign, execute-method, execute-function)
    WHEN serialized then parsed
    THEN each action element is emitted with the correct attributes/children
         AND the parsed then-list deep-equals the input
  `, () => {
    // Given
    const actions: Action[] = [
      { kind: 'console-print', value: { type: 'Input', content: 'hit' } },
      {
        kind: 'var-assign',
        var: 'approved',
        varLabel: '通过',
        varCategory: '结果',
        valueType: 'variable',
        value: { type: 'Input', content: 'true' },
      },
      {
        kind: 'execute-method',
        bean: 'svc',
        beanLabel: '服务',
        methodName: 'm1',
        methodLabel: '方法',
        parameters: [{ name: 'x', type: 'String', value: { type: 'Input', content: 'y' } }],
      },
      {
        kind: 'execute-function',
        functionLabel: '求和',
        functionName: 'SumFun',
        parameter: {
          name: 'a',
          propertyName: 'amount',
          propertyLabel: '金额',
          value: { type: 'Input', content: '1' },
        },
      },
    ];
    const state: Ruleset = {
      ...emptyRuleset(),
      rules: [rule('四动作', junction('and', simpleAtom('Equals', 'C', 'v', 'l', 'String', { type: 'Input', content: '1' })), actions)],
    };

    // When
    const xml = serializeRuleset(state);
    const back = parseRuleset(xml);

    // Then — wire format of each action
    expect(xml).toContain('<console-print><value content="hit" type="Input"></value></console-print>');
    expect(xml).toContain(
      '<var-assign var="approved" var-label="通过" var-category="结果" type="variable"><value content="true" type="Input"></value></var-assign>',
    );
    expect(xml).toContain(
      '<execute-method bean-name="svc" bean-label="服务" method-name="m1" method-label="方法"><parameter name="x" type="String"><value content="y" type="Input"></value></parameter></execute-method>',
    );
    expect(xml).toContain(
      '<execute-function function-name="SumFun" function-label="求和"><function-parameter name="a" property-name="amount" property-label="金额"><value content="1" type="Input"></value></function-parameter></execute-function>',
    );
    // Then — round-trip
    expect(back.rules[0].then).toEqual(actions);
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 6. simple-arith chain (left) + complex-arith (value)
// ---------------------------------------------------------------------------

describe('serialize/parse: arithmetic chains', () => {
  it(`
    GIVEN a left value carrying a chained simple-arith (Add → Sub) AND a
         right value carrying a complex-arith whose payload is a paren with
         an inner complex-arith
    WHEN serialized then parsed
    THEN the nested arithmetic structures round-trip deep-equal
  `, () => {
    // Given — left.age + 100 - 5, right = (18 + 2) * 3 modeled as complex-arith
    const simpleChain: SimpleArith = {
      type: 'Add',
      value: '100',
      next: { type: 'Sub', value: '5' },
    };
    const ifNode: ConditionNode = {
      kind: 'atom',
      op: 'GreaterThen',
      left: {
        type: 'variable',
        varCategory: '客户.客户',
        var: 'age',
        varLabel: '年龄',
        datatype: 'Integer',
        arithmetic: simpleChain,
      },
      right: {
        type: 'Input',
        content: '18',
        // (18) * 3 — paren wrapping an Input, followed by a Mul complex-arith
        arithmetic: {
          type: 'Mul',
          paren: {
            value: { type: 'Input', content: '18' },
            arithmetic: { type: 'Add', value: { type: 'Input', content: '2' } },
          },
        },
      },
    };
    const state: Ruleset = { ...emptyRuleset(), rules: [rule('算术规则', ifNode)] };

    // When
    const xml = serializeRuleset(state);
    const back = parseRuleset(xml);

    // Then — simple-arith chain nesting
    expect(xml).toContain('<simple-arith type="Add" value="100"><simple-arith type="Sub" value="5"></simple-arith></simple-arith>');
    // Then — complex-arith with paren + inner complex-arith
    expect(xml).toContain('<complex-arith type="Mul"><paren><value content="18" type="Input"></value><complex-arith type="Add"><value content="2" type="Input"></value></complex-arith></paren></complex-arith>');
    // Then — round-trip
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 7. Rule properties (string + boolean) round-trip on <rule> attributes
// ---------------------------------------------------------------------------

describe('serialize/parse: rule properties', () => {
  it(`
    GIVEN a rule with string properties (salience) and boolean properties
         (enabled, loop)
    WHEN serialized then parsed
    THEN <rule> carries the attributes, booleans render as "true"/"false",
         AND round-trip preserves the value type
  `, () => {
    // Given
    const state: Ruleset = {
      ...emptyRuleset(),
      rules: [
        {
          name: '带属性',
          properties: [
            { name: 'salience', value: '100' },
            { name: 'enabled', value: true },
            { name: 'loop', value: false },
          ],
          remark: '',
          if: junction('and'),
          then: [],
          else: [],
        },
      ],
    };

    // When
    const xml = serializeRuleset(state);
    const back = parseRuleset(xml);

    // Then — attribute order: name first, then properties in given order
    expect(xml).toContain('<rule name="带属性" salience="100" enabled="true" loop="false">');
    // Then — round-trip preserves string vs boolean
    expect(back.rules[0].properties).toEqual([
      { name: 'salience', value: '100' },
      { name: 'enabled', value: true },
      { name: 'loop', value: false },
    ]);
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 8. Full document round-trip with the spec example
// ---------------------------------------------------------------------------

describe('serialize/parse: spec example document', () => {
  it(`
    GIVEN a ruleset mirroring the spec's canonical example (libraries,
         remark, one rule with nested if, four then actions, one else action)
    WHEN serialized then parsed
    THEN the full document round-trips deep-equal
  `, () => {
    // Given
    const state: Ruleset = {
      parameterLibraries: ['param.xml'],
      variableLibraries: ['var.xml'],
      constantLibraries: ['const.xml'],
      actionLibraries: ['act.xml'],
      remark: '规则集备注',
      rules: [
        {
          name: '规则1',
          properties: [{ name: 'salience', value: '100' }, { name: 'enabled', value: true }],
          remark: '规则备注',
          if: junction(
            'and',
            {
              kind: 'atom',
              op: 'GreaterThen',
              left: {
                type: 'variable',
                varCategory: '客户.客户',
                var: 'age',
                varLabel: '年龄',
                datatype: 'Integer',
                arithmetic: { type: 'Add', value: '100' },
              },
              right: { type: 'Input', content: '18' },
            },
            {
              kind: 'named',
              type: 'and',
              referenceName: 'c',
              varCategory: '客户.客户',
              items: [
                {
                  op: 'Equals',
                  var: 'gender',
                  varLabel: '性别',
                  datatype: 'String',
                  right: { type: 'Constant', constCategory: '性别', const: 'MALE', constLabel: '男' },
                },
              ],
            },
            junction('or', simpleAtom('Equals', '申请', 'channel', '渠道', 'String', { type: 'Input', content: 'APP' })),
          ),
          then: [
            {
              kind: 'var-assign',
              var: 'approved',
              varLabel: '通过',
              varCategory: '结果',
              valueType: 'variable',
              value: { type: 'Input', content: 'true' },
            },
            {
              kind: 'execute-method',
              bean: 'svc',
              beanLabel: '服务',
              methodName: 'm1',
              methodLabel: '方法',
              parameters: [{ name: 'x', type: 'String', value: { type: 'Input', content: 'y' } }],
            },
            {
              kind: 'execute-function',
              functionLabel: '求和',
              functionName: 'SumFun',
              parameter: { name: 'a', propertyName: 'amount', propertyLabel: '金额', value: { type: 'Input', content: '1' } },
            },
            { kind: 'console-print', value: { type: 'Input', content: 'hit' } },
          ],
          else: [{ kind: 'console-print', value: { type: 'Input', content: 'miss' } }],
        },
      ],
    };

    // When
    const xml = serializeRuleset(state);
    const back = parseRuleset(xml);

    // Then — spot-check key fragments from the spec example
    expect(xml).toContain('<rule-set>');
    expect(xml).toContain('<remark><![CDATA[规则集备注]]></remark>');
    expect(xml).toContain('<rule name="规则1" salience="100" enabled="true">');
    expect(xml).toContain('<named-atom junction-type="and" reference-name="c" var-category="客户.客户">');
    expect(xml).toContain('<simple-arith type="Add" value="100"></simple-arith>');
    // Then — full deep-equal round-trip
    expect(back).toEqual(state);
  });
});
