import {Component} from 'react';
import {Tag, Select, Empty} from 'antd';
import {jsonPost} from '@/api/client';

// ====== 类型定义 ======

interface AuditRecord {
    id: number;
    sessionId: string | null;
    messageId: string | null;
    userId: string;
    toolName: string;
    argsSummary: string | null;
    resultSize: number;
    status: 'OK' | 'ERROR' | 'RATE_LIMITED';
    errorCode: string | null;
    errorMessage: string | null;
    durationMs: number;
    at: string;
}

interface AgentAuditViewProps {
    username?: string;  // 默认只看自己
}

interface AgentAuditViewState {
    rows: AuditRecord[];
    loading: boolean;
    errorMsg: string | null;
    statusFilter: 'ALL' | 'OK' | 'ERROR' | 'RATE_LIMITED';
    scope: 'me' | 'all';
}

/**
 * V5.22.3 — Agent 工具调用历史
 *
 * 调 list_agent_audit tool,列出 nd_agent_audit 表里的调用记录。
 * 默认只看自己调过的(按 username 过滤),可切到"全部"。
 */
export default class AgentAuditView extends Component<AgentAuditViewProps, AgentAuditViewState> {

    state: AgentAuditViewState = {
        rows: [],
        loading: false,
        errorMsg: null,
        statusFilter: 'ALL',
        scope: 'me',
    };

    componentDidMount() {
        this.load();
    }

    load = async () => {
        this.setState({loading: true, errorMsg: null});
        try {
            const body: Record<string, any> = {limit: 50};
            if (this.state.scope === 'me' && this.props.username) {
                body.userId = this.props.username;
            }
            if (this.state.statusFilter !== 'ALL') {
                body.status = this.state.statusFilter;
            }
            const data = await jsonPost<{audits: AuditRecord[]; count: number}>(
                '/agent/tools/list_agent_audit', body, {silent: true}
            );
            this.setState({rows: data.audits || [], loading: false});
        } catch (e) {
            this.setState({errorMsg: '加载审计失败: ' + (e as Error).message, loading: false});
        }
    };

    handleStatusFilterChange = (statusFilter: AgentAuditViewState['statusFilter']) => {
        this.setState({statusFilter}, () => this.load());
    };

    handleScopeChange = (scope: AgentAuditViewState['scope']) => {
        this.setState({scope}, () => this.load());
    };

    statusTag(s: string) {
        if (s === 'OK') return <Tag color="green">OK</Tag>;
        if (s === 'ERROR') return <Tag color="red">ERROR</Tag>;
        if (s === 'RATE_LIMITED') return <Tag color="orange">限流</Tag>;
        return <Tag>{s}</Tag>;
    }

    renderToolbar() {
        return (
            <div style={{display: 'flex', gap: 8, padding: '8px 12px', borderBottom: '1px solid #e8e8e8', alignItems: 'center', fontSize: 12}}>
                <span style={{color: '#666'}}>范围:</span>
                <Select
                    size="small"
                    style={{width: 110}}
                    value={this.state.scope}
                    onChange={this.handleScopeChange}
                    options={[
                        {value: 'me', label: this.props.username || '我'},
                        {value: 'all', label: '全部'},
                    ]}
                />
                <span style={{color: '#666'}}>状态:</span>
                <Select
                    size="small"
                    style={{width: 120}}
                    value={this.state.statusFilter}
                    onChange={this.handleStatusFilterChange}
                    options={[
                        {value: 'ALL', label: '全部'},
                        {value: 'OK', label: 'OK'},
                        {value: 'ERROR', label: 'ERROR'},
                        {value: 'RATE_LIMITED', label: '限流'},
                    ]}
                />
                <span style={{marginLeft: 'auto', color: '#999', fontSize: 11}}>
                    {this.state.rows.length} 条记录
                </span>
                <button className="btn btn-xs btn-default" onClick={this.load} disabled={this.state.loading}>
                    <i className="glyphicon glyphicon-refresh" />
                </button>
            </div>
        );
    }

    renderRow(a: AuditRecord) {
        return (
            <div key={a.id} style={{
                padding: 8, background: '#fff', border: '1px solid #e8e8e8',
                borderRadius: 4, marginBottom: 4, fontSize: 12,
            }}>
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4}}>
                    <div>
                        <code style={{background: '#f0f0f0', padding: '1px 6px', borderRadius: 3, marginRight: 6}}>
                            {a.toolName}
                        </code>
                        {this.statusTag(a.status)}
                        <span style={{color: '#999', fontSize: 10, marginLeft: 6}}>
                            {a.durationMs}ms · {a.resultSize}B
                        </span>
                    </div>
                    <span style={{color: '#999', fontSize: 11}}>
                        {a.at?.substring(0, 16).replace('T', ' ')}
                    </span>
                </div>
                <div style={{fontSize: 11, color: '#666'}}>
                    <span>by {a.userId}</span>
                    {a.sessionId && <span style={{marginLeft: 8}}>· 会话 {a.sessionId.substring(0, 8)}</span>}
                </div>
                {a.argsSummary && (
                    <div style={{fontSize: 10, color: '#999', marginTop: 4, fontFamily: 'monospace',
                                 background: '#fafafa', padding: 4, borderRadius: 3, overflow: 'hidden',
                                 textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}>
                        {a.argsSummary}
                    </div>
                )}
                {a.errorMessage && (
                    <div style={{fontSize: 11, color: '#cf1322', marginTop: 4, padding: 4, background: '#fff1f0', borderRadius: 3}}>
                        ❌ {a.errorCode && <strong>{a.errorCode}</strong>} {a.errorMessage}
                    </div>
                )}
            </div>
        );
    }

    render() {
        const {rows, loading, errorMsg} = this.state;

        return (
            <div style={{display: 'flex', flexDirection: 'column', height: '100%', overflow: 'auto'}}>
                {this.renderToolbar()}

                {errorMsg && (
                    <div style={{padding: '8px 12px', background: '#fff1f0', color: '#cf1322', fontSize: 12}}>
                        {errorMsg}
                    </div>
                )}

                <div style={{padding: 12, flex: 1}}>
                    {loading && rows.length === 0 ? (
                        <div style={{padding: 40, textAlign: 'center', color: '#999'}}>
                            <i className="glyphicon glyphicon-refresh" /> 加载中...
                        </div>
                    ) : rows.length === 0 ? (
                        <Empty description="暂无调用记录" />
                    ) : (
                        <>{rows.map(r => this.renderRow(r))}</>
                    )}
                </div>
            </div>
        );
    }
}
