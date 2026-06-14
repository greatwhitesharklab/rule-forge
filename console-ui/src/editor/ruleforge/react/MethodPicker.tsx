/**
 * MethodPicker — shared bean/method library browser for action libraries.
 *
 * Mirrors {@link VariablePicker} / {@link ConstantPicker} but for
 * `<action-library>` documents (`.al.xml`). The bean / method name fields of
 * an `<execute-method>` action (and a `<value type="Method">`) used to be four
 * free-text inputs (`bean` / `beanLabel` / `methodName` / `methodLabel`); this
 * component replaces them with a controlled Cascader: first pick a bean, then a
 * method. The selected method's parameter signature (name + Java type) is
 * shown read-only below the picker so the BA can see what to fill in.
 *
 * Data flow is single-direction:
 *   props.value   →  rendered selection
 *   user picks    →  props.onChange(next) with all four fields populated
 *
 * The parent owns the value object; this component never mutates
 * `props.value`. Picking a bean-only (no method) still emits the bean fields
 * (lets the user stage a bean before choosing the method).
 *
 * Action-library shape
 * --------------------
 *   <action-library>
 *     <spring-bean id='customerService' name='客户服务'>
 *       <method name='查询客户' method-name='findCustomer'>
 *         <parameter name='id' type='String'/>
 *       </method>
 *     </spring-bean>
 *   </action-library>
 *
 * Field mapping to the `<execute-method>` attributes that serialize.ts writes:
 *
 *   spring-bean.id     → bean        (→ bean-name="…")
 *   spring-bean.name   → beanLabel   (→ bean-label="…")
 *   method.name        → methodLabel (→ method-label="…")
 *   method.method-name → methodName  (→ method-name="…")
 *
 * Data is supplied by the caller via `libraries` (an `ActionLibrary[]`, i.e.
 * one array of spring-beans per imported `.al.xml` library). Callers without
 * live data can use the {@link useActionLibraries} hook; the component itself
 * does NO fetching (keeps it unit-testable in jsdom).
 *
 * Differences from VariablePicker
 * -------------------------------
 * - The bean's binding key is its `id` (not `name`) — that is what the
 *   executor looks up the Spring bean by. The `name` is the display label
 *   (becomes bean-label). The Cascader option shows `name (id)` so both are
 *   visible.
 * - A method carries TWO identifiers: `name` (label, → method-label) and
 *   `method-name` (the actual Java method, → method-name). The option shows
 *   `name (method-name)`.
 * - The method's parameter signature is rendered read-only below the picker
 *   (the action carries its OWN `<parameter>` list with values; the library
 *   signature is only a hint, so we do not auto-fill the parameters).
 */
import { Cascader } from 'antd';
import { useMemo } from 'react';
import { FieldLabel } from './FieldLabel';

// ---------------------------------------------------------------------------
// Public types — re-exported for callers.
// ---------------------------------------------------------------------------

/** One parameter of a method — name + Java type (display only). */
export interface PickerActionParameter {
  name: string;
  /** Java datatype, e.g. "String". */
  type: string;
}

/**
 * One method inside a spring-bean. `name` is the display label (becomes
 * method-label); `method-name` is the actual Java method name (becomes
 * method-name).
 */
export interface PickerActionMethod {
  /** Display name — written into method-label. */
  name: string;
  /** Actual Java method name — written into method-name. */
  methodName: string;
  parameters: PickerActionParameter[];
}

/**
 * One spring-bean inside an action library. `id` is the binding key (becomes
 * bean-name); `name` is the display label (becomes bean-label).
 */
export interface PickerSpringBean {
  /** Spring bean id — written into bean-name. */
  id: string;
  /** Display name — written into bean-label. */
  name: string;
  methods: PickerActionMethod[];
}

/**
 * A whole imported action library is itself a list of spring-beans. A project
 * imports several libraries, so the `libraries` prop is `ActionLibrary[]`.
 */
export type ActionLibrary = PickerSpringBean[];

