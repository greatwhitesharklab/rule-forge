/**
 * BDD tests for the decision-table model serialize/parse round-trip.
 *
 * vitest + jsdom (DOMParser is globally available). Each test follows the
 * Given/When/Then structure and asserts both the produced XML (via substring
 * expectations, so a human can eyeball the wire format) and a deep-equal
 * round-trip: parse(serialize(state)) must reproduce the input state exactly.
 */

import { describe, it, expect } from 'vitest';
import { serializeDecisionTable } from './serialize';
import { parseDecisionTable } from './parse';
import type { Action, ValueExpr } from '../../ruleforge/model/types';
import type { Cell, Column, DecisionTableData } from './types';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function emptyTable(remark = ''): DecisionTableData {
  return {
    remark,
    libraries: [],
    properties: [],
    columns: [],
    rows: [],
    cells: [],
  };
}

/** A Criteria column bound to a variable. */
function criteriaColumn(num: number, varName: string, varLabel: string, datatype: string): Column {
  return {
    num,
    type: 'Criteria',
    width: 120,
    variableCategory: '客户.客户',
    variableName: varName,
    variableLabel: varLabel,
    datatype,
  };
}

/** An Assignment (var-assign) action column — no var binding on the column itself. */
function assignmentColumn(num: number): Column {
  return { num, type: 'Assignment', width: 200 };
}

/** A simple Input value `<value type="Input" content="…"/>`. */
function inputValue(content: string): ValueExpr {
  return { type: 'Input', content };
}

/** A Criteria cell holding one condition with the given op + Input value. */
function criteriaCell(row: number, col: number, op: string, value: string): Cell {
  return {
    row,
    col,
    rowspan: 1,
    content: { joint: { type: 'and', conditions: [{ op, right: inputValue(value) }] } },
  };
}

/** An empty cell (user hasn't filled it). */
function emptyCell(row: number, col: number): Cell {
  return { row, col, rowspan: 1, content: { empty: true } };
}

