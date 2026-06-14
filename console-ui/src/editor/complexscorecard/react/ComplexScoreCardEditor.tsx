/**
 * ComplexScoreCardEditor — top-level React complex scorecard (复杂评分卡) editor.
 *
 * Responsibilities:
 *   1. Load: fetch the complex-scorecard XML → parseComplexScoreCard → state.
 *   2. Hold the ComplexScoreCardData state at the top (single-direction data flow).
 *   3. Render:
 *      - config area (scoring-type dropdown + custom bean input +
 *        assign-target selector; remark text area)
 *      - AntD Table whose columns are the dynamic col list (Criteria / Score /
 *        Custom — each with its own header label) and whose rows are the flat
 *        row matrix. Each cell is looked up by (row, col) and edited via a
 *        type-appropriate editor (Criteria cell = variable binding + condition
 *        joint; Score / Custom cell = a ValueEditor).
 *      - cell editing reuses ruleforge ValueEditor (condition right value,
 *        score/custom value, assign-target value)
 *   4. Save: serializeComplexScoreCard → POST /common/saveFile (URL-encoded).
 *
 * Data flow mirrors the plain scorecard editor but is simpler because the
 * complex scorecard is a flat row×col matrix (no attribute-row / condition-row
 * nesting — rowspan carries the vertical-merge semantics instead).
 *
 * Reuse vs the plain scorecard editor:
 *   - ValueEditor (ruleforge) reused unchanged for condition right value,
 *     score value, custom value, assign-target value
 *   - OPERATOR_OPTIONS + opHasNoInput (ruleforge) reused for the condition op
 *   - The shared model types (AssignTarget / CardJoint / CardCondition /
 *     CardProperty / LibraryImport / ScoringType) are re-exported from
 *     ../model/types → imported from there
 *
 * ── TODO (jquery editor features NOT yet ported) ──────────────────────────
 *   - variable library browser for the Criteria cell + column (currently
 *     free-text category/var/datatype input)
 *   - rowspan editing (currently every cell is rowspan=1; the model supports
 *     rowspan but the UI doesn't expose a merge-cell gesture)
 *   - col width resize drag handles
 *
 * ── Row copy/paste ───────────────────────────────────────────────────────
 * Mirrors DecisionTableEditor's V5.60 pattern. Copy grabs every cell OWNED by
 * the source row (cell.row === row.num), deep-clones them, and clamps every
 * rowspan to 1 so a later paste never extends a merge beyond the single pasted
 * row. The stored cells carry a placeholder row (we keep the source's num; paste
 * re-stamps it). Paste APPENDS a fresh row at the end with a brand-new dense
 * `num = max(rows.num)+1` (matching addRow) and re-stamps every cell's `row` to
 * that new num. We append rather than splice because row `num` is a sparse
 * stable identity here (removeRow does NOT renumber), so splicing mid-table
 * would need an offset cascade — appending avoids that and the wire format
 * accepts non-contiguous row numbers (the backend walks rows by num). The whole
 * cell set is deep-cloned again on paste so repeated pastes are independent.
 *
 * ── Cell copy/paste ──────────────────────────────────────────────────────
 * Cell-level granularity on top of the row-level clipboard. A SEPARATE
 * `clipboardCell` holds a deep-cloned ComplexCell (the whole cell — row + col +
 * rowspan + content fields). Paste deep-clones the stored cell again and merges
 * ONLY the content fields onto the TARGET cell (keeping the target's
 * row/col/rowspan), matching the scorecard cell-clipboard approach. Content
 * fields are column-type-specific (Criteria → variable binding + joint;
 * Score/Custom → value), so we cherry-pick whichever the source has — a copied
 * Criteria joint pastes onto another Criteria cell, a copied score value onto
 * another score cell. This is independent of the row clipboard.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Dropdown, Input, InputNumber, Select, Space, Spin, Table, Typography, message } from 'antd';
import {
  CopyOutlined,
  DeleteOutlined,
  MoreOutlined,
  PlusOutlined,
  SaveOutlined,
  SnippetsOutlined,
  BlockOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps } from 'antd';
import type { ValueExpr } from '../../ruleforge/model/types';
import { OPERATOR_OPTIONS, opHasNoInput } from '../../ruleforge/react/constants';
import { ValueEditor } from '../../ruleforge/react/ValueEditor';
import {
  VariablePicker,
  useConstantLibraries,
  useParameterLibraries,
  useVariableLibraries,
  type VariableCategoryGroup,
} from '../../ruleforge/react';
import type { ConstantCategoryGroup } from '../../ruleforge/react/ConstantPicker';
import type { ParameterLibrary } from '../../ruleforge/react/ParameterPicker';
import type {
  AssignTarget,
  AssignTargetType,
  CardCondition,
  ComplexCell,
  ComplexCol,
  ComplexColType,
  ComplexRow,
  ComplexScoreCardData,
  ScoringType,
} from '../model/types';
import { parseComplexScoreCard } from '../model/parse';
import { serializeComplexScoreCard } from '../model/serialize';
import { formPost, save } from '@/api/client';

const { Text } = Typography;

export interface ComplexScoreCardEditorProps {
  /** The complex-scorecard file path. */
  file: string;
  /** Optional override for the load function (tests inject a stub). */
  onLoad?: (file: string) => Promise<string>;
  /** Optional override for the save function (tests inject a stub). */
  onSave?: (file: string, xml: string) => Promise<void>;
}

