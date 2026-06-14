/**
 * LeftValueEditor — controlled editor for a `LeftValue` (`<left>` element).
 *
 * The left-hand side of an atom. `type` selects which fields are populated:
 *   - variable    → varCategory / var / varLabel / datatype
 *   - parameter   → same fields (var-category is implicitly "参数" on the
 *                   server side, so the varCategory field is hidden)
 *   - method      → beanName / beanLabel / methodName / methodLabel + <parameter>
 *   - commonfunction → functionName / functionLabel + <function-parameter>
 *
 * Data flow is single-direction: parent owns the LeftValue object; field
 * edits emit a shallow-cloned next value via onChange.
 *
 * antd 6 note: the old `addonBefore` prop on `<Input>` is runtime-deprecated
 * (warning "use Space.Compact instead"). We use the non-deprecated `prefix`
 * slot with a FieldLabel span instead.
 */
import { Button, Input, Select, Space } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { FunctionParam, LeftValue, MethodParam } from '../model/types';
import { LEFT_TYPE_OPTIONS, ARITH_OP_OPTIONS } from './constants';
import { FieldLabel } from './FieldLabel';
import { ValueEditor } from './ValueEditor';

export interface LeftValueEditorProps {
  /** The current left value (controlled). */
  value: LeftValue;
  /** Called with a new LeftValue on every field edit. */
  onChange: (next: LeftValue) => void;
  /** Optional compact mode. */
  compact?: boolean;
}

function patch(v: LeftValue, p: Partial<LeftValue>): LeftValue {
  return { ...v, ...p };
}

/**
 * Build a fresh LeftValue when the user switches `type`, dropping fields that
 * don't belong to the new type.
 */
function freshLeftForType(type: LeftValue['type']): LeftValue {
  switch (type) {
    case 'variable':
      return { type, varCategory: '', var: '', varLabel: '', datatype: '' };
    case 'parameter':
      // Server implicitly sets var-category="参数" for parameter lefts.
      return { type, var: '', varLabel: '', datatype: '' };
    case 'method':
      return { type, beanName: '', beanLabel: '', methodName: '', methodLabel: '', parameters: [] };
    case 'commonfunction':
      return {
        type,
        functionName: '',
        functionLabel: '',
        functionParameter: { name: '', propertyName: '', propertyLabel: '', value: { type: 'Input', content: '' } },
      };
    default:
      return { type: 'variable', varCategory: '', var: '', varLabel: '', datatype: '' };
  }
}

