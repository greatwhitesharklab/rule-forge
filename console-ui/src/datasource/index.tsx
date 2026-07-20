// V7.24:拆分重构 — 本文件保留为容器(connect + 默认导出不变),子区块移至 ./components/
import React, {Component} from 'react';
import {connect} from 'react-redux';
import {Alert, Tabs} from 'antd';
import PageShell from '@/frame/components/PageShell';
import {alert} from '@/utils/modal';
import {
    loadDatasources, createDatasource, updateDatasource, deleteDatasource,
    testConnection, setSelectedDatasource, setTab,
    loadEntityMappings, saveEntityMapping,
    loadFieldMappings, saveFieldMappings,
    DatasourceItem, EntityMapping, FieldMapping
} from './action';
import DatasourceList from './components/DatasourceList';
import DatasourceForm, {ModelListItem} from './components/DatasourceForm';
import MappingPanel from './components/MappingPanel';
import {handleBatchTest} from './components/batchTestModals';

interface DatasourcePanelProps {
    dispatch: (action: unknown) => unknown;
    datasources: DatasourceItem[];
    entityMappings: EntityMapping[];
    fieldMappings: FieldMapping[];
    activeTab: string;
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

    render() {
        const {datasources, entityMappings, fieldMappings, activeTab} = this.props;
        const {showForm, formDatasource, testResult} = this.state;

        return (
            <PageShell
                title="数据源"
                description="管理决策流外部数据源连接与字段映射"
                toolbar={
                    <Tabs
                        activeKey={activeTab}
                        onChange={(key: string) => this.props.dispatch(setTab(key))}
                        items={[
                            {key: 'datasources', label: '数据源'},
                            {key: 'mappings', label: '映射配置'},
                        ]}
                    />
                }
            >
                {testResult && (
                    <Alert type="info" showIcon title={testResult} style={{marginBottom: 12}}/>
                )}

                {activeTab === 'datasources' && (
                    <DatasourceList
                        datasources={datasources}
                        formNode={showForm && formDatasource ? (
                            <DatasourceForm
                                formDatasource={formDatasource}
                                availableModels={this.state.availableModels}
                                onFormChange={this.handleFormChange}
                                onUpdateConfigJson={this.updateConfigJson}
                                onSave={this.handleSave}
                                onCancel={() => this.setState({showForm: false})}
                                onFetchModelList={this.fetchModelList}
                            />
                        ) : null}
                        onCreate={this.handleCreate}
                        onEdit={this.handleEdit}
                        onTest={this.handleTest}
                        onBatchTest={handleBatchTest}
                        onDelete={this.handleDelete}
                    />
                )}
                {activeTab === 'mappings' && (
                    <MappingPanel
                        datasources={datasources}
                        entityMappings={entityMappings}
                        fieldMappings={fieldMappings}
                        mappingClazz={this.state.mappingClazz}
                        mappingDatasourceId={this.state.mappingDatasourceId}
                        mappingFieldClazz={this.state.mappingFieldClazz}
                        onMappingClazzChange={(v: string) => this.setState({mappingClazz: v})}
                        onMappingDatasourceIdChange={(v: string) => this.setState({mappingDatasourceId: v})}
                        onSaveMapping={this.handleSaveMapping}
                        onLoadFieldMappings={this.handleLoadFieldMappings}
                        onFetchModelFields={this.handleFetchModelFields}
                    />
                )}
            </PageShell>
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
