import {Component} from 'react';
import {connect} from 'react-redux';
import {Dispatch} from 'redux';
import Grid from '../../components/grid/component/Grid.tsx';
import Splitter from '../../components/splitter/component/Splitter.tsx';
import * as action from '../action.js';
import {
    ResourcePackage,
    PackageConfig,
    ResourceItem,
} from '../action.js';
import PackageDialog from './PackageDialog.jsx';
import ItemDialog from './ItemDialog.jsx';
import * as event from '../event.js';
import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import SimulatorPage from './SimulatorPage.jsx';
import ReteDiagramDialog from './ReteDiagramDialog.jsx';
import FlowDialog from './FlowDialog.jsx';
import ImportExcelDataDialog from './ImportExcelDataDialog.jsx';
import ExportExcelDataDialog from './ExportExcelDataDialog.jsx';
import ImportExcelErrorDialog from './ImportExcelErrorDialog.jsx';
import BatchTestDialog from './BatchTestDialog.jsx';
import ChildListDialog from '../../components/grid/component/ChildListDialog.tsx';
import VersionsDialog from './VersionsDialog.jsx'

interface PackageEditorProps {
    masterData: ResourcePackage[];
    masterRowData: ResourcePackage;
    dispatch: Dispatch;
    project: string;
    packageConfig: PackageConfig;
}

class PackageEditor extends Component<PackageEditorProps> {
    currentPackage: ResourcePackage | null = null;

