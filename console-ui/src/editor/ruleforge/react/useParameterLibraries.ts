/**
 * useParameterLibraries — data hook for the {@link ParameterPicker}.
 *
 * Mirrors {@link useVariableLibraries} but for `<parameter-library>` documents
 * (`.pl.xml`). Given the imported `.pl.xml` paths a rule file declares (the
 * `libraries: LibraryImport[]` filtered to `type === 'Parameter'`, i.e. the
 * `path` attribute of each `<import-parameter-library path="…"/>`), this hook
 * fetches and parses those libraries into the `ParameterLibrary[]` shape the
 * picker consumes (one array of parameters per imported library).
 *
 * Fetch strategy
 * --------------
 * Same as the sibling hooks: `/common/loadXml?files=p1;p2;p3` returns one
 * entry per file as raw-XML passthrough (`{xml: "<parameter-library>…"}); we
 * DOMParse client-side. A pre-deserialized object shape (`{parameters: [...]}`)
 * is also tolerated.
 *
 * The hook is OPTIONAL and `silent` — failures degrade to an empty array so
 * the picker shows "未加载参数库".
 *
 * Parameter library XML shape
 * ---------------------------
 * Unlike variable / constant libraries, the parameter library has NO category
 * level — it is a flat list of `<parameter>` elements directly under the root:
 *
 *   <parameter-library>
 *     <parameter name="amount" label="金额" type="BigDecimal" act="InOut"/>
 *     <parameter name="term"   label="期限" type="Integer"     act="InOut"/>
 *   </parameter-library>
 *
 * (see src/parameter/action.ts saveData — the writer always emits `act="InOut"`
 * and never wraps parameters in `<category>`.)
 */
import { useEffect, useState } from 'react';
import { formPost } from '@/api/client';
import type { PickerParameterItem, ParameterLibrary } from './ParameterPicker';

// ---------------------------------------------------------------------------
// Types — kept local (the wire shape from /common/loadXml for a .pl.xml).
// ---------------------------------------------------------------------------

/**
 * Backend response shape for ONE `.pl.xml` file under `/common/loadXml`.
 * Either form is possible; we tolerate both.
 */
type PlXmlEntry =
  | { xml?: string; parameters?: never }
  | { xml?: never; parameters?: PlParam[] };

interface PlParam {
  name: string;
  label?: string;
  type?: string;
  act?: string;
}

export interface UseParameterLibrariesResult {
  /** One ParameterLibrary (array of parameters) per imported library. */
  libraries: ParameterLibrary[];
  /** True while the first fetch is in flight. */
  loading: boolean;
  /** Set if the fetch failed; libraries will be empty in that case. */
  error: string | null;
}

// ---------------------------------------------------------------------------
// XML parsing — only used when the backend returns raw XML (passthrough).
// ---------------------------------------------------------------------------

/**
 * Parse a `<parameter-library>` XML document into a ParameterLibrary (a flat
 * list of parameters — there is no category level).
 *
 * Exported (not just module-local) so the unit tests can exercise the raw-XML
 * branch directly without going through the hook's network fetch.
 */
export function parseParameterLibraryXml(xml: string): ParameterLibrary {
  if (!xml) return [];
  const doc = new DOMParser().parseFromString(xml, 'application/xml');
  const root = doc.documentElement;
  if (!root || root.tagName !== 'parameter-library') return [];

  return Array.from(root.getElementsByTagName('parameter')).map((p) => ({
    name: p.getAttribute('name') ?? '',
    label: p.getAttribute('label') ?? '',
    type: p.getAttribute('type') ?? undefined,
    act: p.getAttribute('act') ?? undefined,
  }));
}

/** Coerce one backend entry (either shape) to a ParameterLibrary. */
function entryToLibrary(entry: PlXmlEntry | undefined): ParameterLibrary {
  if (!entry) return [];
  if (entry.parameters && Array.isArray(entry.parameters)) {
    return entry.parameters.map((p) => ({
      name: p.name,
      label: p.label ?? '',
      type: p.type,
      act: p.act,
    }));
  }
  if (entry.xml) {
    return parseParameterLibraryXml(entry.xml);
  }
  return [];
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Load the parameter libraries declared by `paths`.
 *
 * @param paths The `.pl.xml` file paths imported by the rule file (the
 *              `path` attribute of each `<import-parameter-library>`). Pass an
 *              empty array to short-circuit (no fetch).
 */
export function useParameterLibraries(paths: string[]): UseParameterLibrariesResult {
  const [libraries, setLibraries] = useState<ParameterLibrary[]>([]);
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
    formPost<PlXmlEntry[]>('/common/loadXml', { files: list.join(';') }, { silent: true })
      .then((data) => {
        if (cancelled) return;
        const libs = (Array.isArray(data) ? data : []).map(entryToLibrary);
        setLibraries(libs);
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

// Re-export the item type for callers that want to type a single parameter.
export type { PickerParameterItem };

export default useParameterLibraries;
