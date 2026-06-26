import type {Node, Edge} from '@xyflow/react';
import type {RuleAsset, V1Node, NodeType} from './ruleAsset';
import type {V1NodeData} from './FlowNodes';

/**
 * RuleAsset JSON → canvas state(反向序列化,跟 FlowDesigner.toRuleAsset 对偶)。
 *
 * <p>解析 flow.flowElements → ReactFlow nodes/edges;nodes Map → nodesMap。
 * 让"导入已有 .json → 在画布编辑 → 导出"client-side round-trip 闭环。
 *
 * <p>BPMN 元素类型 → V1 节点类型:
 * <ul>
 *   <li>serviceTask/endEvent/startEvent:靠 implementation "NodeType:nodeId" 权威解析</li>
 *   <li>exclusiveGateway → 'Gateway'(无 implementation,纯流程控制元素,不进 nodes{})</li>
 * </ul>
 *
 * <p>Gateway 出边的 conditionExpression 透传到 edge.data(画布编辑 + 导出回写)。
 * Gateway 的 defaultFlow 透传到 rfNode.data.defaultFlow。
 */
const NODE_FROM_BPMN: Record<string, NodeType> = {
    startEvent: 'Start',
    serviceTask: 'RuleSet', // serviceTask 的具体类型靠 implementation 前缀
    endEvent: 'Decision',
    exclusiveGateway: 'Gateway',
};

export interface CanvasState {
    nodes: Node<V1NodeData>[];
    edges: Edge[];
    nodesMap: Record<string, V1Node>;
    schemaName: string;
}

export function fromRuleAsset(asset: RuleAsset): CanvasState {
    const rfNodes: Node<V1NodeData>[] = [];
    const rfEdges: Edge[] = [];
    const nodesMap: Record<string, V1Node> = {...(asset.nodes || {})};

    for (const el of asset.flow?.flowElements || []) {
        if (el.type === 'sequenceFlow') {
            rfEdges.push({
                id: el.id,
                source: el.sourceRef!,
                target: el.targetRef!,
                type: 'default',
                // 出边 CEL 条件透传(仅 Gateway 出边有);toRuleAsset 回写 conditionExpression
                data: el.conditionExpression ? {conditionExpression: el.conditionExpression} : undefined,
            });
            continue;
        }
        // implementation "NodeType:nodeId" 是权威节点类型来源(优于 BPMN 元素类型);
        // Gateway 无 implementation → 靠 BPMN type exclusiveGateway → 'Gateway'
        const impl = el.implementation || '';
        const colon = impl.indexOf(':');
        const nodeType = (colon > 0 ? impl.substring(0, colon) : NODE_FROM_BPMN[el.type]) as NodeType;
        const nodeId = colon > 0 ? impl.substring(colon + 1) : el.id;
        rfNodes.push({
            id: nodeId,
            type: 'v1',
            position: el.position ? {x: el.position.x, y: el.position.y} : {x: 100, y: 100},
            data: {
                nodeType,
                name: el.name || nodeType,
                implementation: impl || `${nodeType}:${nodeId}`,
                // Gateway default 兜底出边 id(exclusiveGateway.defaultFlow)
                ...(el.type === 'exclusiveGateway' && el.defaultFlow ? {defaultFlow: el.defaultFlow} : {}),
                // V7.5:规则节点从 nodesMap 还原 ruleRef(如果有)
                ...((nodeType === 'RuleSet' || nodeType === 'DecisionTable' || nodeType === 'ScoreCard') && nodesMap[nodeId]?.ruleRef ? {ruleRef: (nodesMap[nodeId] as {ruleRef?: string}).ruleRef} : {}),
            },
        });
    }

    return {
        nodes: rfNodes,
        edges: rfEdges,
        nodesMap,
        schemaName: asset.schema?.name || (nodesMap[Object.keys(nodesMap)[0]] as {schema?: string})?.schema || 'Fact',
    };
}
