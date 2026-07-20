// V7.24:从 datasource/index.tsx 拆分 — 数据源新增/编辑表单(含 ADVANCE_AI/JDBC/REST/PKL 配置区块)
import React from 'react';
import {Button, Card, Input, Select} from 'antd';
import {ReloadOutlined} from '@ant-design/icons';
import {DatasourceItem} from '../action';

export interface ModelListItem {
    model_id: string;
    name: string;
    active: boolean;
}

interface DatasourceFormProps {
    formDatasource: DatasourceItem;
    availableModels: ModelListItem[];
    onFormChange: (field: string, value: unknown) => void;
    onUpdateConfigJson: (field: string, value: string) => void;
    onSave: () => void;
    onCancel: () => void;
    onFetchModelList: () => void;
}

export default function DatasourceForm(props: DatasourceFormProps) {
    const {formDatasource, availableModels, onFormChange, onUpdateConfigJson, onSave, onCancel, onFetchModelList} = props;
    const isAdvanceAi = formDatasource.type === 'ADVANCE_AI';
    const isJdbc = formDatasource.type === 'JDBC';
    const isPkl = formDatasource.type === 'PKL';

    let configJson: Record<string, unknown> = {};
    try { configJson = JSON.parse(formDatasource.configJson || '{}'); } catch (_e) { /* ignore */ }

    return (
        <Card size="small" title={formDatasource.id ? '编辑数据源' : '新增数据源'} style={{marginBottom: 15}}>
            <div className="">
                    <div className="ff-group">
                        <label className="ff-col-2 ">名称</label>
                        <div className="ff-col-6">
                            <Input size="small" value={formDatasource.name || ''}
                                   onChange={e => onFormChange('name', e.target.value)} />
                        </div>
                    </div>
                    <div className="ff-group">
                        <label className="ff-col-2 ">类型</label>
                        <div className="ff-col-6">
                            <Select size="small" value={formDatasource.type || 'REST_API'}
                                    onChange={(v: string) => onFormChange('type', v)}
                                    options={[
                                        {value: 'ADVANCE_AI', label: 'Advance AI'},
                                        {value: 'REST_API', label: 'REST API'},
                                        {value: 'JDBC', label: 'JDBC'},
                                        {value: 'PKL', label: 'PKL 模型'},
                                    ]}/>
                        </div>
                    </div>
                    <div className="ff-group">
                        <label className="ff-col-2 ">描述</label>
                        <div className="ff-col-6">
                            <Input size="small" value={formDatasource.description || ''}
                                   onChange={e => onFormChange('description', e.target.value)} />
                        </div>
                    </div>

                    {isAdvanceAi && <AdvanceAiConfig config={configJson} onUpdateConfigJson={onUpdateConfigJson} />}
                    {isJdbc && <JdbcConfig config={configJson} onUpdateConfigJson={onUpdateConfigJson} />}
                    {isPkl && <PklConfig config={configJson} availableModels={availableModels}
                                        onUpdateConfigJson={onUpdateConfigJson} onFetchModelList={onFetchModelList} />}
                    {!isAdvanceAi && !isJdbc && !isPkl && <RestConfig config={configJson} onUpdateConfigJson={onUpdateConfigJson} />}

                    <div className="ff-group">
                        <div className="ff-col-offset-2 ff-col-6">
                            <Button type="primary" size="small" onClick={onSave}>保存</Button>
                            {' '}
                            <Button size="small" onClick={onCancel}>取消</Button>
                        </div>
                    </div>
                </div>
        </Card>
    );
}

interface ConfigSectionProps {
    config: Record<string, unknown>;
    onUpdateConfigJson: (field: string, value: string) => void;
}

function AdvanceAiConfig({config, onUpdateConfigJson}: ConfigSectionProps) {
    return (
        <>
            <div className="ff-group">
                <label className="ff-col-2 ">Base URL</label>
                <div className="ff-col-6">
                    <Input size="small" value={(config.baseUrl as string) || ''}
                           onChange={e => onUpdateConfigJson('baseUrl', e.target.value)} />
                </div>
            </div>
            <div className="ff-group">
                <label className="ff-col-2 ">Access Key</label>
                <div className="ff-col-6">
                    <Input size="small" type="password" value={(config.accessKey as string) || ''}
                           onChange={e => onUpdateConfigJson('accessKey', e.target.value)} />
                </div>
            </div>
            <div className="ff-group">
                <label className="ff-col-2 ">Secret Key</label>
                <div className="ff-col-6">
                    <Input size="small" type="password" value={(config.secretKey as string) || ''}
                           onChange={e => onUpdateConfigJson('secretKey', e.target.value)} />
                </div>
            </div>
        </>
    );
}

