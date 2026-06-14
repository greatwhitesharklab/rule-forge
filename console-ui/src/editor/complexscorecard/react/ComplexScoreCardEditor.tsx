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
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Input, InputNumber, Select, Space, Spin, Table, Typography, message } from 'antd';
import { DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { ValueExpr } from '../../ruleforge/model/types';
import { OPERATOR_OPTIONS, opHasNoInput } from '../../ruleforge/react/constants';
import { ValueEditor } from '../../ruleforge/react/ValueEditor';
import type {
  AssignTarget,
  AssignTargetType,
  CardCondition,
  ComplexCell,
  ComplexCol,
  ComplexColType,
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
            if (col.type === 'Criteria') {
              return (
                <CriteriaCellEditor
                  value={cell}
                  onChange={(next) => setCell(dr.rowNumber, col.num, next)}
                />
              );
            }
            return (
              <ValueCellEditor
                value={cell}
                onChange={(next) => setCell(dr.rowNumber, col.num, next)}
              />
            );
          },
        };
      });

    // Trailing action column: delete row.
    cols.push({
      title: '',
      key: 'row-actions',
      width: 50,
      render: (_v, dr) => (
        <Button
          size="small"
          type="text"
          danger
          icon={<DeleteOutlined />}
          onClick={() => removeRow(dr.rowNumber)}
        />
      ),
    });

    return cols;
  }, [state.cols, findCell, setCell, removeCol, removeRow]);

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

      <ConfigArea state={state} setState={setState} patchCol={patchCol} />

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
}: {
  state: ComplexScoreCardData;
  setState: React.Dispatch<React.SetStateAction<ComplexScoreCardData>>;
  patchCol: (colNumber: number, patch: Partial<ComplexCol>) => void;
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
  onChange,
}: {
  value: ComplexCell | undefined;
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

  return (
    <div>
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
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
        <Select
          size="small"
          style={{ width: '100%' }}
          value={op}
          onChange={(nextOp) => patchCondition({ op: nextOp })}
          options={OPERATOR_OPTIONS}
        />
        {!noRight && <ValueEditor value={right} compact onChange={(v) => patchCondition({ right: v })} />}
      </Space>
    </div>
  );
}

/** Score / Custom cell: a single value expression. */
function ValueCellEditor({
  value,
  onChange,
}: {
  value: ComplexCell | undefined;
  onChange: (next: ComplexCell) => void;
}) {
  const cell: ComplexCell = value ?? { row: 0, col: 0, rowspan: 1 };
  const v: ValueExpr = cell.value ?? { type: 'Input', content: '' };
  return <ValueEditor value={v} compact onChange={(value) => onChange({ ...cell, value })} />;
}

export default ComplexScoreCardEditor;
