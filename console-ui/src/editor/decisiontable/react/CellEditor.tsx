/**
 * CellEditor — controlled editor for one decision-table `<cell>` content.
 *
 * Renders the right widget for the parent column's type:
 *   - Criteria      → op dropdown (OPERATOR_OPTIONS) + ValueEditor for the right value
 *   - Assignment    → ActionEditor (var-assign)
 *   - ConsolePrint  → ActionEditor (console-print)
 *   - ExecuteMethod → ActionEditor (execute-method)
 *
 * For a Criteria cell we surface a FLAT single condition (the column already
 * binds the left variable, so a condition is just op + right value). The model
 * keeps the `<joint>` wrapper for XML fidelity; this editor writes the first
 * condition of the joint. Nested joints / multi-condition cells are a TODO
 * (handsontable supported them via merge; the React table does not yet).
 *
 * An empty cell renders a placeholder; clicking it seeds a default condition
 * or action matching the column type.
 *
 * ValueEditor / ActionEditor / OPERATOR_OPTIONS are reused from the ruleforge
 * React editor (../../ruleforge/react) — the cell `<value>` and action wire
 * formats are identical to the ruleset ones.
 */
import { Select } from 'antd';
import { OPERATOR_OPTIONS, opHasNoInput } from '../../ruleforge/react/constants';
import { ValueEditor } from '../../ruleforge/react/ValueEditor';
import { ActionEditor } from '../../ruleforge/react/ActionEditor';
import type { Action } from '../../ruleforge/model/types';
import type { CellContent, CellJoint, ColumnType } from '../model/types';

export interface CellEditorProps {
  /** The column type this cell lives under (drives the widget variant). */
  columnType: ColumnType;
  /** The current cell content (controlled). */
  value: CellContent;
  /** Called with the new CellContent on every edit. */
  onChange: (next: CellContent) => void;
}

/** Seed a default content matching the column type when the user starts editing an empty cell. */
function seedForColumnType(type: ColumnType): CellContent {
  switch (type) {
    case 'Criteria':
      return { joint: { type: 'and', conditions: [{ op: 'Equals', right: { type: 'Input', content: '' } }] } };
    case 'Assignment':
      return {
        action: {
          kind: 'var-assign', var: '', varLabel: '', varCategory: '',
          valueType: 'variable', value: { type: 'Input', content: '' },
        },
      };
    case 'ConsolePrint':
      return { action: { kind: 'console-print', value: { type: 'Input', content: '' } } };
    case 'ExecuteMethod':
      return {
        action: { kind: 'execute-method', bean: '', beanLabel: '', methodName: '', methodLabel: '', parameters: [] },
      };
  }
}

/** Extract the single condition of a Criteria joint (or undefined when empty/absent). */
function firstCondition(joint: CellJoint | undefined) {
  return joint?.conditions[0];
}

export function CellEditor({ columnType, value, onChange }: CellEditorProps) {
  // Empty cell: show a clickable placeholder that seeds content on first edit.
  if ('empty' in value) {
    return (
      <div
        style={{ color: '#bbb', cursor: 'pointer', padding: '2px 4px' }}
        onClick={() => onChange(seedForColumnType(columnType))}
        title="点击编辑"
      >
        无
      </div>
    );
  }

  // Criteria cell: op + optional right value.
  if ('joint' in value) {
    const cond = firstCondition(value.joint);
    const op = cond?.op ?? 'Equals';
    const right = cond?.right ?? { type: 'Input' as const, content: '' };
    const noRight = opHasNoInput(op);

    const patch = (next: { op?: string; right?: typeof right }): void => {
      const conditions = value.joint.conditions.slice();
      conditions[0] = {
        op: next.op ?? op,
        ...(noRight || next.right === undefined ? {} : { right: next.right ?? right }),
      };
      // If the new op takes no input, drop the right value (mirror serialize).
      const newOp = next.op ?? op;
      if (opHasNoInput(newOp)) {
        delete (conditions[0] as { right?: typeof right }).right;
      }
      onChange({ joint: { type: value.joint.type, conditions } });
    };

    return (
      <div>
        <div style={{ marginBottom: 4 }}>
          <Select
            size="small"
            style={{ width: '100%' }}
            value={op}
            onChange={(nextOp) => patch({ op: nextOp })}
            options={OPERATOR_OPTIONS}
          />
        </div>
        {!opHasNoInput(op) && (
          <ValueEditor value={right} compact onChange={(v) => patch({ right: v })} />
        )}
      </div>
    );
  }

  // Action cell (Assignment / ConsolePrint / ExecuteMethod).
  if ('action' in value) {
    const handle = (next: Action): void => onChange({ action: next });
    return <ActionEditor value={value.action} onChange={handle} />;
  }

  return null;
}

export default CellEditor;
