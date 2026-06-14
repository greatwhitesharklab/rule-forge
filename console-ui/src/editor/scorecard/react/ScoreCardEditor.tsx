/**
 * ScoreCardEditor — top-level React scorecard (评分卡) editor.
 *
 * Responsibilities:
 *   1. Load: fetch the scorecard XML → parseScoreCard → state.
 *   2. Hold the ScoreCardData state at the top (single-direction data flow).
 *   3. Render:
 *      - config area (name + three col header names + weightSupport radio +
 *        scoring-type dropdown + custom bean input + assign-target selector)
 *      - remark text area
 *      - AntD Table of score rows (attribute row + its condition rows,
 *        grouped), columns = attribute / condition / score / [custom cols]
 *      - cell editing reuses ruleforge ValueEditor (condition right value,
 *        score value, custom value)
 *   4. Save: serializeScoreCard → POST /common/saveFile (URL-encoded content).
 *
 * Data flow:
 *   loadXml → parseScoreCard(state) ─┐
 *                                    ├→ React state (the only owner)
 *   cell/row/col edits via onChange ─┘
 *   save button → serializeScoreCard(state) → formPost(/common/saveFile)
 *
 * Row model: cells are keyed by (row, col). The original editor numbers rows
 * starting at 2 (row 1 is the header). An attribute row at row N occupies one
 * row slot; each condition row under it occupies the next slot. So if an
 * attribute row is at row 2 with one condition row, the condition row is at
 * row 3, and the next attribute row is at row 4. We track row slots explicitly
 * in the `rows` array (AttributeRow + nested ConditionRow carry the row
 * numbers).
 *
 * ── TODO (jquery editor features NOT yet ported) ──────────────────────────
 *   - variable library browser for the attribute cell (currently free-text
 *     category/var/datatype input)
 *   - per-row property overrides (salience / dates / enabled) — table-level
 *     only for now
 *   - col width resize drag handles
 *   - cell-level copy/paste (row-level copy/paste IS implemented below;
 *     single-cell / cell-range copy-paste is still TODO)
 *
 * ── Row copy/paste ───────────────────────────────────────────────────────
 * Mirrors DecisionTableEditor's V5.60 pattern. The copy granularity is a whole
 * ATTRIBUTE GROUP: an attribute row plus every nested condition row and every
 * cell those rows own. Cells are keyed by (row, col), and rowNumber is sparse
 * (attribute row at 2 + one condition row → next attribute row at 4), so paste
 * APPENDS a fresh group at the end with brand-new rowNumbers instead of
 * splicing mid-table — that keeps rowNumber stable for existing cells (no
 * renumber cascade) and the wire format accepts non-contiguous rows (the
 * backend walks rows by number, not by index). The whole group is deep-cloned
 * on both copy and paste so a pasted group never shares references with its
 * source. We store the source AttributeRow + its conditionRows + the cells in
 * a component-local clipboard (`clipboardGroup`); `null` disables the paste
 * button. Condition-row-only copy/paste is a TODO (row-level here means
 * attribute-group level).
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Dropdown, Input, InputNumber, Radio, Select, Space, Spin, Table, Typography, message,
} from 'antd';
import {
  CopyOutlined, DeleteOutlined, MoreOutlined, PlusOutlined, SaveOutlined, SnippetsOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps } from 'antd';
import type { ValueExpr } from '../../ruleforge/model/types';
import { OPERATOR_OPTIONS, opHasNoInput } from '../../ruleforge/react/constants';
import { ValueEditor } from '../../ruleforge/react/ValueEditor';
import {
  VariablePicker,
  useVariableLibraries,
  useConstantLibraries,
  useParameterLibraries,
  type VariableCategoryGroup,
} from '../../ruleforge/react';
import type { ConstantCategoryGroup } from '../../ruleforge/react/ConstantPicker';
import type { ParameterLibrary } from '../../ruleforge/react/ParameterPicker';
import type {
  AssignTarget,
  AssignTargetType,
  AttributeRow,
  CardCell,
  CardCellType,
  ScoreCardData,
  ScoringType,
} from '../model/types';
import { parseScoreCard } from '../model/parse';
import { serializeScoreCard } from '../model/serialize';
import { formPost, save } from '@/api/client';

const { Text } = Typography;

export interface ScoreCardEditorProps {
  /** The scorecard file path (e.g. "/project/rules/foo.sc.xml"). */
  file: string;
  /** Optional override for the load function (tests inject a stub). */
  onLoad?: (file: string) => Promise<string>;
  /** Optional override for the save function (tests inject a stub). */
  onSave?: (file: string, xml: string) => Promise<void>;
}

