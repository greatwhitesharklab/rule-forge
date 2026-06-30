import {Drawer, Form, Input, InputNumber, Select, Button, Space, Tag, Divider, Typography} from 'antd';
import {PlusOutlined, DeleteOutlined} from '@ant-design/icons';
import {AgGridReact} from 'ag-grid-react';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import {type V1Node, type RuleSetNode, type Rule,
    type DecisionTableNode, type ScoreCardNode, type Card,
    type DecisionNode, type StartNode} from './ruleAsset';
import ConditionEditor from './ConditionEditor';
import {ActionsEditor} from './ActionEditor';

const {Text} = Typography;
const HIT_POLICIES = [{value: 'FIRST_MATCH', label: 'FIRST_MATCH(首命中)'}, {value: 'ALL_MATCH', label: 'ALL_MATCH(全命中)'}, {value: 'PRIORITY', label: 'PRIORITY(优先级)'}];
const TABLE_HIT_POLICIES = [{value: 'FIRST', label: 'FIRST(首行)'}, {value: 'UNIQUE', label: 'UNIQUE'}, {value: 'PRIORITY', label: 'PRIORITY'}, {value: 'ANY', label: 'ANY'}, {value: 'COLLECT', label: 'COLLECT'}];
const AGGREGATIONS = [{value: 'SUM', label: 'SUM'}, {value: 'AVG', label: 'AVG'}, {value: 'MIN', label: 'MIN'}, {value: 'MAX', label: 'MAX'}, {value: 'WEIGHTED_SUM', label: 'WEIGHTED_SUM'}];

let _id = 1;
const newId = () => `x${_id++}`;

/** 节点属性 Drawer —— 点画布节点弹出,按类型编辑节点内容。改动直写回 V1Node。 */
export default function NodePropertyDrawer({
    node, open, onClose, onChange,
}: {
    node: V1Node | null;
    open: boolean;
    onClose: () => void;
    onChange: (n: V1Node) => void;
}) {
    if (!node) return null;
    const update = (patch: Partial<V1Node>) => onChange({...node, ...patch} as V1Node);

    return (
        <Drawer
            title={<Space><NodeTypeBadge type={node.type}/><Text strong>{node.name}</Text></Space>}
            open={open} onClose={onClose} width={620}
            extra={<Text type='secondary' style={{fontSize: 12}}>id: {node.id}</Text>}
        >
            <Form layout='vertical' size='small'>
                <Form.Item label='节点名'><Input value={node.name} onChange={(e) => update({name: e.target.value} as Partial<V1Node>)}/></Form.Item>
                {node.type === 'Start' && <StartEditor node={node as StartNode} update={update}/>}
                {node.type === 'RuleSet' && <RuleSetEditor node={node as RuleSetNode} update={update}/>}
                {node.type === 'DecisionTable' && <DecisionTableEditor node={node as DecisionTableNode} update={update}/>}
                {node.type === 'ScoreCard' && <ScoreCardEditor node={node as ScoreCardNode} update={update}/>}
                {node.type === 'Decision' && <DecisionEditor node={node as DecisionNode} update={update}/>}
            </Form>
        </Drawer>
    );
}

function NodeTypeBadge({type}: {type: V1Node['type']}) {
    const color: Record<string, string> = {Start: '#52c41a', RuleSet: '#1677ff', DecisionTable: '#722ed1', ScoreCard: '#fa8c16', Decision: '#eb2f96'};
    return <Tag color={color[type]}>{type}</Tag>;
}

function StartEditor({update}: {node: StartNode; update: (p: Partial<V1Node>) => void}) {
    return <Form.Item label='Schema 名(输入 fact 类型)'><Input onChange={(e) => update({schema: e.target.value} as Partial<V1Node>)}/></Form.Item>;
}

