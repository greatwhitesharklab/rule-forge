import {useState, useEffect, useCallback} from 'react';
import {Input, Select, Button, Space, Typography, message, Divider, Form, InputNumber, theme, Modal, Alert} from 'antd';
import {PlusOutlined, DeleteOutlined, SaveOutlined, ImportOutlined} from '@ant-design/icons';
import {formPost} from '@/api/client';
import ConditionEditor from './ConditionEditor';
import {ActionsEditor} from './ActionEditor';
import {parseDrlToRuleSet, type ImportedRuleSet} from './drlImport';
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
    /** V7.18 DRL 导入 Modal 状态。 */
    const [drlImportOpen, setDrlImportOpen] = useState(false);
    const [drlText, setDrlText] = useState('');
    const [drlResult, setDrlResult] = useState<{ruleSet: ImportedRuleSet; warnings: string[]} | null>(null);

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
                <Button size='small' icon={<ImportOutlined/>} onClick={() => { setDrlText(''); setDrlResult(null); setDrlImportOpen(true); }} data-testid='v1-drl-import-btn'>导入 DRL</Button>
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
            <Modal
                title='导入 DRL → V1 RuleSet(V7.18 极简子集)'
                open={drlImportOpen} onCancel={() => setDrlImportOpen(false)}
                footer={
                    <Space>
                        <Button size='small' onClick={() => { setDrlText(''); setDrlResult(null); }}>清空</Button>
                        <Button size='small' type='primary' onClick={() => setDrlResult(parseDrlToRuleSet(drlText))} data-testid='v1-drl-parse-btn'>解析</Button>
                        <Button size='small' type='primary' disabled={!drlResult || drlResult.ruleSet.rules.length === 0}
                            onClick={() => {
                                if (!drlResult) return;
                                setRuleSet({
                                    id: drlResult.ruleSet.id,
                                    type: 'RuleSet',
                                    name: drlResult.ruleSet.name,
                                    hitPolicy: 'FIRST',
                                    rules: drlResult.ruleSet.rules.map((r) => ({
                                        id: r.id, name: r.name, priority: r.priority, condition: r.condition, actions: r.actions as Action[],
                                    })),
                                });
                                setDrlImportOpen(false);
                                message.success(`已应用 ${drlResult.ruleSet.rules.length} 条 rule 到编辑器`);
                            }}
                            data-testid='v1-drl-apply-btn'>应用</Button>
                    </Space>
                }
                width={640}
            >
                <Text type='secondary' style={{fontSize: 12}}>
                    粘贴 DRL 文本 → 解析为 V1 RuleSet。支持子集:rule/when/then/end,
                    条件(==/!=/&gt;=/&lt;=/&gt;/&lt;)字面量,动作 setDecision/reject/set。
                    不支持(||/OR、modify() 块、$var 绑定、accumulate、function)→ warning 跳过。
                </Text>
                <Input.TextArea
                    data-testid='v1-drl-text'
                    value={drlText}
                    onChange={(e) => setDrlText(e.target.value)}
                    rows={10}
                    placeholder={'rule "approveAdult" salience 100\nwhen\n  age >= 18\nthen\n  setDecision("approve");\nend'}
                    style={{fontFamily: 'monospace', fontSize: 12, marginTop: 8}}
                />
                {drlResult && (
                    <div style={{marginTop: 12}}>
                        <Alert
                            type={drlResult.ruleSet.rules.length > 0 ? 'success' : 'warning'}
                            showIcon
                            title={`解析完成:${drlResult.ruleSet.rules.length} 条 rule`}
                            description={drlResult.warnings.length > 0
                                ? <ul style={{margin: 0, paddingLeft: 20}}>{drlResult.warnings.map((w, i) => <li key={i}>{w}</li>)}</ul>
                                : '无 warning'}
                        />
                    </div>
                )}
            </Modal>
        </div>
    );
}
