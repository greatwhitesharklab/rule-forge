/**
 * ConditionFlow — the react-flow canvas that renders a ConditionNode tree.
 *
 * The ConditionNode tree is the single source of truth. react-flow's
 * nodes/edges are a *derived view*: on every render we run toFlow(value) to
 * rebuild them. All edits (add condition / add junction / switch and-or /
 * delete / edit atom) go through props.onChange with a new ConditionNode
 * root built via the pure helpers in flowLayout.ts.
 *
 * Custom node body:
 *   - junction  → "并且" / "或者" pill + add-condition / add-junction buttons
 *   - named     → reference name + var-category + item count
 *   - atom      → one-line summary (varLabel op right); double-click opens
 *                 an AtomEditor in a Modal
 *
 * react-flow's edges are non-interactive (no manual re-parenting in this
 * pass); structural edits happen through the node-body buttons.
 */
import { useCallback, useMemo, useState } from 'react';
import { Background, Controls, ReactFlow, ReactFlowProvider } from '@xyflow/react';
import { Button, Modal, Space, Tag } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ApartmentOutlined,
} from '@ant-design/icons';
import '@xyflow/react/dist/style.css';

import type { ConditionNode, JunctionType } from '../model/types';
import { JUNCTION_TYPE_OPTIONS, opLabel, opHasNoInput } from './constants';
import {
  FlowNode,
  appendChild,
  findNode,
  makeAtom,
  makeJunction,
  removeNode,
  replaceNode,
  setJunctionType,
  toFlow,
} from './flowLayout';
import { AtomEditor } from './AtomEditor';

export interface ConditionFlowProps {
  /** The root condition node (must be a junction). Controlled. */
  value: ConditionNode;
  /** Called with a new root whenever the user edits the tree. */
  onChange: (next: ConditionNode) => void;
  /** Canvas height in px (default 420). */
  height?: number;
}

/** The react-flow nodeTypes map. Stable identity across renders. */
const NODE_TYPES = { rfCondition: ConditionNodeBody };

export function ConditionFlow({ value, onChange, height = 420 }: ConditionFlowProps) {
  return (
    <ReactFlowProvider>
      <ConditionFlowInner value={value} onChange={onChange} height={height} />
    </ReactFlowProvider>
  );
}

function ConditionFlowInner({ value, onChange, height }: ConditionFlowProps) {
  // The atom currently being edited in the modal (path id) — undefined when closed.
  const [editingPath, setEditingPath] = useState<string | null>(null);

  const handleAddCondition = useCallback(
    (path: string) => {
      onChange(appendChild(value, path, makeAtom()));
    },
    [value, onChange],
  );

  const handleAddJunction = useCallback(
    (path: string) => {
      onChange(appendChild(value, path, makeJunction('and')));
    },
    [value, onChange],
  );

  const handleSwitchType = useCallback(
    (path: string, type: JunctionType) => {
      onChange(setJunctionType(value, path, type));
    },
    [value, onChange],
  );

  const handleDelete = useCallback(
    (path: string) => {
      onChange(removeNode(value, path));
    },
    [value, onChange],
  );

  const handleEditAtom = useCallback((path: string) => {
    setEditingPath(path);
  }, []);

  // Derive nodes/edges from the tree, then inject the handler callbacks into
  // each node's `data` so ConditionNodeBody can call them. Handlers are
  // memoized so identity is stable across renders unless `value` changes.
  const { nodes, edges } = useMemo(() => {
    const flow = toFlow(value);
    const handlers = {
      onAddCondition: handleAddCondition,
      onAddJunction: handleAddJunction,
      onSwitchType: handleSwitchType,
      onDelete: handleDelete,
      onEditAtom: handleEditAtom,
    };
    const nodesWithHandlers = flow.nodes.map((n) => ({ ...n, data: { ...n.data, ...handlers } }));
    return { nodes: nodesWithHandlers, edges: flow.edges };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, handleAddCondition, handleAddJunction, handleSwitchType, handleDelete, handleEditAtom]);

  const onNodesChange = useCallback(() => {
    // No-op: nodes/edges are derived from `value`; we don't support manual
    // drag-to-move in this pass (positions are recomputed by toFlow).
  }, []);

  // Look up the atom being edited (may be undefined if the tree was mutated
  // out from under us — guard with findNode).
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
        // Pass-through callbacks the node bodies need. react-flow merges
        // `data` with whatever we set; we put the handlers under a `handlers`
        // key to avoid colliding with the ConditionNode data.
        nodeOrigin={[0, 0]}
      >
        <Background gap={16} size={1} />
        <Controls showInteractive={false} />
      </ReactFlow>

      <Modal
        title="编辑条件"
        open={editingPath !== null && editingNode !== undefined && editingNode.kind === 'atom'}
        onCancel={() => setEditingPath(null)}
        onOk={() => {
          // AtomEditor is live (controlled); the tree is already updated on
          // every field edit through onChange above, so OK just closes.
          setEditingPath(null);
        }}
        okText="关闭"
        cancelText="取消"
        width={520}
        destroyOnHidden
      >
        {editingNode && editingNode.kind === 'atom' && (
          <AtomEditor value={editingNode} onChange={(next) => onChange(replaceNode(value, editingPath!, next))} />
        )}
      </Modal>
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
    // ConditionFlow passes these through via node.data — see note above.
    onAddCondition?: (path: string) => void;
    onAddJunction?: (path: string) => void;
    onSwitchType?: (path: string, type: JunctionType) => void;
    onDelete?: (path: string) => void;
    onEditAtom?: (path: string) => void;
  };
  selected?: boolean;
}

