/**
 * serialize.ts — ScoreCard state → legacy `<scorecard>` XML string.
 *
 * Pure function. No React / DOM / jquery. Output is compatible with the
 * original jquery editor's ScoreCardTable.toXml() chain
 * (ScoreCardTable.ts → PropertyConfig / TableAction / AttributeCol /
 * ConditionCol / ScoreCol → AttributeRow → Cell → ConditionCell / ScoreCell /
 * CustomCell), so the React rewrite persists into the exact same storage
 * format the backend (ScorecardParser.java) already parses.
 *
 * The `<joint>` of a condition cell and the `<value>` of a condition / score /
 * custom cell are serialized by the ruleforge model's serializeValue — reused
 * verbatim — because the wire format is identical.
 */

import { serializeValue } from '../../ruleforge/model/serialize';
import type {
  AssignTarget,
  CardCell,
  CardJoint,
  CardProperty,
  CustomCol,
  LibraryImport,
  ScoreCardData,
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

/** `<scorecard name="value" …/>` attribute. Booleans → "true"/"false". */
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
 * `<card-cell type row col …>…</card-cell>`.
 *
 * Wire format per type (mirrors Cell.toXml):
 *   - attribute → optional weight, var / var-label / datatype / category
 *                 attributes, empty body
 *   - condition → joint in the body
 *   - score     → `<value>` in the body
 *   - custom    → `<value>` in the body
 */
function serializeCell(cell: CardCell): string {
  let attrs =
    ' type="' + cell.type + '"' +
    ' row="' + cell.row + '"' +
    ' col="' + cell.col + '"';

  let body = '';

  if (cell.type === 'attribute') {
    if (!cell.variableName) {
      throw new Error('请先选择属性 (card-cell row=' + cell.row + ')');
    }
    if (!cell.category) {
      throw new Error('请先选择分类 (card-cell row=' + cell.row + ')');
    }
    // weightSupport is checked at the caller; when weight is present we emit it.
    if (cell.weight !== undefined && cell.weight !== '') {
      attrs += ' weight="' + esc(cell.weight) + '"';
    }
    attrs += ' var="' + esc(cell.variableName) + '"';
    attrs += ' var-label="' + esc(cell.variableLabel ?? cell.variableName) + '"';
    attrs += ' datatype="' + esc(cell.datatype ?? 'String') + '"';
    attrs += ' category="' + esc(cell.category) + '"';
  } else if (cell.type === 'condition') {
    if (!cell.joint) {
      throw new Error('请配置好条件 (card-cell row=' + cell.row + ')');
    }
    body = serializeJoint(cell.joint);
  } else if (cell.type === 'score' || cell.type === 'custom') {
    if (!cell.value) {
      throw new Error('请配置好' + (cell.type === 'score' ? '分值' : '自定义值') + ' (card-cell row=' + cell.row + ')');
    }
    body = serializeValue(cell.value);
  }

  return '<card-cell' + attrs + '>' + body + '</card-cell>';
}

/** `<attribute-row row-number><condition-row row-number/>…</attribute-row>`. */
function serializeAttributeRow(row: { rowNumber: number; conditionRows: { rowNumber: number }[] }): string {
  let xml = '<attribute-row row-number="' + row.rowNumber + '">';
  for (const cr of row.conditionRows) {
    xml += '<condition-row row-number="' + cr.rowNumber + '"/>';
  }
  xml += '</attribute-row>';
  return xml;
}

/** `<custom-col col-number name width/>`. */
function serializeCustomCol(col: CustomCol): string {
  return (
    '<custom-col col-number="' + col.colNumber + '"' +
    ' name="' + esc(col.name) + '"' +
    ' width="' + col.width + '"/>'
  );
}

/**
 * The assign-target payload. Mirrors TableAction.toXml:
 *   - always emits `assign-target-type="…"` as an attribute
 *   - when type !== 'none', emits the variable/parameter `<value>` as a bare
 *     child element (these become attributes/children on the `<scorecard>`
 *     element in the final XML)
 *
 * Returns the attribute portion + the bare `<value>` child (concatenated in
 * document order: attribute first, then the value child appears among the
 * scorecard's other children).
 */
function serializeAssignTarget(target: AssignTarget): { attr: string; child: string } {
  let attr = ' assign-target-type="' + target.type + '"';
  let child = '';
  if (target.type !== 'none' && target.value) {
    child = serializeValue(target.value);
  }
  return { attr, child };
}

/**
 * Serialize the full scorecard document (including the XML declaration).
 *
 * Element order matches ScoreCardTable.toXml: remark, libraries, all cells
 * (attribute rows' cells first, in row order, then the cell list is iterated
 * as-is — the original emits cells grouped by attribute row), attribute-row
 * wrappers, custom-col definitions.
 */
export function serializeScoreCard(card: ScoreCardData): string {
  if (!card.name || card.name.length < 1) {
    throw new Error('评分卡名称不能为空');
  }

  // Validate weight requirement when weight-support is on.
  if (card.weightSupport) {
    for (const cell of card.cells) {
      if (cell.type === 'attribute' && (!cell.weight || cell.weight.length < 1)) {
        throw new Error('请先定义[' + (cell.variableLabel || cell.variableName) + ']属性的权重值');
      }
    }
  }

  const assignParts = serializeAssignTarget(card.assignTarget);

  // ---- opening tag attributes ----
  let xml = '<?xml version="1.0" encoding="UTF-8"?>';
  xml += '<scorecard';
  xml += ' weight-support="' + (card.weightSupport ? 'true' : 'false') + '"';
  xml += ' name="' + esc(card.name) + '"';
  for (const prop of card.properties) {
    xml += ' ' + serializeProperty(prop);
  }
  // three fixed col headers
  xml += ' attr-col-width="' + card.attributeCol.width + '"';
  xml += ' attr-col-name="' + esc(card.attributeCol.name) + '"';
  xml += ' condition-col-width="' + card.conditionCol.width + '"';
  xml += ' condition-col-name="' + esc(card.conditionCol.name) + '"';
  xml += ' score-col-width="' + card.scoreCol.width + '"';
  xml += ' score-col-name="' + esc(card.scoreCol.name) + '"';
  // scoring + assign-target attributes
  if (!card.scoringType) {
    throw new Error('请选择得分计算方式');
  }
  xml += ' scoring-type="' + card.scoringType + '"';
  xml += assignParts.attr;
  if (card.scoringType === 'custom') {
    if (!card.customScoringBean || card.customScoringBean.length < 1) {
      throw new Error('请输入自定义计算得分的Bean ID');
    }
    xml += ' custom-scoring-bean="' + esc(card.customScoringBean) + '"';
  }
  xml += '>';

  // ---- children (matching ScoreCardTable.toXml order) ----
  xml += serializeRemark(card.remark);
  for (const lib of card.libraries) {
    xml += serializeLibrary(lib);
  }
  // assign-target value child (bare <value>) when present.
  if (assignParts.child) {
    xml += assignParts.child;
  }
  // cells: the original emits all cells grouped by attribute row (cellsToXml
  // recurses). We emit them in document order (row, then col within a row).
  const sortedCells = card.cells.slice().sort((a, b) =>
    a.row === b.row ? a.col - b.col : a.row - b.row,
  );
  for (const cell of sortedCells) {
    xml += serializeCell(cell);
  }
  for (const row of card.rows) {
    xml += serializeAttributeRow(row);
  }
  for (const col of card.customCols) {
    xml += serializeCustomCol(col);
  }

  xml += '</scorecard>';
  return xml;
}
