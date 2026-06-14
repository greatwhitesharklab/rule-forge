/**
 * ValueEditor — controlled editor for a `ValueExpr` (`<value>` element).
 *
 * The right-hand side of an atom, a named criterion, an action argument, a
 * method/function parameter, etc. — anything that serializes through
 * serializeValue() in model/serialize.ts.
 *
 * Data flow (single direction, parent owns state):
 *   props.value  →  rendered fields
 *   field edit   →  props.onChange(nextValue)
 *
 * The parent must replace its ValueExpr with the new object; this component
 * never mutates `props.value`.
 *
 * Implemented: Input / Variable / VariableCategory / Parameter / Constant /
 * Method / CommonFunction / NamedReference.
 *
 * The Method / CommonFunction fields are edited inline (manual bean/function
 * name + label text + a parameter list / single function-parameter). A real
 * bean/function knowledge-tree picker can replace the name inputs later; the
 * parameter sub-editor already reuses ValueEditor for each parameter value.
 *
 * antd 6 note: the old `addonBefore` prop on `<Input>` is runtime-deprecated
 * (warning "use Space.Compact instead"). We use the non-deprecated `prefix`
 * slot with a FieldLabel span instead, which keeps the small inline field hint
 * without the console warning.
 */
import { Button, Input, Select, Space } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { FunctionParam, MethodParam, ValueExpr } from '../model/types';
import { VALUE_TYPE_OPTIONS } from './constants';
import { FieldLabel } from './FieldLabel';

export interface ValueEditorProps {
  /** The current value expression (controlled). */
  value: ValueExpr;
  /** Called with a new ValueExpr on every field edit. */
  onChange: (next: ValueExpr) => void;
  /** Optional compact mode (no margin under fields). */
  compact?: boolean;
}

/**
 * Patch the value with a partial update, preserving the existing type.
 * Returns a shallow clone so React sees a new object reference.
 */
function patch(v: ValueExpr, p: Partial<ValueExpr>): ValueExpr {
  return { ...v, ...p };
}

/** Build a fresh ValueExpr when the user switches `type`. */
function freshValueForType(type: ValueExpr['type']): ValueExpr {
  switch (type) {
    case 'Input':
      return { type, content: '' };
    case 'Variable':
      return { type, varCategory: '', var: '', varLabel: '', datatype: '' };
    case 'VariableCategory':
      return { type, varCategory: '' };
    case 'Parameter':
      return { type, varCategory: '参数', var: '', varLabel: '', datatype: '' };
    case 'Constant':
      return { type, constCategory: '', const: '', constLabel: '' };
    case 'Method':
      return { type, beanName: '', beanLabel: '', methodName: '', methodLabel: '', parameters: [] };
    case 'CommonFunction':
      return {
        type,
        functionName: '',
        functionLabel: '',
        functionParameter: { name: '', propertyName: '', propertyLabel: '', value: { type: 'Input', content: '' } },
      };
    case 'NamedReference':
      return { type, referenceName: '', propertyName: '', propertyLabel: '', datatype: '' };
    default:
      return { type: 'Input', content: '' };
  }
}

