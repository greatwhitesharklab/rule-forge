import React, {Component} from 'react';
import {createStore, applyMiddleware} from 'redux';
import {Provider, connect} from 'react-redux';
import thunk from 'redux-thunk';
import {createRoot} from 'react-dom/client';
import reducer from './reducer.js';
import * as action from './action.js';
import {getStartTime, getGranularity} from './helpers.js';

const echarts = window.echarts;
const store = createStore(reducer, applyMiddleware(thunk));

const TIME_RANGES = [
    {label: '1小时', value: '1h'},
    {label: '6小时', value: '6h'},
    {label: '24小时', value: '24h'},
    {label: '7天', value: '7d'},
    {label: '30天', value: '30d'}
];

const TABS = [
    {id: 'trend', label: '决策趋势', icon: 'glyphicon glyphicon-stats'},
    {id: 'coverage', label: '规则覆盖', icon: 'glyphicon glyphicon-tasks'},
    {id: 'anomaly', label: '偏差检测', icon: 'glyphicon glyphicon-warning-sign'}
];

// ========== Chart Components ==========

class FlowTrendChart extends Component {
    componentDidMount() {
        this.chart = echarts.init(this.container);
        this.updateChart(this.props);
        window.addEventListener('resize', this.handleResize);
    }
    componentDidUpdate(prev) {
        if (prev.data !== this.props.data) this.updateChart(this.props);
    }
    componentWillUnmount() {
        window.removeEventListener('resize', this.handleResize);
        if (this.chart) this.chart.dispose();
    }
    handleResize = () => { if (this.chart) this.chart.resize(); };
    updateChart(props) {
        const {data} = props;
        if (!data || !data.timestamps || data.timestamps.length === 0) {
            this.chart.setOption({
                title: {text: '决策趋势', left: 'center', textStyle: {fontSize: 14}},
                graphic: {type: 'text', left: 'center', top: 'middle', style: {text: '暂无数据', fill: '#999', fontSize: 16}}
            });
            return;
        }
        this.chart.setOption({
            title: {text: '决策趋势', left: 'center', textStyle: {fontSize: 14}},
            tooltip: {trigger: 'axis'},
            legend: {data: ['调用量', '成功率%', '拒绝率%', '平均延迟ms'], top: 30},
            grid: {left: 60, right: 60, top: 70, bottom: 30},
            xAxis: {type: 'category', data: data.timestamps, axisLabel: {fontSize: 10, rotate: 30}},
            yAxis: [
                {type: 'value', name: '调用量', position: 'left'},
                {type: 'value', name: '%/ms', position: 'right'}
            ],
            series: [
                {name: '调用量', type: 'bar', data: data.volume, yAxisIndex: 0, itemStyle: {color: '#5470c6'}},
                {name: '成功率%', type: 'line', data: data.successRate, yAxisIndex: 1, smooth: true, itemStyle: {color: '#91cc75'}},
                {name: '拒绝率%', type: 'line', data: data.rejectRate, yAxisIndex: 1, smooth: true, itemStyle: {color: '#ee6666'}},
                {name: '平均延迟ms', type: 'line', data: data.avgLatency, yAxisIndex: 1, smooth: true, lineStyle: {type: 'dashed'}, itemStyle: {color: '#fac858'}}
            ]
        }, true);
    }
    render() {
        return <div ref={el => this.container = el} style={{width: '100%', height: 360}}/>;
    }
}

class RejectDistributionChart extends Component {
    componentDidMount() {
        this.chart = echarts.init(this.container);
        this.updateChart(this.props);
        window.addEventListener('resize', this.handleResize);
    }
    componentDidUpdate(prev) {
        if (prev.data !== this.props.data) this.updateChart(this.props);
    }
    componentWillUnmount() {
        window.removeEventListener('resize', this.handleResize);
        if (this.chart) this.chart.dispose();
    }
    handleResize = () => { if (this.chart) this.chart.resize(); };
    updateChart(props) {
        const {data} = props;
        if (!data || data.length === 0) {
            this.chart.setOption({title: {text: '拒绝码分布', left: 'center', textStyle: {fontSize: 14}}});
            return;
        }
        this.chart.setOption({
            title: {text: '拒绝码分布 Top-' + data.length, left: 'center', textStyle: {fontSize: 14}},
            tooltip: {trigger: 'axis'},
            grid: {left: 120, right: 30, top: 40, bottom: 20},
            xAxis: {type: 'value'},
            yAxis: {type: 'category', data: data.map(d => (d.rejectCode || '') + ' ' + (d.rejectReason || '').substring(0, 15)), axisLabel: {fontSize: 10}},
            series: [{type: 'bar', data: data.map(d => d.count), itemStyle: {color: '#ee6666'}}]
        }, true);
    }
    render() {
        return <div ref={el => this.container = el} style={{width: '100%', height: 300}}/>;
    }
}

