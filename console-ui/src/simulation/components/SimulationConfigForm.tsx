import React, {Component, ChangeEvent, ReactNode} from 'react';
import {Button, Input} from 'antd';
import {startSimulation, SimulationStartResult} from '../action.js';

import {alert} from '@/utils/modal';
interface SimulationConfigFormState {
    project: string;
    packageId: string;
    files: string;
    flowId: string;
    startTime: string;
    endTime: string;
    submitting: boolean;
}

interface SimulationConfigFormProps {
    /** 当前选中项目名(由 SimulationPanel 从 frame store 注入,替代 window._projectName)。 */
    project?: string;
    onStarted?: (data: SimulationStartResult) => void;
}

/**
 * 仿真配置表单 — 选择规则包 + 时间范围 → 启动仿真
 */
class SimulationConfigForm extends Component<SimulationConfigFormProps, SimulationConfigFormState> {

    constructor(props: SimulationConfigFormProps) {
        super(props);
        this.state = {
            project: props.project || '',
            packageId: '',
            files: '',
            flowId: '',
            startTime: '',
            endTime: '',
            submitting: false
        };
    }

    handleChange(field: keyof SimulationConfigFormState, e: ChangeEvent<HTMLInputElement>) {
        this.setState({[field]: e.target.value} as unknown as SimulationConfigFormState);
    }

    handleSubmit() {
        const state = this.state;
        if (!state.project || !state.packageId || !state.files || !state.startTime || !state.endTime) {
            alert('请填写所有必填字段');
            return;
        }
        this.setState({submitting: true});
        const self = this;
        startSimulation({
            project: state.project,
            packageId: state.packageId,
            files: state.files,
            flowId: state.flowId || null,
            startTime: state.startTime,
            endTime: state.endTime,
            createdBy: 'console'
        }, function (data: SimulationStartResult) {
            self.setState({submitting: false});
            if (data.error) {
                alert(data.message || '启动仿真失败');
            } else {
                if (self.props.onStarted) {
                    self.props.onStarted(data);
                }
            }
        });
    }

    render(): ReactNode {
        const state = this.state;
        return (
            <div style={{padding: 4}}>
                <div className="ff-group">
                    <label style={{fontSize: 12, color: '#666'}}>项目名称</label>
                    <Input type="text" size="small"
                           value={state.project}
                           onChange={this.handleChange.bind(this, 'project')}
                           placeholder="项目名"/>
                </div>
                <div className="ff-group">
                    <label style={{fontSize: 12, color: '#666'}}>包ID</label>
                    <Input type="text" size="small"
                           value={state.packageId}
                           onChange={this.handleChange.bind(this, 'packageId')}
                           placeholder="规则包ID"/>
                </div>
                <div className="ff-group">
                    <label style={{fontSize: 12, color: '#666'}}>规则文件（;分隔）</label>
                    <Input type="text" size="small"
                           value={state.files}
                           onChange={this.handleChange.bind(this, 'files')}
                           placeholder="a.xml,1;b.xml,2"/>
                </div>
                <div className="ff-group">
                    <label style={{fontSize: 12, color: '#666'}}>决策流ID（可选）</label>
                    <Input type="text" size="small"
                           value={state.flowId}
                           onChange={this.handleChange.bind(this, 'flowId')}
                           placeholder="flowId"/>
                </div>
                <div className="ff-group">
                    <label style={{fontSize: 12, color: '#666'}}>开始时间</label>
                    <Input type="date" size="small"
                           value={state.startTime}
                           onChange={this.handleChange.bind(this, 'startTime')}/>
                </div>
                <div className="ff-group">
                    <label style={{fontSize: 12, color: '#666'}}>结束时间</label>
                    <Input type="date" size="small"
                           value={state.endTime}
                           onChange={this.handleChange.bind(this, 'endTime')}/>
                </div>
                <Button type="primary" size="small" block
                        disabled={state.submitting}
                        onClick={this.handleSubmit.bind(this)}>
                    {state.submitting ? '启动中...' : '启动仿真'}
                </Button>
            </div>
        );
    }
}

export default SimulationConfigForm;
