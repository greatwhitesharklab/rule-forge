/**
 * useActionLibraries — data hook for the {@link MethodPicker}.
 *
 * Mirrors {@link useVariableLibraries} / {@link useConstantLibraries} but for
 * `<action-library>` documents (`.al.xml`). Given the imported `.al.xml` paths
 * a rule file declares (the `path` attribute of each
 * `<import-action-library path="…"/>`, parsed into `Ruleset.actionLibraries`),
 * this hook fetches and parses those libraries into the `ActionLibrary[]`
 * shape the picker consumes (one array of spring-beans per imported library).
 *
 * Fetch strategy
 * --------------
 * Same as the sibling hooks: `/common/loadXml?files=p1;p2;p3` returns one
 * entry per file as raw-XML passthrough (`{xml: "<action-library>…"}`); we
 * DOMParse that client-side. A pre-deserialized object shape
 * (`{springBeans: [...]}`) is also tolerated should the backend ever grow a
 * deserializer (the legacy `src/action/action.ts` editor already POSTs to
 * `/xml` for a structured shape; we accept either form).
 *
 * The hook is OPTIONAL and `silent` (no global error dialog) — failures
 * degrade to an empty array so the picker just shows "未加载动作库" rather than
 * blocking the editor.
 *
 * Action library XML shape
 * ------------------------
 *   <action-library>
 *     <spring-bean id='customerService' name='客户服务'>
 *       <method name='查询客户' method-name='findCustomer'>
 *         <parameter name='id'   type='String'/>
 *         <parameter name='name' type='String'/>
 *       </method>
 *     </spring-bean>
 *   </action-library>
 *
 * Field mapping to the `<execute-method>` action the editor serializes
 * (see serializeAction / parseAction in model/):
 *
 *   spring-bean.id   → bean      (written into bean-name="…")
 *   spring-bean.name → beanLabel (written into bean-label="…")
 *   method.name      → methodLabel (written into method-label="…")
 *   method.method-name → methodName (written into method-name="…")
 *   method.parameter[] → picker-only signature display (the execute-method
 *                        action carries its OWN `<parameter>` list the BA
 *                        fills in; the library signature is just a hint)
 */
import { useEffect, useState } from 'react';
import { formPost } from '@/api/client';
import type {
  ActionLibrary,
  PickerActionMethod,
  PickerActionParameter,
  PickerSpringBean,
} from './MethodPicker';

// ---------------------------------------------------------------------------
// Types — kept local (the wire shape from /common/loadXml for a .al.xml).
// ---------------------------------------------------------------------------

/**
 * Backend response shape for ONE `.al.xml` file under `/common/loadXml`.
 * Either form is possible (see file header); we tolerate both.
 */
type AlXmlEntry =
  | { xml?: string; springBeans?: never }
  | { xml?: never; springBeans?: AlBean[] };

interface AlBean {
  id: string;
  name: string;
  methods?: AlMethod[];
}

interface AlMethod {
  name: string;
  methodName?: string;
  parameters?: AlParameter[];
}

interface AlParameter {
  name: string;
  type: string;
}

export interface UseActionLibrariesResult {
  /** One ActionLibrary (array of spring-beans) per imported library. */
  libraries: ActionLibrary[];
  /** True while the first fetch is in flight. */
  loading: boolean;
  /** Set if the fetch failed; libraries will be empty in that case. */
  error: string | null;
}

// ---------------------------------------------------------------------------
// XML parsing — only used when the backend returns raw XML (passthrough).
// ---------------------------------------------------------------------------

/**
 * Parse an `<action-library>` XML document into an {@link ActionLibrary} (a
 * list of spring-beans). Each bean carries its methods; each method carries
 * its parameter signature (name + Java type) for display only.
 *
 * Exported (not just module-local) so the unit tests can exercise the raw-XML
 * branch directly without going through the hook's network fetch.
 */
export function parseActionLibraryXml(xml: string): ActionLibrary {
  if (!xml) return [];
  const doc = new DOMParser().parseFromString(xml, 'application/xml');
  const root = doc.documentElement;
  if (!root || root.tagName !== 'action-library') return [];

  const beans: PickerSpringBean[] = [];
  const beanNodes = Array.from(root.getElementsByTagName('spring-bean'));
  for (const beanNode of beanNodes) {
    // Guard against nested spring-beans (shouldn't happen, but be defensive).
    if (beanNode.parentElement !== root) continue;
    const id = beanNode.getAttribute('id') ?? '';
    const name = beanNode.getAttribute('name') ?? '';
    const methods: PickerActionMethod[] = Array.from(
      beanNode.getElementsByTagName('method'),
    )
      .filter((m) => m.parentElement === beanNode)
      .map((m) => {
        const parameters: PickerActionParameter[] = Array.from(
          m.getElementsByTagName('parameter'),
        )
          .filter((p) => p.parentElement === m)
          .map((p) => ({
            name: p.getAttribute('name') ?? '',
            type: p.getAttribute('type') ?? '',
          }));
        return {
          name: m.getAttribute('name') ?? '',
          methodName: m.getAttribute('method-name') ?? '',
          parameters,
        };
      });
    beans.push({ id, name, methods });
  }
  return beans;
}

/** Coerce one backend entry (either shape) to an ActionLibrary. */
function entryToLibrary(entry: AlXmlEntry | undefined): ActionLibrary {
  if (!entry) return [];
  if (entry.springBeans && Array.isArray(entry.springBeans)) {
    return entry.springBeans.map((b) => ({
      id: b.id,
      name: b.name,
      methods: (b.methods ?? []).map((m) => ({
        name: m.name,
        methodName: m.methodName ?? '',
        parameters: (m.parameters ?? []).map((p) => ({
          name: p.name,
          type: p.type,
        })),
      })),
    }));
  }
  if (entry.xml) {
    return parseActionLibraryXml(entry.xml);
  }
  return [];
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Load the action libraries declared by `paths`.
 *
 * @param paths The `.al.xml` file paths imported by the rule file (the
 *              `path` attribute of each `<import-action-library>`). Pass an
 *              empty array to short-circuit (no fetch).
 */
export function useActionLibraries(paths: string[]): UseActionLibrariesResult {
  const [libraries, setLibraries] = useState<ActionLibrary[]>([]);
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
    formPost<AlXmlEntry[]>('/common/loadXml', { files: list.join(';') }, { silent: true })
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

export default useActionLibraries;
