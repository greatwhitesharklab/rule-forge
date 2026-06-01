import {Component, ReactNode} from 'react';
import {ThunkDispatch} from 'redux-thunk';

import {AlertRule, MonitoringAction} from '../action.js';
import {MonitoringState} from '../reducer.js';
import {saveAlertRule as saveAlertRuleAction, deleteAlertRule as deleteAlertRuleAction} from '../action.js';

interface AlertFormState {
    name: string;
    metricName: string;
    condition: string;
    threshold: number;
    durationMin: number;
    webhookUrl: string;
    cooldownMin: number;
}

interface AlertRulePanelState {
    showForm: boolean;
    editingRule: AlertRule | null;
    form: AlertFormState;
}

interface AlertRulePanelProps {
    alertRules: AlertRule[];
    dispatch: ThunkDispatch<MonitoringState, unknown, MonitoringAction>;
}

const EMPTY_FORM: AlertFormState = {
    name: '',
    metricName: 'rule.execution.latency',
    condition: 'GT',
    threshold: 5000,
    durationMin: 1,
    webhookUrl: '',
    cooldownMin: 10
};

export default class AlertRulePanel extends Component<AlertRulePanelProps, AlertRulePanelState> {
    constructor(props: AlertRulePanelProps) {
        super(props);
        this.state = {
            showForm: false,
            editingRule: null,
            form: {...EMPTY_FORM}
        };
    }

    openCreateForm(): void {
        this.setState({
            showForm: true,
            editingRule: null,
            form: {...EMPTY_FORM}
        });
    }

    openEditForm(rule: AlertRule): void {
        this.setState({
            showForm: true,
            editingRule: rule,
            form: {
                name: rule.name,
                metricName: rule.metricName,
                condition: rule.condition,
                threshold: rule.threshold,
                durationMin: rule.durationMin,
                webhookUrl: rule.webhookUrl,
                cooldownMin: rule.cooldownMin
            }
        });
    }

    handleSave(): void {
        const {form, editingRule} = this.state;
        const rule: AlertRule = {
            ...form,
            enabled: true
        };
        if (editingRule) rule.id = editingRule.id;
        this.props.dispatch(saveAlertRuleAction(rule, () => this.setState({showForm: false})));
    }

    handleDelete(id: number): void {
        if (window.confirm('确定删除此告警规则？')) {
            this.props.dispatch(deleteAlertRuleAction(id));
        }
    }

    render(): ReactNode {
        const {alertRules} = this.props;
        const {showForm, form} = this.state;

        return (
            <div style={{marginTop: 20}}>
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10}}>
                    <h4 style={{margin: 0}}>告警规则</h4>
                    <button onClick={() => this.openCreateForm()}
                            style={{padding: '4px 12px', fontSize: 12, cursor: 'pointer'}}>
                        + 新建规则
                    </button>
                </div>

                {showForm && (
                    <div style={{border: '1px solid #ddd', padding: 12, marginBottom: 10, background: '#fafafa'}}>
                        <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8}}>
                            <label>名称: <input value={form.name} onChange={e => this.setState({form: {...form, name: e.target.value}})} style={{width: '100%', padding: 4}}/></label>
                            <label>指标: <input value={form.metricName} onChange={e => this.setState({form: {...form, metricName: e.target.value}})} style={{width: '100%', padding: 4}}/></label>
                            <label>条件:
                                <select value={form.condition} onChange={e => this.setState({form: {...form, condition: e.target.value}})} style={{width: '100%', padding: 4}}>
                                    <option value="GT">大于 (GT)</option>
                                    <option value="GTE">大于等于 (GTE)</option>
                                    <option value="LT">小于 (LT)</option>
                                    <option value="LTE">小于等于 (LTE)</option>
                                    <option value="EQ">等于 (EQ)</option>
                                </select>
                            </label>
                            <label>阈值: <input type="number" value={form.threshold} onChange={e => this.setState({form: {...form, threshold: Number(e.target.value)}})} style={{width: '100%', padding: 4}}/></label>
                            <label>连续窗口数: <input type="number" value={form.durationMin} onChange={e => this.setState({form: {...form, durationMin: Number(e.target.value)}})} style={{width: '100%', padding: 4}}/></label>
                            <label>冷却时间(分): <input type="number" value={form.cooldownMin} onChange={e => this.setState({form: {...form, cooldownMin: Number(e.target.value)}})} style={{width: '100%', padding: 4}}/></label>
                            <label style={{gridColumn: '1 / -1'}}>Webhook URL: <input value={form.webhookUrl} onChange={e => this.setState({form: {...form, webhookUrl: e.target.value}})} style={{width: '100%', padding: 4}} placeholder="http://"/></label>
                        </div>
                        <div style={{marginTop: 8, textAlign: 'right'}}>
                            <button onClick={() => this.setState({showForm: false})} style={{marginRight: 8, padding: '4px 12px'}}>取消</button>
                            <button onClick={() => this.handleSave()} style={{padding: '4px 12px', background: '#5470c6', color: '#fff', border: 'none', cursor: 'pointer'}}>保存</button>
                        </div>
                    </div>
                )}

                <table style={{width: '100%', borderCollapse: 'collapse', fontSize: 12}}>
                    <thead>
                    <tr style={{background: '#f5f5f5'}}>
                        <th style={{padding: 6, textAlign: 'left', borderBottom: '1px solid #ddd'}}>名称</th>
                        <th style={{padding: 6, textAlign: 'left', borderBottom: '1px solid #ddd'}}>指标</th>
                        <th style={{padding: 6, textAlign: 'left', borderBottom: '1px solid #ddd'}}>条件</th>
                        <th style={{padding: 6, textAlign: 'left', borderBottom: '1px solid #ddd'}}>阈值</th>
                        <th style={{padding: 6, textAlign: 'left', borderBottom: '1px solid #ddd'}}>状态</th>
                        <th style={{padding: 6, textAlign: 'left', borderBottom: '1px solid #ddd'}}>操作</th>
                    </tr>
                    </thead>
                    <tbody>
                    {(alertRules || []).map(rule => (
                        <tr key={rule.id} style={{borderBottom: '1px solid #eee'}}>
                            <td style={{padding: 6}}>{rule.name}</td>
                            <td style={{padding: 6}}>{rule.metricName}</td>
                            <td style={{padding: 6}}>{rule.condition}</td>
                            <td style={{padding: 6}}>{rule.threshold}</td>
                            <td style={{padding: 6}}>{rule.enabled ? '启用' : '禁用'}</td>
                            <td style={{padding: 6}}>
                                <button onClick={() => this.openEditForm(rule)} style={{marginRight: 4, fontSize: 11, cursor: 'pointer'}}>编辑</button>
                                <button onClick={() => this.handleDelete(rule.id!)} style={{fontSize: 11, color: '#ee6666', cursor: 'pointer'}}>删除</button>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
        );
    }
}
