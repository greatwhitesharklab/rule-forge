/**
 * DecisionTreeEditor — the react-flow canvas that renders a TreeNode tree.
 *
 * The TreeNode tree (rooted at a variable node) is the single source of
 * truth. react-flow's nodes/edges are a *derived view*: on every render we
 * run toFlow(value) to rebuild them. All edits (add condition / add action /
 * add variable / delete / edit) go through props.onChange with a new TreeNode
 * root built via the pure helpers in flowLayout.ts.
 *
 * Custom node body:
 *   variable  → the left-hand value summary + add-condition button
 *   condition → op + right-value summary + add condition/variable/action buttons
 *   action    → action summaries (leaf; no add-child, only edit + delete)
 *
 * Double-clicking a node opens a Modal with the matching inline editor:
 *   variable  → LeftValueEditor (the <left>)
 *   condition → op Select + ValueEditor (the right-hand <value>)
 *   action    → ActionEditor list (one row per action, add/remove)
 *
 * react-flow's edges are non-interactive in this pass (no manual re-parent);
 * structural edits happen through the node-body buttons.
 *
 * Reuse: the inline editors (LeftValueEditor / ValueEditor / ActionEditor)
 * and the constants (OPERATOR_OPTIONS) are imported from the ruleforge editor
 * because the decision-tree schema uses byte-for-byte the same <left> /
 * <value> / action widgets as the ruleset schema.
 */
import { useCallback, useMemo, useState } from 'react';
import { Background, Controls, ReactFlow, ReactFlowProvider } from '@xyflow/react';
import { Button, Modal, Select, Space, Tag } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ApartmentOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import '@xyflow/react/dist/style.css';

import type { Action, LeftValue, TreeNode } from '../model/types';
import { OPERATOR_OPTIONS, opLabel, opHasNoInput } from '../../ruleforge/react/constants';
import { LeftValueEditor } from '../../ruleforge/react/LeftValueEditor';
import { ValueEditor } from '../../ruleforge/react/ValueEditor';
import { ActionEditor } from '../../ruleforge/react/ActionEditor';
import {
  FlowNode,
  allowedChildKinds,
  appendChild,
  findNode,
  makeActionNode,
  makeConditionNode,
  makeVariableNode,
  removeNode,
  replaceNode,
  toFlow,
} from './flowLayout';

export interface DecisionTreeFlowProps {
  /** The root tree node (must be a variable node). Controlled. */
  value: TreeNode;
  /** Called with a new root whenever the user edits the tree. */
  onChange: (next: TreeNode) => void;
  /** Canvas height in px (default 480). */
  height?: number;
}

/** The react-flow nodeTypes map. Stable identity across renders. */
const NODE_TYPES = { rfTree: TreeNodeBody };

export function DecisionTreeFlow({ value, onChange, height = 480 }: DecisionTreeFlowProps) {
  return (
    <ReactFlowProvider>
      <DecisionTreeFlowInner value={value} onChange={onChange} height={height} />
    </ReactFlowProvider>
  );
}

