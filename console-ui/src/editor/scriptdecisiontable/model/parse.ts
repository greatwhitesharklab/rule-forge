/**
 * parse.ts — legacy `<script-decision-table>` XML string →
 * ScriptDecisionTableData state.
 *
 * Pure function. Uses DOMParser (jsdom in tests, the browser in the editor).
 * The inverse of serialize.ts: `parse(serialize(state))` deep-equals `state`.
 *
 * ── Differences from ../decisiontable/model/parse.ts ───────────────────────
 *   - Root tag is `<script-decision-table>` (not `<decision-table>`).
 *   - NO remark, NO properties to read.
 *   - Cells are `<script-cell>` whose entire child is a CDATA / text node
 *     holding the script string. We read `.textContent` (which DOMParser
 *     surfaces for CDATA sections), so a cell with no text content becomes
 *     `script: ''`.
 *   - Column / row / library parsing is IDENTICAL to the decision-table
 *     variant, so we delegate to the decision-table helpers.
 */

import {
  parseColumn,
  parseRow,
} from '../../decisiontable/model/parse';
import type {
  Column,
  LibraryImport,
  Row,
} from '../../decisiontable/model/types';
import type { ScriptCell, ScriptDecisionTableData } from './types';

const XML_MIME = 'text/xml';

/** Parse a full script-decision-table XML string into ScriptDecisionTableData. */
export function parseScriptDecisionTable(xml: string): ScriptDecisionTableData {
  const doc = new DOMParser().parseFromString(xml, XML_MIME);
  assertNoParserError(doc);
  const root = doc.documentElement;
  if (!root || root.tagName !== 'script-decision-table') {
    throw new Error('parseScriptDecisionTable: root element is not <script-decision-table>');
  }

  const libraries: LibraryImport[] = [];
  const columns: Column[] = [];
  const rows: Row[] = [];
  const cells: ScriptCell[] = [];

  for (const child of Array.from(root.children)) {
    switch (child.tagName) {
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
      case 'script-cell':
        cells.push(parseScriptCell(child));
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

  return { libraries, columns, rows, cells };
}

/** DOMParser surfaces a `<parsererror>` element when the XML is malformed. */
function assertNoParserError(doc: Document): void {
  const err = doc.getElementsByTagName('parsererror')[0];
  if (err) {
    throw new Error('parseScriptDecisionTable: malformed XML — ' + (err.textContent ?? '').slice(0, 200));
  }
}

/**
 * `<script-cell row col rowspan><![CDATA[...]]></script-cell>`.
 * `textContent` reads through CDATA sections; absent content → ''.
 */
function parseScriptCell(el: Element): ScriptCell {
  return {
    row: parseInt(el.getAttribute('row') ?? '0', 10),
    col: parseInt(el.getAttribute('col') ?? '0', 10),
    rowspan: parseInt(el.getAttribute('rowspan') ?? '1', 10),
    content: { script: el.textContent ?? '' },
  };
}
