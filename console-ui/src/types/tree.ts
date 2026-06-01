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
    [key: string]: unknown;
}

export type TreeNodeType =
    | 'root'
    | 'project'
    | 'rule'
    | 'resource'
    | 'all'
    | 'folder'
    | 'resourcePackage'
    | 'lib'
    | 'action'
    | 'parameter'
    | 'constant'
    | 'variable'
    | 'ruleLib'
    | 'decisionTableLib'
    | 'decisionTreeLib'
    | 'flowLib'
    | 'scorecardLib'
    | 'ul'
    | 'decisionTable'
    | 'scriptDecisionTable'
    | 'decisionTree'
    | 'flow'
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
