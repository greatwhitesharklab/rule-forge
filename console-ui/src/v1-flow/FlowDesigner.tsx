import {useCallback, useState, useMemo} from 'react';
import {
    ReactFlow,
    Background,
    Controls,
    MiniMap,
    addEdge,
    useNodesState,
    useEdgesState,
    MarkerType,
    type Node,
    type Edge,
    type Connection,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {Layout, Menu, Button, Space, Typography, Input, Drawer, theme} from 'antd';
import {nodeTypes, PALETTE, type V1NodeData} from './FlowNodes';
import {type RuleAsset, type FlowElement, type V1Node, type NodeType} from './ruleAsset';
import NodePropertyDrawer from './NodePropertyDrawer';

const {Sider, Content, Header} = Layout;
const {Text} = Typography;

let idCounter = 1;
const genId = (prefix: string) => `${prefix}_${idCounter++}`;

function newNodeDefault(type: NodeType, id: string, schemaName: string): V1Node {
    switch (type) {
        case 'Start':
            return {id, type: 'Start', name: 'Start', schema: schemaName};
        case 'RuleSet':
            return {id, type: 'RuleSet', name: 'RuleSet', hitPolicy: 'FIRST_MATCH', rules: []};
        case 'DecisionTable':
            return {id, type: 'DecisionTable', name: 'DecisionTable', hitPolicy: 'FIRST', inputs: [], outputs: [], rows: []};
        case 'ScoreCard':
            return {id, type: 'ScoreCard', name: 'ScoreCard', output: 'score', aggregation: 'SUM', cards: []};
        case 'Decision':
            return {id, type: 'Decision', name: 'Decision', outputs: ['approve', 'review', 'reject'], decisionField: 'decision', defaultOutput: 'review'};
    }
}

/** canvas nodes/edges + 节点内容 map → V1 RuleAsset。不存 ReactFlow JSON。 */
function toRuleAsset(
    rfNodes: Node<V1NodeData>[],
    rfEdges: Edge[],
    nodesMap: Record<string, V1Node>,
    schemaName: string,
): RuleAsset {
    const flowElements: FlowElement[] = [];
    const bpmnType: Record<NodeType, FlowElement['type']> = {
        Start: 'startEvent',
        RuleSet: 'serviceTask',
        DecisionTable: 'serviceTask',
        ScoreCard: 'serviceTask',
        Decision: 'endEvent',
    };
    const nodes: Record<string, V1Node> = {};
    for (const n of rfNodes) {
        const data = n.data as V1NodeData;
        flowElements.push({
            type: bpmnType[data.nodeType],
            id: n.id,
            name: data.name,
            position: {x: Math.round(n.position.x), y: Math.round(n.position.y)},
            implementation: `${data.nodeType}:${n.id}`,
        });
        // 用 nodesMap 的完整内容(已被 Drawer 编辑过),fallback 到默认
        nodes[n.id] = nodesMap[n.id] || newNodeDefault(data.nodeType, n.id, schemaName);
    }
    for (const e of rfEdges) {
        flowElements.push({type: 'sequenceFlow', id: e.id, sourceRef: e.source, targetRef: e.target});
    }
    return {
        version: '1.0',
        id: genId('asset'),
        name: 'Untitled Flow',
        flow: {id: genId('flow'), name: 'Flow', version: '1.0', flowElements},
        nodes,
    };
}

export default function FlowDesigner() {
    const {token} = theme.useToken();
    const [nodes, setNodes, onNodesChange] = useNodesState<Node<V1NodeData>>([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
    const [schemaName, setSchemaName] = useState('LoanApplication');
    const [nodesMap, setNodesMap] = useState<Record<string, V1Node>>({});
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [exportOpen, setExportOpen] = useState(false);
    const [exported, setExported] = useState('');

    const onConnect = useCallback(
        (params: Connection) => setEdges((eds: Edge[]) => addEdge({...params, markerEnd: {type: MarkerType.ArrowClosed}} as Edge, eds)),
        [setEdges],
    );

    const addNode = useCallback(
        (type: NodeType) => {
            const id = genId(type.toLowerCase());
            const node: Node<V1NodeData> = {
                id,
                type: 'v1',
                position: {x: 120 + Math.random() * 200, y: 100 + nodes.length * 110},
                data: {nodeType: type, name: type, implementation: `${type}:${id}`},
            };
            setNodes((nds: Node<V1NodeData>[]) => nds.concat(node));
            setNodesMap((m) => ({...m, [id]: newNodeDefault(type, id, schemaName)}));
        },
        [nodes.length, setNodes, schemaName],
    );

    /** Drawer 改节点内容 → 回写 nodesMap + 同步画布节点显示名。 */
    const onNodeChange = useCallback((updated: V1Node) => {
        setNodesMap((m) => ({...m, [updated.id]: updated}));
        setNodes((nds) => nds.map((n) => n.id === updated.id ? {...n, data: {...n.data, name: updated.name}} : n));
    }, [setNodes]);

    const exportJson = useCallback(() => {
        setExported(JSON.stringify(toRuleAsset(nodes, edges, nodesMap, schemaName), null, 2));
        setExportOpen(true);
    }, [nodes, edges, nodesMap, schemaName]);

    const selectedNode = selectedId ? nodesMap[selectedId] : null;

    const palette = useMemo(() => PALETTE.map((t) => ({key: t, label: `+ ${t}`})), []);

    return (
        <Layout style={{height: '100vh'}}>
            <Header style={{background: token.colorBgContainer, padding: '0 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                <Text strong style={{fontSize: 16}}>RuleForge · V1 决策流设计器</Text>
                <Space>
                    <Text type='secondary' style={{fontSize: 12}}>Schema:</Text>
                    <Input size='small' value={schemaName} onChange={(e) => setSchemaName(e.target.value)} style={{width: 160}}/>
                    <Button size='small' type='primary' onClick={exportJson}>导出 .json</Button>
                </Space>
            </Header>
            <Layout>
                <Sider width={200} style={{background: token.colorBgContainer, padding: 12}}>
                    <Text type='secondary' style={{fontSize: 12}}>节点(MVP 5 种)</Text>
                    <Menu mode='inline' style={{borderInlineEnd: 'none', marginTop: 8}} items={palette} onClick={({key}) => addNode(key as NodeType)}/>
                    <Text type='secondary' style={{fontSize: 11, display: 'block', marginTop: 16}}>
                        点节点加入画布 → 连线 → 点节点弹属性 Drawer 编辑内容 → 导出。
                    </Text>
                </Sider>
                <Content style={{position: 'relative'}}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
                        onNodeClick={(_, n) => setSelectedId(n.id)}
                        nodeTypes={nodeTypes}
                        fitView
                        data-testid='v1-canvas'
                    >
                        <Background/>
                        <Controls/>
                        <MiniMap/>
                    </ReactFlow>
                </Content>
            </Layout>
            <NodePropertyDrawer
                node={selectedNode}
                open={selectedId !== null}
                onClose={() => setSelectedId(null)}
                onChange={onNodeChange}
            />
            <Drawer title='RuleAsset JSON(后端可执行)' open={exportOpen} onClose={() => setExportOpen(false)} width={520}>
                <pre data-testid='v1-export' style={{fontSize: 11, fontFamily: 'monospace', whiteSpace: 'pre-wrap'}}>{exported}</pre>
            </Drawer>
        </Layout>
    );
}
