/**
 * Pure data model for the script-decision-table (脚本式决策表) editor.
 *
 * Framework-free: no React, no DOM, no jquery, no handsontable, no CodeMirror.
 * This is the single source of truth the React rewrite
 * (`../react/ScriptDecisionTableEditor.tsx`) edits, and that serialize.ts /
 * parse.ts round-trip to/from the legacy `<script-decision-table>` XML the
 * backend parses
 * (server/lib/ruleforge-core/.../parse/table/ScriptDecisionTableParser.java).
 *
 * ── How it differs from the plain `<decision-table>` (../decisiontable/model)
 * ─────────────────────────────────────────────────────────────────────────
 * The two rule types share the *grid* shape (columns + rows + cells) and the
 * column-type vocabulary (Criteria / Assignment / ConsolePrint / ExecuteMethod).
 * They reuse the SAME {@link Column}, {@link Row}, {@link LibraryImport} types
 * (re-exported below). What differs is the CELL CONTENT:
 *
 *   decision-table cell → a structured `<joint>` of `<condition>`s OR one of
 *                          the structured actions (var-assign / console-print /
 *                          execute-method). Edited via ValueEditor/ActionEditor.
 *
 *   script-decision-table cell → a raw SCRIPT STRING (UL expression) wrapped in
 *                          CDATA inside `<script-cell>`. No structure, no
 *                          joint/condition/value tree. Edited via a textarea /
 *                          code input. One script per cell, per column type
 *                          the script KIND differs (Criteria→if-expression,
 *                          ConsolePrint→print-expression, Assignment/
 *                          ExecuteMethod→then-expression) but the wire format
 *                          is always just CDATA text.
 *
 * The XML wire format (matching the original jquery editor's toXml() in
 * ScriptDecisionTable.ts):
 *
 *   <script-decision-table>
 *     <import-variable-library path="..."/>
 *     <import-constant-library path="..."/>
 *     <import-action-library path="..."/>
 *     <import-parameter-library path="..."/>
 *     <script-cell row="0" col="0" rowspan="1"><![CDATA[...script...]]></script-cell>
 *     <row num="0" height="40"/>
 *     <col num="0" width="120" type="Criteria"
 *         var-category="..." var="..." var-label="..." datatype="..."/>
 *   </script-decision-table>
 *
 * Note: NO `<?xml ...?>` declaration and NO `<remark>` / property attributes —
 * the legacy jquery toXml() emits a bare `<script-decision-table>` root with
 * only libraries / script-cells / rows / cols as children. We preserve that
 * byte-level.
 *
 * ── Reuse ──────────────────────────────────────────────────────────────────
 * Column / Row / LibraryImport / ColumnType are imported verbatim from
 * ../decisiontable/model/types — the column and row wire format is identical
 * between the two rule types, so duplicating those types would drift.
 */

// Re-export the shared column / row / library types so consumers can import
// everything from this model without reaching across editor boundaries.
export type {
  Column,
  ColumnType,
  Row,
  LibraryImport,
} from '../../decisiontable/model/types';

import type {
  Column,
  LibraryImport,
  Row,
} from '../../decisiontable/model/types';

/**
 * The content of a `<script-cell>`. Always a raw script string (UL
 * expression). An EMPTY cell (user hasn't filled it) is `script: ''` and
 * serializes as an empty CDATA section (`<![CDATA[]]>`), matching the legacy
 * `cell.script || ''` fallback in ScriptDecisionTable.toXml().
 *
 * Unlike the decision-table CellContent this is NOT a discriminated union —
 * there is only one variant (a script string), so a plain string field is
 * enough. We keep it wrapped in an interface so future variants (e.g. a
 * structured fallback) can extend without breaking callers.
 */
export interface ScriptCellContent {
  /** The raw script / UL expression. Empty string for an unfilled cell. */
  script: string;
}

/** A `<script-cell row col rowspan>` element with its CDATA script content. */
export interface ScriptCell {
  /** `<script-cell row="…">`. 1-based row index in the legacy wire format. */
  row: number;
  /** `<script-cell col="…">`. 1-based col index in the legacy wire format. */
  col: number;
  /** `<script-cell rowspan="…">`. */
  rowspan: number;
  /** The CDATA-wrapped script string. */
  content: ScriptCellContent;
}

/**
 * The full `<script-decision-table>` document state.
 *
 * Compared to {@link ../../decisiontable/model/types.DecisionTableData}:
 *   - NO `remark` (script-decision-table has no remark element).
 *   - NO `properties` (no salience / enabled / debug / loop attributes).
 *   - `cells` are {@link ScriptCell} (CDATA script), not structured cells.
 *   - `columns` / `rows` / `libraries` reuse the decision-table types verbatim.
 */
export interface ScriptDecisionTableData {
  libraries: LibraryImport[];
  columns: Column[];
  rows: Row[];
  cells: ScriptCell[];
}
