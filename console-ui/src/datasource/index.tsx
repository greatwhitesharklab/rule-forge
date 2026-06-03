import React, {Component} from 'react';
import {connect} from 'react-redux';
import {
    loadDatasources, createDatasource, updateDatasource, deleteDatasource,
    testConnection, setSelectedDatasource, setTab,
    loadEntityMappings, saveEntityMapping,
    loadFieldMappings, saveFieldMappings,
    DatasourceItem, EntityMapping, FieldMapping
} from './action';

interface DatasourcePanelProps {
    dispatch: (action: unknown) => unknown;
    datasources: DatasourceItem[];
    entityMappings: EntityMapping[];
    fieldMappings: FieldMapping[];
    activeTab: string;
}

interface ModelListItem {
    model_id: string;
    name: string;
    active: boolean;
}

interface DatasourcePanelState {
    showForm: boolean;
    formDatasource: DatasourceItem | null;
    testResult: string | null;
    mappingClazz: string;
    mappingDatasourceId: string;
    mappingFieldClazz: string;
    availableModels: ModelListItem[];
}

class DatasourcePanel extends Component<DatasourcePanelProps, DatasourcePanelState> {

    state: DatasourcePanelState = {
        showForm: false,
        formDatasource: null,
        testResult: null,
        mappingClazz: '',
        mappingDatasourceId: '',
        mappingFieldClazz: '',
        availableModels: []
    };

    componentDidMount() {
        this.props.dispatch(loadDatasources());
        this.props.dispatch(loadEntityMappings());
    }

    handleCreate = () => {
        this.setState({
            showForm: true,
            formDatasource: {name: '', type: 'REST_API', configJson: '{}', enabled: true, description: '',
                timeoutMs: 30000, cacheEnabled: true, cacheTtlHours: 120}
        });
    };

    handleEdit = (ds: DatasourceItem) => {
        this.setState({showForm: true, formDatasource: {...ds}});
    };

    handleDelete = (id: number) => {
        if (window.confirm('确定删除此数据源？')) {
            this.props.dispatch(deleteDatasource(id));
        }
    };

    handleTest = async (id: number) => {
        this.setState({testResult: '测试中...'});
        const result = await this.props.dispatch(testConnection(id)) as { success: boolean; message?: string };
        this.setState({testResult: result.success ? '✓ 连接成功' : '✗ 连接失败: ' + (result.message || '')});
        setTimeout(() => this.setState({testResult: null}), 3000);
    };

    /**
     * V5.8.0: 批量测试入口(FLOW+DATASOURCE 模式)
     * 1. bootbox.prompt 让用户填 entityIds(逗号分隔)和 flowId
     * 2. 调 startBatchTest(subjectType=FLOW, inputSourceType=DATASOURCE, ...)
     * 3. 成功后 emit OPEN_BATCH_TEST_DIALOG 让全局 BatchTestDialog 弹起来轮询
     */
    handleBatchTest = async (ds: DatasourceItem) => {
        // 弹窗:让用户填 entityIds 和 flowId
        const html = `
            <div style="text-align:left;padding:0 8px">
                <label>Entity 主键(逗号分隔,比如 "1,2,3,100,200")</label>
                <textarea id="bb-entity-ids" class="form-control" rows="3" placeholder="e.g. 1,2,3,100,200"></textarea>
                <label style="margin-top:10px">主键字段名(默认 id)</label>
                <input id="bb-id-field" class="form-control" value="id" />
                <label style="margin-top:10px">Flow ID(待测决策流,比如 "loan-approval")</label>
                <input id="bb-flow-id" class="form-control" placeholder="e.g. loan-approval" />
            </div>
        `;
        window.bootbox.dialog({
            title: '批量测试 - ' + ds.name + ' (V5.8.0 FLOW+DATASOURCE)',
            message: html,
            closeButton: true,
            buttons: {
                cancel: { label: '取消', className: 'btn-default' },
                confirm: {
                    label: '开始',
                    className: 'btn-primary',
                    callback: () => {
                        const idsText = (document.getElementById('bb-entity-ids') as HTMLTextAreaElement).value.trim();
                        const idField = (document.getElementById('bb-id-field') as HTMLInputElement).value.trim() || 'id';
                        const flowId = (document.getElementById('bb-flow-id') as HTMLInputElement).value.trim();
                        if (!idsText) {
                            window.bootbox.alert('请输入 entityIds');
                            return false;
                        }
                        if (!flowId) {
                            window.bootbox.alert('请输入 Flow ID');
                            return false;
                        }
                        const valueList = idsText.split(/[,\s]+/).filter((s: string) => s.length > 0);
                        this.startBatchTestDs(ds, idField, valueList, flowId);
                        return true;
                    }
                }
            }
        });
    };

