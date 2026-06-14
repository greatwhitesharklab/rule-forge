/**
 * BDD tests for the scorecard model serialize/parse round-trip.
 *
 * vitest + jsdom (DOMParser is globally available). Each test follows the
 * Given/When/Then structure and asserts both the produced XML (via substring
 * expectations, so a human can eyeball the wire format) and a deep-equal
 * round-trip: parse(serialize(state)) must reproduce the input state exactly.
 */

import { describe, it, expect } from 'vitest';
import { serializeScoreCard } from './serialize';
import { parseScoreCard } from './parse';
import type { ValueExpr } from '../../ruleforge/model/types';
import type {
  CardCell,
  CustomCol,
  ScoreCardData,
} from './types';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

/** A minimal valid scorecard (required: name + scoringType + assignTarget). */
function emptyCard(name = '测试评分卡'): ScoreCardData {
  return {
    name,
    remark: '',
    properties: [],
    weightSupport: false,
    attributeCol: { name: '属性', width: 200 },
    conditionCol: { name: '条件', width: 220 },
    scoreCol: { name: '分值', width: 180 },
    scoringType: 'sum',
    assignTarget: { type: 'none' },
    libraries: [],
    cells: [],
    rows: [],
    customCols: [],
  };
}

/** An Input value `<value type="Input" content="…"/>`. */
function inputValue(content: string): ValueExpr {
  return { type: 'Input', content };
}

/**
 * An attribute row at the given row number, with one condition+score pair.
 * The original editor's first attribute row lives at row=2.
 */
function attributeCell(row: number, varName: string, varLabel: string, datatype: string, category: string, weight?: string): CardCell {
  const cell: CardCell = {
    type: 'attribute',
    row,
    col: 1,
    category,
    variableName: varName,
    variableLabel: varLabel,
    datatype,
  };
  if (weight !== undefined) cell.weight = weight;
  return cell;
}

/** A condition cell with a flat single-condition joint. */
function conditionCell(row: number, op: string, value?: ValueExpr): CardCell {
  const cond = value ? { op, right: value } : { op };
  return { type: 'condition', row, col: 2, joint: { type: 'and', conditions: [cond] } };
}

