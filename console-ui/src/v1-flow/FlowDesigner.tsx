import {useCallback, useState, useMemo, useEffect, useRef} from 'react';
import {
    ReactFlow,
    ReactFlowProvider,
    Background,
    Controls,
    MiniMap,
    addEdge,
    useNodesState,
    useEdgesState,
    useReactFlow,
    MarkerType,
    type Node,
    type Edge,
    type Connection,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {Layout, Menu, Button, Space, Typography, Input, Drawer, Modal, message, theme, AutoComplete} from 'antd';
import {UploadOutlined, SaveOutlined, FolderOpenOutlined, CloudUploadOutlined, UndoOutlined, RedoOutlined, PartitionOutlined} from '@ant-design/icons';
import {formPost, jsonPost, httpGet} from '@/api/client';
import {openEditorTab} from '@/frame/event';
import type {EditorType} from '@/frame/editorTypeMap';
import {nodeTypes, PALETTE, NODE_LABELS, type V1NodeData} from './FlowNodes';
import {type RuleAsset, type FlowElement, type V1Node, type NodeType} from './ruleAsset';
import NodePropertyDrawer from './NodePropertyDrawer';
import GatewayEditor from './GatewayEditor';
import {fromRuleAsset} from './fromRuleAsset';
import {validateRuleAsset, type ValidationIssue} from './validation';
import {autoLayout} from './layout';
import {searchNodes} from './search';

const {Sider, Content, Header} = Layout;
const {Text} = Typography;

let idCounter = 1;
const genId = (prefix: string) => `${prefix}_${idCounter++}`;

/** V1 执行端点响应(POST /v1/execute)。 */
interface V1ExecutionResponse {
    decision: string;
    rejected: boolean;
    rejectReason: string | null;
    flags: unknown[];
    fact: Record<string, unknown>;
}

/** 5 业务节点的默认内容。Gateway 不走(它是 flow element,非 nodes{} 业务节点)。 */
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
        default:
            throw new Error(`Gateway 是 flow element(非 nodes{} 业务节点),不走 newNodeDefault`);
    }
}

/** canvas nodes/edges + 节点内容 map → V1 RuleAsset。不存 ReactFlow JSON。
 *  Gateway(exclusiveGateway)是纯流程元素:无 implementation,不进 nodes{},
 *  带 defaultFlow(兜底出边 id);出边 conditionExpression 从 edge.data 回写。 */
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
        Gateway: 'exclusiveGateway',
    };
    const nodes: Record<string, V1Node> = {};
    for (const n of rfNodes) {
        const data = n.data as V1NodeData;
        const isGateway = data.nodeType === 'Gateway';
        const fe: FlowElement = {
            type: bpmnType[data.nodeType],
            id: n.id,
            name: data.name,
            position: {x: Math.round(n.position.x), y: Math.round(n.position.y)},
        };
        if (isGateway) {
            if (data.defaultFlow) {
                fe.defaultFlow = data.defaultFlow;
            }
        } else {
            fe.implementation = `${data.nodeType}:${n.id}`;
            // V7.5:规则节点(RuleSet/DecisionTable/ScoreCard)有 ruleRef → 只存 {type, name, ruleRef},不内嵌规则
            const hasRuleRef = data.ruleRef && data.ruleRef.trim() !== '';
            if (hasRuleRef && (data.nodeType === 'RuleSet' || data.nodeType === 'DecisionTable' || data.nodeType === 'ScoreCard')) {
                nodes[n.id] = {id: n.id, type: data.nodeType, name: data.name, ruleRef: data.ruleRef} as V1Node;
            } else {
                // 用 nodesMap 的完整内容(已被 Drawer 编辑过),fallback 到默认
                nodes[n.id] = nodesMap[n.id] || newNodeDefault(data.nodeType, n.id, schemaName);
            }
        }
        flowElements.push(fe);
    }
    for (const e of rfEdges) {
        const fe: FlowElement = {type: 'sequenceFlow', id: e.id, sourceRef: e.source, targetRef: e.target};
        const cond = (e.data as {conditionExpression?: string} | undefined)?.conditionExpression;
        if (cond) {
            fe.conditionExpression = cond;
        }
        flowElements.push(fe);
    }
    return {
        version: '1.0',
        id: genId('asset'),
        name: 'Untitled Flow',
        flow: {id: genId('flow'), name: 'Flow', version: '1.0', flowElements},
        nodes,
    };
}

