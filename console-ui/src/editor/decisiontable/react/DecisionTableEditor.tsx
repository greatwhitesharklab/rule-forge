/**
 * DecisionTableEditor — top-level React decision-table editor.
 *
 * Responsibilities:
 *   1. Load: fetch the decision-table XML → parseDecisionTable → state.
 *   2. Hold the DecisionTableData state at the top (single-direction data flow).
 *   3. Render: toolbar (save / add column / add row) + remark + AntD Table
 *      (columns from the table's columns, rows from the table's rows, each
 *      cell editable via CellEditor).
 *   4. Save: serializeDecisionTable → POST /common/saveFile (URL-encoded content).
 *
 * Data flow:
 *   loadXml → parseDecisionTable(state) ─┐
 *                                        ├→ React state (the only owner)
 *   cell/column edits via onChange ──────┘
 *   save button → serializeDecisionTable(state) → formPost(/common/saveFile)
 *
 * ── Loading note ─────────────────────────────────────────────────────────
 * /common/loadXml deserializes the file server-side for decision-table (a
 * registered DecisionTableDeserializer exists). This editor mirrors the
 * RulesetEditor approach: request the raw XML under `editorData.xml`
 * (falling back to `editorData.content`) and parse it client-side. An empty
 * string means a fresh file.
 *
 * ── TODO (handsontable features NOT yet ported) ──────────────────────────
 *   - freeze panes
 *   - copy/paste of cell ranges
 *   - per-row salience / property overrides (table-level only for now)
 *   - nested joints inside a Criteria cell (flat single condition only)
 *
 * ── Merged cells (rowspan) ───────────────────────────────────────────────
 * Criteria cells can span N rows: a "向下合并" gesture on a Criteria cell
 * bumps its rowspan and removes the cell on the next row (so the next row is
 * "covered" — the backend rule builder walks up to find the owner, see
 * DecisionTableRulesBuilder.getCell). onCell returns {rowSpan:N} for the owner
 * and {rowSpan:0} for covered rows (AntD's merge idiom). Right-click a Criteria
 * cell for the "向下合并" / "拆分合并" context menu. Action columns are never
 * merged.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Dropdown, Input, Space, Spin, Table, Typography, message } from 'antd';
import { DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps } from 'antd';
import type { CellContent, Column, DecisionTableData } from '../model/types';
import { parseDecisionTable } from '../model/parse';
import { serializeDecisionTable } from '../model/serialize';
import { formPost, save } from '@/api/client';
import CellEditor from './CellEditor';
import ColumnEditor, { type ColumnDraft } from './ColumnEditor';

const { Text } = Typography;

export interface DecisionTableEditorProps {
  /** The decision-table file path (e.g. "/project/rules/foo.dtx.xml"). */
  file: string;
  /** Optional override for the load function (tests inject a stub). */
  onLoad?: (file: string) => Promise<string>;
  /** Optional override for the save function (tests inject a stub). */
  onSave?: (file: string, xml: string) => Promise<void>;
}