class RuleFireChart extends Component {
    componentDidMount() {
        this.chart = echarts.init(this.container);
        this.updateChart(this.props);
        window.addEventListener('resize', this.handleResize);
    }
    componentDidUpdate(prev) {
        if (prev.data !== this.props.data) this.updateChart(this.props);
    }
    componentWillUnmount() {
        window.removeEventListener('resize', this.handleResize);
        if (this.chart) this.chart.dispose();
    }
    handleResize = () => { if (this.chart) this.chart.resize(); };
    updateChart(props) {
        const {data} = props;
        if (!data || !data.hotRules || data.hotRules.length === 0) {
            this.chart.setOption({title: {text: '规则触发频率', left: 'center', textStyle: {fontSize: 14}}});
            return;
        }
        const top20 = data.hotRules.slice(0, 20);
        this.chart.setOption({
            title: {text: '规则触发频率 Top-20', left: 'center', textStyle: {fontSize: 14}},
            tooltip: {trigger: 'axis'},
            grid: {left: 140, right: 30, top: 40, bottom: 20},
            xAxis: {type: 'value', name: '触发次数'},
            yAxis: {type: 'category', data: top20.map(r => r.ruleName), axisLabel: {fontSize: 10}},
            series: [{type: 'bar', data: top20.map(r => r.fireCount), itemStyle: {color: '#5470c6'}}]
        }, true);
    }
    render() {
        return <div ref={el => this.container = el} style={{width: '100%', height: 400}}/>;
    }
}

// ========== Main Dashboard ==========

class AnalysisDashboard extends Component {
    componentDidMount() {
        this.refreshData();
    }
    componentDidUpdate(prevProps) {
        if (prevProps.timeRange !== this.props.timeRange ||
            prevProps.selectedPackage !== this.props.selectedPackage) {
            this.refreshData();
        }
    }
    refreshData() {
        const {timeRange, selectedPackage} = this.props;
        const startTime = getStartTime(timeRange);
        const endTime = new Date();
        const granularity = getGranularity(timeRange);
        const pkg = selectedPackage || null;

        this.props.dispatch(action.loadFlowTimeseries(startTime, endTime, pkg, null, null, granularity));
        this.props.dispatch(action.loadPackageSummary(startTime, endTime));
        this.props.dispatch(action.loadRejectDistribution(startTime, endTime, pkg, 20));
        this.props.dispatch(action.loadRuleCoverage(pkg, startTime, endTime));
        this.props.dispatch(action.loadAnomalies(endTime, 7, 2.0, pkg));
        this.props.dispatch(action.loadAnalysisPackages());
    }
    handleTimeRangeChange(range) {
        this.props.dispatch(action.setTimeRange(range));
    }
    handlePackageChange(pkg) {
        this.props.dispatch(action.setAnalysisPackage(pkg || null));
    }
    handleTabChange(tab) {
        this.props.dispatch(action.setTab(tab));
    }
    render() {
        const {activeTab, timeRange, selectedPackage, packages,
               timeSeriesData, timeSeriesLoading,
               rejectDistribution, rejectDistributionLoading,
               packageSummary, packageSummaryLoading,
               ruleCoverage, ruleCoverageLoading,
               anomalies, anomaliesLoading} = this.props;

        return (
            <div style={{padding: 16, maxWidth: 1200, margin: '0 auto'}}>
                {/* Header */}
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16}}>
                    <h3 style={{margin: 0}}>决策分析仪表盘</h3>
                    <div style={{display: 'flex', gap: 12, alignItems: 'center'}}>
                        <select value={selectedPackage || ''} onChange={e => this.handlePackageChange(e.target.value)}
                                style={{padding: '4px 8px', borderRadius: 4, border: '1px solid #ccc'}}>
                            <option value="">全部规则包</option>
                            {(packages || []).map(p => <option key={p} value={p}>{p}</option>)}
                        </select>
                        {TIME_RANGES.map(tr => (
                            <button key={tr.value} onClick={() => this.handleTimeRangeChange(tr.value)}
                                    style={{
                                        padding: '4px 12px', borderRadius: 4, border: '1px solid #ccc',
                                        background: timeRange === tr.value ? '#1890ff' : '#fff',
                                        color: timeRange === tr.value ? '#fff' : '#333', cursor: 'pointer', fontSize: 12
                                    }}>
                                {tr.label}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Tabs */}
                <div style={{display: 'flex', gap: 0, borderBottom: '2px solid #e8e8e8', marginBottom: 16}}>
                    {TABS.map(tab => (
                        <button key={tab.id} onClick={() => this.handleTabChange(tab.id)}
                                style={{
                                    padding: '8px 20px', border: 'none', cursor: 'pointer',
                                    background: activeTab === tab.id ? '#fff' : '#f5f5f5',
                                    borderBottom: activeTab === tab.id ? '2px solid #1890ff' : '2px solid transparent',
                                    fontWeight: activeTab === tab.id ? 'bold' : 'normal', fontSize: 13
                                }}>
                            <span className={tab.icon} style={{marginRight: 6}}/> {tab.label}
                        </button>
                    ))}
                </div>

                {/* Tab Content */}
                {activeTab === 'trend' && this.renderTrendTab(timeSeriesData, timeSeriesLoading, rejectDistribution, rejectDistributionLoading, packageSummary, packageSummaryLoading)}
                {activeTab === 'coverage' && this.renderCoverageTab(ruleCoverage, ruleCoverageLoading)}
                {activeTab === 'anomaly' && this.renderAnomalyTab(anomalies, anomaliesLoading)}
            </div>
        );
    }

