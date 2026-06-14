/**
 * Pure data model for the scorecard (评分卡) editor.
 *
 * Framework-free: no React, no DOM, no jquery. This is the single source of
 * truth the React rewrite (`../react/ScoreCardEditor.tsx`) edits, and that
 * serialize.ts / parse.ts round-trip to/from the legacy `<scorecard>` XML the
 * backend parses
 * (server/lib/ruleforge-core/.../parse/scorecard/ScorecardParser.java).
 *
 * The XML wire format (matching the original jquery editor's toXml chain in
 * ScoreCardTable.ts → PropertyConfig / TableAction / AttributeCol /
 * ConditionCol / ScoreCol → AttributeRow → Cell → ConditionCell / ScoreCell /
 * CustomCell):
 *
 *   <scorecard weight-support="true|false"
 *              name="..." salience="10" effective-date="..." enabled="true" ...
 *              attr-col-width="200" attr-col-name="属性"
 *              condition-col-width="220" condition-col-name="条件"
 *              score-col-width="180" score-col-name="分值"
 *              scoring-type="sum|weightsum|custom" assign-target-type="..."
 *              [custom-scoring-bean="..."]
 *              [assign-target var/var-label/datatype ...]>
 *     <remark><![CDATA[...]]></remark>
 *     <import-variable-library path="..."/>
 *     <import-constant-library path="..."/>
 *     <import-action-library path="..."/>
 *     <import-parameter-library path="..."/>
 *     <card-cell type="attribute" row="2" col="1"
 *                [weight="..."] var="..." var-label="..." datatype="..."
 *                category="..."></card-cell>
 *     <card-cell type="condition" row="2" col="2"><joint>...</joint></card-cell>
 *     <card-cell type="score" row="2" col="3"><value/></card-cell>
 *     <card-cell type="custom" row="2" col="4"><value/></card-cell>
 *     ...
 *     <attribute-row row-number="2">
 *       <condition-row row-number="3"/>
 *       ...
 *     </attribute-row>
 *     ...
 *     <custom-col col-number="4" name="..." width="160"/>
 *     ...
 *   </scorecard>
 *
 * Key shape notes (mirrors the jquery editor + backend):
 *   - Cells are a FLAT list keyed by (row, col). Col 1 = attribute, col 2 =
 *     condition, col 3 = score, col 4+ = custom columns.
 *   - `row-number` in `<attribute-row>` / `<condition-row>` mirrors the SAME
 *     row index as the cells' `row` attribute. An attribute row at row 2 with
 *     one extra condition row bumps the next attribute row to row 4 (one slot
 *     for the attribute row, one for the condition row). See Row.getRowNumber.
 *   - The first attribute row's cells live at row=2 (the editor reserves
 *     row 1 for the header conceptually; the original emits from row 2).
 *   - An attribute cell carries the variable binding (var / var-label /
 *     datatype / category) plus optional weight (only when weight-support).
 *   - A condition cell carries a `<joint>` of `<condition>`s — same wire format
 *     as a decision-table Criteria cell, so the ruleforge ValueEditor / Joint
 *     types are reused.
 *   - A score / custom cell carries a single `<value>` (a ruleforge ValueExpr).
 *
 * Reuse: the condition `<value>` and the score/custom `<value>` reuse the
 * ruleforge model types verbatim (ValueExpr from
 * ../../ruleforge/model/types), so the React editor reuses ValueEditor
 * unchanged.
 */

import type { ValueExpr } from '../../ruleforge/model/types';

/** Cell type — maps 1:1 to backend CellType enum. */
export type CardCellType = 'attribute' | 'condition' | 'score' | 'custom';

/**
 * The condition inside a condition cell's `<joint>`.
 *
 * `op` is the ComparisonOperator value (GreaterThen / Equals / …), the same
 * vocabulary as the ruleforge atom `op`. The right-hand `<value>` is omitted
 * for Null / NotNull (mirrors ruleforge serialize.ts).
 *
 * The LEFT side of the condition is the attribute cell's variable, so a
 * `<condition>` only serializes `op` + an optional `<value>` — unlike a
 * ruleforge `<atom>` it carries no `<left>` child.
 */
export interface CardCondition {
  op: string;
  /** Omitted when op is Null / NotNull. */
  right?: ValueExpr;
}

/**
 * A single junction inside a condition cell (`<joint type="and|or">`).
 * The editor surfaces it as a flat condition list (one `<joint>` per cell).
 */
export interface CardJoint {
  type: 'and' | 'or';
  conditions: CardCondition[];
}