/** Build an empty scorecard (used when the server has nothing yet). */
function emptyCard(): ScoreCardData {
  return {
    name: '',
    remark: '',
    properties: [],
    weightSupport: false,
    attributeCol: { name: '属性', width: 200 },
    conditionCol: { name: '条件', width: 220 },
    scoreCol: { name: '分值', width: 180 },
    scoringType: 'sum',
    assignTarget: { type: 'none' },
    libraries: [],
    cells: [],
    rows: [],
    customCols: [],
  };
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

/**
 * A flat display row for the AntD Table. Either an attribute row or a
 * condition row (nested under an attribute row).
 */
interface DisplayRow {
  /** Stable key. */
  key: string;
  /** The wire row number (matches card-cell `row` attribute). */
  rowNumber: number;
  /** 'attribute' for the top row, 'condition' for a nested condition row. */
  kind: 'attribute' | 'condition';
  /** Reference back to the AttributeRow this row belongs to (for re-render). */
  attributeRowNumber: number;
}

/**
 * Deep-clone a value independent of the source. Prefer the platform
 * `structuredClone` (Node ≥17, all evergreen browsers); fall back to a JSON
 * round-trip for the older jsdom test environment. Used by row copy/paste so a
 * pasted group never shares references with the group it was copied from.
 */
function deepClone<T>(value: T): T {
  if (typeof structuredClone === 'function') return structuredClone(value);
  return JSON.parse(JSON.stringify(value)) as T;
}

/**
 * Component-local clipboard payload for attribute-group copy/paste. Stores the
 * source attribute row, its nested condition rows, and the cells owned by all
 * of those rows. Cell `.row` values are placeholders here (the source's own
 * rowNumbers); paste re-stamps every cell to a brand-new rowNumber.
 */
interface ClipboardGroup {
  /** Deep-cloned source attribute row (rowNumber placeholder). */
  attributeRow: AttributeRow;
  /** Deep-cloned source cells owned by the attribute row + its condition rows. */
  cells: CardCell[];
}

export function ScoreCardEditor({
  file,
  onLoad = loadFromServer,
  onSave = saveToServer,
}: ScoreCardEditorProps) {
  const [state, setState] = useState<ScoreCardData>(emptyCard());
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  // Attribute-group copy/paste clipboard. `null` = nothing copied yet (disables
  // the paste buttons).
  const [clipboardGroup, setClipboardGroup] = useState<ClipboardGroup | null>(null);

  // Load the project's imported variable libraries once (paths are stable per
  // scorecard). Passed down to ConfigArea + AttributeCellEditor so the
  // Variable/Parameter value editor + the attribute-cell binding can render
  // the shared VariablePicker instead of free-text inputs.
  const variableLibraryPaths = state.libraries
    .filter((lib) => lib.type === 'Variable' && lib.path)
    .map((lib) => lib.path);
  const { libraries: variableLibraries } = useVariableLibraries(variableLibraryPaths);

  // Same pattern for constant / parameter libraries — fed to the right-hand
  // ValueEditor so `<value type="Constant">` / `<value type="Parameter">` can
  // be picked from a Cascader instead of typed by hand.
  const constantLibraryPaths = state.libraries
    .filter((lib) => lib.type === 'Constant' && lib.path)
    .map((lib) => lib.path);
  const parameterLibraryPaths = state.libraries
    .filter((lib) => lib.type === 'Parameter' && lib.path)
    .map((lib) => lib.path);
  const { libraries: constantLibraries } = useConstantLibraries(constantLibraryPaths);
  const { libraries: parameterLibraries } = useParameterLibraries(parameterLibraryPaths);

  // ---- load on mount ----
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    onLoad(file)
      .then((xml) => {
        if (cancelled) return;
        if (xml && xml.trim().length > 0) {
          setState(parseScoreCard(xml));
        } else {
          setState(emptyCard());
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
  const findCell = useCallback(
    (row: number, col: number) => state.cells.find((c) => c.row === row && c.col === col),
    [state.cells],
  );

  /** Patch a single cell (creates it if missing). */
  const patchCell = useCallback((row: number, col: number, type: CardCellType, patch: Partial<CardCell>) => {
    setState((prev) => {
      const cells = prev.cells.slice();
      const idx = cells.findIndex((c) => c.row === row && c.col === col);
      if (idx >= 0) {
        cells[idx] = { ...cells[idx], ...patch };
      } else {
        cells.push({ type, row, col, ...patch });
      }
      return { ...prev, cells };
    });
  }, []);

  /** Replace a single cell wholesale (used by cell-type-specific editors). */
  const setCell = useCallback((row: number, col: number, type: CardCellType, cell: CardCell) => {
    setState((prev) => {
      const cells = prev.cells.slice();
      const idx = cells.findIndex((c) => c.row === row && c.col === col);
      const merged = { ...cell, type, row, col };
      if (idx >= 0) {
        cells[idx] = merged;
      } else {
        cells.push(merged);
      }
      return { ...prev, cells };
    });
  }, []);

  // ---- row management ----
  /**
   * Compute the next free wire row number (one past the highest currently in use).
   */
  const nextRowNumber = useCallback(() => {
    const maxCell = state.cells.reduce((m, c) => Math.max(m, c.row), 0);
    const maxRow = state.rows.reduce((m, r) => Math.max(m, r.rowNumber), 0);
    return Math.max(maxCell, maxRow, 1) + 1; // first row is 2 (header at 1)
  }, [state.cells, state.rows]);

  const addAttributeRow = useCallback(() => {
    setState((prev) => {
      const maxCell = prev.cells.reduce((m, c) => Math.max(m, c.row), 0);
      const maxRow = prev.rows.reduce((m, r) => Math.max(m, r.rowNumber), 0);
      const rowNumber = Math.max(maxCell, maxRow, 1) + 1;
      const rows = prev.rows.concat([{ rowNumber, conditionRows: [] }]);
      return { ...prev, rows };
    });
  }, []);

  /** Add a condition row under an attribute row. */
  const addConditionRow = useCallback((attributeRowNumber: number) => {
    setState((prev) => {
      // Insert the new condition row right after the attribute row's existing
      // condition rows (and before the next attribute row). For simplicity we
      // append at the end of the row-number space; the wire format only needs
      // the row numbers to be consistent with the cells, not contiguous.
      const rows = prev.rows.map((r) => {
        if (r.rowNumber !== attributeRowNumber) return r;
        const maxCond = r.conditionRows.reduce((m, c) => Math.max(m, c.rowNumber), attributeRowNumber);
        const newRowNumber = maxCond + 1;
        return { ...r, conditionRows: r.conditionRows.concat([{ rowNumber: newRowNumber }]) };
      });
      return { ...prev, rows };
    });
  }, []);

  const removeRow = useCallback((rowNumber: number, isCondition: boolean, attributeRowNumber?: number) => {
    setState((prev) => {
      const cells = prev.cells.filter((c) => c.row !== rowNumber);
      let rows = prev.rows;
      if (isCondition && attributeRowNumber !== undefined) {
        rows = prev.rows.map((r) =>
          r.rowNumber === attributeRowNumber
            ? { ...r, conditionRows: r.conditionRows.filter((c) => c.rowNumber !== rowNumber) }
            : r,
        );
      } else {
        rows = prev.rows.filter((r) => r.rowNumber !== rowNumber);
      }
      return { ...prev, cells, rows };
    });
  }, []);

  // ---- attribute-group copy / paste ----
  //
  // Copy grabs the source AttributeRow (incl. nested condition rows) and every
  // cell owned by one of those rows, deep-clones them, and stashes them in
  // `clipboardGroup`. Paste appends a fresh group at the end with brand-new
  // rowNumbers (one for the attribute row, one per nested condition row) and
  // re-stamps every cell's `row` to its new owner rowNumber. The whole group
  // is deep-cloned again on paste so repeated pastes are independent.
  //
  // We append at the end (rather than splicing mid-table) because rowNumber is
  // sparse and the wire format accepts non-contiguous rows — appending avoids
  // a renumber cascade across all existing cells.
  const copyAttributeRow = useCallback(
    (attributeRowNumber: number) => {
      const ar = state.rows.find((r) => r.rowNumber === attributeRowNumber);
      if (!ar) return;
      const ownedRowNumbers = new Set<number>([
        ar.rowNumber,
        ...ar.conditionRows.map((cr) => cr.rowNumber),
      ]);
      const ownedCells = state.cells
        .filter((c) => ownedRowNumbers.has(c.row))
        .map((c) => deepClone(c));
      setClipboardGroup({
        attributeRow: deepClone(ar),
        cells: ownedCells,
      });
      message.success('已复制属性组');
    },
    [state.rows, state.cells],
  );

  const pasteAttributeRow = useCallback(() => {
    if (!clipboardGroup) {
      message.warning('剪贴板为空,请先复制一个属性组');
      return;
    }
    setState((prev) => {
      // Allocate fresh, monotonically-increasing rowNumbers past every number
      // currently in use (cells + attribute rows + condition rows).
      const usedRowNumbers = new Set<number>();
      for (const c of prev.cells) usedRowNumbers.add(c.row);
      for (const r of prev.rows) {
        usedRowNumbers.add(r.rowNumber);
        for (const cr of r.conditionRows) usedRowNumbers.add(cr.rowNumber);
      }
      let next = 1;
      const alloc = (): number => {
        while (usedRowNumbers.has(next)) next++;
        usedRowNumbers.add(next);
        return next++;
      };

      // Map each source rowNumber to a brand-new one (attribute row first,
      // then condition rows in their original order).
      const newRowNumber = alloc();
      const conditionRowMap = new Map<number, number>();
      for (const cr of clipboardGroup.attributeRow.conditionRows) {
        conditionRowMap.set(cr.rowNumber, alloc());
      }

      // Build the new AttributeRow with re-stamped rowNumbers.
      const newAttributeRow: AttributeRow = {
        rowNumber: newRowNumber,
        conditionRows: clipboardGroup.attributeRow.conditionRows.map((cr) => ({
          rowNumber: conditionRowMap.get(cr.rowNumber)!,
        })),
      };

      // Re-stamp every cell to its new owner rowNumber (attribute row or one of
      // its condition rows). Cells belonging to the attribute row stay on the
      // new attribute rowNumber; condition-row cells move to the mapped number.
      const pastedCells = clipboardGroup.cells.map((c) => {
        const srcAttr = clipboardGroup.attributeRow.rowNumber;
        const targetRow =
          c.row === srcAttr ? newRowNumber : conditionRowMap.get(c.row) ?? newRowNumber;
        return deepClone({ ...c, row: targetRow });
      });

      return {
        ...prev,
        rows: prev.rows.concat([newAttributeRow]),
        cells: prev.cells.concat(pastedCells),
      };
    });
    message.success('已粘贴属性组');
  }, [clipboardGroup]);

  // ---- custom col management ----
  const addCustomCol = useCallback(() => {
    setState((prev) => {
      const nextColNumber = prev.customCols.reduce((m, c) => Math.max(m, c.colNumber), 3) + 1;
      const name = '自定义列' + (nextColNumber - 3);
      return { ...prev, customCols: prev.customCols.concat([{ colNumber: nextColNumber, name, width: 160 }]) };
    });
  }, []);

  const removeCustomCol = useCallback((colNumber: number) => {
    setState((prev) => ({
      ...prev,
      customCols: prev.customCols.filter((c) => c.colNumber !== colNumber),
      cells: prev.cells.filter((c) => c.col !== colNumber),
    }));
  }, []);

  // ---- save ----
  const handleSave = useCallback(() => {
    setSaving(true);
    let xml: string;
    try {
      xml = serializeScoreCard(state);
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

  // ---- build the flat display-row list ----
  const displayRows: DisplayRow[] = useMemo(() => {
    const out: DisplayRow[] = [];
    for (const ar of state.rows) {
      out.push({ key: 'r' + ar.rowNumber, rowNumber: ar.rowNumber, kind: 'attribute', attributeRowNumber: ar.rowNumber });
      for (const cr of ar.conditionRows) {
        out.push({ key: 'r' + cr.rowNumber, rowNumber: cr.rowNumber, kind: 'condition', attributeRowNumber: ar.rowNumber });
      }
    }
    return out;
  }, [state.rows]);

  // ---- AntD table columns ----
  const antColumns: ColumnsType<DisplayRow> = useMemo(() => {
    const cols: ColumnsType<DisplayRow> = [];

    // Attribute column (col=1) — only rendered on attribute rows.
    cols.push({
      title: state.attributeCol.name,
      key: 'attribute',
      width: state.attributeCol.width,
      render: (_v, dr) => {
        if (dr.kind !== 'attribute') {
          return null; // condition rows don't have an attribute cell (rowspan)
        }
        const cell = findCell(dr.rowNumber, 1);
        return (
          <AttributeCellEditor
            value={cell}
            weightSupport={state.weightSupport}
            libraries={variableLibraries}
            onChange={(next) => setCell(dr.rowNumber, 1, 'attribute', next)}
            onAddCondition={() => addConditionRow(dr.rowNumber)}
            onRemove={() => removeRow(dr.rowNumber, false)}
          />
        );
      },
    });

    // Condition column (col=2) — rendered on every row.
    cols.push({
      title: state.conditionCol.name,
      key: 'condition',
      width: state.conditionCol.width,
      render: (_v, dr) => {
        const cell = findCell(dr.rowNumber, 2);
        return (
          <ConditionCellEditor
            value={cell}
            libraries={variableLibraries}
            constantLibraries={constantLibraries}
            parameterLibraries={parameterLibraries}
            onChange={(next) => setCell(dr.rowNumber, 2, 'condition', next)}
            onRemove={
              dr.kind === 'condition'
                ? () => removeRow(dr.rowNumber, true, dr.attributeRowNumber)
                : undefined
            }
          />
        );
      },
    });

    // Score column (col=3) — rendered on every row.
    cols.push({
      title: state.scoreCol.name,
      key: 'score',
      width: state.scoreCol.width,
      render: (_v, dr) => {
        const cell = findCell(dr.rowNumber, 3);
        return (
          <ScoreCellEditor
            value={cell}
            libraries={variableLibraries}
            constantLibraries={constantLibraries}
            parameterLibraries={parameterLibraries}
            onChange={(next) => setCell(dr.rowNumber, 3, 'score', next)}
          />
        );
      },
    });

    // Custom columns (col=4+).
    for (const cc of state.customCols) {
      cols.push({
        title: cc.name,
        key: 'custom-' + cc.colNumber,
        width: cc.width,
        render: (_v, dr) => {
          const cell = findCell(dr.rowNumber, cc.colNumber);
          return (
            <CustomCellEditor
              value={cell}
              libraries={variableLibraries}
              constantLibraries={constantLibraries}
              parameterLibraries={parameterLibraries}
              onChange={(next) => setCell(dr.rowNumber, cc.colNumber, 'custom', next)}
            />
          );
        },
      });
    }

    // Trailing action column: per-row ⋯ dropdown (copy/paste/delete) on
    // attribute rows; plain delete button on condition rows. The attribute-row
    // menu's paste item copies + appends the clipboard group at the end.
    cols.push({
      title: '',
      key: 'row-actions',
      width: 50,
      render: (_v, dr) => {
        if (dr.kind === 'condition') {
          return (
            <Button
              size="small"
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={() => removeRow(dr.rowNumber, true, dr.attributeRowNumber)}
            />
          );
        }
        const rowMenuItems: MenuProps['items'] = [
          {
            key: 'copy-group',
            label: '复制属性组',
            icon: <CopyOutlined />,
            onClick: () => copyAttributeRow(dr.rowNumber),
          },
          {
            key: 'paste-group',
            label: '粘贴属性组',
            icon: <SnippetsOutlined />,
            disabled: !clipboardGroup,
            onClick: () => pasteAttributeRow(),
          },
          { type: 'divider' },
          {
            key: 'delete-group',
            label: '删除属性行',
            icon: <DeleteOutlined />,
            danger: true,
            onClick: () => removeRow(dr.rowNumber, false),
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
  }, [
    state.attributeCol,
    state.conditionCol,
    state.scoreCol,
    state.customCols,
    state.weightSupport,
    findCell,
    setCell,
    addConditionRow,
    removeRow,
    copyAttributeRow,
    pasteAttributeRow,
    clipboardGroup,
    variableLibraries,
    constantLibraries,
    parameterLibraries,
  ]);

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin description="加载评分卡…" />
      </div>
    );
  }

  return (
    <div style={{ padding: 16, maxWidth: 1400, margin: '0 auto' }}>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
        <Text strong>评分卡: {decodeURIComponent(file)}</Text>
        <Space wrap>
          <Button icon={<PlusOutlined />} onClick={addAttributeRow}>添加属性行</Button>
          <Button icon={<PlusOutlined />} onClick={addCustomCol}>添加自定义列</Button>
          <Button
            icon={<SnippetsOutlined />}
            disabled={!clipboardGroup}
            onClick={() => pasteAttributeRow()}
          >
            粘贴属性组
          </Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>保存</Button>
        </Space>
      </Space>

      {loadError && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="加载评分卡失败,以空白卡启动"
          description={loadError}
          closable={{ onClose: () => setLoadError(null) }}
        />
      )}

      <ConfigArea
        state={state}
        setState={setState}
        libraries={variableLibraries}
        constantLibraries={constantLibraries}
        parameterLibraries={parameterLibraries}
      />

      <div style={{ marginBottom: 12 }}>
        <Input.TextArea
          rows={2}
          placeholder="评分卡备注 (remark)"
          value={state.remark}
          onChange={(e) => setState((prev) => ({ ...prev, remark: e.target.value }))}
        />
      </div>

      {state.rows.length === 0 ? (
        <Alert
          type="info"
          showIcon
          message="还没有属性行"
          description='点击右上角"添加属性行"开始。每个属性行包含一个属性(变量)+若干条件行,每个条件行带条件+分值。'
        />
      ) : (
        <>
          <Table<DisplayRow>
            rowKey={(dr) => dr.key}
            columns={antColumns}
            dataSource={displayRows}
            pagination={false}
            bordered
            size="small"
            scroll={{ x: 'max-content' }}
          />
          {/* custom-col delete buttons (below the table, header-adjacent is hard in AntD) */}
          {state.customCols.length > 0 && (
            <Space style={{ marginTop: 8 }} wrap>
              <Text type="secondary">自定义列:</Text>
              {state.customCols.map((cc) => (
                <Button
                  key={'del-' + cc.colNumber}
                  size="small"
                  type="link"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => removeCustomCol(cc.colNumber)}
                >
                  {cc.name}
                </Button>
              ))}
            </Space>
          )}
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Config area (name + col headers + scoring + assign-target)
// ---------------------------------------------------------------------------

function ConfigArea({
  state,
  setState,
  libraries = [],
  constantLibraries,
  parameterLibraries,
}: {
  state: ScoreCardData;
  setState: React.Dispatch<React.SetStateAction<ScoreCardData>>;
  libraries?: VariableCategoryGroup[];
  /** Imported constant libraries forwarded to the assign-target ValueEditor. */
  constantLibraries?: ConstantCategoryGroup[];
  /** Imported parameter libraries forwarded to the assign-target ValueEditor. */
  parameterLibraries?: ParameterLibrary[];
}) {
  const patchName = (name: string) => setState((prev) => ({ ...prev, name }));
  const patchCol = (key: 'attributeCol' | 'conditionCol' | 'scoreCol') => (name: string) =>
    setState((prev) => ({ ...prev, [key]: { ...prev[key], name } }));
  const patchWeight = (weightSupport: boolean) =>
    setState((prev) => ({ ...prev, weightSupport }));
  const patchScoringType = (scoringType: ScoringType) =>
    setState((prev) => ({ ...prev, scoringType }));
  const patchCustomBean = (customScoringBean: string) =>
    setState((prev) => ({ ...prev, customScoringBean }));
  const patchAssignTarget = (assignTarget: AssignTarget) =>
    setState((prev) => ({ ...prev, assignTarget }));

  return (
    <div style={{ marginBottom: 12, padding: 12, border: '1px solid #eee', borderRadius: 6 }}>
      <Space wrap size="middle">
        <Space>
          <Text>名称:</Text>
          <Input
            style={{ width: 200 }}
            placeholder="评分卡名称 (必填)"
            value={state.name}
            onChange={(e) => patchName(e.target.value)}
          />
        </Space>
        <Space>
          <Text>权重:</Text>
          <Radio.Group
            value={state.weightSupport}
            onChange={(e) => patchWeight(e.target.value)}
          >
            <Radio value={true}>支持</Radio>
            <Radio value={false}>不支持</Radio>
          </Radio.Group>
        </Space>
        <Space>
          <Text>得分计算:</Text>
          <Select<ScoringType>
            style={{ width: 130 }}
            value={state.scoringType}
            onChange={patchScoringType}
            options={[
              { value: 'sum', label: '求和' },
              { value: 'weightsum', label: '加权求和' },
              { value: 'custom', label: '自定义' },
            ]}
          />
          {state.scoringType === 'custom' && (
            <Input
              style={{ width: 200 }}
              placeholder="自定义 Bean ID"
              value={state.customScoringBean ?? ''}
              onChange={(e) => patchCustomBean(e.target.value)}
            />
          )}
        </Space>
        <Space>
          <Text>得分赋值:</Text>
          <Select<AssignTargetType>
            style={{ width: 120 }}
            value={state.assignTarget.type}
            onChange={(type) => {
              if (type === 'none') {
                patchAssignTarget({ type: 'none' });
              } else if (type === 'variable') {
                patchAssignTarget({
                  type: 'variable',
                  value: { type: 'Variable', varCategory: '', var: '', varLabel: '', datatype: '' },
                });
              } else {
                patchAssignTarget({
                  type: 'parameter',
                  value: { type: 'Parameter', varCategory: '参数', var: '', varLabel: '', datatype: '' },
                });
              }
            }}
            options={[
              { value: 'variable', label: '变量' },
              { value: 'parameter', label: '参数' },
              { value: 'none', label: '不赋值' },
            ]}
          />
        </Space>
      </Space>

      <Space wrap size="middle" style={{ marginTop: 8 }}>
        <Space>
          <Text>属性行标题:</Text>
          <Input
            style={{ width: 120 }}
            value={state.attributeCol.name}
            onChange={(e) => patchCol('attributeCol')(e.target.value)}
          />
          <InputNumber
            style={{ width: 80 }}
            min={40}
            value={state.attributeCol.width}
            onChange={(w) =>
              setState((prev) => ({ ...prev, attributeCol: { ...prev.attributeCol, width: w ?? 200 } }))
            }
          />
        </Space>
        <Space>
          <Text>条件列标题:</Text>
          <Input
            style={{ width: 120 }}
            value={state.conditionCol.name}
            onChange={(e) => patchCol('conditionCol')(e.target.value)}
          />
        </Space>
        <Space>
          <Text>分值列标题:</Text>
          <Input
            style={{ width: 120 }}
            value={state.scoreCol.name}
            onChange={(e) => patchCol('scoreCol')(e.target.value)}
          />
        </Space>
      </Space>

      {state.assignTarget.type !== 'none' && state.assignTarget.value && (
        <div style={{ marginTop: 8 }}>
          <Text type="secondary">赋值目标变量:</Text>
          <div style={{ marginTop: 4 }}>
            <ValueEditor
              value={state.assignTarget.value}
              libraries={libraries}
              constantLibraries={constantLibraries}
              parameterLibraries={parameterLibraries}
              onChange={(value) => patchAssignTarget({ ...state.assignTarget, value })}
            />
          </div>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Cell editors (one per cell type)
// ---------------------------------------------------------------------------

/** Attribute cell: category / variable binding (free-text or VariablePicker) + optional weight. */
function AttributeCellEditor({
  value,
  weightSupport,
  libraries = [],
  onChange,
  onAddCondition,
  onRemove,
}: {
  value: CardCell | undefined;
  weightSupport: boolean;
  libraries?: VariableCategoryGroup[];
  onChange: (next: CardCell) => void;
  onAddCondition: () => void;
  onRemove: () => void;
}) {
  const cell: CardCell = value ?? { type: 'attribute', row: 0, col: 1 };
  const patch = (p: Partial<CardCell>) => onChange({ ...cell, ...p });

  // Map the CardCell's category/variableName/variableLabel/datatype fields
  // to/from the VariablePicker's VariableBinding (varCategory/var/…).
  const usePicker = libraries.some((lib) => (lib || []).length > 0);

  return (
    <div>
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        {usePicker ? (
          <VariablePicker
            compact
            libraries={libraries}
            allowDatatypeEdit
            value={{
              varCategory: cell.category,
              var: cell.variableName,
              varLabel: cell.variableLabel,
              datatype: cell.datatype,
            }}
            onChange={(b) =>
              patch({
                category: b.varCategory,
                variableName: b.var,
                variableLabel: b.varLabel,
                datatype: b.datatype,
              })
            }
          />
        ) : (
          <>
            <Input
              size="small"
              placeholder="分类 (category)"
              value={cell.category ?? ''}
              onChange={(e) => patch({ category: e.target.value })}
            />
            <Input
              size="small"
              placeholder="变量名 (var)"
              value={cell.variableName ?? ''}
              onChange={(e) => patch({ variableName: e.target.value })}
            />
            <Input
              size="small"
              placeholder="变量标签 (var-label)"
              value={cell.variableLabel ?? ''}
              onChange={(e) => patch({ variableLabel: e.target.value })}
            />
            <Input
              size="small"
              placeholder="数据类型 (datatype)"
              value={cell.datatype ?? ''}
              onChange={(e) => patch({ datatype: e.target.value })}
            />
          </>
        )}
        {weightSupport && (
          <Input
            size="small"
            placeholder="权重 (weight)"
            value={cell.weight ?? ''}
            onChange={(e) => patch({ weight: e.target.value })}
          />
        )}
        <Space size={4}>
          <Button size="small" type="link" icon={<PlusOutlined />} onClick={onAddCondition}>
            条件行
          </Button>
          <Button size="small" type="link" danger icon={<DeleteOutlined />} onClick={onRemove}>
            删除属性行
          </Button>
        </Space>
      </Space>
    </div>
  );
}

/** Condition cell: op dropdown + optional right value (single condition). */
function ConditionCellEditor({
  value,
  libraries,
  constantLibraries,
  parameterLibraries,
  onChange,
  onRemove,
}: {
  value: CardCell | undefined;
  /** Imported variable libraries forwarded to the right-hand ValueEditor. */
  libraries?: VariableCategoryGroup[];
  /** Imported constant libraries forwarded to the right-hand ValueEditor. */
  constantLibraries?: ConstantCategoryGroup[];
  /** Imported parameter libraries forwarded to the right-hand ValueEditor. */
  parameterLibraries?: ParameterLibrary[];
  onChange: (next: CardCell) => void;
  onRemove?: () => void;
}) {
  const cell: CardCell = value ?? { type: 'condition', row: 0, col: 2 };
  const joint = cell.joint ?? { type: 'and' as const, conditions: [] };
  const cond = joint.conditions[0];
  const op = cond?.op ?? 'Equals';
  const right: ValueExpr = cond?.right ?? { type: 'Input', content: '' };
  const noRight = opHasNoInput(op);

  const patchCondition = (next: { op?: string; right?: ValueExpr }) => {
    const newOp = next.op ?? op;
    const newCond = {
      op: newOp,
      ...(opHasNoInput(newOp) || next.right === undefined ? {} : { right: next.right ?? right }),
    };
    onChange({ ...cell, joint: { type: joint.type, conditions: [newCond] } });
  };

  return (
    <div>
      <div style={{ marginBottom: 4 }}>
        <Select
          size="small"
          style={{ width: '100%' }}
          value={op}
          onChange={(nextOp) => patchCondition({ op: nextOp })}
          options={OPERATOR_OPTIONS}
        />
      </div>
      {!noRight && (
        <ValueEditor
          value={right}
          compact
          libraries={libraries}
          constantLibraries={constantLibraries}
          parameterLibraries={parameterLibraries}
          onChange={(v) => patchCondition({ right: v })}
        />
      )}
      {onRemove && (
        <Button size="small" type="link" danger icon={<DeleteOutlined />} onClick={onRemove} style={{ padding: 0 }}>
          删除条件行
        </Button>
      )}
    </div>
  );
}

/** Score cell: a single value expression. */
function ScoreCellEditor({
  value,
  libraries,
  constantLibraries,
  parameterLibraries,
  onChange,
}: {
  value: CardCell | undefined;
  /** Imported variable libraries forwarded to the score ValueEditor. */
  libraries?: VariableCategoryGroup[];
  /** Imported constant libraries forwarded to the score ValueEditor. */
  constantLibraries?: ConstantCategoryGroup[];
  /** Imported parameter libraries forwarded to the score ValueEditor. */
  parameterLibraries?: ParameterLibrary[];
  onChange: (next: CardCell) => void;
}) {
  const cell: CardCell = value ?? { type: 'score', row: 0, col: 3 };
  const v: ValueExpr = cell.value ?? { type: 'Input', content: '' };
  return (
    <ValueEditor
      value={v}
      compact
      libraries={libraries}
      constantLibraries={constantLibraries}
      parameterLibraries={parameterLibraries}
      onChange={(value) => onChange({ ...cell, value })}
    />
  );
}

/** Custom cell: a single value expression. */
function CustomCellEditor({
  value,
  libraries,
  constantLibraries,
  parameterLibraries,
  onChange,
}: {
  value: CardCell | undefined;
  /** Imported variable libraries forwarded to the custom-cell ValueEditor. */
  libraries?: VariableCategoryGroup[];
  /** Imported constant libraries forwarded to the custom-cell ValueEditor. */
  constantLibraries?: ConstantCategoryGroup[];
  /** Imported parameter libraries forwarded to the custom-cell ValueEditor. */
  parameterLibraries?: ParameterLibrary[];
  onChange: (next: CardCell) => void;
}) {
  const cell: CardCell = value ?? { type: 'custom', row: 0, col: 4 };
  const v: ValueExpr = cell.value ?? { type: 'Input', content: '' };
  return (
    <ValueEditor
      value={v}
      compact
      libraries={libraries}
      constantLibraries={constantLibraries}
      parameterLibraries={parameterLibraries}
      onChange={(value) => onChange({ ...cell, value })}
    />
  );
}

export default ScoreCardEditor;
