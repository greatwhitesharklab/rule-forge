/**
 * flowLayout.ts — pure TreeNode ↔ react-flow nodes/edges conversion + tree
 * mutations for the decisiontree editor.
 *
 * Why a separate module:
 *   react-flow's <ReactFlow> does not render reliably under jsdom (it needs
 *   real layout / ResizeObserver / d3-zoom). So the data-tree → canvas mapping
 *   is extracted here as pure, side-effect-free functions that can be
 *   unit-tested directly. DecisionTreeEditor.tsx only wires these into the
 *   <ReactFlow> component and renders the custom node bodies.
 *
 * Stable node id strategy:
 *   Each TreeNode in the tree is assigned a path-based id:
 *     root              → "root"
 *     root's child #i   → "root.i"
 *     grandchild #j     → "root.i.j"
 *   These ids survive re-renders and edits that don't move a node, so
 *   react-flow doesn't remount node components (which would lose focus in
 *   the inline editors).
 *
 * Layout:
 *   Horizontal-tree style: depth → x column, sibling index → y row. The root
 *   sits at the left; children sit to the right of their parent. Edges go
 *   parent → child (left → right). Each level has a fixed column step;
 *   siblings are stacked with a fixed row step (grown to fit each subtree's
 *   leaf count so subtrees don't overlap).
 */
import type { Edge, Node } from '@xyflow/react';
import type { TreeNode } from '../model/types';

/** The kind label mirrored onto each FlowNodeData for the node body switch. */
export type NodeKind = TreeNode['kind'];

/** react-flow node payload — everything the custom node bodies need. */
export interface FlowNodeData {
  /** The TreeNode subtree rooted at this node (immutable snapshot). */
  node: TreeNode;
  /** This node's path id (mirrors the react-flow node id). */
  path: string;
  /** Depth in the tree (root = 0). */
  depth: number;
  /** Parent's path id, undefined for the root. */
  parentPath?: string;
  /** The node kind ("variable" / "condition" / "action"). */
  kind: NodeKind;
  /**
   * react-flow's `Node<T>` requires `T extends Record<string, unknown>`, which
   * means an index signature. We allow extra handler keys to be merged in by
   * DecisionTreeEditor (onAddCondition / onAddAction / onDelete / onEdit).
   */
  [key: string]: unknown;
}

/** A typed react-flow node carrying our FlowNodeData. */
export type FlowNode = Node<FlowNodeData>;

const ROOT_ID = 'root';
const COL_WIDTH = 300;
const ROW_HEIGHT = 110;

/**
 * The child kinds a node of the given kind may hold:
 *   variable  → [condition]            (legacy VariableTreeNode only adds conditions)
 *   condition → [condition, variable, action]
 *   action    → []                      (leaf)
 */
export function allowedChildKinds(kind: NodeKind): TreeNode['kind'][] {
  switch (kind) {
    case 'variable':
      return ['condition'];
    case 'condition':
      return ['condition', 'variable', 'action'];
    case 'action':
      return [];
  }
}

/** Build a fresh empty variable node (the only valid root). */
export function makeVariableNode(): TreeNode {
  return { kind: 'variable', left: { type: 'variable' }, children: [] };
}

/** Build a fresh empty condition node. */
export function makeConditionNode(): TreeNode {
  return { kind: 'condition', op: 'Equals', right: { type: 'Input', content: '' }, children: [] };
}

/** Build a fresh empty action leaf with one console-print action. */
export function makeActionNode(): TreeNode {
  return { kind: 'action', actions: [{ kind: 'console-print', value: { type: 'Input', content: '' } }] };
}

/** Factory for the empty node of a given kind (used by "add child" buttons). */
export function makeNode(kind: TreeNode['kind']): TreeNode {
  switch (kind) {
    case 'variable':
      return makeVariableNode();
    case 'condition':
      return makeConditionNode();
    case 'action':
      return makeActionNode();
  }
}

/**
 * Convert a TreeNode tree into react-flow {nodes, edges}.
 *
 * Layout is computed bottom-up: each subtree reports how many leaf-rows it
 * occupies, and the parent centers itself over its children's vertical span.
 * The root sits at the leftmost column; edges go parent → child.
 */
export function toFlow(root: TreeNode): { nodes: FlowNode[]; edges: Edge[] } {
  const nodes: FlowNode[] = [];
  const edges: Edge[] = [];

  /**
   * Recursive layout. Returns the number of leaf-rows this subtree occupies
   * (1 for action leaves, ≥1 for variable/condition). Writes nodes/edges as a
   * side effect. `yOffset` is the top y for this subtree; this subtree's
   * leaves occupy [yOffset, yOffset + leafCount * ROW_HEIGHT).
   */
  function walk(
    node: TreeNode,
    path: string,
    depth: number,
    parentPath: string | undefined,
    yOffset: number,
  ): number {
    const kind = node.kind;
    const x = depth * COL_WIDTH;

    if (kind === 'action') {
      nodes.push({ id: path, type: 'rfTree', position: { x, y: yOffset }, data: { node, path, depth, parentPath, kind } });
      return ROW_HEIGHT;
    }

    // variable / condition: lay out children first to know our own center.
    const childPaths: string[] = [];
    let cursor = yOffset;
    const kids = node.children as TreeNode[];
    kids.forEach((child, i) => {
      const childPath = path + '.' + i;
      childPaths.push(childPath);
      cursor += walk(child, childPath, depth + 1, path, cursor);
    });

    const leafCount = Math.max(1, Math.round((cursor - yOffset) / ROW_HEIGHT));
    const centerY = yOffset + (leafCount * ROW_HEIGHT) / 2;

    nodes.push({
      id: path,
      type: 'rfTree',
      position: { x, y: centerY - ROW_HEIGHT / 2 },
      data: { node, path, depth, parentPath, kind },
    });
    childPaths.forEach((childPath) => {
      edges.push(makeEdge(path, childPath));
    });
    return leafCount * ROW_HEIGHT;
  }

  walk(root, ROOT_ID, 0, undefined, 0);
  return { nodes, edges };
}

