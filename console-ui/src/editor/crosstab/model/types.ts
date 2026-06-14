/**
 * Pure data model for the crosstab (交叉决策表) editor.
 *
 * Framework-free: no React, no DOM, no jquery, no handsontable. This is the
 * single source of truth the React rewrite (`../react/CrossTableEditor.tsx`)
 * edits, and that serialize.ts / parse.ts round-trip to/from the legacy
 * `<crosstab>` XML the backend parses
 * (server/lib/ruleforge-core/.../parse/crosstab/CrosstabParser.java).
 *
 * A crosstab is a 2-D decision matrix:
 *   - The TOP region holds condition rows (each binds a variable/parameter and
 *     generates the column-axis condition cells).
 *   - The LEFT region holds condition columns (each binds a variable/parameter
 *     and generates the row-axis condition cells).
 *   - The intersection of a LEFT row and a TOP column is a VALUE cell: a plain
 *     `<value>` expression that, at runtime, gets assigned to the crosstab's
 *     single assignment-target variable.
 *
 * The wire format (matching the original jquery editor's CrossTable.toXml chain
 * in CrossTable.ts → HeaderCell / Row / Column / ConditionCell / Cell and the
 * backend CrosstabParser):
 *
 *   <crosstab salience="10" enabled="true" ...
 *             assign-target-type="variable" var-category="..." var="..."
 *             var-label="..." datatype="...">
 *     <remark><![CDATA[...]]></remark>
 *     <header rowspan="1" colspan="1"><![CDATA[TOP/LEFT]]></header>
 *     <import-variable-library path="..."/>
 *     <import-constant-library path="..."/>
 *     <import-action-library path="..."/>
 *     <import-parameter-library path="..."/>
 *     <row number="1" type="top" bundle-data-type="variable" var-category="..."
 *          var="..." var-label="..." datatype="..."></row>
 *     <row number="2" type="left"></row>
 *     <column number="1" type="left" bundle-data-type="variable" ...></column>
 *     <column number="2" type="top"></column>
 *     <condition-cell row="1" col="1" rowspan="1" colspan="1">
 *       <joint type="and"><condition op="..."><value/></condition>...</joint>
 *     </condition-cell>
 *     <value-cell row="2" col="2"><value .../></value-cell>
 *   </crosstab>
 *
 * Reuse: the `<value>` of a value-cell and of a condition's right-hand side
 * reuse the ruleforge model's ValueExpr verbatim
 * (../../ruleforge/model/types), so the React editor reuses ValueEditor.
 * The condition inside a `<joint>` reuses the same CellJoint shape the
 * decision-table model defines (../../decisiontable/model/types) — a flat
 * junction of `<condition op><value/></condition>` entries where the LEFT side
 * is the row/column's bound variable.
 */

import type { ValueExpr } from '../../ruleforge/model/types';

/** A `<row>` element. `type` decides whether it is a condition (top) or value (left) row. */
export type CrossRowType = 'top' | 'left';

/** A `<column>` element. `type` decides whether it is a condition (left) or value (top) column. */
export type CrossColType = 'top' | 'left';

/**
 * The variable/parameter bundle bound to a condition row or condition column.
 *
 * A condition row (`<row type="top">`) binds a variable that becomes the LEFT
 * side of every condition cell generated in that row; a condition column
 * (`<column type="left">`) does the same down its column. The bundle-data-type
 * selects the attribute set:
 *   - variable  → var-category / var / var-label / datatype
 *   - parameter → var-category="参数" / var / var-label / datatype
 *
 * Matches BaseRowCol.bundleDataToXml in the original editor.
 */
export interface BundleData {
  /** `bundle-data-type` attribute: "variable" or "parameter". */
  type: 'variable' | 'parameter';
  /** `var-category` attribute. For parameters the editor emits "参数". */
  variableCategory: string;
  /** `var-label` attribute. */
  variableLabel?: string;
  /** `var` attribute. */
  variableName?: string;
  /** `datatype` attribute. */
  datatype?: string;
}

/**
 * A `<row number type [bundle-data]>` element.
 *
 * Top rows (condition rows) carry a bundle; left rows (value rows) do not.
 */
export interface CrossRow {
  /** `<row number="…">`. 1-based row index in the wire format. */
  number: number;
  /** `<row type="…">` — top = condition row, left = value row. */
  type: CrossRowType;
  /** Only present on top rows. */
  bundleData?: BundleData;
}

/**
 * A `<column number type [bundle-data]>` element.
 *
 * Left columns (condition columns) carry a bundle; top columns (value columns) do not.
 */
