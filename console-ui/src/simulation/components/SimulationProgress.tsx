import React, {Component, ReactNode} from 'react';
import {getSimulationProgress, SimulationProgressData} from '../action.js';

/**
 * 仿真进度面板 — 轮询显示 LOADING/RUNNING/COMPARING/COMPLETED 状态
 */
const STATUS_LABELS: Record<string, string> = {
    PENDING: '等待中',
    LOADING: '加载历史日志',
    RUNNING: '执行批量测试',
    COMPARING: '对比结果',
    COMPLETED: '已完成',
    FAILED: '执行失败',
    NOT_FOUND: '未找到'
};

const STATUS_COLORS: Record<string, string> = {
    PENDING: '#999',
    LOADING: '#2196F3',
    RUNNING: '#FF9800',
    COMPARING: '#9C27B0',
    COMPLETED: '#4CAF50',
    FAILED: '#F44336',
    NOT_FOUND: '#999'
};

interface SimulationProgressProps {
    runId: string | null;
}

interface SimulationProgressState {
    progress: SimulationProgressData | null;
    pollTimer: ReturnType<typeof setTimeout> | null;
}

class SimulationProgress extends Component<SimulationProgressProps, SimulationProgressState> {

    constructor(props: SimulationProgressProps) {
        super(props);
        this.state = {
            progress: null,
            pollTimer: null
        };
    }

    componentDidMount() {
        this.pollProgress();
    }

    componentDidUpdate(prevProps: SimulationProgressProps) {
        if (this.props.runId !== prevProps.runId) {
            this.stopPolling();
            this.pollProgress();
        }
    }

    componentWillUnmount() {
        this.stopPolling();
    }

    pollProgress() {
        if (!this.props.runId) return;
        const self = this;
        getSimulationProgress(this.props.runId, function (data) {
            self.setState({progress: data});
            // 继续轮询直到终态
            if (data.status && data.status !== 'COMPLETED' && data.status !== 'FAILED' && data.status !== 'NOT_FOUND') {
                const timer = setTimeout(function () {
                    self.pollProgress();
                }, 2000);
                self.setState({pollTimer: timer});
            }
        });
    }

    stopPolling() {
        if (this.state.pollTimer) {
            clearTimeout(this.state.pollTimer);
            this.setState({pollTimer: null});
        }
    }

    renderProgressBar(progress: SimulationProgressData): ReactNode {
        const totalLogs = progress.totalLogs || 0;
        const totalCompared = progress.totalCompared || 0;
        const pct = totalLogs > 0 ? Math.round(totalCompared / totalLogs * 100) : 0;
        return (
            <div style={{marginTop: 8}}>
                <div style={{
                    height: 8, backgroundColor: '#e0e0e0', borderRadius: 4, overflow: 'hidden'
                }}>
                    <div style={{
                        width: pct + '%', height: '100%',
                        backgroundColor: STATUS_COLORS[progress.status] || '#2196F3',
                        transition: 'width 0.3s'
                    }}/>
                </div>
                <div style={{fontSize: 11, color: '#666', marginTop: 4}}>
                    {totalCompared} / {totalLogs} ({pct}%)
                </div>
            </div>
        );
    }

    renderStats(progress: SimulationProgressData): ReactNode {
        // V5.101:rf-table KV 统计表 → div 列表(原是 key-value 统计,非记录表)
        const rows: Array<[string, ReactNode, string]> = [
            ['总偏差', <strong>{progress.totalDivergent || 0}</strong>, 'var(--rf-text-primary)'],
            ['偏差率', <strong>{(progress.divergenceRate || 0).toFixed(2)}%</strong>, 'var(--rf-text-primary)'],
            ['HIGH', <>{progress.highSeverityCount || 0}</>, 'var(--rf-danger)'],
            ['MEDIUM', <>{progress.mediumSeverityCount || 0}</>, 'var(--rf-warning)'],
            ['LOW', <>{progress.lowSeverityCount || 0}</>, 'var(--rf-primary)'],
        ];
        return (
            <div style={{marginTop: 12}}>
                <div style={{fontSize: 12, fontWeight: 600, marginBottom: 4}}>偏差统计</div>
                <div style={{display: 'flex', flexDirection: 'column', fontSize: 11}}>
                    {rows.map(([label, val, color]) => (
                        <div key={label} style={{display: 'flex', justifyContent: 'space-between', padding: '2px 0', color}}>
                            <span>{label}</span>
                            <span>{val}</span>
                        </div>
                    ))}
                </div>
            </div>
        );
    }

    render(): ReactNode {
        if (!this.props.runId) {
            return <div style={{color: '#999', fontSize: 12, padding: 8}}>请先启动仿真</div>;
        }
        const progress = this.state.progress;
        if (!progress) {
            return <div style={{color: '#999', fontSize: 12, padding: 8}}>加载中...</div>;
        }
        return (
            <div style={{padding: 4}}>
                <div style={{display: 'flex', alignItems: 'center', marginBottom: 8}}>
                    <span style={{
                        display: 'inline-block', width: 10, height: 10,
                        borderRadius: '50%', backgroundColor: STATUS_COLORS[progress.status] || '#999',
                        marginRight: 8
                    }}/>
                    <span style={{fontSize: 13, fontWeight: 'bold'}}>
                        {STATUS_LABELS[progress.status] || progress.status}
                    </span>
                </div>
                {this.renderProgressBar(progress)}
                {progress.status === 'COMPLETED' ? this.renderStats(progress) : null}
                {progress.errorMessage ? (
                    <div style={{marginTop: 8, color: '#F44336', fontSize: 11}}>{progress.errorMessage}</div>
                ) : null}
            </div>
        );
    }
}

export default SimulationProgress;