/** Build a standard edge id and shape for a parent → child link. */
function makeEdge(source: string, target: string): Edge {
  return { id: 'e-' + source + '-' + target, source, target, type: 'smoothstep' };
}

// ---------------------------------------------------------------------------
// Tree mutations — all pure, return a new TreeNode root.
// ---------------------------------------------------------------------------

/**
 * Replace the subtree at `path` with `next`. Returns a new root. Throws if the
 * path isn't found (programming error, not user-facing).
 *
 * `path` is the dotted form ("root.0.1"); the leading "root" segment is
 * stripped before recursion.
 */
export function replaceNode(root: TreeNode, path: string, next: TreeNode): TreeNode {
  return replaceAt(root, stripRoot(path), next);
}

function stripRoot(path: string): number[] {
  if (!path.startsWith(ROOT_ID)) {
    throw new Error('stripRoot: path "' + path + '" does not start at root');
  }
  const rest = path.slice(ROOT_ID.length);
  if (rest === '') return [];
  return rest.split('.').filter((s) => s.length > 0).map((s) => parseInt(s, 10));
}

function replaceAt(node: TreeNode, segs: number[], next: TreeNode): TreeNode {
  if (segs.length === 0) return next;
  if (node.kind === 'action') {
    throw new Error('replaceAt: cannot descend into an action leaf');
  }
  const [head, ...tail] = segs;
  const children = (node.children as TreeNode[]).slice();
  children[head] = replaceAt(children[head], tail, next);
  return { ...node, children } as TreeNode;
}

/**
 * Append a child to the node at `path`. Returns a new root. Throws if the
 * target is an action leaf (which cannot have children) or if the child kind
 * is not allowed for the parent kind.
 */
export function appendChild(root: TreeNode, path: string, child: TreeNode): TreeNode {
  return mutateAt(root, stripRoot(path), (parent) => {
    if (parent.kind === 'action') {
      throw new Error('appendChild: an action leaf cannot have children');
    }
    if (!allowedChildKinds(parent.kind).includes(child.kind)) {
      throw new Error('appendChild: ' + child.kind + ' is not an allowed child of ' + parent.kind);
    }
    return { ...parent, children: (parent.children as TreeNode[]).concat(child) } as TreeNode;
  });
}

/**
 * Remove the subtree at `path`. Returns a new root. Removing the root
 * (`path === "root"`) is rejected — the editor always keeps a root variable
 * node; call replaceNode(root, makeVariableNode()) to clear instead.
 */
export function removeNode(root: TreeNode, path: string): TreeNode {
  if (path === ROOT_ID) {
    throw new Error('removeNode: cannot remove the root; replace it instead');
  }
  const segs = stripRoot(path);
  return mutateAt(root, segs.slice(0, -1), (parent) => {
    if (parent.kind === 'action') {
      throw new Error('removeNode: parent is an action leaf');
    }
    const idx = segs.slice(-1)[0];
    const children = (parent.children as TreeNode[]).slice();
    children.splice(idx, 1);
    return { ...parent, children } as TreeNode;
  });
}

/**
 * Generic recursion that walks to the target segment and applies `fn` to
 * produce a replacement node. `segs` is the path *to the target* (including
 * the final index); for remove we slice off the last segment and operate on
 * the parent, for replace / appendChild we go all the way.
 */
function mutateAt(node: TreeNode, segs: number[], fn: (node: TreeNode) => TreeNode): TreeNode {
  if (segs.length === 0) {
    return fn(node);
  }
  if (node.kind === 'action') {
    throw new Error('mutateAt: cannot descend into an action leaf');
  }
  const [head, ...tail] = segs;
  const children = (node.children as TreeNode[]).slice();
  children[head] = mutateAt(children[head], tail, fn);
  return { ...node, children } as TreeNode;
}

/**
 * Find the TreeNode at `path` (or undefined if not found). Used by the editor
 * to look up the node data for the inline editor modal.
 */
export function findNode(root: TreeNode, path: string): TreeNode | undefined {
  try {
    return findAt(root, stripRoot(path));
  } catch {
    return undefined;
  }
}

function findAt(node: TreeNode, segs: number[]): TreeNode {
  if (segs.length === 0) return node;
  if (node.kind === 'action') {
    throw new Error('findAt: cannot descend into an action leaf');
  }
  const [head, ...tail] = segs;
  return findAt((node.children as TreeNode[])[head], tail);
}
