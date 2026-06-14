/**
 * serialize.ts — DecisionTree state → legacy `<decision-tree>` XML string.
 *
 * Pure function. No React / DOM / jquery / Raphael. The output is
 * byte-for-byte compatible with the original jquery + Raphael editor's
 * toXml() chain (new/DecisionTree.ts → VariableNode → ConditionNode →
 * ActionNode → ConditionLeft / ComparisonOperator / ActionType), so the
 * React rewrite can persist into the exact same storage format the backend
 * com.ruleforge.parse.decisiontree.DecisionTreeParser already parses.
 *
 * The <left> / <value> / action children are serialized by the SHARED
 * ruleforge serialize helpers (serializeLeft / serializeValue /
 * serializeAction) — the decision-tree schema uses the identical widgets as
 * the ruleset schema, so we reuse the implementations instead of forking.
 *
 * XML escaping: the shared ruleforge serialize helpers escape all attribute
 * values and text content (see their `esc()`). The only decision-tree-local
 * output that needs escaping here is the remark CDATA payload.
 */

import type { DecisionTree, TreeNode } from './types';
import {
  serializeAction,
  serializeLeft,
  serializeValue,
} from '../../ruleforge/model/serialize';

/** XML-escape only the CDATA terminator so CDATA stays well-formed. */
function escCdata(s: string): string {
  return s.replace(/]]>/g, ']]&gt;');
}

/**
 * Serialize the full decision-tree document (including the XML declaration).
 *
 * Order matches new/DecisionTree.toXml:
 *   <?xml?>
 *   <decision-tree {properties}>
 *     <remark><![CDATA[…]]></remark>
 *     <import-parameter-library/>…
 *     <import-variable-library/>…
 *     <import-constant-library/>…
 *     <import-action-library/>…
 *     <variable-tree-node>…</variable-tree-node>
 *   </decision-tree>
 */
export function serializeDecisionTree(tree: DecisionTree): string {
  let xml = '<?xml version="1.0" encoding="UTF-8"?>';
  xml += '<decision-tree';
  for (const prop of tree.properties) {
    xml += ' ' + serializeProperty(prop);
  }
  xml += '>';
  xml += serializeRemark(tree.remark);
  for (const p of tree.parameterLibraries) {
    xml += '<import-parameter-library path="' + escAttr(p) + '"/>';
  }
  for (const v of tree.variableLibraries) {
    xml += '<import-variable-library path="' + escAttr(v) + '"/>';
  }
  for (const c of tree.constantLibraries) {
    xml += '<import-constant-library path="' + escAttr(c) + '"/>';
  }
  for (const a of tree.actionLibraries) {
    xml += '<import-action-library path="' + escAttr(a) + '"/>';
  }
  xml += serializeTreeNode(tree.root);
  xml += '</decision-tree>';
  return xml;
}

/** `<remark><![CDATA[...]]></remark>` (empty remark still emits an empty CDATA). */
function serializeRemark(remark: string): string {
  return '<remark><![CDATA[' + escCdata(remark) + ']]></remark>';
}

/**
 * Serialize a decision-tree attribute property as `name="value"` or
 * `name="true"/"false"`. (Matches RuleProperty.toXml and the ruleset editor.)
 */
function serializeProperty(prop: { name: string; value: string | boolean }): string {
  if (typeof prop.value === 'boolean') {
    return prop.name + '="' + (prop.value ? 'true' : 'false') + '"';
  }
  return prop.name + '="' + escAttr(prop.value) + '"';
}

/**
 * Serialize one TreeNode (variable / condition / action) recursively.
 *
 *   variable  → <variable-tree-node><left/>…condition children…</variable-tree-node>
 *   condition → <condition-tree-node op="…"><value/>…children…</condition-tree-node>
 *   action    → <action-tree-node>…actions…</action-tree-node>   (leaf, no children)
 */
function serializeTreeNode(node: TreeNode): string {
  switch (node.kind) {
    case 'variable': {
      let xml = '<variable-tree-node>';
      xml += serializeLeft(node.left);
      for (const child of node.children) {
        xml += serializeTreeNode(child);
      }
      xml += '</variable-tree-node>';
      return xml;
    }
    case 'condition': {
      let xml = '<condition-tree-node op="' + escAttr(node.op) + '">';
      if (node.right) {
        xml += serializeValue(node.right);
      }
      for (const child of node.children) {
        xml += serializeTreeNode(child);
      }
      xml += '</condition-tree-node>';
      return xml;
    }
    case 'action': {
      let xml = '<action-tree-node>';
      for (const action of node.actions) {
        xml += serializeAction(action);
      }
      xml += '</action-tree-node>';
      return xml;
    }
  }
}

/**
 * XML-escape a string for an attribute value.
 *
 * Local copy of the ruleforge serialize `esc()` so this module has no
 * non-export-symbol dependency on the ruleset model. Kept identical so output
 * is byte-for-byte the same.
 */
function escAttr(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/'/g, '&apos;')
    .replace(/"/g, '&quot;');
}
