/**
 * File tree node type definitions
 *
 * Represents the tree structure used in the project navigator.
 */

export interface TreeNodeData {
    id: string;
    name: string;
    type: TreeNodeType;
    fullPath: string;
    children?: TreeNodeData[];
    _level?: number;
    _icon?: string;
    _style?: Record<string, string>;
    contextMenu?: ContextMenuItem[];
    editorPath?: string;
    folderType?: string;
    project?: string;
    _forceExpand?: boolean;
    // V7.7.2:树节点 published 徽标用 — loadData 后置 enrichment(POST /v1/publish/status-batch)回填。
    // _publishedStatus === 'published' 时 FileTreeNode 渲染 <Tag color="green">已发布 v{_publishedVersion}</Tag>
    _publishedStatus?: 'draft' | 'published';
    _publishedVersion?: string | null;
    [key: string]: unknown;
}

export type TreeNodeType =
    | 'root'
    | 'project'
    | 'rule'
    | 'resource'
    | 'all'
    | 'folder'
    // V7.7.2:'resourcePackage' 删除 — 老 .rp 知识包节点类型废弃
    | 'lib'
    | 'action'
    | 'parameter'
    | 'constant'
    | 'variable'
    | 'ruleLib'
    | 'decisionTableLib'
    | 'decisionTreeLib'
    | 'scorecardLib'
    | 'ul'
    | 'decisionTable'
    | 'scriptDecisionTable'
    | 'decisionTree'
    | 'scorecard'
    | 'complexscorecard'
    | 'crosstab'
    | 'publicResource';

export interface ContextMenuItem {
    name: string;
    icon?: string;
    click?: (data: TreeNodeData) => void;
}

export interface FileVersion {
    name: string;
    version: string;
    createDate: string;
    [key: string]: unknown;
}
