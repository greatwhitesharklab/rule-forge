import type {Node, Edge} from '@xyflow/react';
import type {RuleAsset, V1Node, NodeType} from './ruleAsset';
import type {V1NodeData} from './FlowNodes';

/**
 * RuleAsset JSON → canvas state(反向序列化,跟 FlowDesigner.toRuleAsset 对偶)。
 *
 * <p>解析 flow.flowElements → ReactFlow nodes/edges;nodes Map → nodesMap。
 * 让"导入已有 .json → 在画布编辑 → 导出"client-side round-trip 闭环。
 *
 * <p>BPMN 元素类型 → V1 节点类型(implementation "NodeType:nodeId" 是权威来源)。
 */
const NODE_FROM_BPMN: Record<string, NodeType> = {
    startEvent: 'Start',
    serviceTask: 'RuleSet', // serviceTask 的具体类型靠 implementation 前缀
    endEvent: 'Decision',
    exclusiveGateway: 'Decision', // gateway MVP 当 Decision 占位(实际 V1 完整版才用)
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
            rfEdges.push({id: el.id, source: el.sourceRef!, target: el.targetRef!, type: 'default'});
            continue;
        }
        // implementation "NodeType:nodeId" 是权威节点类型来源(优于 BPMN 元素类型)
        const impl = el.implementation || '';
        const colon = impl.indexOf(':');
        const nodeType = (colon > 0 ? impl.substring(0, colon) : NODE_FROM_BPMN[el.type]) as NodeType;
        const nodeId = colon > 0 ? impl.substring(colon + 1) : el.id;
        rfNodes.push({
            id: nodeId,
            type: 'v1',
            position: el.position ? {x: el.position.x, y: el.position.y} : {x: 100, y: 100},
            data: {nodeType, name: el.name || nodeType, implementation: impl || `${nodeType}:${nodeId}`},
        });
    }

    return {
        nodes: rfNodes,
        edges: rfEdges,
        nodesMap,
        schemaName: asset.schema?.name || (nodesMap[Object.keys(nodesMap)[0]] as {schema?: string})?.schema || 'Fact',
    };
}