/** Build an empty complex scorecard (used when the server has nothing yet). */
function emptyCard(): ComplexScoreCardData {
  return {
    remark: '',
    properties: [],
    scoringType: 'sum',
    assignTarget: { type: 'none' },
    libraries: [],
    cols: [],
    rows: [],
    cells: [],
  };
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
 * CELLS (deep-cloned, rowspan clamped to 1). We store cells rather than the
 * whole row because the row is just {num, height} and the cells are where the
 * content lives; paste re-stamps every cell's `row` to the target num.
 */
interface ClipboardRow {
  /** Deep-cloned, rowspan-clamped source cells (`.row` is the source's num). */
  cells: ComplexCell[];
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

/** A flat display row for the AntD Table (one per <row num>). */
interface DisplayRow {
  /** Stable key. */
  key: string;
  /** The wire row number (matches <row num>). */
  rowNumber: number;
}

export function ComplexScoreCardEditor({
  file,
  onLoad = loadFromServer,
  onSave = saveToServer,
}: ComplexScoreCardEditorProps) {
  const [state, setState] = useState<ComplexScoreCardData>(emptyCard());
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  // Row-copy clipboard. `null` = nothing copied yet (disables the paste button).
  const [clipboardRow, setClipboardRow] = useState<ClipboardRow | null>(null);
  // Cell-copy clipboard. Holds a deep-cloned ComplexCell (content fields only —
  // row/col/rowspan are kept on the target). Separate from `clipboardRow` so
  // cell copy/paste never collides with row copy/paste. `null` = nothing
  // copied yet (disables the cell-paste menu item).
  const [clipboardCell, setClipboardCell] = useState<ComplexCell | null>(null);

  // Load the project's imported variable libraries once; passed down to the
  // cell editors so they can render the shared VariablePicker.
  const variableLibraryPaths = state.libraries
    .filter((lib) => lib.type === 'Variable' && lib.path)
    .map((lib) => lib.path);
  const { libraries: variableLibraries } = useVariableLibraries(variableLibraryPaths);

  // Parallel constant/parameter library paths — same derivation as variable.
  // The cell ValueEditors render ConstantPicker/ParameterPicker when these are
  // non-empty (otherwise free-text fallback).
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
          setState(parseComplexScoreCard(xml));
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
  const patchCell = useCallback((row: number, col: number, patch: Partial<ComplexCell>) => {
    setState((prev) => {
      const cells = prev.cells.slice();
      const idx = cells.findIndex((c) => c.row === row && c.col === col);
      if (idx >= 0) {
        cells[idx] = { ...cells[idx], ...patch };
      } else {
        cells.push({ row, col, rowspan: 1, ...patch });
      }
      return { ...prev, cells };
    });
  }, []);

  /** Replace a single cell wholesale. */
  const setCell = useCallback((row: number, col: number, cell: ComplexCell) => {
    setState((prev) => {
      const cells = prev.cells.slice();
      const idx = cells.findIndex((c) => c.row === row && c.col === col);
      const merged = { ...cell, row, col };
      if (idx >= 0) {
        cells[idx] = merged;
      } else {
        cells.push(merged);
      }
      return { ...prev, cells };
    });
  }, []);

  // ---- row management ----
  const addRow = useCallback(() => {
    setState((prev) => {
      const nextNum = prev.rows.reduce((m, r) => Math.max(m, r.num), -1) + 1;
      return { ...prev, rows: prev.rows.concat([{ num: nextNum, height: 40 }]) };
    });
  }, []);

  const removeRow = useCallback((rowNumber: number) => {
    setState((prev) => ({
      ...prev,
      rows: prev.rows.filter((r) => r.num !== rowNumber),
      cells: prev.cells.filter((c) => c.row !== rowNumber),
    }));
  }, []);

  // ---- row copy / paste ----
  //
  // Copy grabs every cell owned by the source row (cell.row === rowNumber),
  // deep-clones them, and clamps every rowspan to 1 so a later paste never
  // extends a merge beyond the single pasted row. We snapshot from the LIVE
  // render state (copy has no state transition of its own). Paste APPENDS a
  // fresh row at the end with num = max(rows.num)+1 and re-stamps every cell's
  // `row` to that new num (deep-cloned again so repeated pastes are
  // independent). Append-at-end keeps existing row nums stable (no cascade);
  // the wire format accepts non-contiguous row numbers.
  const copyRow = useCallback(
    (rowNumber: number) => {
      const owned = state.cells
        .filter((c) => c.row === rowNumber)
        .map((c) => deepClone({ ...c, rowspan: 1 }));
      const height = state.rows.find((r) => r.num === rowNumber)?.height ?? 40;
      setClipboardRow({ cells: owned, height });
      message.success('已复制行');
    },
    [state.cells, state.rows],
  );

  const pasteRow = useCallback(() => {
    if (!clipboardRow) {
      message.warning('剪贴板为空,请先复制一行');
      return;
    }
    setState((prev) => {
      const nextNum = prev.rows.reduce((m, r) => Math.max(m, r.num), -1) + 1;
      const newRow: ComplexRow = { num: nextNum, height: clipboardRow.height };
      // Re-stamp every clipboard cell to the new row num; deep-clone so
      // repeated pastes are fully independent of each other and of the source.
      const pasted = clipboardRow.cells.map((c) =>
        deepClone({ ...c, row: nextNum }),
      );
      return {
        ...prev,
        rows: prev.rows.concat([newRow]),
        cells: prev.cells.concat(pasted),
      };
    });
    message.success('已粘贴行');
  }, [clipboardRow]);

  // ---- cell copy / paste ----
  //
  // Cell-level copy/paste. Copy deep-clones the source ComplexCell at
  // (row, col) into `clipboardCell`. Paste deep-clones the stored cell again
  // and merges ONLY the content fields onto the target cell (keeping the
  // target's row/col/rowspan). Content fields are column-type-specific
  // (Criteria → variable binding + joint; Score/Custom → value); we
  // cherry-pick whichever the source has so a copied joint pastes onto another
  // Criteria cell, a copied value onto another score cell, etc. Independent of
  // the row clipboard.
  const copyCell = useCallback(
    (row: number, col: number) => {
      const cell = findCell(row, col);
      if (!cell) {
        message.warning('该单元格为空,无内容可复制');
        return;
      }
      setClipboardCell(deepClone(cell));
      message.success('已复制单元格内容');
    },
    [findCell],
  );

  const pasteCell = useCallback(
    (row: number, col: number) => {
      if (!clipboardCell) {
        message.warning('单元格剪贴板为空,请先复制一个单元格');
        return;
      }
      // Deep-clone the stored cell again so repeated pastes stay independent.
      const src = deepClone(clipboardCell);
      // Cherry-pick the content fields the source has; the target keeps its
      // row/col/rowspan.
      const patch: Partial<ComplexCell> = {};
      if (src.variableName !== undefined) patch.variableName = src.variableName;
      if (src.variableLabel !== undefined) patch.variableLabel = src.variableLabel;
      if (src.datatype !== undefined) patch.datatype = src.datatype;
      if (src.joint !== undefined) patch.joint = src.joint;
      if (src.value !== undefined) patch.value = src.value;
      setState((prev) => {
        const cells = prev.cells.slice();
        const idx = cells.findIndex((c) => c.row === row && c.col === col);
        if (idx >= 0) {
          cells[idx] = { ...cells[idx], ...patch };
        } else {
          cells.push({ row, col, rowspan: 1, ...patch });
        }
        return { ...prev, cells };
      });
      message.success('已粘贴单元格内容');
    },
    [clipboardCell],
  );

  // ---- col management ----
  const addCol = useCallback((type: ComplexColType, variableCategory?: string) => {
    setState((prev) => {
      const nextNum = prev.cols.reduce((m, c) => Math.max(m, c.num), -1) + 1;
      const col: ComplexCol = {
        num: nextNum,
        width: type === 'Score' ? 120 : 150,
        type,
      };
      if (type === 'Criteria') col.variableCategory = variableCategory ?? '';
      if (type === 'Custom') col.customLabel = '自定义列' + (nextNum);
      return { ...prev, cols: prev.cols.concat([col]) };
    });
  }, []);

  const removeCol = useCallback((colNumber: number) => {
    setState((prev) => ({
      ...prev,
      cols: prev.cols.filter((c) => c.num !== colNumber),
      cells: prev.cells.filter((c) => c.col !== colNumber),
    }));
  }, []);

  const patchCol = useCallback((colNumber: number, patch: Partial<ComplexCol>) => {
    setState((prev) => ({
      ...prev,
      cols: prev.cols.map((c) => (c.num === colNumber ? { ...c, ...patch } : c)),
    }));
  }, []);

  // ---- save ----
  const handleSave = useCallback(() => {
    setSaving(true);
    let xml: string;
    try {
      xml = serializeComplexScoreCard(state);
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
  const displayRows: DisplayRow[] = useMemo(
    () =>
      state.rows
        .slice()
        .sort((a, b) => a.num - b.num)
        .map((r) => ({ key: 'r' + r.num, rowNumber: r.num })),
    [state.rows],
  );

  // ---- AntD table columns (one per <col>) ----
  const antColumns: ColumnsType<DisplayRow> = useMemo(() => {
    /**
     * Wrap a cell editor in a right-click Dropdown that exposes cell-level
     * copy / paste (independent of the row clipboard). The menu's paste item
     * is disabled when the cell clipboard is empty.
     */
    const wrapCellContextMenu = (
      editor: React.ReactNode,
      row: number,
      col: number,
    ): React.ReactNode => {
      const items: MenuProps['items'] = [
        {
          key: 'copy-cell',
          label: '复制单元格',
          icon: <BlockOutlined />,
          onClick: () => copyCell(row, col),
        },
        {
          key: 'paste-cell',
          label: '粘贴单元格',
          icon: <SnippetsOutlined />,
          disabled: !clipboardCell,
          onClick: () => pasteCell(row, col),
        },
      ];
      return (
        <Dropdown menu={{ items }} trigger={['contextMenu']}>
          <div style={{ cursor: 'context-menu' }}>{editor}</div>
        </Dropdown>
      );
    };

    const cols: ColumnsType<DisplayRow> = state.cols
      .slice()
      .sort((a, b) => a.num - b.num)
      .map((col) => {
        const title =
          col.type === 'Criteria'
            ? col.variableCategory || '条件列' + (col.num + 1)
            : col.type === 'Score'
              ? '分值'
              : col.customLabel || '自定义列';
        return {
          title: (
            <Space direction="vertical" size={2} style={{ width: '100%' }}>
              <Text strong style={{ fontSize: 12 }}>{title}</Text>
              <Button
                size="small"
                type="text"
                danger
                icon={<DeleteOutlined />}
                onClick={() => removeCol(col.num)}
                style={{ padding: 0, height: 18, fontSize: 11 }}
              >
                删列
              </Button>
            </Space>
          ),
          key: 'col-' + col.num,
          width: col.width,
          render: (_v, dr) => {
            const cell = findCell(dr.rowNumber, col.num);
            const editor =
              col.type === 'Criteria' ? (
                <CriteriaCellEditor
                  value={cell}
                  libraries={variableLibraries}
                  constantLibraries={constantLibraries}
                  parameterLibraries={parameterLibraries}
                  onChange={(next) => setCell(dr.rowNumber, col.num, next)}
                />
              ) : (
                <ValueCellEditor
                  value={cell}
                  libraries={variableLibraries}
                  constantLibraries={constantLibraries}
                  parameterLibraries={parameterLibraries}
                  onChange={(next) => setCell(dr.rowNumber, col.num, next)}
                />
              );
            return wrapCellContextMenu(editor, dr.rowNumber, col.num);
          },
        };
      });

    // Trailing action column: per-row copy/paste/delete via a ⋯ dropdown.
    // "粘贴行" is disabled when the clipboard is empty.
    cols.push({
      title: '',
      key: 'row-actions',
      width: 60,
      render: (_v, dr) => {
        const rowMenuItems: MenuProps['items'] = [
          {
            key: 'copy-row',
            label: '复制此行',
            icon: <CopyOutlined />,
            onClick: () => copyRow(dr.rowNumber),
          },
          {
            key: 'paste-row',
            label: '粘贴行',
            icon: <SnippetsOutlined />,
            disabled: !clipboardRow,
            onClick: () => pasteRow(),
          },
          { type: 'divider' },
          {
            key: 'delete-row',
            label: '删除此行',
            icon: <DeleteOutlined />,
            danger: true,
            onClick: () => removeRow(dr.rowNumber),
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
  }, [state.cols, findCell, setCell, removeCol, removeRow, copyRow, pasteRow, clipboardRow, copyCell, pasteCell, clipboardCell, variableLibraries, constantLibraries, parameterLibraries]);

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin description="加载复杂评分卡…" />
      </div>
    );
  }

  return (
    <div style={{ padding: 16, maxWidth: 1400, margin: '0 auto' }}>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
        <Text strong>复杂评分卡: {decodeURIComponent(file)}</Text>
        <Space wrap>
          <Button icon={<PlusOutlined />} onClick={addRow}>添加行</Button>
          <Button icon={<PlusOutlined />} onClick={() => addCol('Criteria')}>添加条件列</Button>
          <Button icon={<PlusOutlined />} onClick={() => addCol('Score')}>添加分值列</Button>
          <Button icon={<PlusOutlined />} onClick={() => addCol('Custom')}>添加自定义列</Button>
          <Button
            icon={<SnippetsOutlined />}
            disabled={!clipboardRow}
            onClick={() => pasteRow()}
          >
            粘贴行
          </Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>保存</Button>
        </Space>
      </Space>

      {loadError && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="加载复杂评分卡失败,以空白卡启动"
          description={loadError}
          closable={{ onClose: () => setLoadError(null) }}
        />
      )}

      <ConfigArea
        state={state}
        setState={setState}
        patchCol={patchCol}
        libraries={variableLibraries}
        constantLibraries={constantLibraries}
        parameterLibraries={parameterLibraries}
      />

      <div style={{ marginBottom: 12 }}>
        <Input.TextArea
          rows={2}
          placeholder="复杂评分卡备注 (remark)"
          value={state.remark}
          onChange={(e) => setState((prev) => ({ ...prev, remark: e.target.value }))}
        />
      </div>

      {state.cols.length === 0 ? (
        <Alert
          type="info"
          showIcon
          message="还没有列"
          description='点击右上角"添加条件列/分值列/自定义列"开始。条件列(Criteria)放变量绑定+条件,分值列(Score)和自定义列(Custom)放数值表达式。'
        />
      ) : (
        <Table<DisplayRow>
          rowKey={(dr) => dr.key}
          columns={antColumns}
          dataSource={displayRows}
          pagination={false}
          bordered
          size="small"
          scroll={{ x: 'max-content' }}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Config area (scoring + assign-target + Criteria column category editors)
// ---------------------------------------------------------------------------

function ConfigArea({
  state,
  setState,
  patchCol,
  libraries = [],
  constantLibraries = [],
  parameterLibraries = [],
}: {
  state: ComplexScoreCardData;
  setState: React.Dispatch<React.SetStateAction<ComplexScoreCardData>>;
  patchCol: (colNumber: number, patch: Partial<ComplexCol>) => void;
  /** Imported variable libraries forwarded to the assign-target ValueEditor. */
  libraries?: VariableCategoryGroup[];
  /** Imported constant libraries forwarded to the assign-target ValueEditor. */
  constantLibraries?: ConstantCategoryGroup[];
  /** Imported parameter libraries forwarded to the assign-target ValueEditor. */
  parameterLibraries?: ParameterLibrary[];
}) {
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

      {/* Assign-target binding editor (variable/parameter). */}
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

      {/* Criteria column category editors (set each condition column's var-category). */}
      {state.cols.some((c) => c.type === 'Criteria') && (
        <Space wrap size="middle" style={{ marginTop: 8 }}>
          <Text type="secondary">条件列分类:</Text>
          {state.cols
            .filter((c) => c.type === 'Criteria')
            .map((c) => (
              <Space key={'cat-' + c.num} size={4}>
                <Text style={{ fontSize: 12 }}>列{c.num + 1}:</Text>
                <Input
                  style={{ width: 140 }}
                  size="small"
                  placeholder="var-category"
                  value={c.variableCategory ?? ''}
                  onChange={(e) => patchCol(c.num, { variableCategory: e.target.value })}
                />
              </Space>
            ))}
        </Space>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Cell editors
// ---------------------------------------------------------------------------

/**
 * Criteria cell: variable binding (free-text) + a single-condition joint.
 *
 * Mirrors the plain scorecard ConditionCellEditor shape, but the variable
 * binding lives on the cell (not the column) in the complex scorecard.
 */
function CriteriaCellEditor({
  value,
  libraries = [],
  constantLibraries = [],
  parameterLibraries = [],
  onChange,
}: {
  value: ComplexCell | undefined;
  /** Imported variable libraries (drives VariablePicker for the cell binding). */
  libraries?: VariableCategoryGroup[];
  /** Imported constant libraries forwarded to the right-hand ValueEditor. */
  constantLibraries?: ConstantCategoryGroup[];
  /** Imported parameter libraries forwarded to the right-hand ValueEditor. */
  parameterLibraries?: ParameterLibrary[];
  onChange: (next: ComplexCell) => void;
}) {
  const cell: ComplexCell = value ?? { row: 0, col: 0, rowspan: 1 };
  const joint = cell.joint ?? { type: 'and' as const, conditions: [] };
  const cond: CardCondition = joint.conditions[0] ?? { op: 'Equals' };
  const op = cond.op;
  const right: ValueExpr = cond.right ?? { type: 'Input', content: '' };
  const noRight = opHasNoInput(op);

  const patchBinding = (p: Partial<ComplexCell>) => onChange({ ...cell, ...p });
  const patchCondition = (next: { op?: string; right?: ValueExpr }) => {
    const newOp = next.op ?? op;
    const newCond: CardCondition = {
      op: newOp,
      ...(opHasNoInput(newOp) || next.right === undefined ? {} : { right: next.right ?? right }),
    };
    onChange({ ...cell, joint: { type: joint.type, conditions: [newCond] } });
  };

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
              // ComplexCell has no varCategory field (the category lives on
              // the Criteria column), so we leave it blank; the picker still
              // writes variableName/variableLabel/datatype to the cell.
              varCategory: '',
              var: cell.variableName,
              varLabel: cell.variableLabel,
              datatype: cell.datatype,
            }}
            onChange={(b) =>
              patchBinding({
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
              placeholder="变量名 (var)"
              value={cell.variableName ?? ''}
              onChange={(e) => patchBinding({ variableName: e.target.value })}
            />
            <Input
              size="small"
              placeholder="变量标签 (var-label)"
              value={cell.variableLabel ?? ''}
              onChange={(e) => patchBinding({ variableLabel: e.target.value })}
            />
            <Input
              size="small"
              placeholder="数据类型 (datatype)"
              value={cell.datatype ?? ''}
              onChange={(e) => patchBinding({ datatype: e.target.value })}
            />
          </>
        )}
        <Select
          size="small"
          style={{ width: '100%' }}
          value={op}
          onChange={(nextOp) => patchCondition({ op: nextOp })}
          options={OPERATOR_OPTIONS}
        />
        {!noRight && (
          <ValueEditor
            value={right}
            libraries={libraries}
            constantLibraries={constantLibraries}
            parameterLibraries={parameterLibraries}
            compact
            onChange={(v) => patchCondition({ right: v })}
          />
        )}
      </Space>
    </div>
  );
}

/** Score / Custom cell: a single value expression. */
function ValueCellEditor({
  value,
  libraries = [],
  constantLibraries = [],
  parameterLibraries = [],
  onChange,
}: {
  value: ComplexCell | undefined;
  /** Imported variable libraries forwarded to the cell ValueEditor. */
  libraries?: VariableCategoryGroup[];
  /** Imported constant libraries forwarded to the cell ValueEditor. */
  constantLibraries?: ConstantCategoryGroup[];
  /** Imported parameter libraries forwarded to the cell ValueEditor. */
  parameterLibraries?: ParameterLibrary[];
  onChange: (next: ComplexCell) => void;
}) {
  const cell: ComplexCell = value ?? { row: 0, col: 0, rowspan: 1 };
  const v: ValueExpr = cell.value ?? { type: 'Input', content: '' };
  return (
    <ValueEditor
      value={v}
      libraries={libraries}
      constantLibraries={constantLibraries}
      parameterLibraries={parameterLibraries}
      compact
      onChange={(value) => onChange({ ...cell, value })}
    />
  );
}

export default ComplexScoreCardEditor;
