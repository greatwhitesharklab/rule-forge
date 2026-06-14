/**
 * ActionEditor — controlled editor for a single `Action`.
 *
 * Renders a kind selector at the top and the matching field set below. All
 * four kinds are supported (console-print / var-assign / execute-method /
 * execute-function), matching serializeAction() in model/serialize.ts.
 *
 * execute-method / execute-function parameters reuse ValueEditor for each
 * parameter value; the parameter name/type inputs are plain text fields for
 * the first pass (a real bean-picker can replace them later).
 *
 * Data flow is single-direction; the parent owns the Action object and
 * replaces it via onChange on every field edit.
 */
import { Button, Input, Select, Space } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { Action, MethodParam, ValueExpr } from '../model/types';
import { ACTION_KIND_OPTIONS, VAR_ASSIGN_TYPE_OPTIONS } from './constants';
import { ValueEditor } from './ValueEditor';
import { FieldLabel } from './FieldLabel';

export interface ActionEditorProps {
  /** The current action (controlled). */
  value: Action;
  /** Called with a new Action on every field edit. */
  onChange: (next: Action) => void;
  /** Called when the user clicks the delete affordance. */
  onDelete?: () => void;
}

/** Build a fresh Action when the user picks a new kind. */
function freshActionForKind(kind: Action['kind']): Action {
  switch (kind) {
    case 'console-print':
      return { kind: 'console-print', value: { type: 'Input', content: '' } };
    case 'var-assign':
      return {
        kind: 'var-assign',
        var: '',
        varLabel: '',
        varCategory: '',
        valueType: 'variable',
        value: { type: 'Input', content: '' },
      };
    case 'execute-method':
      return { kind: 'execute-method', bean: '', beanLabel: '', methodName: '', methodLabel: '', parameters: [] };
    case 'execute-function':
      return { kind: 'execute-function', functionName: '', functionLabel: '' };
    default:
      return { kind: 'console-print', value: { type: 'Input', content: '' } };
  }
}

export function ActionEditor({ value, onChange, onDelete }: ActionEditorProps) {
  const onKindChange = (next: string) => {
    onChange(freshActionForKind(next as Action['kind']));
  };

  return (
    <div style={{ border: '1px solid #f0f0f0', padding: 8, borderRadius: 4, marginBottom: 8 }}>
      <Space style={{ marginBottom: 8, width: '100%', justifyContent: 'space-between' }}>
        <Select
          size="small"
          style={{ width: 160 }}
          value={value.kind}
          onChange={onKindChange}
          options={ACTION_KIND_OPTIONS}
        />
        {onDelete && (
          <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={onDelete} />
        )}
      </Space>

      {value.kind === 'console-print' && (
        <ValueEditor value={value.value} onChange={(v: ValueExpr) => onChange({ ...value, value: v })} />
      )}

      {value.kind === 'var-assign' && (
        <div>
          <div style={{ marginBottom: 8 }}>
            <Input
              size="small"
              prefix={<FieldLabel>变量分类</FieldLabel>}
              placeholder="如 客户.客户"
              value={value.varCategory}
              onChange={(e) => onChange({ ...value, varCategory: e.target.value })}
            />
          </div>
          <div style={{ marginBottom: 8 }}>
            <Input
              size="small"
              prefix={<FieldLabel>变量名</FieldLabel>}
              placeholder="如 result"
              value={value.var}
              onChange={(e) => onChange({ ...value, var: e.target.value })}
            />
          </div>
          <div style={{ marginBottom: 8 }}>
            <Input
              size="small"
              prefix={<FieldLabel>标签</FieldLabel>}
              placeholder="如 结果"
              value={value.varLabel}
              onChange={(e) => onChange({ ...value, varLabel: e.target.value })}
            />
          </div>
          <div style={{ marginBottom: 8 }}>
            <Select
              size="small"
              style={{ width: '100%' }}
              value={value.valueType}
              onChange={(vt: 'variable' | 'parameter' | 'reference') => onChange({ ...value, valueType: vt })}
              options={VAR_ASSIGN_TYPE_OPTIONS}
            />
          </div>
          <ValueEditor value={value.value} onChange={(v: ValueExpr) => onChange({ ...value, value: v })} />
        </div>
      )}

      {value.kind === 'execute-method' && (
        <div>
          <div style={{ marginBottom: 8 }}>
            <Input
              size="small"
              prefix={<FieldLabel>bean</FieldLabel>}
              placeholder="bean-name"
              value={value.bean}
              onChange={(e) => onChange({ ...value, bean: e.target.value })}
            />
          </div>
          <div style={{ marginBottom: 8 }}>
            <Input
              size="small"
              prefix={<FieldLabel>方法</FieldLabel>}
              placeholder="method-name"
              value={value.methodName}
              onChange={(e) => onChange({ ...value, methodName: e.target.value })}
            />
          </div>
          <div style={{ marginBottom: 8 }}>
            <Input
              size="small"
              prefix={<FieldLabel>标签</FieldLabel>}
              placeholder="method-label"
              value={value.methodLabel}
              onChange={(e) => onChange({ ...value, methodLabel: e.target.value })}
            />
          </div>
          <ParametersEditor
            parameters={value.parameters}
            onChange={(parameters) => onChange({ ...value, parameters })}
          />
        </div>
      )}

      {value.kind === 'execute-function' && (
        <div>
          <div style={{ marginBottom: 8 }}>
            <Input
              size="small"
              prefix={<FieldLabel>函数</FieldLabel>}
              placeholder="function-name"
              value={value.functionName}
              onChange={(e) => onChange({ ...value, functionName: e.target.value })}
            />
          </div>
          <div style={{ marginBottom: 8 }}>
            <Input
              size="small"
              prefix={<FieldLabel>标签</FieldLabel>}
              placeholder="function-label"
              value={value.functionLabel}
              onChange={(e) => onChange({ ...value, functionLabel: e.target.value })}
            />
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Sub-editor for the `<parameter>` list of an execute-method action. Each row
 * is name + type + a ValueEditor. Add / delete via buttons.
 */
function ParametersEditor({
  parameters,
  onChange,
}: {
  parameters: MethodParam[];
  onChange: (next: MethodParam[]) => void;
}) {
  const update = (i: number, p: Partial<MethodParam>) => {
    const next = parameters.slice();
    next[i] = { ...next[i], ...p };
    onChange(next);
  };
  const remove = (i: number) => {
    const next = parameters.slice();
    next.splice(i, 1);
    onChange(next);
  };
  const add = () => {
    onChange(parameters.concat([{ name: '', type: '', value: { type: 'Input', content: '' } }]));
  };

  return (
    <div>
      <div style={{ marginBottom: 4, color: '#888', fontSize: 12 }}>参数列表</div>
      {parameters.map((p, i) => (
        <div key={i} style={{ border: '1px dashed #eee', padding: 4, marginBottom: 4 }}>
          <Space style={{ marginBottom: 4 }}>
            <Input
              size="small"
              style={{ width: 100 }}
              placeholder="参数名"
              value={p.name}
              onChange={(e) => update(i, { name: e.target.value })}
            />
            <Input
              size="small"
              style={{ width: 100 }}
              placeholder="参数类型"
              value={p.type}
              onChange={(e) => update(i, { type: e.target.value })}
            />
            <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => remove(i)} />
          </Space>
          <ValueEditor
            value={p.value}
            compact
            onChange={(v: ValueExpr) => update(i, { value: v })}
          />
        </div>
      ))}
      <Button size="small" type="dashed" onClick={add}>
        + 添加参数
      </Button>
    </div>
  );
}

export default ActionEditor;