/** A var-assign Assignment cell setting a variable to an Input value. */
function varAssignCell(row: number, col: number, varName: string, value: string): Cell {
  const action: Action = {
    kind: 'var-assign',
    var: varName,
    varLabel: varName,
    varCategory: '客户.客户',
    valueType: 'variable',
    value: inputValue(value),
  };
  return { row, col, rowspan: 1, content: { action } };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('decision-table model', () => {
  describe('serialize: empty table', () => {
    it('Given an empty table, When serialized, Then emits a minimal <decision-table> with empty CDATA remark', () => {
      // Given
      const dt = emptyTable();
      // When
      const xml = serializeDecisionTable(dt);
      // Then
      expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>');
      expect(xml).toContain('<decision-table>');
      expect(xml).toContain('<remark><![CDATA[]]></remark>');
      expect(xml).toContain('</decision-table>');
    });
  });

  describe('serialize: properties + libraries', () => {
    it('Given a table with salience + a variable library, When serialized, Then emits attributes + import element', () => {
      // Given
      const dt = emptyTable();
      dt.properties = [{ name: 'salience', value: '10' }, { name: 'enabled', value: true }];
      dt.libraries = [{ type: 'Variable', path: '/project/lib/customer.vl.xml' }];
      // When
      const xml = serializeDecisionTable(dt);
      // Then
      expect(xml).toContain('<decision-table salience="10" enabled="true">');
      expect(xml).toContain('<import-variable-library path="/project/lib/customer.vl.xml"/>');
    });
  });

  describe('serialize: columns and rows', () => {
    it('Given a table with a Criteria column + a row, When serialized, Then emits <col> with var attrs and <row>', () => {
      // Given
      const dt = emptyTable();
      dt.columns = [criteriaColumn(0, 'age', '年龄', 'Integer'), assignmentColumn(1)];
      dt.rows = [{ num: 0, height: 40 }];
      // When
      const xml = serializeDecisionTable(dt);
      // Then
      expect(xml).toContain('<col num="0" width="120" type="Criteria" var-category="客户.客户" var-label="年龄" var="age" datatype="Integer"/>');
      expect(xml).toContain('<col num="1" width="200" type="Assignment"/>');
      expect(xml).toContain('<row num="0" height="40"/>');
    });

    it('Given a parameter-bound Criteria column, When serialized, Then var-category is 参数', () => {
      // Given
      const dt = emptyTable();
      dt.columns = [{
        num: 0, type: 'Criteria', width: 120,
        variableCategory: 'parameter', variableName: 'amount',
        variableLabel: '金额', datatype: 'BigDecimal',
      }];
      // When
      const xml = serializeDecisionTable(dt);
      // Then
      expect(xml).toContain('var-category="参数"');
      expect(xml).toContain('var="amount"');
    });
  });

  describe('serialize: cells', () => {
    it('Given a Criteria cell, When serialized, Then emits <joint><condition op><value/></condition></joint>', () => {
      // Given
      const dt = emptyTable();
      dt.cells = [criteriaCell(1, 1, 'GreaterThen', '30')];
      // When
      const xml = serializeDecisionTable(dt);
      // Then
      expect(xml).toContain('<cell row="1" col="1" rowspan="1">');
      expect(xml).toContain('<joint type="and">');
      expect(xml).toContain('<condition op="GreaterThen">');
      expect(xml).toContain('<value content="30" type="Input"></value>');
      expect(xml).toContain('</condition>');
    });

    it('Given a Null condition, When serialized, Then the <value> child is suppressed', () => {
      // Given
      const dt = emptyTable();
      dt.cells = [{ row: 1, col: 1, rowspan: 1, content: { joint: { type: 'and', conditions: [{ op: 'Null' }] } } }];
      // When
      const xml = serializeDecisionTable(dt);
      // Then
      expect(xml).toContain('<condition op="Null"></condition>');
      expect(xml).not.toContain('<value');
    });

    it('Given an Assignment cell, When serialized, Then emits <var-assign><value/></var-assign>', () => {
      // Given
      const dt = emptyTable();
      dt.cells = [varAssignCell(1, 2, 'tier', 'GOLD')];
      // When
      const xml = serializeDecisionTable(dt);
      // Then
      expect(xml).toContain('<var-assign var="tier"');
      expect(xml).toContain('type="variable"');
      expect(xml).toContain('<value content="GOLD" type="Input"></value>');
    });

    it('Given an empty cell, When serialized, Then emits an empty <cell></cell>', () => {
      // Given
      const dt = emptyTable();
      dt.cells = [emptyCell(1, 1)];
      // When
      const xml = serializeDecisionTable(dt);
      // Then
      expect(xml).toContain('<cell row="1" col="1" rowspan="1"></cell>');
    });
  });

  describe('round-trip: parse(serialize(state)) deep-equals state', () => {
    it('Given a full table with columns/rows/cells/libraries/properties, When round-tripped, Then state is preserved', () => {
      // Given
      const original: DecisionTableData = {
        remark: '客户分级',
        properties: [{ name: 'salience', value: '10' }, { name: 'enabled', value: true }],
        libraries: [
          { type: 'Variable', path: '/p/customer.vl.xml' },
          { type: 'Constant', path: '/p/tier.cl.xml' },
        ],
        columns: [criteriaColumn(0, 'age', '年龄', 'Integer'), assignmentColumn(1)],
        rows: [{ num: 0, height: 40 }, { num: 1, height: 40 }],
        cells: [
          criteriaCell(1, 1, 'GreaterThen', '30'),
          varAssignCell(1, 2, 'tier', 'GOLD'),
          emptyCell(2, 1),
        ],
      };
      // When
      const roundTrip = parseDecisionTable(serializeDecisionTable(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a table with a Null condition, When round-tripped, Then the condition keeps op=Null and no right', () => {
      // Given
      const original = emptyTable('空判断');
      original.columns = [criteriaColumn(0, 'name', '姓名', 'String')];
      original.rows = [{ num: 0, height: 40 }];
      original.cells = [
        { row: 1, col: 1, rowspan: 1, content: { joint: { type: 'and', conditions: [{ op: 'Null' }] } } },
      ];
      // When
      const roundTrip = parseDecisionTable(serializeDecisionTable(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given an empty table, When round-tripped, Then the empty remark is preserved', () => {
      // Given
      const original = emptyTable();
      // When
      const roundTrip = parseDecisionTable(serializeDecisionTable(original));
      // Then
      expect(roundTrip).toEqual(original);
    });
  });
});
