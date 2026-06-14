/**
 * BDD tests for the decisiontree model serialize/parse round-trip.
 *
 * vitest + jsdom (DOMParser is globally available). Each test follows the
 * Given/When/Then structure and asserts both the produced XML (via substring
 * expectations, so a human can eyeball the wire format) and a deep-equal
 * round-trip: parse(serialize(state)) must reproduce the input state exactly.
 *
 * The decision-tree schema is a tree of three node kinds rooted at a
 * <variable-tree-node>:
 *
 *   <decision-tree>
 *     <remark/>
 *     <import-*-library/>…
 *     <variable-tree-node>            ← root (kind: variable)
 *       <left/>
 *       <condition-tree-node op>      ← branch (kind: condition)
 *         <value/>
 *         <action-tree-node>          ← leaf (kind: action)
 *           <console-print/>…
 *         </action-tree-node>
 *       </condition-tree-node>
 *     </variable-tree-node>
 *   </decision-tree>
 */

import { describe, it, expect } from 'vitest';
import { serializeDecisionTree } from './serialize';
import { parseDecisionTree } from './parse';
import type { Action, DecisionTree, LeftValue, TreeNode } from './types';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

/** An empty decision-tree: just the root variable node, no children. */
function emptyTree(remark = ''): DecisionTree {
  return {
    parameterLibraries: [],
    variableLibraries: [],
    constantLibraries: [],
    actionLibraries: [],
    remark,
    properties: [],
    root: { kind: 'variable', left: { type: 'variable' }, children: [] },
  };
}

/** A variable-tree-node with the given left + condition children. */
function variableNode(
  left: LeftValue,
  ...children: Extract<TreeNode, { kind: 'condition' }>[]
): Extract<TreeNode, { kind: 'variable' }> {
  return { kind: 'variable', left, children };
}

/** A condition-tree-node with op + optional right + tree children. */
function conditionNode(
  op: string,
  children: TreeNode[],
  right?: Extract<TreeNode, { kind: 'condition' }>['right'],
): Extract<TreeNode, { kind: 'condition' }> {
  const node: Extract<TreeNode, { kind: 'condition' }> = { kind: 'condition', op, children };
  if (right) node.right = right;
  return node;
}

/** An action-tree-node leaf holding the given actions. */
function actionNode(...actions: Action[]): Extract<TreeNode, { kind: 'action' }> {
  return { kind: 'action', actions };
}

// ---------------------------------------------------------------------------
// 1. Empty decision tree
// ---------------------------------------------------------------------------

