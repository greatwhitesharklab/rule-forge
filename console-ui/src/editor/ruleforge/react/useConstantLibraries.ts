/**
 * useConstantLibraries — data hook for the {@link ConstantPicker}.
 *
 * Mirrors {@link useVariableLibraries} but for `<constant-library>` documents
 * (`.cl.xml`). Given the imported `.cl.xml` paths a rule file declares (the
 * `libraries: LibraryImport[]` filtered to `type === 'Constant'`, i.e. the
 * `path` attribute of each `<import-constant-library path="…"/>`), this hook
 * fetches and parses those libraries into the `ConstantCategoryGroup[]` shape
 * the picker consumes.
 *
 * Fetch strategy
 * --------------
 * Same as the variable hook: `/common/loadXml?files=p1;p2;p3` returns one
 * entry per file. After V5.44.3 deleted the library deserializers, the backend
 * returns raw-XML passthrough (`{xml: "<constant-library>…"}`); this hook
 * DOMParses that client-side. It also tolerates a pre-deserialized object
 * shape (`{constantCategories: [...]}`) should the backend ever grow one.
 *
 * The hook is OPTIONAL and `silent` (no global error dialog) — failures
 * degrade to an empty array so the picker just shows "未加载常量库".
 *
 * Constant library XML shape
 * --------------------------
 *   <constant-library>
 *     <category name="常量分类" label="常量分类标题">
 *       <constant name="ONE_HUNDRED" label="一百" type="Integer"/>
 *     </category>
 *   </constant-library>
 *
 * Note the category carries BOTH `name` (the binding key written into
 * `<value const-category="…">`) AND a `label` (display title) — unlike the
 * variable `<category>` which has only `name`. Both are preserved here.
 */
import { useEffect, useState } from 'react';
import { formPost } from '@/api/client';
import type {
  ConstantCategoryGroup,
  PickerConstantCategory,
} from './ConstantPicker';

// ---------------------------------------------------------------------------
// Types — kept local (the wire shape from /common/loadXml for a .cl.xml).
// ---------------------------------------------------------------------------

/**
 * Backend response shape for ONE `.cl.xml` file under `/common/loadXml`.
 * Either form is possible (see file header); we tolerate both.
 */
type ClXmlEntry =
  | { xml?: string; constantCategories?: never }
  | { xml?: never; constantCategories?: ClCategory[] };

/** The deserialized category. */
interface ClCategory {
  name: string;
  label?: string;
  type?: string;
  constants?: ClConstant[];
}

interface ClConstant {
  name: string;
  label?: string;
  type?: string;
}

export interface UseConstantLibrariesResult {
  /** One ConstantCategoryGroup per imported library, in declaration order. */
  libraries: ConstantCategoryGroup[];
  /** True while the first fetch is in flight. */
  loading: boolean;
  /** Set if the fetch failed; libraries will be empty in that case. */
  error: string | null;
}

// ---------------------------------------------------------------------------
// XML parsing — only used when the backend returns raw XML (passthrough).
// ---------------------------------------------------------------------------

/**
 * Parse a `<constant-library>` XML document into a ConstantCategoryGroup.
 *
 * Exported (not just module-local) so the unit tests can exercise the raw-XML
 * branch directly without going through the hook's network fetch.
 */
export function parseConstantLibraryXml(xml: string): ConstantCategoryGroup {
  if (!xml) return [];
  const doc = new DOMParser().parseFromString(xml, 'application/xml');
  const root = doc.documentElement;
  if (!root || root.tagName !== 'constant-library') return [];

  const categories: PickerConstantCategory[] = [];
  const catNodes = Array.from(root.getElementsByTagName('category'));
  for (const catNode of catNodes) {
    // Guard against nested categories (shouldn't happen, but be defensive).
    if (catNode.parentElement !== root) continue;
    const name = catNode.getAttribute('name') ?? '';
    const label = catNode.getAttribute('label') ?? '';
    const constants = Array.from(catNode.getElementsByTagName('constant')).map((c) => ({
      name: c.getAttribute('name') ?? '',
      label: c.getAttribute('label') ?? '',
      type: c.getAttribute('type') ?? undefined,
    }));
    categories.push({ name, label, constants });
  }
  return categories;
}

/** Coerce one backend entry (either shape) to a ConstantCategoryGroup. */
function entryToGroup(entry: ClXmlEntry | undefined): ConstantCategoryGroup {
  if (!entry) return [];
  if (entry.constantCategories && Array.isArray(entry.constantCategories)) {
    return entry.constantCategories.map((c) => ({
      name: c.name,
      label: c.label ?? '',
      constants: (c.constants ?? []).map((cst) => ({
        name: cst.name,
        label: cst.label ?? '',
        type: cst.type,
      })),
    }));
  }
  if (entry.xml) {
    return parseConstantLibraryXml(entry.xml);
  }
  return [];
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Load the constant libraries declared by `paths`.
 *
 * @param paths The `.cl.xml` file paths imported by the rule file (the
 *              `path` attribute of each `<import-constant-library>`). Pass an
 *              empty array to short-circuit (no fetch).
 */
export function useConstantLibraries(paths: string[]): UseConstantLibrariesResult {
  const [libraries, setLibraries] = useState<ConstantCategoryGroup[]>([]);
  const [loading, setLoading] = useState<boolean>(paths.length > 0);
  const [error, setError] = useState<string | null>(null);

  // Stable key for the dependency array.
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
    formPost<ClXmlEntry[]>('/common/loadXml', { files: list.join(';') }, { silent: true })
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

export default useConstantLibraries;
