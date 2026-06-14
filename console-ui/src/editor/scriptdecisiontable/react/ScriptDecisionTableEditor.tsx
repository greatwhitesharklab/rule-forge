/**
 * ScriptDecisionTableEditor — top-level React script-decision-table editor.
 *
 * Responsibilities:
 *   1. Load: fetch the script-decision-table XML → parseScriptDecisionTable
 *      → state.
 *   2. Hold the ScriptDecisionTableData state at the top (single-direction
 *      data flow).
 *   3. Render: toolbar (save / add column / add row) + AntD Table (columns
 *      from the table's columns, rows from the table's rows, each cell
 *      editable via ScriptCellEditor).
 *   4. Save: serializeScriptDecisionTable → POST /common/saveFile
 *      (URL-encoded content).
 *
 * ── Differences from ../decisiontable/react/DecisionTableEditor.tsx ─────────
 *   - NO remark textarea (script-decision-table has no remark element).
 *   - NO salience/properties UI (no property attributes on the root).
 *   - Cells are ScriptCell (CDATA script string), edited via ScriptCellEditor
 *     (a textarea), NOT structured CellContent edited via ValueEditor/
 *     ActionEditor.
 *   - Load path: same /common/loadXml, but the legacy jquery editor received a
 *     SERVER-DESERIALIZED object (`decisionTable.cells` etc.) rather than raw
 *     XML. The React rewrite mirrors the DecisionTableEditor approach of
 *     requesting raw XML under `editorData.xml` (falling back to `.content`)
 *     and parsing client-side. An empty string means a fresh file.
 *
 * Data flow:
 *   loadXml → parseScriptDecisionTable(state) ─┐
 *                                             ├→ React state (the only owner)
 *   cell/column edits via onChange ───────────┘
 *   save button → serializeScriptDecisionTable(state) → formPost(/common/saveFile)
 *
 * ── TODO (handsontable features NOT yet ported) ──────────────────────────
 *   - merged cells (rowspan > 1 for multi-row Criteria cells)
 *   - freeze panes
 *   - cell-range / multi-cell selection copy-paste is still TODO (row-level
 *     copy/paste IS implemented below)
 *   - CodeMirror in-cell editing with UL autocomplete (legacy ScriptRenderers
 *     used CodeMirror modes `if` / `then` / `print`; React uses a textarea).
 *
 * ── Row copy/paste ───────────────────────────────────────────────────────
 * Mirrors DecisionTableEditor's V5.60 pattern. Copy grabs the ScriptCells OWNED
 * by the source wire-row (cell.row === wireRow), deep-clones them, and clamps
 * every rowspan to 1 so a later paste never extends a merge beyond the single
 * pasted row. The stored cells carry a placeholder row (0); paste re-stamps
 * them. Paste inserts a new grid row BELOW `displayRow` (displayRow=-1 → top),
 * shifts every existing cell whose wire-row is at/under the insertion point
 * down by one, then drops in the clipboard cells re-stamped to the new wire-row.
 * The new Row gets a fresh dense `num` via the same renumber pass addRow/
 * removeRow use (num IS the identity, kept dense 0..n-1 by removeRow).
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Dropdown, Space, Spin, Table, Typography, message } from 'antd';
import {
  CopyOutlined,
  DeleteOutlined,
  MoreOutlined,
  PlusOutlined,
  SaveOutlined,
  SnippetsOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps } from 'antd';
import type { Column, ScriptCell, ScriptCellContent, ScriptDecisionTableData } from '../model/types';
import { parseScriptDecisionTable } from '../model/parse';
import { serializeScriptDecisionTable } from '../model/serialize';
import { formPost, save } from '@/api/client';
import ScriptCellEditor from './ScriptCellEditor';
import ColumnEditor, { type ColumnDraft } from '../../decisiontable/react/ColumnEditor';

const { Text } = Typography;

export interface ScriptDecisionTableEditorProps {
  /** The script-decision-table file path (e.g. "/project/rules/foo.sdt.xml"). */
  file: string;
  /** Optional override for the load function (tests inject a stub). */
  onLoad?: (file: string) => Promise<string>;
  /** Optional override for the save function (tests inject a stub). */
  onSave?: (file: string, xml: string) => Promise<void>;
}

/** Build an empty script decision table (used when the server has nothing yet). */
function emptyTable(): ScriptDecisionTableData {
  return { libraries: [], columns: [], rows: [], cells: [] };
}

