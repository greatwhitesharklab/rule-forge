/**
 * BDD tests for the complex scorecard model serialize/parse round-trip.
 *
 * vitest + jsdom (DOMParser is globally available). Each test follows the
 * Given/When/Then structure and asserts both the produced XML (via substring
 * expectations, so a human can eyeball the wire format) and a deep-equal
 * round-trip: parse(serialize(state)) must reproduce the input state exactly.
 *
 * The baseline empty card mirrors the backend's default new-file payload
 * (FrameController.java — new ComplexScorecard): 2 rows (num 0/1, height 40),
 * 3 cols (2× Criteria width 150, 1× Score width 120), 6 cells.
 */

import { describe, it, expect } from 'vitest';
import { serializeComplexScoreCard } from './serialize';
import { parseComplexScoreCard } from './parse';
import type { ValueExpr } from '../../ruleforge/model/types';
import type {
  ComplexCell,
  ComplexCol,
  ComplexScoreCardData,
} from './types';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

/** A minimal valid complex scorecard (required: scoringType + assignTarget). */
function emptyCard(): ComplexScoreCardData {
  return {
    remark: '',
    properties: [],
    scoringType: 'sum',
    assignTarget: { type: 'none' },
    libraries: [],
    cols: [],
    rows: [],
    cells: [],
  };
}

/** The backend's default new-file payload (2 rows, 3 cols, 6 cells). */
function freshCard(): ComplexScoreCardData {
  const cols: ComplexCol[] = [
    { num: 0, width: 150, type: 'Criteria', variableCategory: '客户.客户' },
    { num: 1, width: 150, type: 'Criteria', variableCategory: '客户.客户' },
    { num: 2, width: 120, type: 'Score' },
  ];
  const rows = [
    { num: 0, height: 40 },
    { num: 1, height: 40 },
  ];
  const cells: ComplexCell[] = [
    criteriaCell(0, 0, 'age', '年龄', 'Integer'),
    criteriaCell(0, 1, 'gender', '性别', 'String'),
    scoreCell(0, 2, '10'),
    criteriaCell(1, 0, 'age', '年龄', 'Integer'),
    criteriaCell(1, 1, 'gender', '性别', 'String'),
    scoreCell(1, 2, '5'),
  ];
  return { ...emptyCard(), cols, rows, cells };
}

/** An Input value `<value type="Input" content="…"/>`. */
function inputValue(content: string): ValueExpr {
  return { type: 'Input', content };
}

/** A Criteria cell at (row, col) with a single-condition joint. */
function criteriaCell(row: number, col: number, varName: string, varLabel: string, datatype: string, op = 'Equals', right?: ValueExpr): ComplexCell {
  const cond = right ? { op, right } : { op };
  return {
    row,
    col,
    rowspan: 1,
    variableName: varName,
    variableLabel: varLabel,
    datatype,
    joint: { type: 'and', conditions: [cond] },
  };
}

