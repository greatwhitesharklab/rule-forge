import {useCallback, useMemo, useState} from 'react';
import {ReactFlow, Background, Controls, MiniMap, addEdge, useNodesState, useEdgesState, MarkerType, type Node, type Edge, type Connection} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {nodeTypes, PALETTE, type V1NodeData} from './nodes';
import {type RuleAsset, type FlowElement, type V1Node, type NodeType} from './ruleAsset';

let idCounter = 1;
const genId = (prefix: string) => `${prefix}_${idCounter++}`;

/** ReactFlow node → flow element + V1 node。不存 ReactFlow JSON,只存自己的模型 + position。 */
function toRuleAsset(
  rfNodes: Node<V1NodeData>[],
  rfEdges: Edge[],
  schemaName: string,
): RuleAsset {
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
    const fe: FlowElement = {
      type: bpmnType[data.nodeType],
      id: n.id,
      name: data.name,
      position: {x: Math.round(n.position.x), y: Math.round(n.position.y)},
      implementation: `${data.nodeType}:${n.id}`,
    };
    flowElements.push(fe);
    nodes[n.id] = newNodeDefault(data.nodeType, n.id, data.name, schemaName);
  }
  for (const e of rfEdges) {
    flowElements.push({
      type: 'sequenceFlow',
      id: e.id,
      sourceRef: e.source,
      targetRef: e.target,
    });
  }
  return {
    version: '1.0',
    id: genId('asset'),
    name: 'Untitled Flow',
    flow: {id: genId('flow'), name: 'Flow', version: '1.0', flowElements},
    nodes,
  };
}

/** 新建 V1 节点的最小默认内容(前端占位,实际内容由属性面板编辑)。 */
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

export default function FlowEditor() {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<V1NodeData>>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [schemaName, setSchemaName] = useState('LoanApplication');
  const [exported, setExported] = useState<string>('');

  const onConnect = useCallback((params: Connection) => setEdges((eds: Edge[]) => addEdge({...params, markerEnd: {type: MarkerType.ArrowClosed}} as Edge, eds)), [setEdges]);

  /** 从 palette 添加节点到画布。 */
  const addNode = useCallback((type: NodeType) => {
    const id = genId(type.toLowerCase());
    const newNode: Node<V1NodeData> = {
      id,
      type: 'v1',
      position: {x: 120 + Math.random() * 200, y: 100 + nodes.length * 110},
      data: {nodeType: type, name: type, implementation: `${type}:${id}`},
    };
    setNodes((nds: Node<V1NodeData>[]) => nds.concat(newNode));
  }, [nodes.length, setNodes]);

  const exportJson = useCallback(() => {
    const asset = toRuleAsset(nodes, edges, schemaName);
    setExported(JSON.stringify(asset, null, 2));
  }, [nodes, edges, schemaName]);

  const palette = useMemo(() => PALETTE.map((t) => (
    <button key={t} onClick={() => addNode(t)} style={paletteBtn}>+ {t}</button>
  )), [addNode]);

  return (
    <div style={{display: 'flex', height: '100vh', fontFamily: 'system-ui, sans-serif'}}>
      <div style={{width: 180, padding: 12, borderRight: '1px solid #eee', background: '#fafafa'}}>
        <h3 style={{fontSize: 14, marginTop: 0}}>RuleForge Studio</h3>
        <label style={{fontSize: 12, color: '#666'}}>Schema 名</label>
        <input value={schemaName} onChange={(e) => setSchemaName(e.target.value)} style={{width: '100%', marginBottom: 12, padding: 4}}/>
        <div style={{fontSize: 12, color: '#999', marginBottom: 6}}>节点(MVP 5 种)</div>
        <div style={{display: 'flex', flexDirection: 'column', gap: 6}}>{palette}</div>
        <button onClick={exportJson} style={{...paletteBtn, marginTop: 16, background: '#1677ff', color: '#fff'}}>导出 .json</button>
      </div>
      <div style={{flex: 1, position: 'relative'}}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          nodeTypes={nodeTypes}
          fitView
          data-testid="v1-canvas"
        >
          <Background/>
          <Controls/>
          <MiniMap/>
        </ReactFlow>
      </div>
      {exported && (
        <div data-testid="v1-export" style={{width: 420, borderLeft: '1px solid #eee', padding: 12, overflow: 'auto', fontSize: 11, fontFamily: 'monospace', background: '#fafafa'}}>
          <button onClick={() => setExported('')} style={{float: 'right'}}>✕</button>
          <pre>{exported}</pre>
        </div>
      )}
    </div>
  );
}

const paletteBtn: React.CSSProperties = {
  padding: '6px 10px',
  border: '1px solid #d9d9d9',
  background: '#fff',
  borderRadius: 4,
  cursor: 'pointer',
  textAlign: 'left',
  fontSize: 13,
};
