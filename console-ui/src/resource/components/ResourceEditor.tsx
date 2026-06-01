import React, {Component} from 'react';
import Grid from '../../components/grid/component/Grid.tsx';
import * as action from '../action.js';
import * as refEvent from '../../reference/event.js';
import ReferenceDialog from '../../reference/ReferenceDialog.jsx';
import {connect} from 'react-redux';
import Splitter from '../../components/splitter/component/Splitter.tsx';
import * as componentEvent from '../../components/componentEvent.js';
import AddParamsDialog from './AddParamsDialog.jsx';
import * as event from '../event.js';
import type {ResourceCategory, VariableItem} from '../action.js';
import type {ResourceRootState} from '../reducer.js';

interface ResourceEditorProps {
    masterData: ResourceCategory[];
    masterRowData: ResourceCategory;
    dispatch: (a: unknown) => unknown;
    file: string;
}

class ResourceEditor extends Component<ResourceEditorProps> {
    masterData: ResourceCategory | null = null;
    currentData: VariableItem | null = null;

    constructor(props: ResourceEditorProps) {
        super(props);
    }

    render() {
        const {masterData, masterRowData, dispatch, file} = this.props;
        const masterGridHeaders = [
            {id: 'master-name', name: 'name', label: '名称', filterable: true, editable: false, width: '130px'},
            {id: 'master-clazz', name: 'clazz', label: '类路径', filterable: true, editable: false}
        ];
        const slaveGridHeaders = [
            {id: 'slave-name', name: 'name', label: '字段名称', filterable: true, editable: false, width: '130px'},
            {id: 'slave-label', name: 'label', label: '标题(中文名)', filterable: true, width: '220px', editable: false},
            {id: 'slave-defaultVal', name: 'defaultVal', label: '默认值', editable: false},
            {
                id: 'slave-type',
                name: 'type',
                label: '数据类型',
                width: '90px',
                editorType: 'select',
                selectData: ['String', 'Integer', 'Char', 'Double', 'Long', 'Float', 'BigDecimal', 'Boolean', 'Date', 'List', 'Set', 'Map', 'Enum', 'Object'],
                editable: true
            },
            {id: 'slave-logicComment', name: 'logicComment', label: '实现逻辑', editable: false},
            {id: 'slave-categoryLabel', name: 'categoryLabel', label: '类型', filterable: true, editable: false, width: '70px'},
            {
                id: 'slave-dsStatus', name: 'dsStatus', label: '状态', filterable: true, editable: false, width: '70px', formatter: (value: number) => {
                    switch (value) {
                        case 0:
                            return '待开发';
                        case 1:
                            return '已上线';
                        default:
                            return value;
                    }
                }
            },
        ];

        const slaveGridOperationCol = {
            width: '60px',
            operations: [
                {
                    label: '删除',
                    icon: 'glyphicon glyphicon-trash',
                    style: {fontSize: 'var(--rf-font-size-lg)', color: 'var(--rf-danger)', padding: '0px 10px', cursor: 'pointer'},
                    click: function (rowIndex: number) {
                        window.bootbox.confirm('真的要删除当前记录？', function (result) {
                            if (!result) return;
                            dispatch(action.deleteSlave(rowIndex));
                        })
                    }
                }
            ]
        };

        let variables = masterRowData.variables || [];
        variables = variables.filter(item => +item.dsStatus !== 99);
        return (
            <div style={{margin: '0px'}}>
                <ReferenceDialog/>
                <AddParamsDialog dispatch={dispatch} file={file}/>
                <Splitter orientation='vertical' position='450px'>
                    <div style={{padding: '0px'}}>
                        <div style={{margin: '2px'}}>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-primary" type="button" onClick={(e) => {
                                    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                    dispatch(action.reFresh(file))
                                }}><i className="glyphicon glyphicon glyphicon-refresh"/> 刷新
                                </button>
                            </div>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-info" type="button" onClick={(e) => {
                                    if (!this.currentData) {
                                        window.bootbox.alert('请先选择一条具体的变量');
                                        return;
                                    }
                                    const title = `变量"${this.masterData!.name}.${this.currentData.label}"`;
                                    const data = {
                                        path: file,
                                        varCategory: this.masterData!.name,
                                        varLabel: this.currentData.label,
                                        varName: this.currentData.name
                                    };
                                    refEvent.eventEmitter.emit(refEvent.OPEN_REFERENCE_DIALOG, data, title, {fromResourceEditor: true});
                                }}><i className="rf rf-link"/> 查看引用
                                </button>
                            </div>
                        </div>

                        <Grid headers={masterGridHeaders} dispatch={dispatch} rows={masterData} rowClick={(rowData: ResourceCategory) => {
                            this.masterData = rowData;
                            this.currentData = null;
                            setTimeout(function () {
                                dispatch(action.loadSlaveData(rowData));
                            }, 100);
                        }}/>
                    </div>
                    <div style={{padding: '0px'}}>
                        <div style={{margin: '2px'}}>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-primary" type="button" onClick={(e) => {
                                    console.log(masterRowData)
                                    if (masterRowData.name) {
                                        event.eventEmitter.emit(event.OPEN_CREATE_PARAMS_DIALOG, {
                                            create: true,
                                            data: masterRowData,
                                            title: "新增[" + masterRowData.name + "]数据源字段",
                                            callback: () => {
                                                setTimeout(() => {
                                                    dispatch(action.loadSlaveData(this.masterData!));
                                                }, 100);
                                            }
                                        })
                                    } else {
                                        window.bootbox.alert('请先选择一条数据源！');
                                    }
                                }}><i className="glyphicon glyphicon-plus-sign"/> 添加字段
                                </button>
                            </div>
                        </div>
                        <Grid headers={slaveGridHeaders} dispatch={dispatch}
                              operationConfig={slaveGridOperationCol}
                              rows={variables || []} rowClick={(rowData: VariableItem) => {
                            this.currentData = rowData;
                        }}/>
                    </div>
                </Splitter>
            </div>
        );
    }
}

function select(state: ResourceRootState): { masterData: ResourceCategory[]; masterRowData: ResourceCategory } {
    return {
        masterData: state.master.data || [],
        masterRowData: state.slave.data || {} as ResourceCategory
    };
}

export default connect(select)(ResourceEditor);
