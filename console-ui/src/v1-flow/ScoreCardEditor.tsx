import {useState, useEffect, useCallback} from 'react';
import {Input, Select, Button, Space, Typography, message, Divider, Form, InputNumber, theme} from 'antd';
import {PlusOutlined, DeleteOutlined, SaveOutlined} from '@ant-design/icons';
import {formPost} from '@/api/client';
import ConditionEditor from './ConditionEditor';

const {Text} = Typography;
const AGGREGATIONS = [{value: 'SUM', label: 'SUM(求和)'}, {value: 'MAX', label: 'MAX(最大)'}, {value: 'MIN', label: 'MIN(最小)'}, {value: 'WEIGHTED_SUM', label: 'WEIGHTED_SUM(加权和)'}];

interface Band {
    id: string;
    condition: string;
    score: number;
}
interface Card {
    id: string;
    field: string;
    bands: Band[];
}
interface ScoreCard {
    id: string;
    type: 'ScoreCard';
    name: string;
    output: string;
    aggregation: string;
    cards: Card[];
}

/**
 * V1 评分卡独立编辑器(V7.5)。编辑 .v1sc.json(output/aggregation/cards/bands)。
 * 从 NodePropertyDrawer.ScoreCardEditor 抽出,支持独立文件加载/保存。
 */
export default function ScoreCardEditor({file}: {file?: string}) {
    const {token} = theme.useToken();
    const [sc, setSc] = useState<ScoreCard>({id: 'sc01', type: 'ScoreCard', name: '未命名评分卡', output: 'score', aggregation: 'SUM', cards: []});
    const [filePath, setFilePath] = useState(file || '');

    useEffect(() => {
        if (!file) return;
        setFilePath(file);
        formPost<{content: string}>('/frame/fileSource', {path: file})
            .then((res) => setSc(JSON.parse(res.content)))
            .catch(() => message.error('加载失败(后端未运行或文件不存在)'));
    }, [file]);

    const setCards = (cards: Card[]) => setSc((s) => ({...s, cards}));

    const save = useCallback(() => {
        if (!filePath) { message.warning('先填文件路径'); return; }
        formPost('/common/saveFile', {file: filePath, content: JSON.stringify(sc, null, 2)})
            .then(() => message.success('已保存到 ' + filePath))
            .catch(() => message.error('保存失败(后端未运行或路径无效)'));
    }, [filePath, sc]);

    return (
        <div style={{height: '100vh', background: token.colorBgContainer, padding: 24, overflow: 'auto'}}>
            <Space style={{marginBottom: 16}} wrap>
                <Text strong style={{fontSize: 16}}>RuleForge · V1 评分卡编辑器</Text>
                <Input size='small' value={sc.name} style={{width: 160}} onChange={(e) => setSc((s) => ({...s, name: e.target.value}))}/>
                <Input size='small' placeholder='后端路径 /proj/x.v1sc.json' value={filePath} onChange={(e) => setFilePath(e.target.value)} style={{width: 260}}/>
                <Button size='small' icon={<SaveOutlined/>} onClick={save}>保存</Button>
            </Space>

            <Space style={{marginBottom: 16}}>
                <Form.Item label='输出字段' style={{marginBottom: 0}}><Input value={sc.output} onChange={(e) => setSc((s) => ({...s, output: e.target.value}))}/></Form.Item>
                <Form.Item label='聚合' style={{marginBottom: 0}}><Select value={sc.aggregation} options={AGGREGATIONS} onChange={(v) => setSc((s) => ({...s, aggregation: v}))}/></Form.Item>
            </Space>

            <Divider style={{margin: '8px 0'}}><Text type='secondary'>评分项({sc.cards.length})</Text></Divider>
            {sc.cards.map((c, i) => (
                <div key={c.id} style={{border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, marginBottom: 8}}>
                    <Space style={{width: '100%', justifyContent: 'space-between'}}>
                        <Input placeholder='字段' value={c.field} style={{width: 140}}
                            onChange={(e) => {const nc = [...sc.cards]; nc[i] = {...c, field: e.target.value}; setCards(nc);}}/>
                        <Button size='small' danger icon={<DeleteOutlined/>} onClick={() => setCards(sc.cards.filter((_, j) => j !== i))}/>
                    </Space>
                    {(c.bands || []).map((b, j) => (
                        <Space key={b.id} style={{display: 'flex', marginTop: 4}} size={4}>
                            <Text type='secondary' style={{fontSize: 11}}>band</Text>
                            <div style={{flex: 1}}><ConditionEditor value={b.condition} onChange={(v) => {const nc = [...sc.cards]; const nb = [...c.bands!]; nb[j] = {...b, condition: v}; nc[i] = {...c, bands: nb}; setCards(nc);}}/></div>
                            <InputNumber placeholder='分' value={b.score} style={{width: 70}}
                                onChange={(v) => {const nc = [...sc.cards]; const nb = [...c.bands!]; nb[j] = {...b, score: v ?? 0}; nc[i] = {...c, bands: nb}; setCards(nc);}}/>
                            <Button size='small' danger icon={<DeleteOutlined/>} onClick={() => {const nc = [...sc.cards]; nc[i] = {...c, bands: c.bands!.filter((_, k) => k !== j)}; setCards(nc);}}/>
                        </Space>
                    ))}
                    <Button size='small' type='dashed' icon={<PlusOutlined/>} style={{marginTop: 4}}
                        onClick={() => {const nc = [...sc.cards]; nc[i] = {...c, bands: [...(c.bands || []), {id: `band${(c.bands || []).length + 1}`, condition: '', score: 0}]}; setCards(nc);}}>+ band</Button>
                </div>
            ))}
            <Button block icon={<PlusOutlined/>} onClick={() => setCards([...sc.cards, {id: `card${sc.cards.length + 1}`, field: '', bands: []}])}>添加评分项</Button>
        </div>
    );
}
