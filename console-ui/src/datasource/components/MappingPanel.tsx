// V7.24:从 datasource/index.tsx 拆分 — 映射配置页(实体类→数据源映射 + 字段映射)
import React from 'react';
import {Button, Input, Select, Table} from 'antd';
import {DatasourceItem, EntityMapping, FieldMapping} from '../action';

interface MappingPanelProps {
    datasources: DatasourceItem[];
    entityMappings: EntityMapping[];
    fieldMappings: FieldMapping[];
    mappingClazz: string;
    mappingDatasourceId: string;
    mappingFieldClazz: string;
    onMappingClazzChange: (v: string) => void;
    onMappingDatasourceIdChange: (v: string) => void;
    onSaveMapping: () => void;
    onLoadFieldMappings: (dsId: number, clazz: string) => void;
    onFetchModelFields: (dsId: number, clazz: string, ds: DatasourceItem) => void;
}

export default function MappingPanel(props: MappingPanelProps) {
    const {
        datasources, entityMappings, fieldMappings,
        mappingClazz, mappingDatasourceId, mappingFieldClazz,
        onMappingClazzChange, onMappingDatasourceIdChange,
        onSaveMapping, onLoadFieldMappings, onFetchModelFields
    } = props;
    return (
        <div>
            <h5 style={{marginBottom: '10px'}}>实体类 → 数据源映射</h5>
            <div className="form-inline" style={{marginBottom: '15px'}}>
                <Input size="small" placeholder="实体类名 (clazz)"
                       value={mappingClazz}
                       onChange={e => onMappingClazzChange(e.target.value)} />
                {' '}
                <Select size="small" value={mappingDatasourceId}
                        onChange={(v: string) => onMappingDatasourceIdChange(v)}
                        placeholder="选择数据源"
                        options={datasources.map(ds => ({value: String(ds.id), label: ds.name}))}/>
                {' '}
                <Button type="primary" size="small" onClick={onSaveMapping}>保存映射</Button>
            </div>

            <Table<EntityMapping> rowKey="id" dataSource={entityMappings} pagination={false} size="small"
                columns={[
                    {title: '实体类 (clazz)', dataIndex: 'clazz', key: 'clazz'},
                    {title: '数据源', key: 'ds',
                        render: (_: unknown, m: EntityMapping) => {
                            const ds = datasources.find(d => d.id === m.datasourceId);
                            return ds ? ds.name : '(未知)';
                        }},
                    {title: '操作', key: 'op',
                        render: (_: unknown, m: EntityMapping) => {
                            const ds = datasources.find(d => d.id === m.datasourceId);
                            return (
                                <>
                                    <Button size="small"
                                            onClick={() => onLoadFieldMappings(m.datasourceId, m.clazz)}>字段映射</Button>
                                    {ds && ds.type === 'PKL' && (
                                        <Button size="small" style={{marginLeft: 4}}
                                                onClick={() => onFetchModelFields(m.datasourceId, m.clazz, ds)}>获取模型字段</Button>
                                    )}
                                </>
                            );
                        }},
                ]}/>

            {fieldMappings.length > 0 && (
                <div>
                    <h5 style={{marginTop: '15px'}}>字段映射: {mappingFieldClazz}</h5>
                    <Table<FieldMapping> rowKey="id" dataSource={fieldMappings} pagination={false} size="small"
                        columns={[
                            {title: '规则变量名', dataIndex: 'variableName', key: 'v'},
                            {title: '外部字段名', dataIndex: 'remoteField', key: 'r'},
                        ]}/>
                </div>
            )}
        </div>
    );
}