/**
 * The four-attribute bean/method binding this picker emits. Mirrors the
 * `bean` / `beanLabel` / `methodName` / `methodLabel` fields on the
 * `execute-method` Action variant (and the Method ValueExpr variant) that
 * serialize.ts writes for `<execute-method …>` / `<value type="Method">`.
 */
export interface MethodBinding {
  /** `<execute-method bean-name="…">`. */
  bean?: string;
  /** `<execute-method bean-label="…">`. */
  beanLabel?: string;
  /** `<execute-method method-name="…">`. */
  methodName?: string;
  /** `<execute-method method-label="…">`. */
  methodLabel?: string;
}

export interface MethodPickerProps {
  /** The current binding (controlled). */
  value: MethodBinding;
  /** Called with a fully-populated binding whenever the selection changes. */
  onChange: (next: MethodBinding) => void;
  /**
   * The imported action libraries to browse. Each element is one library
   * (an array of spring-beans); the union of all libraries is what the user
   * sees. Pass `[]` (or omit) to render an empty picker.
   */
  libraries?: ActionLibrary[];
  /** Optional compact mode (no margin under fields). */
  compact?: boolean;
  /** Optional placeholder shown when nothing is selected. */
  placeholder?: string;
  /** Optional inline style for the outer wrapper. */
  style?: React.CSSProperties;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface CascaderOption {
  value: string;
  label: string;
  /** Carried through so we can recover the method's methodName on select. */
  methodName?: string;
  parameters?: PickerActionParameter[];
  children?: CascaderOption[];
}

function beanOption(bean: PickerSpringBean): CascaderOption {
  const beanLabel =
    bean.name && bean.name !== bean.id ? `${bean.name} (${bean.id})` : bean.id;
  const children: CascaderOption[] = (bean.methods || []).map((m) => {
    const methodLabel =
      m.name && m.name !== m.methodName
        ? `${m.name} (${m.methodName})`
        : m.methodName || m.name;
    return {
      value: m.methodName || m.name,
      label: methodLabel,
      methodName: m.methodName,
      parameters: m.parameters,
    };
  });
  return {
    value: bean.id,
    label: beanLabel,
    children,
  };
}

function buildOptions(libraries: ActionLibrary[]): CascaderOption[] {
  const singleLibrary = libraries.length === 1 ? libraries[0] : null;

  if (singleLibrary) {
    // Hoist beans to the top level when there is only one library.
    return (singleLibrary || []).map(beanOption);
  }

  // Multiple libraries: nest under a per-library node so colliding bean ids
  // stay distinguishable.
  return libraries.map((lib, i) => ({
    value: `__lib${i}`,
    label: `库 ${i + 1}`,
    children: (lib || []).map(beanOption),
  }));
}

/**
 * Compute the Cascader `value` (path array) to show for a given binding.
 * Returns `undefined` when the binding is empty (nothing selected).
 *
 * For a multiple-library tree the path is `[libMarker, beanId, methodName]`;
 * for a single-library tree it is `[beanId, methodName]` (or just `[beanId]`
 * for a bean-only binding).
 */
function pathFromBinding(
  binding: MethodBinding,
  libraries: ActionLibrary[],
): string[] | undefined {
  if (!binding.bean) return undefined;
  const singleLibrary = libraries.length === 1;

  if (singleLibrary) {
    if (!binding.methodName) return [binding.bean];
    return [binding.bean, binding.methodName];
  }

  // Multiple libraries: find which library contains the bean id.
  for (let i = 0; i < libraries.length; i++) {
    const lib = libraries[i] || [];
    if (lib.some((b) => b.id === binding.bean)) {
      const libMarker = `__lib${i}`;
      if (!binding.methodName) return [libMarker, binding.bean];
      return [libMarker, binding.bean, binding.methodName];
    }
  }
  // Bean not found in any library (stale binding) — show nothing so the user
  // can re-pick (the free-text fallback in the caller still shows the value).
  return undefined;
}

/**
 * Resolve the parameter signature for the current binding by looking it up in
 * the libraries. Used only for the read-only hint below the picker.
 */
function lookupParameters(
  binding: MethodBinding,
  libraries: ActionLibrary[],
): PickerActionParameter[] | undefined {
  if (!binding.bean || !binding.methodName) return undefined;
  for (const lib of libraries) {
    for (const bean of lib || []) {
      if (bean.id !== binding.bean) continue;
      const m = (bean.methods || []).find(
        (mm) => mm.methodName === binding.methodName,
      );
      if (m) return m.parameters;
    }
  }
  return undefined;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function MethodPicker({
  value,
  onChange,
  libraries = [],
  compact = false,
  placeholder = '选择 bean / 方法',
  style,
}: MethodPickerProps) {
  const options = useMemo(() => buildOptions(libraries), [libraries]);
  const path = useMemo(
    () => pathFromBinding(value, libraries),
    [value, libraries],
  );
  const parameters = useMemo(
    () => lookupParameters(value, libraries),
    [value, libraries],
  );

  const gap = compact ? 4 : 8;
  const rowStyle: React.CSSProperties = { marginBottom: gap };

  const handleCascade = (next: string[]): void => {
    if (!next || next.length === 0) {
      // Cleared.
      onChange({ bean: '', beanLabel: '', methodName: '', methodLabel: '' });
      return;
    }

    // Normalize: strip the synthetic `__libN` prefix if present.
    let parts = next;
    if (parts.length > 0 && parts[0].startsWith('__lib')) {
      parts = parts.slice(1);
    }
    const [beanId, methodNameValue] = parts;

    if (!beanId) {
      onChange({ bean: '', beanLabel: '', methodName: '', methodLabel: '' });
      return;
    }

    if (!methodNameValue) {
      // Bean-only selection — recover the bean label.
      let beanLabel = '';
      for (const lib of libraries) {
        for (const bean of lib || []) {
          if (bean.id === beanId) {
            beanLabel = bean.name ?? '';
            break;
          }
        }
      }
      onChange({ bean: beanId, beanLabel, methodName: '', methodLabel: '' });
      return;
    }

    // Full bean+method selection — recover bean label + method label.
    let beanLabel = '';
    let methodLabel = '';
    for (const lib of libraries) {
      for (const bean of lib || []) {
        if (bean.id !== beanId) continue;
        beanLabel = bean.name ?? '';
        const m = (bean.methods || []).find(
          (mm) => (mm.methodName || mm.name) === methodNameValue,
        );
        if (m) {
          methodLabel = m.name ?? '';
          // Prefer the real method-name when present (it is the Java method).
          const realMethodName = m.methodName || methodNameValue;
          onChange({
            bean: beanId,
            beanLabel,
            methodName: realMethodName,
            methodLabel,
          });
          return;
        }
      }
    }

    // Method not found in library (stale / hand-edited) — keep the raw values.
    onChange({
      bean: beanId,
      beanLabel,
      methodName: methodNameValue,
      methodLabel,
    });
  };

  const hasLibraries = libraries.some((lib) => (lib || []).length > 0);

  const signature = parameters && parameters.length > 0
    ? parameters.map((p) => `${p.name}: ${p.type}`).join(', ')
    : parameters && parameters.length === 0
      ? '()'
      : '';

  return (
    <div style={style}>
      <div style={rowStyle}>
        <Cascader
          size="small"
          style={{ width: '100%' }}
          expandTrigger="hover"
          placeholder={placeholder}
          value={path}
          onChange={(next) => handleCascade(next as string[])}
          options={options}
          changeOnSelect
          notFoundContent={hasLibraries ? '无匹配方法' : '未加载动作库'}
        />
      </div>
      {signature && (
        <div style={{ ...rowStyle, color: '#888', fontSize: 12 }}>
          <FieldLabel>参数签名</FieldLabel> {signature}
        </div>
      )}
      <div style={{ ...rowStyle, color: '#888', fontSize: 12 }}>
        <FieldLabel>方法</FieldLabel>{' '}
        {value.bean
          ? `${value.beanLabel ?? value.bean}.${value.methodName ?? ''}`
          : '（未选择）'}
      </div>
    </div>
  );
}

export default MethodPicker;
