import {Component, ReactNode} from 'react';
import {connect} from 'react-redux';
import {Card, Col, Empty, Row, Statistic, Tabs} from 'antd';
import PageShell from '@/frame/components/PageShell';
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

/**
 * UX-B3 重写:V5.8.4 起专用面板撑满整个 content 区,老实现仍用 240px 侧栏的
 * side-panel-nav 样式 —— 导航被横向拉满、P95/成功率/告警 三个指标沉到页面左下角。
 * 现改为 PageShell + Tabs 骨架(与 GitStatusPanel 一致),指标用 antd Statistic 卡片呈现。
 *
 * <p>注意:实时 metrics 端点尚未接入,三指标沿用上版的占位值(-- / -- / 0)。
 */
class MonitoringPanel extends Component<MonitoringPanelProps> {
    handleTabChange(tabId: string) {
        this.props.dispatch(setMonitoringTab(tabId));
        if (this.props.onNavigate) {
            this.props.onNavigate(tabId);
        }
    }

    renderOverview() {
        const items = [
            {title: 'P95 延迟', value: '--'},
            {title: '成功率', value: '--'},
            {title: '活跃告警', value: 0},
        ];
        return (
            <Row gutter={[16, 16]} data-testid="monitoring-overview">
                {items.map(it => (
                    <Col xs={24} sm={8} key={it.title}>
                        <Card size="small">
                            <Statistic title={it.title} value={it.value}/>
                        </Card>
                    </Col>
                ))}
            </Row>
        );
    }

    render() {
        const {monitoringTab} = this.props;
        return (
            <PageShell
                title="监控告警"
                fill
                toolbar={
                    <Tabs
                        activeKey={monitoringTab}
                        onChange={(key: string) => this.handleTabChange(key)}
                        items={TABS.map(t => ({key: t.id, label: (<><span style={{marginRight: 6}}>{t.icon}</span>{t.label}</>)}))}
                    />
                }
            >
                <div style={{flex: 1, minHeight: 0, overflow: 'auto', padding: 16}}>
                    {monitoringTab === 'overview' ? this.renderOverview() : (
                        <Empty
                            description={monitoringTab === 'metrics' ? '指标数据接入中' : '告警规则接入中'}
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                    )}
                </div>
            </PageShell>
        );
    }
}

const selector = (state: { ui?: { monitoringTab?: string } }) => ({monitoringTab: (state.ui && state.ui.monitoringTab) || 'overview'});
export default connect(selector)(MonitoringPanel);
