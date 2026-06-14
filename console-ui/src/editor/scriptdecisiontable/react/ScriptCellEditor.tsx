/**
 * ScriptCellEditor — controlled editor for one `<script-cell>` content.
 *
 * Unlike the decision-table CellEditor (which renders a structured op+value
 * widget for Criteria cells or an ActionEditor for action cells), the script
 * variant renders a single textarea / code input per cell: the cell content is
 * a raw UL expression string, no structure. The column type only affects the
 * PLACEHOLDER shown to the user (Criteria → if-expression hint, ConsolePrint →
 * print-expression hint, Assignment/ExecuteMethod → then-expression hint),
 * matching the CodeMirror `mode` switch in the legacy ScriptRenderers.ts
 * (`if` / `print` / `then`).
 *
 * The legacy jquery editor embedded a full CodeMirror instance per cell with
 * autocomplete; the React rewrite uses a plain AntD Input.TextArea. A
 * CodeMirror-backed editor is a TODO (the model is already script-string-
 * only, so swapping the widget later is a one-line change here).
 */
import { Input } from 'antd';
import type { ColumnType } from '../../decisiontable/model/types';
import type { ScriptCellContent } from '../model/types';

export interface ScriptCellEditorProps {
  /** The column type this cell lives under (drives the placeholder hint). */
  columnType: ColumnType;
  /** The current cell content (controlled). */
  value: ScriptCellContent;
  /** Called with the new ScriptCellContent on every edit. */
  onChange: (next: ScriptCellContent) => void;
}

/** Placeholder hint per column type — mirrors the legacy CodeMirror mode. */
function placeholderFor(type: ColumnType): string {
  switch (type) {
    case 'Criteria':
      return '条件脚本 (if 表达式),如:客户.年龄 > 30';
    case 'ConsolePrint':
      return '打印脚本 (print 表达式),如:"rate=" + 客户.费率';
    case 'Assignment':
      return '赋值脚本 (then 表达式),如:tier = "GOLD"';
    case 'ExecuteMethod':
      return '执行方法脚本 (then 表达式),如:贷款服务.记录日志(客户.id)';
  }
}

export function ScriptCellEditor({ columnType, value, onChange }: ScriptCellEditorProps) {
  return (
    <Input.TextArea
      value={value.script}
      onChange={(e) => onChange({ script: e.target.value })}
      placeholder={placeholderFor(columnType)}
      autoSize={{ minRows: 1, maxRows: 6 }}
      style={{ width: '100%' }}
    />
  );
}

export default ScriptCellEditor;
