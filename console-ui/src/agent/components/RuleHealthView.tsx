import {Component} from 'react';
import {Progress, Tag, Alert} from 'antd';
import {jsonPost} from '@/api/client';

// ====== 类型定义 ======

interface HealthData {
    status?: 'OK' | 'PARTIAL' | 'DEGRADED';   // V5.22.3
    failedSources?: string[];                  // V5.22.3
    failedSourceCount?: number;                // V5.22.3
    totalSourceCount?: number;                 // V5.22.3
    project: string;
    days: number;
    generatedAt: string;
    coverage: {
        totalRules?: number;
        activeRules?: number;
        deadRules?: number;
        hotRules?: any[];
    };
    hotRules: Array<{ruleId: string; fireCount: number; [k: string]: any}>;
    recentAnomalies: Array<{type: string; severity: string; message?: string; [k: string]: any}>;
    topRejectReasons: Array<{reason: string; count: number; [k: string]: any}>;
    staleDrafts: Array<{
        draftId: string;
        title: string | null;
        status: string;
        project: string;
        daysOld: number;
        createdBy: string;
    }>;
    staleDraftCount: number;
}

interface RuleHealthViewProps {
    project?: string;
}

interface RuleHealthViewState {
    data: HealthData | null;
    loading: boolean;
    errorMsg: string | null;
    days: number;
}

/**
 * V5.22.2 — 规则健康仪表盘
 * V5.22.3 — 加 antd Progress / Tag / Alert:
 *          - 覆盖率卡片加 Progress 条
 *          - 顶部加 status 横幅(OK / PARTIAL / DEGRADED)
 *          - 失败 source 列表
 */
export default class RuleHealthView extends Component<RuleHealthViewProps, RuleHealthViewState> {

    state: RuleHealthViewState = {
        data: null,
        loading: false,
        errorMsg: null,
        days: 30,
    };

    componentDidMount() {
        this.load();
    }

    componentDidUpdate(prev: RuleHealthViewProps) {
        if (prev.project !== this.props.project) {
            this.load();
        }
    }

    load = async () => {
        this.setState({loading: true, errorMsg: null});
        try {
            const body: Record<string, any> = {days: this.state.days};
            if (this.props.project) body.project = this.props.project;
            const data = await jsonPost<HealthData>('/agent/tools/get_rule_health', body, {silent: true});
            this.setState({data, loading: false});
        } catch (e) {
            this.setState({errorMsg: '加载健康数据失败: ' + (e as Error).message, loading: false});
        }
    };

    handleDaysChange = (days: number) => {
        this.setState({days}, () => this.load());
    };

    renderStatusBanner() {
        const status = this.state.data?.status || 'OK';
        if (status === 'OK') return null;

        const failedSources = this.state.data?.failedSources || [];
        const isDegraded = status === 'DEGRADED';

        return (
            <div style={{padding: '6px 12px'}}>
                <Alert
                    type={isDegraded ? 'error' : 'warning'}
                    showIcon
                    title={
                        isDegraded
                            ? '健康数据源全部不可用 — 显示空为正常,稍后重试'
                            : `健康数据源部分失败 (${failedSources.length}/${this.state.data?.totalSourceCount || '?'})`
                    }
                    description={
                        failedSources.length > 0 && (
                            <span>
                                失败来源: {failedSources.map(s => <Tag key={s} color="orange">{s}</Tag>)}
                            </span>
                        )
                    }
                />
            </div>
        );
    }

