/**
 * parse.ts — legacy `<crosstab>` XML string → CrossTableData state.
 *
 * Pure function. Uses DOMParser (jsdom in tests, the browser in the editor).
 * The inverse of serialize.ts: `parse(serialize(state))` deep-equals `state`.
 *
 * The `<value>` of a value-cell and of a condition's right-hand side is parsed
 * by the ruleforge model's parseValue — reused verbatim — because the wire
 * format is identical.
 */

import { parseValue } from '../../ruleforge/model/parse';
import type { ValueExpr } from '../../ruleforge/model/types';
import type {
  AssignTarget,
  BundleData,
  ConditionCrossCell,
  CrossColumn,
  CrossCondition,
  CrossJoint,
  CrossRow,
  CrosstabProperty,
  CrossTableData,
  HeaderCell,
  LibraryImport,
  ValueCrossCell,
} from './types';

const XML_MIME = 'text/xml';

/** Parse a full crosstab XML string into CrossTableData. */
export function parseCrossTable(xml: string): CrossTableData {
  const doc = new DOMParser().parseFromString(xml, XML_MIME);
  assertNoParserError(doc);
  const root = doc.documentElement;
  if (!root || root.tagName !== 'crosstab') {
    throw new Error('parseCrossTable: root element is not <crosstab>');
  }

  const properties = parseProperties(root);
  const assignTarget = parseAssignTarget(root);

  let remark = '';
  const libraries: LibraryImport[] = [];
  const rows: CrossRow[] = [];
  const columns: CrossColumn[] = [];
  const conditionCells: ConditionCrossCell[] = [];
  const valueCells: ValueCrossCell[] = [];
  let header: HeaderCell = { rowspan: 1, colspan: 1, text: '' };

  for (const child of Array.from(root.children)) {
    switch (child.tagName) {
      case 'remark':
        remark = child.textContent ?? '';
        break;
      case 'header':
        header = parseHeader(child);
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
      case 'row':
        rows.push(parseRow(child));
        break;
      case 'column':
        columns.push(parseColumn(child));
        break;
      case 'condition-cell':
        conditionCells.push(parseConditionCell(child));
        break;
      case 'value-cell':
        valueCells.push(parseValueCell(child));
        break;
      default:
        // Unknown element — ignore (forward-compat).
        break;
    }
  }

  const data: CrossTableData = {
    remark,
    properties,
    header,
    libraries,
    rows,
    columns,
    conditionCells,
    valueCells,
  };
  if (assignTarget) data.assignTarget = assignTarget;
  return data;
}

/** DOMParser surfaces a `<parsererror>` element when the XML is malformed. */
function assertNoParserError(doc: Document): void {
  const err = doc.getElementsByTagName('parsererror')[0];
  if (err) {
    throw new Error('parseCrossTable: malformed XML — ' + (err.textContent ?? '').slice(0, 200));
  }
}

/**
 * Read the known `<crosstab>` attributes (salience / dates / enabled / debug).
 * Skips the assign-target group (parsed separately) and any name attribute.
 */
function parseProperties(root: Element): CrosstabProperty[] {
  const KNOWN = ['salience', 'effective-date', 'expires-date', 'enabled', 'debug'];
  const props: CrosstabProperty[] = [];
  for (const name of KNOWN) {
    const raw = root.getAttribute(name);
    if (raw === null) continue;
    if (name === 'enabled' || name === 'debug') {
      props.push({ name, value: raw === 'true' });
    } else {
      props.push({ name, value: raw });
    }
  }
  return props;
}

/**
 * Read the assign-target attribute group from `<crosstab>`.
 *
 * `assign-target-type` selects variable or parameter; `var-category` / `var` /
 * `var-label` / `datatype` carry the target binding. Returns undefined when
 * `assign-target-type` is absent (no assignment target configured).
 */
