/**
 * serialize.ts — ComplexScoreCard state → legacy `<complex-scorecard>` XML string.
 *
 * Pure function. No React / DOM / jquery. Output is compatible with the
 * original jquery editor's ComplexScoreCard.toXml() chain
 * (ComplexScoreCard.ts → TableAction → cells → rows → cols), so the React
 * rewrite persists into the exact same storage format the backend
 * (ComplexScorecardParser.java) already parses.
 *
 * Element order (mirrors ComplexScoreCard.toXml):
 *   1. XML declaration
 *   2. <complex-scorecard scoring-type assign-target-type [custom-scoring-bean]
 *      [properties] [assign-target var/var-label/datatype/var-category]>
 *   3.   <remark><![CDATA[...]]></remark>
 *   4.   <import-{type}-library path="…"/> ... (one per library)
 *   5.   <cell row col rowspan ...>...</cell> ... (all cells, row-then-col order)
 *   6.   <row num height/> ...
 *   7.   <col num width type .../> ...
 *   8. </complex-scorecard>
 *
 * The `<joint>` of a condition cell and the `<value>` of a score / custom
 * cell / assign-target are serialized by the ruleforge model's serializeValue
 * — reused verbatim — because the wire format is identical to the plain
 * scorecard.
 */

import { serializeValue } from '../../ruleforge/model/serialize';
import type {
  AssignTarget,
  CardJoint,
  CardProperty,
  LibraryImport,
} from '../../scorecard/model/types';
import type {
  ComplexCell,
  ComplexCol,
  ComplexColType,
  ComplexScoreCardData,
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

/** `<complex-scorecard foo="value" …/>` attribute. Booleans → "true"/"false". */
function serializeProperty(prop: CardProperty): string {
  if (typeof prop.value === 'boolean') {
    return prop.name + '="' + (prop.value ? 'true' : 'false') + '"';
  }
  return prop.name + '="' + esc(prop.value) + '"';
}

/** `<import-{type}-library path="…"/>` for one of the four library kinds. */
function serializeLibrary(lib: LibraryImport): string {
  const tag =
    lib.type === 'Variable' ? 'import-variable-library'
      : lib.type === 'Constant' ? 'import-constant-library'
        : lib.type === 'Action' ? 'import-action-library'
          : 'import-parameter-library';
  return '<' + tag + ' path="' + esc(lib.path) + '"/>';
}

/** `<joint type><condition op>[<value/>]</condition>…</joint>`. */
function serializeJoint(joint: CardJoint): string {
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

/**
 * `<cell row col rowspan …>…</cell>`.
 *
 * Wire format (mirrors ComplexScoreCard.toXml cells section):
 *   - in a Criteria column → var / var-label / datatype attributes + `<joint>`
 *     body
 *   - in a Score / Custom column → empty attrs + `<value>` body
 *
 * The caller resolves the column type; we don't carry it on the cell.
 */
function serializeCell(cell: ComplexCell, colType: ComplexColType | undefined): string {
  let attrs =
    ' row="' + cell.row + '"' +
    ' col="' + cell.col + '"' +
    ' rowspan="' + (cell.rowspan ?? 1) + '"';

  let body = '';

  if (colType === 'Criteria') {
    attrs += ' var-label="' + esc(cell.variableLabel ?? '') + '"';
    attrs += ' var="' + esc(cell.variableName ?? '') + '"';
    attrs += ' datatype="' + esc(cell.datatype ?? 'String') + '"';
    if (cell.joint) {
      body = serializeJoint(cell.joint);
    }
  } else {
    // Score / Custom: a single <value> body (may be empty for an unset cell).
    if (cell.value) {
      body = serializeValue(cell.value);
    }
  }

  return '<cell' + attrs + '>' + body + '</cell>';
}

/** `<row num height/>`. */
function serializeRow(row: { num: number; height: number }): string {
  return '<row num="' + row.num + '" height="' + row.height + '"/>';
}

/** `<col num width type [var-category] [custom-label]/>`. */
function serializeCol(col: ComplexCol): string {
  let xml =
    '<col num="' + col.num + '"' +
    ' width="' + col.width + '"' +
    ' type="' + col.type + '"';
  if (col.variableCategory) {
    xml += ' var-category="' + esc(col.variableCategory) + '"';
  }
  if (col.type === 'Custom' && col.customLabel) {
    xml += ' custom-label="' + esc(col.customLabel) + '"';
  }
  xml += '/>';
  return xml;
}

/**
 * Serialize the assign-target onto the `<complex-scorecard>` root element.
 *
 * Mirrors TableAction.toXml:
 *   - always emits `assign-target-type="…"` as an attribute
 *   - when type === 'variable', emits the variable binding as root attributes
 *     (var / var-label / datatype / var-category) — the backend parser reads
 *     these off the `<complex-scorecard>` element directly (see
 *     ComplexScorecardParser lines 51-58)
 *   - when type === 'parameter', same shape (var-category defaults to "参数")
 *
 * Unlike the plain scorecard (which emits a bare `<value>` child element for
 * the assign target), the complex scorecard emits the variable/parameter
 * binding as ROOT ATTRIBUTES — this is the key wire-format difference. We
 * return the attribute string so the caller can splice it into the opening tag.
 */
function serializeAssignTargetAttrs(target: AssignTarget): string {
  let attr = ' assign-target-type="' + target.type + '"';
  if ((target.type === 'variable' || target.type === 'parameter') && target.value) {
    const v = target.value as {
      varCategory?: string;
      var?: string;
      varLabel?: string;
      datatype?: string;
    };
    if (v.var) attr += ' var="' + esc(v.var) + '"';
    if (v.varLabel) attr += ' var-label="' + esc(v.varLabel) + '"';
    if (v.datatype) attr += ' datatype="' + esc(v.datatype) + '"';
    if (v.varCategory) attr += ' var-category="' + esc(v.varCategory) + '"';
  }
  return attr;
}

/**
 * Serialize the full complex-scorecard document (including the XML declaration).
 *
 * Element order matches ComplexScoreCard.toXml: remark, libraries, cells,
 * rows, cols.
 */
export function serializeComplexScoreCard(card: ComplexScoreCardData): string {
  if (!card.scoringType) {
    throw new Error('请选择得分计算方式');
  }
  if (!card.assignTarget || !card.assignTarget.type) {
    throw new Error('请选择得分赋值对象');
  }
  if (card.scoringType === 'custom' && (!card.customScoringBean || card.customScoringBean.length < 1)) {
    throw new Error('请输入自定义计算得分的Bean ID');
  }

  // Validate Criteria cells have a variable binding (mirrors ComplexScoreCard.toXml).
  const colTypeByNum = new Map<number, ComplexColType>();
  for (const col of card.cols) colTypeByNum.set(col.num, col.type);
  for (const cell of card.cells) {
    const ct = colTypeByNum.get(cell.col);
    if (ct === 'Criteria' && !cell.variableLabel) {
      throw new Error('请选择条件格[' + (cell.row + 1) + ',' + (cell.col + 1) + ']对应的对象属性！');
    }
    if (ct === 'Criteria' && !colTypeByNum.has(cell.col)) {
      // No matching Criteria column — skip (defensive).
    }
  }
  // Validate Criteria columns have a variable category.
  for (const col of card.cols) {
    if (col.type === 'Criteria' && !col.variableCategory) {
      throw new Error('第[' + (col.num + 1) + ']条件列未定义具体变量或参数！');
    }
  }

  // ---- opening tag attributes ----
  let xml = '<?xml version="1.0" encoding="UTF-8"?>';
  xml += '<complex-scorecard';
  xml += ' scoring-type="' + card.scoringType + '"';
  xml += serializeAssignTargetAttrs(card.assignTarget);
  if (card.scoringType === 'custom') {
    xml += ' custom-scoring-bean="' + esc(card.customScoringBean!) + '"';
  }
  for (const prop of card.properties) {
    xml += ' ' + serializeProperty(prop);
  }
  xml += '>';

  // ---- children (matching ComplexScoreCard.toXml order) ----
  xml += serializeRemark(card.remark);
  for (const lib of card.libraries) {
    xml += serializeLibrary(lib);
  }
  // cells: emit in row-then-col order (the original iterates contentRows then
  // each row's conditionCells + actionCells; sorting by (row, col) reproduces
  // that for a flat list).
  const sortedCells = card.cells.slice().sort((a, b) =>
    a.row === b.row ? a.col - b.col : a.row - b.row,
  );
  for (const cell of sortedCells) {
    xml += serializeCell(cell, colTypeByNum.get(cell.col));
  }
  for (const row of card.rows) {
    xml += serializeRow(row);
  }
  for (const col of card.cols) {
    xml += serializeCol(col);
  }

  xml += '</complex-scorecard>';
  return xml;
}
