/**
 * serialize.ts — DecisionTable state → legacy `<decision-table>` XML string.
 *
 * Pure function. No React / DOM / jquery. Output is compatible with the
 * original jquery editor's DecisionTable.toXml() chain
 * (DecisionTable.ts → Column / Row / Cell → Join / Condition / InputType),
 * so the React rewrite persists into the exact same storage format the
 * backend (DecisionTableParser.java) already parses.
 *
 * The `<value>` of a condition and the four action variants are serialized by
 * the ruleforge model's serializeValue / serializeAction — reused verbatim —
 * because the wire format is identical (the ruleforge `<value>` element IS the
 * decision-table condition `<value>` element).
 */

import { serializeAction, serializeValue } from '../../ruleforge/model/serialize';
import type {
  Cell,
  CellContent,
  CellJoint,
  Column,
  DecisionTableData,
  LibraryImport,
  Row,
  TableProperty,
} from './types';

/** XML-escape a string for use in an attribute value or text content. */
function esc(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/'/g, '&apos;')
    .replace(/"/g, '&quot;');
}

/** XML-escape only the CDATA terminator so CDATA stays well-formed. */
function escCdata(s: string): string {
  return s.replace(/]]>/g, ']]&gt;');
}

/** `<remark><![CDATA[...]]></remark>` (empty remark still emits an empty CDATA). */
function serializeRemark(remark: string): string {
  return '<remark><![CDATA[' + escCdata(remark) + ']]></remark>';
}

/** `<decision-table name="value" …/>` attribute. Booleans → "true"/"false". */
function serializeProperty(prop: TableProperty): string {
  if (typeof prop.value === 'boolean') {
    return prop.name + '="' + (prop.value ? 'true' : 'false') + '"';
  }
  return prop.name + '="' + esc(prop.value) + '"';
}

/**
 * `<import-{type}-library path="…"/>` for one of the four library kinds.
 *
 * Exported so the script-decision-table model can reuse it — the library wire
 * format is identical between the two rule types.
 */
export function serializeLibrary(lib: LibraryImport): string {
  const tag =
    lib.type === 'Variable' ? 'import-variable-library'
      : lib.type === 'Constant' ? 'import-constant-library'
        : lib.type === 'Action' ? 'import-action-library'
          : 'import-parameter-library';
  return '<' + tag + ' path="' + esc(lib.path) + '"/>';
}

/**
 * `<col num width type [var-category var var-label datatype]/>`.
 *
 * Exported so the script-decision-table model can reuse it — the column wire
 * format is identical between the two rule types.
 */
export function serializeColumn(col: Column): string {
  let attrs =
    ' num="' + col.num + '"' +
    ' width="' + col.width + '"' +
    ' type="' + col.type + '"';
  if (col.variableName) {
    // var-category: parameter columns store "参数" (matches DecisionTable.toXml).
    attrs += ' var-category="' + esc(col.variableCategory === 'parameter' ? '参数' : col.variableCategory ?? '') + '"';
    attrs += ' var-label="' + esc(col.variableLabel ?? '') + '"';
    attrs += ' var="' + esc(col.variableName) + '"';
    attrs += ' datatype="' + esc(col.datatype ?? '') + '"';
  }
  return '<col' + attrs + '/>';
}

/**
 * `<row num height/>`.
 *
 * Exported so the script-decision-table model can reuse it — the row wire
 * format is identical between the two rule types.
 */
export function serializeRow(row: Row): string {
  return '<row num="' + row.num + '" height="' + row.height + '"/>';
}

/**
 * `<joint type><condition op>[<value/>]</condition>…</joint>`.
 * The `<value>` child is omitted when the condition op is Null / NotNull.
 */
function serializeJoint(joint: CellJoint): string {
  let xml = '<joint type="' + joint.type + '">';
  for (const cond of joint.conditions) {
    xml += '<condition op="' + esc(cond.op) + '">';
    if (cond.right && cond.op !== 'Null' && cond.op !== 'NotNull') {
      xml += serializeValue(cond.right);
    }
    xml += '</condition>';
  }
  xml += '</joint>';
  return xml;
}

/** The inner element of a `<cell>` (joint / action / nothing when empty). */
function serializeCellContent(content: CellContent): string {
  if ('empty' in content) return '';
  if ('joint' in content) return serializeJoint(content.joint);
  if ('action' in content) return serializeAction(content.action);
  return '';
}

/** `<cell row col rowspan>…content…</cell>`. */
function serializeCell(cell: Cell): string {
  return (
    '<cell row="' + cell.row + '" col="' + cell.col + '" rowspan="' + cell.rowspan + '">' +
    serializeCellContent(cell.content) +
    '</cell>'
  );
}

/**
 * Serialize the full decision-table document (including the XML declaration).
 *
 * Element order matches DecisionTable.toXml: remark, libraries, cells, rows,
 * cols (the original emits cells before rows before cols — preserved for
 * byte-level compatibility).
 */
export function serializeDecisionTable(dt: DecisionTableData): string {
  let xml = '<?xml version="1.0" encoding="UTF-8"?>';
  xml += '<decision-table';
  for (const prop of dt.properties) {
    xml += ' ' + serializeProperty(prop);
  }
  xml += '>';
  xml += serializeRemark(dt.remark);
  for (const lib of dt.libraries) {
    xml += serializeLibrary(lib);
  }
  for (const cell of dt.cells) {
    xml += serializeCell(cell);
  }
  for (const row of dt.rows) {
    xml += serializeRow(row);
  }
  for (const col of dt.columns) {
    xml += serializeColumn(col);
  }
  xml += '</decision-table>';
  return xml;
}
