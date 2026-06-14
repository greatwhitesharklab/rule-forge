/**
 * parse.ts — legacy `<scorecard>` XML string → ScoreCardData state.
 *
 * Pure function. Uses DOMParser (jsdom in tests, the browser in the editor).
 * The inverse of serialize.ts: `parse(serialize(state))` deep-equals `state`.
 *
 * The `<joint>` of a condition cell and the `<value>` of a condition / score /
 * custom cell / assign-target are parsed by the ruleforge model's parseValue
 * — reused verbatim — because the wire format is identical.
 */

import { parseValue } from '../../ruleforge/model/parse';
import type { ValueExpr } from '../../ruleforge/model/types';
import type {
  AssignTarget,
  AssignTargetType,
  AttributeRow,
  CardCell,
  CardCellType,
  CardCondition,
  CardJoint,
  CardProperty,
  CustomCol,
  LibraryImport,
  ScoreCardData,
  ScoringType,
} from './types';

const XML_MIME = 'text/xml';

/** Known `<scorecard>` attributes that are boolean (parse as true/false). */
const BOOLEAN_PROPS = new Set(['enabled', 'debug']);

/** Known `<scorecard>` attributes other than the structural ones. */
const CARD_PROPS = ['salience', 'effective-date', 'expires-date', 'enabled', 'debug'];

/** Parse a full scorecard XML string into ScoreCardData. */
export function parseScoreCard(xml: string): ScoreCardData {
  const doc = new DOMParser().parseFromString(xml, XML_MIME);
  assertNoParserError(doc);
  const root = doc.documentElement;
  if (!root || root.tagName !== 'scorecard') {
    throw new Error('parseScoreCard: root element is not <scorecard>');
  }

  // ---- name (required) ----
  const name = root.getAttribute('name') ?? '';

  // ---- weight-support ----
  const weightSupportRaw = root.getAttribute('weight-support');
  const weightSupport = weightSupportRaw === 'true';

  // ---- three fixed col headers (defaults match ScoreCardTable.init) ----
  const attributeCol = parseFixedCol(root, 'attr-col', '属性', '200');
  const conditionCol = parseFixedCol(root, 'condition-col', '条件', '220');
  const scoreCol = parseFixedCol(root, 'score-col', '分值', '180');

  // ---- scoring-type + custom-scoring-bean ----
  const scoringType = (root.getAttribute('scoring-type') ?? 'sum') as ScoringType;
  const customScoringBean =
    scoringType === 'custom' ? (root.getAttribute('custom-scoring-bean') ?? undefined) : undefined;

  // ---- assign-target-type + the bare <value> child (variable/parameter) ----
  const assignTarget = parseAssignTarget(root);

  // ---- properties (salience / dates / enabled / debug) ----
  const properties = parseProperties(root);

  // ---- walk children ----
  let remark = '';
  const libraries: LibraryImport[] = [];
  const cells: CardCell[] = [];
  const rows: AttributeRow[] = [];
  const customCols: CustomCol[] = [];

  for (const child of Array.from(root.children)) {
    switch (child.tagName) {
      case 'remark':
        remark = child.textContent ?? '';
        break;
      case 'import-variable-library':
        libraries.push({ type: 'Variable', path: child.getAttribute('path') ?? '' });
        break;
      case 'import-constant-library':
        libraries.push({ type: 'Constant', path: child.getAttribute('path') ?? '' });
        break;
      case 'import-action-library':
        libraries.push({ type: 'Action', path: child.getAttribute('path') ?? '' });
        break;
      case 'import-parameter-library':
        libraries.push({ type: 'Parameter', path: child.getAttribute('path') ?? '' });
        break;
      case 'card-cell':
        cells.push(parseCell(child));
        break;
      case 'attribute-row':
        rows.push(parseAttributeRow(child));
        break;
      case 'custom-col':
        customCols.push(parseCustomCol(child));
        break;
      case 'value':
        // The bare assign-target <value> child — already consumed by
        // parseAssignTarget; skip here.
        break;
      default:
        // Unknown element — ignore (forward-compat).
        break;
    }
  }

  return {
    name,
    remark,
    properties,
    weightSupport,
    attributeCol,
    conditionCol,
    scoreCol,
    scoringType,
    customScoringBean,
    assignTarget,
    libraries,
    cells,
    rows,
    customCols,
  };
}

/** DOMParser surfaces a `<parsererror>` element when the XML is malformed. */
function assertNoParserError(doc: Document): void {
  const err = doc.getElementsByTagName('parsererror')[0];
  if (err) {
    throw new Error('parseScoreCard: malformed XML — ' + (err.textContent ?? '').slice(0, 200));
  }
}

