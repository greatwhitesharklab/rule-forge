/**
 * useVariableLibraries — data hook for the {@link VariablePicker}.
 *
 * Given the imported `.vl.xml` paths a rule file declares (the
 * `libraries: LibraryImport[]` that every React editor already parses out of
 * `<import-variable-library path="…"/>`), this hook fetches and parses those
 * libraries into the `VariableCategoryGroup[]` shape the picker consumes.
 *
 * Fetch strategy
 * --------------
 * `/common/loadXml?files=p1;p2;p3` (semicolon-joined) returns one entry per
 * file. For `.vl.xml` the backend either has a registered deserializer
 * (returns a `{variableCategories: ResourceCategory[]}` object) or — after
 * V5.44.3 when the 4 library deserializers were deleted — falls back to
 * raw-XML passthrough (`{xml: "<variable-library>…"}`). This hook handles
 * BOTH shapes:
 *
 *   - object with `variableCategories` → use directly (already JSON)
 *   - object with `xml`                → DOMParse the raw XML client-side
 *
 * The hook is OPTIONAL: callers that already have library data (e.g. a
 * parent that loaded everything in one `/common/loadXml` call for the rule
 * file itself) can skip the hook and pass `libraries={data}` straight to
 * VariablePicker. The hook only exists to make a one-line data bind possible
 * for editors that don't otherwise fetch the libraries.
 *
 * The fetch is `silent` (no global error dialog) — failures degrade to an
 * empty libraries array so the picker just shows "未加载变量库" rather than
 * blocking the editor.
 */
import { useEffect, useState } from 'react';
import { formPost } from '@/api/client';
import type { PickerVariableCategory, VariableCategoryGroup } from './VariablePicker';

// ---------------------------------------------------------------------------
// Types — kept local (the wire shape from /common/loadXml for a .vl.xml).
// ---------------------------------------------------------------------------

/**
 * Backend response shape for ONE `.vl.xml` file under `/common/loadXml`.
 * Either form is possible (see file header); we tolerate both.
 */
type VlXmlEntry =
  | { xml?: string; variableCategories?: never }
  | { xml?: never; variableCategories?: VlCategory[] };

/** The deserialized category (matches ResourceCategory in resource/action.ts). */
interface VlCategory {
  name: string;
  type?: string;
  clazz?: string;
  variables?: VlVariable[];
}

interface VlVariable {
  name: string;
  label?: string;
  type?: string;
  act?: string;
}

export interface UseVariableLibrariesResult {
  /** One VariableCategoryGroup per imported library, in declaration order. */
  libraries: VariableCategoryGroup[];
  /** True while the first fetch is in flight. */
  loading: boolean;
  /** Set if the fetch failed; libraries will be empty in that case. */
  error: string | null;
}

// ---------------------------------------------------------------------------
// XML parsing — only used when the backend returns raw XML (passthrough).
// ---------------------------------------------------------------------------

/**
 * Parse a `<variable-library>` XML document into a VariableCategoryGroup.
 *
 *   <variable-library>
 *     <category name="客户.客户" type="..." clazz="...">
 *       <var act="InOut" name="age" label="年龄" type="Integer"/>
 *     </category>
 *   </variable-library>
 *
 * Exported (not just module-local) so the unit tests can exercise the raw-XML
 * branch directly without going through the hook's network fetch.
 */
export function parseVariableLibraryXml(xml: string): VariableCategoryGroup {
  if (!xml) return [];
  const doc = new DOMParser().parseFromString(xml, 'application/xml');
  const root = doc.documentElement;
  if (!root || root.tagName !== 'variable-library') return [];

  const categories: PickerVariableCategory[] = [];
  const catNodes = Array.from(root.getElementsByTagName('category'));
  for (const catNode of catNodes) {
    // Guard against nested categories (shouldn't happen, but be defensive).
    if (catNode.parentElement !== root) continue;
    const name = catNode.getAttribute('name') ?? '';
    const variables = Array.from(catNode.getElementsByTagName('var')).map((v) => ({
      name: v.getAttribute('name') ?? '',
      label: v.getAttribute('label') ?? '',
      type: v.getAttribute('type') ?? undefined,
      act: v.getAttribute('act') ?? undefined,
    }));
    categories.push({ name, variables });
  }
  return categories;
}

/** Coerce one backend entry (either shape) to a VariableCategoryGroup. */
function entryToGroup(entry: VlXmlEntry | undefined): VariableCategoryGroup {
  if (!entry) return [];
  if (entry.variableCategories && Array.isArray(entry.variableCategories)) {
    return entry.variableCategories.map((c) => ({
      name: c.name,
      variables: (c.variables ?? []).map((v) => ({
        name: v.name,
        label: v.label ?? '',
        type: v.type,
        act: v.act,
      })),
    }));
  }
  if (entry.xml) {
    return parseVariableLibraryXml(entry.xml);
  }
  return [];
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Load the variable libraries declared by `paths`.
 *
 * @param paths The `.vl.xml` file paths imported by the rule file (the
 *              `path` attribute of each `<import-variable-library>`). Pass an
 *              empty array to short-circuit (no fetch).
 */
export function useVariableLibraries(paths: string[]): UseVariableLibrariesResult {
  const [libraries, setLibraries] = useState<VariableCategoryGroup[]>([]);
  const [loading, setLoading] = useState<boolean>(paths.length > 0);
  const [error, setError] = useState<string | null>(null);

  // Stable key for the dependency array — join paths so a new fetch only
  // triggers when the set actually changes.
  const pathsKey = paths.join(';');

  useEffect(() => {
    let cancelled = false;
    const list = pathsKey ? pathsKey.split(';').filter(Boolean) : [];
    if (list.length === 0) {
      setLibraries([]);
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    formPost<VlXmlEntry[]>('/common/loadXml', { files: list.join(';') }, { silent: true })
      .then((data) => {
        if (cancelled) return;
        const groups = (Array.isArray(data) ? data : []).map(entryToGroup);
        setLibraries(groups);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : String(err));
        setLibraries([]);
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathsKey]);

  return { libraries, loading, error };
}

export default useVariableLibraries;
