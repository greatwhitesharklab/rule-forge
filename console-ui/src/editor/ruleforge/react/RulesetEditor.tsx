/**
 * RulesetEditor — top-level React ruleset editor.
 *
 * Responsibilities:
 *   1. Load: fetch the ruleset XML → parseRuleset → state.
 *   2. Hold the Ruleset state at the top (single-direction data flow).
 *   3. Render: toolbar (save + add rule) + remark input + rule list.
 *   4. Save: serializeRuleset → POST /common/saveFile (URL-encoded content).
 *
 * Data flow:
 *   loadXml → parseRuleset(state) ─┐
 *                                  ├→ React state (the only owner)
 *   rule edits via onChange ───────┘
 *   save button → serializeRuleset(state) → formPost(/common/saveFile)
 *
 * ── Loading note ─────────────────────────────────────────────────────────
 * /common/loadXml deserializes the file server-side for most types
 * (decision-table / scorecard / decision-tree / crosstab …). Rulesets have
 * NO registered deserializer, so the backend now (ruleset passthrough branch
 * in CommonController.loadXml) returns the raw XML text under
 * `editorData.xml` for `.rs.xml` files instead of a deserialized object.
 *
 * This editor reads `editorData.xml` (falling back to `editorData.content`)
 * and feeds it to parseRuleset. An empty string means a fresh file.
 */
import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Input, Space, Spin, Typography, message } from 'antd';
import { PlusOutlined, SaveOutlined } from '@ant-design/icons';
import type { Rule, Ruleset } from '../model/types';
import { parseRuleset } from '../model/parse';
import { serializeRuleset } from '../model/serialize';
import { formPost, save } from '@/api/client';
import { RuleEditor } from './RuleEditor';

const { Text } = Typography;

export interface RulesetEditorProps {
  /** The ruleset file path (e.g. "/project/rules/foo.xml"). */
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

/** Build an empty ruleset (used when the server has nothing yet). */
function emptyRuleset(): Ruleset {
  return {
    parameterLibraries: [],
    variableLibraries: [],
    constantLibraries: [],
    actionLibraries: [],
    remark: '',
    rules: [],
  };
}

/** Build an empty rule with a top-level AND junction. */
function emptyRule(name: string): Rule {
  return {
    name,
    properties: [],
    remark: '',
    if: { kind: 'junction', type: 'and', children: [] },
    then: [],
    else: [],
  };
}

/**
 * Default server loader. Hits /common/loadXml and reads the raw XML from
 * `editorData.xml` (backend ruleset passthrough branch returns the verbatim
 * file text there for `.rs.xml` files). Returns an empty string when no XML
 * is available, which the editor interprets as "fresh file".
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

export function RulesetEditor({ file, onLoad = loadFromServer, onSave = saveToServer }: RulesetEditorProps) {
  const [state, setState] = useState<Ruleset>(emptyRuleset());
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
          setState(parseRuleset(xml));
        } else {
          // No XML available yet — start fresh (TODO note in file header).
          setState(emptyRuleset());
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

  // ---- rule-list mutations ----
  const addRule = useCallback(() => {
    const name = window.prompt('规则名称', '规则' + (state.rules.length + 1));
    if (name === null) return; // cancelled
    setState((prev) => ({ ...prev, rules: prev.rules.concat([emptyRule(name)]) }));
  }, [state.rules.length]);

  const updateRule = useCallback((i: number, next: Rule) => {
    setState((prev) => {
      const rules = prev.rules.slice();
      rules[i] = next;
      return { ...prev, rules };
    });
  }, []);

  const removeRule = useCallback((i: number) => {
    setState((prev) => {
      const rules = prev.rules.slice();
      rules.splice(i, 1);
      return { ...prev, rules };
    });
  }, []);

  // ---- save ----
  const handleSave = useCallback(() => {
    setSaving(true);
    let xml: string;
    try {
      xml = serializeRuleset(state);
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
        <Spin description="加载规则集…" />
      </div>
    );
  }

  return (
    <div style={{ padding: 16, maxWidth: 1200, margin: '0 auto' }}>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
        <Text strong>规则集: {decodeURIComponent(file)}</Text>
        <Space>
          <Button icon={<PlusOutlined />} onClick={addRule}>
            添加规则
          </Button>
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
          title="加载规则集失败,以空白规则集启动"
          description={loadError}
          closable={{ onClose: () => setLoadError(null) }}
        />
      )}

      <div style={{ marginBottom: 12 }}>
        <Input.TextArea
          rows={2}
          placeholder="规则集备注 (remark)"
          value={state.remark}
          onChange={(e) => setState((prev) => ({ ...prev, remark: e.target.value }))}
        />
      </div>

      {state.rules.length === 0 && (
        <Alert
          type="info"
          showIcon
          title="还没有规则"
          description='点击右上角"添加规则"开始。'
        />
      )}

      {state.rules.map((rule, i) => (
        <RuleEditor
          key={i}
          index={i}
          value={rule}
          onChange={(next) => updateRule(i, next)}
          onDelete={() => removeRule(i)}
        />
      ))}
    </div>
  );
}

export default RulesetEditor;