/** Read one of the three fixed col headers (attr / condition / score). */
function parseFixedCol(root: Element, prefix: string, defaultName: string, defaultWidth: string) {
  const name = root.getAttribute(prefix + '-name') ?? defaultName;
  const widthRaw = root.getAttribute(prefix + '-width') ?? defaultWidth;
  const width = parseInt(widthRaw, 10);
  return { name, width: Number.isFinite(width) ? width : parseInt(defaultWidth, 10) };
}

/** Read the known card properties (salience / dates / enabled / debug). */
function parseProperties(root: Element): CardProperty[] {
  const props: CardProperty[] = [];
  for (const name of CARD_PROPS) {
    const raw = root.getAttribute(name);
    if (raw === null) continue;
    if (BOOLEAN_PROPS.has(name)) {
      props.push({ name, value: raw === 'true' });
    } else {
      props.push({ name, value: raw });
    }
  }
  return props;
}

/**
 * Parse the assign-target. The `assign-target-type` is always on `<scorecard>`.
 * The variable/parameter target is a bare `<value>` direct child of `<scorecard>`
 * (emitted by TableAction.toXml as a sibling of remark/libraries/card-cell).
 */
function parseAssignTarget(root: Element): AssignTarget {
  const typeRaw = root.getAttribute('assign-target-type') ?? 'none';
  const type = typeRaw as AssignTargetType;
  if (type === 'none') {
    return { type: 'none' };
  }
  // Find the first direct <value> child that is a Variable or Parameter value.
  let value: ValueExpr | undefined;
  for (const child of Array.from(root.children)) {
    if (child.tagName !== 'value') continue;
    const parsed = parseValue(child);
    value = parsed;
    break;
  }
  return { type, value };
}

/** Parse a `<card-cell>` element. */
function parseCell(el: Element): CardCell {
  const type = (el.getAttribute('type') ?? 'attribute') as CardCellType;
  const cell: CardCell = {
    type,
    row: parseInt(el.getAttribute('row') ?? '0', 10),
    col: parseInt(el.getAttribute('col') ?? '0', 10),
  };
  if (type === 'attribute') {
    cell.category = el.getAttribute('category') ?? undefined;
    cell.variableName = el.getAttribute('var') ?? undefined;
    cell.variableLabel = el.getAttribute('var-label') ?? undefined;
    cell.datatype = el.getAttribute('datatype') ?? undefined;
    cell.weight = el.getAttribute('weight') ?? undefined;
  }
  // Inspect the child element(s) for joint (condition) or value (score/custom).
  for (const child of Array.from(el.children)) {
    if (child.tagName === 'joint') {
      cell.joint = parseJoint(child);
    } else if (child.tagName === 'value') {
      cell.value = parseValue(child);
    }
  }
  return cell;
}

/** `<joint type><condition op>[<value/>]</condition>…</joint>`. */
function parseJoint(el: Element): CardJoint {
  const type = (el.getAttribute('type') ?? 'and') === 'or' ? 'or' : 'and';
  const conditions: CardCondition[] = [];
  for (const child of Array.from(el.children)) {
    if (child.tagName !== 'condition') continue;
    const op = child.getAttribute('op') ?? 'Equals';
    const valueEl = firstElementChild(child, 'value');
    const cond: CardCondition = { op };
    if (valueEl) {
      cond.right = parseValue(valueEl) as ValueExpr;
    }
    conditions.push(cond);
  }
  return { type, conditions };
}

/** `<attribute-row row-number><condition-row row-number/>…</attribute-row>`. */
function parseAttributeRow(el: Element): AttributeRow {
  const rowNumber = parseInt(el.getAttribute('row-number') ?? '0', 10);
  const conditionRows: { rowNumber: number }[] = [];
  for (const child of Array.from(el.children)) {
    if (child.tagName !== 'condition-row') continue;
    conditionRows.push({ rowNumber: parseInt(child.getAttribute('row-number') ?? '0', 10) });
  }
  return { rowNumber, conditionRows };
}

/** `<custom-col col-number name width/>`. */
function parseCustomCol(el: Element): CustomCol {
  return {
    colNumber: parseInt(el.getAttribute('col-number') ?? '0', 10),
    name: el.getAttribute('name') ?? '',
    width: parseInt(el.getAttribute('width') ?? '160', 10),
  };
}

/** First direct child element with the given tag name, or undefined. */
function firstElementChild(el: Element, tag: string): Element | undefined {
  for (const child of Array.from(el.children)) {
    if (child.tagName === tag) return child;
  }
  return undefined;
}
