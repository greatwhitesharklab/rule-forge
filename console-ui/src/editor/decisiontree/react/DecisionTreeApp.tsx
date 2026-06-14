/**
 * DecisionTreeApp — top-level React decision-tree editor.
 *
 * Responsibilities:
 *   1. Load: fetch the decision-tree XML → parseDecisionTree → state.
 *   2. Hold the DecisionTree state at the top (single-direction data flow).
 *   3. Render: toolbar (save) + remark input + properties + the react-flow
 *      canvas (DecisionTreeFlow) bound to state.root.
 *   4. Save: serializeDecisionTree → POST /common/saveFile (URL-encoded content).
 *
 * Data flow:
 *   loadXml → parseDecisionTree(state) ─┐
 *                                       ├→ React state (the only owner)
 *   tree edits via onRootChange ────────┘
 *   save button → serializeDecisionTree(state) → formPost(/common/saveFile)
 *
 * ── Loading note ─────────────────────────────────────────────────────────
 * /common/loadXml deserializes the file server-side for decision-tree (a
 * DecisionTreeDeserializer IS registered). The backend passthrough branch
 * returns the raw XML text under `editorData.xml` for the React rewrite to
 * parse client-side, matching how the ruleset / decision-table / crosstab
 * editors all consume the same field.
 */
import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Input, Space, Spin, Typography, message } from 'antd';
import { ExperimentOutlined, SaveOutlined } from '@ant-design/icons';
import type { DecisionTree } from '../model/types';
import { parseDecisionTree } from '../model/parse';
import { serializeDecisionTree } from '../model/serialize';
import { formPost, save } from '@/api/client';
import { useVariableLibraries, useConstantLibraries, useParameterLibraries } from '../../ruleforge/react';
import { DecisionTreeFlow } from './DecisionTreeEditor';
// Reused jquery-era dialogs (event-driven class components). Mounted once in the
// JSX tree below; opened by emitting OPEN_* events. ConfigLibraryDialog reads
// the project-global `variableLibraries` / `constantLibraries` / etc. arrays and
// calls the `refreshXxxLibraries()` globals on add/delete, so we bridge those
// globals to React state in a mount effect (see useLibrariesGlobalBridge).
import * as componentEvent from '@/components/componentEvent.js';
import { OPEN_CONFIG_LIBRARY_DIALOG } from '@/components/dialog/component/ConfigLibraryDialog';
import ConfigLibraryDialog from '@/components/dialog/component/ConfigLibraryDialog';
import QuickTestDialog from '@/components/dialog/component/QuickTestDialog';
import KnowledgeTreeDialog from '@/components/dialog/component/KnowledgeTreeDialog';
import { buildProjectNameFromFile } from '@/Utils';

// Lib-type discriminator used by the shared ConfigLibraryDialog (one of the
// four kinds of `<import-*-library>` it manages). Matches the dialog's
// `CONFIGS` record keys exactly.
type LibType = 'variable' | 'constant' | 'action' | 'parameter';

const { Text } = Typography;

export interface DecisionTreeAppProps {
  /** The decision-tree file path (e.g. "/project/rules/foo.dt.xml"). */
  file: string;
  /**
   * Optional override for the load function (defaults to loadFromServer).
   * Tests inject a stub here instead of mocking fetch.
   */
  onLoad?: (file: string) => Promise<string>;
  /**
   * Optional override for the save function. Tests inject a stub here.
   * Defaults to POST /common/saveFile.
   */
  onSave?: (file: string, xml: string) => Promise<void>;
}

/** Build an empty decision-tree (used when the server has nothing yet). */
function emptyDecisionTree(): DecisionTree {
  return {
    parameterLibraries: [],
    variableLibraries: [],
    constantLibraries: [],
    actionLibraries: [],
    remark: '',
    properties: [],
    root: { kind: 'variable', left: { type: 'variable' }, children: [] },
  };
}

/**
 * Default server loader. Hits /common/loadXml and reads the raw XML from
 * `editorData.xml` (the backend passthrough branch returns the verbatim file
 * text there for `.dt.xml` files). Returns an empty string when no XML is
 * available, which the editor interprets as "fresh file".
 */
