import {Input, Select, Button, Space, Typography} from 'antd';
import {PlusOutlined, DeleteOutlined} from '@ant-design/icons';
import type {Action, ActionType, Arg} from './ruleAsset';

const {Text} = Typography;

/** V7.10 共享 action 编辑器 ACTION_TYPES(供 NodePropertyDrawer / RuleSetEditor 复用)。
 *  V7.9 加 INVOKE(al 动作库,V7.4.1b 后端 InvokeAction 反射 bean)。 */
export const ACTION_TYPES: ActionType[] = ['SET_VARIABLE', 'ADD_SCORE', 'SET_DECISION', 'REJECT', 'FLAG', 'INVOKE'];

/** V7.10 共享 ActionsEditor —— 规则 actions 列表编辑(type + 类型专属字段 + 删除;INVOKE 走 InvokeActionFields)。 */
export function ActionsEditor({actions, onChange}: {actions: Action[]; onChange: (a: Action[]) => void}) {
    const updateAction = (i: number, patch: Partial<Action>) => {
        const na = [...actions];
        na[i] = {...actions[i], ...patch};
        onChange(na);
    };
    return (
        <div style={{marginTop: 6}}>
            <Text type='secondary' style={{fontSize: 11}}>actions(命中时执行)</Text>
            {actions.map((a, i) => (
                <Space key={i} style={{display: 'flex', marginBottom: 4}} size={4} wrap>
                    <Select value={a.type} options={ACTION_TYPES.map((t) => ({value: t, label: t}))} style={{width: 130}}
                        onChange={(v) => updateAction(i, {type: v})}/>
                    {(a.type === 'SET_VARIABLE' || a.type === 'ADD_SCORE') && <Input placeholder='target' value={a.target} style={{width: 100}}
                        onChange={(e) => updateAction(i, {target: e.target.value})}/>}
                    {(a.type === 'SET_VARIABLE' || a.type === 'ADD_SCORE' || a.type === 'SET_DECISION') && <Input placeholder='value' value={String(a.value ?? '')} style={{width: 90}}
                        onChange={(e) => updateAction(i, {value: e.target.value})}/>}
                    {(a.type === 'REJECT' || a.type === 'FLAG') && <Input placeholder='reason' value={a.reason} style={{width: 120}}
                        onChange={(e) => updateAction(i, {reason: e.target.value})}/>}
                    {a.type === 'INVOKE' && <InvokeActionFields action={a} onChange={(patch) => updateAction(i, patch)}/>}
                    <Button size='small' danger icon={<DeleteOutlined/>} onClick={() => onChange(actions.filter((_, j) => j !== i))}/>
                </Space>
            ))}
            <Button size='small' type='dashed' icon={<PlusOutlined/>} style={{marginTop: 4}}
                onClick={() => onChange([...actions, {type: 'SET_VARIABLE', target: '', value: ''}])}>+ action</Button>
        </div>
    );
}

/** V7.9 al 动作库:INVOKE 专用字段(ref="beanId.method" + 可选 target 写回 + args 列表)。
 *  镜像后端 V7.4.1b {@code InvokeAction} 反射调 bean。 */
export function InvokeActionFields({action, onChange}: {action: Action; onChange: (patch: Partial<Action>) => void}) {
    const args = action.args || [];
    const updateArg = (j: number, patch: Partial<Arg>) => {
        const next = [...args];
        next[j] = {...args[j], ...patch};
        onChange({args: next});
    };
    return (
        <Space direction='vertical' size={2} style={{width: '100%'}}>
            <Space size={4} wrap>
                <Input placeholder='ref (beanId.method)' value={action.ref ?? ''} style={{width: 180}}
                    onChange={(e) => onChange({ref: e.target.value})}/>
                <Input placeholder='target (写回字段,可空)' value={action.target ?? ''} style={{width: 170}}
                    onChange={(e) => onChange({target: e.target.value})}/>
            </Space>
            <div>
                <Text type='secondary' style={{fontSize: 11}}>args(name + 字面量 value / 字段 ref)</Text>
                {args.map((arg, j) => (
                    <Space key={j} size={4} style={{marginTop: 2}} wrap>
                        <Input placeholder='name' value={arg.name} style={{width: 80}}
                            onChange={(e) => updateArg(j, {name: e.target.value})}/>
                        <Input placeholder='value(字面量)' value={String(arg.value ?? '')} style={{width: 110}}
                            onChange={(e) => updateArg(j, {value: e.target.value})}/>
                        <Input placeholder='ref(字段引用)' value={arg.ref ?? ''} style={{width: 110}}
                            onChange={(e) => updateArg(j, {ref: e.target.value})}/>
                        <Button size='small' danger icon={<DeleteOutlined/>} onClick={() => onChange({args: args.filter((_, k) => k !== j)})}/>
                    </Space>
                ))}
                <Button size='small' type='dashed' style={{marginTop: 2}} icon={<PlusOutlined/>}
                    onClick={() => onChange({args: [...args, {name: '', value: ''}]})}>+ arg</Button>
            </div>
        </Space>
    );
}