function ConditionNodeBody({ id, data, selected }: NodeBodyProps) {
  const { node, kind } = data;

  const containerStyle: React.CSSProperties = {
    minWidth: 160,
    maxWidth: 240,
    padding: 8,
    borderRadius: 6,
    background: '#fff',
    border: selected ? '2px solid #1677ff' : '1px solid #d9d9d9',
    boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
    fontSize: 12,
  };

  if (kind === 'junction' && node.kind === 'junction') {
    const junction = node;
    return (
      <div style={containerStyle} data-testid="rf-node" data-rf-id={id} data-rf-kind="junction">
        <Space orientation="vertical" size={4} style={{ width: '100%' }}>
          <Space>
            <Tag color={junction.type === 'and' ? 'blue' : 'orange'} style={{ margin: 0 }}>
              {junction.type === 'and' ? '并且' : '或者'}
            </Tag>
            <select
              data-testid="rf-junction-switch"
              value={junction.type}
              onChange={(e) => data.onSwitchType?.(id, e.target.value as JunctionType)}
              style={{ fontSize: 11, padding: '0 4px', height: 22 }}
            >
              {JUNCTION_TYPE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </Space>
          <Space size={4} wrap>
            <Button
              size="small"
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => data.onAddCondition?.(id)}
            >
              条件
            </Button>
            <Button
              size="small"
              icon={<ApartmentOutlined />}
              onClick={() => data.onAddJunction?.(id)}
            >
              联合
            </Button>
            {data.parentPath && (
              <Button
                size="small"
                type="text"
                danger
                icon={<DeleteOutlined />}
                onClick={() => data.onDelete?.(id)}
              />
            )}
          </Space>
        </Space>
      </div>
    );
  }

  if (kind === 'named' && node.kind === 'named') {
    const named = node;
    return (
      <div style={containerStyle} data-testid="rf-node" data-rf-id={id} data-rf-kind="named">
        <Space orientation="vertical" size={2} style={{ width: '100%' }}>
          <Tag color="purple" style={{ margin: 0 }}>
            命名联合
          </Tag>
          <div>
            <strong>{named.referenceName || '(未命名)'}</strong>
          </div>
          <div style={{ color: '#888' }}>{named.varCategory}</div>
          <div style={{ color: '#888' }}>{named.items.length} 项</div>
          {data.parentPath && (
            <Button
              size="small"
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={() => data.onDelete?.(id)}
            />
          )}
        </Space>
      </div>
    );
  }

  // atom
  const summary = atomSummary(node as Extract<ConditionNode, { kind: 'atom' }>);
  return (
    <div
      style={containerStyle}
      data-testid="rf-node"
      data-rf-id={id}
      data-rf-kind="atom"
      onDoubleClick={() => data.onEditAtom?.(id)}
      title="双击编辑"
    >
      <Space orientation="vertical" size={2} style={{ width: '100%' }}>
        <div>{summary}</div>
        {data.parentPath && (
          <Button
            size="small"
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={() => data.onDelete?.(id)}
          />
        )}
      </Space>
    </div>
  );
}

/**
 * One-line atom summary: `varLabel [op] rightContent`.
 * Falls back to var name if no label; falls back to op value if unknown.
 */
function atomSummary(atom: Extract<ConditionNode, { kind: 'atom' }>): string {
  const left = atom.left;
  const leftLabel = left.varLabel || left.var || left.beanName || left.functionName || '?';
  const op = opLabel(atom.op);
  if (opHasNoInput(atom.op) || !atom.right) {
    return leftLabel + ' ' + op;
  }
  const right = atom.right;
  let rightText = '';
  if (right.type === 'Input') rightText = right.content ?? '';
  else if (right.type === 'Variable' || right.type === 'Parameter')
    rightText = right.varLabel || right.var || '';
  else if (right.type === 'Constant') rightText = right.constLabel || right.const || '';
  else if (right.type === 'NamedReference') rightText = right.propertyLabel || right.propertyName || '';
  else rightText = right.type;
  return leftLabel + ' ' + op + ' ' + rightText;
}

export default ConditionFlow;
