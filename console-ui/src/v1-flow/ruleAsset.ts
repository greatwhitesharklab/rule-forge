/**
 * V1 RuleAsset 前端类型(镜像后端 com.ruleforge.v1.ast)。
 * .json 文件格式,顶层 version 自识别。
 * 不存 ReactFlow JSON — position 是节点可选字段(运行时忽略),由 ReactFlow nodes 派生。
 *
 * <p>V7.0.0:从 console-ui-v1 demo 骨架并进 console-ui(单 app,antd)。console-ui-v1 用完即弃。
 */

export type V1DataType = 'NUMBER' | 'STRING' | 'BOOLEAN' | 'LIST';
export type NodeType = 'Start' | 'RuleSet' | 'DecisionTable' | 'ScoreCard' | 'Decision';
export type HitPolicy = 'FIRST_MATCH' | 'ALL_MATCH' | 'PRIORITY';
export type TableHitPolicy = 'FIRST' | 'UNIQUE' | 'PRIORITY' | 'ANY' | 'COLLECT';
export type ScoreAggregation = 'SUM' | 'AVG' | 'MIN' | 'MAX' | 'WEIGHTED_SUM';
export type ActionType = 'SET_VARIABLE' | 'ADD_SCORE' | 'SET_DECISION' | 'REJECT' | 'FLAG';

export interface NodeBase {
    id: string;
    type: NodeType;
    name: string;
    description?: string;
}

export interface StartNode extends NodeBase {
    type: 'Start';
    schema: string;
}

export interface Rule {
    id: string;
    name?: string;
    priority?: number;
    enabled?: boolean;
    condition: string; // CEL
    actions: Action[];
}

export interface RuleSetNode extends NodeBase {
    type: 'RuleSet';
    hitPolicy: HitPolicy;
    rules: Rule[];
}

export interface Column {
    name: string;
    dataType: V1DataType;
    direction: 'INPUT' | 'OUTPUT';
}

export interface TableRow {
    id: string;
    conditions: string[];
    outputs: unknown[];
    annotation?: string;
}

export interface DecisionTableNode extends NodeBase {
    type: 'DecisionTable';
    hitPolicy: TableHitPolicy;
    inputs: Column[];
    outputs: Column[];
    rows: TableRow[];
}

export interface Band {
    id: string;
    condition: string;
    score: number;
    reasonCode?: string;
}

export interface Card {
    id: string;
    field: string;
    weight?: number;
    bands: Band[];
}

export interface ScoreCardNode extends NodeBase {
    type: 'ScoreCard';
    output: string;
    aggregation: ScoreAggregation;
    cards: Card[];
}

export interface DecisionNode extends NodeBase {
    type: 'Decision';
    outputs: string[];
    decisionField?: string;
    defaultOutput?: string;
}

export type V1Node = StartNode | RuleSetNode | DecisionTableNode | ScoreCardNode | DecisionNode;

export interface Action {
    type: ActionType;
    target?: string;
    value?: unknown;
    ref?: string;
    reason?: string;
}

export interface SchemaField {
    name: string;
    type: V1DataType;
    label?: string;
    required?: boolean;
}

export interface Schema {
    name: string;
    fields: SchemaField[];
}

export interface FlowElement {
    type: 'startEvent' | 'serviceTask' | 'exclusiveGateway' | 'endEvent' | 'sequenceFlow';
    id: string;
    name?: string;
    position?: { x: number; y: number };
    implementation?: string;
    sourceRef?: string;
    targetRef?: string;
    conditionExpression?: string;
    defaultFlow?: string;
}

export interface Flow {
    id: string;
    name: string;
    version: string;
    flowElements: FlowElement[];
}

export interface RuleAsset {
    version: '1.0';
    id: string;
    name: string;
    flow: Flow;
    nodes: Record<string, V1Node>;
    schema?: Schema;
}
