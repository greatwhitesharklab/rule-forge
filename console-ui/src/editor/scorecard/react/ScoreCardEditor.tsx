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
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Input, InputNumber, Radio, Select, Space, Spin, Table, Typography, message } from 'antd';
import { DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { ValueExpr } from '../../ruleforge/model/types';
import { OPERATOR_OPTIONS, opHasNoInput } from '../../ruleforge/react/constants';
import { ValueEditor } from '../../ruleforge/react/ValueEditor';
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

export function ScoreCardEditor({
  file,
  onLoad = loadFromServer,
  onSave = saveToServer,
}: ScoreCardEditorProps) {
  const [state, setState] = useState<ScoreCardData>(emptyCard());
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

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
        return <ScoreCellEditor value={cell} onChange={(next) => setCell(dr.rowNumber, 3, 'score', next)} />;
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
              onChange={(next) => setCell(dr.rowNumber, cc.colNumber, 'custom', next)}
            />
          );
        },
      });
    }

    // Trailing action column: delete custom col header / delete row.
    cols.push({
      title: '',
      key: 'row-actions',
      width: 50,
      render: (_v, dr) =>
        dr.kind === 'condition' ? (
          <Button
            size="small"
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={() => removeRow(dr.rowNumber, true, dr.attributeRowNumber)}
          />
        ) : null,
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

      <ConfigArea state={state} setState={setState} />

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
}: {
  state: ScoreCardData;
  setState: React.Dispatch<React.SetStateAction<ScoreCardData>>;
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

/** Attribute cell: category / variable binding (free-text) + optional weight. */
function AttributeCellEditor({
  value,
  weightSupport,
  onChange,
  onAddCondition,
  onRemove,
}: {
  value: CardCell | undefined;
  weightSupport: boolean;
  onChange: (next: CardCell) => void;
  onAddCondition: () => void;
  onRemove: () => void;
}) {
  const cell: CardCell = value ?? { type: 'attribute', row: 0, col: 1 };
  const patch = (p: Partial<CardCell>) => onChange({ ...cell, ...p });

  return (
    <div>
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
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
  onChange,
  onRemove,
}: {
  value: CardCell | undefined;
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
      {!noRight && <ValueEditor value={right} compact onChange={(v) => patchCondition({ right: v })} />}
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
  onChange,
}: {
  value: CardCell | undefined;
  onChange: (next: CardCell) => void;
}) {
  const cell: CardCell = value ?? { type: 'score', row: 0, col: 3 };
  const v: ValueExpr = cell.value ?? { type: 'Input', content: '' };
  return <ValueEditor value={v} compact onChange={(value) => onChange({ ...cell, value })} />;
}

/** Custom cell: a single value expression. */
function CustomCellEditor({
  value,
  onChange,
}: {
  value: CardCell | undefined;
  onChange: (next: CardCell) => void;
}) {
  const cell: CardCell = value ?? { type: 'custom', row: 0, col: 4 };
  const v: ValueExpr = cell.value ?? { type: 'Input', content: '' };
  return <ValueEditor value={v} compact onChange={(value) => onChange({ ...cell, value })} />;
}

export default ScoreCardEditor;
