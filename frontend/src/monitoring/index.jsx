import React, {Component} from 'react';
import {createRoot} from 'react-dom/client';
import {applyMiddleware, createStore} from 'redux';
import thunk from 'redux-thunk';
import {Provider, connect} from 'react-redux';
import reducer from './reducer.js';
import * as action from './action.js';
import LatencyChart from './components/LatencyChart.jsx';
import SuccessFailureChart from './components/SuccessFailureChart.jsx';
import CallVolumeChart from './components/CallVolumeChart.jsx';
import AlertRulePanel from './components/AlertRulePanel.jsx';

const TIME_RANGES = [
    {label: '1小时', value: '1h'},
    {label: '6小时', value: '6h'},
    {label: '24小时', value: '24h'},
    {label: '7天', value: '7d'}
];

function getStartTime(range) {
    const now = new Date();
    switch (range) {
        case '1h': return new Date(now - 3600000);
        case '6h': return new Date(now - 6 * 3600000);
        case '24h': return new Date(now - 24 * 3600000);
        case '7d': return new Date(now - 7 * 24 * 3600000);
        default: return new Date(now - 3600000);
    }
}

class MonitoringDashboard extends Component {
    componentDidMount() {
        this.refreshData();
    }

    componentDidUpdate(prevProps) {
        if (prevProps.timeRange !== this.props.timeRange || prevProps.selectedPackage !== this.props.selectedPackage) {
            this.refreshData();
        }
    }

    refreshData() {
        const {timeRange, selectedPackage} = this.props;
        const startTime = getStartTime(timeRange);
        this.props.dispatch(action.loadMetrics('rule.execution.latency', startTime, new Date(), selectedPackage));
        this.props.dispatch(action.loadPackages());
        this.props.dispatch(action.loadAlertRules());
    }

    handleTimeRangeChange(range) {
        this.props.dispatch(action.setTimeRange(range));
    }

    handlePackageChange(pkg) {
        this.props.dispatch(action.setSelectedPackage(pkg || null));
    }

    render() {
        const {metricsData, packages, alertRules, timeRange, selectedPackage, loading} = this.props;

        return (
            <div style={{padding: 16, maxWidth: 1200, margin: '0 auto'}}>
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16}}>
                    <h3 style={{margin: 0}}>监控仪表盘</h3>
                    <div style={{display: 'flex', gap: 12, alignItems: 'center'}}>
                        <select value={selectedPackage || ''} onChange={e => this.handlePackageChange(e.target.value)}
                                style={{padding: '4px 8px', fontSize: 12}}>
                            <option value="">全部规则包</option>
                            {(packages || []).map(pkg => <option key={pkg} value={pkg}>{pkg}</option>)}
                        </select>
                        {TIME_RANGES.map(tr => (
                            <button key={tr.value}
                                    onClick={() => this.handleTimeRangeChange(tr.value)}
                                    style={{
                                        padding: '4px 10px', fontSize: 12, cursor: 'pointer',
                                        background: timeRange === tr.value ? '#5470c6' : '#f5f5f5',
                                        color: timeRange === tr.value ? '#fff' : '#333',
                                        border: '1px solid #ddd'
                                    }}>
                                {tr.label}
                            </button>
                        ))}
                    </div>
                </div>

                <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16}}>
                    <div style={{gridColumn: '1 / -1'}}>
                        <LatencyChart data={metricsData}/>
                    </div>
                    <div>
                        <CallVolumeChart data={metricsData}/>
                    </div>
                    <div>
                        <SuccessFailureChart data={metricsData}/>
                    </div>
                </div>

                <AlertRulePanel alertRules={alertRules} dispatch={this.props.dispatch}/>

                {loading && <div style={{textAlign: 'center', padding: 20, color: '#999'}}>加载中...</div>}
            </div>
        );
    }
}

const ConnectedDashboard = connect(state => state)(MonitoringDashboard);

const store = createStore(reducer, applyMiddleware(thunk));

document.addEventListener('DOMContentLoaded', function () {
    createRoot(document.getElementById('container')).render(
        <Provider store={store}>
            <ConnectedDashboard/>
        </Provider>
    );
});
