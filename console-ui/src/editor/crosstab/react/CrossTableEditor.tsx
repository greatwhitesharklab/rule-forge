/**
 * CrossTableEditor — top-level React crosstab (交叉决策表) editor.
 *
 * A crosstab is a 2-D decision matrix:
 *   - TOP rows are condition rows (each binds a variable → they form the
 *     column-axis condition headers).
 *   - LEFT columns are condition columns (each binds a variable → they form
 *     the row-axis condition headers).
 *   - The intersection of a LEFT row and a TOP column is a VALUE cell holding
 *     a `<value>` that gets assigned to the crosstab's single assignment-target
 *     variable at runtime.
 *
 * This editor surfaces a flat shape (the original jquery editor supported
 * hierarchical rowspan/colspan merging; the React table renders a flat 2-D
 * grid for the first pass). It mirrors the DecisionTableEditor approach:
 *   1. Load: GET /common/loadXml → read raw XML under editorData.xml/content
 *      → parseCrossTable → state.
 *   2. Hold CrossTableData at the top (single-direction data flow).
 *   3. Render: toolbar (save / add row / add column / config) + remark +
 *      AntD Table where the grid is rows × columns and each cell is editable.
 *   4. Save: serializeCrossTable → POST /common/saveFile (URL-encoded content).
 *
 * Reuse: cross-cell values reuse the ruleforge ValueEditor (the `<value>` wire
 * format is identical); condition cells reuse the OPERATOR_OPTIONS + a flat
 * joint (matches the decision-table CellEditor). Excel import is a TODO.
 *
 * ── Merged cells (rowspan) ───────────────────────────────────────────────
 * Mirrors DecisionTableEditor V5.57 but adapted to the crosstab's SPARSE row
 * model (row `number` is a stable identity; removeRow never renumbers). Only
 * CONDITION cells merge (top-row cells + left-col cells — both form the
 * condition bands); VALUE cells (the LEFT-row × TOP-col intersection) never
 * merge. "向下合并" bumps the top condition cell's rowspan and removes the
 * condition cell on the next row (covered). onCell returns {rowSpan:N} for the
 * owner and {rowSpan:0} for covered rows (AntD's merge idiom). Right-click a
 * condition cell for "向下合并" / "拆分合并". colspan stays at 1 (only vertical
 * merges here — multi-level horizontal merging is still TODO). "Next row" is
 * the row with the smallest number strictly greater than the current.
 *
 * ── TODO (jquery features NOT yet ported) ──────────────────────────────────
 *   - multi-level horizontal colspan merging (vertical rowspan IS implemented)
 *   - Excel import (the toolbar button is wired but a no-op alert for now)
 *   - cell-range copy-paste (single-cell + row copy-paste IS implemented)
 *   - full condition-cell joint UI (flat single condition only, like DT)
 *
 * ── Row copy/paste ───────────────────────────────────────────────────────
 * Mirrors DecisionTableEditor's V5.60 pattern. The copy granularity is a single
 * ROW (top condition row or left data row) plus every cell that row owns:
 *   - a top row owns conditionCells on its row (the top band, every left col);
 *   - a left row owns conditionCells on its row (the left band, every left col
 *     — i.e. cells where row==thisRow && the col is a left col) AND valueCells
 *     on its row (every top col).
 * Paste appends a fresh row at the end of `rows` with a brand-new `number` and
 * re-stamps every cloned cell to that number. Columns are left untouched (a
 * pasted row lands on the same set of columns). The whole payload is
 * deep-cloned on both copy and paste so a pasted row never shares references
 * with its source. We store the source row + its cells in a component-local
 * clipboard (`clipboardRow`); `null` disables the paste buttons. Column
 * copy/paste is a TODO.
 *
 * ── Cell copy/paste ──────────────────────────────────────────────────────
 * Cell-level granularity on top of the row-level clipboard. A SEPARATE
 * `clipboardCell` holds a deep-cloned cell content tagged with its kind
 * ('condition' or 'value'), since the crosstab has two cell-content shapes
 * (ConditionCellContent vs ValueExpr). Paste deep-clones the stored content
 * again and writes it onto the target cell of the SAME kind (a condition
 * clipboard only pastes onto condition cells, a value clipboard onto value
 * cells) — this keeps the paste type-safe. Paste onto the other kind is a
 * no-op with a hint. This is independent of the row clipboard.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Dropdown, Input, Modal, Select, Space, Spin, Table, Typography, message,
} from 'antd';
import {
  CopyOutlined, DeleteOutlined, MoreOutlined, PlusOutlined, SaveOutlined, SettingOutlined, SnippetsOutlined, BlockOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps } from 'antd';
import type { ValueExpr } from '../../ruleforge/model/types';
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
import { OPERATOR_OPTIONS, opHasNoInput } from '../../ruleforge/react/constants';
import type {
  AssignTarget,
  BundleData,
  ConditionCellContent,
  ConditionCrossCell,
  CrossRow,
  CrossTableData,
  ValueCrossCell,
} from '../model/types';
import { parseCrossTable } from '../model/parse';
import { serializeCrossTable } from '../model/serialize';
import { formPost, save, apiBase } from '@/api/client';

const { Text } = Typography;

export interface CrossTableEditorProps {
  /** The crosstab file path (e.g. "/project/rules/foo.ct.xml"). */
  file: string;
  /** Optional override for the load function (tests inject a stub). */
  onLoad?: (file: string) => Promise<string>;
  /** Optional override for the save function (tests inject a stub). */
  onSave?: (file: string, xml: string) => Promise<void>;
}

