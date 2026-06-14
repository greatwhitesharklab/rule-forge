/**
 * Pure data model for the complex scorecard (复杂评分卡) editor.
 *
 * Framework-free: no React, no DOM, no jquery. This is the single source of
 * truth the React rewrite (`../react/ComplexScoreCardEditor.tsx`) edits, and
 * that serialize.ts / parse.ts round-trip to/from the legacy
 * `<complex-scorecard>` XML the backend parses
 * (server/lib/ruleforge-core/.../parse/scorecard/ComplexScorecardParser.java).
 *
 * The XML wire format (matching the original jquery editor's toXml chain in
 * ComplexScoreCard.ts → TableAction / HeaderRow → ConditionColumn /
 * ActionColumn → ScoreCardRow → ConditionCell / ActionCell):
 *
 *   <complex-scorecard scoring-type="sum|weightsum|custom"
 *                      assign-target-type="variable|parameter|none"
 *                      [custom-scoring-bean="..."]
 *                      [salience="10" effective-date="..." enabled="true" ...]
 *                      [var="..." var-label="..." datatype="..."
 *                       var-category="..."]>
 *     <remark><![CDATA[...]]></remark>
 *     <import-variable-library path="..."/>
 *     <import-constant-library path="..."/>
 *     <import-action-library path="..."/>
 *     <import-parameter-library path="..."/>
 *     <cell row="0" col="2" rowspan="1"></cell>
 *     <cell row="0" col="1" rowspan="1"><joint>...</joint></cell>
 *     <cell row="0" col="0" rowspan="1"
 *           var="..." var-label="..." datatype="..."><joint>...</joint></cell>
 *     ...
 *     <row num="0" height="40"/>
 *     <row num="1" height="40"/>
 *     <col num="0" width="150" type="Criteria" var-category="..."/>
 *     <col num="1" width="150" type="Score"/>
 *     <col num="2" width="120" type="Custom" custom-label="..."/>
 *   </complex-scorecard>
 *
 * Differences vs the plain scorecard model
 * (../../scorecard/model/types.ts):
 *
 *  1. No `name` attribute on the root (the file path is the identity).
 *  2. No `<card-cell>` + `<attribute-row>`/`<condition-row>` nesting. The
 *     complex scorecard uses a flat `<row>` / `<col>` / `<cell>` matrix — the
 *     same wire format as the decision table — with `rowspan` carrying the
 *     vertical-merge semantics that the plain scorecard expresses via nested
 *     condition rows.
 *  3. Multiple condition columns (`Criteria`) AND multiple action columns
 *     (`Score` and `Custom`) — the plain scorecard has exactly one attribute
 *     column, one condition column, one score column.
 *  4. A condition cell carries the variable binding (var / var-label /
 *     datatype) ON THE CELL itself, not on the column. Each condition cell in
 *     a Criteria column selects which variable it tests; the column only
 *     binds a variable-category menu.
 *  5. Row/col numbering starts at 0 (not 2 like the plain scorecard).
 *
 * Reuse: the `<joint>` wire format inside a condition cell, the `<value>`
 * inside a score/custom cell, and the assign-target `<value>` payload all
 * reuse the ruleforge model (ValueExpr from ../../ruleforge/model/types), so
 * the React editor reuses ValueEditor unchanged. The CardCondition / CardJoint
 * / CardProperty / LibraryImport / ScoringType / AssignTargetType /
 * AssignTarget shapes are imported verbatim from the scorecard model — the
 * wire format for those pieces is identical.
 */

import type { ValueExpr } from '../../ruleforge/model/types';
// Re-export the shared pieces so callers can import everything from this module
// without reaching into the scorecard model directly.
export type {
  AssignTarget,
  AssignTargetType,
  CardCondition,
  CardJoint,
  CardProperty,
  LibraryImport,
  ScoringType,
} from '../../scorecard/model/types';

/** Column type — maps 1:1 to backend ComplexColumnType enum. */
export type ComplexColType = 'Criteria' | 'Score' | 'Custom';

/**
 * A `<col num width type var-category? custom-label?>` element.
 *
 * `var-category` is only present on Criteria columns (the variable/parameter
 * category menu bound to the column). `custom-label` is only present on Custom
 * action columns (the user-supplied header name).
 */
export interface ComplexCol {
  /** `<col num="…">`. 0-based, matches the cells' `col` attribute. */
  num: number;
  /** `<col width="…">`. */
  width: number;
  /** `<col type="Criteria|Score|Custom">`. */
  type: ComplexColType;
  /** Criteria column: the variable/parameter category. */
  variableCategory?: string;
  /** Custom action column: the user-supplied header label. */
  customLabel?: string;
}

/** A `<row num height/>` element. */
export interface ComplexRow {
  /** `<row num="…">`. 0-based, matches the cells' `row` attribute. */
  num: number;
  /** `<row height="…">`. Pixel height of the row. */
  height: number;
}

/**
 * A `<cell row col rowspan …>…</cell>` element.
 *
 * The cell discriminates by the COLUMN it lives in (looked up via `col` →
 * ComplexCol.type):
 *   - in a Criteria column → variable binding (var / var-label / datatype) +
 *     a `<joint>` of conditions
 *   - in a Score column    → a single `<value>` (the score)
 *   - in a Custom column   → a single `<value>`
 *
 * `rowspan` carries the vertical merge (a condition cell spanning N rows).
 */
export interface ComplexCell {
  /** `<cell row="…">`. */
  row: number;
  /** `<cell col="…">`. Matches a ComplexCol.num. */
  col: number;
  /** `<cell rowspan="…">`. 1 = single row. */
  rowspan: number;
  /** Criteria cell: variable name. */
  variableName?: string;
  /** Criteria cell: variable label. */
  variableLabel?: string;
  /** Criteria cell: datatype (String / Integer / BigDecimal / …). */
  datatype?: string;
  /** Criteria cell: the joint of conditions. */
  joint?: import('../../scorecard/model/types').CardJoint;
  /** Score / Custom cell: the value expression. */
  value?: ValueExpr;
}

/**
 * The full `<complex-scorecard>` document state.
 */
export interface ComplexScoreCardData {
  /** 备注CDATA. */
  remark: string;
  /** `<complex-scorecard>` attributes other than the structural ones. */
  properties: import('../../scorecard/model/types').CardProperty[];
  /** 得分计算方式. */
  scoringType: import('../../scorecard/model/types').ScoringType;
  /** 自定义得分计算 Bean ID (only when scoringType === 'custom'). */
  customScoringBean?: string;
  /** 得分赋值对象. */
  assignTarget: import('../../scorecard/model/types').AssignTarget;
  /** 库导入. */
  libraries: import('../../scorecard/model/types').LibraryImport[];
  /** 列定义 (Criteria + Score + Custom). */
  cols: ComplexCol[];
  /** 行定义 (num + height). */
  rows: ComplexRow[];
  /** 扁平单元格列表 keyed by (row, col). */
  cells: ComplexCell[];
}
