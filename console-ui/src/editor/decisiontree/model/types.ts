/**
 * Pure data model for the decisiontree (决策树) editor.
 *
 * This module is intentionally framework-free: no React, no DOM, no jquery,
 * no Raphael. It defines the TypeScript types that the React rewrite
 * (react-flow canvas) uses as its single source of truth, and that
 * serialize.ts / parse.ts round-trip to/from the legacy `<decision-tree>` XML.
 *
 * The decision-tree XML schema (from the backend
 * com.ruleforge.parse.decisiontree.*) is a tree of three node kinds:
 *
 *   <variable-tree-node>          → holds a <left> + child condition nodes
 *   <condition-tree-node op="…">  → holds a <value> (right-hand side) + child
 *                                    condition / variable / action nodes
 *   <action-tree-node>            → a LEAF holding one or more actions
 *
 * The root of a decision-tree is always a <variable-tree-node>.
 *
 * Comparison/condition value widgets (<left>, <value>) and the leaf actions
 * are byte-for-byte identical to the ruleset schema, so this model REUSES the
 * ruleforge model types (LeftValue / ValueExpr / Action / RuleProperty) rather
 * than redefining them. Only the tree scaffolding is decision-tree-specific.
 *
 * Tagged-union `kind` maps to the XML element name:
 *   TreeNode.variable  →  <variable-tree-node>
 *   TreeNode.condition →  <condition-tree-node>
 *   TreeNode.action    →  <action-tree-node>
 */

/**
 * The three tree-node kinds. Discriminant for the `TreeNode` union below.
 *
 * Mirrors the backend TreeNodeType enum (variable / condition / action) and
 * the legacy addChild('condition' | 'action' | 'variable') calls.
 */
export type TreeNodeKind = 'variable' | 'condition' | 'action';

/**
 * Recursive decision-tree node. Discriminated by `kind`.
 *
 *   variable  → a <variable-tree-node>: a left-hand value + a list of child
 *               condition nodes (the branches off this variable).
 *   condition → a <condition-tree-node op="…">: an operator + a right-hand
 *               value, then any mix of child condition / variable / action
 *               nodes (the branches when this condition is true).
 *   action    → a <action-tree-node>: a LEAF holding one or more actions.
 *               Has no children.
 */
export type TreeNode =
  | { kind: 'variable'; left: LeftValue; children: ConditionOrActionChild[] }
  | { kind: 'condition'; op: string; right?: ValueExpr; children: TreeNode[] }
  | { kind: 'action'; actions: Action[] };

/**
 * A <variable-tree-node> only ever holds condition children (the legacy
 * VariableTreeNode.addChild only allowed 'condition'; see TreeNode.ts:119).
 * We model that constraint at the type level so the editor UI and serialize
 * cannot produce an invalid tree.
 */
export type ConditionOrActionChild = Extract<TreeNode, { kind: 'condition' }>;

// ---------------------------------------------------------------------------
// Re-exports — shared with the ruleset (ruleforge) model
// ---------------------------------------------------------------------------

/**
 * The `<left>` element of a <variable-tree-node>.
 *
 * Re-exported from the ruleforge model so the editor reuses the same
 * LeftValueEditor widget and the same serialize/parse logic. See
 * `console-ui/src/editor/ruleforge/model/types.ts` for the full shape.
 */
export type {
  LeftValue,
  ValueExpr,
  Action,
  RuleProperty,
  MethodParam,
  FunctionParam,
  SimpleArith,
  ComplexArith,
} from '../../ruleforge/model/types';

// Re-import the same types for local use (the `import type` above is
// type-only; this file references them in TreeNode).
import type {
  LeftValue,
  ValueExpr,
  Action,
  RuleProperty,
} from '../../ruleforge/model/types';

/**
 * The full `<decision-tree>` document state.
 *
 * The root is always a variable node (the backend DecisionTreeParser requires
 * a <variable-tree-node> as the single tree child). An "empty" decision-tree
 * has a root variable node with an empty left and no children.
 */
export interface DecisionTree {
  /** `<import-parameter-library path="…"/>` (order preserved). */
  parameterLibraries: string[];
  /** `<import-variable-library path="…"/>` (order preserved). */
  variableLibraries: string[];
  /** `<import-constant-library path="…"/>` (order preserved). */
  constantLibraries: string[];
  /** `<import-action-library path="…"/>` (order preserved). */
  actionLibraries: string[];
  /** `<remark><![CDATA[...]]></remark>` (raw text). */
  remark: string;
  /**
   * `<decision-tree salience="…" effective-date="…" …>` attributes.
   *
   * Subset of the ruleforge RuleProperty: salience / effective-date /
   * expires-date / enabled / debug. (loop / activation-group / agenda-group
   * etc. are rule-only and do not appear on <decision-tree>.)
   */
  properties: RuleProperty[];
  /** The single <variable-tree-node> root. */
  root: TreeNode;
}
