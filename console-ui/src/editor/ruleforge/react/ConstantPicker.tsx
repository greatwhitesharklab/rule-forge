/**
 * ConstantPicker — shared two-level constant-library browser.
 *
 * Mirrors {@link VariablePicker} but for `<constant-library>` documents. The
 * right-hand side of a `<value type="Constant">` used to be three free-text
 * inputs (`constCategory` / `const` / `constLabel`); this component replaces
 * them with a controlled Cascader: first pick a category, then a constant.
 * The constant's datatype is shown read-only next to the picker (constant
 * items carry a `type`, which becomes the `<value datatype="…">` attribute).
 *
 * Data flow is single-direction:
 *   props.value   →  rendered selection
 *   user picks    →  props.onChange(next) with all four fields populated
 *
 * The parent owns the value object; this component never mutates
 * `props.value`. Picking a category-only (no constant) still emits the
 * category.
 *
 * Data is supplied by the caller via `libraries` (a `ConstantCategoryGroup[]`,
 * i.e. one array per imported `.cl.xml` library, each holding its categories).
 * Callers without live data can use the {@link useConstantLibraries} hook; the
 * component itself does NO fetching (keeps it unit-testable in jsdom).
 *
 * Differences from VariablePicker
 * -------------------------------
 * - Category carries a display `label` (in addition to the binding `name`);
 *   the Cascader shows `label (name)` when they differ so the user sees the
 *   friendly title.
 * - The constant item's `type` becomes the emitted `datatype`.
 * - No `act` filter (constants have no In/Out flag).
 */
import { Cascader } from 'antd';
import { useMemo } from 'react';
import { FieldLabel } from './FieldLabel';

// ---------------------------------------------------------------------------
// Public types — re-exported for callers.
// ---------------------------------------------------------------------------

/** One constant inside a category. `type` is the Java datatype. */
export interface PickerConstantItem {
  name: string;
  label: string;
  /** Java datatype, e.g. "Integer". */
  type?: string;
}

/**
 * One category inside a constant library. Carries BOTH a binding `name`
 * (written to `<value const-category="…">`) AND a display `label` (title) —
 * the constant `<category>` element has both attributes.
 */
export interface PickerConstantCategory {
  name: string;
  label: string;
  constants: PickerConstantItem[];
}

/** A whole imported constant library is itself a list of categories. */
export type ConstantCategoryGroup = PickerConstantCategory[];

/**
 * The four-attribute constant binding this picker emits. Mirrors the
 * `constCategory` / `const` / `constLabel` / `datatype` fields on ValueExpr
 * that serialize.ts writes for `<value type="Constant">`. (`datatype` is
 * derived from the picked constant's `type`.)
 */
export interface ConstantBinding {
  /** `<value const-category="…">`. */
  constCategory?: string;
  /** `<value const="…">`. */
  const?: string;
  /** `<value const-label="…">`. */
  constLabel?: string;
  /** `<value datatype="…">` — derived from the constant's `type`. */
  datatype?: string;
}

export interface ConstantPickerProps {
  /** The current binding (controlled). */
  value: ConstantBinding;
  /** Called with a fully-populated binding whenever the selection changes. */
  onChange: (next: ConstantBinding) => void;
  /**
   * The imported constant libraries to browse. Each element is one library
   * (an array of categories); the union of all libraries is what the user
   * sees. Pass `[]` (or omit) to render an empty picker.
   */
  libraries?: ConstantCategoryGroup[];
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

function categoryLabel(cat: PickerConstantCategory): string {
  return cat.label && cat.label !== cat.name
    ? `${cat.label} (${cat.name})`
    : cat.name;
}

function buildOptions(libraries: ConstantCategoryGroup[]): CascaderOption[] {
  const singleLibrary = libraries.length === 1 ? libraries[0] : null;

  const categoryOption = (cat: PickerConstantCategory): CascaderOption => {
    const children: CascaderOption[] = (cat.constants || []).map((c) => ({
      value: c.name,
      label: c.label && c.label !== c.name ? `${c.label} (${c.name})` : c.name,
      datatype: c.type ?? '',
    }));
    return {
      value: cat.name,
      label: categoryLabel(cat),
      datatype: '',
      children,
    };
  };

  if (singleLibrary) {
    return singleLibrary.map(categoryOption);
  }

  return libraries.map((lib, i) => ({
    value: `__lib${i}`,
    label: `库 ${i + 1}`,
    datatype: '',
    children: (lib || []).map(categoryOption),
  }));
}

function pathFromBinding(
  binding: ConstantBinding,
  libraries: ConstantCategoryGroup[],
): string[] | undefined {
  if (!binding.constCategory) return undefined;
  const singleLibrary = libraries.length === 1;

  if (singleLibrary) {
    if (!binding.const) return [binding.constCategory];
    return [binding.constCategory, binding.const];
  }

  for (let i = 0; i < libraries.length; i++) {
    const lib = libraries[i] || [];
    if (lib.some((c) => c.name === binding.constCategory)) {
      const libMarker = `__lib${i}`;
      if (!binding.const) return [libMarker, binding.constCategory];
      return [libMarker, binding.constCategory, binding.const];
    }
  }
  return undefined;
}

function lookupDatatype(
  binding: ConstantBinding,
  libraries: ConstantCategoryGroup[],
): string {
  if (!binding.constCategory || !binding.const) return binding.datatype ?? '';
  for (const lib of libraries) {
    for (const cat of lib || []) {
      if (cat.name !== binding.constCategory) continue;
      const c = (cat.constants || []).find((cc) => cc.name === binding.const);
      if (c && c.type) return c.type;
    }
  }
  return binding.datatype ?? '';
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ConstantPicker({
  value,
  onChange,
  libraries = [],
  compact = false,
  placeholder = '选择常量',
  style,
}: ConstantPickerProps) {
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
      onChange({ constCategory: '', const: '', constLabel: '', datatype: '' });
      return;
    }

    let parts = next;
    if (parts.length > 0 && parts[0].startsWith('__lib')) {
      parts = parts.slice(1);
    }
    const [category, constantName] = parts;

    if (!constantName) {
      onChange({ constCategory: category, const: '', constLabel: '', datatype: '' });
      return;
    }

    // Recover label + datatype from the picked constant.
    let constLabel = '';
    let dt = '';
    for (const lib of libraries) {
      for (const cat of lib || []) {
        if (cat.name !== category) continue;
        const c = (cat.constants || []).find((cc) => cc.name === constantName);
        if (c) {
          constLabel = c.label ?? '';
          dt = c.type ?? '';
        }
      }
    }

    onChange({
      constCategory: category,
      const: constantName,
      constLabel,
      datatype: dt,
    });
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
          changeOnSelect
          notFoundContent={hasLibraries ? '无匹配常量' : '未加载常量库'}
        />
      </div>
      {datatype && (
        <div style={{ ...rowStyle, color: '#888', fontSize: 12 }}>
          类型: {datatype}
        </div>
      )}
      <div style={{ ...rowStyle, color: '#888', fontSize: 12 }}>
        <FieldLabel>常量</FieldLabel>{' '}
        {value.const ? `${value.constLabel ?? value.const}` : '（未选择）'}
      </div>
    </div>
  );
}

export default ConstantPicker;