    renderTrendTab(timeSeriesData, timeSeriesLoading, rejectDistribution, rejectDistributionLoading, packageSummary, packageSummaryLoading) {
        return (
            <div>
                <div style={{marginBottom: 16}}>
                    {timeSeriesLoading && <div style={{textAlign: 'center', padding: 20, color: '#999'}}>加载中...</div>}
                    <FlowTrendChart data={timeSeriesData}/>
                </div>
                <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16}}>
                    <div>
                        <h4 style={{margin: '0 0 8px'}}>包/流汇总</h4>
                        {packageSummaryLoading && <div style={{color: '#999'}}>加载中...</div>}
                        <table style={{width: '100%', borderCollapse: 'collapse', fontSize: 11}}>
                            <thead>
                                <tr style={{background: '#fafafa'}}>
                                    <th style={{padding: 6, borderBottom: '1px solid #e8e8e8', textAlign: 'left'}}>规则包</th>
                                    <th style={{padding: 6, borderBottom: '1px solid #e8e8e8', textAlign: 'right'}}>调用量</th>
                                    <th style={{padding: 6, borderBottom: '1px solid #e8e8e8', textAlign: 'right'}}>成功率%</th>
                                    <th style={{padding: 6, borderBottom: '1px solid #e8e8e8', textAlign: 'right'}}>拒绝率%</th>
                                    <th style={{padding: 6, borderBottom: '1px solid #e8e8e8', textAlign: 'right'}}>延迟ms</th>
                                </tr>
                            </thead>
                            <tbody>
                                {(packageSummary || []).map((row, i) => (
                                    <tr key={i} style={{background: i % 2 === 0 ? '#fff' : '#fafafa'}}>
                                        <td style={{padding: 4, borderBottom: '1px solid #f0f0f0'}}>{row.rulePackagePath}</td>
                                        <td style={{padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'right'}}>{row.totalCount}</td>
                                        <td style={{padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'right', color: '#91cc75'}}>{row.successRate}</td>
                                        <td style={{padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'right', color: '#ee6666'}}>{row.rejectRate}</td>
                                        <td style={{padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'right'}}>{row.avgTotalTimeMs ? row.avgTotalTimeMs.toFixed(1) : '-'}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <div>
                        {rejectDistributionLoading && <div style={{color: '#999'}}>加载中...</div>}
                        <RejectDistributionChart data={rejectDistribution}/>
                    </div>
                </div>
            </div>
        );
    }

    renderCoverageTab(ruleCoverage, ruleCoverageLoading) {
        const hotRules = ruleCoverage.hotRules || [];
        const coldRules = ruleCoverage.coldRules || [];
        const dist = ruleCoverage.frequencyDistribution || {};
        return (
            <div>
                <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16, marginBottom: 16}}>
                    <div style={{padding: 16, background: '#e6f7ff', borderRadius: 8, textAlign: 'center'}}>
                        <div style={{fontSize: 24, fontWeight: 'bold', color: '#1890ff'}}>{ruleCoverage.totalFiredInWindow || 0}</div>
                        <div style={{color: '#666', fontSize: 12}}>窗口内触发规则</div>
                    </div>
                    <div style={{padding: 16, background: '#fff7e6', borderRadius: 8, textAlign: 'center'}}>
                        <div style={{fontSize: 24, fontWeight: 'bold', color: '#fa8c16'}}>{coldRules.length}</div>
                        <div style={{color: '#666', fontSize: 12}}>冷规则（未触发）</div>
                    </div>
                    <div style={{padding: 16, background: '#f6ffed', borderRadius: 8, textAlign: 'center'}}>
                        <div style={{fontSize: 24, fontWeight: 'bold', color: '#52c41a'}}>{ruleCoverage.totalRulesEverSeen || 0}</div>
                        <div style={{color: '#666', fontSize: 12}}>历史总规则数</div>
                    </div>
                </div>

                {ruleCoverageLoading && <div style={{textAlign: 'center', padding: 20, color: '#999'}}>加载中...</div>}

                <RuleFireChart data={ruleCoverage}/>

                {coldRules.length > 0 && (
                    <div style={{marginTop: 16}}>
                        <h4 style={{margin: '0 0 8px', color: '#fa8c16'}}>冷规则（窗口内未触发）</h4>
                        <div style={{display: 'flex', flexWrap: 'wrap', gap: 8}}>
                            {coldRules.map(name => (
                                <span key={name} style={{padding: '2px 8px', background: '#fff7e6', borderRadius: 4, fontSize: 11, border: '1px solid #ffd591'}}>{name}</span>
                            ))}
                        </div>
                    </div>
                )}

                <div style={{marginTop: 16}}>
                    <h4 style={{margin: '0 0 8px'}}>频率分布</h4>
                    <div style={{display: 'flex', gap: 12}}>
                        {Object.entries(dist).map(([range, count]) => (
                            <div key={range} style={{padding: '8px 16px', background: '#f5f5f5', borderRadius: 4, textAlign: 'center'}}>
                                <div style={{fontWeight: 'bold'}}>{count}</div>
                                <div style={{fontSize: 11, color: '#666'}}>{range} 次</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        );
    }

    renderAnomalyTab(anomalies, anomaliesLoading) {
        const severityColors = {HIGH: '#ff4d4f', MEDIUM: '#faad14', LOW: '#1890ff'};
        const directionLabels = {SPIKE: '↑ 升高', DROP: '↓ 降低'};
        return (
            <div>
                <h4 style={{margin: '0 0 16px'}}>偏差检测结果 <span style={{fontSize: 12, color: '#999', fontWeight: 'normal'}}>（基线: 7天日均值, 阈值: 2.0σ）</span></h4>

                {anomaliesLoading && <div style={{textAlign: 'center', padding: 20, color: '#999'}}>检测中...</div>}

                {!anomaliesLoading && anomalies.length === 0 && (
                    <div style={{textAlign: 'center', padding: 40, color: '#52c41a', fontSize: 16}}>
                        <span className="glyphicon glyphicon-ok-circle" style={{fontSize: 40, display: 'block', marginBottom: 8}}/>
                        当前未检测到异常偏差
                    </div>
                )}

                <div style={{display: 'grid', gap: 12}}>
                    {(anomalies || []).map((a, i) => (
                        <div key={i} style={{
                            padding: 16, borderRadius: 8, border: '1px solid #e8e8e8',
                            borderLeft: '4px solid ' + (severityColors[a.severity] || '#999'),
                            background: '#fff'
                        }}>
                            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                                <div>
                                    <span style={{fontWeight: 'bold', fontSize: 14}}>{a.label || a.metric}</span>
                                    <span style={{marginLeft: 8, fontSize: 12, color: '#666'}}>{directionLabels[a.direction] || a.direction}</span>
                                </div>
                                <span style={{
                                    padding: '2px 10px', borderRadius: 12, fontSize: 11, fontWeight: 'bold',
                                    background: severityColors[a.severity] || '#999', color: '#fff'
                                }}>{a.severity}</span>
                            </div>
                            <div style={{display: 'flex', gap: 24, marginTop: 8, fontSize: 12, color: '#666'}}>
                                <span>基线: {typeof a.baseline === 'number' ? a.baseline.toFixed(4) : a.baseline}</span>
                                <span>当前: {typeof a.current === 'number' ? a.current.toFixed(4) : a.current}</span>
                                <span>偏差: {typeof a.sigmaDelta === 'number' ? a.sigmaDelta.toFixed(2) : a.sigmaDelta}σ</span>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        );
    }
}

function mapStateToProps(state) {
    return state;
}

const ConnectedDashboard = connect(mapStateToProps)(AnalysisDashboard);

const container = document.getElementById('container');
const root = createRoot(container);
root.render(
    <Provider store={store}>
        <ConnectedDashboard/>
    </Provider>
);