function RuleSetEditor({node, update}: {node: RuleSetNode; update: (p: Partial<V1Node>) => void}) {
    const setRules = (rules: Rule[]) => update({rules} as Partial<V1Node>);
    const rules = node.rules || [];
    return (
        <>
            <Form.Item label='命中策略'><Select value={node.hitPolicy} options={HIT_POLICIES} onChange={(v) => update({hitPolicy: v} as Partial<V1Node>)}/></Form.Item>
            <Divider style={{margin: '8px 0'}}><Text type='secondary'>规则({rules.length})</Text></Divider>
            {rules.map((r, i) => (
                <div key={r.id} style={{border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, marginBottom: 8}}>
                    <Space style={{width: '100%', justifyContent: 'space-between'}}>
                        <Input placeholder='规则名' value={r.name} style={{width: 150}}
                            onChange={(e) => {const nr = [...rules]; nr[i] = {...r, name: e.target.value}; setRules(nr);}}/>
                        <Space>
                            <Text type='secondary'>优先级</Text>
                            <InputNumber value={r.priority} style={{width: 70}}
                                onChange={(v) => {const nr = [...rules]; nr[i] = {...r, priority: v ?? 0}; setRules(nr);}}/>
                            <Button size='small' danger icon={<DeleteOutlined/>} onClick={() => setRules(rules.filter((_, j) => j !== i))}/>
                        </Space>
                    </Space>
                    <div style={{marginTop: 6}}><Text type='secondary' style={{fontSize: 11}}>condition(返回 boolean,可视化或 CEL)</Text></div>
                    <ConditionEditor value={r.condition} onChange={(v) => {const nr = [...rules]; nr[i] = {...r, condition: v}; setRules(nr);}}/>
                    <ActionsEditor actions={r.actions || []} onChange={(actions) => {const nr = [...rules]; nr[i] = {...r, actions}; setRules(nr);}}/>
                </div>
            ))}
            <Button block icon={<PlusOutlined/>} onClick={() => setRules([...rules, {id: newId(), condition: '', actions: []}])}>添加规则</Button>
        </>
    );
}

function DecisionTableEditor({node, update}: {node: DecisionTableNode; update: (p: Partial<V1Node>) => void}) {
    const inputs = node.inputs || [];
    const outputs = node.outputs || [];
    const rows = node.rows || [];
    // AG Grid 列:每个 input 一个 condition 文本列,每个 output 一个值列
    const colDefs: any[] = [
        ...inputs.map((c, i) => ({field: `c${i}`, headerName: `${c.name} (CEL)`, editable: true, flex: 1})),
        ...outputs.map((c, i) => ({field: `o${i}`, headerName: `${c.name} (=)`, editable: true, flex: 1})),
    ];
    // node.rows(conditions[]/outputs[])→ AG Grid 平坦行
    const gridRows = rows.map((r) => {
        const flat: any = {id: r.id};
        r.conditions.forEach((c, i) => {flat[`c${i}`] = c === '*' ? '' : c;});
        r.outputs.forEach((o, i) => {flat[`o${i}`] = o;});
        return flat;
    });
    const onCellEdit = (e: any) => {
        const idx = gridRows.findIndex((gr) => gr.id === e.data.id);
        if (idx < 0) return;
        const r = {...rows[idx]};
        inputs.forEach((_, i) => {const v = e.data[`c${i}`]; const conds = [...(r.conditions || [])]; conds[i] = v || '*'; r.conditions = conds;});
        outputs.forEach((_, i) => {const outs = [...(r.outputs || [])]; outs[i] = e.data[`o${i}`]; r.outputs = outs;});
        const nr = [...rows]; nr[idx] = r; update({rows: nr} as Partial<V1Node>);
    };
    const addRow = () => update({rows: [...rows, {id: newId(), conditions: inputs.map(() => '*'), outputs: outputs.map(() => '')}]} as Partial<V1Node>);
    const addInputCol = () => update({inputs: [...inputs, {name: `in${inputs.length}`, dataType: 'NUMBER', direction: 'INPUT'}]} as Partial<V1Node>);
    const addOutputCol = () => update({outputs: [...outputs, {name: `out${outputs.length}`, dataType: 'STRING', direction: 'OUTPUT'}]} as Partial<V1Node>);

    return (
        <>
            <Form.Item label='命中策略'><Select value={node.hitPolicy} options={TABLE_HIT_POLICIES} onChange={(v) => update({hitPolicy: v} as Partial<V1Node>)}/></Form.Item>
            <Space style={{marginBottom: 8}}>
                <Button size='small' onClick={addInputCol}>+ 输入列</Button>
                <Button size='small' onClick={addOutputCol}>+ 输出列</Button>
                <Button size='small' onClick={addRow} icon={<PlusOutlined/>}>+ 行</Button>
            </Space>
            <div className='ag-theme-quartz' style={{height: Math.max(120, (rows.length + 1) * 36), width: '100%'}}>
                <AgGridReact rowData={gridRows} columnDefs={colDefs} onCellValueChanged={onCellEdit} stopEditingWhenCellsLoseFocus={true}/>
            </div>
            <Text type='secondary' style={{fontSize: 11}}>condition 留空 = 通配 '*';CEL 表达式如 `score &lt; 500`</Text>
        </>
    );
}