export interface CrossColumn {
  /** `<column number="…">`. 1-based column index in the wire format. */
  number: number;
  /** `<column type="…">` — left = condition column, top = value column. */
  type: CrossColType;
  /** Only present on left columns. */
  bundleData?: BundleData;
}

/**
 * The `<header rowspan colspan><![CDATA[text]]></header>` element — the
 * top-left corner of the crosstab grid. rowspan/colspan describe how many
 * condition rows / condition columns the header spans (the structural origin).
 */
export interface HeaderCell {
  rowspan: number;
  colspan: number;
  text: string;
}

/**
 * A single condition inside a condition cell's `<joint>`.
 *
 * Identical shape to the decision-table CellCondition: `op` is the
 * ComparisonOperator value, and the right-hand `<value>` is omitted for
 * Null / NotNull. The LEFT side of the condition is the row's / column's
 * bound variable, so a `<condition>` only serializes `op` + an optional value.
 */
export interface CrossCondition {
  op: string;
  /** Omitted when op is Null / NotNull. */
  right?: ValueExpr;
}

/**
 * A junction inside a condition cell (`<joint type="and|or">`). Kept as a flat
 * list (the original editor in practice holds one junction per cell).
 */
export interface CrossJoint {
  type: 'and' | 'or';
  conditions: CrossCondition[];
}

/**
 * The content of a condition-cell. Empty when the user hasn't configured a
 * condition; otherwise a single joint.
 */
export type ConditionCellContent =
  | { empty: true }
  | { joint: CrossJoint };

/**
 * A `<condition-cell row col rowspan colspan>` element. These sit at the
 * intersection of a condition row and a condition column (i.e. the top band
 * or the left band) and hold a `<joint>` of conditions.
 */
export interface ConditionCrossCell {
  /** `<condition-cell row="…">`. 1-based. */
  row: number;
  /** `<condition-cell col="…">`. 1-based. */
  col: number;
  /** `<condition-cell rowspan="…">`. */
  rowspan: number;
  /** `<condition-cell colspan="…">`. */
  colspan: number;
  /** The condition joint (or empty). */
  content: ConditionCellContent;
}

/**
 * A `<value-cell row col><value/></value-cell>` element — the cross-cell at
 * the intersection of a LEFT row and a TOP column. Its `<value>` is assigned
 * to the crosstab's assignment-target variable at runtime.
 */
export interface ValueCrossCell {
  /** `<value-cell row="…">`. 1-based. */
  row: number;
  /** `<value-cell col="…">`. 1-based. */
  col: number;
  /** The value expression (empty when the user hasn't filled the cell). */
  value?: ValueExpr;
}

/**
 * The crosstab's assignment-target descriptor (the four `<crosstab>`
 * attributes `assign-target-type` / `var-category` / `var` / `var-label` /
 * `datatype`). At runtime, each value-cell's `<value>` is assigned to this
 * target. `type` picks variable or parameter (matches
 * CrossCellVariableBundle.toXml in the original editor).
 */
export interface AssignTarget {
  /** `assign-target-type` attribute: "variable" or "parameter". */
  type: 'variable' | 'parameter';
  /** `var-category` attribute. For parameters the editor emits "参数". */
  variableCategory?: string;
  /** `var` attribute. */
  variableName?: string;
  /** `var-label` attribute. */
  variableLabel?: string;
  /** `datatype` attribute. */
  datatype?: string;
}

/** A `<crosstab>` attribute (other than the assign-target group). */
export interface CrosstabProperty {
  /** XML attribute name on `<crosstab>`: salience / effective-date / expires-date / enabled / debug. */
  name: string;
  /** String or boolean. Booleans serialize as "true"/"false". */
  value: string | boolean;
}

/** Library import descriptor (one of the four `<import-*-library>` kinds). */
export interface LibraryImport {
  type: 'Variable' | 'Constant' | 'Action' | 'Parameter';
  path: string;
}

/** The full `<crosstab>` document state. */
export interface CrossTableData {
  remark: string;
  /** `<crosstab>` attributes (salience / dates / enabled / debug). */
  properties: CrosstabProperty[];
  /** The cross-cell assignment target. Optional — required before save. */
  assignTarget?: AssignTarget;
  /** The top-left corner header cell. */
  header: HeaderCell;
  libraries: LibraryImport[];
  rows: CrossRow[];
  columns: CrossColumn[];
  /** Condition cells (top band + left band). */
  conditionCells: ConditionCrossCell[];
  /** Value cells (the cross-cell matrix). */
  valueCells: ValueCrossCell[];
}
