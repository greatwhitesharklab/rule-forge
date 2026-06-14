/**
 * flowLayout.ts — pure ConditionNode ↔ react-flow nodes/edges conversion.
 *
 * Why a separate module:
 *   react-flow's ReactFlow component does not render reliably under jsdom
 *   (it needs a real layout / ResizeObserver / d3-zoom). So the data-tree →
 *   canvas mapping is extracted here as pure, side-effect-free functions that
 *   can be unit-tested directly. ConditionFlow.tsx only wires these into the
 *   <ReactFlow> component and renders the custom node bodies.
 *
 * Stable node id strategy:
 *   Each ConditionNode in the tree is assigned a path-based id:
 *     root              → "root"
 *     root's child #i   → "root.i"
 *     grandchild #j     → "root.i.j"
 *   These ids survive re-renders and edits that don't move a node, so
 *   react-flow doesn't remount node components (which would lose focus in
 *   the inline AtomEditor).
 *
 * Layout:
 *   Vertical-tree style: depth → x column, sibling index → y row. Parents
 *   sit to the left of their children, edges go parent → child. Each level
 *   has a fixed column step; siblings are stacked with a fixed row step
 *   (grown to fit each subtree's leaf count so subtrees don't overlap).
 */
import type { Edge, Node } from '@xyflow/react';
import type { ConditionNode, JunctionType } from '../model/types';

/** react-flow node payload — everything ConditionFlow's custom nodes need. */
export interface FlowNodeData {
  /** The ConditionNode subtree rooted at this node (immutable snapshot). */
  node: ConditionNode;
  /** This node's path id (mirrors the react-flow node id). */
  path: string;
  /** Depth in the tree (root = 0). Used by the node body for indentation. */
  depth: number;
  /** Parent's path id, undefined for the root. */
  parentPath?: string;
  /** The kind label ("并且" / "或者" / condition summary). */
  kind: 'junction' | 'named' | 'atom';
  /**
   * react-flow's `Node<T>` requires `T extends Record<string, unknown>`, which
   * means an index signature. We allow extra handler keys to be merged in by
   * ConditionFlow (onAddCondition / onSwitchType / etc.).
   */
  [key: string]: unknown;
}

/** A typed react-flow node carrying our FlowNodeData. */
export type FlowNode = Node<FlowNodeData>;

const ROOT_ID = 'root';
const COL_WIDTH = 280;
const ROW_HEIGHT = 120;

/** Build a fresh empty atom (used when "add condition" is clicked). */
export function makeAtom(): ConditionNode {
  return {
    kind: 'atom',
    op: 'Equals',
    left: { type: 'variable', varCategory: '', var: '', varLabel: '', datatype: '' },
    right: { type: 'Input', content: '' },
  };
}

/** Build a fresh empty junction. */
export function makeJunction(type: JunctionType = 'and'): ConditionNode {
  return { kind: 'junction', type, children: [] };
}

/**
 * Convert a ConditionNode tree into react-flow {nodes, edges}.
 *
 * Layout is computed bottom-up: each subtree reports how many leaf-rows it
 * occupies, and the parent centers itself over its children's vertical span.
 */
export function toFlow(root: ConditionNode): { nodes: FlowNode[]; edges: Edge[] } {
  const nodes: FlowNode[] = [];
  const edges: Edge[] = [];

  /**
   * Recursive layout. Returns the number of leaf-rows this subtree occupies
   * (1 for atoms / named, ≥1 for junctions). Writes nodes/edges as a side
   * effect. `yOffset` is the top y for this subtree; this subtree's leaves
   * occupy [yOffset, yOffset + leafCount * ROW_HEIGHT).
   */
  function walk(
    node: ConditionNode,
    path: string,
    depth: number,
    parentPath: string | undefined,
    yOffset: number,
  ): number {
    const kind = node.kind;

    if (kind === 'junction') {
      // Lay out children first to know our own center.
      const childPaths: string[] = [];
      let cursor = yOffset;
      node.children.forEach((child, i) => {
        const childPath = path + '.' + i;
        childPaths.push(childPath);
        cursor += walk(child, childPath, depth + 1, path, cursor);
      });
      const leafCount = Math.max(1, Math.round((cursor - yOffset) / ROW_HEIGHT));
      const centerY = yOffset + (leafCount * ROW_HEIGHT) / 2;

      nodes.push({
        id: path,
        type: 'rfCondition',
        position: { x: depth * COL_WIDTH, y: centerY - ROW_HEIGHT / 2 },
        data: { node, path, depth, parentPath, kind: 'junction' },
      });
      childPaths.forEach((childPath) => {
        edges.push(makeEdge(path, childPath));
      });
      return leafCount * ROW_HEIGHT;
    }

    // named + atom are leaves (1 row tall).
    nodes.push({
      id: path,
      type: 'rfCondition',
      position: { x: depth * COL_WIDTH, y: yOffset },
      data: { node, path, depth, parentPath, kind },
    });
    return ROW_HEIGHT;
  }

  walk(root, ROOT_ID, 0, undefined, 0);
  return { nodes, edges };
}

