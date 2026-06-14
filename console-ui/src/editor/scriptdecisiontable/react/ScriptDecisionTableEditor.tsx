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
 *   - copy/paste of cell ranges
 *   - CodeMirror in-cell editing with UL autocomplete (legacy ScriptRenderers
 *     used CodeMirror modes `if` / `then` / `print`; React uses a textarea).
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Space, Spin, Table, Typography, message } from 'antd';
import { DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Column, ScriptCellContent, ScriptDecisionTableData } from '../model/types';
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
    // Trailing action column: delete row.
    cols.push({
      title: '',
      key: 'row-actions',
      width: 50,
      render: (_v, _wireRow, displayRow) => (
        <Button
          size="small"
          type="text"
          danger
          icon={<DeleteOutlined />}
          onClick={() => removeRow(displayRow)}
        />
      ),
    });
    return cols;
  }, [state.columns, getCellContent, setCellContent, removeRow]);

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
