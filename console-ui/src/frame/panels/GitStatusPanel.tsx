import {Component, ReactNode} from 'react';
import {connect} from 'react-redux';
import {getGitStatusSummary, getGitStatusRecent, GitStatusSummary, GitStatusFailure} from '@/api/client.js';

interface TabDef {
    id: string;
    label: string;
    icon: string;
}

const TABS: TabDef[] = [
    {id: 'summary', label: '健康概览', icon: 'glyphicon glyphicon-dashboard'},
    {id: 'recent', label: '最近失败', icon: 'glyphicon glyphicon-list-alt'},
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

    renderHeader() {
        const {summary, loading, error, lastRefresh} = this.state;
        return (
            <div className="git-status-summary" data-testid="git-status-summary">
                <div className="git-status-summary-row">
                    <div className="git-status-cell">
                        <div className="git-status-cell-label">总失败</div>
                        <div className="git-status-cell-value" data-testid="git-status-total">
                            {loading && !summary ? '--' : (summary?.totalFailures ?? 0)}
                        </div>
                    </div>
                    <div className="git-status-cell">
                        <div className="git-status-cell-label">近 1h</div>
                        <div className="git-status-cell-value" data-testid="git-status-last1h">
                            {loading && !summary ? '--' : (summary?.last1h ?? 0)}
                        </div>
                    </div>
                    <div className="git-status-cell">
                        <div className="git-status-cell-label">近 24h</div>
                        <div className="git-status-cell-value" data-testid="git-status-last24h">
                            {loading && !summary ? '--' : (summary?.last24h ?? 0)}
                        </div>
                    </div>
                </div>
                {error ? <div className="git-status-error" data-testid="git-status-error">加载失败: {error}</div> : null}
                {lastRefresh ? (
                    <div className="git-status-refresh">更新于 {lastRefresh.toLocaleTimeString()}</div>
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
            <div className="git-status-panel" style={{height: '100%'}}>
                <div className="side-panel-header">Git 健康</div>
                <div className="side-panel-nav">
                    {TABS.map(tab => (
                        <div key={tab.id}
                             className={'side-panel-nav-item' + (gitStatusTab === tab.id ? ' active' : '')}
                             onClick={() => this.handleTabClick(tab.id)}>
                            <i className={tab.icon} style={{marginRight: 8, fontSize: 12}}/>
                            {tab.label}
                        </div>
                    ))}
                </div>
                {this.renderHeader()}
                <div className="git-status-body">
                    {gitStatusTab === 'recent' ? this.renderRecent() : this.renderSummary()}
                </div>
                <div style={{flex: 1}}/>
                <div className="git-status-status">
                    <div className="status-divider"/>
                    <div className="status-item">
                        <span className={'status-dot ' + (this.state.error
                            ? 'status-dot-red'
                            : this.state.summary && this.state.summary.last24h > 0
                                ? 'status-dot-yellow'
                                : 'status-dot-green')}/>
                        <span className="status-label">24h</span>
                        <span className="status-value">
                            {this.state.summary ? this.state.summary.last24h : '--'}
                        </span>
                    </div>
                    <div className="status-item">
                        <span className="status-dot status-dot-gray"/>
                        <span className="status-label">轮询</span>
                        <span className="status-value">{POLL_INTERVAL_MS / 1000}s</span>
                    </div>
                </div>
            </div>
        );
    }
}

const selector = (state: { ui?: { gitStatusTab?: string } }) => ({
    gitStatusTab: (state.ui && state.ui.gitStatusTab) || 'summary',
});
export default connect(selector)(GitStatusPanel);