    /**
     * 调后端 startBatchTest + emit dialog 事件
     */
    startBatchTestDs = async (ds: DatasourceItem, idField: string, valueList: string[], flowId: string) => {
        const ce = (window as any).parent?.componentEvent || (window as any).componentEvent;
        const ev = (window as any).parent?.event || (window as any).event;

        try {
            const resp = await fetch((window as any)._server + '/batchtest/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    subjectType: 'FLOW',
                    subjectId: null,  // TODO: resolve flowId → subjectId
                    inputSourceType: 'DATASOURCE',
                    inputSourceId: ds.id,
                    inputConfig: {
                        datasourceId: ds.id,
                        clazz: '',
                        valueList,
                        inputField: idField
                    },
                    project: '',
                    packageId: '',
                    flowId
                })
            });
            const result = await resp.json();

            if (result.sessionId) {
                // 触发全局 BatchTestDialog(已挂在 frame 顶层)
                // skipStart: true 表示 session 已经在外面 start 了,dialog 只轮询
                ev.eventEmitter.emit(ev.OPEN_BATCH_TEST_DIALOG, {
                    sessionId: result.sessionId,
                    data: { subjectType: 'FLOW', inputSourceType: 'DATASOURCE', skipStart: true }
                });
                if (ce) ce.eventEmitter.emit(ce.SHOW_LOADING);
            } else {
                window.bootbox.alert('启动失败: ' + (result.error || JSON.stringify(result)));
            }
        } catch (e: any) {
            window.bootbox.alert('请求失败: ' + e.message);
        }
    };

    handleSave = () => {
        const {formDatasource} = this.state;
        if (!formDatasource || !formDatasource.name || !formDatasource.type) return;
        if (formDatasource.id) {
            this.props.dispatch(updateDatasource(formDatasource.id, formDatasource));
        } else {
            this.props.dispatch(createDatasource(formDatasource));
        }
        this.setState({showForm: false, formDatasource: null});
    };

    handleFormChange = (field: string, value: unknown) => {
        this.setState(prev => ({
            formDatasource: prev.formDatasource ? {...prev.formDatasource, [field]: value} : null
        }));
    };

    handleSaveMapping = () => {
        const {mappingClazz, mappingDatasourceId} = this.state;
        if (!mappingClazz || !mappingDatasourceId) return;
        this.props.dispatch(saveEntityMapping(mappingClazz, parseInt(mappingDatasourceId)));
        this.setState({mappingClazz: '', mappingDatasourceId: ''});
    };

    handleLoadFieldMappings = (dsId: number, clazz: string) => {
        this.setState({mappingFieldClazz: clazz});
        this.props.dispatch(setSelectedDatasource({id: dsId, clazz}));
        this.props.dispatch(loadFieldMappings(dsId, clazz));
    };

    handleFetchModelFields = (dsId: number, clazz: string, ds: DatasourceItem) => {
        let config: Record<string, unknown> = {};
        try { config = JSON.parse(ds.configJson || '{}'); } catch (_e) { /* ignore */ }
        const url = config.modelServiceUrl as string;
        const modelId = config.modelId as string;
        if (!url || !modelId) {
            alert('请先在数据源配置中填写模型服务地址和模型 ID');
            return;
        }
        fetch(url + '/models/' + encodeURIComponent(modelId))
            .then(res => {
                if (!res.ok) throw new Error('模型不存在');
                return res.json();
            })
            .then((modelInfo: { input_fields: Array<{name: string}>; output_fields: Array<{name: string}> }) => {
                const allFields = [...modelInfo.input_fields, ...modelInfo.output_fields];
                const mappings: FieldMapping[] = allFields.map(f => ({
                    variableName: f.name,
                    remoteField: f.name
                }));
                this.props.dispatch(saveFieldMappings(dsId, clazz, mappings));
            })
            .catch(err => {
                console.error('获取模型字段失败', err);
                alert('获取模型字段失败: ' + err.message);
            });
    };

    render() {
        const {datasources, entityMappings, fieldMappings, activeTab} = this.props;
        const {showForm, formDatasource, testResult} = this.state;

        return (
            <div style={{padding: '15px', height: '100%', overflow: 'auto'}}>
                <ul className="nav nav-tabs" style={{marginBottom: '15px'}}>
                    <li className={activeTab === 'datasources' ? 'active' : ''}>
                        <a href="#" onClick={(e) => { e.preventDefault(); this.props.dispatch(setTab('datasources')); }}>数据源</a>
                    </li>
                    <li className={activeTab === 'mappings' ? 'active' : ''}>
                        <a href="#" onClick={(e) => { e.preventDefault(); this.props.dispatch(setTab('mappings')); }}>映射配置</a>
                    </li>
                </ul>

                {testResult && (
                    <div className="alert alert-info" style={{padding: '8px 12px'}}>{testResult}</div>
                )}

                {activeTab === 'datasources' && this.renderDatasources(datasources, showForm, formDatasource)}
                {activeTab === 'mappings' && this.renderMappings(datasources, entityMappings, fieldMappings)}
            </div>
        );
    }

    renderDatasources(datasources: DatasourceItem[], showForm: boolean, formDatasource: DatasourceItem | null) {
        return (
            <div>
                <div style={{marginBottom: '10px'}}>
                    <button className="btn btn-primary btn-sm" onClick={this.handleCreate}>
                        <i className="glyphicon glyphicon-plus"></i> 新增数据源
                    </button>
                </div>

                {showForm && formDatasource && this.renderForm(formDatasource)}

                <table className="table table-bordered table-hover" style={{fontSize: '13px'}}>
                    <thead>
                        <tr>
                            <th>名称</th>
                            <th>类型</th>
                            <th>状态</th>
                            <th>描述</th>
                            <th>超时(ms)</th>
                            <th>缓存</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {datasources.map(ds => (
                            <tr key={ds.id}>
                                <td>{ds.name}</td>
                                <td><span className="label label-info">{ds.type}</span></td>
                                <td>{ds.enabled ? '✓ 启用' : '✗ 禁用'}</td>
                                <td>{ds.description || '-'}</td>
                                <td>{ds.timeoutMs}</td>
                                <td>{ds.cacheEnabled ? `${ds.cacheTtlHours}h` : '关'}</td>
                                <td>
                                    <button className="btn btn-xs btn-default" onClick={() => this.handleEdit(ds)}>
                                        <i className="glyphicon glyphicon-edit"></i>
                                    </button>
                                    {' '}
                                    <button className="btn btn-xs btn-success" onClick={() => this.handleTest(ds.id!)}>
                                        <i className="glyphicon glyphicon-signal"></i> 测试
                                    </button>
                                    {' '}
                                    {/* V5.8.0: 批量测试按钮 — FLOW+DATASOURCE 模式,调三方 API 拉真实数据测决策流 */}
                                    <button className="btn btn-xs btn-primary" onClick={() => this.handleBatchTest(ds)}>
                                        <i className="glyphicon glyphicon-flash"></i> 批量测试
                                    </button>
                                    {' '}
                                    <button className="btn btn-xs btn-danger" onClick={() => this.handleDelete(ds.id!)}>
                                        <i className="glyphicon glyphicon-trash"></i>
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        );
    }

    renderForm(formDatasource: DatasourceItem) {
        const isAdvanceAi = formDatasource.type === 'ADVANCE_AI';
        const isJdbc = formDatasource.type === 'JDBC';
        const isPkl = formDatasource.type === 'PKL';

        let configJson: Record<string, unknown> = {};
        try { configJson = JSON.parse(formDatasource.configJson || '{}'); } catch (_e) { /* ignore */ }

        return (
            <div className="panel panel-default" style={{marginBottom: '15px'}}>
                <div className="panel-heading">
                    <strong>{formDatasource.id ? '编辑数据源' : '新增数据源'}</strong>
                </div>
                <div className="panel-body" style={{padding: '10px'}}>
                    <div className="form-horizontal">
                        <div className="form-group">
                            <label className="col-sm-2 control-label">名称</label>
                            <div className="col-sm-6">
                                <input className="form-control input-sm" value={formDatasource.name || ''}
                                       onChange={e => this.handleFormChange('name', e.target.value)} />
                            </div>
                        </div>
                        <div className="form-group">
                            <label className="col-sm-2 control-label">类型</label>
                            <div className="col-sm-6">
                                <select className="form-control input-sm" value={formDatasource.type || 'REST_API'}
                                        onChange={e => this.handleFormChange('type', e.target.value)}>
                                    <option value="ADVANCE_AI">Advance AI</option>
                                    <option value="REST_API">REST API</option>
                                    <option value="JDBC">JDBC</option>
                                    <option value="PKL">PKL 模型</option>
                                </select>
                            </div>
                        </div>
                        <div className="form-group">
                            <label className="col-sm-2 control-label">描述</label>
                            <div className="col-sm-6">
                                <input className="form-control input-sm" value={formDatasource.description || ''}
                                       onChange={e => this.handleFormChange('description', e.target.value)} />
                            </div>
                        </div>

                        {isAdvanceAi && this.renderAdvanceAiConfig(configJson)}
                        {isJdbc && this.renderJdbcConfig(configJson)}
                        {isPkl && this.renderPklConfig(configJson)}
                        {!isAdvanceAi && !isJdbc && !isPkl && this.renderRestConfig(configJson)}

                        <div className="form-group">
                            <div className="col-sm-offset-2 col-sm-6">
                                <button className="btn btn-primary btn-sm" onClick={this.handleSave}>保存</button>
                                {' '}
                                <button className="btn btn-default btn-sm" onClick={() => this.setState({showForm: false})}>取消</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    renderAdvanceAiConfig(config: Record<string, unknown>) {
        return (
            <>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Base URL</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={(config.baseUrl as string) || ''}
                               onChange={e => this.updateConfigJson('baseUrl', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Access Key</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" type="password" value={(config.accessKey as string) || ''}
                               onChange={e => this.updateConfigJson('accessKey', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Secret Key</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" type="password" value={(config.secretKey as string) || ''}
                               onChange={e => this.updateConfigJson('secretKey', e.target.value)} />
                    </div>
                </div>
            </>
        );
    }

    renderJdbcConfig(config: Record<string, unknown>) {
        return (
            <>
                <div className="form-group">
                    <label className="col-sm-2 control-label">JDBC URL</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={(config.url as string) || ''}
                               onChange={e => this.updateConfigJson('url', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">用户名</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={(config.username as string) || ''}
                               onChange={e => this.updateConfigJson('username', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">密码</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" type="password" value={(config.password as string) || ''}
                               onChange={e => this.updateConfigJson('password', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">查询模板</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm"
                               value={(config.queryTemplate as string) || ''}
                               placeholder="SELECT ${fieldName} FROM table WHERE user_id = '${entityId}'"
                               onChange={e => this.updateConfigJson('queryTemplate', e.target.value)} />
                    </div>
                </div>
            </>
        );
    }

    renderRestConfig(config: Record<string, unknown>) {
        return (
            <>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Base URL</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={(config.baseUrl as string) || ''}
                               onChange={e => this.updateConfigJson('baseUrl', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Endpoint</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={(config.endpoint as string) || ''}
                               onChange={e => this.updateConfigJson('endpoint', e.target.value)} />
                    </div>
                </div>
            </>
        );
    }

    renderPklConfig(config: Record<string, unknown>) {
        return (
            <>
                <div className="form-group">
                    <label className="col-sm-2 control-label">模型服务地址</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm"
                               value={(config.modelServiceUrl as string) || ''}
                               placeholder="http://localhost:8501"
                               onChange={e => this.updateConfigJson('modelServiceUrl', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">模型 ID</label>
                    <div className="col-sm-4">
                        <input className="form-control input-sm"
                               value={(config.modelId as string) || ''}
                               placeholder="credit_scoring_v1"
                               onChange={e => this.updateConfigJson('modelId', e.target.value)} />
                    </div>
                    <div className="col-sm-2">
                        <button className="btn btn-sm btn-default" onClick={() => this.fetchModelList()}>
                            <i className="glyphicon glyphicon-refresh"></i> 加载模型
                        </button>
                    </div>
                </div>
                {this.state.availableModels.length > 0 && (
                    <div className="form-group">
                        <label className="col-sm-2 control-label">可选模型</label>
                        <div className="col-sm-6">
                            <select className="form-control input-sm"
                                    onChange={e => this.updateConfigJson('modelId', e.target.value)}>
                                <option value="">选择模型...</option>
                                {this.state.availableModels.map(m => (
                                    <option key={m.model_id} value={m.model_id}>
                                        {m.name} ({m.model_id}) {m.active ? '✓' : '✗'}
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>
                )}
            </>
        );
    }

    fetchModelList = () => {
        const {formDatasource} = this.state;
        if (!formDatasource) return;
        let config: Record<string, unknown> = {};
        try { config = JSON.parse(formDatasource.configJson || '{}'); } catch (_e) { /* ignore */ }
        const url = config.modelServiceUrl as string;
        if (!url) {
            alert('请先填写模型服务地址');
            return;
        }
        fetch(url + '/models')
            .then(res => res.json())
            .then((data: ModelListItem[]) => {
                this.setState({availableModels: data});
            })
            .catch(err => {
                console.error('加载模型列表失败', err);
                alert('加载模型列表失败，请检查模型服务地址');
            });
    };

    updateConfigJson = (field: string, value: string) => {
        let config: Record<string, unknown> = {};
        try { config = JSON.parse(this.state.formDatasource?.configJson || '{}'); } catch (_e) { /* ignore */ }
        config[field] = value;
        this.handleFormChange('configJson', JSON.stringify(config));
    };

    renderMappings(datasources: DatasourceItem[], entityMappings: EntityMapping[], fieldMappings: FieldMapping[]) {
        return (
            <div>
                <h5 style={{marginBottom: '10px'}}>实体类 → 数据源映射</h5>
                <div className="form-inline" style={{marginBottom: '15px'}}>
                    <input className="form-control input-sm" placeholder="实体类名 (clazz)"
                           value={this.state.mappingClazz}
                           onChange={e => this.setState({mappingClazz: e.target.value})} />
                    {' '}
                    <select className="form-control input-sm" value={this.state.mappingDatasourceId}
                            onChange={e => this.setState({mappingDatasourceId: e.target.value})}>
                        <option value="">选择数据源</option>
                        {datasources.map(ds => <option key={ds.id} value={ds.id}>{ds.name}</option>)}
                    </select>
                    {' '}
                    <button className="btn btn-primary btn-sm" onClick={this.handleSaveMapping}>保存映射</button>
                </div>

                <table className="table table-bordered table-hover" style={{fontSize: '13px'}}>
                    <thead>
                        <tr><th>实体类 (clazz)</th><th>数据源</th><th>操作</th></tr>
                    </thead>
                    <tbody>
                        {entityMappings.map(m => {
                            const ds = datasources.find(d => d.id === m.datasourceId);
                            return (
                                <tr key={m.id}>
                                    <td>{m.clazz}</td>
                                    <td>{ds ? ds.name : '(未知)'}</td>
                                    <td>
                                        <button className="btn btn-xs btn-info"
                                                onClick={() => this.handleLoadFieldMappings(m.datasourceId, m.clazz)}>
                                            字段映射
                                        </button>
                                        {ds && ds.type === 'PKL' && (
                                            <>
                                                {' '}
                                                <button className="btn btn-xs btn-warning"
                                                        onClick={() => this.handleFetchModelFields(m.datasourceId, m.clazz, ds)}>
                                                    获取模型字段
                                                </button>
                                            </>
                                        )}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>

                {fieldMappings.length > 0 && (
                    <div>
                        <h5 style={{marginTop: '15px'}}>字段映射: {this.state.mappingFieldClazz}</h5>
                        <table className="table table-bordered table-hover" style={{fontSize: '13px'}}>
                            <thead><tr><th>规则变量名</th><th>外部字段名</th></tr></thead>
                            <tbody>
                                {fieldMappings.map(fm => (
                                    <tr key={fm.id}>
                                        <td>{fm.variableName}</td>
                                        <td>{fm.remoteField}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        );
    }
}

interface DatasourcePanelStateTree {
    datasource: {
        datasources: DatasourceItem[];
        entityMappings: EntityMapping[];
        fieldMappings: FieldMapping[];
        activeTab: string;
    };
}

function mapStateToProps(state: DatasourcePanelStateTree): Omit<DatasourcePanelProps, 'dispatch'> {
    return {
        datasources: state.datasource.datasources || [],
        entityMappings: state.datasource.entityMappings || [],
        fieldMappings: state.datasource.fieldMappings || [],
        activeTab: state.datasource.activeTab || 'datasources'
    };
}

export default connect(mapStateToProps)(DatasourcePanel);