async function loadFromServer(file: string): Promise<string> {
  type EditorDataLike = { xml?: string; content?: string };
  const data = await formPost<EditorDataLike[]>('/common/loadXml', { files: file });
  const editorData = Array.isArray(data) ? data[0] : undefined;
  if (!editorData) return '';
  return editorData.xml ?? editorData.content ?? '';
}

/**
 * Default server saver. URL-encodes the XML (the jquery editor did the same)
 * and POSTs it to /common/saveFile with a `newVersion` flag.
 */
async function saveToServer(file: string, xml: string): Promise<void> {
  const url = (window._server ?? '') + '/common/saveFile';
  await save(url, {
    content: encodeURIComponent(xml),
    file: file,
    newVersion: 'false',
  });
}

/**
 * Bridge the React decision-tree state to the jquery-era globals that the
 * shared ConfigLibraryDialog reads (`variableLibraries` / `constantLibraries` /
 * `actionLibraries` / `parameterLibraries`) and refreshes through
 * (`refreshVariableLibraries()` etc.).
 *
 * The React editor never goes through the legacy `loadEditorData` /
 * `loadLibraries` path that populates these globals, so on mount we (a) install
 * the four array globals + four `refresh*` globals on `window` if they are
 * missing, (b) seed them from the current `state.*Libraries` arrays, and (c)
 * re-point each `refresh*` global to a function that reads the array back into
 * React state. That makes the reused dialog's add/delete flow round-trip
 * through React state without rewriting the dialog. `syncOut` is idempotent.
 *
 * The jquery bundle (when loaded alongside the SPA) defines the same globals as
 * `var variableLibraries = []` — re-assigning them here overrides that, which
 * is fine because the React editor owns the library import list for its file.
 */
function installLibrariesBridge(
  state: DecisionTree,
  setState: React.Dispatch<React.SetStateAction<DecisionTree>>,
): void {
  const w = window as unknown as Record<string, unknown>;
  // Seed the array globals from React state on every call (sync-out is cheap
  // and keeps the dialog's read-side consistent after external edits).
  w.variableLibraries = state.variableLibraries.slice();
  w.constantLibraries = state.constantLibraries.slice();
  w.actionLibraries = state.actionLibraries.slice();
  w.parameterLibraries = state.parameterLibraries.slice();

  // The refresh* globals push the (possibly mutated) array back into React
  // state. Idempotent — only installed once per mount.
  if (typeof w.refreshVariableLibraries !== 'function') {
    const pushBack = (key: 'variableLibraries' | 'constantLibraries' | 'actionLibraries' | 'parameterLibraries') => {
      return () => {
        const arr = (w[key] as string[] | undefined) ?? [];
        setState((prev) => ({ ...prev, [key]: arr.slice() }));
        window._setDirty?.();
      };
    };
    w.refreshVariableLibraries = pushBack('variableLibraries');
    w.refreshConstantLibraries = pushBack('constantLibraries');
    w.refreshActionLibraries = pushBack('actionLibraries');
    w.refreshParameterLibraries = pushBack('parameterLibraries');
  }
}

