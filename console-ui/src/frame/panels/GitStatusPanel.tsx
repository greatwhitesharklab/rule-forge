import {Component, ReactNode} from 'react';
import {connect} from 'react-redux';
import {Card, Col, Row, Statistic, Tabs} from 'antd';
import PageShell from '@/frame/components/PageShell';
import {getGitStatusSummary, getGitStatusRecent, GitStatusSummary, GitStatusFailure} from '@/api/client.js';
import {DashboardOutlined, ProfileOutlined} from '@ant-design/icons';

interface TabDef {
    id: string;
    label: string;
    icon: ReactNode;
}

const TABS: TabDef[] = [
    {id: 'summary', label: '健康概览', icon: <DashboardOutlined />,},
    {id: 'recent', label: '最近失败', icon: <ProfileOutlined />,},
];

interface GitStatusPanelProps {
    gitStatusTab: string;
    dispatch: Function;
    onNavigate?: (tabId: string) => void;
}

interface GitStatusPanelState {
    summary: GitStatusSummary | null;
    recent: GitStatusFailure[];
    loading: boolean;
    error: string | null;
    lastRefresh: Date | null;
}

const POLL_INTERVAL_MS = 30_000;

class GitStatusPanel extends Component<GitStatusPanelProps, GitStatusPanelState> {
    private timer: ReturnType<typeof setInterval> | null = null;

    state: GitStatusPanelState = {
        summary: null,
        recent: [],
        loading: true,
        error: null,
        lastRefresh: null,
    };

    componentDidMount() {
        this.refresh();
        this.timer = setInterval(() => this.refresh(), POLL_INTERVAL_MS);
    }

    componentWillUnmount() {
        if (this.timer) clearInterval(this.timer);
    }

    refresh() {
        Promise.all([getGitStatusSummary({silent: true}), getGitStatusRecent(50, {silent: true})])
            .then(([summary, recent]) => {
                this.setState({summary, recent, loading: false, error: null, lastRefresh: new Date()});
            })
            .catch((err: Error) => {
                this.setState({loading: false, error: err.message || 'unknown error'});
            });
    }

    handleTabClick(tabId: string) {
        this.props.dispatch({type: 'set_git_status_tab', tab: tabId});
        if (this.props.onNavigate) this.props.onNavigate(tabId);
    }

    /**
     * UX-B3:统计项改 antd Statistic 卡片 —— 原 git-status-* CSS 类在 V5.101 重设计后
     * 已无定义,统计项渲染成无样式纯文本堆叠。valueRender 里保留 data-testid 供测试锁定。
     */
    renderStat(title: string, testId: string, value: number | string) {
        return (
            <Col xs={24} sm={8}>
                <Card size="small">
                    <Statistic
                        title={title}
                        value={value}
                        valueRender={() => <span data-testid={testId}>{value}</span>}
                    />
                </Card>
            </Col>
        );
    }

    renderHeader() {
        const {summary, loading, error, lastRefresh} = this.state;
        const stat = (v: number | undefined) => (loading && !summary ? '--' : (v ?? 0));
        return (
            <div className="git-status-summary" data-testid="git-status-summary">
                <Row gutter={[16, 16]}>
                    {this.renderStat('总失败', 'git-status-total', stat(summary?.totalFailures))}
                    {this.renderStat('近 1h', 'git-status-last1h', stat(summary?.last1h))}
                    {this.renderStat('近 24h', 'git-status-last24h', stat(summary?.last24h))}
                </Row>
                {error ? <div data-testid="git-status-error" style={{color: '#ff4d4f', fontSize: 12, marginTop: 8}}>加载失败: {error}</div> : null}
                {lastRefresh ? (
                    <div style={{color: '#999', fontSize: 12, marginTop: 8}}>更新于 {lastRefresh.toLocaleTimeString()}</div>
                ) : null}
            </div>
        );
    }

    renderSummary() {
        const {summary} = this.state;
        if (!summary) return <div className="git-status-empty">加载中…</div>;
        const counterEntries = Object.entries(summary.counters || {});
        return (
            <div className="git-status-counters">
                <div className="git-status-counters-title">Micrometer counters</div>
                {counterEntries.length === 0 ? (
                    <div className="git-status-empty">无 counter 记录(dualWrite 还没被触发过)</div>
                ) : (
                    <ul className="git-status-counter-list">
                        {counterEntries.map(([k, v]) => (
                            <li key={k}>
                                <code>{k}</code> = <strong>{v}</strong>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        );
    }

    renderRecent() {
        const {recent, loading} = this.state;
        if (loading) return <div className="git-status-empty">加载中…</div>;
        if (recent.length === 0) return <div className="git-status-empty">无失败 (No failures)</div>;
        return (
            <table className="git-status-recent-table">
                <thead>
                    <tr>
                        <th>时间</th>
                        <th>文件</th>
                        <th>分支</th>
                        <th>异常</th>
                    </tr>
                </thead>
                <tbody>
                    {recent.map(r => (
                        <tr key={r.id}>
                            <td title={r.occurredAt}>{(r.occurredAt || '').slice(11, 19)}</td>
                            <td title={r.filePath} className="git-status-cell-path">
                                {(r.filePath || '').split('/').slice(-2).join('/')}
                            </td>
                            <td>{r.branch || '--'}</td>
                            <td title={r.errorMessage || ''}>{r.errorType}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        );
    }

    render(): ReactNode {
        const {gitStatusTab} = this.props;
        return (
            <PageShell
                title="Git 健康"
                fill
                toolbar={
                    <Tabs
                        activeKey={gitStatusTab}
                        onChange={(key: string) => this.handleTabClick(key)}
                        items={TABS.map(t => ({key: t.id, label: (<><span style={{marginRight: 6}}>{t.icon}</span>{t.label}</>)}))}
                    />
                }
            >
                <div style={{flexShrink: 0, padding: '16px 16px 0'}}>{this.renderHeader()}</div>
                <div className="git-status-body" style={{flex: 1, minHeight: 0, overflow: 'auto', padding: 16}}>
                    {gitStatusTab === 'recent' ? this.renderRecent() : this.renderSummary()}
                </div>
            </PageShell>
        );
    }
}

const selector = (state: { ui?: { gitStatusTab?: string } }) => ({
    gitStatusTab: (state.ui && state.ui.gitStatusTab) || 'summary',
});
export default connect(selector)(GitStatusPanel);
