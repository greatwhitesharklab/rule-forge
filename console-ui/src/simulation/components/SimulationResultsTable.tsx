import {Component, ReactNode} from 'react';
import {Button, Table, Tag} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {loadSimulationResults, SimulationResultItem} from '../action.js';

/**
 * 仿真对比结果表格 — 严重度颜色标记。V5.101:rf-table → antd Table。
 */
const SEVERITY_TAG: Record<string, string> = {
    HIGH: 'error',
    MEDIUM: 'warning',
    LOW: 'processing',
    NONE: 'success'
};

interface SimulationResultsTableProps {
    runId: string | null;
}

interface SimulationResultsTableState {
    results: SimulationResultItem[];
    page: number;
    size: number;
    loading: boolean;
}

class SimulationResultsTable extends Component<SimulationResultsTableProps, SimulationResultsTableState> {

    constructor(props: SimulationResultsTableProps) {
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

    componentDidUpdate(prevProps: SimulationResultsTableProps) {
        if (this.props.runId && this.props.runId !== prevProps.runId) {
            this.loadResults(1);
        }
    }

    loadResults(page: number) {
        const self = this;
        this.setState({loading: true, page: page});
        loadSimulationResults(this.props.runId!, page, this.state.size, function (data) {
            self.setState({
                results: data.results || [],
                page: data.page || page,
                size: data.size || self.state.size,
                loading: false
            });
        });
    }

    columns: ColumnsType<SimulationResultItem> = [
        {title: '日志ID', dataIndex: 'originalFlowLogId', key: 'log'},
        {
            title: '状态', dataIndex: 'statusMatch', key: 'st',
            render: (v: boolean | number) => <span style={{color: (v === true || v === 1) ? 'var(--rf-success)' : 'var(--rf-danger)'}}>{(v === true || v === 1) ? '✓' : '✗'}</span>
        },
        {
            title: '结果', dataIndex: 'resultMatch', key: 'rs',
            render: (v: boolean | number) => <span style={{color: (v === true || v === 1) ? 'var(--rf-success)' : 'var(--rf-danger)'}}>{(v === true || v === 1) ? '✓' : '✗'}</span>
        },
        {
            title: '严重度', dataIndex: 'divergenceSeverity', key: 'sev',
            render: (s: string) => <Tag color={SEVERITY_TAG[s] || 'default'}>{s || 'NONE'}</Tag>
        },
        {title: '错误', dataIndex: 'errorMessage', key: 'err', render: (v: string) => v || '-'},
    ];

    render(): ReactNode {
        if (!this.props.runId) {
            return <div style={{color: 'var(--rf-text-tertiary)', fontSize: 12, padding: 8}}>请先启动仿真</div>;
        }
        const state = this.state;
        if (state.loading && state.results.length === 0) {
            return <div style={{color: 'var(--rf-text-tertiary)', fontSize: 12, padding: 8}}>加载中...</div>;
        }
        return (
            <div>
                <Table<SimulationResultItem> rowKey="id" dataSource={state.results}
                    columns={this.columns} pagination={false} size="small"
                    onRow={(r: SimulationResultItem) => ({style: {background: r.hasDivergence ? '#fff8e1' : undefined}})}
                    locale={{emptyText: '暂无数据'}}/>
                <div style={{textAlign: 'center', marginTop: 8}}>
                    <Button size="small" disabled={state.page <= 1}
                            onClick={() => this.loadResults(state.page - 1)}>上一页</Button>
                    <span style={{margin: '0 8px', fontSize: 11}}>第 {state.page} 页</span>
                    <Button size="small" disabled={state.results.length < state.size}
                            onClick={() => this.loadResults(state.page + 1)}>下一页</Button>
                </div>
            </div>
        );
    }
}

export default SimulationResultsTable;
