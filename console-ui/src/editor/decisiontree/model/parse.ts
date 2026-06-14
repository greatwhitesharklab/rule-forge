/**
 * parse.ts — legacy `<decision-tree>` XML string → DecisionTree state.
 *
 * Pure function. Uses DOMParser (jsdom in tests, the browser in the editor).
 * The inverse of serialize.ts: `parse(serialize(state))` deep-equals `state`.
 *
 * The <left> / <value> / action children are parsed by the SHARED ruleforge
 * parse helpers (parseLeft / parseValue / parseAction) — the decision-tree
 * schema uses the identical widgets as the ruleset schema, so we reuse the
 * implementations instead of forking.
 *
 * All text returned to the model is RAW (XML-unescaped). The DOM already
 * resolves entities (`&amp;` → `&`) for both attribute values and text
 * content, so we just read them back verbatim.
 */

import type { DecisionTree, TreeNode } from './types';
import type { RuleProperty } from '../../ruleforge/model/types';
import { parseAction, parseLeft, parseValue } from '../../ruleforge/model/parse';

const XML_MIME = 'text/xml';

/**
 * The decision-tree attributes that are booleans on the wire (vs. strings).
 * Mirrors DecisionTreeParser.parse: enabled / debug are Boolean.valueOf;
 * salience / effective-date / expires-date are string-typed in the model
 * (we keep them as raw strings, matching the ruleset RuleProperty convention).
 */
const BOOLEAN_PROPS = new Set(['enabled', 'debug']);

/** Parse a full decision-tree XML string into a DecisionTree. */
export function parseDecisionTree(xml: string): DecisionTree {
  const doc = new DOMParser().parseFromString(xml, XML_MIME);
  assertNoParserError(doc);
  const root = doc.documentElement;
  if (!root || root.tagName !== 'decision-tree') {
    throw new Error('parseDecisionTree: root element is not <decision-tree>');
  }

  const properties: RuleProperty[] = [];
  for (const attr of Array.from(root.attributes)) {
    if (BOOLEAN_PROPS.has(attr.name)) {
      properties.push({ name: attr.name, value: attr.value === 'true' });
    } else {
      properties.push({ name: attr.name, value: attr.value });
    }
  }

  const parameterLibraries: string[] = [];
  const variableLibraries: string[] = [];
  const constantLibraries: string[] = [];
  const actionLibraries: string[] = [];
  let remark = '';
  let treeRoot: TreeNode | undefined;

  for (const child of Array.from(root.children)) {
    switch (child.tagName) {
      case 'import-parameter-library':
        parameterLibraries.push(child.getAttribute('path') ?? '');
        break;
      case 'import-variable-library':
        variableLibraries.push(child.getAttribute('path') ?? '');
        break;
      case 'import-constant-library':
        constantLibraries.push(child.getAttribute('path') ?? '');
        break;
      case 'import-action-library':
        actionLibraries.push(child.getAttribute('path') ?? '');
        break;
      case 'remark':
        remark = child.textContent ?? '';
        break;
      case 'variable-tree-node':
        treeRoot = parseVariableNode(child);
        break;
      default:
        // Unknown element — ignore (forward-compat).
        break;
    }
  }

  if (!treeRoot) {
    // No root <variable-tree-node> present — fall back to an empty tree so the
    // editor still renders (the backend always emits one, but a hand-edited
    // file might not).
    treeRoot = { kind: 'variable', left: { type: 'variable' }, children: [] };
  }

  return {
    parameterLibraries,
    variableLibraries,
    constantLibraries,
    actionLibraries,
    remark,
    properties,
    root: treeRoot,
  };
}

/** DOMParser can surface a `<parsererror>` element when the XML is malformed. */
function assertNoParserError(doc: Document): void {
  const err = doc.getElementsByTagName('parsererror')[0];
  if (err) {
    throw new Error('parseDecisionTree: malformed XML — ' + (err.textContent ?? '').slice(0, 200));
  }
}

// ---------------------------------------------------------------------------
// Tree nodes
// ---------------------------------------------------------------------------

/**
 * Parse a <variable-tree-node>: one <left> + zero or more <condition-tree-node>
 * children. (The legacy VariableTreeNode only allows condition children.)
 */
function parseVariableNode(el: Element): TreeNode {
  let left;
  const children: Extract<TreeNode, { kind: 'condition' }>[] = [];
  for (const child of Array.from(el.children)) {
    if (child.tagName === 'left') {
      left = parseLeft(child);
    } else if (child.tagName === 'condition-tree-node') {
      children.push(parseConditionNode(child) as Extract<TreeNode, { kind: 'condition' }>);
    }
    // A <variable-tree-node> never holds action/variable children per the
    // legacy schema; ignore anything else for forward-compat.
  }
  return {
    kind: 'variable',
    left: left ?? { type: 'variable' },
    children,
  };
}

/**
 * Parse a <condition-tree-node op="…">: an optional <value> (right-hand side),
 * then any mix of child <condition-tree-node> / <variable-tree-node> /
 * <action-tree-node>.
 */
function parseConditionNode(el: Element): TreeNode {
  const op = el.getAttribute('op') ?? '';
  let right;
  const children: TreeNode[] = [];
  for (const child of Array.from(el.children)) {
    if (child.tagName === 'value') {
      right = parseValue(child);
    } else if (child.tagName === 'condition-tree-node') {
      children.push(parseConditionNode(child));
    } else if (child.tagName === 'variable-tree-node') {
      children.push(parseVariableNode(child));
    } else if (child.tagName === 'action-tree-node') {
      children.push(parseActionNode(child));
    }
  }
  const node: Extract<TreeNode, { kind: 'condition' }> = { kind: 'condition', op, children };
  if (right) node.right = right;
  return node;
}

/**
 * Parse an <action-tree-node>: one or more action children (leaf, no tree
 * children of its own).
 */
function parseActionNode(el: Element): TreeNode {
  const actions = Array.from(el.children).map((c) => parseAction(c));
  return { kind: 'action', actions };
}