/** A score cell with an Input value. */
function scoreCell(row: number, content: string): CardCell {
  return { type: 'score', row, col: 3, value: inputValue(content) };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('scorecard model', () => {
  describe('serialize: empty card', () => {
    it('Given a minimal scorecard, When serialized, Then emits a valid <scorecard> with required attrs + empty remark', () => {
      // Given
      const card = emptyCard();
      // When
      const xml = serializeScoreCard(card);
      // Then
      expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>');
      expect(xml).toContain('<scorecard');
      expect(xml).toContain('weight-support="false"');
      expect(xml).toContain('name="测试评分卡"');
      expect(xml).toContain('attr-col-width="200"');
      expect(xml).toContain('attr-col-name="属性"');
      expect(xml).toContain('condition-col-name="条件"');
      expect(xml).toContain('score-col-name="分值"');
      expect(xml).toContain('scoring-type="sum"');
      expect(xml).toContain('assign-target-type="none"');
      expect(xml).toContain('<remark><![CDATA[]]></remark>');
      expect(xml).toContain('</scorecard>');
    });

    it('Given a card without a name, When serialized, Then throws', () => {
      // Given
      const card = emptyCard('');
      // When / Then
      expect(() => serializeScoreCard(card)).toThrow('评分卡名称不能为空');
    });
  });

  describe('serialize: properties + libraries + custom col headers', () => {
    it('Given a card with salience + a variable library + a custom header, When serialized, Then emits attrs + import', () => {
      // Given
      const card = emptyCard();
      card.properties = [{ name: 'salience', value: '10' }, { name: 'enabled', value: true }];
      card.libraries = [{ type: 'Variable', path: '/project/lib/customer.vl.xml' }];
      card.attributeCol = { name: '评分属性', width: 250 };
      // When
      const xml = serializeScoreCard(card);
      // Then
      expect(xml).toContain('salience="10"');
      expect(xml).toContain('enabled="true"');
      expect(xml).toContain('<import-variable-library path="/project/lib/customer.vl.xml"/>');
      expect(xml).toContain('attr-col-width="250"');
      expect(xml).toContain('attr-col-name="评分属性"');
    });
  });

  describe('serialize: scoringType custom + assignTarget variable', () => {
    it('Given a custom scoringType, When serialized, Then emits custom-scoring-bean', () => {
      // Given
      const card = emptyCard();
      card.scoringType = 'custom';
      card.customScoringBean = 'myScoringBean';
      // When
      const xml = serializeScoreCard(card);
      // Then
      expect(xml).toContain('scoring-type="custom"');
      expect(xml).toContain('custom-scoring-bean="myScoringBean"');
    });

    it('Given a custom scoringType without a bean, When serialized, Then throws', () => {
      // Given
      const card = emptyCard();
      card.scoringType = 'custom';
      // When / Then
      expect(() => serializeScoreCard(card)).toThrow('Bean ID');
    });

    it('Given an assignTarget of variable, When serialized, Then emits the bare <value> child', () => {
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
      const xml = serializeScoreCard(card);
      // Then
      expect(xml).toContain('assign-target-type="variable"');
      expect(xml).toContain('<value var-category="客户.客户" var="totalScore"');
      expect(xml).toContain('var-label="总分"');
      expect(xml).toContain('type="Variable"');
    });
  });

  describe('serialize: cells (attribute / condition / score)', () => {
    it('Given an attribute row, When serialized, Then emits card-cell of each type in order', () => {
      // Given
      const card = emptyCard();
      card.cells = [
        attributeCell(2, 'age', '年龄', 'Integer', '客户.客户'),
        conditionCell(2, 'GreaterThen', inputValue('30')),
        scoreCell(2, '10'),
      ];
      card.rows = [{ rowNumber: 2, conditionRows: [] }];
      // When
      const xml = serializeScoreCard(card);
      // Then
      expect(xml).toContain('<card-cell type="attribute" row="2" col="1"');
      expect(xml).toContain('var="age"');
      expect(xml).toContain('var-label="年龄"');
      expect(xml).toContain('datatype="Integer"');
      expect(xml).toContain('category="客户.客户"');
      expect(xml).toContain('<card-cell type="condition" row="2" col="2">');
      expect(xml).toContain('<joint type="and">');
      expect(xml).toContain('<condition op="GreaterThen">');
      expect(xml).toContain('<value content="30" type="Input"></value>');
      expect(xml).toContain('<card-cell type="score" row="2" col="3">');
      expect(xml).toContain('<attribute-row row-number="2">');
    });

    it('Given a Null condition, When serialized, Then the <value> child is suppressed', () => {
      // Given
      const card = emptyCard();
      card.cells = [
        attributeCell(2, 'name', '姓名', 'String', '客户.客户'),
        conditionCell(2, 'Null'),
        scoreCell(2, '5'),
      ];
      card.rows = [{ rowNumber: 2, conditionRows: [] }];
      // When
      const xml = serializeScoreCard(card);
      // Then
      expect(xml).toContain('<condition op="Null"></condition>');
    });

    it('Given weightSupport + a weighted attribute, When serialized, Then emits weight attr', () => {
      // Given
      const card = emptyCard();
      card.weightSupport = true;
      card.cells = [
        attributeCell(2, 'age', '年龄', 'Integer', '客户.客户', '0.5'),
        conditionCell(2, 'GreaterThen', inputValue('30')),
        scoreCell(2, '10'),
      ];
      card.rows = [{ rowNumber: 2, conditionRows: [] }];
      // When
      const xml = serializeScoreCard(card);
      // Then
      expect(xml).toContain('weight-support="true"');
      expect(xml).toContain('weight="0.5"');
    });

    it('Given weightSupport without a weight on an attribute, When serialized, Then throws', () => {
      // Given
      const card = emptyCard();
      card.weightSupport = true;
      card.cells = [attributeCell(2, 'age', '年龄', 'Integer', '客户.客户')];
      card.rows = [{ rowNumber: 2, conditionRows: [] }];
      // When / Then
      expect(() => serializeScoreCard(card)).toThrow('权重');
    });
  });

  describe('serialize: custom col', () => {
    it('Given a custom col, When serialized, Then emits <custom-col>', () => {
      // Given
      const card = emptyCard();
      const customCol: CustomCol = { colNumber: 4, name: '备注', width: 160 };
      card.customCols = [customCol];
      // When
      const xml = serializeScoreCard(card);
      // Then
      expect(xml).toContain('<custom-col col-number="4" name="备注" width="160"/>');
    });
  });

  describe('round-trip: parse(serialize(state)) deep-equals state', () => {
    it('Given a minimal card, When round-tripped, Then state is preserved', () => {
      // Given
      const original = emptyCard('往返');
      // When
      const roundTrip = parseScoreCard(serializeScoreCard(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a full card with rows/cells/libraries/properties/custom col, When round-tripped, Then state is preserved', () => {
      // Given
      const original: ScoreCardData = {
        name: '客户评分',
        remark: '信贷客户评分卡',
        properties: [{ name: 'salience', value: '10' }, { name: 'enabled', value: true }],
        weightSupport: false,
        attributeCol: { name: '属性', width: 200 },
        conditionCol: { name: '条件', width: 220 },
        scoreCol: { name: '分值', width: 180 },
        scoringType: 'sum',
        assignTarget: { type: 'none' },
        libraries: [
          { type: 'Variable', path: '/p/customer.vl.xml' },
          { type: 'Constant', path: '/p/tier.cl.xml' },
        ],
        cells: [
          attributeCell(2, 'age', '年龄', 'Integer', '客户.客户'),
          conditionCell(2, 'GreaterThen', inputValue('30')),
          scoreCell(2, '10'),
        ],
        rows: [{ rowNumber: 2, conditionRows: [] }],
        customCols: [{ colNumber: 4, name: '备注', width: 160 }],
      };
      // When
      const roundTrip = parseScoreCard(serializeScoreCard(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a weighted card with a variable assignTarget, When round-tripped, Then state is preserved', () => {
      // Given
      const original: ScoreCardData = {
        name: '加权评分',
        remark: '',
        properties: [],
        weightSupport: true,
        attributeCol: { name: '属性', width: 200 },
        conditionCol: { name: '条件', width: 220 },
        scoreCol: { name: '分值', width: 180 },
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
        libraries: [],
        cells: [
          attributeCell(2, 'age', '年龄', 'Integer', '客户.客户', '0.5'),
          conditionCell(2, 'Null'),
          scoreCell(2, '10'),
        ],
        rows: [{ rowNumber: 2, conditionRows: [] }],
        customCols: [],
      };
      // When
      const roundTrip = parseScoreCard(serializeScoreCard(original));
      // Then
      expect(roundTrip).toEqual(original);
    });

    it('Given a card with a custom scoringType + condition row, When round-tripped, Then state is preserved', () => {
      // Given
      const original: ScoreCardData = {
        name: '自定义评分',
        remark: '',
        properties: [],
        weightSupport: false,
        attributeCol: { name: '属性', width: 200 },
        conditionCol: { name: '条件', width: 220 },
        scoreCol: { name: '分值', width: 180 },
        scoringType: 'custom',
        customScoringBean: 'myBean',
        assignTarget: { type: 'none' },
        libraries: [],
        // attribute row at row 2, one condition row at row 3 (the next row slot)
        cells: [
          attributeCell(2, 'age', '年龄', 'Integer', '客户.客户'),
          conditionCell(2, 'GreaterThen', inputValue('30')),
          scoreCell(2, '10'),
          // condition row 3 has condition + score cells (no attribute cell)
          conditionCell(3, 'LessThen', inputValue('20')),
          scoreCell(3, '5'),
        ],
        rows: [{ rowNumber: 2, conditionRows: [{ rowNumber: 3 }] }],
        customCols: [],
      };
      // When
      const roundTrip = parseScoreCard(serializeScoreCard(original));
      // Then
      expect(roundTrip).toEqual(original);
    });
  });
});
