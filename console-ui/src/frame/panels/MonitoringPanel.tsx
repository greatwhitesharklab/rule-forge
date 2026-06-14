import {Component, ReactNode} from 'react';
import {connect} from 'react-redux';
import {setMonitoringTab} from '@/frame/action.js';
import {BarChartOutlined, BellOutlined, DashboardOutlined} from '@ant-design/icons';

interface TabDef {
    id: string;
    label: string;
    icon: ReactNode;
}

const TABS: TabDef[] = [
    {id: 'overview', label: '总览仪表盘', icon: <DashboardOutlined />,},
    {id: 'metrics', label: '指标浏览', icon: <BarChartOutlined />,},
    {id: 'alerts', label: '告警规则', icon: <BellOutlined />,},
];

interface MonitoringPanelProps {
    monitoringTab: string;
    dispatch: Function;
    onNavigate?: (tabId: string) => void;
}

class MonitoringPanel extends Component<MonitoringPanelProps> {
    handleTabClick(tabId: string) {
        this.props.dispatch(setMonitoringTab(tabId));
        if (this.props.onNavigate) {
            this.props.onNavigate(tabId);
        }
    }

    render() {
        const {monitoringTab} = this.props;
        return (
            <div className="monitoring-panel" style={{height: '100%'}}>
                <div className="side-panel-header">监控告警</div>
                <div className="side-panel-nav">
                    {TABS.map(tab => (
                        <div key={tab.id}
                             className={'side-panel-nav-item' + (monitoringTab === tab.id ? ' active' : '')}
                             onClick={() => this.handleTabClick(tab.id)}>
                            <span style={{marginRight: 8, fontSize: 12}}>{tab.icon}</span>
                            {tab.label}
                        </div>
                    ))}
                </div>
                <div style={{flex: 1}}/>
                <div className="monitoring-panel-status">
                    <div className="status-divider"/>
                    <div className="status-item">
                        <span className="status-dot status-dot-green"/>
                        <span className="status-label">P95</span>
                        <span className="status-value">--</span>
                    </div>
                    <div className="status-item">
                        <span className="status-dot status-dot-green"/>
                        <span className="status-label">成功率</span>
                        <span className="status-value">--</span>
                    </div>
                    <div className="status-item">
                        <span className="status-dot status-dot-gray"/>
                        <span className="status-label">告警</span>
                        <span className="status-value">0</span>
                    </div>
                </div>
            </div>
        );
    }
}

const selector = (state: { ui?: { monitoringTab?: string } }) => ({monitoringTab: (state.ui && state.ui.monitoringTab) || 'overview'});
export default connect(selector)(MonitoringPanel);