/** A Score cell with an Input value. */
function scoreCell(row: number, col: number, content: string): ComplexCell {
  return { row, col, rowspan: 1, value: inputValue(content) };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('complex scorecard model', () => {
  describe('serialize: empty card', () => {
    it('Given a minimal card, When serialized, Then emits a valid <complex-scorecard> with required attrs + empty remark', () => {
      // Given
      const card = emptyCard();
      // When
      const xml = serializeComplexScoreCard(card);
      // Then
      expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>');
      expect(xml).toContain('<complex-scorecard');
      expect(xml).toContain('scoring-type="sum"');
      expect(xml).toContain('assign-target-type="none"');
      expect(xml).toContain('<remark><![CDATA[]]></remark>');
      expect(xml).toContain('</complex-scorecard>');
    });

    it('Given a card without scoringType, When serialized, Then throws', () => {
      // Given
      const card = emptyCard();
      card.scoringType = '' as ComplexScoreCardData['scoringType'];
      // When / Then
      expect(() => serializeComplexScoreCard(card)).toThrow('得分计算方式');
    });

    it('Given a custom scoringType without a bean, When serialized, Then throws', () => {
      // Given
      const card = emptyCard();
      card.scoringType = 'custom';
      // When / Then
      expect(() => serializeComplexScoreCard(card)).toThrow('Bean ID');
    });
  });

  describe('serialize: properties + libraries', () => {
    it('Given a card with salience + libraries, When serialized, Then emits attrs + imports', () => {
      // Given
      const card = emptyCard();
      card.properties = [{ name: 'salience', value: '10' }, { name: 'enabled', value: true }];
      card.libraries = [
        { type: 'Variable', path: '/project/lib/customer.vl.xml' },
        { type: 'Parameter', path: '/project/lib/app.pl.xml' },
      ];
      // When
      const xml = serializeComplexScoreCard(card);
      // Then
      expect(xml).toContain('salience="10"');
      expect(xml).toContain('enabled="true"');
      expect(xml).toContain('<import-variable-library path="/project/lib/customer.vl.xml"/>');
      expect(xml).toContain('<import-parameter-library path="/project/lib/app.pl.xml"/>');
    });
  });

  describe('serialize: assign-target as root attributes', () => {
    it('Given a variable assignTarget, When serialized, Then emits var/var-label/datatype/var-category on the root', () => {
      // Given
      const card = emptyCard();
      card.assignTarget = {
        type: 'variable',
        value: {
          type: 'Variable',
          varCategory: '客户.客户',
          var: 'totalScore',
          varLabel: '总分',
          datatype: 'BigDecimal',
        },
      };
      // When
      const xml = serializeComplexScoreCard(card);
      // Then
      expect(xml).toContain('assign-target-type="variable"');
      expect(xml).toContain('var="totalScore"');
      expect(xml).toContain('var-label="总分"');
      expect(xml).toContain('datatype="BigDecimal"');
      expect(xml).toContain('var-category="客户.客户"');
      // NOT a bare <value> child (complex-scorecard puts it on the root).
      expect(xml).not.toContain('<value var-category=');
    });
  });

  describe('serialize: cells / rows / cols', () => {
    it('Given a fresh card, When serialized, Then emits cells then rows then cols in order', () => {
      // Given
      const card = freshCard();
      // When
      const xml = serializeComplexScoreCard(card);
      // Then
      // Criteria cell: var-label / var / datatype attrs + joint body.
      expect(xml).toContain('<cell row="0" col="0" rowspan="1" var-label="年龄" var="age" datatype="Integer">');
      expect(xml).toContain('<joint type="and">');
      // Score cell: empty attrs + value body.
      expect(xml).toContain('<cell row="0" col="2" rowspan="1">');
      expect(xml).toContain('<value content="10" type="Input"></value>');
      // Rows.
      expect(xml).toContain('<row num="0" height="40"/>');
      expect(xml).toContain('<row num="1" height="40"/>');
      // Cols.
      expect(xml).toContain('<col num="0" width="150" type="Criteria" var-category="客户.客户"/>');
      expect(xml).toContain('<col num="2" width="120" type="Score"/>');
    });

    it('Given a Null condition in a Criteria cell, When serialized, Then the <value> child is suppressed', () => {
      // Given
      const card = freshCard();
      card.cells[0] = criteriaCell(0, 0, 'name', '姓名', 'String', 'Null');
      // When
      const xml = serializeComplexScoreCard(card);
      // Then
      expect(xml).toContain('<condition op="Null"></condition>');
    });

    it('Given a Custom column with a custom-label, When serialized, Then emits custom-label attr', () => {
      // Given
      const card = emptyCard();
      card.cols = [
        { num: 0, width: 150, type: 'Criteria', variableCategory: '客户.客户' },
        { num: 1, width: 120, type: 'Score' },
        { num: 2, width: 160, type: 'Custom', customLabel: '备注' },
      ];
      card.rows = [{ num: 0, height: 40 }];
      card.cells = [
        criteriaCell(0, 0, 'age', '年龄', 'Integer'),
        scoreCell(0, 1, '10'),
        { row: 0, col: 2, rowspan: 1, value: inputValue('N/A') },
      ];
      // When
      const xml = serializeComplexScoreCard(card);
      // Then
      expect(xml).toContain('<col num="2" width="160" type="Custom" custom-label="备注"/>');
      expect(xml).toContain('<cell row="0" col="2" rowspan="1">');
    });

    it('Given a Criteria cell without a variable label, When serialized, Then throws', () => {
      // Given
      const card = freshCard();
      card.cells[0] = { row: 0, col: 0, rowspan: 1, joint: { type: 'and', conditions: [] } };
      // When / Then
      expect(() => serializeComplexScoreCard(card)).toThrow('对象属性');
    });

    it('Given a Criteria column without a variable category, When serialized, Then throws', () => {
      // Given
      const card = freshCard();
      card.cols[0] = { num: 0, width: 150, type: 'Criteria' };
      // When / Then
      expect(() => serializeComplexScoreCard(card)).toThrow('未定义具体变量或参数');
    });
  });

  describe('round-trip: parse(serialize(state)) deep-equals state', () => {
    it('Given a minimal card, When round-tripped, Then state is preserved', () => {
      // Given
      const original = emptyCard();
      // When
      const roundTrip = parseComplexScoreCard(serializeComplexScoreCard(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a fresh card with 2 rows / 3 cols / 6 cells, When round-tripped, Then state is preserved', () => {
      // Given
      const original = freshCard();
      // When
      const roundTrip = parseComplexScoreCard(serializeComplexScoreCard(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a card with a variable assignTarget + properties + libraries, When round-tripped, Then state is preserved', () => {
      // Given
      const original: ComplexScoreCardData = {
        remark: '信贷客户复杂评分卡',
        properties: [{ name: 'salience', value: '20' }, { name: 'enabled', value: true }],
        scoringType: 'weightsum',
        assignTarget: {
          type: 'variable',
          value: {
            type: 'Variable',
            varCategory: '客户.客户',
            var: 'totalScore',
            varLabel: '总分',
            datatype: 'BigDecimal',
          },
        },
        libraries: [
          { type: 'Variable', path: '/p/customer.vl.xml' },
          { type: 'Constant', path: '/p/tier.cl.xml' },
        ],
        cols: [
          { num: 0, width: 150, type: 'Criteria', variableCategory: '客户.客户' },
          { num: 1, width: 120, type: 'Score' },
        ],
        rows: [{ num: 0, height: 40 }],
        cells: [
          criteriaCell(0, 0, 'age', '年龄', 'Integer', 'GreaterThen', inputValue('30')),
          scoreCell(0, 1, '10'),
        ],
      };
      // When
      const roundTrip = parseComplexScoreCard(serializeComplexScoreCard(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a card with custom scoringType + Custom column + Null condition, When round-tripped, Then state is preserved', () => {
      // Given
      const original: ComplexScoreCardData = {
        remark: '',
        properties: [],
        scoringType: 'custom',
        customScoringBean: 'myBean',
        assignTarget: { type: 'none' },
        libraries: [],
        cols: [
          { num: 0, width: 150, type: 'Criteria', variableCategory: '客户.客户' },
          { num: 1, width: 120, type: 'Score' },
          { num: 2, width: 160, type: 'Custom', customLabel: '备注' },
        ],
        rows: [
          { num: 0, height: 40 },
          { num: 1, height: 40 },
        ],
        cells: [
          // Row 0: Criteria with Null condition + score + custom value.
          criteriaCell(0, 0, 'name', '姓名', 'String', 'Null'),
          scoreCell(0, 1, '5'),
          { row: 0, col: 2, rowspan: 1, value: inputValue('VIP') },
          // Row 1: a Criteria cell with rowspan=2 (vertical merge demo).
          { row: 1, col: 0, rowspan: 2, variableName: 'name', variableLabel: '姓名', datatype: 'String', joint: { type: 'or', conditions: [{ op: 'Equals', right: inputValue('张三') }] } },
          scoreCell(1, 1, '8'),
        ],
      };
      // When
      const roundTrip = parseComplexScoreCard(serializeComplexScoreCard(original));
      // Then
      expect(roundTrip).toEqual(original);
    });
  });

  describe('parse: malformed input', () => {
    it('Given XML whose root is not <complex-scorecard>, When parsed, Then throws', () => {
      // Given
      const bad = '<?xml version="1.0"?><scorecard/>';
      // When / Then
      expect(() => parseComplexScoreCard(bad)).toThrow('root element');
    });

    it('Given malformed XML, When parsed, Then throws', () => {
      // Given
      const bad = '<?xml version="1.0"?><complex-scorecard><cell row="0"></complex-scorecard>';
      // When / Then
      expect(() => parseComplexScoreCard(bad)).toThrow();
    });
  });
});
