/**
 * serialize.ts — ScriptDecisionTable state → legacy `<script-decision-table>`
 * XML string.
 *
 * Pure function. No React / DOM / jquery / CodeMirror. Output is byte-
 * compatible with the original jquery editor's ScriptDecisionTable.toXml()
 * (ScriptDecisionTable.ts), so the React rewrite persists into the exact same
 * storage format the backend (ScriptDecisionTableParser.java) already parses.
 *
 * ── Differences from ../decisiontable/model/serialize.ts ───────────────────
 *   - Root tag is `<script-decision-table>` (not `<decision-table>`).
 *   - NO `<?xml version="1.0"?>` declaration (legacy toXml emits a bare root).
 *   - NO `<remark>` element and NO property attributes.
 *   - Cells are `<script-cell>` with a CDATA-wrapped script string, not
 *     `<cell>` with joint/action children.
 *   - Column / row / library serialization is IDENTICAL (same `<col>` / `<row>`
 *     / `<import-*-library>` wire format), so we delegate to the decision-table
 *     helpers for those.
 */

import {
  serializeColumn,
  serializeLibrary,
  serializeRow,
} from '../../decisiontable/model/serialize';
import type { ScriptCell, ScriptDecisionTableData } from './types';

/** 在 CDATA 内,`]]>` 会提前终止段。标准做法:拆成多段 CDATA,parse 时 textContent 自动拼接还原。 */
function escCdata(s: string): string {
  return s.replace(/]]>/g, ']]]]><![CDATA[>');
}

/**
 * `<script-cell row col rowspan><![CDATA[...]]></script-cell>`.
 * Empty script still emits an empty CDATA section (matches `cell.script || ''`).
 */
function serializeScriptCell(cell: ScriptCell): string {
  return (
    '<script-cell row="' + cell.row + '" col="' + cell.col + '" rowspan="' + cell.rowspan + '">' +
    '<![CDATA[' + escCdata(cell.content.script) + ']]>' +
    '</script-cell>'
  );
}

/**
 * Serialize the full script-decision-table document.
 *
 * Element order matches ScriptDecisionTable.toXml(): libraries, script-cells,
 * rows, cols. No XML declaration, no remark, no property attributes — those
 * are decision-table-only and absent from the script variant.
 */
export function serializeScriptDecisionTable(sdt: ScriptDecisionTableData): string {
  let xml = '<script-decision-table>';
  for (const lib of sdt.libraries) {
    xml += serializeLibrary(lib);
  }
  for (const cell of sdt.cells) {
    xml += serializeScriptCell(cell);
  }
  for (const row of sdt.rows) {
    xml += serializeRow(row);
  }
  for (const col of sdt.columns) {
    xml += serializeColumn(col);
  }
  xml += '</script-decision-table>';
  return xml;
}
