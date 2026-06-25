import {useState, useEffect, useCallback} from 'react';
import {Input, Select, Button, Space, Typography, message, theme} from 'antd';
import {PlusOutlined, DeleteOutlined, SaveOutlined} from '@ant-design/icons';
import {formPost} from '@/api/client';

const {Text} = Typography;
const LIB_TYPES = [{value: 'PARAMETER', label: '参数库(pl)'}, {value: 'CONSTANT', label: '常量库(cl)'}, {value: 'VARIABLE', label: '变量库(vl)'}];
const DATA_TYPES = [{value: 'NUMBER', label: 'NUMBER'}, {value: 'STRING', label: 'STRING'}, {value: 'BOOLEAN', label: 'BOOLEAN'}, {value: 'LIST', label: 'LIST'}];

interface LibraryEntry {
    key: string;
    value: unknown;
    dataType: string;
    label?: string;
}
interface Library {
    type: string;
    name: string;
    entries: LibraryEntry[];
}

/**
 * V1 库编辑器(V7.4-3)。编辑 .v1lib.json(vl/cl/pl 四库):type/name + entries
 * (key=CEL 引用名 param.{key}/constant.{key};vl 条目=fact schema 字段)。保存 POST /common/saveFile。
 */
export default function LibraryEditor({file}: {file?: string}) {
    const {token} = theme.useToken();
    const [lib, setLib] = useState<Library>({type: 'PARAMETER', name: '未命名库', entries: []});
    const [filePath, setFilePath] = useState(file || '');

    useEffect(() => {
        if (!file) return;
        setFilePath(file);
        formPost<{content: string}>('/frame/fileSource', {path: file})
            .then((res) => setLib(JSON.parse(res.content)))
            .catch(() => message.error('加载失败(后端未运行或文件不存在)'));
    }, [file]);

    const setEntry = (i: number, patch: Partial<LibraryEntry>) => {
        setLib((l) => ({...l, entries: l.entries.map((e, j) => (j === i ? {...e, ...patch} : e))}));
    };
    const addEntry = () => setLib((l) => ({...l, entries: [...l.entries, {key: '', value: '', dataType: 'NUMBER', label: ''}]}));
    const delEntry = (i: number) => setLib((l) => ({...l, entries: l.entries.filter((_, j) => j !== i)}));

    const save = useCallback(() => {
        if (!filePath) { message.warning('先填文件路径'); return; }
        formPost('/common/saveFile', {file: filePath, content: JSON.stringify(lib, null, 2)})
            .then(() => message.success('已保存到 ' + filePath))
            .catch(() => message.error('保存失败(后端未运行或路径无效)'));
    }, [filePath, lib]);

    return (
        <div style={{height: '100vh', background: token.colorBgContainer, padding: 24, overflow: 'auto'}}>
            <Space style={{marginBottom: 16}} wrap>
                <Text strong style={{fontSize: 16}}>RuleForge · V1 库编辑器</Text>
                <Text type='secondary' style={{fontSize: 12}}>类型:</Text>
                <Select size='small' value={lib.type} options={LIB_TYPES} style={{width: 140}} onChange={(v) => setLib((l) => ({...l, type: v}))}/>
                <Text type='secondary' style={{fontSize: 12}}>库名:</Text>
                <Input size='small' value={lib.name} style={{width: 160}} onChange={(e) => setLib((l) => ({...l, name: e.target.value}))}/>
                <Input size='small' placeholder='后端路径 /proj/x.v1lib.json' value={filePath} onChange={(e) => setFilePath(e.target.value)} style={{width: 220}}/>
                <Button size='small' icon={<SaveOutlined/>} onClick={save}>保存</Button>
            </Space>
            <Text type='secondary' style={{fontSize: 12, display: 'block', marginBottom: 8}}>
                条目(key = CEL 引用名:pl <code>riskScore &gt;= param.{'{key}'}</code> / cl <code>constant.{'{key}'}</code>;vl 条目 = fact schema 字段)
            </Text>
            {lib.entries.map((e, i) => (
                <Space key={i} style={{display: 'flex', marginBottom: 6}} size={6}>
                    <Input size='small' placeholder='key' value={e.key} style={{width: 140}} onChange={(ev) => setEntry(i, {key: ev.target.value})}/>
                    <Input size='small' placeholder='value' value={String(e.value ?? '')} style={{width: 120}} onChange={(ev) => setEntry(i, {value: ev.target.value})}/>
                    <Select size='small' value={e.dataType} options={DATA_TYPES} style={{width: 110}} onChange={(v) => setEntry(i, {dataType: v})}/>
                    <Input size='small' placeholder='label' value={e.label || ''} style={{width: 120}} onChange={(ev) => setEntry(i, {label: ev.target.value})}/>
                    <Button size='small' danger icon={<DeleteOutlined/>} onClick={() => delEntry(i)}/>
                </Space>
            ))}
            <Button size='small' type='dashed' icon={<PlusOutlined/>} onClick={addEntry} style={{marginTop: 4}}>添加条目</Button>
        </div>
    );
}