function DecisionTreeFlowInner({ value, onChange, height }: DecisionTreeFlowProps) {
  // The path of the node currently being edited in the modal — null when closed.
  const [editingPath, setEditingPath] = useState<string | null>(null);

  const handleAddChild = useCallback(
    (path: string, kind: TreeNode['kind']) => {
      const child =
        kind === 'variable' ? makeVariableNode() : kind === 'condition' ? makeConditionNode() : makeActionNode();
      onChange(appendChild(value, path, child));
    },
    [value, onChange],
  );

  const handleDelete = useCallback(
    (path: string) => {
      onChange(removeNode(value, path));
    },
    [value, onChange],
  );

  const handleEdit = useCallback((path: string) => {
    setEditingPath(path);
  }, []);

  // Derive nodes/edges from the tree, then inject the handler callbacks into
  // each node's `data` so TreeNodeBody can call them. Handlers are memoized so
  // identity is stable across renders unless `value` changes.
  const { nodes, edges } = useMemo(() => {
    const flow = toFlow(value);
    const handlers = {
      onAddChild: handleAddChild,
      onDelete: handleDelete,
      onEdit: handleEdit,
    };
    const nodesWithHandlers = flow.nodes.map((n) => ({ ...n, data: { ...n.data, ...handlers } }));
    return { nodes: nodesWithHandlers, edges: flow.edges };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, handleAddChild, handleDelete, handleEdit]);

  const onNodesChange = useCallback(() => {
    // No-op: nodes/edges are derived from `value`; we don't support manual
    // drag-to-move in this pass (positions are recomputed by toFlow).
  }, []);

  const editingNode = editingPath ? findNode(value, editingPath) : undefined;

  return (
    <div style={{ height, border: '1px solid #e8e8e8', borderRadius: 4, background: '#fafafa' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={NODE_TYPES}
        onNodesChange={onNodesChange}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable
        fitView
        proOptions={{ hideAttribution: true }}
        nodeOrigin={[0, 0]}
      >
        <Background gap={16} size={1} />
        <Controls showInteractive={false} />
      </ReactFlow>

      <NodeEditorModal
        path={editingPath}
        node={editingNode}
        onChange={(next) => {
          if (editingPath) onChange(replaceNode(value, editingPath, next));
        }}
        onClose={() => setEditingPath(null)}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Edit modal — reuses ruleforge LeftValueEditor / ValueEditor / ActionEditor
// ---------------------------------------------------------------------------

interface NodeEditorModalProps {
  path: string | null;
  node: TreeNode | undefined;
  onChange: (next: TreeNode) => void;
  onClose: () => void;
}

function NodeEditorModal({ path, node, onChange, onClose }: NodeEditorModalProps) {
  const open = path !== null && node !== undefined;
  const title = node ? nodeKindLabel(node.kind) + '节点' : '编辑';
  return (
    <Modal title={title} open={open} onCancel={onClose} onOk={onClose} okText="关闭" cancelText="取消" width={560} destroyOnHidden>
      {node && node.kind === 'variable' && (
        <LeftValueEditor value={node.left} onChange={(left) => onChange({ ...node, left })} />
      )}
      {node && node.kind === 'condition' && (
        <ConditionFieldsEditor
          node={node}
          onChange={(next) => onChange(next)}
        />
      )}
      {node && node.kind === 'action' && (
        <ActionListEditor actions={node.actions} onChange={(actions) => onChange({ ...node, actions })} />
      )}
    </Modal>
  );
}

/** op Select + optional right-hand ValueEditor for a condition node. */
function ConditionFieldsEditor({
  node,
  onChange,
}: {
  node: Extract<TreeNode, { kind: 'condition' }>;
  onChange: (next: Extract<TreeNode, { kind: 'condition' }>) => void;
}) {
  const noRight = opHasNoInput(node.op);
  const onOpChange = (op: string) => {
    if (opHasNoInput(op)) {
      const { right: _right, ...rest } = node;
      onChange({ ...rest, op } as Extract<TreeNode, { kind: 'condition' }>);
    } else {
      onChange({ ...node, op, right: node.right ?? { type: 'Input', content: '' } });
    }
  };
  return (
    <div>
      <div style={{ marginBottom: 8 }}>
        <Select size="small" style={{ width: '100%' }} value={node.op} onChange={onOpChange} options={OPERATOR_OPTIONS} />
      </div>
      {!noRight && (
        <ValueEditor value={node.right ?? { type: 'Input', content: '' }} onChange={(right) => onChange({ ...node, right })} />
      )}
    </div>
  );
}

/** The action list inside an action leaf: add/remove/edit each action. */
function ActionListEditor({ actions, onChange }: { actions: Action[]; onChange: (next: Action[]) => void }) {
  const update = (i: number, a: Action) => {
    const next = actions.slice();
    next[i] = a;
    onChange(next);
  };
  const remove = (i: number) => {
    if (actions.length <= 1) return; // keep at least one action
    const next = actions.slice();
    next.splice(i, 1);
    onChange(next);
  };
  const add = () => {
    onChange(actions.concat([{ kind: 'console-print', value: { type: 'Input', content: '' } }]));
  };
  return (
    <div>
      {actions.map((a, i) => (
        <ActionEditor key={i} value={a} onChange={(next) => update(i, next)} onDelete={() => remove(i)} />
      ))}
      <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={add}>
        添加动作
      </Button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Custom react-flow node body
// ---------------------------------------------------------------------------

/** Props handed to a react-flow custom node component. */
interface NodeBodyProps {
  id: string;
  data: FlowNode['data'] & {
    onAddChild?: (path: string, kind: TreeNode['kind']) => void;
    onDelete?: (path: string) => void;
    onEdit?: (path: string) => void;
  };
  selected?: boolean;
}

function TreeNodeBody({ id, data, selected }: NodeBodyProps) {
  const { node, kind } = data;

  const containerStyle: React.CSSProperties = {
    minWidth: 150,
    maxWidth: 230,
    padding: 8,
    borderRadius: 6,
    background: '#fff',
    border: selected ? '2px solid #1677ff' : '1px solid #d9d9d9',
    boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
    fontSize: 12,
  };

  if (kind === 'variable' && node.kind === 'variable') {
    const summary = leftSummary(node.left);
    const isRoot = !data.parentPath;
    return (
      <div style={containerStyle} data-testid="rf-tree-node" data-rf-id={id} data-rf-kind="variable" onDoubleClick={() => data.onEdit?.(id)} title="双击编辑">
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Tag color="blue" style={{ margin: 0 }}>
            变量 {isRoot ? '· 根' : ''}
          </Tag>
          <div>{summary}</div>
          <Space size={4} wrap>
            <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => data.onAddChild?.(id, 'condition')}>
              条件
            </Button>
            {!isRoot && (
              <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => data.onDelete?.(id)} />
            )}
          </Space>
        </Space>
      </div>
    );
  }

  if (kind === 'condition' && node.kind === 'condition') {
    const summary = conditionSummary(node);
    return (
      <div style={containerStyle} data-testid="rf-tree-node" data-rf-id={id} data-rf-kind="condition" onDoubleClick={() => data.onEdit?.(id)} title="双击编辑">
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Tag color="orange" style={{ margin: 0 }}>
            条件
          </Tag>
          <div>{summary}</div>
          <Space size={4} wrap>
            <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => data.onAddChild?.(id, 'condition')}>
              条件
            </Button>
            <Button size="small" icon={<ApartmentOutlined />} onClick={() => data.onAddChild?.(id, 'variable')}>
              变量
            </Button>
            <Button size="small" icon={<ThunderboltOutlined />} onClick={() => data.onAddChild?.(id, 'action')}>
              动作
            </Button>
            <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => data.onDelete?.(id)} />
          </Space>
        </Space>
      </div>
    );
  }

  // action leaf
  const action = node as Extract<TreeNode, { kind: 'action' }>;
  const summaries = action.actions.map(actionSummary);
  return (
    <div style={containerStyle} data-testid="rf-tree-node" data-rf-id={id} data-rf-kind="action" onDoubleClick={() => data.onEdit?.(id)} title="双击编辑">
      <Space direction="vertical" size={2} style={{ width: '100%' }}>
        <Tag color="green" style={{ margin: 0 }}>
          动作
        </Tag>
        {summaries.map((s, i) => (
          <div key={i}>{s}</div>
        ))}
        <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => data.onDelete?.(id)} />
      </Space>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Summaries — one-line human-readable text for the node body and canvas
// ---------------------------------------------------------------------------

/** `varLabel` or `var` or the type fallback for a left value. */
function leftSummary(left: LeftValue): string {
  const label = left.varLabel || left.var || left.beanName || left.functionName || '';
  if (label) return label;
  return left.type === 'variable' ? '(未选择变量)' : '(未设置)';
}

/** `op [right]` summary for a condition node. */
function conditionSummary(node: Extract<TreeNode, { kind: 'condition' }>): string {
  const op = opLabel(node.op);
  if (opHasNoInput(node.op) || !node.right) return op;
  const right = node.right;
  let text = '';
  if (right.type === 'Input') text = right.content ?? '';
  else if (right.type === 'Variable' || right.type === 'Parameter') text = right.varLabel || right.var || '';
  else if (right.type === 'Constant') text = right.constLabel || right.const || '';
  else if (right.type === 'NamedReference') text = right.propertyLabel || right.propertyName || '';
  else text = right.type;
  return op + ' ' + text;
}

/** One-line summary for a single action. */
function actionSummary(action: Action): string {
  switch (action.kind) {
    case 'console-print':
      return '打印: ' + (action.value.content || action.value.type);
    case 'var-assign':
      return '赋值: ' + (action.varLabel || action.var) + ' = ' + valueShort(action.value);
    case 'execute-method':
      return '执行方法: ' + (action.methodLabel || action.methodName);
    case 'execute-function':
      return '执行函数: ' + (action.functionLabel || action.functionName);
  }
}

function valueShort(v: { type: string; content?: string }): string {
  if (v.type === 'Input') return v.content || '';
  return v.type;
}

/** Chinese label for a node kind (used in the edit modal title). */
function nodeKindLabel(kind: TreeNode['kind']): string {
  switch (kind) {
    case 'variable':
      return '变量';
    case 'condition':
      return '条件';
    case 'action':
      return '动作';
  }
}

export default DecisionTreeFlow;
