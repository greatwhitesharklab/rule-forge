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
import { SaveOutlined } from '@ant-design/icons';
import type { DecisionTree } from '../model/types';
import { parseDecisionTree } from '../model/parse';
import { serializeDecisionTree } from '../model/serialize';
import { formPost, save } from '@/api/client';
import { DecisionTreeFlow } from './DecisionTreeEditor';

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

export function DecisionTreeApp({ file, onLoad = loadFromServer, onSave = saveToServer }: DecisionTreeAppProps) {
  const [state, setState] = useState<DecisionTree>(emptyDecisionTree());
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

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
        <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
          保存
        </Button>
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

      <DecisionTreeFlow value={state.root} onChange={updateRoot} height={560} />
    </div>
  );
}

export default DecisionTreeApp;
