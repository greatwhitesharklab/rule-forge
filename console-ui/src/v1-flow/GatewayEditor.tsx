import {Drawer, Input, Radio, Tag, Space, Typography, Divider} from 'antd';
import type {Edge, Node} from '@xyflow/react';
import {type V1NodeData} from './FlowNodes';

const {Text} = Typography;

/**
 * Gateway(exclusiveGateway)出边条件编辑器。
 *
 * <p>Gateway 是纯流程控制元素(非 nodes{} 业务节点),不进 NodePropertyDrawer。
 * 选中 Gateway 时本组件弹出:列出该 Gateway 的所有出边,逐条编辑 CEL
 * conditionExpression,并标记哪条是 default 兜底(exclusiveGateway.defaultFlow)。
 *
 * <p>语义对齐后端 V1FlowRunner:出边 CEL 首个命中即走;全不命中走 default;
 * default 边的 condition 通常留空(BPMN default flow 不看 condition)。
 */
export default function GatewayEditor({
    open, gatewayId, edges, nodes, defaultFlow, onUpdateEdge, onSetDefault, onClose,
}: {
    open: boolean;
    gatewayId: string | null;
    edges: Edge[];
    nodes: Node<V1NodeData>[];
    defaultFlow?: string;
    onUpdateEdge: (edgeId: string, condition: string) => void;
    onSetDefault: (edgeId: string) => void;
    onClose: () => void;
}) {
    const outEdges = gatewayId ? edges.filter((e) => e.source === gatewayId) : [];
    const targetName = (id: string) => nodes.find((n) => n.id === id)?.data.name || id;

    return (
        <Drawer
            title={<Space><Tag color='cyan'>Gateway</Tag><Text strong>出边分流条件</Text></Space>}
            open={open} onClose={onClose} size={560}
        >
            <Text type='secondary' style={{fontSize: 12}}>
                按出边 CEL 条件<b>首个命中</b>分流;全不命中走 <b>default</b> 兜底边。
                default 边的 condition 通常留空。
            </Text>
            <Divider style={{margin: '12px 0'}}/>
            {outEdges.length === 0 && (
                <Text type='secondary'>先在画布上从 Gateway 连出至少一条边。</Text>
            )}
            {outEdges.map((e) => {
                const cond = (e.data as {conditionExpression?: string} | undefined)?.conditionExpression || '';
                const isDefault = defaultFlow === e.id;
                return (
                    <div key={e.id} data-testid={`gw-edge-${e.id}`} style={{border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, marginBottom: 8}}>
                        <Space style={{width: '100%', justifyContent: 'space-between'}}>
                            <Tag color={isDefault ? 'orange' : 'blue'}>→ {targetName(e.target)}</Tag>
                            <Radio checked={isDefault} onChange={() => onSetDefault(e.id)}>default(兜底)</Radio>
                        </Space>
                        <Input
                            data-testid={`gw-cond-${e.id}`}
                            value={cond}
                            placeholder='CEL 条件,如 riskScore >= 50(default 边可空)'
                            onChange={(ev) => onUpdateEdge(e.id, ev.target.value)}
                            style={{marginTop: 6, fontFamily: 'monospace', fontSize: 12}}
                            disabled={isDefault}
                        />
                    </div>
                );
            })}
        </Drawer>
    );
}