    render() {
        const containerWidth = document.getElementById('container')!.clientWidth;
        const {masterData, masterRowData, dispatch, project, packageConfig} = this.props;
        const _this = this;
        const masterGridHeaders = [
            {id: 'm-id', name: 'id', label: '编码', filterable: true},
            {id: 'm-name', name: 'name', label: '名称', filterable: true, width: '160px'},
            {
                id: 'm-createDate',
                name: 'createDate',
                label: '创建日期',
                width: '150px',
                dateFormat: 'yyyy-MM-dd HH:mm:ss'
            },
            {id: 'm-version', name: 'version', label: '使用中版本', width: '100px'},
            {id: 'm-testVersion', name: 'testVersion', label: '测试版本', width: '100px'},
        ];
        const slaveGridHeaders = [
            {id: 's-name', name: 'name', label: '名称', filterable: true, width: '200px'},
            {id: 's-path', name: 'path', label: '资源文件路径'},
            {id: 's-version', name: 'version', label: '版本', width: '70px'}
        ];

        const masterGridOperationCol = {
            width: '80px',
            operations: [
                {
                    label: '编辑',
                    icon: 'glyphicon glyphicon-edit',
                    style: {fontSize: 'var(--rf-font-size-lg)', color: 'var(--rf-danger)', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex: number, rowData: ResourcePackage) {
                        event.eventEmitter.emit(event.OPEN_CREATE_PACKAGE_DIALOG, {
                            create: false,
                            rowIndex,
                            rowData,
                            title: '编辑知识包'
                        });
                    }
                },
                {
                    label: '删除',
                    icon: 'glyphicon glyphicon-trash',
                    style: {fontSize: 'var(--rf-font-size-lg)', color: 'var(--rf-danger)', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex: number) {
                        window.bootbox.confirm('真的要删除当前记录？', function (result: boolean) {
                            if (!result) return;
                            _this.currentPackage = null;
                            dispatch(action.deleteMaster(rowIndex));
                            dispatch(action.loadSlaveData({} as ResourcePackage));
                        });
                    }
                }
            ]
        };

        const slaveGridOperationCol = {
            width: '90px',
            operations: [
                {
                    label: '编辑',
                    icon: 'glyphicon glyphicon-edit',
                    style: {fontSize: 'var(--rf-font-size-lg)', color: 'var(--rf-danger)', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex: number, rowData: ResourceItem) {
                        event.eventEmitter.emit(event.OPEN_CREATE_PACKAGE_ITEM_DIALOG, {
                            create: false,
                            rowData,
                            rowIndex,
                            title: '编辑知识文件'
                        })
                    }
                },
                {
                    label: '删除',
                    icon: 'glyphicon glyphicon-trash',
                    style: {fontSize: 'var(--rf-font-size-lg)', color: 'var(--rf-danger)', padding: '0px 10px', cursor: 'pointer'},
                    click: function (rowIndex: number) {
                        window.bootbox.confirm('真的要删除当前记录？', function (result: boolean) {
                            if (!result) return;
                            dispatch(action.deleteSlave(rowIndex));
                        })
                    }
                }
            ]
        };
        return (
            <div>
                <PackageDialog dispatch={dispatch}/>
                <SimulatorPage/>
                <ItemDialog dispatch={dispatch} project={this.props.project}/>
                <KnowledgeTreeDialog/>
                <ReteDiagramDialog/>
                <ImportExcelDataDialog/>
                <ExportExcelDataDialog/>
                <ImportExcelErrorDialog/>
                <FlowDialog/>
                <BatchTestDialog/>
                <ChildListDialog/>
                <VersionsDialog dispatch={dispatch} project={this.props.project}/>
                <Splitter orientation='vertical' position='50%' limit='300'>
                    <div>
                        <div style={{margin: '2px'}}>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-primary" type="button" onClick={() => {
                                    event.eventEmitter.emit(event.OPEN_CREATE_PACKAGE_DIALOG, {
                                        create: true,
                                        title: '添加知识包'
                                    });
                                }}><i className="glyphicon glyphicon-plus-sign"/> 添加包
                                </button>
                            </div>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-success" type="button" onClick={() => {
                                    dispatch(action.save(false, project))
                                }}><i className="glyphicon glyphicon-floppy-saved"/> 保存
                                </button>
                                <button className="btn btn-success" type="button" onClick={() => {
                                    if (this.currentPackage) {
                                        event.eventEmitter.emit(event.OPEN_VERSION_DIALOG, this.currentPackage);
                                    } else {
                                        window.bootbox.alert('请先选择一个知识包！');
                                    }
                                }}><i className="glyphicon glyphicon-floppy-disk"/> 生成版本
                                </button>
                            </div>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-info" type="button" onClick={() => {
                                    if (this.currentPackage) {
                                        dispatch(action.apply(project, packageConfig, this.currentPackage))
                                    } else {
                                        window.bootbox.alert('请先选择一个知识包！');
                                    }
                                }}><i className="glyphicon glyphicon-send"/> 发起审批
                                </button>
                            </div>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-warning" type="button" onClick={() => {
                                    if (this.currentPackage) {
                                        action.refreshKnowledgeCache(project, packageConfig, this.currentPackage);
                                    } else {
                                        window.bootbox.alert('请先选择一个知识包！');
                                    }
                                }}><i className="glyphicon glyphicon-cloud-upload"/> 发布测试
                                </button>
                                <button className="btn btn-danger" type="button" onClick={() => {
                                    if (this.currentPackage) {
                                        event.eventEmitter.emit(event.OPEN_SIMULATOR_DIALOG, this.currentPackage);
                                    } else {
                                        window.bootbox.alert('请先选择一个知识包！');
                                    }
                                }}><i className="glyphicon glyphicon-flash"/> 仿真测试
                                </button>
                            </div>
                        </div>

                        <Grid headers={masterGridHeaders} dispatch={dispatch} rows={masterData}
                              operationConfig={masterGridOperationCol} rowClick={(rowData: ResourcePackage) => {
                            this.currentPackage = rowData;
                            setTimeout(function () {
                                dispatch(action.loadSlaveData(rowData));
                            }, 100);
                        }}/>
                    </div>
                    <div>
                        <div style={{margin: '2px'}}>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-primary" type="button" onClick={() => {
                                    if (masterRowData.resourceItems) {
                                        event.eventEmitter.emit(event.OPEN_CREATE_PACKAGE_ITEM_DIALOG, {
                                            create: true,
                                            title: "添加包[" + masterRowData.name + "]中的知识文件"
                                        })
                                    } else {
                                        window.bootbox.alert('请先选择一个知识包！');
                                    }
                                }}><i className="fa fa-plus-square"/> 添加文件
                                </button>
                            </div>
                        </div>
                        <Grid headers={slaveGridHeaders} dispatch={dispatch} operationConfig={slaveGridOperationCol}
                              rows={masterRowData.resourceItems || []}/>
                    </div>
                </Splitter>
            </div>
        );
    }
}

interface StateTree {
    master: { data?: ResourcePackage[] };
    slave: { data?: ResourcePackage };
    config: { data?: PackageConfig };
}

function select(state: StateTree): PackageEditorProps {
    return {
        masterData: state.master.data || [],
        masterRowData: state.slave.data || {} as ResourcePackage,
        packageConfig: state.config.data || {} as PackageConfig,
        dispatch: null as unknown as Dispatch,
        project: '' as string,
    };
}

export default connect(select)(PackageEditor);