    renderCoverage() {
        const c = this.state.data?.coverage;
        if (!c) return null;
        const total = c.totalRules || 0;
        const dead = c.deadRules || 0;
        const active = c.activeRules || (total - dead);
        const deadPct = total > 0 ? Math.round((dead / total) * 100) : 0;
        const activePct = total > 0 ? Math.round((active / total) * 100) : 0;
        return (
            <div style={{display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 16}}>
                <div style={{padding: 12, background: '#f0f5ff', borderRadius: 4, border: '1px solid #d6e4ff'}}>
                    <div style={{fontSize: 11, color: '#666'}}>总规则数</div>
                    <div style={{fontSize: 22, fontWeight: 600, color: '#1677ff'}}>{total}</div>
                </div>
                <div style={{padding: 12, background: '#f6ffed', borderRadius: 4, border: '1px solid #b7eb8f'}}>
                    <div style={{fontSize: 11, color: '#666', marginBottom: 4}}>活跃</div>
                    <div style={{fontSize: 18, fontWeight: 600, color: '#389e0d', marginBottom: 4}}>{active}</div>
                    <Progress
                        percent={activePct}
                        size="small"
                        strokeColor="#389e0d"
                        showInfo={false}
                    />
                    <div style={{fontSize: 10, color: '#999', marginTop: 2}}>{activePct}% 触发率</div>
                </div>
                <div style={{padding: 12, background: dead > 0 ? '#fff1f0' : '#fafafa', borderRadius: 4, border: '1px solid ' + (dead > 0 ? '#ffa39e' : '#e8e8e8')}}>
                    <div style={{fontSize: 11, color: '#666', marginBottom: 4}}>死规则</div>
                    <div style={{fontSize: 18, fontWeight: 600, color: dead > 0 ? '#cf1322' : '#999', marginBottom: 4}}>{dead}</div>
                    <Progress
                        percent={deadPct}
                        size="small"
                        strokeColor={dead > 0 ? '#cf1322' : '#999'}
                        showInfo={false}
                    />
                    <div style={{fontSize: 10, color: '#999', marginTop: 2}}>{deadPct}% 占比{dead > 0 ? ' — 建议清理' : ''}</div>
                </div>
            </div>
        );
    }

    renderStaleDrafts() {
        const drafts = this.state.data?.staleDrafts || [];
        const count = this.state.data?.staleDraftCount || 0;
        if (count === 0) {
            return (
                <div style={{padding: 12, fontSize: 12, color: '#52c41a', background: '#f6ffed', borderRadius: 4, marginBottom: 12}}>
                    ✅ 没有滞留草稿
                </div>
            );
        }
        return (
            <div style={{marginBottom: 16}}>
                <div style={{fontSize: 12, fontWeight: 600, color: '#cf1322', marginBottom: 4}}>
                    ⚠️ 滞留草稿 ({count})
                </div>
                {drafts.map(d => (
                    <div key={d.draftId} style={{
                        padding: 8, background: '#fff7e6', border: '1px solid #ffd591',
                        borderRadius: 4, marginBottom: 4, fontSize: 12,
                    }}>
                        <div style={{fontWeight: 500}}>{d.title || d.draftId}</div>
                        <div style={{fontSize: 10, color: '#999', marginTop: 2}}>
                            {d.status} · {d.project} · {d.daysOld} 天前 · {d.createdBy}
                        </div>
                    </div>
                ))}
            </div>
        );
    }

    renderAnomalies() {
        const anomalies = this.state.data?.recentAnomalies || [];
        if (anomalies.length === 0) return null;
        return (
            <div style={{marginBottom: 16}}>
                <div style={{fontSize: 12, fontWeight: 600, color: '#666', marginBottom: 4}}>
                    最近异常 ({anomalies.length})
                </div>
                {anomalies.slice(0, 5).map((a, i) => {
                    const color = a.severity === 'high' ? '#cf1322' : a.severity === 'medium' ? '#d46b08' : '#666';
                    return (
                        <div key={i} style={{
                            padding: 8, background: '#fff', border: '1px solid #e8e8e8',
                            borderLeft: `3px solid ${color}`, borderRadius: 4, marginBottom: 4, fontSize: 12,
                        }}>
                            <div style={{fontWeight: 500, color}}>
                                {a.type} <Tag color={a.severity === 'high' ? 'red' : a.severity === 'medium' ? 'orange' : 'default'} style={{marginLeft: 4}}>{a.severity}</Tag>
                            </div>
                            {a.message && <div style={{fontSize: 11, color: '#666', marginTop: 2}}>{a.message}</div>}
                        </div>
                    );
                })}
            </div>
        );
    }

