/**
 * parse.ts — legacy `<complex-scorecard>` XML string → ComplexScoreCardData state.
 *
 * Pure function. Uses DOMParser (jsdom in tests, the browser in the editor).
 * The inverse of serialize.ts: `parse(serialize(state))` deep-equals `state`.
 *
 * The `<joint>` of a condition cell and the `<value>` of a score / custom
 * cell are parsed by the ruleforge model's parseValue — reused verbatim —
 * because the wire format is identical to the plain scorecard.
 *
 * Assign-target binding: unlike the plain scorecard (which carries a bare
 * `<value>` child for the assign target), the complex scorecard stores the
 * variable/parameter binding as ROOT ATTRIBUTES on `<complex-scorecard>`
 * (var / var-label / datatype / var-category) — see ComplexScorecardParser
 * lines 51-58. We rebuild an AssignTarget.value from those attributes when
 * assign-target-type !== 'none'.
 */

import { parseValue } from '../../ruleforge/model/parse';
import type { ValueExpr } from '../../ruleforge/model/types';
import type {
  AssignTarget,
  AssignTargetType,
  CardCondition,
  CardJoint,
  CardProperty,
  ComplexCell,
  ComplexCol,
  ComplexColType,
  ComplexScoreCardData,
  LibraryImport,
  ScoringType,
} from './types';

const XML_MIME = 'text/xml';

/** Known `<complex-scorecard>` attributes that are boolean (parse as true/false). */
const BOOLEAN_PROPS = new Set(['enabled', 'debug']);

/** Known `<complex-scorecard>` attributes other than the structural ones. */
const CARD_PROPS = ['salience', 'effective-date', 'expires-date', 'enabled', 'debug'];

/** Parse a full complex-scorecard XML string into ComplexScoreCardData. */
export function parseComplexScoreCard(xml: string): ComplexScoreCardData {
  const doc = new DOMParser().parseFromString(xml, XML_MIME);
  assertNoParserError(doc);
  const root = doc.documentElement;
  if (!root || root.tagName !== 'complex-scorecard') {
    throw new Error('parseComplexScoreCard: root element is not <complex-scorecard>');
  }

  // ---- scoring-type + custom-scoring-bean ----
  const scoringType = (root.getAttribute('scoring-type') ?? 'sum') as ScoringType;
  const customScoringBean =
    scoringType === 'custom' ? (root.getAttribute('custom-scoring-bean') ?? undefined) : undefined;

  // ---- assign-target-type + root var/var-label/datatype/var-category attrs ----
  const assignTarget = parseAssignTarget(root);

  // ---- properties (salience / dates / enabled / debug) ----
  const properties = parseProperties(root);

  // ---- walk children ----
  let remark = '';
  const libraries: LibraryImport[] = [];
  const cells: ComplexCell[] = [];
  const rows: ComplexScoreCardData['rows'] = [];
  const cols: ComplexCol[] = [];

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
      case 'cell':
        cells.push(parseCell(child));
        break;
      case 'row':
        rows.push(parseRow(child));
        break;
      case 'col':
        cols.push(parseCol(child));
        break;
      default:
        // Unknown element — ignore (forward-compat).
        break;
    }
  }

  return {
    remark,
    properties,
    scoringType,
    customScoringBean,
    assignTarget,
    libraries,
    cols,
    rows,
    cells,
  };
}

/** DOMParser surfaces a `<parsererror>` element when the XML is malformed. */
function assertNoParserError(doc: Document): void {
  const err = doc.getElementsByTagName('parsererror')[0];
  if (err) {
    throw new Error('parseComplexScoreCard: malformed XML — ' + (err.textContent ?? '').slice(0, 200));
  }
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
 * Parse the assign-target. The `assign-target-type` is always on
 * `<complex-scorecard>`. The variable/parameter target binding lives on the
 * SAME element as attributes (var / var-label / datatype / var-category) —
 * rebuild a ValueExpr.Variable / ValueExpr.Parameter from those when type !==
 * 'none'.
 */
function parseAssignTarget(root: Element): AssignTarget {
  const typeRaw = root.getAttribute('assign-target-type') ?? 'none';
  const type = typeRaw as AssignTargetType;
  if (type === 'none') {
    return { type: 'none' };
  }
  const v = root.getAttribute('var');
  // Only build the value when the variable name is present; otherwise leave
  // value undefined (matches the backend's tolerant parse).
  let value: ValueExpr | undefined;
  if (v) {
    value = {
      type: type === 'parameter' ? 'Parameter' : 'Variable',
      varCategory: root.getAttribute('var-category') ?? (type === 'parameter' ? '参数' : ''),
      var: v,
      varLabel: root.getAttribute('var-label') ?? '',
      datatype: root.getAttribute('datatype') ?? '',
    } as ValueExpr;
  }
  return { type, value };
}

/** Parse a `<cell>` element. The column type is resolved by the caller via
 * the cols array (a cell alone doesn't know if it's Criteria / Score / Custom). */
function parseCell(el: Element): ComplexCell {
  const cell: ComplexCell = {
    row: parseInt(el.getAttribute('row') ?? '0', 10),
    col: parseInt(el.getAttribute('col') ?? '0', 10),
    rowspan: parseInt(el.getAttribute('rowspan') ?? '1', 10),
  };
  // Criteria cells carry the variable binding as attributes.
  const varName = el.getAttribute('var');
  if (varName !== null) {
    cell.variableName = varName;
    cell.variableLabel = el.getAttribute('var-label') ?? undefined;
    cell.datatype = el.getAttribute('datatype') ?? undefined;
  }
  // Inspect the child element(s) for joint (Criteria) or value (Score/Custom).
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

/** `<row num height/>`. */
function parseRow(el: Element): { num: number; height: number } {
  return {
    num: parseInt(el.getAttribute('num') ?? '0', 10),
    height: parseInt(el.getAttribute('height') ?? '40', 10),
  };
}

/** `<col num width type [var-category] [custom-label]/>`. */
function parseCol(el: Element): ComplexCol {
  const col: ComplexCol = {
    num: parseInt(el.getAttribute('num') ?? '0', 10),
    width: parseInt(el.getAttribute('width') ?? '150', 10),
    type: (el.getAttribute('type') ?? 'Criteria') as ComplexColType,
  };
  const vc = el.getAttribute('var-category');
  if (vc !== null) col.variableCategory = vc;
  const cl = el.getAttribute('custom-label');
  if (cl !== null) col.customLabel = cl;
  return col;
}

/** First direct child element with the given tag name, or undefined. */
function firstElementChild(el: Element, tag: string): Element | undefined {
  for (const child of Array.from(el.children)) {
    if (child.tagName === tag) return child;
  }
  return undefined;
}