/**
 * Deep-clone a value independent of the source. Prefer the platform
 * `structuredClone` (Node ≥17, all evergreen browsers); fall back to a JSON
 * round-trip for the older jsdom test environment. Used by row copy/paste so a
 * pasted row never shares references with the row it was copied from.
 */
function deepClone<T>(value: T): T {
  if (typeof structuredClone === 'function') return structuredClone(value);
  return JSON.parse(JSON.stringify(value)) as T;
}

/**
 * Component-local clipboard payload for row copy/paste. Stores the source row's
 * ScriptCells (deep-cloned, rowspan clamped to 1; `.row` is a placeholder 0
 * until paste re-stamps it). We store cells rather than the whole Row because
 * Row is just {num, height} and the cells are where the content lives.
 */
interface ClipboardRow {
  /** Deep-cloned, rowspan-clamped source ScriptCells; `.row` is a placeholder (0). */
  cells: ScriptCell[];
  /** Source row height, copied onto the new row. */
  height: number;
}

/** Default loader: GET /common/loadXml, read raw XML under editorData.xml/content. */
async function loadFromServer(file: string): Promise<string> {
  type EditorDataLike = { xml?: string; content?: string };
  const data = await formPost<EditorDataLike[]>('/common/loadXml', { files: file });
  const editorData = Array.isArray(data) ? data[0] : undefined;
  if (!editorData) return '';
  return editorData.xml ?? editorData.content ?? '';
}

/** Default saver: URL-encode the XML and POST /common/saveFile. */
async function saveToServer(file: string, xml: string): Promise<void> {
  const url = (window._server ?? '') + '/common/saveFile';
  await save(url, {
    content: encodeURIComponent(xml),
    file: file,
    newVersion: 'false',
  });
}

