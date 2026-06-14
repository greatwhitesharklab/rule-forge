/**
 * VariablePicker — shared two-level variable-library browser.
 *
 * Replaces the free-text `var-category` / `var` / `var-label` / `datatype`
 * inputs that every React rule editor (decision-table / scorecard / crosstab
 * / complex-scorecard / ruleforge LeftValue & Value editors) used to render.
 * The original jquery editor exposed the same UX via a right-click context
 * menu driven by `window._VariableValueArray` (see
 * components/widgets/VariableValueWidget.tsx); this React component reproduces
 * it as a controlled Cascader: first pick a category, then a variable. The
 * datatype is shown read-only next to the picker.
 *
 * Data flow is single-direction:
 *   props.value   →  rendered selection
 *   user picks    →  props.onChange(next) with all four fields populated
 *
 * The parent owns the value object; this component never mutates
 * `props.value`. Picking a category-only (no variable) still emits the
 * category — this matches `<value type="VariableCategory">` which carries
 * just the category.
 *
 * Data is supplied by the caller via `libraries` (a `VariableCategoryGroup[]`,
 * i.e. one array per imported `.vl.xml` library, each holding its categories).
 * Callers without live data can use the {@link useVariableLibraries} hook; the
 * component itself does NO fetching (keeps it unit-testable in jsdom).
 *
 * antd 6 note: Cascader is stable and non-deprecated; we use `size="small"`
 * to match the existing ruleforge editor inputs.
 */
import { Cascader, Input } from 'antd';
import { useMemo } from 'react';
import { FieldLabel } from './FieldLabel';

// ---------------------------------------------------------------------------
// Public types — re-exported for callers.
// ---------------------------------------------------------------------------

/**
 * One variable inside a category. Field names mirror the original jquery
 * `VariableItem` and the resource-editor `VariableItem`
 * (resource/action.ts): `type` is the Java datatype ("Integer" / "String" /
 * "BigDecimal" …), `act` is the In/Out/InOut flag (used by some editors to
 * filter; ignored here unless `actFilter` is set).
 */
export interface PickerVariableItem {
  name: string;
  label: string;
  /** Java datatype, e.g. "Integer". */
  type?: string;
  /** "In" / "Out" / "InOut" — optional filter. */
  act?: string;
}

/**
 * One category inside a library. Matches `VariableCategory` in the legacy
 * widget and `ResourceCategory` (minus `clazz`) in the resource editor.
 */
export interface PickerVariableCategory {
  name: string;
  variables: PickerVariableItem[];
}

/**
 * A whole imported library is itself a list of categories (the legacy
 * `VariableCategoryGroup = VariableCategory[]`). A project imports several
 * libraries, so the `libraries` prop is `VariableCategoryGroup[]`.
 */
export type VariableCategoryGroup = PickerVariableCategory[];

/**
 * The four-attribute variable binding this picker emits. Mirrors the
 * `var-category` / `var` / `var-label` / `datatype` XML attributes that
 * serialize.ts writes for `<col>`, `<value type="Variable">`, `<left>` and
 * the crosstab bundle / target editors.
 */
export interface VariableBinding {
  /** `<col var-category="…">` / `<value var-category="…">`. */
  varCategory?: string;
  /** `<col var="…">` / `<value var="…">`. */
  var?: string;
  /** `<col var-label="…">` / `<value var-label="…">`. */
  varLabel?: string;
  /** `<col datatype="…">` / `<value datatype="…">`. */
  datatype?: string;
}

