/**
 * BDD tests for the crosstab model serialize/parse round-trip.
 *
 * vitest + jsdom (DOMParser is globally available). Each test follows the
 * Given/When/Then structure and asserts both the produced XML (via substring
 * expectations, so a human can eyeball the wire format) and a deep-equal
 * round-trip: parse(serialize(state)) must reproduce the input state exactly.
 */

import { describe, it, expect } from 'vitest';
import { serializeCrossTable } from './serialize';
import { parseCrossTable } from './parse';
import type { ValueExpr } from '../../ruleforge/model/types';
import type {
  AssignTarget,
  BundleData,
  ConditionCrossCell,
  CrossColumn,
  CrossRow,
  CrossTableData,
  ValueCrossCell,
} from './types';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function emptyTable(remark = ''): CrossTableData {
  return {
    remark,
    properties: [],
    header: { rowspan: 1, colspan: 1, text: '' },
    libraries: [],
    rows: [],
    columns: [],
    conditionCells: [],
    valueCells: [],
  };
}

/** A `<value type="Input" content="…"/>`. */
function inputValue(content: string): ValueExpr {
  return { type: 'Input', content };
}

/** A top (condition) row bound to a variable. */
function topRowVar(number: number, category: string, varName: string, varLabel: string, datatype: string): CrossRow {
  const bundle: BundleData = {
    type: 'variable',
    variableCategory: category,
    variableName: varName,
    variableLabel: varLabel,
    datatype,
  };
  return { number, type: 'top', bundleData: bundle };
}

/** A left (value) row. */
function leftRow(number: number): CrossRow {
  return { number, type: 'left' };
}

/** A left (condition) column bound to a variable. */
function leftColVar(number: number, category: string, varName: string, varLabel: string, datatype: string): CrossColumn {
  const bundle: BundleData = {
    type: 'variable',
    variableCategory: category,
    variableName: varName,
    variableLabel: varLabel,
    datatype,
  };
  return { number, type: 'left', bundleData: bundle };
}

/** A top (value) column. */
function topCol(number: number): CrossColumn {
  return { number, type: 'top' };
}

/** A variable assignment target. */
function variableTarget(category: string, varName: string, varLabel: string, datatype: string): AssignTarget {
  return { type: 'variable', variableCategory: category, variableName: varName, variableLabel: varLabel, datatype };
}

/** A condition-cell with a single condition. */
function conditionCell(row: number, col: number, op: string, value: string): ConditionCrossCell {
  return {
    row,
    col,
    rowspan: 1,
    colspan: 1,
    content: { joint: { type: 'and', conditions: [{ op, right: inputValue(value) }] } },
  };
}

