import React, {Component} from 'react';
import {connect} from 'react-redux';
import {Modal, Radio, Input, Form, Button, Space, Tag, Tooltip, Upload, message} from 'antd';
import {
    loadDatasources, createDatasource, updateDatasource, deleteDatasource,
    testConnection, setSelectedDatasource, setTab,
    loadEntityMappings, saveEntityMapping,
    loadFieldMappings, saveFieldMappings,
    DatasourceItem, EntityMapping, FieldMapping
} from './action';
import {startBatchTestWithFile} from '../api/client';
import * as batchTestEvent from '../package/event';

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
    // v5.8.4: Excel 上传批量测试的临时 state(modal 内绑定,onOk 读完即用)
    excelUploadFile: File | null;
}

class DatasourcePanel extends Component<DatasourcePanelProps, DatasourcePanelState> {

    state: DatasourcePanelState = {
        showForm: false,
        formDatasource: null,
        testResult: null,
        mappingClazz: '',
        mappingDatasourceId: '',
        mappingFieldClazz: '',
        availableModels: [],
        excelUploadFile: null
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
     * V5.8.4: 批量测试入口 — Antd Modal 支持 3 种模式:
     *   - "测决策流" (FLOW + DATASOURCE):用三方数据调 Flow,验证集成(手填 entityIds)
     *   - "裸数据源测试" (DATASOURCE + FILE):只调数据源,测 SLA(手填 entityIds)
     *   - "Excel 上传" (v5.8.4):上传 .xlsx,3 种 mode 都能走
     */
    handleBatchTest = async (ds: DatasourceItem) => {
        const testMode = await new Promise<'FLOW' | 'DATASOURCE' | 'EXCEL' | null>((resolve) => {
            let selectedMode: 'FLOW' | 'DATASOURCE' | 'EXCEL' = 'FLOW';
            Modal.confirm({
                title: (
                    <span>
                        <i className="glyphicon glyphicon-flash" style={{marginRight: 8}} />
                        批量测试 - {ds.name}
                    </span>
                ),
                icon: null,
                width: 580,
                content: (
                    <div>
                        <div style={{marginBottom: 16, color: '#666', fontSize: 13}}>
                            选一种测试模式。Excel 模式支持 1000+ 行(手填只适合小批量)。
                        </div>
                        <Radio.Group
                            defaultValue="FLOW"
                            onChange={(e) => { selectedMode = e.target.value; }}
                            style={{display: 'flex', flexDirection: 'column', gap: 12}}
                        >
                            <Radio value="FLOW" style={{alignItems: 'flex-start'}}>
                                <div>
                                    <div style={{fontWeight: 500}}>
                                        <Tag color="blue">FLOW + DATASOURCE</Tag>
                                        {' '}测决策流(用三方真实数据)
                                    </div>
                                    <div style={{fontSize: 12, color: '#999', marginTop: 4, marginLeft: 24}}>
                                        调数据源拿真实响应,跑决策流,验证集成链路
                                    </div>
                                </div>
                            </Radio>
                            <Radio value="DATASOURCE" style={{alignItems: 'flex-start'}}>
                                <div>
                                    <div style={{fontWeight: 500}}>
                                        <Tag color="orange">DATASOURCE + FILE</Tag>
                                        {' '}裸数据源测试(SLA / 字段映射)
                                    </div>
                                    <div style={{fontSize: 12, color: '#999', marginTop: 4, marginLeft: 24}}>
                                        Excel 喂 {`{entityId, fieldName}`},直接调数据源 connector
                                    </div>
                                </div>
                            </Radio>
                            <Radio value="EXCEL" style={{alignItems: 'flex-start'}}>
                                <div>
                                    <div style={{fontWeight: 500}}>
                                        <Tag color="green">上传 Excel (v5.8.4)</Tag>
                                        {' '}三种模式都支持
                                    </div>
                                    <div style={{fontSize: 12, color: '#999', marginTop: 4, marginLeft: 24}}>
                                        上传 .xlsx,固定列 schema:entityId / fieldName(可选 clazz)
                                    </div>
                                </div>
                            </Radio>
                        </Radio.Group>
                    </div>
                ),
                okText: '下一步',
                cancelText: '取消',
                onOk: () => { resolve(selectedMode); },
                onCancel: () => { resolve(null); },
            });
        });
        if (!testMode) return;

        if (testMode === 'FLOW') {
            this.showFlowBatchModal(ds);
        } else if (testMode === 'DATASOURCE') {
            this.showDatasourceOnlyBatchModal(ds);
        } else {
            this.showExcelUploadModal(ds);
        }
    };

    /**
     * FLOW + DATASOURCE 模式:用三方数据测决策流
     */
    showFlowBatchModal = (ds: DatasourceItem) => {
        let entityIds = '';
        let idField = 'id';
        let flowId = '';

        Modal.confirm({
            title: (
                <span>
                    <Tag color="blue">FLOW + DATASOURCE</Tag>
                    {' '}用 {ds.name} 的真实数据测决策流
                </span>
            ),
            icon: null,
            width: 540,
            content: (
                <Form layout="vertical" style={{marginTop: 8}}>
                    <Form.Item
                        label="Entity 主键值(逗号或空格分隔)"
                        required
                        help={'比如 "1,2,3,100,200" — 100 个值就要拉 100 次数据源'}
                    >
                        <Input.TextArea
                            rows={3}
                            defaultValue={entityIds}
                            placeholder="e.g. 1,2,3,100,200"
                            onChange={(e) => { entityIds = e.target.value; }}
                        />
                    </Form.Item>
                    <Form.Item label="主键字段名" help="数据源 connector 认的主键字段,默认 id">
                        <Input
                            defaultValue={idField}
                            onChange={(e) => { idField = e.target.value || 'id'; }}
                        />
                    </Form.Item>
                    <Form.Item
                        label="待测决策流 ID"
                        required
                        help="跑哪个 Flow,比如 loan-approval / risk-score"
                    >
                        <Input
                            defaultValue={flowId}
                            placeholder="e.g. loan-approval"
                            onChange={(e) => { flowId = e.target.value; }}
                        />
                    </Form.Item>
                </Form>
            ),
            okText: '开始测试',
            cancelText: '取消',
            onOk: () => {
                const valueList = entityIds.split(/[,\s]+/).filter((s: string) => s.length > 0);
                if (valueList.length === 0) {
                    window.bootbox.alert('请输入 entityIds');
                    return Promise.reject();
                }
                if (!flowId) {
                    window.bootbox.alert('请输入 Flow ID');
                    return Promise.reject();
                }
                this.startBatchTestDs(ds, 'FLOW', idField, valueList, flowId);
                return Promise.resolve();
            },
        });
    };

    /**
     * DATASOURCE + FILE 模式:裸数据源 SLA / 字段映射验证
     * 简化版:用 Antd prompt 拿 entityIds + fieldName,直接调数据源
     * (实际生产里应该 Excel 导入,但 MVP 简化)
     */
    showDatasourceOnlyBatchModal = (ds: DatasourceItem) => {
        let entityIds = '';
        let fieldName = '';
        let idField = 'id';

        Modal.confirm({
            title: (
                <span>
                    <Tag color="orange">DATASOURCE + FILE</Tag>
                    {' '}测 {ds.name} 的数据源连接
                </span>
            ),
            icon: null,
            width: 540,
            content: (
                <Form layout="vertical" style={{marginTop: 8}}>
                    <Form.Item
                        label="要拉的字段名"
                        required
                        help="数据源 connector 认的字段,比如 score / name"
                    >
                        <Input
                            defaultValue={fieldName}
                            placeholder="e.g. score"
                            onChange={(e) => { fieldName = e.target.value; }}
                        />
                    </Form.Item>
                    <Form.Item
                        label="Entity 主键值(逗号或空格分隔)"
                        required
                        help="N 个值就会拉 N 次数据源"
                    >
                        <Input.TextArea
                            rows={3}
                            defaultValue={entityIds}
                            placeholder="e.g. 1,2,3,100,200"
                            onChange={(e) => { entityIds = e.target.value; }}
                        />
                    </Form.Item>
                    <Form.Item label="主键字段名" help="数据源主键字段,默认 id">
                        <Input
                            defaultValue={idField}
                            onChange={(e) => { idField = e.target.value || 'id'; }}
                        />
                    </Form.Item>
                    <div style={{background: '#fffbe6', border: '1px solid #ffe58f',
                                padding: 8, borderRadius: 4, marginTop: 8, fontSize: 12, color: '#874d00'}}>
                        <strong>说明:</strong> V5.8.2 简化为 Modal 填 entityIds;
                        后续接 Excel 导入可以走老路径或加新 FILE 上传端点。
                    </div>
                </Form>
            ),
            okText: '开始测试',
            cancelText: '取消',
            onOk: () => {
                const valueList = entityIds.split(/[,\s]+/).filter((s: string) => s.length > 0);
                if (!fieldName) {
                    window.bootbox.alert('请输入 fieldName');
                    return Promise.reject();
                }
                if (valueList.length === 0) {
                    window.bootbox.alert('请输入 entityIds');
                    return Promise.reject();
                }
                // 构造 Excel 风格的 rows:[{entityId, fieldName, clazz: ''}]
                // 然后调 startBatchTest(subjectType=DATASOURCE, inputSourceType=FILE, inputConfig={rows: [...]})
                // V5.8.2 简化:用 inline inputConfig,后端 DatasourceInputSource 暂未实现
                // FILE 模式 — 走老 BatchTestService 异步执行
                this.startBatchTestDs(ds, 'DATASOURCE', idField, valueList, fieldName);
                return Promise.resolve();
            },
        });
    };

    /**
     * 调后端 startBatchTest + emit dialog 事件
     * subjectType: 'FLOW' (调决策流) | 'DATASOURCE' (只测数据源)
     */
    startBatchTestDs = async (
        ds: DatasourceItem,
        subjectType: 'FLOW' | 'DATASOURCE',
        idField: string,
        valueList: string[],
        extra: string,  // flowId or fieldName
    ) => {
        const ce = (window as any).parent?.componentEvent || (window as any).componentEvent;

        try {
            let body: Record<string, unknown>;
            if (subjectType === 'FLOW') {
                body = {
                    subjectType: 'FLOW',
                    subjectId: null,
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
                    flowId: extra  // extra is flowId
                };
            } else {
                // DATASOURCE subject:inputConfig.rows 是 [{entityId, fieldName}]
                // (V5.8.2 简化:用 inline inputConfig,FileInputSource 暂未实现 FILE 模式 fetch)
                body = {
                    subjectType: 'DATASOURCE',
                    subjectId: ds.id,  // subjectId = datasourceId in this mode
                    inputSourceType: 'FILE',  // V5.8.2 暂用 FILE + inline rows(后续会走 Excel)
                    inputSourceId: null,
                    inputConfig: {
                        // For V5.8.2 简化的 DATASOURCE+FILE 路径,FileInputSource
                        // 会校验 session 有 rows。这里我们靠 BatchTestServiceImpl.executeBatchAsync
                        // 复用老路径(测数据源走的代码还没接好 — 留 V5.8.3+)
                        datasourceId: ds.id,
                        rows: valueList.map((id) => ({ entityId: id, fieldName: extra }))
                    },
                    project: '',
                    packageId: '',
                    flowId: ''  // not used for DATASOURCE subject
                };
            }
            const resp = await fetch((window as any)._server + '/batchtest/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            const result = await resp.json();

            if (result.sessionId) {
                batchTestEvent.eventEmitter.emit(batchTestEvent.OPEN_BATCH_TEST_DIALOG, {
                    sessionId: result.sessionId,
                    data: { subjectType, inputSourceType: 'DATASOURCE', skipStart: true }
                });
                if (ce) ce.eventEmitter.emit(ce.SHOW_LOADING);
            } else {
                window.bootbox.alert('启动失败: ' + (result.error || JSON.stringify(result)));
            }
        } catch (e: any) {
            window.bootbox.alert('请求失败: ' + e.message);
        }
    };

    /**
     * v5.8.4 新:Excel 上传批量测试。
     * 弹一个 Antd Modal,让用户选 subject(FLOW / DATASOURCE) + 拖 .xlsx。
     * Excel schema:
     *   - DATASOURCE+FILE:列 entityId, fieldName, clazz(可选)
     *   - FLOW+DATASOURCE:列 entityId(主键)+ 其他要 fetch 的列
     */
    showExcelUploadModal = (ds: DatasourceItem) => {
        let subjectType: 'FLOW' | 'DATASOURCE' = 'DATASOURCE';
        let idField = 'entityId';
        let flowId = '';
        let uploadedFile: File | null = null;

        Modal.confirm({
            title: (
                <span>
                    <Tag color="green">上传 Excel (v5.8.4)</Tag>
                    {' '}测 {ds.name}
                </span>
            ),
            icon: null,
            width: 560,
            content: (
                <Form layout="vertical" style={{marginTop: 8}}>
                    <Form.Item label="测试模式" required>
                        <Radio.Group
                            defaultValue="DATASOURCE"
                            onChange={(e) => { subjectType = e.target.value; }}
                        >
                            <Radio value="DATASOURCE">DATASOURCE + FILE</Radio>
                            <Radio value="FLOW">FLOW + DATASOURCE</Radio>
                        </Radio.Group>
                    </Form.Item>
                    <Form.Item label="Excel 文件" required
                               help="DATASOURCE+FILE:entityId/fieldName/clazz 列;FLOW+DATASOURCE:entityId + 其他列">
                        <Upload.Dragger
                            accept=".xlsx,.xls"
                            maxCount={1}
                            beforeUpload={(file) => {
                                uploadedFile = file;
                                return false;  // 不自动上传
                            }}
                            onRemove={() => { uploadedFile = null; return true; }}
                            fileList={uploadedFile ? [{
                                uid: '1', name: uploadedFile.name, status: 'done',
                            }] : []}
                        >
                            <p className="ant-upload-drag-icon">
                                <i className="glyphicon glyphicon-folder-open" />
                            </p>
                            <p className="ant-upload-text">点击或拖拽 .xlsx 到这里</p>
                            <p className="ant-upload-hint">单文件,最大 10000 行</p>
                        </Upload.Dragger>
                    </Form.Item>
                    <Form.Item label="主键字段名" help="Excel 里 entityId 那一列的名字,默认 entityId">
                        <Input
                            defaultValue={idField}
                            onChange={(e) => { idField = e.target.value || 'entityId'; }}
                        />
                    </Form.Item>
                    <Form.Item label="待测决策流 ID(仅 FLOW 模式)" help="FLOW 模式必填,DATASOURCE 模式忽略">
                        <Input
                            defaultValue={flowId}
                            placeholder="e.g. loan-approval"
                            onChange={(e) => { flowId = e.target.value; }}
                        />
                    </Form.Item>
                </Form>
            ),
            okText: '启动测试',
            cancelText: '取消',
            onOk: () => {
                if (!uploadedFile) {
                    message.error('请先选择 Excel 文件');
                    return Promise.reject();
                }
                if (subjectType === 'FLOW' && !flowId) {
                    message.error('FLOW 模式需要决策流 ID');
                    return Promise.reject();
                }
                this.startBatchTestWithExcel(ds, subjectType, idField, flowId, uploadedFile);
                return Promise.resolve();
            },
        });
    };

    /**
     * v5.8.4 新:走 multipart /batchtest/start-with-file
     */
    startBatchTestWithExcel = async (
        ds: DatasourceItem,
        subjectType: 'FLOW' | 'DATASOURCE',
        idField: string,
        flowId: string,
        file: File,
    ) => {
        const ce = (window as any).parent?.componentEvent || (window as any).componentEvent;

        try {
            let req: Record<string, unknown>;
            if (subjectType === 'FLOW') {
                req = {
                    subjectType: 'FLOW',
                    subjectId: null,
                    inputSourceType: 'DATASOURCE',
                    inputSourceId: ds.id,
                    inputConfig: {
                        datasourceId: ds.id,
                        clazz: '',
                        inputField: idField,
                    },
                    project: '',
                    packageId: '',
                    flowId,
                };
            } else {
                req = {
                    subjectType: 'DATASOURCE',
                    subjectId: ds.id,
                    inputSourceType: 'FILE',
                    inputSourceId: null,
                    inputConfig: {
                        datasourceId: ds.id,
                        // fieldName 暂存到 inputConfig,Excel 每行都得含 fieldName 列
                        // (后端 parser 不读 inputConfig 里的 fieldName,以列名为准)
                    },
                    project: '',
                    packageId: '',
                    flowId: '',
                };
            }
            const result = await startBatchTestWithFile(req as any, file);

            if (result.sessionId) {
                batchTestEvent.eventEmitter.emit(batchTestEvent.OPEN_BATCH_TEST_DIALOG, {
                    sessionId: result.sessionId,
                    data: { subjectType, inputSourceType: req.inputSourceType, skipStart: true }
                });
                if (ce) ce.eventEmitter.emit(ce.SHOW_LOADING);
            } else {
                message.error('启动失败: ' + JSON.stringify(result));
            }
        } catch (e: any) {
            message.error('Excel 上传失败: ' + (e.message || e));
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
                                    <Space size="middle">
                                        <Tooltip title="编辑数据源">
                                            <Button
                                                size="small"
                                                icon={<i className="glyphicon glyphicon-edit"></i>}
                                                onClick={() => this.handleEdit(ds)}
                                            />
                                        </Tooltip>
                                        <Tooltip title="测试连接(单条)">
                                            <Button
                                                size="small"
                                                type="primary"
                                                ghost
                                                icon={<i className="glyphicon glyphicon-signal"></i>}
                                                onClick={() => this.handleTest(ds.id!)}
                                            >
                                                测试
                                            </Button>
                                        </Tooltip>
                                        <Tooltip title="批量测试 V5.8.0+:FLOW 测集成 / DATASOURCE 测数据源">
                                            <Button
                                                size="small"
                                                type="primary"
                                                icon={<i className="glyphicon glyphicon-flash"></i>}
                                                onClick={() => this.handleBatchTest(ds)}
                                            >
                                                批量测试
                                            </Button>
                                        </Tooltip>
                                        <Tooltip title="删除数据源">
                                            <Button
                                                size="small"
                                                danger
                                                icon={<i className="glyphicon glyphicon-trash"></i>}
                                                onClick={() => this.handleDelete(ds.id!)}
                                            />
                                        </Tooltip>
                                    </Space>
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