export function LeftValueEditor({ value, onChange, compact }: LeftValueEditorProps) {
  const gap = compact ? 4 : 8;
  const rowStyle: React.CSSProperties = { marginBottom: gap };
  const onTypeChange = (next: string) => {
    onChange(freshLeftForType(next as LeftValue['type']));
  };

  return (
    <div>
      <div style={rowStyle}>
        <Select
          size="small"
          style={{ width: '100%' }}
          value={value.type}
          onChange={onTypeChange}
          options={LEFT_TYPE_OPTIONS}
        />
      </div>

      {(value.type === 'variable' || value.type === 'parameter') && (
        <>
          {value.type === 'variable' && (
            <div style={rowStyle}>
              <Input
                size="small"
                prefix={<FieldLabel>变量分类</FieldLabel>}
                placeholder="如 客户.客户"
                value={value.varCategory ?? ''}
                onChange={(e) => onChange(patch(value, { varCategory: e.target.value }))}
              />
            </div>
          )}
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>变量名</FieldLabel>}
              placeholder="如 age"
              value={value.var ?? ''}
              onChange={(e) => onChange(patch(value, { var: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>标签</FieldLabel>}
              placeholder="如 年龄"
              value={value.varLabel ?? ''}
              onChange={(e) => onChange(patch(value, { varLabel: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>类型</FieldLabel>}
              placeholder="如 Integer"
              value={value.datatype ?? ''}
              onChange={(e) => onChange(patch(value, { datatype: e.target.value }))}
            />
          </div>
        </>
      )}

      {value.type === 'method' && (
        <MethodLeftFields value={value} onChange={onChange} rowStyle={rowStyle} />
      )}

      {value.type === 'commonfunction' && (
        <CommonFunctionLeftFields value={value} onChange={onChange} rowStyle={rowStyle} />
      )}

      {/* Optional simple-arith chain — for the first pass we only render the
          head node; nested chains are preserved verbatim. */}
      {value.arithmetic && (
        <div style={rowStyle}>
          <Select
            size="small"
            style={{ width: 100 }}
            value={value.arithmetic.type}
            onChange={(next) =>
              onChange(patch(value, { arithmetic: { ...value.arithmetic!, type: next as 'Add' | 'Sub' | 'Mul' | 'Div' | 'Mod' } }))}
            options={ARITH_OP_OPTIONS}
          />
          <Input
            size="small"
            style={{ width: 'calc(100% - 104px)', marginLeft: 4 }}
            placeholder="算术值"
            value={value.arithmetic.value}
            onChange={(e) => onChange(patch(value, { arithmetic: { ...value.arithmetic!, value: e.target.value } }))}
          />
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Method left fields — bean / method name+label + a parameter list.
// ---------------------------------------------------------------------------

interface MethodLeftFieldsProps {
  value: LeftValue;
  onChange: (next: LeftValue) => void;
  rowStyle: React.CSSProperties;
}

function MethodLeftFields({ value, onChange, rowStyle }: MethodLeftFieldsProps) {
  const patchMethod = (p: Partial<LeftValue>) => onChange(patch(value, p));

  return (
    <>
      <div style={rowStyle}>
        <Input
          size="small"
          prefix={<FieldLabel>bean</FieldLabel>}
          placeholder="bean-name"
          value={value.beanName ?? ''}
          onChange={(e) => patchMethod({ beanName: e.target.value })}
        />
      </div>
      <div style={rowStyle}>
        <Input
          size="small"
          prefix={<FieldLabel>bean标签</FieldLabel>}
          placeholder="bean-label"
          value={value.beanLabel ?? ''}
          onChange={(e) => patchMethod({ beanLabel: e.target.value })}
        />
      </div>
      <div style={rowStyle}>
        <Input
          size="small"
          prefix={<FieldLabel>方法</FieldLabel>}
          placeholder="method-name"
          value={value.methodName ?? ''}
          onChange={(e) => patchMethod({ methodName: e.target.value })}
        />
      </div>
      <div style={rowStyle}>
        <Input
          size="small"
          prefix={<FieldLabel>方法标签</FieldLabel>}
          placeholder="method-label"
          value={value.methodLabel ?? ''}
          onChange={(e) => patchMethod({ methodLabel: e.target.value })}
        />
      </div>
      <LeftMethodParametersEditor
        parameters={value.parameters ?? []}
        onChange={(parameters) => patchMethod({ parameters })}
      />
    </>
  );
}

/**
 * Sub-editor for the `<parameter>` list of a method left. Each row is
 * name + type + a nested ValueEditor. Add / delete via buttons.
 *
 * Mirrors the ParametersEditor pattern used by ActionEditor for
 * execute-method actions.
 */
function LeftMethodParametersEditor({
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
            onChange={(v) => update(i, { value: v })}
          />
        </div>
      ))}
      <Button size="small" type="dashed" onClick={add}>
        + 添加参数
      </Button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// CommonFunction left fields — function name+label + a single
// function-parameter (name / propertyName / propertyLabel + nested value).
// ---------------------------------------------------------------------------

interface CommonFunctionLeftFieldsProps {
  value: LeftValue;
  onChange: (next: LeftValue) => void;
  rowStyle: React.CSSProperties;
}

function CommonFunctionLeftFields({ value, onChange, rowStyle }: CommonFunctionLeftFieldsProps) {
  const patchFn = (p: Partial<LeftValue>) => onChange(patch(value, p));

  // Ensure a functionParameter object exists so the nested editor can write
  // into it; if it is missing we lazily seed a fresh one on first edit.
  const fnParam: FunctionParam = value.functionParameter ?? {
    name: '',
    propertyName: '',
    propertyLabel: '',
    value: { type: 'Input', content: '' },
  };
  const patchFnParam = (p: Partial<FunctionParam>) =>
    patchFn({ functionParameter: { ...fnParam, ...p } });

  return (
    <>
      <div style={rowStyle}>
        <Input
          size="small"
          prefix={<FieldLabel>函数</FieldLabel>}
          placeholder="function-name"
          value={value.functionName ?? ''}
          onChange={(e) => patchFn({ functionName: e.target.value })}
        />
      </div>
      <div style={rowStyle}>
        <Input
          size="small"
          prefix={<FieldLabel>函数标签</FieldLabel>}
          placeholder="function-label"
          value={value.functionLabel ?? ''}
          onChange={(e) => patchFn({ functionLabel: e.target.value })}
        />
      </div>
      <div style={rowStyle}>
        <Input
          size="small"
          prefix={<FieldLabel>参数名</FieldLabel>}
          placeholder="function-parameter name"
          value={fnParam.name ?? ''}
          onChange={(e) => patchFnParam({ name: e.target.value })}
        />
      </div>
      <div style={rowStyle}>
        <Input
          size="small"
          prefix={<FieldLabel>属性</FieldLabel>}
          placeholder="property-name"
          value={fnParam.propertyName ?? ''}
          onChange={(e) => patchFnParam({ propertyName: e.target.value })}
        />
      </div>
      <div style={rowStyle}>
        <Input
          size="small"
          prefix={<FieldLabel>属性标签</FieldLabel>}
          placeholder="property-label"
          value={fnParam.propertyLabel ?? ''}
          onChange={(e) => patchFnParam({ propertyLabel: e.target.value })}
        />
      </div>
      <ValueEditor
        value={fnParam.value}
        compact
        onChange={(v) => patchFnParam({ value: v })}
      />
    </>
  );
}

export default LeftValueEditor;
