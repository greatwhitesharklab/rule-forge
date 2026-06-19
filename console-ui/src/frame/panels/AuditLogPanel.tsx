// V5.17 audit log 面板 — 用户/权限操作审计日志
//
// 入口:ActivityBar 选 "审计日志" → SidePanelSwitcher 渲染本组件。
// 功能:
//   1. 表格列出 audit log rows(actor/action/target/field/old/new/note)
//   2. actor 输入框 + action 下拉 + 刷新按钮 → 触发重查
//   3. 点行 → 详情 Drawer 显示完整 row
//
// 权限门控:后端 PermissionController.listAuditLogs 已 admin 门控,
// 前端不重复门控(非 admin 用户调 API 会 401 → 弹"权限不足")。

import {Component, ReactNode} from 'react';
import {connect} from 'react-redux';
import {Table, Input, Select, Button, Drawer, Flex, Tag, Spin, Alert} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import PageShell from '@/frame/components/PageShell';
import {getAuditLogs, AuditLogRow} from '@/api/client.js';

const ACTION_OPTIONS = [
    {value: '', label: '全部动作'},
    {value: 'CREATE_USER', label: '创建用户'},
    {value: 'UPDATE_USER', label: '更新用户'},
    {value: 'TOGGLE_ENABLED', label: '启用/禁用'},
    {value: 'RESET_PASSWORD', label: '重置密码'},
    {value: 'SAVE_PERMISSIONS', label: '保存权限'},
    {value: 'LOGIN_SUCCESS', label: '登录成功'},
    {value: 'LOGIN_FAIL', label: '登录失败'},
];

const ACTION_COLORS: Record<string, string> = {
    CREATE_USER: 'green',
    UPDATE_USER: 'blue',
    TOGGLE_ENABLED: 'orange',
    RESET_PASSWORD: 'volcano',
    SAVE_PERMISSIONS: 'purple',
    LOGIN_SUCCESS: 'cyan',
    LOGIN_FAIL: 'red',
};

interface AuditLogPanelState {
    rows: AuditLogRow[];
    loading: boolean;
    error: string | null;
    actorFilter: string;
    actionFilter: string;
    selected: AuditLogRow | null;
}

class AuditLogPanel extends Component<{}, AuditLogPanelState> {
    state: AuditLogPanelState = {
        rows: [],
        loading: true,
        error: null,
        actorFilter: '',
        actionFilter: '',
        selected: null,
    };

    componentDidMount() {
        this.refresh();
    }

    refresh = async () => {
        this.setState({loading: true, error: null});
        try {
            const rows = await getAuditLogs(
                this.state.actorFilter || null,
                this.state.actionFilter || null,
                50,
            );
            this.setState({rows, loading: false});
        } catch (e) {
            this.setState({error: (e as Error).message, loading: false});
        }
    };

    handleActorChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        this.setState({actorFilter: e.target.value});
    };

    handleActionChange = (value: string) => {
        this.setState({actionFilter: value});
    };

    handleRowClick = (row: AuditLogRow) => {
        this.setState({selected: row});
    };

    handleDrawerClose = () => {
        this.setState({selected: null});
    };

    columns = (): ColumnsType<AuditLogRow> => [
        {
            title: '时间',
            dataIndex: 'occurredAt',
            key: 'occurredAt',
            width: 180,
            render: (v: string) => <span data-testid={`audit-log-time-${v}`}>{v}</span>,
        },
        {
            title: '执行人',
            dataIndex: 'actor',
            key: 'actor',
            width: 120,
        },
        {
            title: '动作',
            dataIndex: 'action',
            key: 'action',
            width: 160,
            render: (v: string) => (
                <Tag color={ACTION_COLORS[v] || 'default'}>{v}</Tag>
            ),
        },
        {
            title: '目标用户',
            dataIndex: 'targetUsername',
            key: 'targetUsername',
            width: 120,
            render: (v: string | null) => v || <span style={{color: '#999'}}>—</span>,
        },
        {
            title: '字段',
            dataIndex: 'fieldName',
            key: 'fieldName',
            width: 100,
            render: (v: string | null) => v || <span style={{color: '#999'}}>—</span>,
        },
        {
            title: '旧值 → 新值',
            key: 'change',
            render: (_: unknown, row: AuditLogRow) => {
                if (row.oldValue === null && row.newValue === null) {
                    return <span style={{color: '#999'}}>—</span>;
                }
                return (
                    <span>
                        <code>{row.oldValue || '∅'}</code>
                        {' → '}
                        <code>{row.newValue || '∅'}</code>
                    </span>
                );
            },
        },
    ];

    render(): ReactNode {
        const {rows, loading, error, actorFilter, actionFilter, selected} = this.state;
        return (
            <PageShell
                title="审计日志"
                toolbar={
                    <Flex gap="middle">
                    <Input
                        data-testid="audit-log-actor-input"
                        placeholder="按执行人过滤"
                        value={actorFilter}
                        onChange={this.handleActorChange}
                        style={{width: 200}}
                        allowClear
                    />
                    <Select
                        data-testid="audit-log-action-select"
                        value={actionFilter}
                        onChange={this.handleActionChange}
                        style={{width: 180}}
                        options={ACTION_OPTIONS}
                        placeholder="按动作过滤"
                    />
                    <Button
                        data-testid="audit-log-refresh"
                        type="primary"
                        onClick={this.refresh}
                    >
                        刷新
                    </Button>
                    </Flex>
                }
            >
                {error && (
                    <Alert
                        data-testid="audit-log-error"
                        type="error"
                        message="加载失败"
                        description={error}
                        showIcon
                        style={{marginBottom: 16}}
                    />
                )}

                {loading && rows.length === 0 ? (
                    <div
                        data-testid="audit-log-loading"
                        style={{textAlign: 'center', padding: 40}}
                    >
                        <Spin>
                            <div style={{padding: 20}}>加载中...</div>
                        </Spin>
                    </div>
                ) : (
                    <Table
                        data-testid="audit-log-table"
                        rowKey="id"
                        columns={this.columns()}
                        dataSource={rows}
                        loading={loading}
                        size="small"
                        pagination={{pageSize: 20, showSizeChanger: false}}
                        onRow={(row) => ({
                            'data-testid': `audit-log-row-${row.id}`,
                            onClick: () => this.handleRowClick(row),
                            style: {cursor: 'pointer'},
                        })}
                    />
                )}

                <Drawer
                    data-testid="audit-log-drawer"
                    title={selected ? `审计详情 #${selected.id}` : '审计详情'}
                    open={!!selected}
                    onClose={this.handleDrawerClose}
                    size={500}
                    extra={
                        <Button
                            data-testid="audit-log-drawer-close"
                            onClick={this.handleDrawerClose}
                        >
                            关闭
                        </Button>
                    }
                >
                    {selected && (
                        <Flex vertical gap="middle" style={{width: '100%'}}>
                            <div><strong>时间:</strong> {selected.occurredAt}</div>
                            <div><strong>执行人:</strong> {selected.actor}</div>
                            <div>
                                <strong>动作:</strong>{' '}
                                <Tag color={ACTION_COLORS[selected.action] || 'default'}>
                                    {selected.action}
                                </Tag>
                            </div>
                            <div>
                                <strong>目标用户:</strong>{' '}
                                {selected.targetUsername || '—'}{' '}
                                {selected.targetUserId && `(id=${selected.targetUserId})`}
                            </div>
                            <div><strong>字段:</strong> {selected.fieldName || '—'}</div>
                            <div>
                                <strong>旧值 → 新值:</strong>{' '}
                                <code>{selected.oldValue || '∅'}</code> →{' '}
                                <code>{selected.newValue || '∅'}</code>
                            </div>
                            <div><strong>项目:</strong> {selected.project || '—'}</div>
                            <div data-testid="audit-log-drawer-note">
                                <strong>备注:</strong> {selected.note || '—'}
                            </div>
                        </Flex>
                    )}
                </Drawer>
            </PageShell>
        );
    }
}

const selector = () => ({});
export {AuditLogPanel};
export default connect(selector)(AuditLogPanel);