export function ValueEditor({ value, onChange, compact }: ValueEditorProps) {
  const onTypeChange = (next: string) => {
    onChange(freshValueForType(next as ValueExpr['type']));
  };

  const gap = compact ? 4 : 8;
  const rowStyle: React.CSSProperties = { marginBottom: gap };

  return (
    <div>
      <div style={rowStyle}>
        <Select
          size="small"
          style={{ width: '100%' }}
          value={value.type}
          onChange={onTypeChange}
          options={VALUE_TYPE_OPTIONS}
        />
      </div>

      {value.type === 'Input' && (
        <div style={rowStyle}>
          <Input
            size="small"
            placeholder="输入值"
            value={value.content ?? ''}
            onChange={(e) => onChange(patch(value, { content: e.target.value }))}
          />
        </div>
      )}

      {value.type === 'Variable' && (
        <>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>变量分类</FieldLabel>}
              placeholder="如 客户.客户"
              value={value.varCategory ?? ''}
              onChange={(e) => onChange(patch(value, { varCategory: e.target.value }))}
            />
          </div>
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

      {value.type === 'VariableCategory' && (
        <div style={rowStyle}>
          <Input
            size="small"
            prefix={<FieldLabel>变量分类</FieldLabel>}
            placeholder="变量分类名"
            value={value.varCategory ?? ''}
            onChange={(e) => onChange(patch(value, { varCategory: e.target.value }))}
          />
        </div>
      )}

      {value.type === 'Parameter' && (
        <>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>参数名</FieldLabel>}
              placeholder="如 amount"
              value={value.var ?? ''}
              onChange={(e) => onChange(patch(value, { var: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>标签</FieldLabel>}
              placeholder="参数标签"
              value={value.varLabel ?? ''}
              onChange={(e) => onChange(patch(value, { varLabel: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>类型</FieldLabel>}
              placeholder="如 BigDecimal"
              value={value.datatype ?? ''}
              onChange={(e) => onChange(patch(value, { datatype: e.target.value }))}
            />
          </div>
        </>
      )}

      {value.type === 'Constant' && (
        <>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>常量分类</FieldLabel>}
              placeholder="常量分类名"
              value={value.constCategory ?? ''}
              onChange={(e) => onChange(patch(value, { constCategory: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>常量名</FieldLabel>}
              placeholder="如 ONE_HUNDRED"
              value={value.const ?? ''}
              onChange={(e) => onChange(patch(value, { const: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>标签</FieldLabel>}
              placeholder="常量标签"
              value={value.constLabel ?? ''}
              onChange={(e) => onChange(patch(value, { constLabel: e.target.value }))}
            />
          </div>
        </>
      )}

      {value.type === 'Method' && (
        <MethodValueFields value={value} onChange={onChange} rowStyle={rowStyle} />
      )}

      {value.type === 'CommonFunction' && (
        <CommonFunctionValueFields value={value} onChange={onChange} rowStyle={rowStyle} />
      )}

      {value.type === 'NamedReference' && (
        <>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>引用名</FieldLabel>}
              placeholder="命名引用 reference-name"
              value={value.referenceName ?? ''}
              onChange={(e) => onChange(patch(value, { referenceName: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>属性</FieldLabel>}
              placeholder="property-name"
              value={value.propertyName ?? ''}
              onChange={(e) => onChange(patch(value, { propertyName: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>属性标签</FieldLabel>}
              placeholder="property-label"
              value={value.propertyLabel ?? ''}
              onChange={(e) => onChange(patch(value, { propertyLabel: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              prefix={<FieldLabel>类型</FieldLabel>}
              placeholder="如 String"
              value={value.datatype ?? ''}
              onChange={(e) => onChange(patch(value, { datatype: e.target.value }))}
            />
          </div>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Method value fields — bean / method name+label + a parameter list.
// ---------------------------------------------------------------------------

interface MethodValueFieldsProps {
  value: ValueExpr;
  onChange: (next: ValueExpr) => void;
  rowStyle: React.CSSProperties;
}

function MethodValueFields({ value, onChange, rowStyle }: MethodValueFieldsProps) {
  const patchMethod = (p: Partial<ValueExpr>) => onChange(patch(value, p));

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
      <ValueMethodParametersEditor
        parameters={value.parameters ?? []}
        onChange={(parameters) => patchMethod({ parameters })}
      />
    </>
  );
}

/**
 * Sub-editor for the `<parameter>` list of a Method value. Each row is
 * name + type + a nested ValueEditor. Add / delete via buttons.
 *
 * Mirrors the ParametersEditor pattern used by ActionEditor for
 * execute-method actions so the two stay visually consistent.
 */
function ValueMethodParametersEditor({
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

// ---------------------------------------------------------------------------
// CommonFunction value fields — function name+label + a single
// function-parameter (name / propertyName / propertyLabel + nested value).
// ---------------------------------------------------------------------------

interface CommonFunctionValueFieldsProps {
  value: ValueExpr;
  onChange: (next: ValueExpr) => void;
  rowStyle: React.CSSProperties;
}

function CommonFunctionValueFields({ value, onChange, rowStyle }: CommonFunctionValueFieldsProps) {
  const patchFn = (p: Partial<ValueExpr>) => onChange(patch(value, p));

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
        onChange={(v: ValueExpr) => patchFnParam({ value: v })}
      />
    </>
  );
}

export default ValueEditor;
