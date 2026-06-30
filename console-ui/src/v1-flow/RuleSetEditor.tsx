import {useState, useEffect, useCallback} from 'react';
import {Input, Select, Button, Space, Typography, message, Divider, Form, InputNumber, theme} from 'antd';
import {PlusOutlined, DeleteOutlined, SaveOutlined} from '@ant-design/icons';
import {formPost} from '@/api/client';
import ConditionEditor from './ConditionEditor';
import {ActionsEditor} from './ActionEditor';
import type {Action} from './ruleAsset';

const {Text} = Typography;
const HIT_POLICIES = [{value: 'FIRST', label: 'FIRST(首个命中)'}, {value: 'ALL', label: 'ALL(全部命中)'}, {value: 'ANY', label: 'ANY(任一命中)'}];

interface Rule {
    id: string;
    name: string;
    priority: number;
    condition: string;
    actions: Action[];
}
interface RuleSet {
    id: string;
    type: 'RuleSet';
    name: string;
    hitPolicy: string;
    rules: Rule[];
}

/**
 * V1 规则集独立编辑器(V7.5)。编辑 .v1rs.json(hitPolicy + rules)。
 * 从 NodePropertyDrawer.RuleSetEditor 抽出,支持独立文件加载/保存。
 */
export default function RuleSetEditor({file}: {file?: string}) {
    const {token} = theme.useToken();
    const [ruleSet, setRuleSet] = useState<RuleSet>({id: 'ruleset01', type: 'RuleSet', name: '未命名规则集', hitPolicy: 'FIRST', rules: []});
    const [filePath, setFilePath] = useState(file || '');

    useEffect(() => {
        if (!file) return;
        setFilePath(file);
        formPost<{content: string}>('/frame/fileSource', {path: file})
            .then((res) => setRuleSet(JSON.parse(res.content)))
            .catch(() => message.error('加载失败(后端未运行或文件不存在)'));
    }, [file]);

    const setRules = (rules: Rule[]) => setRuleSet((rs) => ({...rs, rules}));

    const save = useCallback(() => {
        if (!filePath) { message.warning('先填文件路径'); return; }
        formPost('/common/saveFile', {file: filePath, content: JSON.stringify(ruleSet, null, 2)})
            .then(() => message.success('已保存到 ' + filePath))
            .catch(() => message.error('保存失败(后端未运行或路径无效)'));
    }, [filePath, ruleSet]);

    return (
        <div style={{height: '100vh', background: token.colorBgContainer, padding: 24, overflow: 'auto'}}>
            <Space style={{marginBottom: 16}} wrap>
                <Text strong style={{fontSize: 16}}>RuleForge · V1 规则集编辑器</Text>
                <Input size='small' value={ruleSet.name} style={{width: 160}} onChange={(e) => setRuleSet((rs) => ({...rs, name: e.target.value}))}/>
                <Input size='small' placeholder='后端路径 /proj/x.v1rs.json' value={filePath} onChange={(e) => setFilePath(e.target.value)} style={{width: 260}}/>
                <Button size='small' icon={<SaveOutlined/>} onClick={save}>保存</Button>
            </Space>

            <Form.Item label='命中策略' style={{marginBottom: 8}}><Select value={ruleSet.hitPolicy} options={HIT_POLICIES} onChange={(v) => setRuleSet((rs) => ({...rs, hitPolicy: v}))}/></Form.Item>
            <Divider style={{margin: '8px 0'}}><Text type='secondary'>规则({ruleSet.rules.length})</Text></Divider>
            {ruleSet.rules.map((r, i) => (
                <div key={r.id} style={{border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, marginBottom: 8}}>
                    <Space style={{width: '100%', justifyContent: 'space-between'}}>
                        <Input placeholder='规则名' value={r.name} style={{width: 150}}
                            onChange={(e) => {const nr = [...ruleSet.rules]; nr[i] = {...r, name: e.target.value}; setRules(nr);}}/>
                        <Space>
                            <Text type='secondary'>优先级</Text>
                            <InputNumber value={r.priority} style={{width: 70}}
                                onChange={(v) => {const nr = [...ruleSet.rules]; nr[i] = {...r, priority: v ?? 0}; setRules(nr);}}/>
                            <Button size='small' danger icon={<DeleteOutlined/>} onClick={() => setRules(ruleSet.rules.filter((_, j) => j !== i))}/>
                        </Space>
                    </Space>
                    <div style={{marginTop: 6}}><Text type='secondary' style={{fontSize: 11}}>condition(返回 boolean,CEL 表达式)</Text></div>
                    <ConditionEditor value={r.condition} onChange={(v) => {const nr = [...ruleSet.rules]; nr[i] = {...r, condition: v}; setRules(nr);}}/>
                    <ActionsEditor actions={r.actions || []} onChange={(actions) => {const nr = [...ruleSet.rules]; nr[i] = {...r, actions}; setRules(nr);}}/>
                </div>
            ))}
            <Button block icon={<PlusOutlined/>} onClick={() => setRules([...ruleSet.rules, {id: `rule${ruleSet.rules.length + 1}`, name: '', priority: 0, condition: '', actions: []}])}>添加规则</Button>
        </div>
    );
}