/** Build an empty decision table (used when the server has nothing yet). */
function emptyTable(): DecisionTableData {
  return { remark: '', libraries: [], properties: [], columns: [], rows: [], cells: [] };
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

export function DecisionTableEditor({
  file,
  onLoad = loadFromServer,
  onSave = saveToServer,
}: DecisionTableEditorProps) {
  const [state, setState] = useState<DecisionTableData>(emptyTable());
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
          setState(parseDecisionTable(xml));
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

  const setCellContent = useCallback((wireRow: number, wireCol: number, content: CellContent) => {
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

  // ---- merged-cell (rowspan) helpers ----
  //
  // Merge semantics (matches the backend DecisionTableRulesBuilder.getCell
  // walk-up: server/lib/ruleforge-core/.../DecisionTableRulesBuilder.java).
  // A merged cell is represented as:
  //   - the TOP cell holds the content + rowspan=N (covers N rows total);
  //   - the N-1 rows BELOW it have NO <cell> element at that column at all
  //     (they are "covered" — the rule builder resolves them by walking up to
  //     the first row that has a cell).
  // So on merge-down we (a) bump the top cell's rowspan and (b) delete any
  // cell at the covered (row, col); on unmerge we reset the top rowspan to 1
  // and re-seed empty cells on the previously-covered rows.

  /**
   * Find the cell that OWNS (wireRow, wireCol) — i.e. the topmost cell at this
   * column whose rowspan range includes wireRow. Returns undefined when nothing
   * covers it (the cell is genuinely empty/unmerged). Mirrors the backend
   * getCell walk-up.
   */
  const findOwningCell = useCallback(
    (wireRow: number, wireCol: number) => {
      for (let r = wireRow; r >= 1; r--) {
        const c = state.cells.find((cc) => cc.row === r && cc.col === wireCol);
        if (c) {
          // Does this cell's rowspan reach down to wireRow?
          if (c.row + (c.rowspan || 1) - 1 >= wireRow) return c;
          return undefined; // a cell exists above but doesn't span to us
        }
      }
      return undefined;
    },
    [state.cells],
  );

  /** Merge the cell at (wireRow, wireCol) DOWN into the next row. */
  const mergeCellDown = useCallback((wireRow: number, wireCol: number) => {
    setState((prev) => {
      const top = prev.cells.find((c) => c.row === wireRow && c.col === wireCol);
      // No cell here, or the next row doesn't exist → nothing to merge.
      const maxRow = prev.rows.length > 0 ? Math.max(...prev.rows.map((r) => r.num)) + 1 : 0;
      const nextWireRow = wireRow + 1;
      if (!top || nextWireRow > maxRow) return prev;
      const cells = prev.cells.filter(
        (c) => !(c.row === nextWireRow && c.col === wireCol),
      );
      const topIdx = cells.findIndex((c) => c.row === wireRow && c.col === wireCol);
      cells[topIdx] = { ...cells[topIdx], rowspan: (top.rowspan || 1) + 1 };
      return { ...prev, cells };
    });
  }, []);

  /** Unmerge the cell at (wireRow, wireCol): collapse rowspan back to 1. */
  const unmergeCell = useCallback((wireRow: number, wireCol: number) => {
    setState((prev) => {
      const top = prev.cells.find((c) => c.row === wireRow && c.col === wireCol);
      if (!top || (top.rowspan || 1) <= 1) return prev;
      const cells = prev.cells.slice();
      const topIdx = cells.findIndex((c) => c.row === wireRow && c.col === wireCol);
      cells[topIdx] = { ...cells[topIdx], rowspan: 1 };
      // Re-seed empty cells on the rows that were covered so they become
      // independently editable (parse/serialize already treat absence as
      // covered; seeding keeps them visible as "无" instead of inherited).
      for (let r = wireRow + 1; r < wireRow + (top.rowspan || 1); r++) {
        if (!cells.some((c) => c.row === r && c.col === wireCol)) {
          cells.push({ row: r, col: wireCol, rowspan: 1, content: { empty: true } });
        }
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
      // Fix merges before dropping cells:
      //   - For each column, find the cell owning wireRow.
      //   - If the owning cell is ABOVE wireRow (covered by a merge) the removed
      //     row was inside a merge → shrink that merge's rowspan by 1.
      //   - If the owning cell IS wireRow and it had rowspan>1, the merge top
      //     is gone: collapse (the covered rows below just become ownerless /
      //     inherited-from-nothing, which the renderer treats as empty).
      const cells = prev.cells.map((c) => ({ ...c }));
      for (const col of prev.columns) {
        const wireCol = col.num + 1;
        // walk up to the owner at this column
        let owner: typeof cells[number] | undefined;
        for (let r = wireRow; r >= 1; r--) {
          const found = cells.find((c) => c.row === r && c.col === wireCol);
          if (found) { owner = found; break; }
        }
        if (!owner) continue;
        if (owner.row < wireRow && (owner.rowspan || 1) > 1) {
          owner.rowspan = (owner.rowspan || 1) - 1;
        } else if (owner.row === wireRow && (owner.rowspan || 1) > 1) {
          // Merge top removed → collapse; covered rows below become empty.
          owner.rowspan = 1;
        }
      }
      // Drop the cells on the removed row, then renumber surviving cell.row to
      // match the dense 1..n wire-row numbering (rows were renumbered above).
      const kept = cells.filter((c) => c.row !== wireRow);
      const renumbered = kept.map((c) => (c.row > wireRow ? { ...c, row: c.row - 1 } : c));
      return { ...prev, rows, cells: renumbered };
    });
  }, []);

  // ---- save ----
  const handleSave = useCallback(() => {
    setSaving(true);
    let xml: string;
    try {
      xml = serializeDecisionTable(state);
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
  // onCell drives AntD's cell-merge: the OWNING cell returns { rowSpan: N }
  // and every covered row below returns { rowSpan: 0 } (AntD hides rowSpan:0
  // cells, fusing them into the owner's vertical span). This is the standard
  // AntD Table rowspan idiom (see antd Table "colSpan and rowSpan" docs).
  const antColumns: ColumnsType<number> = useMemo(() => {
    const cols: ColumnsType<number> = state.columns.map((col, displayCol) => {
      const wireCol = displayCol + 1;
      return {
        title: columnTitle(col, displayCol),
        key: 'col-' + col.num,
        width: col.width,
        onCell: (wireRow) => {
          const owner = findOwningCell(wireRow, wireCol);
          if (!owner) return {}; // genuinely empty — single cell
          if (owner.row === wireRow) {
            // This row owns the cell → span down by its rowspan.
            return { rowSpan: owner.rowspan || 1 };
          }
          // Covered by an owner above → AntD hides this cell.
          return { rowSpan: 0 };
        },
        render: (_value, wireRow) => {
          // Render the OWNING cell's content (covered rows inherit the top).
          const owner = findOwningCell(wireRow, wireCol);
          const content: CellContent = owner?.content ?? { empty: true };
          const ownerRow = owner?.row ?? wireRow;
          const rowspan = owner?.rowspan ?? 1;
          // Merge / unmerge only makes sense on Criteria columns (the original
          // handsontable merged multi-condition criteria cells). Action columns
          // are always single-row.
          const mergeable = col.type === 'Criteria';
          // Next row exists? (wireRow is 1-based; max wireRow == rows.length)
          const hasNextRow = wireRow < state.rows.length;
          const menuItems: MenuProps['items'] = mergeable
            ? [
                {
                  key: 'merge-down',
                  label: '向下合并',
                  disabled: !hasNextRow,
                  onClick: () => mergeCellDown(ownerRow, wireCol),
                },
                {
                  key: 'unmerge',
                  label: '拆分合并',
                  disabled: rowspan <= 1,
                  onClick: () => unmergeCell(ownerRow, wireCol),
                },
              ]
            : [];
          const editor = (
            <CellEditor
              columnType={col.type}
              value={content}
              onChange={(next) => setCellContent(ownerRow, wireCol, next)}
            />
          );
          if (!mergeable) return editor;
          return (
            <Dropdown
              menu={{ items: menuItems }}
              trigger={['contextMenu']}
            >
              <div style={{ cursor: 'context-menu' }}>{editor}</div>
            </Dropdown>
          );
        },
      };
    });
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
  }, [state.columns, state.rows.length, findOwningCell, setCellContent, mergeCellDown, unmergeCell, removeRow]);

  // Wire rows (1..n) — one table row per model row.
  const dataRows = useMemo(() => state.rows.map((r) => r.num + 1), [state.rows]);

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin description="加载决策表…" />
      </div>
    );
  }

  return (
    <div style={{ padding: 16, maxWidth: 1400, margin: '0 auto' }}>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
        <Text strong>决策表: {decodeURIComponent(file)}</Text>
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
          message="加载决策表失败,以空白表启动"
          description={loadError}
          closable={{ onClose: () => setLoadError(null) }}
        />
      )}

      <div style={{ marginBottom: 12 }}>
        <Input.TextArea
          rows={2}
          placeholder="决策表备注 (remark)"
          value={state.remark}
          onChange={(e) => setState((prev) => ({ ...prev, remark: e.target.value }))}
        />
      </div>

      {state.columns.length === 0 ? (
        <Alert
          type="info"
          showIcon
          message="还没有列"
          description='点击右上角"添加列"开始。每列是条件列或动作列。'
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
        variableLibraryPaths={state.libraries
          .filter((lib) => lib.type === 'Variable' && lib.path)
          .map((lib) => lib.path)}
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

export default DecisionTableEditor;