function FlowDesignerInner({file}: {file?: string}) {
    const {token} = theme.useToken();
    const [nodes, setNodes, onNodesChange] = useNodesState<Node<V1NodeData>>([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
    const [schemaName, setSchemaName] = useState('LoanApplication');
    const [nodesMap, setNodesMap] = useState<Record<string, V1Node>>({});
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [exportOpen, setExportOpen] = useState(false);
    const [exported, setExported] = useState('');
    const [importOpen, setImportOpen] = useState(false);
    const [importText, setImportText] = useState('');
    const [filePath, setFilePath] = useState(file || '');
    const [runOpen, setRunOpen] = useState(false);
    const [runFact, setRunFact] = useState('{\n  "age": 30\n}');
    const [runResult, setRunResult] = useState<V1ExecutionResponse | null>(null);
    /** V7.15 节点搜索 query + 跳转。fitView 来自 useReactFlow(需 ReactFlowProvider 包裹,见 return)。 */
    const [searchQuery, setSearchQuery] = useState('');
    const {fitView} = useReactFlow();
    const gotoNode = useCallback((id: string) => {
        setSearchQuery('');
        fitView({nodes: [{id}], duration: 400, padding: 0.2, maxZoom: 1.2});
        setSelectedId(id);
    }, [fitView]);
    /** V7.6:当前决策流的已发布版本号(null = 草稿/未发布)。加载/发布后刷新。 */
    const [publishedVersion, setPublishedVersion] = useState<string | null>(null);

    /** V7.6:刷新决策流的已发布状态(GET /v1/publish/status → publishedVersion)。 */
    const refreshPublishStatus = useCallback((flow: string) => {
        if (!flow) {
            setPublishedVersion(null);
            return;
        }
        httpGet<{ status: string; currentVersion: string | null }>('/v1/publish/status?flow=' + encodeURIComponent(flow))
            .then((res) => setPublishedVersion(res.status === 'published' ? res.currentVersion : null))
            .catch(() => setPublishedVersion(null));
    }, []);

    /** V7.6:发布决策流(POST /v1/publish → 后端冻结闭包 bundle + git tag)。V7.11 发布前 gate 校验(error 禁发)。 */
    const publishFlow = useCallback(() => {
        if (!filePath) {
            message.error('先加载或输入决策流路径(顶部路径框)');
            return;
        }
        const issues = runValidation();
        const errors = issues.filter((i) => i.level === 'error');
        if (errors.length > 0) {
            message.error(`校验未通过:${errors.length} 个 error(点"校验"按钮查看详情)`);
            return;
        }
        formPost<{ version: string; status: string }>('/v1/publish', {flow: filePath})
            .then((res) => {
                setPublishedVersion(res.version);
                message.success(`已发布 v${res.version}`);
            })
            .catch(() => message.error('发布失败(后端未运行/未登录,或闭包解析失败 — 检查 ruleRef/库文件)'));
    }, [filePath]);

    /** V7.11 校验状态 + 弹窗控制。 */
    const [validationOpen, setValidationOpen] = useState(false);
    const [validationIssues, setValidationIssues] = useState<ValidationIssue[]>([]);

    /** 跑画布校验 → 写 issues + 开 Modal。返回 issues(供 publish gate)。 */
    const runValidation = useCallback((): ValidationIssue[] => {
        const asset = toRuleAsset(nodes, edges, nodesMap, schemaName);
        const issues = validateRuleAsset(asset);
        setValidationIssues(issues);
        setValidationOpen(true);
        return issues;
    }, [nodes, edges, nodesMap, schemaName]);

    /** V7.13 undo/redo 栈 — 粗粒度 mutation 推快照(addNode/connect/drag/delete/layout)。
     *  抽屉实时编辑(每键)不推 → 抽屉 undo 留未来;抽屉内 Input 浏览器原生 Ctrl+Z 兜底。 */
    const MAX_HISTORY = 50;
    type Snapshot = {nodes: typeof nodes; edges: typeof edges; nodesMap: typeof nodesMap; schemaName: string};
    const [history, setHistory] = useState<Snapshot[]>([]);
    const [historyIndex, setHistoryIndex] = useState(-1);

    /** 推当前 state 到历史(在 mutation 前调)。truncate redo + 限长。 */
    const pushHistory = useCallback(() => {
        setHistory((h) => {
            const base = h.slice(0, historyIndex + 1);
            const snap: Snapshot = {nodes, edges, nodesMap, schemaName};
            const next = [...base, snap];
            return next.length > MAX_HISTORY ? next.slice(next.length - MAX_HISTORY) : next;
        });
        setHistoryIndex((i) => Math.min(i + 1, MAX_HISTORY - 1));
    }, [historyIndex, nodes, edges, nodesMap, schemaName]);

    /** 加载/导入新流 → 重置 history(防止跨流 undo 出怪状态)。 */
    const resetHistory = useCallback(() => {
        setHistory([]);
        setHistoryIndex(-1);
    }, []);

    const undo = useCallback(() => {
        if (historyIndex <= 0) return;
        const snap = history[historyIndex - 1];
        setNodes(snap.nodes); setEdges(snap.edges); setNodesMap(snap.nodesMap); setSchemaName(snap.schemaName);
        setHistoryIndex(historyIndex - 1);
    }, [history, historyIndex, setNodes, setEdges, setNodesMap, setSchemaName]);

    const redo = useCallback(() => {
        if (historyIndex < 0 || historyIndex >= history.length - 1) return;
        const snap = history[historyIndex + 1];
        setNodes(snap.nodes); setEdges(snap.edges); setNodesMap(snap.nodesMap); setSchemaName(snap.schemaName);
        setHistoryIndex(historyIndex + 1);
    }, [history, historyIndex, setNodes, setEdges, setNodesMap, setSchemaName]);

    /** V7.13 dagre 自动布局 → 重置所有节点 position。 */
    const runAutoLayout = useCallback(() => {
        pushHistory();
        setNodes((nds) => autoLayout(nds, edges) as Node<V1NodeData>[]);
    }, [pushHistory, edges, setNodes]);

    /** V7.13 drag start → 推 pre-drag 快照(让 undo 回到拖前位置)。 */
    const onNodeDragStart = useCallback(() => {
        pushHistory();
    }, [pushHistory]);

    /** V7.13 节点删除 → 重建 pre-delete 快照(把 deleted 节点插回当前 nodes 推入 history)。 */
    const onNodesDelete = useCallback((deleted: Node[]) => {
        if (!deleted || deleted.length === 0) return;
        const restored = [...nodes, ...deleted] as Node<V1NodeData>[];
        setHistory((h) => {
            const base = h.slice(0, historyIndex + 1);
            const next = [...base, {nodes: restored, edges, nodesMap, schemaName}];
            return next.length > MAX_HISTORY ? next.slice(next.length - MAX_HISTORY) : next;
        });
        setHistoryIndex((i) => Math.min(i + 1, MAX_HISTORY - 1));
    }, [nodes, edges, nodesMap, schemaName, historyIndex]);

    /** V7.13 边删除 → 重建 pre-delete 快照。 */
    const onEdgesDelete = useCallback((deleted: Edge[]) => {
        if (!deleted || deleted.length === 0) return;
        const restored = [...edges, ...deleted];
        setHistory((h) => {
            const base = h.slice(0, historyIndex + 1);
            const next = [...base, {nodes, edges: restored, nodesMap, schemaName}];
            return next.length > MAX_HISTORY ? next.slice(next.length - MAX_HISTORY) : next;
        });
        setHistoryIndex((i) => Math.min(i + 1, MAX_HISTORY - 1));
    }, [nodes, edges, nodesMap, schemaName, historyIndex]);

    /** V7.14 抽屉整段 undo:打开时 snapshot pre-state,关闭时若 dirty 把 pre-state 推 history
     *  (整段抽屉编辑 = 1 个 undo 步;抽屉内 Input Ctrl+Z 仍由浏览器原生处理文本级 undo)。 */
    useEffect(() => {
        const prev = prevSelectedIdRef.current;
        if (prev === null && selectedId !== null) {
            preDrawerSnapshotRef.current = {nodes, edges, nodesMap, schemaName};
            drawerDirtyRef.current = false;
        } else if (prev !== null && selectedId === null && drawerDirtyRef.current && preDrawerSnapshotRef.current) {
            const snap = preDrawerSnapshotRef.current;
            setHistory((h) => {
                const base = h.slice(0, historyIndex + 1);
                const next = [...base, snap];
                return next.length > MAX_HISTORY ? next.slice(next.length - MAX_HISTORY) : next;
            });
            setHistoryIndex((i) => Math.min(i + 1, MAX_HISTORY - 1));
            preDrawerSnapshotRef.current = null;
            drawerDirtyRef.current = false;
            // V7.17 字段级:关闭时重置 burst flag + 清理 timer(下段从新 push)
            fieldUndoPushedRef.current = false;
            if (fieldUndoTimerRef.current) {
                clearTimeout(fieldUndoTimerRef.current);
                fieldUndoTimerRef.current = null;
            }
        }
        prevSelectedIdRef.current = selectedId;
    }, [selectedId, nodes, edges, nodesMap, schemaName, historyIndex]);

    /** V7.14 全局键盘 Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z → undo/redo。input/textarea/contenteditable 内交给浏览器原生。 */
    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if (!(e.ctrlKey || e.metaKey)) return;
            const t = e.target as HTMLElement | null;
            if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || (t as any).isContentEditable)) {
                return;
            }
            const key = e.key.toLowerCase();
            if (key === 'z' && !e.shiftKey) {
                e.preventDefault();
                undo();
            } else if ((key === 'z' && e.shiftKey) || key === 'y') {
                e.preventDefault();
                redo();
            }
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [undo, redo]);

    /** 挂载时若有 file(从项目树进入),按 file 加载 RuleAsset → 画布。V7.13:加载新流重置 history。 */
    useEffect(() => {
        if (!file) return;
        setFilePath(file);
        formPost<{content: string}>('/frame/fileSource', {path: file})
            .then((res) => {
                const asset = JSON.parse(res.content) as RuleAsset;
                const st = fromRuleAsset(asset);
                setNodes(st.nodes); setEdges(st.edges); setNodesMap(st.nodesMap); setSchemaName(st.schemaName);
                resetHistory();
                refreshPublishStatus(file);
                message.success(`加载 ${file}:${st.nodes.length} 节点`);
            })
            .catch(() => message.error('加载失败(后端未运行或文件不存在)'));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [file]);

    const onConnect = useCallback(
        (params: Connection) => {
            pushHistory();
            setEdges((eds: Edge[]) => addEdge({...params, markerEnd: {type: MarkerType.ArrowClosed}} as Edge, eds));
        },
        [pushHistory, setEdges],
    );

    const addNode = useCallback(
        (type: NodeType) => {
            const id = genId(type.toLowerCase());
            const isGateway = type === 'Gateway';
            const node: Node<V1NodeData> = {
                id,
                type: 'v1',
                position: {x: 120 + Math.random() * 200, y: 100 + nodes.length * 110},
                data: {nodeType: type, name: type, implementation: isGateway ? '' : `${type}:${id}`},
            };
            pushHistory();
            setNodes((nds: Node<V1NodeData>[]) => nds.concat(node));
            // Gateway 是 flow element,不进 nodesMap(无业务定义);其余 5 业务节点建默认内容
            if (!isGateway) {
                setNodesMap((m) => ({...m, [id]: newNodeDefault(type, id, schemaName)}));
            }
        },
        [pushHistory, nodes.length, setNodes, schemaName],
    );

    /** Drawer 改节点内容 → 回写 nodesMap + 同步画布节点显示名。
     *  V7.14: drawerDirty 让关闭时推整段 undo。
     *  V7.17: 字段级 debounce undo — 同一段连续编辑(每键) = 1 个 undo 步;
     *  每段首键 pushHistory(pre-burst),静默 600ms 后重置 flag(下段再 push)。 */
    const onNodeChange = useCallback((updated: V1Node) => {
        setNodesMap((m) => ({...m, [updated.id]: updated}));
        setNodes((nds) => nds.map((n) => n.id === updated.id ? {...n, data: {...n.data, name: updated.name}} : n));
        drawerDirtyRef.current = true;
        if (!fieldUndoPushedRef.current) {
            pushHistory();
            fieldUndoPushedRef.current = true;
        }
        if (fieldUndoTimerRef.current) clearTimeout(fieldUndoTimerRef.current);
        fieldUndoTimerRef.current = setTimeout(() => { fieldUndoPushedRef.current = false; }, 600);
    }, [pushHistory, setNodes]);

    /** V7.14 抽屉整段 undo refs(打开 snapshot / 关闭时若 dirty 推 history)。V7.17 加字段级 debounce refs。
     *  useEffect 放 history 块后避免 TDZ。 */
    const preDrawerSnapshotRef = useRef<Snapshot | null>(null);
    const drawerDirtyRef = useRef(false);
    const prevSelectedIdRef = useRef<string | null>(null);
    const fieldUndoPushedRef = useRef(false);
    const fieldUndoTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const exportJson = useCallback(() => {
        setExported(JSON.stringify(toRuleAsset(nodes, edges, nodesMap, schemaName), null, 2));
        setExportOpen(true);
    }, [nodes, edges, nodesMap, schemaName]);

    // 选中 Gateway(flow element)→ 弹 GatewayEditor 编辑出边条件;业务节点 → NodePropertyDrawer
    const selectedRfNode = selectedId ? nodes.find((n) => n.id === selectedId) : null;
    const isGatewaySelected = selectedRfNode?.data.nodeType === 'Gateway';
    const selectedNode = !isGatewaySelected && selectedId && nodesMap[selectedId] ? nodesMap[selectedId] : null;

    /** Gateway 出边 CEL 条件编辑 → 回写 edge.data.conditionExpression。 */
    const updateEdgeCondition = useCallback((edgeId: string, condition: string) => {
        setEdges((es) => es.map((e) => e.id === edgeId
            ? {...e, data: {...(e.data as Record<string, unknown> | undefined), conditionExpression: condition}}
            : e));
    }, [setEdges]);
    /** 设 Gateway default 兜底出边 → 回写 rfNode.data.defaultFlow。 */
    const setGatewayDefault = useCallback((edgeId: string) => {
        if (!selectedId) return;
        setNodes((ns) => ns.map((n) => n.id === selectedId ? {...n, data: {...n.data, defaultFlow: edgeId}} : n));
    }, [selectedId, setNodes]);

    /** 导入 RuleAsset JSON(paste)→ canvas state。 */
    const doImport = useCallback(() => {
        try {
            const asset = JSON.parse(importText) as RuleAsset;
            const st = fromRuleAsset(asset);
            setNodes(st.nodes);
            setEdges(st.edges);
            setNodesMap(st.nodesMap);
            setSchemaName(st.schemaName);
            setImportOpen(false);
            setImportText('');
            message.success(`导入成功:${st.nodes.length} 节点`);
        } catch (e) {
            message.error('JSON 解析失败:' + String(e));
        }
    }, [importText, setNodes, setEdges, setNodesMap, setSchemaName]);

    /** 保存到后端(POST /common/saveFile,需后端 8180 在跑)。 */
    const saveToBackend = useCallback(() => {
        if (!filePath) { message.warning('先填文件路径,如 /projA/loan.json'); return; }
        const json = JSON.stringify(toRuleAsset(nodes, edges, nodesMap, schemaName), null, 2);
        formPost('/common/saveFile', {file: filePath, content: json})
            .then(() => message.success('已保存到 ' + filePath))
            .catch(() => message.error('保存失败(后端未运行或路径无效)'));
    }, [filePath, nodes, edges, nodesMap, schemaName]);

    /** 从后端加载(POST /frame/fileSource → RuleAsset → canvas)。V7.13:加载新流重置 history。 */
    const loadFromBackend = useCallback(() => {
        if (!filePath) { message.warning('先填文件路径'); return; }
        formPost<{content: string}>('/frame/fileSource', {path: filePath})
            .then((res) => {
                const asset = JSON.parse(res.content) as RuleAsset;
                const st = fromRuleAsset(asset);
                setNodes(st.nodes); setEdges(st.edges); setNodesMap(st.nodesMap); setSchemaName(st.schemaName);
                resetHistory();
                message.success(`加载成功:${st.nodes.length} 节点`);
            })
            .catch(() => message.error('加载失败(后端未运行或文件不存在)'));
    }, [filePath, resetHistory, setNodes, setEdges, setNodesMap, setSchemaName]);

    /** 运行 flow:画布 toRuleAsset + fact → POST /v1/execute → 显示 decision(需后端 + 登录态)。 */
    const runFlow = useCallback(() => {
        let fact: Record<string, unknown>;
        try {
            fact = JSON.parse(runFact);
        } catch (e) {
            message.error('fact JSON 解析失败:' + String(e));
            return;
        }
        const asset = toRuleAsset(nodes, edges, nodesMap, schemaName);
        jsonPost<V1ExecutionResponse>('/v1/execute', {asset, fact})
            .then((res) => { setRunResult(res); message.success('decision: ' + res.decision); })
            .catch(() => message.error('运行失败(后端未运行或未登录;demo 页 /v1-flow 无 session,用 /app/v1-flow)'));
    }, [runFact, nodes, edges, nodesMap, schemaName]);

    const palette = useMemo(() => PALETTE.map((t) => ({key: t, label: `+ ${NODE_LABELS[t]}`})), []);

    return (
        <Layout style={{height: '100vh'}}>
            <Header style={{background: token.colorBgContainer, padding: '0 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                <Text strong style={{fontSize: 16}}>RuleForge · V1 决策流设计器</Text>
                <Space>
                    <Text type='secondary' style={{fontSize: 12}}>Schema:</Text>
                    <Input size='small' value={schemaName} onChange={(e) => setSchemaName(e.target.value)} style={{width: 140}}/>
                    <AutoComplete
                        size='small'
                        data-testid='v1-search-input'
                        value={searchQuery}
                        placeholder='搜索节点(name/id)'
                        style={{width: 180}}
                        onChange={setSearchQuery}
                        onSelect={(val) => gotoNode(String(val))}
                        options={searchNodes(nodes, searchQuery).map((nd) => ({
                            value: nd.id,
                            label: `${((nd.data as any)?.name as string) || nd.id} (${nd.id})`,
                        }))}
                        allowClear
                    />
                    <Button size='small' icon={<UndoOutlined/>} onClick={undo} disabled={historyIndex <= 0} data-testid='v1-undo-btn' title='撤销'>撤销</Button>
                    <Button size='small' icon={<RedoOutlined/>} onClick={redo} disabled={historyIndex < 0 || historyIndex >= history.length - 1} data-testid='v1-redo-btn' title='重做'>重做</Button>
                    <Button size='small' icon={<PartitionOutlined/>} onClick={runAutoLayout} data-testid='v1-autolayout-btn' title='dagre 自动布局(TB)'>整理</Button>
                    <Button size='small' onClick={runValidation} data-testid='v1-validate-btn'>校验{validationIssues.length > 0 ? ` (${validationIssues.filter((i) => i.level === 'error').length}❌)` : ''}</Button>
                    <Button size='small' icon={<UploadOutlined/>} onClick={() => setImportOpen(true)}>导入</Button>
                    <Input size='small' placeholder='后端路径 /proj/x.json' value={filePath} onChange={(e) => setFilePath(e.target.value)} style={{width: 180}}/>
                    <Button size='small' icon={<FolderOpenOutlined/>} onClick={loadFromBackend}>加载</Button>
                    <Button size='small' icon={<SaveOutlined/>} onClick={saveToBackend}>保存</Button>
                    <Button size='small' color='blue' variant='solid' icon={<CloudUploadOutlined/>} onClick={publishFlow} data-testid='v1-publish-btn'>发布</Button>
                    {publishedVersion
                        ? <Text type='success' style={{fontSize: 12}} data-testid='v1-published-tag'>已发布 v{publishedVersion}</Text>
                        : <Text type='secondary' style={{fontSize: 12}}>草稿</Text>}
                    <Button size='small' type='primary' onClick={exportJson}>导出 .json</Button>
                    <Button size='small' color='green' variant='solid' onClick={() => { setRunResult(null); setRunOpen(true); }}>运行</Button>
                </Space>
            </Header>
            <Layout>
                <Sider width={200} style={{background: token.colorBgContainer, padding: 12}}>
                    <Text type='secondary' style={{fontSize: 12}}>节点(5 业务 + 网关)</Text>
                    <Menu mode='inline' style={{borderInlineEnd: 'none', marginTop: 8}} items={palette} onClick={({key}) => addNode(key as NodeType)}/>
                    <Text type='secondary' style={{fontSize: 11, display: 'block', marginTop: 16}}>
                        点击节点加入画布 → 拖拽连线 → 点击画布节点编辑属性 → 保存或导出。
                        网关的出边条件在选中网关后编辑。
                    </Text>
                </Sider>
                <Content style={{position: 'relative'}}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
                        onNodeDragStart={onNodeDragStart}
                        onNodesDelete={onNodesDelete}
                        onEdgesDelete={onEdgesDelete}
                        onNodeClick={(_, n) => {
                            // V7.5:规则节点(RuleSet/DecisionTable/ScoreCard)有 ruleRef → 开对应编辑器标签,不弹 Drawer
                            const data = n.data as V1NodeData;
                            const ruleRef = data.ruleRef;
                            if (ruleRef && ruleRef.trim() !== '' && (data.nodeType === 'RuleSet' || data.nodeType === 'DecisionTable' || data.nodeType === 'ScoreCard')) {
                                const editorMap: Record<string, EditorType> = {RuleSet: 'v1ruleset', DecisionTable: 'v1decisiontable', ScoreCard: 'v1scorecard'};
                                openEditorTab({editorType: editorMap[data.nodeType], file: ruleRef});
                            } else {
                                setSelectedId(n.id);
                            }
                        }}
                        nodeTypes={nodeTypes}
                        fitView
                        data-testid='v1-canvas'
                    >
                        <Background/>
                        <Controls/>
                        <MiniMap pannable zoomable/>
                    </ReactFlow>
                </Content>
            </Layout>
            <NodePropertyDrawer
                node={selectedNode}
                open={selectedNode !== null}
                onClose={() => setSelectedId(null)}
                onChange={onNodeChange}
            />
            <GatewayEditor
                open={isGatewaySelected}
                gatewayId={isGatewaySelected ? selectedId : null}
                edges={edges}
                nodes={nodes}
                defaultFlow={selectedRfNode?.data.defaultFlow}
                onUpdateEdge={updateEdgeCondition}
                onSetDefault={setGatewayDefault}
                onClose={() => setSelectedId(null)}
            />
            <Drawer title='RuleAsset JSON(后端可执行)' open={exportOpen} onClose={() => setExportOpen(false)} size={520}>
                <pre data-testid='v1-export' style={{fontSize: 11, fontFamily: 'monospace', whiteSpace: 'pre-wrap'}}>{exported}</pre>
            </Drawer>
            <Modal title='导入 RuleAsset JSON' open={importOpen} onCancel={() => setImportOpen(false)} onOk={doImport} okText='导入' width={620}>
                <Input.TextArea
                    data-testid='v1-import-text'
                    value={importText}
                    onChange={(e) => setImportText(e.target.value)}
                    rows={18}
                    placeholder='粘贴 RuleAsset JSON(version 1.0 + flow + nodes)…'
                    style={{fontFamily: 'monospace', fontSize: 11}}
                />
            </Modal>
            <Modal title='运行决策流(填输入 fact → POST /v1/execute)' open={runOpen} onCancel={() => setRunOpen(false)} footer={<Button size='small' onClick={() => setRunOpen(false)}>关闭</Button>} width={620}>
                <Text type='secondary' style={{fontSize: 12}}>输入 fact(JSON,字段对齐 Schema):</Text>
                <Input.TextArea
                    data-testid='v1-run-fact'
                    value={runFact}
                    onChange={(e) => setRunFact(e.target.value)}
                    rows={8}
                    placeholder='{"age":30, "income":8000}'
                    style={{fontFamily: 'monospace', fontSize: 12, marginTop: 4}}
                />
                <Space style={{marginTop: 8}}>
                    <Button size='small' type='primary' data-testid='v1-run-btn' onClick={runFlow}>运行</Button>
                    {runResult && (
                        <Text strong style={{color: runResult.rejected ? '#ff4d4f' : '#52c41a'}}>
                            decision: {runResult.decision}{runResult.rejected ? `(rejected: ${runResult.rejectReason})` : ''}
                        </Text>
                    )}
                </Space>
                {runResult && (
                    <pre data-testid='v1-run-result' style={{marginTop: 8, fontSize: 11, fontFamily: 'monospace', whiteSpace: 'pre-wrap', background: '#fafafa', padding: 8, borderRadius: 4, maxHeight: 240, overflow: 'auto'}}>
                        {JSON.stringify(runResult.fact, null, 2)}
                    </pre>
                )}
            </Modal>
            <Modal title={`校验结果 (${validationIssues.filter((i) => i.level === 'error').length} error, ${validationIssues.filter((i) => i.level === 'warning').length} warning)`}
                open={validationOpen} onCancel={() => setValidationOpen(false)}
                footer={<Button size='small' onClick={() => setValidationOpen(false)}>关闭</Button>} width={520}>
                {validationIssues.length === 0
                    ? <Text type='success'>✓ 所有校验通过,可以发布。</Text>
                    : <Space orientation='vertical' size={4} style={{width: '100%'}}>
                        {validationIssues.map((iss, i) => (
                            <div key={i} style={{display: 'flex', alignItems: 'flex-start', gap: 8, padding: '4px 8px', background: iss.level === 'error' ? '#fff1f0' : '#fffbe6', borderRadius: 4}}>
                                <Text strong style={{color: iss.level === 'error' ? '#ff4d4f' : '#faad14', minWidth: 60}}>
                                    {iss.level === 'error' ? 'ERROR' : 'WARN'}
                                </Text>
                                <Text style={{fontSize: 12}}>{iss.message}</Text>
                            </div>
                        ))}
                    </Space>}
            </Modal>
        </Layout>
    );
}

/**
 * V7.19 修复白屏:ReactFlowProvider 必须是调用 useReactFlow() 的组件的祖先组件,
 * 不能挂在本组件 return 里(否则组件 body 跑 useReactFlow 时 Provider 尚未挂载,
 * 抛 reactflow.dev/error#001 → 整个 /v1-flow 路由白屏)。
 * 拆两层:外层挂 Provider,内层消费 hooks。调用方仍用 <FlowDesigner file=.../>。
 */
export default function FlowDesigner({file}: {file?: string}) {
    return (
        <ReactFlowProvider>
            <FlowDesignerInner file={file}/>
        </ReactFlowProvider>
    );
}