export function DecisionTreeApp({ file, onLoad = loadFromServer, onSave = saveToServer }: DecisionTreeAppProps) {
  const [state, setState] = useState<DecisionTree>(emptyDecisionTree());
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Load the project's imported variable libraries once (paths are stable per
  // decision-tree — `<import-variable-library path="…"/>` is parsed into
  // state.variableLibraries as a plain string[]). Passed down to the node-edit
  // modal's LeftValueEditor / ValueEditor so the shared VariablePicker Cascader
  // replaces free-text variable binding.
  const { libraries: variableLibraries } = useVariableLibraries(state.variableLibraries);

  // Same pattern for constant / parameter libraries — fed to the right-hand
  // ValueEditor so `<value type="Constant">` / `<value type="Parameter">` can
  // be picked from a Cascader instead of typed by hand.
  const { libraries: constantLibraries } = useConstantLibraries(state.constantLibraries);
  const { libraries: parameterLibraries } = useParameterLibraries(state.parameterLibraries);

  // ---- window._project (consumed by the reused dialogs' add/test flows) ----
  // The shared ConfigLibraryDialog / QuickTestDialog / KnowledgeTreeDialog read
  // `window._project` (set to the project derived from the file path), so set
  // it once on mount and when the file changes. Mirrors flow-bpmn's EditorRoute.
  useEffect(() => {
    window._project = buildProjectNameFromFile(file);
  }, [file]);

  // ---- bridge React state ↔ jquery library globals ----
  // Keep the four `*Libraries` array globals + the four `refresh*Libraries`
  // function globals in sync with React state so the reused ConfigLibraryDialog
  // (which reads/mutates them) round-trips through React state. Re-runs on every
  // state change to keep the dialog's view fresh; the refresh* install is
  // idempotent (guarded on first install).
  useEffect(() => {
    installLibrariesBridge(state, setState);
  }, [state, setState]);

  // ---- open reused dialogs ----
  const openLibraryDialog = useCallback((type: LibType) => {
    componentEvent.eventEmitter.emit(OPEN_CONFIG_LIBRARY_DIALOG, type);
  }, []);

  const openQuickTest = useCallback(() => {
    componentEvent.eventEmitter.emit(componentEvent.OPEN_QUICK_TEST_DIALOG, {
      project: window._project,
      file: decodeURIComponent(file),
      type: 'decisiontree',
    });
  }, [file]);

  // ---- load on mount (and when file changes) ----
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    onLoad(file)
      .then((xml) => {
        if (cancelled) return;
        if (xml && xml.trim().length > 0) {
          setState(parseDecisionTree(xml));
        } else {
          setState(emptyDecisionTree());
        }
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setLoadError(err instanceof Error ? err.message : String(err));
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [file, onLoad]);

  // ---- tree root mutation ----
  const updateRoot = useCallback((root: DecisionTree['root']) => {
    setState((prev) => ({ ...prev, root }));
  }, []);

  // ---- remark + properties ----
  const updateRemark = useCallback((remark: string) => {
    setState((prev) => ({ ...prev, remark }));
  }, []);

  // ---- save ----
  const handleSave = useCallback(() => {
    setSaving(true);
    let xml: string;
    try {
      xml = serializeDecisionTree(state);
    } catch (err) {
      setSaving(false);
      message.error('序列化失败: ' + (err instanceof Error ? err.message : String(err)));
      return;
    }
    onSave(file, xml)
      .then(() => {
        setSaving(false);
        message.success('保存成功');
      })
      .catch((err: unknown) => {
        setSaving(false);
        message.error('保存失败: ' + (err instanceof Error ? err.message : String(err)));
      });
  }, [state, file, onSave]);

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin description="加载决策树…" />
      </div>
    );
  }

  return (
    <div style={{ padding: 16, maxWidth: 1400, margin: '0 auto' }}>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
        <Text strong>决策树: {decodeURIComponent(file)}</Text>
        <Space wrap>
          <Button icon={<ExperimentOutlined />} onClick={openQuickTest}>快速测试</Button>
          <Button onClick={() => openLibraryDialog('variable')}>变量库</Button>
          <Button onClick={() => openLibraryDialog('constant')}>常量库</Button>
          <Button onClick={() => openLibraryDialog('action')}>动作库</Button>
          <Button onClick={() => openLibraryDialog('parameter')}>参数库</Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
            保存
          </Button>
        </Space>
      </Space>

      {loadError && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          title="加载决策树失败,以空白决策树启动"
          description={loadError}
          closable={{ onClose: () => setLoadError(null) }}
        />
      )}

      <div style={{ marginBottom: 12 }}>
        <Input.TextArea
          rows={2}
          placeholder="决策树备注 (remark)"
          value={state.remark}
          onChange={(e) => updateRemark(e.target.value)}
        />
      </div>

      <DecisionTreeFlow
        value={state.root}
        onChange={updateRoot}
        height={560}
        libraries={variableLibraries}
        constantLibraries={constantLibraries}
        parameterLibraries={parameterLibraries}
      />

      {/*
        Reused jquery-era dialogs (event-driven class components). Mounted once
        here so their componentDidMount event listeners are live; opened by
        emitting OPEN_* events from the toolbar buttons above.
          - ConfigLibraryDialog: 变量库/常量库/动作库/参数库 import-path manager.
            Its "添加" button opens KnowledgeTreeDialog (mounted below) to pick a
            library file, so KnowledgeTreeDialog must be mounted in the same tree.
          - QuickTestDialog: 快速测试 of the current file (loads versions, runs
            the test, renders input/output tables).
      */}
      <ConfigLibraryDialog />
      <KnowledgeTreeDialog />
      <QuickTestDialog />
    </div>
  );
}

export default DecisionTreeApp;
