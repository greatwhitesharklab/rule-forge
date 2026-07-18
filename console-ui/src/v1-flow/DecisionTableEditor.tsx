import {useState, useEffect, useCallback} from 'react';
import {Input, Select, Button, Space, Typography, message, Form, theme} from 'antd';
import {PlusOutlined, SaveOutlined} from '@ant-design/icons';
import {AgGridReact} from 'ag-grid-react';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import {formPost} from '@/api/client';
import {AG_GRID_LOCALE_ZH} from './agGridLocale';

const {Text} = Typography;
const TABLE_HIT_POLICIES = [{value: 'FIRST', label: 'FIRST(首个命中)'}, {value: 'ALL', label: 'ALL(全部命中)'}, {value: 'UNIQUE', label: 'UNIQUE(唯一命中)'}];
const DATA_TYPES = [{value: 'NUMBER', label: 'NUMBER'}, {value: 'STRING', label: 'STRING'}, {value: 'BOOLEAN', label: 'BOOLEAN'}];

interface Column {
    name: string;
    dataType: string;
    direction: 'INPUT' | 'OUTPUT';
}
interface Row {
    id: string;
    conditions: string[];
    outputs: string[];
}
interface DecisionTable {
    id: string;
    type: 'DecisionTable';
    name: string;
    hitPolicy: string;
    inputs: Column[];
    outputs: Column[];
    rows: Row[];
}

/**
 * V1 决策表独立编辑器(V7.5)。编辑 .v1dt.json(inputs/outputs/rows)。
 * 从 NodePropertyDrawer.DecisionTableEditor 抽出,支持独立文件加载/保存。
 */
export default function DecisionTableEditor({file}: {file?: string}) {
    const {token} = theme.useToken();
    const [dt, setDt] = useState<DecisionTable>({id: 'dt01', type: 'DecisionTable', name: '未命名决策表', hitPolicy: 'FIRST', inputs: [], outputs: [], rows: []});
    const [filePath, setFilePath] = useState(file || '');

    useEffect(() => {
        if (!file) return;
        setFilePath(file);
        formPost<{content: string}>('/frame/fileSource', {path: file})
            .then((res) => setDt(JSON.parse(res.content)))
            .catch(() => message.error('加载失败(后端未运行或文件不存在)'));
    }, [file]);

    // AG Grid 列:每个 input 一个 condition 文本列,每个 output 一个值列
    const colDefs: any[] = [
        ...dt.inputs.map((c, i) => ({field: `c${i}`, headerName: `${c.name} (CEL)`, editable: true, flex: 1})),
        ...dt.outputs.map((c, i) => ({field: `o${i}`, headerName: `${c.name} (=)`, editable: true, flex: 1})),
    ];
    // node.rows(conditions[]/outputs[])→ AG Grid 平坦行
    const gridRows = dt.rows.map((r) => {
        const flat: any = {id: r.id};
        r.conditions.forEach((c, i) => {flat[`c${i}`] = c === '*' ? '' : c;});
        r.outputs.forEach((o, i) => {flat[`o${i}`] = o;});
        return flat;
    });

    const onCellEdit = (e: any) => {
        const idx = gridRows.findIndex((gr) => gr.id === e.data.id);
        if (idx < 0) return;
        const r = {...dt.rows[idx]};
        dt.inputs.forEach((_, i) => {const v = e.data[`c${i}`]; const conds = [...(r.conditions || [])]; conds[i] = v || '*'; r.conditions = conds;});
        dt.outputs.forEach((_, i) => {const outs = [...(r.outputs || [])]; outs[i] = e.data[`o${i}`]; r.outputs = outs;});
        const nr = [...dt.rows]; nr[idx] = r; setDt((d) => ({...d, rows: nr}));
    };
    const addRow = () => setDt((d) => ({...d, rows: [...d.rows, {id: `row${d.rows.length + 1}`, conditions: d.inputs.map(() => '*'), outputs: d.outputs.map(() => '')}]}));
    const addInputCol = () => setDt((d) => ({...d, inputs: [...d.inputs, {name: `in${d.inputs.length}`, dataType: 'NUMBER', direction: 'INPUT'}]}));
    const addOutputCol = () => setDt((d) => ({...d, outputs: [...d.outputs, {name: `out${d.outputs.length}`, dataType: 'STRING', direction: 'OUTPUT'}]}));

    const save = useCallback(() => {
        if (!filePath) { message.warning('先填文件路径'); return; }
        formPost('/common/saveFile', {file: filePath, content: JSON.stringify(dt, null, 2)})
            .then(() => message.success('已保存到 ' + filePath))
            .catch(() => message.error('保存失败(后端未运行或路径无效)'));
    }, [filePath, dt]);

    return (
        <div style={{height: '100vh', background: token.colorBgContainer, padding: 24, overflow: 'auto'}}>
            <Space style={{marginBottom: 16}} wrap>
                <Text strong style={{fontSize: 16}}>RuleForge · V1 决策表编辑器</Text>
                <Input size='small' value={dt.name} style={{width: 160}} onChange={(e) => setDt((d) => ({...d, name: e.target.value}))}/>
                <Input size='small' placeholder='后端路径 /proj/x.v1dt.json' value={filePath} onChange={(e) => setFilePath(e.target.value)} style={{width: 260}}/>
                <Button size='small' icon={<SaveOutlined/>} onClick={save}>保存</Button>
            </Space>

            <Form.Item label='命中策略' style={{marginBottom: 8}}><Select value={dt.hitPolicy} options={TABLE_HIT_POLICIES} onChange={(v) => setDt((d) => ({...d, hitPolicy: v}))}/></Form.Item>
            <Space style={{marginBottom: 8}}>
                <Button size='small' onClick={addInputCol}>+ 输入列</Button>
                <Button size='small' onClick={addOutputCol}>+ 输出列</Button>
                <Button size='small' onClick={addRow} icon={<PlusOutlined/>}>+ 行</Button>
            </Space>
            <div className='ag-theme-quartz' style={{height: Math.max(120, (dt.rows.length + 1) * 36), width: '100%'}}>
                <AgGridReact rowData={gridRows} columnDefs={colDefs} onCellValueChanged={onCellEdit} stopEditingWhenCellsLoseFocus={true} localeText={AG_GRID_LOCALE_ZH}
                    /* v33+ 默认走 Theming API,与上方 legacy CSS 主题共存会报 error #239;显式声明 legacy */ theme='legacy'/>
            </div>
            <Text type='secondary' style={{fontSize: 11}}>condition 留空 = 通配 '*';CEL 表达式如 `score &lt; 500`</Text>
        </div>
    );
}