/** A value-cell with an Input value. */
function valueCell(row: number, col: number, value: string): ValueCrossCell {
  return { row, col, value: inputValue(value) };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('crosstab model', () => {
  describe('serialize: empty crosstab', () => {
    it('Given an empty crosstab, When serialized, Then emits a minimal <crosstab> with header + empty CDATA remark', () => {
      // Given
      const ct = emptyTable();
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>');
      expect(xml).toContain('<crosstab>');
      expect(xml).toContain('<remark><![CDATA[]]></remark>');
      expect(xml).toContain('<header rowspan="1" colspan="1"><![CDATA[]]></header>');
      expect(xml).toContain('</crosstab>');
    });
  });

  describe('serialize: properties + assign target', () => {
    it('Given a crosstab with salience + a variable assign-target, When serialized, Then emits attrs on <crosstab>', () => {
      // Given
      const ct = emptyTable();
      ct.properties = [{ name: 'salience', value: '10' }, { name: 'enabled', value: true }];
      ct.assignTarget = variableTarget('客户.客户', 'result', '结果', 'String');
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('<crosstab salience="10" enabled="true"');
      expect(xml).toContain('assign-target-type="variable"');
      expect(xml).toContain('var-category="客户.客户"');
      expect(xml).toContain('var="result"');
      expect(xml).toContain('var-label="结果"');
      expect(xml).toContain('datatype="String"');
    });

    it('Given a parameter assign-target, When serialized, Then var-category is 参数', () => {
      // Given
      const ct = emptyTable();
      ct.assignTarget = { type: 'parameter', variableName: 'amount', variableLabel: '金额', datatype: 'BigDecimal' };
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('assign-target-type="parameter"');
      expect(xml).toContain('var-category="参数"');
      expect(xml).toContain('var="amount"');
    });
  });

  describe('serialize: header + libraries', () => {
    it('Given a crosstab with header text + libraries, When serialized, Then emits <header> + import elements', () => {
      // Given
      const ct = emptyTable();
      ct.header = { rowspan: 1, colspan: 1, text: 'TOP/LEFT' };
      ct.libraries = [
        { type: 'Variable', path: '/p/customer.vl.xml' },
        { type: 'Action', path: '/p/actions.al.xml' },
      ];
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('<header rowspan="1" colspan="1"><![CDATA[TOP/LEFT]]></header>');
      expect(xml).toContain('<import-variable-library path="/p/customer.vl.xml"/>');
      expect(xml).toContain('<import-action-library path="/p/actions.al.xml"/>');
    });
  });

  describe('serialize: rows and columns', () => {
    it('Given a top condition row bound to a variable, When serialized, Then emits <row> with bundle attrs', () => {
      // Given
      const ct = emptyTable();
      ct.rows = [topRowVar(1, '客户.客户', 'age', '年龄', 'Integer'), leftRow(2)];
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('<row number="1" type="top" bundle-data-type="variable" var-category="客户.客户" var="age" var-label="年龄" datatype="Integer"></row>');
      expect(xml).toContain('<row number="2" type="left"></row>');
    });

    it('Given a left condition column bound to a variable, When serialized, Then emits <column> with bundle attrs', () => {
      // Given
      const ct = emptyTable();
      ct.columns = [leftColVar(1, '客户.客户', 'gender', '性别', 'String'), topCol(2)];
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('<column number="1" type="left" bundle-data-type="variable" var-category="客户.客户" var="gender" var-label="性别" datatype="String"></column>');
      expect(xml).toContain('<column number="2" type="top"></column>');
    });
  });

  describe('serialize: cells', () => {
    it('Given a condition-cell, When serialized, Then emits <condition-cell> with <joint>', () => {
      // Given
      const ct = emptyTable();
      ct.conditionCells = [conditionCell(1, 1, 'Equals', '30')];
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('<condition-cell row="1" col="1" rowspan="1" colspan="1">');
      expect(xml).toContain('<joint type="and">');
      expect(xml).toContain('<condition op="Equals">');
      expect(xml).toContain('<value content="30" type="Input"></value>');
      expect(xml).toContain('</condition>');
    });

    it('Given a Null condition, When serialized, Then the <value> child is suppressed', () => {
      // Given
      const ct = emptyTable();
      ct.conditionCells = [
        { row: 1, col: 1, rowspan: 1, colspan: 1, content: { joint: { type: 'and', conditions: [{ op: 'Null' }] } } },
      ];
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('<condition op="Null"></condition>');
      expect(xml).not.toContain('<value');
    });

    it('Given a value-cell, When serialized, Then emits <value-cell> with a <value> child', () => {
      // Given
      const ct = emptyTable();
      ct.valueCells = [valueCell(2, 2, 'GOLD')];
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('<value-cell row="2" col="2">');
      expect(xml).toContain('<value content="GOLD" type="Input"></value>');
    });

    it('Given an empty value-cell, When serialized, Then emits an empty <value-cell>', () => {
      // Given
      const ct = emptyTable();
      ct.valueCells = [{ row: 2, col: 2 }];
      // When
      const xml = serializeCrossTable(ct);
      // Then
      expect(xml).toContain('<value-cell row="2" col="2"></value-cell>');
    });
  });

  describe('round-trip: parse(serialize(state)) deep-equals state', () => {
    it('Given a full crosstab, When round-tripped, Then state is preserved', () => {
      // Given
      const original: CrossTableData = {
        remark: '客户分级交叉表',
        properties: [{ name: 'salience', value: '10' }, { name: 'enabled', value: true }],
        assignTarget: variableTarget('客户.客户', 'result', '结果', 'String'),
        header: { rowspan: 1, colspan: 1, text: 'TOP/LEFT' },
        libraries: [
          { type: 'Variable', path: '/p/customer.vl.xml' },
          { type: 'Constant', path: '/p/tier.cl.xml' },
        ],
        rows: [topRowVar(1, '客户.客户', 'age', '年龄', 'Integer'), leftRow(2)],
        columns: [leftColVar(1, '客户.客户', 'gender', '性别', 'String'), topCol(2)],
        conditionCells: [conditionCell(1, 1, 'Equals', '30')],
        valueCells: [valueCell(2, 2, 'GOLD')],
      };
      // When
      const roundTrip = parseCrossTable(serializeCrossTable(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a crosstab with a Null condition, When round-tripped, Then the condition keeps op=Null and no right', () => {
      // Given
      const original = emptyTable('空判断');
      original.rows = [topRowVar(1, '客户.客户', 'name', '姓名', 'String')];
      original.conditionCells = [
        { row: 1, col: 1, rowspan: 1, colspan: 1, content: { joint: { type: 'and', conditions: [{ op: 'Null' }] } } },
      ];
      // When
      const roundTrip = parseCrossTable(serializeCrossTable(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given an empty crosstab, When round-tripped, Then the empty header + remark are preserved', () => {
      // Given
      const original = emptyTable();
      // When
      const roundTrip = parseCrossTable(serializeCrossTable(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a crosstab without an assign-target, When round-tripped, Then assignTarget stays undefined', () => {
      // Given
      const original = emptyTable('无赋值目标');
      original.valueCells = [valueCell(1, 1, 'X')];
      // When
      const roundTrip = parseCrossTable(serializeCrossTable(original));
      // Then
      expect(roundTrip.assignTarget).toBeUndefined();
      expect(roundTrip).toEqual(original);
    });
  });
});
