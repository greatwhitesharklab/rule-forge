import React, {Component} from 'react';
import {connect} from 'react-redux';
import {setSimulationTab} from '@/frame/action.js';
import SimulationConfigForm from './components/SimulationConfigForm.jsx';
import SimulationProgress from './components/SimulationProgress.jsx';
import SimulationResultsTable from './components/SimulationResultsTable.jsx';
import SimulationStatsPanel from './components/SimulationStatsPanel.jsx';

const TABS = [
    {id: 'configure', label: '仿真配置', icon: 'glyphicon glyphicon-cog'},
    {id: 'progress', label: '执行进度', icon: 'glyphicon glyphicon-tasks'},
    {id: 'results', label: '对比结果', icon: 'glyphicon glyphicon-list-alt'},
    {id: 'stats', label: '统计分析', icon: 'glyphicon glyphicon-stats'},
];

class SimulationPanel extends Component {

    constructor(props) {
        super(props);
        this.state = {
            currentRunId: null,
            currentPackagePath: null
        };
    }

    handleTabClick(tabId) {
        this.props.dispatch(setSimulationTab(tabId));
    }

    handleSimulationStarted(data) {
        if (data && data.runId) {
            this.setState({currentRunId: data.runId});
            this.props.dispatch(setSimulationTab('progress'));
        }
    }

    handleRunSelected(runId, packagePath) {
        this.setState({currentRunId: runId, currentPackagePath: packagePath});
    }

    renderContent() {
        var tab = this.props.simulationTab || 'configure';
        switch (tab) {
            case 'configure':
                return <SimulationConfigForm onStarted={this.handleSimulationStarted.bind(this)}/>;
            case 'progress':
                return <SimulationProgress runId={this.state.currentRunId}/>;
            case 'results':
                return <SimulationResultsTable runId={this.state.currentRunId}/>;
            case 'stats':
                return <SimulationStatsPanel packagePath={this.state.currentPackagePath}/>;
            default:
                return <SimulationConfigForm onStarted={this.handleSimulationStarted.bind(this)}/>;
        }
    }

    render() {
        var simulationTab = this.props.simulationTab || 'configure';
        return (
            <div className="simulation-panel" style={{height: '100%', display: 'flex', flexDirection: 'column'}}>
                <div className="side-panel-header">规则仿真</div>
                <div className="side-panel-nav">
                    {TABS.map(function (tab) {
                        return (
                            <div key={tab.id}
                                 className={'side-panel-nav-item' + (simulationTab === tab.id ? ' active' : '')}
                                 onClick={this.handleTabClick.bind(this, tab.id)}>
                                <i className={tab.icon} style={{marginRight: 8, fontSize: 12}}/>
                                {tab.label}
                            </div>
                        );
                    }.bind(this))}
                </div>
                <div style={{flex: 1, overflow: 'auto', padding: 8}}>
                    {this.renderContent()}
                </div>
            </div>
        );
    }
}

var selector = function (state) {
    return {simulationTab: (state.ui && state.ui.simulationTab) || 'configure'};
};
export default connect(selector)(SimulationPanel);
