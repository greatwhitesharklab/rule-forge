import React, {Component} from 'react';
import {connect} from 'react-redux';
import {
    loadDatasources, createDatasource, updateDatasource, deleteDatasource,
    testConnection, setSelectedDatasource, setTab,
    loadEntityMappings, saveEntityMapping,
    loadFieldMappings
} from './action';

class DatasourcePanel extends Component {

    state = {
        showForm: false,
        formDatasource: null,
        testResult: null,
        mappingClazz: '',
        mappingDatasourceId: '',
        mappingFieldClazz: ''
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

    handleEdit = (ds) => {
        this.setState({showForm: true, formDatasource: {...ds}});
    };

    handleDelete = (id) => {
        if (window.confirm('确定删除此数据源？')) {
            this.props.dispatch(deleteDatasource(id));
        }
    };

    handleTest = async (id) => {
        this.setState({testResult: '测试中...'});
        const result = await this.props.dispatch(testConnection(id));
        this.setState({testResult: result.success ? '✓ 连接成功' : '✗ 连接失败: ' + (result.message || '')});
        setTimeout(() => this.setState({testResult: null}), 3000);
    };

    handleSave = () => {
        const {formDatasource} = this.state;
        if (!formDatasource.name || !formDatasource.type) return;
        if (formDatasource.id) {
            this.props.dispatch(updateDatasource(formDatasource.id, formDatasource));
        } else {
            this.props.dispatch(createDatasource(formDatasource));
        }
        this.setState({showForm: false, formDatasource: null});
    };

    handleFormChange = (field, value) => {
        this.setState(prev => ({
            formDatasource: {...prev.formDatasource, [field]: value}
        }));
    };

    handleSaveMapping = () => {
        const {mappingClazz, mappingDatasourceId} = this.state;
        if (!mappingClazz || !mappingDatasourceId) return;
        this.props.dispatch(saveEntityMapping(mappingClazz, parseInt(mappingDatasourceId)));
        this.setState({mappingClazz: '', mappingDatasourceId: ''});
    };

    handleLoadFieldMappings = (dsId, clazz) => {
        this.setState({mappingFieldClazz: clazz});
        this.props.dispatch(setSelectedDatasource({id: dsId, clazz}));
        this.props.dispatch(loadFieldMappings(dsId, clazz));
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

    renderDatasources(datasources, showForm, formDatasource) {
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
                                    <button className="btn btn-xs btn-success" onClick={() => this.handleTest(ds.id)}>
                                        <i className="glyphicon glyphicon-signal"></i> 测试
                                    </button>
                                    {' '}
                                    <button className="btn btn-xs btn-danger" onClick={() => this.handleDelete(ds.id)}>
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

    renderForm(formDatasource) {
        const isAdvanceAi = formDatasource.type === 'ADVANCE_AI';
        const isJdbc = formDatasource.type === 'JDBC';

        let configJson = {};
        try { configJson = JSON.parse(formDatasource.configJson || '{}'); } catch (e) {}

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
                        {!isAdvanceAi && !isJdbc && this.renderRestConfig(configJson)}

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

    renderAdvanceAiConfig(config) {
        return (
            <>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Base URL</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={config.baseUrl || ''}
                               onChange={e => this.updateConfigJson('baseUrl', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Access Key</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" type="password" value={config.accessKey || ''}
                               onChange={e => this.updateConfigJson('accessKey', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Secret Key</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" type="password" value={config.secretKey || ''}
                               onChange={e => this.updateConfigJson('secretKey', e.target.value)} />
                    </div>
                </div>
            </>
        );
    }

    renderJdbcConfig(config) {
        return (
            <>
                <div className="form-group">
                    <label className="col-sm-2 control-label">JDBC URL</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={config.url || ''}
                               onChange={e => this.updateConfigJson('url', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">用户名</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={config.username || ''}
                               onChange={e => this.updateConfigJson('username', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">密码</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" type="password" value={config.password || ''}
                               onChange={e => this.updateConfigJson('password', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">查询模板</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm"
                               value={config.queryTemplate || ''}
                               placeholder="SELECT ${fieldName} FROM table WHERE user_id = '${entityId}'"
                               onChange={e => this.updateConfigJson('queryTemplate', e.target.value)} />
                    </div>
                </div>
            </>
        );
    }

    renderRestConfig(config) {
        return (
            <>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Base URL</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={config.baseUrl || ''}
                               onChange={e => this.updateConfigJson('baseUrl', e.target.value)} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Endpoint</label>
                    <div className="col-sm-6">
                        <input className="form-control input-sm" value={config.endpoint || ''}
                               onChange={e => this.updateConfigJson('endpoint', e.target.value)} />
                    </div>
                </div>
            </>
        );
    }

    updateConfigJson = (field, value) => {
        let config = {};
        try { config = JSON.parse(this.state.formDatasource.configJson || '{}'); } catch (e) {}
        config[field] = value;
        this.handleFormChange('configJson', JSON.stringify(config));
    };

    renderMappings(datasources, entityMappings, fieldMappings) {
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

function mapStateToProps(state) {
    return {
        datasources: state.datasource.datasources || [],
        entityMappings: state.datasource.entityMappings || [],
        fieldMappings: state.datasource.fieldMappings || [],
        activeTab: state.datasource.activeTab || 'datasources'
    };
}

export default connect(mapStateToProps)(DatasourcePanel);