export interface VariablePickerProps {
  /** The current binding (controlled). */
  value: VariableBinding;
  /** Called with a fully-populated binding whenever the selection changes. */
  onChange: (next: VariableBinding) => void;
  /**
   * The imported variable libraries to browse. Each element is one library
   * (an array of categories); the union of all libraries is what the user
   * sees. Pass `[]` (or omit) to render an empty picker.
   */
  libraries?: VariableCategoryGroup[];
  /** Optional compact mode (no margin under fields). */
  compact?: boolean;
  /** Optional placeholder shown when nothing is selected. */
  placeholder?: string;
  /**
   * Optional act filter — when set, only variables whose `act` contains this
   * substring are offered (legacy widget behavior). Defaults to no filter.
   */
  actFilter?: string;
  /**
   * When true, the datatype field is rendered as an editable Input (lets the
   * user override for ad-hoc variables not in any library). Defaults to
   * false (read-only display of the picked variable's datatype).
   */
  allowDatatypeEdit?: boolean;
  /** Optional inline style for the outer wrapper. */
  style?: React.CSSProperties;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Build the Cascader option tree from the libraries prop.
 *
 * Structure: each library becomes a top-level option whose label is the
 * library index (`库 1`, `库 2` …) when there is more than one library, OR
 * the categories are hoisted to the top level when there is exactly one
 * library (the common case — a single imported `.vl.xml`). Each category
 * option's children are its variables; the variable option carries the
 * datatype in `value[2]` so the picker can read it back on select.
 *
 * AntD Cascader `value` is `SingleValueType = (string | number)[]`; we pack
 * `[categoryName, variableName, datatype]` so the onChange handler can rebuild
 * the full binding from the path alone.
 */
interface CascaderOption {
  value: string;
  label: string;
  datatype?: string;
  children?: CascaderOption[];
}

function matchAct(actFilter: string | undefined, v: PickerVariableItem): boolean {
  if (!actFilter) return true;
  return !!v.act && v.act.indexOf(actFilter) > -1;
}

function buildOptions(
  libraries: VariableCategoryGroup[],
  actFilter?: string,
): CascaderOption[] {
  const singleLibrary = libraries.length === 1 ? libraries[0] : null;

  // Helper that turns one category into a Cascader option.
  const categoryOption = (cat: PickerVariableCategory): CascaderOption => {
    const children: CascaderOption[] = (cat.variables || [])
      .filter((v) => matchAct(actFilter, v))
      .map((v) => ({
        value: v.name,
        label: v.label ? `${v.label} (${v.name})` : v.name,
        datatype: v.type ?? '',
      }));
    return {
      value: cat.name,
      label: cat.name,
      datatype: '',
      children,
    };
  };

  if (singleLibrary) {
    // Hoist categories to the top level when there is only one library.
    return singleLibrary.map(categoryOption);
  }

  // Multiple libraries: nest under a per-library node so category names that
  // collide across libraries stay distinguishable.
  return libraries.map((lib, i) => {
    const libLabel = `库 ${i + 1}`;
    return {
      value: `__lib${i}`,
      label: libLabel,
      datatype: '',
      children: (lib || []).map(categoryOption),
    };
  });
}

/**
 * Compute the Cascader `value` (path array) to show for a given binding.
 * Returns `undefined` when the binding is empty (nothing selected).
 *
 * For a multiple-library tree the path is `[libMarker, category, variable]`;
 * for a single-library tree it is `[category, variable]` (or just
 * `[category]` for a category-only binding).
 */
function pathFromBinding(
  binding: VariableBinding,
  libraries: VariableCategoryGroup[],
  options: CascaderOption[],
): string[] | undefined {
  if (!binding.varCategory) return undefined;
  const singleLibrary = libraries.length === 1;

  if (singleLibrary) {
    if (!binding.var) return [binding.varCategory];
    return [binding.varCategory, binding.var];
  }

  // Multiple libraries: find which library contains the category.
  for (let i = 0; i < libraries.length; i++) {
    const lib = libraries[i] || [];
    if (lib.some((c) => c.name === binding.varCategory)) {
      const libMarker = `__lib${i}`;
      if (!binding.var) return [libMarker, binding.varCategory];
      return [libMarker, binding.varCategory, binding.var];
    }
  }
  // Category not found in any library (stale binding) — show just the path
  // for the first library so the user can re-pick.
  return undefined;
}

/**
 * Resolve the datatype for the current binding by looking it up in the
 * libraries. Returns the existing `binding.datatype` as a fallback (so a
 * binding that the user typed by hand before this picker existed still
 * shows its datatype).
 */
function lookupDatatype(
  binding: VariableBinding,
  libraries: VariableCategoryGroup[],
): string {
  if (!binding.varCategory || !binding.var) return binding.datatype ?? '';
  for (const lib of libraries) {
    for (const cat of lib || []) {
      if (cat.name !== binding.varCategory) continue;
      const v = (cat.variables || []).find((vv) => vv.name === binding.var);
      if (v && v.type) return v.type;
    }
  }
  return binding.datatype ?? '';
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function VariablePicker({
  value,
  onChange,
  libraries = [],
  compact = false,
  placeholder = '选择变量',
  actFilter,
  allowDatatypeEdit = false,
  style,
}: VariablePickerProps) {
  const options = useMemo(
    () => buildOptions(libraries, actFilter),
    [libraries, actFilter],
  );
  const path = useMemo(
    () => pathFromBinding(value, libraries, options),
    [value, libraries, options],
  );
  const datatype = useMemo(
    () => lookupDatatype(value, libraries),
    [value, libraries],
  );

  const gap = compact ? 4 : 8;
  const rowStyle: React.CSSProperties = { marginBottom: gap };

  const handleCascade = (next: string[]): void => {
    if (!next || next.length === 0) {
      // Cleared.
      onChange({ varCategory: '', var: '', varLabel: '', datatype: '' });
      return;
    }

    // Normalize: strip the synthetic `__libN` prefix if present.
    let parts = next;
    if (parts.length > 0 && parts[0].startsWith('__lib')) {
      parts = parts.slice(1);
    }
    const [category, variableName] = parts;

    if (!variableName) {
      // Category-only selection (mirrors VariableCategory value type).
      onChange({ varCategory: category, var: '', varLabel: '', datatype: '' });
      return;
    }

    // Look up the variable to recover its label + datatype.
    let varLabel = '';
    let dt = '';
    for (const lib of libraries) {
      for (const cat of lib || []) {
        if (cat.name !== category) continue;
        const v = (cat.variables || []).find((vv) => vv.name === variableName);
        if (v) {
          varLabel = v.label ?? '';
          dt = v.type ?? '';
        }
      }
    }

    onChange({
      varCategory: category,
      var: variableName,
      varLabel,
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
          notFoundContent={hasLibraries ? '无匹配变量' : '未加载变量库'}
        />
      </div>
      {allowDatatypeEdit ? (
        <div style={rowStyle}>
          <Input
            size="small"
            prefix={<FieldLabel>类型</FieldLabel>}
            placeholder="如 Integer"
            value={value.datatype ?? ''}
            onChange={(e) => onChange({ ...value, datatype: e.target.value })}
          />
        </div>
      ) : (
        datatype && (
          <div style={{ ...rowStyle, color: '#888', fontSize: 12 }}>
            类型: {datatype}
          </div>
        )
      )}
    </div>
  );
}

export default VariablePicker;