/** Build an empty crosstab (used when the server has nothing yet). */
function emptyTable(): CrossTableData {
  return {
    remark: '',
    properties: [],
    header: { rowspan: 1, colspan: 1, text: 'TOP/LEFT' },
    libraries: [],
    rows: [],
    columns: [],
    conditionCells: [],
    valueCells: [],
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
 * Component-local clipboard payload for row copy/paste. Stores the source row
 * plus every cell it owns:
 *   - conditionCells whose `row` === source.number (both bands);
 *   - valueCells whose `row` === source.number (left rows only have these, but
 *     storing an empty list for a top row is harmless).
 * Stored cell `.row` values are placeholders (the source's own number); paste
 * re-stamps every cell to the new row's number.
 */
interface ClipboardRow {
  /** Deep-cloned source row (number placeholder). */
  row: CrossRow;
  /** Deep-cloned condition cells owned by the source row. */
  conditionCells: ConditionCrossCell[];
  /** Deep-cloned value cells owned by the source row. */
  valueCells: ValueCrossCell[];
}

/**
 * Component-local clipboard payload for cell copy/paste. Tagged with its kind
 * because the crosstab has two cell-content shapes — a condition-cell holds a
 * {@link ConditionCellContent} (empty | joint), a value-cell holds a
 * {@link ValueExpr}. Paste only writes onto a target of the SAME kind so the
 * paste stays type-safe (a condition clipboard never lands on a value cell).
 */
type ClipboardCell =
  | { kind: 'condition'; content: ConditionCellContent }
  | { kind: 'value'; content: ValueExpr | undefined };

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
  const url = apiBase() + '/common/saveFile';
  await save(url, {
    content: encodeURIComponent(xml),
    file: file,
    newVersion: 'false',
  });
}

export function CrossTableEditor({
  file,
  onLoad = loadFromServer,
  onSave = saveToServer,
}: CrossTableEditorProps) {
  const [state, setState] = useState<CrossTableData>(emptyTable());
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [targetModal, setTargetModal] = useState(false);
  // Row copy/paste clipboard. `null` = nothing copied yet (disables the paste
  // buttons).
  const [clipboardRow, setClipboardRow] = useState<ClipboardRow | null>(null);
  // Cell copy/paste clipboard. Tagged 'condition' | 'value'. `null` = nothing
  // copied yet (disables the cell-paste menu item). Independent of clipboardRow.
  const [clipboardCell, setClipboardCell] = useState<ClipboardCell | null>(null);

  // Load the project's imported variable libraries once; passed down to the
  // bundle/target modals so they can render the shared VariablePicker.
  const variableLibraryPaths = state.libraries
    .filter((lib) => lib.type === 'Variable' && lib.path)
    .map((lib) => lib.path);
  const { libraries: variableLibraries } = useVariableLibraries(variableLibraryPaths);

  // Imported constant / parameter library paths — parallel to the variable
  // derivation above. These feed the shared useConstantLibraries /
  // useParameterLibraries hooks so the right-hand ValueEditor inside a value
  // cell or condition cell can render a ConstantPicker / ParameterPicker
  // Cascader instead of free text. The bundle/target modals are NOT fed these
  // (they bind a whole variable axis via VariablePicker, not a `<value>`).
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
          setState(parseCrossTable(xml));
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
  // Value cells live at the intersection of a LEFT row and a TOP column.
  // Condition cells live at the intersection of a TOP row / TOP col (top band)
  // or a LEFT row / LEFT col (left band). For the flat grid we index by the
  // wire (row, col) numbers (1-based).
  const findValueCell = useCallback(
    (row: number, col: number) => state.valueCells.find((c) => c.row === row && c.col === col),
    [state.valueCells],
  );

  const setValueCell = useCallback((row: number, col: number, value: ValueExpr | undefined) => {
    setState((prev) => {
      const cells = prev.valueCells.slice();
      const idx = cells.findIndex((c) => c.row === row && c.col === col);
      if (idx >= 0) {
        if (value === undefined) {
          // Empty value: keep an empty value-cell (no <value> child).
          cells[idx] = { row, col };
        } else {
          cells[idx] = { row, col, value };
        }
      } else if (value !== undefined) {
        cells.push({ row, col, value });
      }
      return { ...prev, valueCells: cells };
    });
  }, []);

  const setConditionCell = useCallback((row: number, col: number, content: ConditionCellContent) => {
    setState((prev) => {
      const cells = prev.conditionCells.slice();
      const idx = cells.findIndex((c) => c.row === row && c.col === col);
      const next: ConditionCrossCell = { row, col, rowspan: 1, colspan: 1, content };
      if (idx >= 0) {
        cells[idx] = { ...cells[idx], content };
      } else {
        cells.push(next);
      }
      return { ...prev, conditionCells: cells };
    });
  }, []);

  // ---- merged-cell (rowspan) helpers ----
  //
  // Merge semantics mirror DecisionTableEditor V5.57 but adapted to the
  // crosstab's SPARSE row model (row `number` is a stable identity; removeRow
  // never renumbers). Only CONDITION cells merge (top-row cells and left-col
  // cells — both form the condition bands); VALUE cells (the LEFT-row × TOP-col
  // intersection) never merge. colspan is left at 1 (only vertical merges here).
  //   - the TOP cell holds the content + rowspan=N (covers N rows total);
  //   - the N-1 rows BELOW it have NO condition-cell at that (row, col) — they
  //     are covered.
  // "Next row" = the row with the smallest number strictly greater than the
  // current (rows are sparse, not num+1).

  /**
   * Find the condition cell that OWNS (row, col) — i.e. the topmost condition
   * cell at this column whose rowspan range covers `row`. Returns undefined
   * when nothing covers it. Walks up the sorted row-number order.
   */
  const findConditionOwningCell = useCallback(
    (row: number, col: number): ConditionCrossCell | undefined => {
      const rowNums = state.rows.map((r) => r.number).sort((a, b) => a - b);
      const idx = rowNums.indexOf(row);
      if (idx < 0) return undefined;
      for (let i = idx; i >= 0; i--) {
        const c = state.conditionCells.find((cc) => cc.row === rowNums[i] && cc.col === col);
        if (c) {
          const spanEndIdx = i + (c.rowspan || 1) - 1;
          if (spanEndIdx >= idx) return c;
          return undefined;
        }
      }
      return undefined;
    },
    [state.conditionCells, state.rows],
  );

  /**
   * The row number immediately BELOW `row` in the sorted row-number order, or
   * undefined if `row` is the last row.
   */
  const nextRowNum = useCallback(
    (row: number): number | undefined => {
      const nums = state.rows.map((r) => r.number).sort((a, b) => a - b);
      const i = nums.indexOf(row);
      if (i < 0 || i >= nums.length - 1) return undefined;
      return nums[i + 1];
    },
    [state.rows],
  );

  /** Merge the condition cell at (row, col) DOWN into the next row. */
  const mergeConditionCellDown = useCallback((row: number, col: number) => {
    setState((prev) => {
      const nums = prev.rows.map((r) => r.number).sort((a, b) => a - b);
      const i = nums.indexOf(row);
      if (i < 0 || i >= nums.length - 1) return prev; // no next row
      const top = prev.conditionCells.find((c) => c.row === row && c.col === col);
      if (!top) return prev;
      const nextNum = nums[i + 1];
      const cells = prev.conditionCells.filter(
        (c) => !(c.row === nextNum && c.col === col),
      );
      const topIdx = cells.findIndex((c) => c.row === row && c.col === col);
      cells[topIdx] = { ...cells[topIdx], rowspan: (top.rowspan || 1) + 1 };
      return { ...prev, conditionCells: cells };
    });
  }, []);

  /** Unmerge the condition cell at (row, col): collapse rowspan back to 1. */
  const unmergeConditionCell = useCallback((row: number, col: number) => {
    setState((prev) => {
      const top = prev.conditionCells.find((c) => c.row === row && c.col === col);
      if (!top || (top.rowspan || 1) <= 1) return prev;
      const nums = prev.rows.map((r) => r.number).sort((a, b) => a - b);
      const startIdx = nums.indexOf(row);
      const cells = prev.conditionCells.slice();
      const topIdx = cells.findIndex((c) => c.row === row && c.col === col);
      cells[topIdx] = { ...cells[topIdx], rowspan: 1 };
      // Re-seed empty condition cells on the rows that were covered.
      for (let k = 1; k < (top.rowspan || 1); k++) {
        const coveredRow = nums[startIdx + k];
        if (coveredRow === undefined) break;
        if (!cells.some((c) => c.row === coveredRow && c.col === col)) {
          cells.push({
            row: coveredRow,
            col,
            rowspan: 1,
            colspan: 1,
            content: { empty: true },
          });
        }
      }
      return { ...prev, conditionCells: cells };
    });
  }, []);

  // ---- row management ----
  const addLeftRow = useCallback(() => {
    setState((prev) => {
      const nextNum = prev.rows.length > 0 ? Math.max(...prev.rows.map((r) => r.number)) + 1 : 1;
      return { ...prev, rows: prev.rows.concat([{ number: nextNum, type: 'left' }]) };
    });
  }, []);

  const addTopRow = useCallback(() => {
    setState((prev) => {
      const nextNum = prev.rows.length > 0 ? Math.max(...prev.rows.map((r) => r.number)) + 1 : 1;
      return { ...prev, rows: prev.rows.concat([{ number: nextNum, type: 'top', bundleData: { type: 'variable', variableCategory: '' } }]) };
    });
  }, []);

  const removeRow = useCallback((displayRow: number) => {
    setState((prev) => {
      const removed = prev.rows[displayRow];
      if (!removed) return prev;
      const wireRow = removed.number;
      // Fix condition-cell merges before dropping the row:
      //   - For each condition column, walk up to the cell OWNING this row.
      //   - If the owning cell is ABOVE this row (covered by a merge) the
      //     removed row was inside a merge → shrink that merge's rowspan by 1.
      //   - If the owning cell IS this row and it had rowspan>1, the merge top
      //     is gone: collapse (covered rows below become ownerless, treated as
      //     empty by the renderer).
      // Sparse rows: walk by sorted number order, not number±1.
      const nums = prev.rows.map((r) => r.number).sort((a, b) => a - b);
      const removedIdx = nums.indexOf(wireRow);
      if (removedIdx < 0) return prev;
      // Only LEFT (condition) columns can carry merged condition cells.
      const conditionCols = prev.columns.filter((c) => c.type === 'left').map((c) => c.number);
      const conditionCells = prev.conditionCells.map((c) => ({ ...c }));
      for (const col of conditionCols) {
        let owner: typeof conditionCells[number] | undefined;
        for (let i = removedIdx; i >= 0; i--) {
          const found = conditionCells.find((c) => c.row === nums[i] && c.col === col);
          if (found) { owner = found; break; }
        }
        if (!owner) continue;
        if (owner.row !== wireRow && (owner.rowspan || 1) > 1) {
          const ownerIdx = nums.indexOf(owner.row);
          const spanEnd = ownerIdx + (owner.rowspan || 1) - 1;
          if (spanEnd >= removedIdx) owner.rowspan = (owner.rowspan || 1) - 1;
        } else if (owner.row === wireRow && (owner.rowspan || 1) > 1) {
          owner.rowspan = 1;
        }
      }
      const rows = prev.rows.filter((_, i) => i !== displayRow);
      return {
        ...prev,
        rows,
        conditionCells: conditionCells.filter((c) => c.row !== wireRow),
        valueCells: prev.valueCells.filter((c) => c.row !== wireRow),
      };
    });
  }, []);

  const configureTopRowBundle = useCallback((displayRow: number, bundle: BundleData) => {
    setState((prev) => {
      const rows = prev.rows.slice();
      rows[displayRow] = { ...rows[displayRow], bundleData: bundle };
      return { ...prev, rows };
    });
  }, []);

  // ---- row copy / paste ----
  //
  // Copy grabs the source row plus every cell whose `row` === source.number
  // (conditionCells + valueCells — top rows have only conditionCells, left rows
  // have conditionCells on left cols + valueCells on top cols), deep-clones
  // them, and stashes them in `clipboardRow`. Paste appends a fresh row at the
  // end of `rows` with a brand-new `number` and re-stamps every cloned cell to
  // that number. Columns are untouched (the pasted row lands on the same set
  // of columns). The whole payload is deep-cloned again on paste so repeated
  // pastes are independent.
  //
  // We append at the end (rather than splicing mid-grid) because the wire
  // format accepts non-contiguous row numbers (removeRow never renumbers
  // either) — appending avoids shifting every cell below the insertion point.
  const copyRow = useCallback(
    (displayRow: number) => {
      const row = state.rows[displayRow];
      if (!row) return;
      const wireRow = row.number;
      const conditionCells = state.conditionCells
        .filter((c) => c.row === wireRow)
        // Clamp rowspan to 1 so a later paste never extends a merge beyond the
        // single pasted row (the original merge spanned specific source rows;
        // copying one row should not reproduce that span). Matches DT V5.57.
        .map((c) => deepClone({ ...c, rowspan: 1 }));
      const valueCells = state.valueCells
        .filter((c) => c.row === wireRow)
        .map((c) => deepClone(c));
      setClipboardRow({ row: deepClone(row), conditionCells, valueCells });
      message.success('已复制行 ' + wireRow);
    },
    [state.rows, state.conditionCells, state.valueCells],
  );

  const pasteRow = useCallback(() => {
    if (!clipboardRow) {
      message.warning('剪贴板为空,请先复制一行');
      return;
    }
    setState((prev) => {
      // Allocate a brand-new row number past every number currently in use.
      const nextNum =
        prev.rows.length > 0 ? Math.max(...prev.rows.map((r) => r.number)) + 1 : 1;
      const newRow: CrossRow = { ...deepClone(clipboardRow.row), number: nextNum };
      // Re-stamp every cloned cell to the new row number (deep-clone again so
      // repeated pastes don't share references).
      const conditionCells = clipboardRow.conditionCells.map((c) =>
        deepClone({ ...c, row: nextNum }),
      );
      const valueCells = clipboardRow.valueCells.map((c) =>
        deepClone({ ...c, row: nextNum }),
      );
      return {
        ...prev,
        rows: prev.rows.concat([newRow]),
        conditionCells: prev.conditionCells.concat(conditionCells),
        valueCells: prev.valueCells.concat(valueCells),
      };
    });
    message.success('已粘贴行');
  }, [clipboardRow]);

  // ---- cell copy / paste ----
  //
  // Cell-level copy/paste. Copy deep-clones the cell content at (wireRow,
  // wireCol) — a condition-cell's ConditionCellContent or a value-cell's
  // ValueExpr — tagged with its kind into `clipboardCell`. Paste deep-clones
  // the stored content again and writes it onto a target of the SAME kind: a
  // 'condition' clipboard only pastes onto condition cells, a 'value' clipboard
  // only onto value cells. Paste onto the other kind is a no-op with a hint
  // (so the user learns why nothing happened). This is independent of the row
  // clipboard.
  const copyCell = useCallback(
    (wireRow: number, wireCol: number, kind: 'condition' | 'value') => {
      if (kind === 'condition') {
        // Copy the OWNER's content (top of a merge when the cell is covered).
        const owner = findConditionOwningCell(wireRow, wireCol);
        const content: ConditionCellContent = owner?.content ?? { empty: true };
        setClipboardCell({ kind: 'condition', content: deepClone(content) });
      } else {
        const cell = findValueCell(wireRow, wireCol);
        setClipboardCell({ kind: 'value', content: deepClone(cell?.value) });
      }
      message.success('已复制单元格内容');
    },
    [findConditionOwningCell, findValueCell],
  );

  const pasteCell = useCallback(
    (wireRow: number, wireCol: number, kind: 'condition' | 'value') => {
      if (!clipboardCell) {
        message.warning('单元格剪贴板为空,请先复制一个单元格');
        return;
      }
      if (clipboardCell.kind !== kind) {
        message.warning('剪贴板单元格类型不匹配(条件 ↔ 值)');
        return;
      }
      // Branch on clipboardCell.kind (not `kind`) so TypeScript narrows the
      // discriminated-union content to the matching shape.
      if (clipboardCell.kind === 'condition') {
        // Resolve the OWNER of the target (top of a merge when the cell is
        // covered) so a paste onto a covered cell writes onto the merge owner,
        // not into a phantom covered slot.
        setState((prev) => {
          const nums = prev.rows.map((r) => r.number).sort((a, b) => a - b);
          let ownerRow = wireRow;
          const idx = nums.indexOf(wireRow);
          if (idx >= 0) {
            for (let i = idx; i >= 0; i--) {
              const c = prev.conditionCells.find((cc) => cc.row === nums[i] && cc.col === wireCol);
              if (c) {
                if (i + (c.rowspan || 1) - 1 >= idx) ownerRow = c.row;
                break;
              }
            }
          }
          const content = deepClone(clipboardCell.content);
          const cells = prev.conditionCells.slice();
          const targetIdx = cells.findIndex((c) => c.row === ownerRow && c.col === wireCol);
          if (targetIdx >= 0) {
            cells[targetIdx] = { ...cells[targetIdx], content };
          } else {
            cells.push({ row: ownerRow, col: wireCol, rowspan: 1, colspan: 1, content });
          }
          return { ...prev, conditionCells: cells };
        });
      } else {
        setValueCell(wireRow, wireCol, deepClone(clipboardCell.content));
      }
      message.success('已粘贴单元格内容');
    },
    [clipboardCell, setValueCell],
  );

  // ---- column management ----
  const addTopColumn = useCallback(() => {
    setState((prev) => {
      const nextNum = prev.columns.length > 0 ? Math.max(...prev.columns.map((c) => c.number)) + 1 : 1;
      return { ...prev, columns: prev.columns.concat([{ number: nextNum, type: 'top' }]) };
    });
  }, []);

  const addLeftColumn = useCallback(() => {
    setState((prev) => {
      const nextNum = prev.columns.length > 0 ? Math.max(...prev.columns.map((c) => c.number)) + 1 : 1;
      return { ...prev, columns: prev.columns.concat([{ number: nextNum, type: 'left', bundleData: { type: 'variable', variableCategory: '' } }]) };
    });
  }, []);

  const removeColumn = useCallback((displayCol: number) => {
    setState((prev) => {
      const removed = prev.columns[displayCol];
      if (!removed) return prev;
      const wireCol = removed.number;
      const columns = prev.columns.filter((_, i) => i !== displayCol);
      const conditionCells = prev.conditionCells.filter((c) => c.col !== wireCol);
      const valueCells = prev.valueCells.filter((c) => c.col !== wireCol);
      return { ...prev, columns, conditionCells, valueCells };
    });
  }, []);

  const configureLeftColumnBundle = useCallback((displayCol: number, bundle: BundleData) => {
    setState((prev) => {
      const columns = prev.columns.slice();
      columns[displayCol] = { ...columns[displayCol], bundleData: bundle };
      return { ...prev, columns };
    });
  }, []);

  // ---- assignment target ----
  const setAssignTarget = useCallback((target: AssignTarget) => {
    setState((prev) => ({ ...prev, assignTarget: target }));
  }, []);

  // ---- save ----
  const handleSave = useCallback(() => {
    setSaving(true);
    let xml: string;
    try {
      xml = serializeCrossTable(state);
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

  // ---- AntD table ----
  // The grid is rows × columns. Each cell is a value-cell (intersection of a
  // LEFT row and a TOP column), a condition-cell (condition band), or empty.
  const antColumns: ColumnsType<number> = useMemo(() => {
    // First column: row header (row number + type + configure button for top rows).
    const cols: ColumnsType<number> = [
      {
        title: '行 \\ 列',
        key: 'row-header',
        width: 120,
        fixed: 'left',
        render: (_v, wireRow) => {
          const displayRow = state.rows.findIndex((r) => r.number === wireRow);
          const row = state.rows[displayRow];
          if (!row) return null;
          const isTop = row.type === 'top';
          return (
            <div>
              <Text style={{ fontSize: 11, color: '#888' }}>
                {isTop ? '条件行(TOP)' : '数据行(LEFT)'}
              </Text>
              <div>
                <Text strong>{row.number}</Text>
                {isTop && (
                  <RowBundleButton
                    bundle={row.bundleData}
                    libraries={variableLibraries}
                    onChange={(b) => configureTopRowBundle(displayRow, b)}
                  />
                )}
              </div>
            </div>
          );
        },
      },
    ];

    // One column per model column.
    for (let displayCol = 0; displayCol < state.columns.length; displayCol++) {
      const col = state.columns[displayCol];
      const isLeft = col.type === 'left';
      const wireCol = col.number;
      cols.push({
        title: (
          <div>
            <div style={{ fontSize: 11, color: '#888' }}>
              {isLeft ? '条件列(LEFT)' : '数据列(TOP)'}
            </div>
            <div>
              <Text strong>{col.number}</Text>
              {isLeft && (
                <ColBundleButton
                  bundle={col.bundleData}
                  libraries={variableLibraries}
                  onChange={(b) => configureLeftColumnBundle(displayCol, b)}
                />
              )}
              <Button
                size="small"
                type="text"
                danger
                icon={<DeleteOutlined />}
                onClick={() => removeColumn(displayCol)}
              />
            </div>
          </div>
        ),
        key: 'col-' + col.number,
        width: 180,
        // AntD cell-merge idiom on condition cells: the OWNING condition cell
        // returns { rowSpan: N } and every covered row below returns
        // { rowSpan: 0 }. Value cells never merge. Only applies to cells in
        // the condition band (top row × any col, or any row × left col).
        onCell: (wireRow) => {
          const displayRow = state.rows.findIndex((r) => r.number === wireRow);
          const row = state.rows[displayRow];
          if (!row) return {};
          const isCondition = row.type === 'top' || col.type === 'left';
          if (!isCondition) return {};
          const owner = findConditionOwningCell(wireRow, wireCol);
          if (!owner) return {};
          if (owner.row === wireRow) {
            return { rowSpan: owner.rowspan || 1 };
          }
          return { rowSpan: 0 };
        },
        render: (_v, wireRow) => {
          const displayRow = state.rows.findIndex((r) => r.number === wireRow);
          const row = state.rows[displayRow];
          if (!row) return null;
          // Intersection semantics:
          //   top row × any col   → condition-cell
          //   any row × left col  → condition-cell
          //   left row × top col  → value-cell
          const isCondition = row.type === 'top' || col.type === 'left';
          // Right-click Dropdown exposing cell-level copy/paste + merge/unmerge
          // (merge only on condition cells). The cell's kind is fixed by its
          // position; copyCell/pasteCell carry that kind so paste only lands on
          // the same kind elsewhere in the grid.
          // For condition cells, render the OWNER's content (covered rows
          // inherit the top) and target merge/paste ops at the owner.
          let ownerRow = wireRow;
          let rowspan = 1;
          let condContent: ConditionCellContent = { empty: true };
          if (isCondition) {
            const owner = findConditionOwningCell(wireRow, wireCol);
            ownerRow = owner?.row ?? wireRow;
            rowspan = owner?.rowspan ?? 1;
            condContent = owner?.content ?? { empty: true };
          }
          const hasNextRow = nextRowNum(ownerRow) !== undefined;
          const cellMenuItems: MenuProps['items'] = [
            ...(isCondition
              ? [
                  {
                    key: 'merge-down',
                    label: '向下合并',
                    disabled: !hasNextRow,
                    onClick: () => mergeConditionCellDown(ownerRow, wireCol),
                  },
                  {
                    key: 'unmerge',
                    label: '拆分合并',
                    disabled: rowspan <= 1,
                    onClick: () => unmergeConditionCell(ownerRow, wireCol),
                  },
                  { type: 'divider' as const },
                ]
              : []),
            {
              key: 'copy-cell',
              label: '复制单元格',
              icon: <BlockOutlined />,
              onClick: () => copyCell(wireRow, wireCol, isCondition ? 'condition' : 'value'),
            },
            {
              key: 'paste-cell',
              label: '粘贴单元格',
              icon: <SnippetsOutlined />,
              disabled: !clipboardCell,
              onClick: () => pasteCell(wireRow, wireCol, isCondition ? 'condition' : 'value'),
            },
          ];
          const editor = isCondition ? (
            <ConditionCellEditor
              value={condContent}
              constantLibraries={constantLibraries}
              parameterLibraries={parameterLibraries}
              onChange={(next) => setConditionCell(ownerRow, wireCol, next)}
            />
          ) : (
            (() => {
              const valueCell = findValueCell(wireRow, wireCol);
              return (
                <ValueCellEditor
                  value={valueCell?.value}
                  constantLibraries={constantLibraries}
                  parameterLibraries={parameterLibraries}
                  onChange={(v) => setValueCell(wireRow, wireCol, v)}
                />
              );
            })()
          );
          return (
            <Dropdown menu={{ items: cellMenuItems }} trigger={['contextMenu']}>
              <div style={{ cursor: 'context-menu' }}>{editor}</div>
            </Dropdown>
          );
        },
      });
    }

    // Trailing action column: per-row ⋯ dropdown (copy/paste/delete). The
    // paste item copies + appends the clipboard row at the end.
    cols.push({
      title: '',
      key: 'row-actions',
      width: 50,
      fixed: 'right',
      render: (_v, wireRow) => {
        const displayRow = state.rows.findIndex((r) => r.number === wireRow);
        const rowMenuItems: MenuProps['items'] = [
          {
            key: 'copy-row',
            label: '复制此行',
            icon: <CopyOutlined />,
            onClick: () => copyRow(displayRow),
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
  }, [
    state.rows, state.columns, findValueCell,
    findConditionOwningCell, nextRowNum,
    setConditionCell, setValueCell, mergeConditionCellDown, unmergeConditionCell,
    removeColumn, removeRow,
    configureTopRowBundle, configureLeftColumnBundle,
    copyRow, pasteRow, clipboardRow,
    copyCell, pasteCell, clipboardCell,
    constantLibraries, parameterLibraries,
  ]);

  // Wire rows — 1..n (one table row per model row).
  const dataRows = useMemo(() => state.rows.map((r) => r.number), [state.rows]);

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin description="加载交叉决策表…" />
      </div>
    );
  }

  return (
    <div style={{ padding: 16, maxWidth: 1400, margin: '0 auto' }}>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
        <Text strong>交叉决策表: {decodeURIComponent(file)}</Text>
        <Space wrap>
          <Button icon={<PlusOutlined />} onClick={addTopRow}>添加条件行</Button>
          <Button icon={<PlusOutlined />} onClick={addLeftRow}>添加数据行</Button>
          <Button icon={<PlusOutlined />} onClick={addLeftColumn}>添加条件列</Button>
          <Button icon={<PlusOutlined />} onClick={addTopColumn}>添加数据列</Button>
          <Button
            icon={<SettingOutlined />}
            onClick={() => setTargetModal(true)}
            type={state.assignTarget ? 'default' : 'dashed'}
          >
            赋值目标{state.assignTarget ? '' : '(必填)'}
          </Button>
          <Button
            icon={<PlusOutlined />}
            onClick={() => message.info('Excel 导入暂未实现(TODO)')}
          >
            导入Excel
          </Button>
          <Button
            icon={<SnippetsOutlined />}
            disabled={!clipboardRow}
            onClick={() => pasteRow()}
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
          message="加载交叉决策表失败,以空白表启动"
          description={loadError}
          closable={{ onClose: () => setLoadError(null) }}
        />
      )}

      {!state.assignTarget && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message='保存前请配置"赋值目标"(交叉单元格值要赋予的对象)'
        />
      )}

      <div style={{ marginBottom: 12 }}>
        <Input.TextArea
          rows={2}
          placeholder="备注 (remark)"
          value={state.remark}
          onChange={(e) => setState((prev) => ({ ...prev, remark: e.target.value }))}
        />
      </div>

      {state.rows.length === 0 ? (
        <Alert
          type="info"
          showIcon
          message="还没有行/列"
          description='点击右上角"添加条件行 / 添加数据行 / 添加条件列 / 添加数据列"开始。'
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
        />
      )}

      <AssignTargetModal
        open={targetModal}
        target={state.assignTarget}
        libraries={variableLibraries}
        onOk={(t) => {
          setAssignTarget(t);
          setTargetModal(false);
        }}
        onCancel={() => setTargetModal(false)}
      />
    </div>
  );
}

// ===========================================================================
// Sub-editors
// ===========================================================================

/**
 * ValueCellEditor — controlled editor for a cross-cell `<value>`.
 *
 * Empty cells render a clickable placeholder; clicking seeds a default Input
 * value. Reuses the ruleforge ValueEditor (the `<value>` wire format is
 * identical to every other ruleforge `<value>`).
 */
function ValueCellEditor({
  value,
  constantLibraries,
  parameterLibraries,
  onChange,
}: {
  value: ValueExpr | undefined;
  /** Imported constant libraries forwarded to the ValueEditor. */
  constantLibraries?: ConstantCategoryGroup[];
  /** Imported parameter libraries forwarded to the ValueEditor. */
  parameterLibraries?: ParameterLibrary[];
  onChange: (next: ValueExpr | undefined) => void;
}) {
  if (!value) {
    return (
      <div
        style={{ color: '#bbb', cursor: 'pointer', padding: '2px 4px' }}
        onClick={() => onChange({ type: 'Input', content: '' })}
        title="点击编辑"
      >
        无
      </div>
    );
  }
  return (
    <ValueEditor
      value={value}
      compact
      constantLibraries={constantLibraries}
      parameterLibraries={parameterLibraries}
      onChange={(v) => onChange(v)}
    />
  );
}

/**
 * ConditionCellEditor — controlled editor for one condition band cell.
 *
 * Surfaces a flat single condition (the row/column already binds the left
 * variable, so a condition is just op + optional right value). Writes the
 * first condition of the joint. Nested joints are a TODO (matches DT).
 */
function ConditionCellEditor({
  value,
  constantLibraries,
  parameterLibraries,
  onChange,
}: {
  value: ConditionCellContent;
  /** Imported constant libraries forwarded to the right-hand ValueEditor. */
  constantLibraries?: ConstantCategoryGroup[];
  /** Imported parameter libraries forwarded to the right-hand ValueEditor. */
  parameterLibraries?: ParameterLibrary[];
  onChange: (next: ConditionCellContent) => void;
}) {
  if ('empty' in value) {
    return (
      <div
        style={{ color: '#bbb', cursor: 'pointer', padding: '2px 4px' }}
        onClick={() =>
          onChange({
            joint: {
              type: 'and',
              conditions: [{ op: 'Equals', right: { type: 'Input', content: '' } }],
            },
          })
        }
        title="点击配置条件"
      >
        无
      </div>
    );
  }

  const cond = value.joint.conditions[0];
  const op = cond?.op ?? 'Equals';
  const right = cond?.right ?? ({ type: 'Input' as const, content: '' });
  const noRight = opHasNoInput(op);

  const patch = (next: { op?: string; right?: ValueExpr }): void => {
    const newOp = next.op ?? op;
    const conditions = value.joint.conditions.slice();
    const newCond: typeof cond = { op: newOp };
    if (!opHasNoInput(newOp)) {
      newCond.right = next.right ?? right;
    }
    conditions[0] = newCond;
    onChange({ joint: { type: value.joint.type, conditions } });
  };

  return (
    <div>
      <div style={{ marginBottom: 4 }}>
        <Select
          size="small"
          style={{ width: '100%' }}
          value={op}
          onChange={(nextOp) => patch({ op: nextOp })}
          options={OPERATOR_OPTIONS}
        />
      </div>
      {!noRight && (
        <ValueEditor
          value={right}
          compact
          constantLibraries={constantLibraries}
          parameterLibraries={parameterLibraries}
          onChange={(v) => patch({ right: v })}
        />
      )}
    </div>
  );
}

/**
 * RowBundleButton / ColBundleButton — modal trigger for binding a condition
 * row / condition column to a variable or parameter.
 */
function RowBundleButton({
  bundle,
  libraries = [],
  onChange,
}: {
  bundle: BundleData | undefined;
  libraries?: VariableCategoryGroup[];
  onChange: (next: BundleData) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button size="small" type="link" onClick={() => setOpen(true)}>
        {bundle?.variableLabel || bundle?.variableName || '绑定变量'}
      </Button>
      <BundleModal
        open={open}
        title="配置条件行变量"
        bundle={bundle}
        libraries={libraries}
        onOk={(b) => {
          onChange(b);
          setOpen(false);
        }}
        onCancel={() => setOpen(false)}
      />
    </>
  );
}

function ColBundleButton({
  bundle,
  libraries = [],
  onChange,
}: {
  bundle: BundleData | undefined;
  libraries?: VariableCategoryGroup[];
  onChange: (next: BundleData) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button size="small" type="link" onClick={() => setOpen(true)}>
        {bundle?.variableLabel || bundle?.variableName || '绑定变量'}
      </Button>
      <BundleModal
        open={open}
        title="配置条件列变量"
        bundle={bundle}
        libraries={libraries}
        onOk={(b) => {
          onChange(b);
          setOpen(false);
        }}
        onCancel={() => setOpen(false)}
      />
    </>
  );
}

/**
 * BundleModal — variable/parameter binding form for a condition row or column.
 *
 * `type` picks variable (emits the raw var-category) or parameter (forces
 * var-category="参数" at serialize time). The four var-* fields match the
 * `<row>` / `<column>` bundle attributes.
 */
function BundleModal({
  open,
  title,
  bundle,
  libraries = [],
  onOk,
  onCancel,
}: {
  open: boolean;
  title: string;
  bundle: BundleData | undefined;
  libraries?: VariableCategoryGroup[];
  onOk: (next: BundleData) => void;
  onCancel: () => void;
}) {
  const [type, setType] = useState<'variable' | 'parameter'>(bundle?.type ?? 'variable');
  const [variableCategory, setVariableCategory] = useState(bundle?.variableCategory ?? '');
  const [variableName, setVariableName] = useState(bundle?.variableName ?? '');
  const [variableLabel, setVariableLabel] = useState(bundle?.variableLabel ?? '');
  const [datatype, setDatatype] = useState(bundle?.datatype ?? '');

  // Re-seed when opening (the modal is reused across rows/columns).
  useEffect(() => {
    if (open) {
      setType(bundle?.type ?? 'variable');
      setVariableCategory(bundle?.variableCategory ?? '');
      setVariableName(bundle?.variableName ?? '');
      setVariableLabel(bundle?.variableLabel ?? '');
      setDatatype(bundle?.datatype ?? '');
    }
  }, [open, bundle]);

  const usePicker = libraries.some((lib) => (lib || []).length > 0);
  const setFromBinding = (b: { varCategory?: string; var?: string; varLabel?: string; datatype?: string }) => {
    setVariableCategory(b.varCategory ?? '');
    setVariableName(b.var ?? '');
    setVariableLabel(b.varLabel ?? '');
    setDatatype(b.datatype ?? '');
  };

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onCancel}
      onOk={() => {
        const next: BundleData = {
          type,
          variableCategory: type === 'parameter' ? '参数' : variableCategory,
          variableName,
          variableLabel,
          datatype,
        };
        onOk(next);
      }}
      destroyOnClose
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <div>
          <Text style={{ width: 100, display: 'inline-block' }}>类型</Text>
          <Select
            style={{ width: 200 }}
            value={type}
            onChange={(t) => setType(t)}
            options={[
              { value: 'variable', label: '变量' },
              { value: 'parameter', label: '参数' },
            ]}
          />
        </div>
        {type === 'variable' && usePicker ? (
          <div>
            <Text style={{ width: 100, display: 'inline-block', verticalAlign: 'top' }}>变量绑定</Text>
            <span style={{ display: 'inline-block', width: 300 }}>
              <VariablePicker
                libraries={libraries}
                allowDatatypeEdit
                value={{ varCategory: variableCategory, var: variableName, varLabel: variableLabel, datatype }}
                onChange={setFromBinding}
              />
            </span>
          </div>
        ) : (
          <>
            {type === 'variable' && (
              <div>
                <Text style={{ width: 100, display: 'inline-block' }}>变量分类</Text>
                <Input
                  style={{ width: 300 }}
                  placeholder="如 客户.客户"
                  value={variableCategory}
                  onChange={(e) => setVariableCategory(e.target.value)}
                />
              </div>
            )}
            <div>
              <Text style={{ width: 100, display: 'inline-block' }}>变量名/参数名</Text>
              <Input
                style={{ width: 300 }}
                placeholder="如 age"
                value={variableName}
                onChange={(e) => setVariableName(e.target.value)}
              />
            </div>
            <div>
              <Text style={{ width: 100, display: 'inline-block' }}>标签</Text>
              <Input
                style={{ width: 300 }}
                placeholder="如 年龄"
                value={variableLabel}
                onChange={(e) => setVariableLabel(e.target.value)}
              />
            </div>
            <div>
              <Text style={{ width: 100, display: 'inline-block' }}>数据类型</Text>
              <Input
                style={{ width: 300 }}
                placeholder="如 Integer"
                value={datatype}
                onChange={(e) => setDatatype(e.target.value)}
              />
            </div>
          </>
        )}
      </Space>
    </Modal>
  );
}

/**
 * AssignTargetModal — form for the crosstab's cross-cell assignment target.
 *
 * Mirrors CrossCellVariableBundle: type picks variable or parameter, then the
 * four var-* attributes. At runtime each value-cell's `<value>` is assigned to
 * this target.
 */
function AssignTargetModal({
  open,
  target,
  libraries = [],
  onOk,
  onCancel,
}: {
  open: boolean;
  target: AssignTarget | undefined;
  libraries?: VariableCategoryGroup[];
  onOk: (next: AssignTarget) => void;
  onCancel: () => void;
}) {
  const [type, setType] = useState<'variable' | 'parameter'>(target?.type ?? 'variable');
  const [variableCategory, setVariableCategory] = useState(target?.variableCategory ?? '');
  const [variableName, setVariableName] = useState(target?.variableName ?? '');
  const [variableLabel, setVariableLabel] = useState(target?.variableLabel ?? '');
  const [datatype, setDatatype] = useState(target?.datatype ?? '');

  useEffect(() => {
    if (open) {
      setType(target?.type ?? 'variable');
      setVariableCategory(target?.variableCategory ?? '');
      setVariableName(target?.variableName ?? '');
      setVariableLabel(target?.variableLabel ?? '');
      setDatatype(target?.datatype ?? '');
    }
  }, [open, target]);

  const usePicker = libraries.some((lib) => (lib || []).length > 0);
  const setFromBinding = (b: { varCategory?: string; var?: string; varLabel?: string; datatype?: string }) => {
    setVariableCategory(b.varCategory ?? '');
    setVariableName(b.var ?? '');
    setVariableLabel(b.varLabel ?? '');
    setDatatype(b.datatype ?? '');
  };

  return (
    <Modal
      title="配置赋值目标"
      open={open}
      onCancel={onCancel}
      onOk={() => {
        const next: AssignTarget = {
          type,
          variableCategory: type === 'parameter' ? '参数' : variableCategory,
          variableName,
          variableLabel,
          datatype,
        };
        onOk(next);
      }}
      destroyOnClose
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <div>
          <Text style={{ width: 100, display: 'inline-block' }}>赋值目标类型</Text>
          <Select
            style={{ width: 200 }}
            value={type}
            onChange={(t) => setType(t)}
            options={[
              { value: 'variable', label: '变量' },
              { value: 'parameter', label: '参数' },
            ]}
          />
        </div>
        {type === 'variable' && usePicker ? (
          <div>
            <Text style={{ width: 100, display: 'inline-block', verticalAlign: 'top' }}>变量绑定</Text>
            <span style={{ display: 'inline-block', width: 300 }}>
              <VariablePicker
                libraries={libraries}
                allowDatatypeEdit
                value={{ varCategory: variableCategory, var: variableName, varLabel: variableLabel, datatype }}
                onChange={setFromBinding}
              />
            </span>
          </div>
        ) : (
          <>
            {type === 'variable' && (
              <div>
                <Text style={{ width: 100, display: 'inline-block' }}>变量分类</Text>
                <Input
                  style={{ width: 300 }}
                  placeholder="如 客户.客户"
                  value={variableCategory}
                  onChange={(e) => setVariableCategory(e.target.value)}
                />
              </div>
            )}
            <div>
              <Text style={{ width: 100, display: 'inline-block' }}>变量名/参数名</Text>
              <Input
                style={{ width: 300 }}
                placeholder="如 result"
                value={variableName}
                onChange={(e) => setVariableName(e.target.value)}
              />
            </div>
            <div>
              <Text style={{ width: 100, display: 'inline-block' }}>标签</Text>
              <Input
                style={{ width: 300 }}
                placeholder="如 结果"
                value={variableLabel}
                onChange={(e) => setVariableLabel(e.target.value)}
              />
            </div>
            <div>
              <Text style={{ width: 100, display: 'inline-block' }}>数据类型</Text>
              <Input
                style={{ width: 300 }}
                placeholder="如 String"
                value={datatype}
                onChange={(e) => setDatatype(e.target.value)}
              />
            </div>
          </>
        )}
      </Space>
    </Modal>
  );
}

export default CrossTableEditor;
