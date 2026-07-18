// V7.24:从 datasource/index.tsx 拆分 — 数据源列表页(新增按钮 + 列表表格 + 内嵌表单)
import React from 'react';
import {Button, Space, Table, Tag, Tooltip} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {ApiOutlined, DeleteOutlined, EditOutlined, PlusOutlined, ThunderboltOutlined} from '@ant-design/icons';
import {DatasourceItem} from '../action';

interface DatasourceListProps {
    datasources: DatasourceItem[];
    formNode: React.ReactNode;
    onCreate: () => void;
    onEdit: (ds: DatasourceItem) => void;
    onTest: (id: number) => void;
    onBatchTest: (ds: DatasourceItem) => void;
    onDelete: (id: number) => void;
}

export default function DatasourceList(props: DatasourceListProps) {
    const {datasources, formNode, onCreate, onEdit, onTest, onBatchTest, onDelete} = props;
    // V5.101:Bootstrap rf-table 在 thead/tbody 间有 21px 渲染 gap(无空行,box model 干净,
    // 是 collapse 渲染 bug)→ 换 antd Table,跟 auditLog/用户管理一致,渲染干净。
    const columns: ColumnsType<DatasourceItem> = [
        {title: '名称', dataIndex: 'name', key: 'name'},
        {title: '类型', dataIndex: 'type', key: 'type', render: (v: string) => <Tag>{v}</Tag>},
        {
            title: '状态', dataIndex: 'enabled', key: 'enabled',
            render: (v: boolean) => <Tag color={v ? 'success' : 'default'}>{v ? '启用' : '禁用'}</Tag>
        },
        {title: '描述', dataIndex: 'description', key: 'description', render: (v: string) => v || '-'},
        {title: '超时(ms)', dataIndex: 'timeoutMs', key: 'timeoutMs', width: 100},
        {
            title: '缓存', key: 'cache', width: 80,
            render: (_: unknown, ds: DatasourceItem) => ds.cacheEnabled ? `${ds.cacheTtlHours}h` : '关'
        },
        {
            title: '操作', key: 'actions', width: 300,
            render: (_: unknown, ds: DatasourceItem) => (
                <Space size="middle">
                    <Tooltip title="编辑数据源">
                        <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(ds)} />
                    </Tooltip>
                    <Tooltip title="测试连接(单条)">
                        <Button size="small" type="primary" ghost icon={<ApiOutlined />}
                                onClick={() => onTest(ds.id!)}>测试</Button>
                    </Tooltip>
                    <Tooltip title="批量测试 V5.8.0+:FLOW 测集成 / DATASOURCE 测数据源">
                        <Button size="small" type="primary" icon={<ThunderboltOutlined />}
                                onClick={() => onBatchTest(ds)}>批量测试</Button>
                    </Tooltip>
                    <Tooltip title="删除数据源">
                        <Button size="small" danger icon={<DeleteOutlined />}
                                onClick={() => onDelete(ds.id!)} />
                    </Tooltip>
                </Space>
            )
        },
    ];
    return (
        <div>
            <div style={{marginBottom: 12}}>
                <Button type="primary" icon={<PlusOutlined />} onClick={onCreate}>新增数据源</Button>
            </div>

            {formNode}

            <Table rowKey="id" columns={columns} dataSource={datasources}
                   pagination={false} size="middle" />
        </div>
    );
}
