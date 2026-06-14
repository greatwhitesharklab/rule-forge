/**
 * AtomEditor — controlled editor for a leaf `atom` condition node.
 *
 * Layout:  <LeftValueEditor>  <op dropdown>  <ValueEditor>
 *
 * The right-hand ValueEditor is hidden when op is Null / NotNull (matches
 * serialize.ts: those ops suppress the `<value>` child entirely).
 *
 * Used in two contexts:
 *   - inline inside a ConditionFlow node body
 *   - inside a Modal opened on double-click of a node
 *
 * Both contexts pass the same `value` / `onChange` props.
 */
import { Select } from 'antd';
import type { ConditionNode } from '../model/types';
import { OPERATOR_OPTIONS, opHasNoInput } from './constants';
import { LeftValueEditor } from './LeftValueEditor';
import { ValueEditor } from './ValueEditor';

/** The atom variant of ConditionNode (a compile-time narrowing for props). */
type AtomNode = Extract<ConditionNode, { kind: 'atom' }>;

export interface AtomEditorProps {
  /** The current atom node (controlled; must be kind 'atom'). */
  value: AtomNode;
  /** Called with a new atom node on every field edit. */
  onChange: (next: AtomNode) => void;
  /** Optional compact mode passed to sub-editors. */
  compact?: boolean;
}

/**
 * Build a fresh atom when the user picks a new operator. If the new op takes
 * no input (Null / NotNull), drop the right value; otherwise ensure a
 * default Input value exists so the ValueEditor has something to render.
 */
function withOp(node: AtomNode, op: string): AtomNode {
  if (opHasNoInput(op)) {
    const { right: _right, ...rest } = node;
    return { ...rest, op };
  }
  return { ...node, op, right: node.right ?? { type: 'Input', content: '' } };
}

export function AtomEditor({ value, onChange, compact }: AtomEditorProps) {
  const noRight = opHasNoInput(value.op);

  return (
    <div>
      <div style={{ marginBottom: compact ? 4 : 8 }}>
        <LeftValueEditor
          value={value.left}
          compact={compact}
          onChange={(left) => onChange({ ...value, left })}
        />
      </div>

      <div style={{ marginBottom: compact ? 4 : 8 }}>
        <Select
          size="small"
          style={{ width: '100%' }}
          value={value.op}
          onChange={(op) => onChange(withOp(value, op))}
          options={OPERATOR_OPTIONS}
        />
      </div>

      {!noRight && (
        <ValueEditor
          value={value.right ?? { type: 'Input', content: '' }}
          compact={compact}
          onChange={(right) => onChange({ ...value, right })}
        />
      )}
    </div>
  );
}

export default AtomEditor;
