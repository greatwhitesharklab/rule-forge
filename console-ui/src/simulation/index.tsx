import React, {Component, ReactNode} from 'react';
import {connect} from 'react-redux';
import {setSimulationTab} from '@/frame/action.js';
import SimulationConfigForm from './components/SimulationConfigForm.jsx';
import SimulationProgress from './components/SimulationProgress.jsx';
import SimulationResultsTable from './components/SimulationResultsTable.jsx';
import SimulationStatsPanel from './components/SimulationStatsPanel.jsx';
import type {SimulationStartResult} from './action';
import {BarChartOutlined, DatabaseOutlined, ProfileOutlined, SettingOutlined} from '@ant-design/icons';

interface TabDef {
    id: string;
    label: string;
    icon: ReactNode;
}

const TABS: TabDef[] = [
    {id: 'configure', label: '仿真配置', icon: <SettingOutlined />,},
    {id: 'progress', label: '执行进度', icon: <DatabaseOutlined />,},
    {id: 'results', label: '对比结果', icon: <ProfileOutlined />,},
    {id: 'stats', label: '统计分析', icon: <BarChartOutlined />,},
];

interface SimulationPanelState {
    currentRunId: string | null;
    currentPackagePath: string | null;
}

interface SimulationPanelProps {
    dispatch: (action: any) => any;
    simulationTab: string;
    /** 当前选中的项目名(来自 frame store ui.projectName)。 */
    projectName?: string | null;
}

class SimulationPanel extends Component<SimulationPanelProps, SimulationPanelState> {

    constructor(props: SimulationPanelProps) {
        super(props);
        this.state = {
            currentRunId: null,
            currentPackagePath: null
        };
    }

    handleTabClick(tabId: string) {
        this.props.dispatch(setSimulationTab(tabId));
    }

    handleSimulationStarted(data: SimulationStartResult) {
        if (data && data.runId) {
            this.setState({currentRunId: data.runId});
            this.props.dispatch(setSimulationTab('progress'));
        }
    }

    handleRunSelected(runId: string, packagePath: string) {
        this.setState({currentRunId: runId, currentPackagePath: packagePath});
    }

    renderContent(): ReactNode {
        const tab = this.props.simulationTab || 'configure';
        switch (tab) {
            case 'configure':
                return <SimulationConfigForm project={this.props.projectName || ''} onStarted={this.handleSimulationStarted.bind(this)}/>;
            case 'progress':
                return <SimulationProgress runId={this.state.currentRunId}/>;
            case 'results':
                return <SimulationResultsTable runId={this.state.currentRunId}/>;
            case 'stats':
                return <SimulationStatsPanel packagePath={this.state.currentPackagePath}/>;
            default:
                return <SimulationConfigForm project={this.props.projectName || ''} onStarted={this.handleSimulationStarted.bind(this)}/>;
        }
    }

    render(): ReactNode {
        const simulationTab = this.props.simulationTab || 'configure';
        return (
            <div className="simulation-panel" style={{height: '100%', display: 'flex', flexDirection: 'column'}}>
                <div className="side-panel-header">规则仿真</div>
                <div className="side-panel-nav">
                    {TABS.map((tab) => {
                        return (
                            <div key={tab.id}
                                 className={'side-panel-nav-item' + (simulationTab === tab.id ? ' active' : '')}
                                 onClick={this.handleTabClick.bind(this, tab.id)}>
                                <span style={{marginRight: 8, fontSize: 12}}>{tab.icon}</span>
                                {tab.label}
                            </div>
                        );
                    })}
                </div>
                <div style={{flex: 1, overflow: 'auto', padding: 8}}>
                    {this.renderContent()}
                </div>
            </div>
        );
    }
}

const selector = function (state: any): SimulationPanelProps {
    return {
        simulationTab: (state.ui && state.ui.simulationTab) || 'configure',
        projectName: (state.ui && state.ui.projectName) ?? null,
    } as SimulationPanelProps;
};
export default connect(selector)(SimulationPanel);