export function ScriptDecisionTableEditor({
  file,
  onLoad = loadFromServer,
  onSave = saveToServer,
}: ScriptDecisionTableEditorProps) {
  const [state, setState] = useState<ScriptDecisionTableData>(emptyTable());
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [colModal, setColModal] = useState<{ open: boolean; editIndex?: number }>({ open: false });
  // Row-copy clipboard. `null` = nothing copied yet (disables the paste button).
  const [clipboardRow, setClipboardRow] = useState<ClipboardRow | null>(null);

  // ---- load on mount ----
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    onLoad(file)
      .then((xml) => {
        if (cancelled) return;
        if (xml && xml.trim().length > 0) {
          setState(parseScriptDecisionTable(xml));
        } else {
          setState(emptyTable());
        }
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setLoadError(err instanceof Error ? err.message : String(err));
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [file, onLoad]);

  // ---- cell accessors ----
  // Cells are keyed by (row, col) in the model; the editor indexes them
  // 1-based to match the legacy wire format. We map display row i (0-based)
  // to wire row i+1, and display col j to wire col j+1.
  const findCell = useCallback(
    (wireRow: number, wireCol: number) => state.cells.find((c) => c.row === wireRow && c.col === wireCol),
    [state.cells],
  );

  const getCellContent = useCallback(
    (wireRow: number, wireCol: number): ScriptCellContent => {
      const cell = findCell(wireRow, wireCol);
      return cell?.content ?? { script: '' };
    },
    [findCell],
  );

  const setCellContent = useCallback((wireRow: number, wireCol: number, content: ScriptCellContent) => {
    setState((prev) => {
      const cells = prev.cells.slice();
      const idx = cells.findIndex((c) => c.row === wireRow && c.col === wireCol);
      if (idx >= 0) {
        cells[idx] = { ...cells[idx], content };
      } else {
        cells.push({ row: wireRow, col: wireCol, rowspan: 1, content });
      }
      return { ...prev, cells };
    });
  }, []);

  // ---- column management ----
  const applyColumnDraft = useCallback((draft: ColumnDraft) => {
    setState((prev) => {
      const editIndex = colModal.editIndex;
      const columns = prev.columns.slice();
      if (editIndex !== undefined && editIndex >= 0 && editIndex < columns.length) {
        columns[editIndex] = { ...columns[editIndex], ...draft };
      } else {
        const nextNum = columns.length > 0 ? Math.max(...columns.map((c) => c.num)) + 1 : 0;
        columns.push({ num: nextNum, ...draft });
      }
      return { ...prev, columns };
    });
    setColModal({ open: false });
  }, [colModal.editIndex]);

  const removeColumn = useCallback((displayCol: number) => {
    setState((prev) => {
      const wireCol = displayCol + 1;
      const columns = prev.columns.filter((_, i) => i !== displayCol);
      // Renumber num to stay dense (0..n-1).
      const renumbered = columns.map((c, i) => ({ ...c, num: i }));
      // Drop cells that lived under the removed column.
      const cells = prev.cells.filter((c) => c.col !== wireCol);
      return { ...prev, columns: renumbered, cells };
    });
  }, []);

  // ---- row management ----
  const addRow = useCallback(() => {
    setState((prev) => {
      const nextNum = prev.rows.length > 0 ? Math.max(...prev.rows.map((r) => r.num)) + 1 : 0;
      const rows = prev.rows.concat([{ num: nextNum, height: 40 }]);
      return { ...prev, rows };
    });
  }, []);

  const removeRow = useCallback((displayRow: number) => {
    setState((prev) => {
      const wireRow = displayRow + 1;
      const rows = prev.rows.filter((_, i) => i !== displayRow).map((r, i) => ({ ...r, num: i }));
      const cells = prev.cells.filter((c) => c.row !== wireRow);
      return { ...prev, rows, cells };
    });
  }, []);

  // ---- row copy / paste ----
  //
  // Copy grabs the ScriptCells owned by the source wire-row (cell.row ===
  // wireRow), deep-clones them, clamps every rowspan to 1 (a copied single row
  // should not reproduce a source merge that spanned multiple rows), and clears
  // `.row` to a placeholder 0; paste re-stamps it. Paste inserts a new grid row
  // BELOW `displayRow`, shifts every existing cell whose wire-row is at/under
  // the insertion point down by one, drops in the clipboard cells re-stamped to
  // the new wire-row, and renumbers every Row.num to stay dense 0..n-1 (matching
  // removeRow's invariant). We deep-clone again on paste so repeated pastes are
  // independent of each other and of the source.
  //
  // displayRow = -1 → paste at the very top; displayRow = rows.length - 1 (or
  // undefined) → append at the bottom.
  const copyRow = useCallback(
    (displayRow: number) => {
      const wireRow = displayRow + 1;
      const owned = state.cells
        .filter((c) => c.row === wireRow)
        .map((c) => deepClone({ ...c, row: 0, rowspan: 1 }));
      const height = state.rows[displayRow]?.height ?? 40;
      setClipboardRow({ cells: owned, height });
      message.success('已复制行 ' + (displayRow + 1));
    },
    [state.cells, state.rows],
  );

  const pasteRow = useCallback(
    (displayRow: number) => {
      if (!clipboardRow) {
        message.warning('剪贴板为空,请先复制一行');
        return;
      }
      setState((prev) => {
        // Resolve the insertion index into a valid 0..rows.length slot.
        const insertAt = Math.max(0, Math.min(displayRow + 1, prev.rows.length));
        const insertWire = insertAt + 1; // 1-based grid row of the new row

        // 1. Renumber existing rows to stay dense 0..n after insertion.
        const rows = prev.rows.slice();
        rows.splice(insertAt, 0, { num: insertAt, height: clipboardRow.height });
        const renumberedRows = rows.map((r, i) => ({ ...r, num: i }));

        // 2. Shift cells at/under the insertion point down by one wire-row.
        const shifted = prev.cells.map((c) =>
          c.row >= insertWire ? { ...c, row: c.row + 1 } : c,
        );

        // 3. Drop in the clipboard cells, deep-cloned again and re-stamped.
        const pasted = clipboardRow.cells.map((c) =>
          deepClone({ ...c, row: insertWire }),
        );

        return { ...prev, rows: renumberedRows, cells: shifted.concat(pasted) };
      });
      message.success('已粘贴行');
    },
    [clipboardRow],
  );

  // ---- save ----
  const handleSave = useCallback(() => {
    setSaving(true);
    let xml: string;
    try {
      xml = serializeScriptDecisionTable(state);
    } catch (err) {
      setSaving(false);
      message.error('序列化失败: ' + (err instanceof Error ? err.message : String(err)));
      return;
    }
    onSave(file, xml)
      .then(() => {
        setSaving(false);
        message.success('保存成功');
      })
      .catch((err: unknown) => {
        setSaving(false);
        message.error('保存失败: ' + (err instanceof Error ? err.message : String(err)));
      });
  }, [state, file, onSave]);

  // ---- AntD table columns ----
  const antColumns: ColumnsType<number> = useMemo(() => {
    const cols: ColumnsType<number> = state.columns.map((col, displayCol) => ({
      title: columnTitle(col, displayCol),
      key: 'col-' + col.num,
      width: col.width,
      render: (_value, wireRow, _displayRow) => {
        const wireCol = displayCol + 1;
        const content = getCellContent(wireRow, wireCol);
        return (
          <ScriptCellEditor
            columnType={col.type}
            value={content}
            onChange={(next) => setCellContent(wireRow, wireCol, next)}
          />
        );
      },
    }));
    // Trailing action column: per-row copy/paste/delete via a ⋯ dropdown.
    // "粘贴到此行下方" is disabled when the clipboard is empty.
    cols.push({
      title: '',
      key: 'row-actions',
      width: 60,
      render: (_v, _wireRow, displayRow) => {
        const rowMenuItems: MenuProps['items'] = [
          {
            key: 'copy-row',
            label: '复制此行',
            icon: <CopyOutlined />,
            onClick: () => copyRow(displayRow),
          },
          {
            key: 'paste-row',
            label: '粘贴到此行下方',
            icon: <SnippetsOutlined />,
            disabled: !clipboardRow,
            onClick: () => pasteRow(displayRow),
          },
          { type: 'divider' },
          {
            key: 'delete-row',
            label: '删除此行',
            icon: <DeleteOutlined />,
            danger: true,
            onClick: () => removeRow(displayRow),
          },
        ];
        return (
          <Dropdown menu={{ items: rowMenuItems }} trigger={['click']}>
            <Button size="small" type="text" icon={<MoreOutlined />} />
          </Dropdown>
        );
      },
    });
    return cols;
  }, [state.columns, getCellContent, setCellContent, removeRow, copyRow, pasteRow, clipboardRow]);

  // Wire rows (1..n) — one table row per model row.
  const dataRows = useMemo(() => state.rows.map((r) => r.num + 1), [state.rows]);

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin description="加载脚本式决策表…" />
      </div>
    );
  }

  return (
    <div style={{ padding: 16, maxWidth: 1400, margin: '0 auto' }}>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
        <Text strong>脚本式决策表: {decodeURIComponent(file)}</Text>
        <Space>
          <Button
            icon={<PlusOutlined />}
            onClick={() => setColModal({ open: true })}
          >
            添加列
          </Button>
          <Button icon={<PlusOutlined />} onClick={addRow}>
            添加行
          </Button>
          <Button
            icon={<SnippetsOutlined />}
            disabled={!clipboardRow}
            onClick={() => pasteRow(state.rows.length - 1)}
          >
            粘贴行
          </Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
            保存
          </Button>
        </Space>
      </Space>

      {loadError && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="加载脚本式决策表失败,以空白表启动"
          description={loadError}
          closable={{ onClose: () => setLoadError(null) }}
        />
      )}

      {state.columns.length === 0 ? (
        <Alert
          type="info"
          showIcon
          message="还没有列"
          description='点击右上角"添加列"开始。每列是条件列或动作列,单元格内容是脚本(UL 表达式)。'
        />
      ) : (
        <Table<number>
          rowKey={(wireRow) => String(wireRow)}
          columns={antColumns}
          dataSource={dataRows}
          pagination={false}
          bordered
          size="small"
          scroll={{ x: 'max-content' }}
          title={() => (
            <Space>
              {state.columns.map((col, displayCol) => (
                <Button
                  key={'edit-col-' + col.num}
                  size="small"
                  type="link"
                  onClick={() => setColModal({ open: true, editIndex: displayCol })}
                >
                  编辑列{displayCol + 1}
                </Button>
              ))}
            </Space>
          )}
        />
      )}

      <ColumnEditor
        open={colModal.open}
        column={colModal.editIndex !== undefined ? state.columns[colModal.editIndex] : undefined}
        onOk={applyColumnDraft}
        onCancel={() => setColModal({ open: false })}
      />
    </div>
  );
}

/** Title cell for an AntD table column: shows the column type + variable label. */
function columnTitle(col: Column, displayCol: number): React.ReactNode {
  const typeLabel =
    col.type === 'Criteria' ? '条件'
      : col.type === 'Assignment' ? '赋值'
        : col.type === 'ConsolePrint' ? '打印'
          : '执行方法';
  const varLabel = col.variableLabel || col.variableName || `列${displayCol + 1}`;
  return (
    <div>
      <div style={{ fontWeight: 600 }}>{varLabel}</div>
      <div style={{ fontSize: 11, color: '#888' }}>{typeLabel}</div>
    </div>
  );
}

export default ScriptDecisionTableEditor;