/** Build a standard edge id and shape for a parent → child link. */
function makeEdge(source: string, target: string): Edge {
  return {
    id: 'e-' + source + '-' + target,
    source,
    target,
    type: 'smoothstep',
  };
}

// ---------------------------------------------------------------------------
// Tree mutations — all pure, return a new ConditionNode root.
// ---------------------------------------------------------------------------

/**
 * Replace the subtree at `path` with `next`. Returns a new root. Throws if
 * the path isn't found (programming error, not user-facing).
 *
 * `path` is the dotted form ("root.0.1"); the leading "root" segment is
 * stripped before recursion.
 */
export function replaceNode(root: ConditionNode, path: string, next: ConditionNode): ConditionNode {
  return replaceAt(root, stripRoot(path), next);
}

function stripRoot(path: string): number[] {
  if (!path.startsWith(ROOT_ID)) {
    throw new Error('replaceNode: path "' + path + '" does not start at root');
  }
  const rest = path.slice(ROOT_ID.length);
  if (rest === '') return [];
  // rest looks like ".0.1"
  return rest.split('.').filter((s) => s.length > 0).map((s) => parseInt(s, 10));
}

function replaceAt(node: ConditionNode, segs: number[], next: ConditionNode): ConditionNode {
  if (segs.length === 0) return next;
  if (node.kind !== 'junction') {
    throw new Error('replaceAt: cannot descend into a non-junction node');
  }
  const [head, ...tail] = segs;
  const children = node.children.slice();
  children[head] = replaceAt(children[head], tail, next);
  return { ...node, children };
}

/**
 * Append a child to the junction at `path`. Returns a new root.
 */
export function appendChild(root: ConditionNode, path: string, child: ConditionNode): ConditionNode {
  return mutateAt(root, stripRoot(path), (junction) => {
    if (junction.kind !== 'junction') {
      throw new Error('appendChild: target is not a junction');
    }
    return { ...junction, children: junction.children.concat(child) };
  });
}

/**
 * Remove the subtree at `path`. Returns a new root.
 *
 * If the path points at the root, the result is an empty AND junction
 * (the editor always shows a root junction; an empty tree is meaningless).
 */
export function removeNode(root: ConditionNode, path: string): ConditionNode {
  if (path === ROOT_ID) {
    return makeJunction('and');
  }
  return mutateAt(root, stripRoot(path).slice(0, -1), (junction) => {
    if (junction.kind !== 'junction') {
      throw new Error('removeNode: parent is not a junction');
    }
    const idx = stripRoot(path).slice(-1)[0];
    const children = junction.children.slice();
    children.splice(idx, 1);
    return { ...junction, children };
  });
}

/**
 * Toggle / set the junction type of the junction at `path`.
 * If the node at `path` is a `named` node (which also carries a type), its
 * type is updated too. Atoms are left untouched.
 */
export function setJunctionType(root: ConditionNode, path: string, type: JunctionType): ConditionNode {
  return mutateAt(root, stripRoot(path), (node) => {
    if (node.kind === 'junction' || node.kind === 'named') {
      return { ...node, type };
    }
    return node;
  });
}

/**
 * Generic recursion that walks to the parent of the target segment and
 * applies `fn` to produce a replacement node. `segs` is the path *to the
 * target* (including the final index); for remove we slice off the last
 * segment and operate on the parent, for replace we go all the way.
 */
function mutateAt(
  node: ConditionNode,
  segs: number[],
  fn: (node: ConditionNode) => ConditionNode,
): ConditionNode {
  if (segs.length === 0) {
    return fn(node);
  }
  if (node.kind !== 'junction') {
    throw new Error('mutateAt: cannot descend into a non-junction node');
  }
  const [head, ...tail] = segs;
  const children = node.children.slice();
  children[head] = mutateAt(children[head], tail, fn);
  return { ...node, children };
}

/**
 * Find the ConditionNode at `path` (or undefined if not found). Used by the
 * ConditionFlow component to look up the node data for the AtomEditor modal.
 */
export function findNode(root: ConditionNode, path: string): ConditionNode | undefined {
  try {
    return findAt(root, stripRoot(path));
  } catch {
    return undefined;
  }
}

function findAt(node: ConditionNode, segs: number[]): ConditionNode {
  if (segs.length === 0) return node;
  if (node.kind !== 'junction') {
    throw new Error('findAt: cannot descend');
  }
  const [head, ...tail] = segs;
  return findAt(node.children[head], tail);
}