function parseAssignTarget(root: Element): AssignTarget | undefined {
  const type = root.getAttribute('assign-target-type');
  if (!type) return undefined;
  const target: AssignTarget = { type: type as AssignTarget['type'] };
  const vc = root.getAttribute('var-category');
  if (vc !== null) target.variableCategory = vc;
  const v = root.getAttribute('var');
  if (v !== null) target.variableName = v;
  const vl = root.getAttribute('var-label');
  if (vl !== null) target.variableLabel = vl;
  const dt = root.getAttribute('datatype');
  if (dt !== null) target.datatype = dt;
  return target;
}

/** `<header rowspan colspan><![CDATA[text]]></header>`. */
function parseHeader(el: Element): HeaderCell {
  return {
    rowspan: parseInt(el.getAttribute('rowspan') ?? '1', 10),
    colspan: parseInt(el.getAttribute('colspan') ?? '1', 10),
    text: el.textContent ?? '',
  };
}

/** `<row number type [bundle-data]/>`. */
function parseRow(el: Element): CrossRow {
  const row: CrossRow = {
    number: parseInt(el.getAttribute('number') ?? '0', 10),
    type: (el.getAttribute('type') === 'left' ? 'left' : 'top'),
  };
  const bundle = parseBundleData(el);
  if (bundle) row.bundleData = bundle;
  return row;
}

/** `<column number type [bundle-data]/>`. */
function parseColumn(el: Element): CrossColumn {
  const col: CrossColumn = {
    number: parseInt(el.getAttribute('number') ?? '0', 10),
    type: (el.getAttribute('type') === 'top' ? 'top' : 'left'),
  };
  const bundle = parseBundleData(el);
  if (bundle) col.bundleData = bundle;
  return col;
}

/**
 * Read the bundle-data-type / var-category / var / var-label / datatype
 * attribute group from a `<row>` or `<column>` element. Returns undefined when
 * `bundle-data-type` is absent (value rows / value columns carry no bundle).
 */
function parseBundleData(el: Element): BundleData | undefined {
  const type = el.getAttribute('bundle-data-type');
  if (!type) return undefined;
  const bundle: BundleData = { type: type as BundleData['type'], variableCategory: '' };
  const vc = el.getAttribute('var-category');
  if (vc !== null) bundle.variableCategory = vc;
  const vl = el.getAttribute('var-label');
  if (vl !== null) bundle.variableLabel = vl;
  const v = el.getAttribute('var');
  if (v !== null) bundle.variableName = v;
  const dt = el.getAttribute('datatype');
  if (dt !== null) bundle.datatype = dt;
  return bundle;
}

/** `<condition-cell row col rowspan colspan>…</condition-cell>`. */
function parseConditionCell(el: Element): ConditionCrossCell {
  const cell: ConditionCrossCell = {
    row: parseInt(el.getAttribute('row') ?? '0', 10),
    col: parseInt(el.getAttribute('col') ?? '0', 10),
    rowspan: parseInt(el.getAttribute('rowspan') ?? '1', 10),
    colspan: parseInt(el.getAttribute('colspan') ?? '1', 10),
    content: { empty: true },
  };
  const jointEl = firstElementChild(el, 'joint');
  if (jointEl) {
    cell.content = { joint: parseJoint(jointEl) };
  }
  return cell;
}

/** `<value-cell row col><value/></value-cell>`. value is optional. */
function parseValueCell(el: Element): ValueCrossCell {
  const cell: ValueCrossCell = {
    row: parseInt(el.getAttribute('row') ?? '0', 10),
    col: parseInt(el.getAttribute('col') ?? '0', 10),
  };
  const valueEl = firstElementChild(el, 'value');
  if (valueEl) {
    cell.value = parseValue(valueEl) as ValueExpr;
  }
  return cell;
}

/** `<joint type><condition op>[<value/>]</condition>…</joint>`. */
function parseJoint(el: Element): CrossJoint {
  const type = (el.getAttribute('type') ?? 'and') === 'or' ? 'or' : 'and';
  const conditions: CrossCondition[] = [];
  for (const child of Array.from(el.children)) {
    if (child.tagName !== 'condition') continue;
    const op = child.getAttribute('op') ?? 'Equals';
    const valueEl = firstElementChild(child, 'value');
    const cond: CrossCondition = { op };
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
