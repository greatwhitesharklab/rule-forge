import {Handle, Position} from '@xyflow/react';
import type {NodeType} from './ruleAsset';

/** V1 节点 BPMN 子集图标 + 配色。5 业务节点 + 1 流程控制节点(Gateway/exclusiveGateway)。
 *  Gateway 是 BPMN flow element(非 nodes{} 业务节点),画布上跟业务节点同渲染。 */
const NODE_STYLE: Record<NodeType, {icon: string; color: string; label: string}> = {
    Start: {icon: '○', color: '#52c41a', label: 'Start'},
    RuleSet: {icon: '⚖', color: '#1677ff', label: 'RuleSet'},
    DecisionTable: {icon: '▦', color: '#722ed1', label: 'DecisionTable'},
    ScoreCard: {icon: '★', color: '#fa8c16', label: 'ScoreCard'},
    Decision: {icon: '◎', color: '#eb2f96', label: 'Decision'},
    Gateway: {icon: '◇', color: '#13c2c2', label: 'Gateway'},
};

export interface V1NodeData {
    nodeType: NodeType;
    name: string;
    implementation: string;
    /** Gateway default 出边 id(exclusiveGateway.defaultFlow);仅 Gateway 用。 */
    defaultFlow?: string;
    /** V7.5:规则独立文件引用(RuleSet/DecisionTable/ScoreCard)。有值 → 点节点跳独立编辑器,不弹 Drawer。 */
    ruleRef?: string;
    [key: string]: unknown;
}

export function V1FlowNode({data}: {data: V1NodeData}) {
    const style = NODE_STYLE[data.nodeType];
    const isStart = data.nodeType === 'Start';
    const isEnd = data.nodeType === 'Decision';
    const isGateway = data.nodeType === 'Gateway';
    return (
        <div
            data-testid={`v1-node-${data.nodeType}`}
            style={{
                padding: '10px 16px',
                // Gateway 菱形(旋转 45° + 内容反向旋转);其余圆角矩形
                borderRadius: isStart || isEnd ? 40 : 8,
                border: `2px solid ${style.color}`,
                borderWidth: isGateway ? 3 : 2,
                background: '#fff',
                minWidth: 120,
                textAlign: 'center',
                fontSize: 13,
                boxShadow: '0 2px 6px rgba(0,0,0,0.1)',
            }}
        >
            {!isStart && <Handle type='target' position={Position.Top} style={{background: style.color}}/>}
            <div style={{fontWeight: 600, color: style.color}}>
                {style.icon} {style.label}
            </div>
            <div style={{marginTop: 4, color: '#333'}}>{data.name}</div>
            {!isEnd && <Handle type='source' position={Position.Bottom} style={{background: style.color}}/>}
        </div>
    );
}

export const nodeTypes = {v1: V1FlowNode};

/** palette(6 种:5 业务节点 + Gateway 分流)。 */
export const PALETTE: NodeType[] = ['Start', 'RuleSet', 'DecisionTable', 'ScoreCard', 'Gateway', 'Decision'];
