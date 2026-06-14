/**
 * parse.ts — legacy `<decision-table>` XML string → DecisionTableData state.
 *
 * Pure function. Uses DOMParser (jsdom in tests, the browser in the editor).
 * The inverse of serialize.ts: `parse(serialize(state))` deep-equals `state`.
 *
 * The `<value>` of a condition and the four action variants are parsed by the
 * ruleforge model's parseValue / parseAction — reused verbatim — because the
 * wire format is identical.
 */

import { parseAction, parseValue } from '../../ruleforge/model/parse';
import type { Action, ValueExpr } from '../../ruleforge/model/types';
import type {
  Cell,
  CellCondition,
  CellContent,
  CellJoint,
  Column,
  ColumnType,
  DecisionTableData,
  LibraryImport,
  Row,
  TableProperty,
} from './types';

const XML_MIME = 'text/xml';

/** Parse a full decision-table XML string into DecisionTableData. */
export function parseDecisionTable(xml: string): DecisionTableData {
  const doc = new DOMParser().parseFromString(xml, XML_MIME);
  assertNoParserError(doc);
  const root = doc.documentElement;
  if (!root || root.tagName !== 'decision-table') {
    throw new Error('parseDecisionTable: root element is not <decision-table>');
  }

  // `<decision-table>` attributes (properties).
  const properties = parseProperties(root);

  let remark = '';
  const libraries: LibraryImport[] = [];
  const columns: Column[] = [];
  const rows: Row[] = [];
  const cells: Cell[] = [];

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
        columns.push(parseColumn(child));
        break;
      default:
        // Unknown element — ignore (forward-compat).
        break;
    }
  }

  return { remark, libraries, properties, columns, rows, cells };
}

/** DOMParser surfaces a `<parsererror>` element when the XML is malformed. */
function assertNoParserError(doc: Document): void {
  const err = doc.getElementsByTagName('parsererror')[0];
  if (err) {
    throw new Error('parseDecisionTable: malformed XML — ' + (err.textContent ?? '').slice(0, 200));
  }
}

/** Read the known `<decision-table>` attributes (salience / dates / enabled / debug / loop). */
function parseProperties(root: Element): TableProperty[] {
  const KNOWN = ['salience', 'effective-date', 'expires-date', 'enabled', 'debug', 'loop'];
  const props: TableProperty[] = [];
  for (const name of KNOWN) {
    const raw = root.getAttribute(name);
    if (raw === null) continue;
    if (name === 'enabled' || name === 'debug' || name === 'loop') {
      props.push({ name, value: raw === 'true' });
    } else {
      props.push({ name, value: raw });
    }
  }
  return props;
}

/**
 * Parse a `<row num height/>` element.
 *
 * Exported so the script-decision-table model can reuse it — the row wire
 * format is identical between the two rule types.
 */
export function parseRow(el: Element): Row {
  return {
    num: parseInt(el.getAttribute('num') ?? '0', 10),
    height: parseInt(el.getAttribute('height') ?? '40', 10),
  };
}

/**
 * Parse a `<col num width type [var-category var var-label datatype]/>` element.
 *
 * Exported so the script-decision-table model can reuse it — the column wire
 * format is identical between the two rule types.
 */
export function parseColumn(el: Element): Column {
  const col: Column = {
    num: parseInt(el.getAttribute('num') ?? '0', 10),
    type: (el.getAttribute('type') ?? 'Criteria') as ColumnType,
    width: parseInt(el.getAttribute('width') ?? '120', 10),
  };
  const vc = el.getAttribute('var-category');
  if (vc !== null) col.variableCategory = vc === '参数' ? 'parameter' : vc;
  const vl = el.getAttribute('var-label');
  if (vl !== null) col.variableLabel = vl;
  const vn = el.getAttribute('var');
  if (vn !== null) col.variableName = vn;
  const dt = el.getAttribute('datatype');
  if (dt !== null) col.datatype = dt;
  return col;
}

function parseCell(el: Element): Cell {
  const cell: Cell = {
    row: parseInt(el.getAttribute('row') ?? '0', 10),
    col: parseInt(el.getAttribute('col') ?? '0', 10),
    rowspan: parseInt(el.getAttribute('rowspan') ?? '1', 10),
    content: { empty: true },
  };
  // Inspect the first child element to decide the content variant.
  for (const child of Array.from(el.children)) {
    if (child.tagName === 'joint') {
      cell.content = { joint: parseJoint(child) };
      break;
    } else if (
      child.tagName === 'var-assign' ||
      child.tagName === 'console-print' ||
      child.tagName === 'execute-method' ||
      child.tagName === 'execute-function'
    ) {
      cell.content = { action: parseAction(child) as Action };
      break;
    }
  }
  return cell;
}

/** `<joint type><condition op>[<value/>]</condition>…</joint>`. */
function parseJoint(el: Element): CellJoint {
  const type = (el.getAttribute('type') ?? 'and') === 'or' ? 'or' : 'and';
  const conditions: CellCondition[] = [];
  for (const child of Array.from(el.children)) {
    if (child.tagName !== 'condition') continue;
    const op = child.getAttribute('op') ?? 'Equals';
    const valueEl = firstElementChild(child, 'value');
    const cond: CellCondition = { op };
    if (valueEl) {
      cond.right = parseValue(valueEl) as ValueExpr;
    }
    conditions.push(cond);
  }
  return { type, conditions };
}

/** First direct child element with the given tag name, or undefined. */
function firstElementChild(el: Element, tag: string): Element | undefined {
  for (const child of Array.from(el.children)) {
    if (child.tagName === tag) return child;
  }
  return undefined;
}
