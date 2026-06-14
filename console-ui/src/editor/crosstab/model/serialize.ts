/**
 * serialize.ts — CrossTable state → legacy `<crosstab>` XML string.
 *
 * Pure function. No React / DOM / jquery. Output is compatible with the
 * original jquery editor's CrossTable.toXml() chain
 * (CrossTable.ts → HeaderCell / Row / Column / ConditionCell / Cell →
 * CellCondition / InputType), so the React rewrite persists into the exact
 * same storage format the backend (CrosstabParser.java) already parses.
 *
 * Element/attribute order matches CrossTable.toXml:
 *   <crosstab {properties} {assign-target}>
 *     <remark/>
 *     <header/>
 *     <import-*-library/>...
 *     <row/>...
 *     <column/>...
 *     <condition-cell|value-cell/>...
 *   </crosstab>
 *
 * The `<value>` of a value-cell and of a condition's right-hand side is
 * serialized by the ruleforge model's serializeValue — reused verbatim —
 * because the wire format is identical.
 */

import { serializeValue } from '../../ruleforge/model/serialize';
import type {
  AssignTarget,
  BundleData,
  ConditionCellContent,
  ConditionCrossCell,
  CrossColumn,
  CrossJoint,
  CrossRow,
  CrosstabProperty,
  CrossTableData,
  HeaderCell,
  LibraryImport,
  ValueCrossCell,
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

/** `<crosstab name="value" …/>` attribute. Booleans → "true"/"false". */
function serializeProperty(prop: CrosstabProperty): string {
  if (typeof prop.value === 'boolean') {
    return prop.name + '="' + (prop.value ? 'true' : 'false') + '"';
  }
  return prop.name + '="' + esc(prop.value) + '"';
}

/**
 * Serialize a bundle's variable/parameter binding as XML attributes.
 *
 * Matches BaseRowCol.bundleDataToXml: a `bundle-data-type` attribute plus the
 * variable attributes. Parameters force `var-category="参数"`. Throws when the
 * variable category / parameter label is missing (the original throws too).
 */
function bundleDataToXml(bundle: BundleData): string {
  let xml = ' bundle-data-type="' + bundle.type + '" ';
  if (bundle.type === 'variable') {
    if (!bundle.variableCategory) {
      throw '变量不能为空！';
    }
    xml += 'var-category="' + esc(bundle.variableCategory) + '"';
    if (bundle.variableName) {
      xml +=
        ' var="' + esc(bundle.variableName) +
        '" var-label="' + esc(bundle.variableLabel ?? '') +
        '" datatype="' + esc(bundle.datatype ?? '') + '"';
    }
  } else {
    if (!bundle.variableLabel) {
      throw '参数不能为空！';
    }
    xml +=
      ' var-category="参数"' +
      ' var="' + esc(bundle.variableName ?? '') +
      '" var-label="' + esc(bundle.variableLabel) +
      '" datatype="' + esc(bundle.datatype ?? '') + '"';
  }
  return xml;
}

/**
 * Serialize the assignment target as XML attributes on `<crosstab>`.
 *
 * Matches CrossCellVariableBundle.toXml: emits `assign-target-type`, then for
 * a variable target the `var-category` / `var` / `var-label` / `datatype`
 * attributes; for a parameter target the same set with `var-category="参数"`.
 */
function assignTargetToXml(target: AssignTarget): string {
  let xml = ' assign-target-type="' + target.type + '" ';
  if (target.type === 'variable') {
    xml += 'var-category="' + esc(target.variableCategory ?? '') + '"';
    if (target.variableName) {
      xml +=
        ' var="' + esc(target.variableName) +
        '" var-label="' + esc(target.variableLabel ?? '') +
        '" datatype="' + esc(target.datatype ?? '') + '"';
    }
  } else {
    xml +=
      ' var-category="参数"' +
      ' var="' + esc(target.variableName ?? '') +
      '" var-label="' + esc(target.variableLabel ?? '') +
      '" datatype="' + esc(target.datatype ?? '') + '"';
  }
  return xml;
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

/** `<header rowspan colspan><![CDATA[text]]></header>`. */
function serializeHeader(header: HeaderCell): string {
  return (
    '<header rowspan="' + header.rowspan +
    '" colspan="' + header.colspan +
    '"><![CDATA[' + escCdata(header.text) + ']]></header>'
  );
}

/** `<row number type [bundle-data]></row>`. */
function serializeRow(row: CrossRow): string {
  let xml = '<row number="' + row.number + '" type="' + row.type + '"';
  if (row.bundleData) {
    xml += bundleDataToXml(row.bundleData);
  }
  xml += '></row>';
  return xml;
}

/** `<column number type [bundle-data]></column>`. */
function serializeColumn(col: CrossColumn): string {
  let xml = '<column number="' + col.number + '" type="' + col.type + '"';
  if (col.bundleData) {
    xml += bundleDataToXml(col.bundleData);
  }
  xml += '></column>';
  return xml;
}

/**
 * `<joint type><condition op>[<value/>]</condition>…</joint>`.
 * The `<value>` child is omitted when the condition op is Null / NotNull.
 */
function serializeJoint(joint: CrossJoint): string {
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

/** The inner element of a `<condition-cell>` (joint / nothing when empty). */
function serializeConditionContent(content: ConditionCellContent): string {
  if ('empty' in content) return '';
  if ('joint' in content) return serializeJoint(content.joint);
  return '';
}

/** `<condition-cell row col rowspan colspan>…joint…</condition-cell>`. */
function serializeConditionCell(cell: ConditionCrossCell): string {
  return (
    '<condition-cell row="' + cell.row +
    '" col="' + cell.col +
    '" rowspan="' + cell.rowspan +
    '" colspan="' + cell.colspan + '">' +
    serializeConditionContent(cell.content) +
    '</condition-cell>'
  );
}

/**
 * `<value-cell row col><value/></value-cell>`.
 *
 * Empty value cells (no `<value>` child) are emitted when the user hasn't
 * filled the cross-cell — the backend ValueCrossCellParser tolerates a missing
 * value (cell.setValue(null)).
 */
function serializeValueCell(cell: ValueCrossCell): string {
  let xml = '<value-cell row="' + cell.row + '" col="' + cell.col + '">';
  if (cell.value) {
    xml += serializeValue(cell.value);
  }
  xml += '</value-cell>';
  return xml;
}

/**
 * Serialize the full crosstab document (including the XML declaration).
 *
 * Attribute/element order matches CrossTable.toXml: properties + assign-target
 * on the root, then remark, header, libraries, rows, columns, cells.
 */
export function serializeCrossTable(data: CrossTableData): string {
  let xml = '<?xml version="1.0" encoding="UTF-8"?>';
  xml += '<crosstab';
  for (const prop of data.properties) {
    xml += ' ' + serializeProperty(prop);
  }
  if (data.assignTarget) {
    xml += assignTargetToXml(data.assignTarget);
  }
  xml += '>';
  xml += serializeRemark(data.remark);
  xml += serializeHeader(data.header);
  for (const lib of data.libraries) {
    xml += serializeLibrary(lib);
  }
  for (const row of data.rows) {
    xml += serializeRow(row);
  }
  for (const col of data.columns) {
    xml += serializeColumn(col);
  }
  for (const cell of data.conditionCells) {
    xml += serializeConditionCell(cell);
  }
  for (const cell of data.valueCells) {
    xml += serializeValueCell(cell);
  }
  xml += '</crosstab>';
  return xml;
}
