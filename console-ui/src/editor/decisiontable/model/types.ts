/**
 * Pure data model for the decision-table (决策表) editor.
 *
 * Framework-free: no React, no DOM, no jquery, no handsontable. This is the
 * single source of truth the React rewrite (`../react/DecisionTableEditor.tsx`)
 * edits, and that serialize.ts / parse.ts round-trip to/from the legacy
 * `<decision-table>` XML the backend parses
 * (server/lib/ruleforge-core/.../parse/table/DecisionTableParser.java).
 *
 * The XML wire format (matching the original jquery editor's toXml chain in
 * DecisionTable.ts → Column / Row / Cell / Join / Condition / InputType):
 *
 *   <decision-table salience="10" enabled="true" ...>
 *     <remark><![CDATA[...]]></remark>
 *     <import-variable-library path="..."/>
 *     <import-constant-library path="..."/>
 *     <import-action-library path="..."/>
 *     <import-parameter-library path="..."/>
 *     <cell row="0" col="0" rowspan="1">
 *       ...cell content...                 ← see CellContent below
 *     </cell>
 *     <row num="0" height="40"/>
 *     <col num="0" width="120" type="Criteria"
 *         var-category="..." var="..." var-label="..." datatype="..."/>
 *   </decision-table>
 *
 * Cell content (exactly one of these, picked by the column's ColumnType):
 *   - Criteria column  →  <joint type="and"><condition op="..."><value/></condition>...</joint>
 *   - Assignment column   →  <var-assign ...><value/></var-assign>     (Action, var-assign)
 *   - ConsolePrint column →  <console-print><value/></console-print>   (Action, console-print)
 *   - ExecuteMethod column → <execute-method ...>...</execute-method>  (Action, execute-method)
 *
 * Reuse: the right-hand `<value>` of a condition and the four action variants
 * reuse the ruleforge model types verbatim (ValueExpr / Action from
 * ../../ruleforge/model/types), so the React editor can reuse
 * ValueEditor / ActionEditor unchanged.
 */

import type { Action, ValueExpr } from '../../ruleforge/model/types';

/** Column type — maps 1:1 to backend ColumnType enum. */
export type ColumnType = 'Criteria' | 'Assignment' | 'ConsolePrint' | 'ExecuteMethod';

/** A `<col>` element. width/num are present; the var-* fields only on Criteria columns. */
export interface Column {
  /** `<col num="…">`. */
  num: number;
  /** `<col type="…">` — drives the cell-content variant. */
  type: ColumnType;
  /** `<col width="…">`. */
  width: number;
  /** `<col var-category="…">` — only for Criteria columns. */
  variableCategory?: string;
  /** `<col var-label="…">` — only for Criteria columns. */
  variableLabel?: string;
  /** `<col var="…">` — only for Criteria columns. */
  variableName?: string;
  /** `<col datatype="…">` — only for Criteria columns. */
  datatype?: string;
}

/** A `<row num="…" height="…"/>` element. */
export interface Row {
  num: number;
  height: number;
}

/**
 * The condition inside a Criteria cell's `<joint>`.
 *
 * `op` is the ComparisonOperator value (GreaterThen / Equals / …), the same
 * vocabulary as the ruleforge atom `op`. The right-hand `<value>` is omitted
 * for Null / NotNull (mirrors ruleforge serialize.ts).
 *
 * Note: the LEFT side of the condition is the column's variable, so a
 * `<condition>` only serializes `op` + an optional `<value>` — unlike a
 * ruleforge `<atom>` it carries no `<left>` child.
 */
export interface CellCondition {
  op: string;
  /** Omitted when op is Null / NotNull. */
  right?: ValueExpr;
}

/**
 * A single junction inside a Criteria cell (`<joint type="and|or">`).
 *
 * The original editor nested joints recursively, but in practice a decision-
 * table condition cell holds a flat list of conditions under one junction.
 * We keep the recursive shape for fidelity, but the editor surfaces it as a
 * flat condition list (TODO: nested joint UI).
 */
export interface CellJoint {
  type: 'and' | 'or';
  conditions: CellCondition[];
}

/**
 * The content of a `<cell>`. Discriminated by the parent column's type:
 *   - Criteria      → joint (a `<joint>` of `<condition>`s)
 *   - Assignment    → action (var-assign)
 *   - ConsolePrint  → action (console-print)
 *   - ExecuteMethod → action (execute-method)
 *
 * An EMPTY cell (the user hasn't filled it) is represented by `empty: true`
 * and serializes as an empty `<cell>…</cell>` (no inner element). The parse
 * path produces `empty: true` when a cell element has no recognized child.
 */
export type CellContent =
  | { empty: true }
  | { joint: CellJoint }
  | { action: Action };

/** A `<cell row col rowspan>` element with its content. */
export interface Cell {
  /** `<cell row="…">`. 1-based row index in the original editor. */
  row: number;
  /** `<cell col="…">`. 1-based col index in the original editor. */
  col: number;
  /** `<cell rowspan="…">`. */
  rowspan: number;
  /** The cell's content (empty / joint / action). */
  content: CellContent;
}

/** A `<decision-table>` attribute (other than name). Same shape as ruleforge RuleProperty. */
export interface TableProperty {
  /** XML attribute name on `<decision-table>`: salience / effective-date / expires-date / enabled / debug / loop. */
  name: string;
  /** String or boolean. Booleans serialize as "true"/"false". */
  value: string | boolean;
}

/** Library import descriptor (one of the four `<import-*-library>` kinds). */
export interface LibraryImport {
  type: 'Variable' | 'Constant' | 'Action' | 'Parameter';
  path: string;
}

/** The full `<decision-table>` document state. */
export interface DecisionTableData {
  remark: string;
  libraries: LibraryImport[];
  /** `<decision-table>` attributes. */
  properties: TableProperty[];
  columns: Column[];
  rows: Row[];
  cells: Cell[];
}
