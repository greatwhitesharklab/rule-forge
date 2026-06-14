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
 * ── TODO (jquery features NOT yet ported) ──────────────────────────────────
 *   - hierarchical rowspan/colspan merging (multi-level condition headers)
 *   - Excel import (the toolbar button is wired but a no-op alert for now)
 *   - copy/paste of cell data
 *   - full condition-cell joint UI (flat single condition only, like DT)
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Input, Modal, Select, Space, Spin, Table, Typography, message,
} from 'antd';
import {
  DeleteOutlined, PlusOutlined, SaveOutlined, SettingOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { ValueExpr } from '../../ruleforge/model/types';
import { ValueEditor } from '../../ruleforge/react/ValueEditor';
import { OPERATOR_OPTIONS, opHasNoInput } from '../../ruleforge/react/constants';
import type {
  AssignTarget,
  BundleData,
  ConditionCellContent,
  ConditionCrossCell,
  CrossTableData,
} from '../model/types';
import { parseCrossTable } from '../model/parse';
import { serializeCrossTable } from '../model/serialize';
import { formPost, save } from '@/api/client';

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

  const findConditionCell = useCallback(
    (row: number, col: number) => state.conditionCells.find((c) => c.row === row && c.col === col),
    [state.conditionCells],
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
      const rows = prev.rows.filter((_, i) => i !== displayRow);
      const conditionCells = prev.conditionCells.filter((c) => c.row !== wireRow);
      const valueCells = prev.valueCells.filter((c) => c.row !== wireRow);
      return { ...prev, rows, conditionCells, valueCells };
    });
  }, []);

  const configureTopRowBundle = useCallback((displayRow: number, bundle: BundleData) => {
    setState((prev) => {
      const rows = prev.rows.slice();
      rows[displayRow] = { ...rows[displayRow], bundleData: bundle };
      return { ...prev, rows };
    });
  }, []);

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
        render: (_v, wireRow) => {
          const displayRow = state.rows.findIndex((r) => r.number === wireRow);
          const row = state.rows[displayRow];
          if (!row) return null;
          // Intersection semantics:
          //   top row × any col   → condition-cell
          //   any row × left col  → condition-cell
          //   left row × top col  → value-cell
          if (row.type === 'top' || col.type === 'left') {
            const cond = findConditionCell(wireRow, wireCol);
            return (
              <ConditionCellEditor
                value={cond?.content ?? { empty: true }}
                onChange={(next) => setConditionCell(wireRow, wireCol, next)}
              />
            );
          }
          const valueCell = findValueCell(wireRow, wireCol);
          return (
            <ValueCellEditor
              value={valueCell?.value}
              onChange={(v) => setValueCell(wireRow, wireCol, v)}
            />
          );
        },
      });
    }

    // Trailing action column: delete row.
    cols.push({
      title: '',
      key: 'row-actions',
      width: 50,
      fixed: 'right',
      render: (_v, wireRow) => {
        const displayRow = state.rows.findIndex((r) => r.number === wireRow);
        return (
          <Button
            size="small"
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={() => removeRow(displayRow)}
          />
        );
      },
    });
    return cols;
  }, [
    state.rows, state.columns, findConditionCell, findValueCell,
    setConditionCell, setValueCell, removeColumn, removeRow,
    configureTopRowBundle, configureLeftColumnBundle,
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
  onChange,
}: {
  value: ValueExpr | undefined;
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
  return <ValueEditor value={value} compact onChange={(v) => onChange(v)} />;
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
  onChange,
}: {
  value: ConditionCellContent;
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
      {!noRight && <ValueEditor value={right} compact onChange={(v) => patch({ right: v })} />}
    </div>
  );
}

/**
 * RowBundleButton / ColBundleButton — modal trigger for binding a condition
 * row / condition column to a variable or parameter.
 */
function RowBundleButton({
  bundle,
  onChange,
}: {
  bundle: BundleData | undefined;
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
  onChange,
}: {
  bundle: BundleData | undefined;
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
  onOk,
  onCancel,
}: {
  open: boolean;
  title: string;
  bundle: BundleData | undefined;
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
  onOk,
  onCancel,
}: {
  open: boolean;
  target: AssignTarget | undefined;
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
      </Space>
    </Modal>
  );
}

export default CrossTableEditor;
