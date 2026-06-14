/**
 * RuleEditor — controlled editor for a single Rule.
 *
 * Assembles: name input + remark + properties list (RulePropertyEditor) +
 * the condition tree (ConditionFlow) + then actions list (ActionEditor × N)
 * + else actions list.
 *
 * Single-direction data flow: the parent owns the Rule; every field edit
 * emits a new Rule via onChange.
 */
import { Button, Input, Space, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { Action, Rule } from '../model/types';
import { ActionEditor } from './ActionEditor';
import { ConditionFlow } from './ConditionFlow';
import { RulePropertyEditor } from './RulePropertyEditor';

const { Text } = Typography;

export interface RuleEditorProps {
  /** The current rule (controlled). */
  value: Rule;
  /** Called with a new Rule on every field edit. */
  onChange: (next: Rule) => void;
  /** Optional — called when the user clicks the rule's delete affordance. */
  onDelete?: () => void;
  /** Optional rule index in the ruleset (for the header label). */
  index?: number;
}

export function RuleEditor({ value, onChange, onDelete, index }: RuleEditorProps) {
  const patch = (p: Partial<Rule>) => onChange({ ...value, ...p });

  // ----- actions list helpers -----
  const addAction = (branch: 'then' | 'else') => {
    const next: Action = { kind: 'console-print', value: { type: 'Input', content: '' } };
    patch({ [branch]: value[branch].concat(next) } as Partial<Rule>);
  };
  const updateAction = (branch: 'then' | 'else', i: number, next: Action) => {
    const list = value[branch].slice();
    list[i] = next;
    patch({ [branch]: list } as Partial<Rule>);
  };
  const removeAction = (branch: 'then' | 'else', i: number) => {
    const list = value[branch].slice();
    list.splice(i, 1);
    patch({ [branch]: list } as Partial<Rule>);
  };

  return (
    <div style={{ border: '1px solid #d9d9d9', borderRadius: 6, padding: 12, marginBottom: 12, background: '#fff' }}>
      <Space style={{ marginBottom: 8, width: '100%', justifyContent: 'space-between' }}>
        <Space>
          <Text strong>规则{index !== undefined ? ' #' + (index + 1) : ''}</Text>
          <Input
            size="small"
            style={{ width: 280 }}
            placeholder="规则名 (rule name)"
            value={value.name}
            onChange={(e) => patch({ name: e.target.value })}
          />
        </Space>
        {onDelete && (
          <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={onDelete}>
            删除规则
          </Button>
        )}
      </Space>

      <div style={{ marginBottom: 8 }}>
        <Input.TextArea
          size="small"
          rows={1}
          placeholder="备注 (remark)"
          value={value.remark}
          onChange={(e) => patch({ remark: e.target.value })}
        />
      </div>

      <div style={{ marginBottom: 8 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          属性
        </Text>
        <div style={{ marginTop: 4 }}>
          <RulePropertyEditor value={value.properties} onChange={(properties) => patch({ properties })} />
        </div>
      </div>

      <div style={{ marginBottom: 8 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          条件 (if)
        </Text>
        <div style={{ marginTop: 4 }}>
          <ConditionFlow value={value.if} onChange={(n) => patch({ if: n })} />
        </div>
      </div>

      <ActionList
        title="满足时执行 (then)"
        actions={value.then}
        onAdd={() => addAction('then')}
        onUpdate={(i, next) => updateAction('then', i, next)}
        onRemove={(i) => removeAction('then', i)}
      />

      <ActionList
        title="否则执行 (else)"
        actions={value.else}
        onAdd={() => addAction('else')}
        onUpdate={(i, next) => updateAction('else', i, next)}
        onRemove={(i) => removeAction('else', i)}
      />
    </div>
  );
}

/** A labeled list of ActionEditor rows + an "add action" button. */
function ActionList({
  title,
  actions,
  onAdd,
  onUpdate,
  onRemove,
}: {
  title: string;
  actions: Action[];
  onAdd: () => void;
  onUpdate: (i: number, next: Action) => void;
  onRemove: (i: number) => void;
}) {
  return (
    <div style={{ marginBottom: 8 }}>
      <Text type="secondary" style={{ fontSize: 12 }}>
        {title}
      </Text>
      <div style={{ marginTop: 4 }}>
        {actions.map((a, i) => (
          <ActionEditor
            key={i}
            value={a}
            onChange={(next) => onUpdate(i, next)}
            onDelete={() => onRemove(i)}
          />
        ))}
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={onAdd}>
          添加动作
        </Button>
      </div>
    </div>
  );
}

export default RuleEditor;