function JdbcConfig({config, onUpdateConfigJson}: ConfigSectionProps) {
    return (
        <>
            <div className="ff-group">
                <label className="ff-col-2 ">JDBC URL</label>
                <div className="ff-col-6">
                    <Input size="small" value={(config.url as string) || ''}
                           onChange={e => onUpdateConfigJson('url', e.target.value)} />
                </div>
            </div>
            <div className="ff-group">
                <label className="ff-col-2 ">用户名</label>
                <div className="ff-col-6">
                    <Input size="small" value={(config.username as string) || ''}
                           onChange={e => onUpdateConfigJson('username', e.target.value)} />
                </div>
            </div>
            <div className="ff-group">
                <label className="ff-col-2 ">密码</label>
                <div className="ff-col-6">
                    <Input size="small" type="password" value={(config.password as string) || ''}
                           onChange={e => onUpdateConfigJson('password', e.target.value)} />
                </div>
            </div>
            <div className="ff-group">
                <label className="ff-col-2 ">查询模板</label>
                <div className="ff-col-6">
                    <Input size="small"
                           value={(config.queryTemplate as string) || ''}
                           placeholder="SELECT ${fieldName} FROM table WHERE user_id = '${entityId}'"
                           onChange={e => onUpdateConfigJson('queryTemplate', e.target.value)} />
                </div>
            </div>
        </>
    );
}

function RestConfig({config, onUpdateConfigJson}: ConfigSectionProps) {
    return (
        <>
            <div className="ff-group">
                <label className="ff-col-2 ">Base URL</label>
                <div className="ff-col-6">
                    <Input size="small" value={(config.baseUrl as string) || ''}
                           onChange={e => onUpdateConfigJson('baseUrl', e.target.value)} />
                </div>
            </div>
            <div className="ff-group">
                <label className="ff-col-2 ">Endpoint</label>
                <div className="ff-col-6">
                    <Input size="small" value={(config.endpoint as string) || ''}
                           onChange={e => onUpdateConfigJson('endpoint', e.target.value)} />
                </div>
            </div>
        </>
    );
}

interface PklConfigProps extends ConfigSectionProps {
    availableModels: ModelListItem[];
    onFetchModelList: () => void;
}

function PklConfig({config, availableModels, onUpdateConfigJson, onFetchModelList}: PklConfigProps) {
    return (
        <>
            <div className="ff-group">
                <label className="ff-col-2 ">模型服务地址</label>
                <div className="ff-col-6">
                    <Input size="small"
                           value={(config.modelServiceUrl as string) || ''}
                           placeholder="http://localhost:8501"
                           onChange={e => onUpdateConfigJson('modelServiceUrl', e.target.value)} />
                </div>
            </div>
            <div className="ff-group">
                <label className="ff-col-2 ">模型 ID</label>
                <div className="ff-col-4">
                    <Input size="small"
                           value={(config.modelId as string) || ''}
                           placeholder="credit_scoring_v1"
                           onChange={e => onUpdateConfigJson('modelId', e.target.value)} />
                </div>
                <div className="ff-col-2">
                    <Button size="small" onClick={() => onFetchModelList()}>
                        <ReloadOutlined /> 加载模型
                    </Button>
                </div>
            </div>
            {availableModels.length > 0 && (
                <div className="ff-group">
                    <label className="ff-col-2 ">可选模型</label>
                    <div className="ff-col-6">
                        <Select size="small"
                                onChange={(v: string) => onUpdateConfigJson('modelId', v)}
                                placeholder="选择模型..."
                                options={availableModels.map(m => ({
                                    value: m.model_id,
                                    label: `${m.name} (${m.model_id}) ${m.active ? '✓' : '✗'}`
                                }))}/>
                    </div>
                </div>
            )}
        </>
    );
}
