import React, {Component, ReactNode} from 'react';
import {loadSimulationStats, SimulationStatsData} from '../action.js';

/**
 * 仿真统计分析面板 — 聚合统计展示
 */

interface SimulationStatsPanelProps {
    packagePath: string | null;
}

interface SimulationStatsPanelState {
    stats: SimulationStatsData | null;
    loading: boolean;
}

class SimulationStatsPanel extends Component<SimulationStatsPanelProps, SimulationStatsPanelState> {

    constructor(props: SimulationStatsPanelProps) {
        super(props);
        this.state = {
            stats: null,
            loading: false
        };
    }

    componentDidMount() {
        if (this.props.packagePath) {
            this.loadStats();
        }
    }

    componentDidUpdate(prevProps: SimulationStatsPanelProps) {
        if (this.props.packagePath && this.props.packagePath !== prevProps.packagePath) {
            this.loadStats();
        }
    }

    loadStats() {
        const self = this;
        this.setState({loading: true});
        loadSimulationStats(this.props.packagePath!, null, null, function (data) {
            self.setState({stats: data, loading: false});
        });
    }

    renderStatCard(label: string, value: string | number, unit: string | null, color?: string): ReactNode {
        return (
            <div style={{
                backgroundColor: '#f5f5f5', borderRadius: 4, padding: 8,
                marginBottom: 8, borderLeft: '3px solid ' + (color || '#2196F3')
            }}>
                <div style={{fontSize: 11, color: '#666'}}>{label}</div>
                <div style={{fontSize: 18, fontWeight: 'bold', color: color || '#333'}}>
                    {value}{unit ? <span style={{fontSize: 12}}>{unit}</span> : null}
                </div>
            </div>
        );
    }

    render(): ReactNode {
        if (!this.props.packagePath) {
            return <div style={{color: '#999', fontSize: 12, padding: 8}}>请先运行仿真</div>;
        }
        if (this.state.loading) {
            return <div style={{color: '#999', fontSize: 12, padding: 8}}>加载中...</div>;
        }
        const stats = this.state.stats;
        if (!stats) {
            return <div style={{color: '#999', fontSize: 12, padding: 8}}>暂无统计数据</div>;
        }
        return (
            <div style={{padding: 4}}>
                {this.renderStatCard('仿真次数', stats.totalRuns || 0, '次', '#2196F3')}
                {this.renderStatCard('总日志数', stats.totalLogs || 0, '条', '#607D8B')}
                {this.renderStatCard('已对比', stats.totalCompared || 0, '条', '#4CAF50')}
                {this.renderStatCard('总偏差', stats.totalDivergent || 0, '条', '#F44336')}
                {this.renderStatCard('平均偏差率', (stats.averageDivergenceRate || 0).toFixed(2), '%', '#FF9800')}
            </div>
        );
    }
}

export default SimulationStatsPanel;
