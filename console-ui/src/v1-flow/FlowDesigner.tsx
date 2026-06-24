import {useCallback, useState} from 'react';
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
import {Layout, Menu, Button, Drawer, Input, Space, Typography, theme} from 'antd';
import {nodeTypes, PALETTE, type V1NodeData} from './FlowNodes';
import {type RuleAsset, type FlowElement, type V1Node, type NodeType} from './ruleAsset';

const {Sider, Content, Header} = Layout;
const {Text} = Typography;

let idCounter = 1;
const genId = (prefix: string) => `${prefix}_${idCounter++}`;

/** ReactFlow node/edge → V1 RuleAsset。不存 ReactFlow JSON,只存自己的模型 + position。 */
function toRuleAsset(rfNodes: Node<V1NodeData>[], rfEdges: Edge[], schemaName: string): RuleAsset {
    const flowElements: FlowElement[] = [];
    const nodes: Record<string, V1Node> = {};
    const bpmnType: Record<NodeType, FlowElement['type']> = {
        Start: 'startEvent',
        RuleSet: 'serviceTask',
        DecisionTable: 'serviceTask',
        ScoreCard: 'serviceTask',
        Decision: 'endEvent',
    };
    for (const n of rfNodes) {
        const data = n.data as V1NodeData;
        flowElements.push({
            type: bpmnType[data.nodeType],
            id: n.id,
            name: data.name,
            position: {x: Math.round(n.position.x), y: Math.round(n.position.y)},
            implementation: `${data.nodeType}:${n.id}`,
        });
        nodes[n.id] = newNodeDefault(data.nodeType, n.id, data.name, schemaName);
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

function newNodeDefault(type: NodeType, id: string, name: string, schemaName: string): V1Node {
    switch (type) {
        case 'Start':
            return {id, type: 'Start', name, schema: schemaName};
        case 'RuleSet':
            return {id, type: 'RuleSet', name, hitPolicy: 'FIRST_MATCH', rules: []};
        case 'DecisionTable':
            return {id, type: 'DecisionTable', name, hitPolicy: 'FIRST', inputs: [], outputs: [], rows: []};
        case 'ScoreCard':
            return {id, type: 'ScoreCard', name, output: 'score', aggregation: 'SUM', cards: []};
        case 'Decision':
            return {id, type: 'Decision', name, outputs: ['approve', 'review', 'reject'], decisionField: 'decision', defaultOutput: 'review'};
    }
}

export default function FlowDesigner() {
    const {token} = theme.useToken();
    const [nodes, setNodes, onNodesChange] = useNodesState<Node<V1NodeData>>([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
    const [schemaName, setSchemaName] = useState('LoanApplication');
    const [exportOpen, setExportOpen] = useState(false);
    const [exported, setExported] = useState('');

    const onConnect = useCallback(
        (params: Connection) => setEdges((eds: Edge[]) => addEdge({...params, markerEnd: {type: MarkerType.ArrowClosed}} as Edge, eds)),
        [setEdges],
    );

    const addNode = useCallback(
        (type: NodeType) => {
            const id = genId(type.toLowerCase());
            const newNode: Node<V1NodeData> = {
                id,
                type: 'v1',
                position: {x: 120 + Math.random() * 200, y: 100 + nodes.length * 110},
                data: {nodeType: type, name: type, implementation: `${type}:${id}`},
            };
            setNodes((nds: Node<V1NodeData>[]) => nds.concat(newNode));
        },
        [nodes.length, setNodes],
    );

    const exportJson = useCallback(() => {
        setExported(JSON.stringify(toRuleAsset(nodes, edges, schemaName), null, 2));
        setExportOpen(true);
    }, [nodes, edges, schemaName]);

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
                    <Menu
                        mode='inline'
                        style={{borderInlineEnd: 'none', marginTop: 8}}
                        items={PALETTE.map((t) => ({key: t, label: `+ ${t}`}))}
                        onClick={({key}) => addNode(key as NodeType)}
                    />
                    <Text type='secondary' style={{fontSize: 11, display: 'block', marginTop: 16}}>
                        点节点加入画布;从节点底部圆点拖到下一节点顶部圆点连线。
                    </Text>
                </Sider>
                <Content style={{position: 'relative'}}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
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
            <Drawer title='RuleAsset JSON(后端可执行)' open={exportOpen} onClose={() => setExportOpen(false)} width={520}>
                <pre data-testid='v1-export' style={{fontSize: 11, fontFamily: 'monospace', whiteSpace: 'pre-wrap'}}>{exported}</pre>
            </Drawer>
        </Layout>
    );
}