function ScoreCardEditor({node, update}: {node: ScoreCardNode; update: (p: Partial<V1Node>) => void}) {
    const cards = node.cards || [];
    const setCards = (cards: Card[]) => update({cards} as Partial<V1Node>);
    return (
        <>
            <Space style={{width: '100%'}}>
                <Form.Item label='输出字段' style={{flex: 1}}><Input value={node.output} onChange={(e) => update({output: e.target.value} as Partial<V1Node>)}/></Form.Item>
                <Form.Item label='聚合'><Select value={node.aggregation} options={AGGREGATIONS} onChange={(v) => update({aggregation: v} as Partial<V1Node>)}/></Form.Item>
            </Space>
            <Divider style={{margin: '8px 0'}}><Text type='secondary'>评分项({cards.length})</Text></Divider>
            {cards.map((c, i) => (
                <div key={c.id} style={{border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, marginBottom: 8}}>
                    <Space style={{width: '100%', justifyContent: 'space-between'}}>
                        <Input placeholder='字段' value={c.field} style={{width: 140}}
                            onChange={(e) => {const nc = [...cards]; nc[i] = {...c, field: e.target.value}; setCards(nc);}}/>
                        <Button size='small' danger icon={<DeleteOutlined/>} onClick={() => setCards(cards.filter((_, j) => j !== i))}/>
                    </Space>
                    {(c.bands || []).map((b, j) => (
                        <Space key={b.id} style={{display: 'flex', marginTop: 4}} size={4}>
                            <Text type='secondary' style={{fontSize: 11}}>band</Text>
                            <div style={{flex: 1}}><ConditionEditor value={b.condition} onChange={(v) => {const nc = [...cards]; const nb = [...c.bands!]; nb[j] = {...b, condition: v}; nc[i] = {...c, bands: nb}; setCards(nc);}}/></div>
                            <InputNumber placeholder='分' value={b.score} style={{width: 70}}
                                onChange={(v) => {const nc = [...cards]; const nb = [...c.bands!]; nb[j] = {...b, score: v ?? 0}; nc[i] = {...c, bands: nb}; setCards(nc);}}/>
                            <Button size='small' danger icon={<DeleteOutlined/>} onClick={() => {const nc = [...cards]; nc[i] = {...c, bands: c.bands!.filter((_, k) => k !== j)}; setCards(nc);}}/>
                        </Space>
                    ))}
                    <Button size='small' type='dashed' icon={<PlusOutlined/>} style={{marginTop: 4}}
                        onClick={() => {const nc = [...cards]; nc[i] = {...c, bands: [...(c.bands || []), {id: newId(), condition: '', score: 0}]}; setCards(nc);}}>+ band</Button>
                </div>
            ))}
            <Button block icon={<PlusOutlined/>} onClick={() => setCards([...cards, {id: newId(), field: '', bands: []}])}>添加评分项</Button>
        </>
    );
}

function DecisionEditor({node, update}: {node: DecisionNode; update: (p: Partial<V1Node>) => void}) {
    const outputs = node.outputs || [];
    return (
        <>
            <Form.Item label='允许的决策值(逗号分隔,如 approve,review,reject)'>
                <Input value={outputs.join(',')} onChange={(e) => update({outputs: e.target.value.split(',').map((s) => s.trim()).filter(Boolean)} as Partial<V1Node>)}/>
            </Form.Item>
            <Form.Item label='决策字段(默认 decision)'><Input value={node.decisionField} placeholder='decision' onChange={(e) => update({decisionField: e.target.value} as Partial<V1Node>)}/></Form.Item>
            <Form.Item label='兜底决策(未设置时)'><Input value={node.defaultOutput} onChange={(e) => update({defaultOutput: e.target.value} as Partial<V1Node>)}/></Form.Item>
        </>
    );
}
