/**
 * BDD tests for the script-decision-table model serialize/parse round-trip.
 *
 * vitest + jsdom (DOMParser is globally available). Each test follows the
 * Given/When/Then structure and asserts both the produced XML (via substring
 * expectations, so a human can eyeball the wire format) and a deep-equal
 * round-trip: parse(serialize(state)) must reproduce the input state exactly.
 *
 * ── How these mirror ../decisiontable/model/serialize.test.ts ─────────────
 * The grid shape is shared (columns / rows / libraries), so the column / row
 * / library fixtures reuse the decision-table helpers. Only the CELL fixture
 * is script-specific: a `<script-cell>` whose content is a CDATA-wrapped UL
 * expression string, not a `<joint>` of `<condition>`s or a structured action.
 */

import { describe, it, expect } from 'vitest';
import { serializeScriptDecisionTable } from './serialize';
import { parseScriptDecisionTable } from './parse';
import type { Column } from '../../decisiontable/model/types';
import type { ScriptCell, ScriptDecisionTableData } from './types';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function emptyTable(): ScriptDecisionTableData {
  return { libraries: [], columns: [], rows: [], cells: [] };
}

/** A Criteria column bound to a variable (same shape as decision-table). */
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

/** An Assignment (var-assign) action column — no var binding on the column. */
function assignmentColumn(num: number): Column {
  return { num, type: 'Assignment', width: 200 };
}

/** A script cell holding a UL expression string. */
function scriptCell(row: number, col: number, script: string): ScriptCell {
  return { row, col, rowspan: 1, content: { script } };
}