    renderHotRules() {
        const hot = this.state.data?.hotRules || [];
        if (hot.length === 0) return null;
        const maxFire = hot.length > 0 ? Math.max(...hot.map(r => r.fireCount || 0)) : 1;
        return (
            <div style={{marginBottom: 16}}>
                <div style={{fontSize: 12, fontWeight: 600, color: '#666', marginBottom: 4}}>
                    🔥 热规则 Top {hot.length}
                </div>
                {hot.map((r, i) => {
                    const pct = maxFire > 0 ? Math.round(((r.fireCount || 0) / maxFire) * 100) : 0;
                    return (
                        <div key={i} style={{
                            padding: 6, background: '#fff', border: '1px solid #e8e8e8',
                            borderRadius: 3, marginBottom: 2, fontSize: 11,
                        }}>
                            <div style={{display: 'flex', justifyContent: 'space-between', marginBottom: 2}}>
                                <span><code>{r.ruleId}</code></span>
                                <span style={{color: '#d46b08', fontWeight: 600}}>{r.fireCount.toLocaleString()} 次</span>
                            </div>
                            <Progress percent={pct} size="small" strokeColor="#d46b08" showInfo={false} />
                        </div>
                    );
                })}
            </div>
        );
    }

    renderTopRejectReasons() {
        const reasons = this.state.data?.topRejectReasons || [];
        if (reasons.length === 0) return null;
        const maxCount = reasons.length > 0 ? Math.max(...reasons.map(r => r.count || 0)) : 1;
        return (
            <div style={{marginBottom: 16}}>
                <div style={{fontSize: 12, fontWeight: 600, color: '#666', marginBottom: 4}}>
                    Top 拒绝原因
                </div>
                {reasons.map((r, i) => {
                    const pct = maxCount > 0 ? Math.round(((r.count || 0) / maxCount) * 100) : 0;
                    return (
                        <div key={i} style={{
                            padding: 6, background: '#fff', border: '1px solid #e8e8e8',
                            borderRadius: 3, marginBottom: 2, fontSize: 11,
                        }}>
                            <div style={{display: 'flex', justifyContent: 'space-between', marginBottom: 2}}>
                                <span>{r.reason}</span>
                                <span style={{color: '#666'}}>{r.count.toLocaleString()} 次</span>
                            </div>
                            <Progress percent={pct} size="small" strokeColor="#888" showInfo={false} />
                        </div>
                    );
                })}
            </div>
        );
    }

    renderToolbar() {
        const status = this.state.data?.status || 'OK';
        const statusTag =
            status === 'DEGRADED' ? <Tag color="red">DEGRADED</Tag> :
            status === 'PARTIAL' ? <Tag color="orange">PARTIAL</Tag> :
            <Tag color="green">OK</Tag>;
        return (
            <div style={{display: 'flex', gap: 6, padding: '8px 12px', borderBottom: '1px solid #e8e8e8', alignItems: 'center'}}>
                <span style={{fontSize: 11, color: '#666'}}>时间窗口:</span>
                {[7, 30, 90].map(d => (
                    <button key={d}
                            className={'btn btn-xs ' + (this.state.days === d ? 'btn-primary' : 'btn-default')}
                            onClick={() => this.handleDaysChange(d)}>
                        {d} 天
                    </button>
                ))}
                <span style={{marginLeft: 'auto', fontSize: 11, color: '#999'}}>
                    {statusTag}
                    {this.state.data?.project || 'all'} · 更新于 {this.state.data?.generatedAt?.substring(0, 16).replace('T', ' ')}
                </span>
                <button className="btn btn-xs btn-default" onClick={this.load} disabled={this.state.loading}>
                    <i className="glyphicon glyphicon-refresh" />
                </button>
            </div>
        );
    }

    render() {
        const {data, loading, errorMsg} = this.state;

        if (loading && !data) {
            return (
                <div style={{padding: 40, textAlign: 'center', color: '#999', fontSize: 12}}>
                    <i className="glyphicon glyphicon-refresh" /> 加载健康数据...
                </div>
            );
        }

        return (
            <div style={{display: 'flex', flexDirection: 'column', height: '100%', overflow: 'auto'}}>
                {this.renderToolbar()}
                {this.renderStatusBanner()}

                {errorMsg && (
                    <div style={{padding: '8px 12px', background: '#fff1f0', color: '#cf1322', fontSize: 12}}>
                        {errorMsg}
                    </div>
                )}

                <div style={{padding: 12, flex: 1}}>
                    {data && (
                        <>
                            {this.renderCoverage()}
                            {this.renderStaleDrafts()}
                            {this.renderAnomalies()}
                            {this.renderHotRules()}
                            {this.renderTopRejectReasons()}
                        </>
                    )}
                </div>
            </div>
        );
    }
}
