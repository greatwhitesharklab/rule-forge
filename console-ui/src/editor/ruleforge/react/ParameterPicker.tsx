/**
 * ParameterPicker — shared parameter-library browser.
 *
 * Mirrors {@link VariablePicker} / {@link ConstantPicker} but for
 * `<parameter-library>` documents (`.pl.xml`). The right-hand side of a
 * `<value type="Parameter">` used to be three free-text inputs
 * (`var` / `varLabel` / `datatype`); this component replaces them with a
 * controlled Cascader (or flat Select, depending on library count): pick a
 * parameter directly.
 *
 * Data flow is single-direction:
 *   props.value   →  rendered selection
 *   user picks    →  props.onChange(next) with all three fields populated
 *
 * The parent owns the value object; this component never mutates
 * `props.value`.
 *
 * Parameter-library shape
 * -----------------------
 * Unlike variable / constant libraries, the parameter library has NO category
 * level — it is a flat list of `<parameter>` elements directly under the root
 * (see src/parameter/action.ts saveData). So this picker is a single-level
 * browser: under one imported library it shows a flat list of parameters;
 * under multiple imported libraries it nests one level deep per library
 * (`库 1 / 库 2 …` so colliding parameter names stay distinguishable).
 *
 * Reuses the ValueExpr `Parameter` fields (`var` / `varLabel` / `datatype`)
 * rather than introducing new ones — a `<value type="Parameter">` serializes
 * those same attributes. `varCategory` is left untouched (it defaults to
 * `'参数'` per freshValueForType and the picker does not change it).
 */
import { Cascader } from 'antd';
import { useMemo } from 'react';
import { FieldLabel } from './FieldLabel';

// ---------------------------------------------------------------------------
// Public types — re-exported for callers.
// ---------------------------------------------------------------------------

/** One parameter inside a library. `type` is the Java datatype. */
export interface PickerParameterItem {
  name: string;
  label: string;
  /** Java datatype, e.g. "BigDecimal". */
  type?: string;
  /** "In" / "Out" / "InOut" — preserved from XML, not filtered here. */
  act?: string;
}

/**
 * A whole imported parameter library — a FLAT list of parameters (there is no
 * category level in `<parameter-library>`). A project imports several
 * libraries, so the `libraries` prop is `ParameterLibrary[]`.
 */
export type ParameterLibrary = PickerParameterItem[];

/**
 * The three-attribute parameter binding this picker emits. Mirrors the
 * `var` / `varLabel` / `datatype` fields on the ValueExpr `Parameter` variant
 * that serialize.ts writes for `<value type="Parameter">`.
 */
export interface ParameterBinding {
  /** `<value var="…">`. */
  var?: string;
  /** `<value var-label="…">`. */
  varLabel?: string;
  /** `<value datatype="…">` — derived from the parameter's `type`. */
  datatype?: string;
}

export interface ParameterPickerProps {
  /** The current binding (controlled). */
  value: ParameterBinding;
  /** Called with a fully-populated binding whenever the selection changes. */
  onChange: (next: ParameterBinding) => void;
  /**
   * The imported parameter libraries to browse. Each element is one library
   * (a flat array of parameters). Pass `[]` (or omit) to render an empty
   * picker.
   */
  libraries?: ParameterLibrary[];
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
  datatype?: string;
  children?: CascaderOption[];
}

function paramOption(p: PickerParameterItem): CascaderOption {
  return {
    value: p.name,
    label: p.label && p.label !== p.name ? `${p.label} (${p.name})` : p.name,
    datatype: p.type ?? '',
  };
}

function buildOptions(libraries: ParameterLibrary[]): CascaderOption[] {
  const singleLibrary = libraries.length === 1 ? libraries[0] : null;

  if (singleLibrary) {
    return (singleLibrary || []).map(paramOption);
  }

  return libraries.map((lib, i) => ({
    value: `__lib${i}`,
    label: `库 ${i + 1}`,
    datatype: '',
    children: (lib || []).map(paramOption),
  }));
}

function pathFromBinding(
  binding: ParameterBinding,
  libraries: ParameterLibrary[],
): string[] | undefined {
  if (!binding.var) return undefined;
  const singleLibrary = libraries.length === 1;

  if (singleLibrary) {
    return [binding.var];
  }

  for (let i = 0; i < libraries.length; i++) {
    const lib = libraries[i] || [];
    if (lib.some((p) => p.name === binding.var)) {
      return [`__lib${i}`, binding.var];
    }
  }
  return undefined;
}

function lookupDatatype(
  binding: ParameterBinding,
  libraries: ParameterLibrary[],
): string {
  if (!binding.var) return binding.datatype ?? '';
  for (const lib of libraries) {
    const p = (lib || []).find((pp) => pp.name === binding.var);
    if (p && p.type) return p.type;
  }
  return binding.datatype ?? '';
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ParameterPicker({
  value,
  onChange,
  libraries = [],
  compact = false,
  placeholder = '选择参数',
  style,
}: ParameterPickerProps) {
  const options = useMemo(() => buildOptions(libraries), [libraries]);
  const path = useMemo(
    () => pathFromBinding(value, libraries),
    [value, libraries],
  );
  const datatype = useMemo(
    () => lookupDatatype(value, libraries),
    [value, libraries],
  );

  const gap = compact ? 4 : 8;
  const rowStyle: React.CSSProperties = { marginBottom: gap };

  const handleCascade = (next: string[]): void => {
    if (!next || next.length === 0) {
      onChange({ var: '', varLabel: '', datatype: '' });
      return;
    }

    let parts = next;
    if (parts.length > 0 && parts[0].startsWith('__lib')) {
      parts = parts.slice(1);
    }
    const paramName = parts[0];

    if (!paramName) {
      onChange({ var: '', varLabel: '', datatype: '' });
      return;
    }

    // Recover label + datatype from the picked parameter.
    let varLabel = '';
    let dt = '';
    for (const lib of libraries) {
      const p = (lib || []).find((pp) => pp.name === paramName);
      if (p) {
        varLabel = p.label ?? '';
        dt = p.type ?? '';
        break;
      }
    }

    onChange({ var: paramName, varLabel, datatype: dt });
  };

  const hasLibraries = libraries.some((lib) => (lib || []).length > 0);

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
          notFoundContent={hasLibraries ? '无匹配参数' : '未加载参数库'}
        />
      </div>
      {datatype && (
        <div style={{ ...rowStyle, color: '#888', fontSize: 12 }}>
          类型: {datatype}
        </div>
      )}
      <div style={{ ...rowStyle, color: '#888', fontSize: 12 }}>
        <FieldLabel>参数</FieldLabel>{' '}
        {value.var ? `${value.varLabel ?? value.var}` : '（未选择）'}
      </div>
    </div>
  );
}

export default ParameterPicker;