/** A script cell holding an empty script (unfilled cell). */
function emptyScriptCell(row: number, col: number): ScriptCell {
  return { row, col, rowspan: 1, content: { script: '' } };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('script-decision-table model', () => {
  describe('serialize: empty table', () => {
    it('Given an empty table, When serialized, Then emits a bare <script-decision-table> root with NO xml declaration and NO remark', () => {
      // Given
      const sdt = emptyTable();
      // When
      const xml = serializeScriptDecisionTable(sdt);
      // Then
      expect(xml).not.toContain('<?xml');
      expect(xml).toBe('<script-decision-table></script-decision-table>');
    });
  });

  describe('serialize: libraries', () => {
    it('Given a table with a variable + constant library, When serialized, Then emits both import elements', () => {
      // Given
      const sdt = emptyTable();
      sdt.libraries = [
        { type: 'Variable', path: '/project/lib/customer.vl.xml' },
        { type: 'Constant', path: '/project/lib/tier.cl.xml' },
      ];
      // When
      const xml = serializeScriptDecisionTable(sdt);
      // Then
      expect(xml).toContain('<import-variable-library path="/project/lib/customer.vl.xml"/>');
      expect(xml).toContain('<import-constant-library path="/project/lib/tier.cl.xml"/>');
    });
  });

  describe('serialize: columns and rows', () => {
    it('Given a table with a Criteria column + an Assignment column + a row, When serialized, Then emits <col> and <row> in the decision-table wire format', () => {
      // Given
      const sdt = emptyTable();
      sdt.columns = [criteriaColumn(0, 'age', '年龄', 'Integer'), assignmentColumn(1)];
      sdt.rows = [{ num: 0, height: 40 }];
      // When
      const xml = serializeScriptDecisionTable(sdt);
      // Then — column wire format is identical to decision-table.
      expect(xml).toContain('<col num="0" width="120" type="Criteria" var-category="客户.客户" var-label="年龄" var="age" datatype="Integer"/>');
      expect(xml).toContain('<col num="1" width="200" type="Assignment"/>');
      expect(xml).toContain('<row num="0" height="40"/>');
    });

    it('Given a parameter-bound Criteria column, When serialized, Then var-category is 参数', () => {
      // Given
      const sdt = emptyTable();
      sdt.columns = [{
        num: 0, type: 'Criteria', width: 120,
        variableCategory: 'parameter', variableName: 'amount',
        variableLabel: '金额', datatype: 'BigDecimal',
      }];
      // When
      const xml = serializeScriptDecisionTable(sdt);
      // Then
      expect(xml).toContain('var-category="参数"');
      expect(xml).toContain('var="amount"');
    });
  });

  describe('serialize: script cells', () => {
    it('Given a Criteria script cell with a UL if-expression, When serialized, Then emits <script-cell> with the script wrapped in CDATA', () => {
      // Given
      const sdt = emptyTable();
      sdt.cells = [scriptCell(1, 1, '客户.年龄 > 30')];
      // When
      const xml = serializeScriptDecisionTable(sdt);
      // Then
      expect(xml).toContain('<script-cell row="1" col="1" rowspan="1">');
      expect(xml).toContain('<![CDATA[客户.年龄 > 30]]>');
      expect(xml).toContain('</script-cell>');
    });

    it('Given an Assignment script cell with a then-expression, When serialized, Then the script is CDATA-wrapped verbatim', () => {
      // Given
      const sdt = emptyTable();
      sdt.cells = [scriptCell(1, 2, 'tier = "GOLD"')];
      // When
      const xml = serializeScriptDecisionTable(sdt);
      // Then
      expect(xml).toContain('<script-cell row="1" col="2" rowspan="1"><![CDATA[tier = "GOLD"]]></script-cell>');
    });

    it('Given an empty script cell, When serialized, Then emits an empty CDATA section', () => {
      // Given
      const sdt = emptyTable();
      sdt.cells = [emptyScriptCell(1, 1)];
      // When
      const xml = serializeScriptDecisionTable(sdt);
      // Then
      expect(xml).toContain('<script-cell row="1" col="1" rowspan="1"><![CDATA[]]></script-cell>');
    });

    it('Given a script cell whose content contains the CDATA terminator, When serialized, Then the terminator is escaped so the CDATA stays well-formed', () => {
      // Given
      const sdt = emptyTable();
      sdt.cells = [scriptCell(1, 1, 'if (x) { /* ]]> */ }')];
      // When
      const xml = serializeScriptDecisionTable(sdt);
      // Then — escCdata 把 `]]>` 拆成多段 CDATA(`]]]]><![CDATA[>`),parse 时 textContent 自动拼接还原
      expect(xml).toContain(']]]]><![CDATA[>');
      expect(xml).not.toContain('<![CDATA[if (x) { /* ]]> */ }]]>');
    });
  });

  describe('serialize: element order', () => {
    it('Given a full table, When serialized, Then children are ordered libraries → script-cells → rows → cols (matching legacy toXml)', () => {
      // Given
      const sdt = emptyTable();
      sdt.libraries = [{ type: 'Variable', path: '/p/c.vl.xml' }];
      sdt.cells = [scriptCell(1, 1, 'x > 0')];
      sdt.rows = [{ num: 0, height: 40 }];
      sdt.columns = [criteriaColumn(0, 'x', 'x', 'Integer')];
      // When
      const xml = serializeScriptDecisionTable(sdt);
      // Then — assert relative order via indexOf.
      const idxLib = xml.indexOf('<import-variable-library');
      const idxCell = xml.indexOf('<script-cell');
      const idxRow = xml.indexOf('<row ');
      const idxCol = xml.indexOf('<col ');
      expect(idxLib).toBeLessThan(idxCell);
      expect(idxCell).toBeLessThan(idxRow);
      expect(idxRow).toBeLessThan(idxCol);
    });
  });

  describe('round-trip: parse(serialize(state)) deep-equals state', () => {
    it('Given a full table with libraries/columns/rows/script cells, When round-tripped, Then state is preserved', () => {
      // Given
      const original: ScriptDecisionTableData = {
        libraries: [
          { type: 'Variable', path: '/p/customer.vl.xml' },
          { type: 'Constant', path: '/p/tier.cl.xml' },
          { type: 'Action', path: '/p/loan.al.xml' },
          { type: 'Parameter', path: '/p/loan.pl.xml' },
        ],
        columns: [criteriaColumn(0, 'age', '年龄', 'Integer'), assignmentColumn(1)],
        rows: [{ num: 0, height: 40 }, { num: 1, height: 40 }],
        cells: [
          scriptCell(1, 1, '客户.年龄 > 30'),
          scriptCell(1, 2, 'tier = "GOLD"'),
          emptyScriptCell(2, 1),
        ],
      };
      // When
      const roundTrip = parseScriptDecisionTable(serializeScriptDecisionTable(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a table with a CDATA-terminator in a script, When round-tripped, Then the script string is preserved byte-for-byte', () => {
      // Given — the legacy toXml would produce a MALFORMED doc here, but our
      // escCdata makes it round-trip-safe, so parse should recover the original.
      const original = emptyTable();
      original.columns = [criteriaColumn(0, 'x', 'x', 'Integer')];
      original.rows = [{ num: 0, height: 40 }];
      original.cells = [scriptCell(1, 1, 'a ]]> b')];
      // When
      const roundTrip = parseScriptDecisionTable(serializeScriptDecisionTable(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given an empty table, When round-tripped, Then the empty cells/libraries/columns arrays are preserved', () => {
      // Given
      const original = emptyTable();
      // When
      const roundTrip = parseScriptDecisionTable(serializeScriptDecisionTable(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given malformed XML, When parsed, Then throws a helpful error mentioning the root tag', () => {
      // Given
      const badXml = '<decision-table></decision-table>';
      // When / Then
      expect(() => parseScriptDecisionTable(badXml)).toThrow(/script-decision-table/);
    });
  });
});