/**
 * A single `<card-cell>`. The `type` field discriminates which optional
 * fields are populated:
 *   - attribute → variable binding (var / var-label / datatype / category)
 *                 + optional weight
 *   - condition → joint
 *   - score     → value (a numeric Input or a Variable/Constant expr)
 *   - custom    → value (same wire format as score)
 */
export interface CardCell {
  /** `<card-cell type="…">`. */
  type: CardCellType;
  /** `<card-cell row="…">`. */
  row: number;
  /** `<card-cell col="…">`. 1 = attribute, 2 = condition, 3 = score, 4+ = custom. */
  col: number;
  /** attribute cell: variable category. */
  category?: string;
  /** attribute cell: variable name. */
  variableName?: string;
  /** attribute cell: variable label. */
  variableLabel?: string;
  /** attribute cell: datatype (String / Integer / BigDecimal / …). */
  datatype?: string;
  /** attribute cell: weight (only when weight-support). */
  weight?: string;
  /** condition cell: the joint of conditions. */
  joint?: CardJoint;
  /** score / custom cell: the value expression. */
  value?: ValueExpr;
}

/** A `<custom-col col-number name width/>` element. */
export interface CustomCol {
  /** `<custom-col col-number="…">`. 4-based (cols 1/2/3 are the fixed cols). */
  colNumber: number;
  name: string;
  width: number;
}

/**
 * A `<attribute-row row-number="…">` element.
 *
 * The condition rows nested under it are siblings in the SAME row-number
 * space as the cells. The editor keeps the nesting for XML fidelity but the
 * cells themselves carry all the data.
 */
export interface AttributeRow {
  /** `<attribute-row row-number="…">`. Matches the row index of the cells. */
  rowNumber: number;
  /** Nested `<condition-row row-number="…"/>` siblings. */
  conditionRows: ConditionRow[];
}

/** A `<condition-row row-number="…"/>` nested under an attribute row. */
export interface ConditionRow {
  /** `<condition-row row-number="…">`. Matches the row index of the cells. */
  rowNumber: number;
}

/** Library import descriptor (one of the four `<import-*-library>` kinds). */
export interface LibraryImport {
  type: 'Variable' | 'Constant' | 'Action' | 'Parameter';
  path: string;
}

/** A `<scorecard>` attribute (other than name). Same shape as ruleforge RuleProperty. */
export interface CardProperty {
  /** XML attribute name: salience / effective-date / expires-date / enabled / debug. */
  name: string;
  /** String or boolean. Booleans serialize as "true"/"false". */
  value: string | boolean;
}

/** 得分计算方式 — maps 1:1 to backend ScoringType enum. */
export type ScoringType = 'sum' | 'weightsum' | 'custom';

/** 得分赋值对象 — maps 1:1 to backend AssignTargetType enum. */
export type AssignTargetType = 'variable' | 'parameter' | 'none';

/**
 * The score-assignment target (the "将得分值赋给" setting in TableAction).
 *
 * When `type === 'variable'` the assignment carries a `<value type="Variable">`
 * (a single variable); when `'parameter'` it carries a `<value type="Parameter">`.
 * The wire format is the same ruleforge `<value>` element emitted as an XML
 * attribute-less payload after `assign-target-type` — see TableAction.toXml.
 */
export interface AssignTarget {
  type: AssignTargetType;
  /** Only present when type !== 'none'. */
  value?: ValueExpr;
}

/** The three fixed column header configs (name + width). */
export interface FixedColConfig {
  name: string;
  width: number;
}

/**
 * The full `<scorecard>` document state.
 */
export interface ScoreCardData {
  /** 评分卡名称 (required — empty string rejected on save). */
  name: string;
  /** 备注CDATA. */
  remark: string;
  /** `<scorecard>` attributes other than name. */
  properties: CardProperty[];
  /** 是否支持权重. */
  weightSupport: boolean;
  /** 三列固定表头配置. */
  attributeCol: FixedColConfig;
  conditionCol: FixedColConfig;
  scoreCol: FixedColConfig;
  /** 得分计算方式. */
  scoringType: ScoringType;
  /** 自定义得分计算 Bean ID (only when scoringType === 'custom'). */
  customScoringBean?: string;
  /** 得分赋值对象. */
  assignTarget: AssignTarget;
  /** 库导入. */
  libraries: LibraryImport[];
  /** 扁平单元格列表 keyed by (row, col). */
  cells: CardCell[];
  /** 属性行 (carries nested condition-row metadata). */
  rows: AttributeRow[];
  /** 自定义列. */
  customCols: CustomCol[];
}