describe('serialize/parse: empty decision tree', () => {
  it(`
    GIVEN an empty decision tree with libraries and a remark
    WHEN serialized
    THEN it produces a <decision-tree> with import-* children, a CDATA remark,
         and an empty <variable-tree-node><left type="variable"/></variable-tree-node>,
         AND parse(serialize(state)) deep-equals the original state
  `, () => {
    // Given
    const state: DecisionTree = {
      parameterLibraries: ['p.jxml'],
      variableLibraries: ['v.jxml'],
      constantLibraries: ['c.jxml'],
      actionLibraries: ['a.jxml'],
      remark: '决策树备注',
      properties: [],
      root: { kind: 'variable', left: { type: 'variable' }, children: [] },
    };

    // When
    const xml = serializeDecisionTree(state);

    // Then — wire format
    expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>');
    expect(xml).toContain('<decision-tree>');
    expect(xml).toContain('<remark><![CDATA[决策树备注]]></remark>');
    expect(xml).toContain('<import-parameter-library path="p.jxml"/>');
    expect(xml).toContain('<import-variable-library path="v.jxml"/>');
    expect(xml).toContain('<import-constant-library path="c.jxml"/>');
    expect(xml).toContain('<import-action-library path="a.jxml"/>');
    // library order: parameter → variable → constant → action
    expect(xml.indexOf('import-parameter-library')).toBeLessThan(xml.indexOf('import-variable-library'));
    expect(xml.indexOf('import-variable-library')).toBeLessThan(xml.indexOf('import-constant-library'));
    expect(xml.indexOf('import-constant-library')).toBeLessThan(xml.indexOf('import-action-library'));
    // empty root variable node emits a bare <left type="variable"/>
    expect(xml).toContain('<variable-tree-node><left type="variable"></left></variable-tree-node>');
    expect(xml.trim().endsWith('</decision-tree>')).toBe(true);

    // Then — round-trip
    expect(parseDecisionTree(xml)).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 2. Simple tree: variable → condition → action leaf
// ---------------------------------------------------------------------------

describe('serialize/parse: variable → condition → action leaf', () => {
  it(`
    GIVEN a decision tree whose root variable holds a left "客户.年龄", with one
         condition branch (age GreaterThen 18) whose child is an action leaf
         printing "通过"
    WHEN serialized then parsed
    THEN the wire format has <variable-tree-node><left…/></variable-tree-node>
         wrapping a <condition-tree-node op="GreaterThen"> wrapping an
         <action-tree-node><console-print/></action-tree-node>, AND the parsed
         state deep-equals the input
  `, () => {
    // Given
    const state: DecisionTree = {
      ...emptyTree(),
      root: variableNode(
        { type: 'variable', varCategory: '客户.客户', var: 'age', varLabel: '年龄', datatype: 'Integer' },
        conditionNode(
          'GreaterThen',
          [actionNode({ kind: 'console-print', value: { type: 'Input', content: '通过' } })],
          { type: 'Input', content: '18' },
        ),
      ),
    };

    // When
    const xml = serializeDecisionTree(state);
    const back = parseDecisionTree(xml);

    // Then — wire format
    expect(xml).toContain(
      '<left var-category="客户.客户" var="age" var-label="年龄" datatype="Integer" type="variable">',
    );
    expect(xml).toContain('<condition-tree-node op="GreaterThen">');
    expect(xml).toContain('<value content="18" type="Input">');
    expect(xml).toContain('<action-tree-node>');
    expect(xml).toContain('<console-print><value content="通过" type="Input"></value></console-print>');

    // Then — round-trip
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 3. Nested tree: condition holding mixed condition / variable / action children
// ---------------------------------------------------------------------------

describe('serialize/parse: nested condition with mixed children', () => {
  it(`
    GIVEN a condition node that holds a nested condition child, a nested
         variable child, and an action leaf child
    WHEN serialized then parsed
    THEN the recursive structure round-trips deep-equal
  `, () => {
    // Given — root variable "金额" with one condition branch; that branch has
    // a nested condition, a nested variable, and an action leaf as siblings.
    const nested: TreeNode[] = [
      conditionNode('Equals', [actionNode({ kind: 'console-print', value: { type: 'Input', content: 'A' } })], {
        type: 'Input',
        content: 'X',
      }),
      variableNode(
        { type: 'variable', varCategory: '客户.客户', var: 'level', varLabel: '等级', datatype: 'String' },
        conditionNode('NotNull', [
          actionNode({ kind: 'console-print', value: { type: 'Input', content: 'B' } }),
        ]),
      ),
      actionNode({ kind: 'console-print', value: { type: 'Input', content: 'C' } }),
    ];
    const state: DecisionTree = {
      ...emptyTree(),
      root: variableNode(
        { type: 'variable', varCategory: '申请', var: 'amount', varLabel: '金额', datatype: 'BigDecimal' },
        conditionNode('GreaterThen', nested, { type: 'Input', content: '1000' }),
      ),
    };

    // When
    const xml = serializeDecisionTree(state);
    const back = parseDecisionTree(xml);

    // Then — both nested element kinds appear
    expect(xml).toContain('<condition-tree-node op="GreaterThen">');
    expect(xml).toContain('<condition-tree-node op="Equals">');
    expect(xml).toContain('<variable-tree-node>');
    expect(xml).toContain('<condition-tree-node op="NotNull">');
    // round-trip
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 4. Action leaf with multiple actions (var-assign + execute-method)
// ---------------------------------------------------------------------------

describe('serialize/parse: action leaf with multiple actions', () => {
  it(`
    GIVEN an action leaf holding a var-assign and an execute-method action
    WHEN serialized then parsed
    THEN the action-tree-node contains both action elements in order AND
         the parsed actions list deep-equals the input
  `, () => {
    // Given
    const actions: Action[] = [
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
    ];
    const state: DecisionTree = {
      ...emptyTree(),
      root: variableNode(
        { type: 'variable', varCategory: '客户.客户', var: 'age', varLabel: '年龄', datatype: 'Integer' },
        conditionNode('Equals', [actionNode(...actions)], {
          type: 'Input',
          content: '20',
        }),
      ),
    };

    // When
    const xml = serializeDecisionTree(state);
    const back = parseDecisionTree(xml);

    // Then — wire format of both actions inside the leaf
    expect(xml).toContain('<action-tree-node>');
    expect(xml).toContain(
      '<var-assign var="approved" var-label="通过" var-category="结果" type="variable"><value content="true" type="Input"></value></var-assign>',
    );
    expect(xml).toContain(
      '<execute-method bean-name="svc" bean-label="服务" method-name="m1" method-label="方法"><parameter name="x" type="String"><value content="y" type="Input"></value></parameter></execute-method>',
    );
    // Then — round-trip
    expect(back.root).toEqual(state.root);
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 5. XML escaping of special characters
// ---------------------------------------------------------------------------

describe('serialize/parse: XML escaping', () => {
  it(`
    GIVEN a decision tree whose remark, attribute values, and action content
         contain the special characters & < > " '
    WHEN serialized then parsed
    THEN the XML is well-formed (DOMParser accepts it), the serialized form
         contains the entity escapes, and parse restores the raw characters
  `, () => {
    // Given — content with all five XML-special characters
    const nasty = 'a & b < c > d " e \' f';
    const state: DecisionTree = {
      ...emptyTree('备注 & < > " \' 符号'),
      root: variableNode(
        { type: 'variable', varCategory: '客<户', var: 'v"x', varLabel: '标<签', datatype: 'String' },
        conditionNode(
          'Equals',
          [actionNode({ kind: 'console-print', value: { type: 'Input', content: nasty } })],
          { type: 'Input', content: nasty },
        ),
      ),
    };

    // When
    const xml = serializeDecisionTree(state);
    const back = parseDecisionTree(xml);

    // Then — attribute values are entity-escaped in the wire form
    expect(xml).toContain('var-category="客&lt;户"');
    expect(xml).toContain('var="v&quot;x"');
    expect(xml).toContain('var-label="标&lt;签"');
    expect(xml).toContain('content="a &amp; b &lt; c &gt; d &quot; e &apos; f"');
    // Then — raw characters restored by parse, full round-trip
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 6. Decision-tree attributes (salience + boolean enabled/debug) round-trip
// ---------------------------------------------------------------------------

describe('serialize/parse: decision-tree properties', () => {
  it(`
    GIVEN a decision tree with string property salience and boolean properties
         enabled / debug on the <decision-tree> element
    WHEN serialized then parsed
    THEN <decision-tree> carries the attributes, booleans render as
         "true"/"false", AND round-trip preserves the value type
  `, () => {
    // Given
    const state: DecisionTree = {
      ...emptyTree(),
      properties: [
        { name: 'salience', value: '100' },
        { name: 'enabled', value: true },
        { name: 'debug', value: false },
      ],
    };

    // When
    const xml = serializeDecisionTree(state);
    const back = parseDecisionTree(xml);

    // Then — attribute order preserved on <decision-tree>
    expect(xml).toContain('<decision-tree salience="100" enabled="true" debug="false">');
    // Then — round-trip preserves string vs boolean
    expect(back.properties).toEqual([
      { name: 'salience', value: '100' },
      { name: 'enabled', value: true },
      { name: 'debug', value: false },
    ]);
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 7. Condition with no right-hand <value> (e.g. Null / NotNull branches)
// ---------------------------------------------------------------------------

describe('serialize/parse: condition without a right value', () => {
  it(`
    GIVEN a condition node with op="NotNull" and NO right value (the leaf is
         the action child only)
    WHEN serialized then parsed
    THEN the <condition-tree-node> has no <value> child AND round-trips
  `, () => {
    // Given — NotNull carries no <value> (matches ComparisonOperator noInput)
    const state: DecisionTree = {
      ...emptyTree(),
      root: variableNode(
        { type: 'variable', varCategory: '客户.客户', var: 'name', varLabel: '姓名', datatype: 'String' },
        conditionNode('NotNull', [actionNode({ kind: 'console-print', value: { type: 'Input', content: 'ok' } })]),
      ),
    };

    // When
    const xml = serializeDecisionTree(state);
    const back = parseDecisionTree(xml);

    // Then — NotNull condition 自身无 <value> 右值(op 后紧跟 <action-tree-node>,不是 <value>)
    // 注意:action 子元素内可能有 <value>(动作的值),那是动作的,不是 condition 的右值
    expect(xml).toContain('<condition-tree-node op="NotNull"><action-tree-node>');
    // Then — round-trip
    expect(back).toEqual(state);
  });
});

// ---------------------------------------------------------------------------
// 8. Full canonical example (mirrors the legacy-decision-tree.xml shape)
// ---------------------------------------------------------------------------

describe('serialize/parse: canonical decision tree example', () => {
  it(`
    GIVEN a decision tree mirroring the canonical example (libraries, remark,
         salience, a root variable with two condition branches each ending in
         an action leaf, one of them nesting a variable sub-branch)
    WHEN serialized then parsed
    THEN the full document round-trips deep-equal
  `, () => {
    // Given
    const state: DecisionTree = {
      parameterLibraries: ['param.xml'],
      variableLibraries: ['var.xml'],
      constantLibraries: ['const.xml'],
      actionLibraries: ['act.xml'],
      remark: '信贷分类决策树',
      properties: [{ name: 'salience', value: '10' }],
      root: variableNode(
        { type: 'variable', varCategory: '客户.客户', var: 'age', varLabel: '年龄', datatype: 'Integer' },
        conditionNode(
          'GreaterThen',
          [
            actionNode({ kind: 'var-assign', var: 'tier', varLabel: '等级', varCategory: '结果', valueType: 'variable', value: { type: 'Input', content: 'A' } }),
            variableNode(
              { type: 'variable', varCategory: '客户.客户', var: 'income', varLabel: '收入', datatype: 'BigDecimal' },
              conditionNode('GreaterThen', [
                actionNode({ kind: 'console-print', value: { type: 'Input', content: '高端客户' } }),
              ], { type: 'Input', content: '100000' }),
            ),
          ],
          { type: 'Input', content: '50' },
        ),
        conditionNode('LessThenEquals', [
          actionNode({ kind: 'var-assign', var: 'tier', varLabel: '等级', varCategory: '结果', valueType: 'variable', value: { type: 'Input', content: 'C' } }),
        ], { type: 'Input', content: '30' }),
      ),
    };

    // When
    const xml = serializeDecisionTree(state);
    const back = parseDecisionTree(xml);

    // Then — spot-check key fragments
    expect(xml).toContain('<decision-tree salience="10">');
    expect(xml).toContain('<remark><![CDATA[信贷分类决策树]]></remark>');
    expect(xml).toContain('<condition-tree-node op="GreaterThen">');
    expect(xml).toContain('<condition-tree-node op="LessThenEquals">');
    expect(xml).toContain('<var-assign var="tier" var-label="等级" var-category="结果" type="variable">');
    // Then — full deep-equal round-trip
    expect(back).toEqual(state);
  });
});
