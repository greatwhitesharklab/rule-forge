import React, {Component} from 'react';
import {loadSimulationResults} from '../action.js';

/**
 * 仿真对比结果表格 — 严重度颜色标记
 */
var SEVERITY_COLORS = {
    HIGH: '#F44336',
    MEDIUM: '#FF9800',
    LOW: '#2196F3',
    NONE: '#4CAF50'
};

class SimulationResultsTable extends Component {

    constructor(props) {
        super(props);
        this.state = {
            results: [],
            page: 1,
            size: 20,
            loading: false
        };
    }

    componentDidMount() {
        if (this.props.runId) {
            this.loadResults(1);
        }
    }

    componentDidUpdate(prevProps) {
        if (this.props.runId && this.props.runId !== prevProps.runId) {
            this.loadResults(1);
        }
    }

    loadResults(page) {
        var self = this;
        this.setState({loading: true, page: page});
        loadSimulationResults(this.props.runId, page, this.state.size, function (data) {
            self.setState({
                results: data.results || [],
                page: data.page || page,
                size: data.size || self.state.size,
                loading: false
            });
        });
    }

    renderSeverityBadge(severity) {
        var color = SEVERITY_COLORS[severity] || '#999';
        return (
            <span style={{
                display: 'inline-block', padding: '2px 8px', borderRadius: 3,
                backgroundColor: color, color: '#fff', fontSize: 11, fontWeight: 'bold'
            }}>
                {severity || 'NONE'}
            </span>
        );
    }

    renderRow(item, index) {
        var bgColor = item.hasDivergence ? '#FFF8E1' : '#fff';
        return (
            <tr key={item.id || index} style={{backgroundColor: bgColor, fontSize: 11}}>
                <td>{item.originalFlowLogId}</td>
                <td>{this.renderMatchIcon(item.statusMatch)}</td>
                <td>{this.renderMatchIcon(item.resultMatch)}</td>
                <td>{this.renderSeverityBadge(item.divergenceSeverity)}</td>
                <td>{item.errorMessage || '-'}</td>
            </tr>
        );
    }

    renderMatchIcon(match) {
        if (match === true || match === 1) {
            return <span style={{color: '#4CAF50'}}>&#10003;</span>;
        }
        return <span style={{color: '#F44336'}}>&#10007;</span>;
    }

    render() {
        if (!this.props.runId) {
            return <div style={{color: '#999', fontSize: 12, padding: 8}}>请先启动仿真</div>;
        }
        var state = this.state;
        if (state.loading && state.results.length === 0) {
            return <div style={{color: '#999', fontSize: 12, padding: 8}}>加载中...</div>;
        }
        return (
            <div>
                <table className="table table-condensed table-bordered" style={{fontSize: 11}}>
                    <thead>
                    <tr>
                        <th>日志ID</th>
                        <th>状态</th>
                        <th>结果</th>
                        <th>严重度</th>
                        <th>错误</th>
                    </tr>
                    </thead>
                    <tbody>
                    {state.results.length > 0
                        ? state.results.map(this.renderRow.bind(this))
                        : <tr><td colSpan="5" style={{textAlign: 'center', color: '#999'}}>暂无数据</td></tr>
                    }
                    </tbody>
                </table>
                <div style={{textAlign: 'center', marginTop: 8}}>
                    <button className="btn btn-xs btn-default"
                            disabled={state.page <= 1}
                            onClick={this.loadResults.bind(this, state.page - 1)}>
                        上一页
                    </button>
                    <span style={{margin: '0 8px', fontSize: 11}}>第 {state.page} 页</span>
                    <button className="btn btn-xs btn-default"
                            disabled={state.results.length < state.size}
                            onClick={this.loadResults.bind(this, state.page + 1)}>
                        下一页
                    </button>
                </div>
            </div>
        );
    }
}

export default SimulationResultsTable;
