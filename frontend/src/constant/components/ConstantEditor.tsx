import React from 'react';
import {connect} from 'react-redux';
import Grid from '../../components/grid/component/Grid.tsx';
import Splitter from '../../components/splitter/component/Splitter.tsx';
import * as action from '../action.js';
import type {ConstantCategory, ConstantItem} from '../action.js';
import * as refEvent from '../../reference/event.js';
import ReferenceDialog from '../../reference/ReferenceDialog.tsx';

interface ConstantEditorProps {
    dispatch: (a: unknown) => unknown;
    masterData: ConstantCategory[];
    masterRowData: ConstantCategory | {};
    file: string;
}

interface GridHeader {
    id: string;
    name: string;
    label: string;
    filterable?: boolean;
    editable?: boolean;
    width?: string;
    editorType?: string;
    selectData?: string[];
}

interface GridOperation {
    label: string;
    icon: string;
    style: React.CSSProperties;
    click: (rowIndex: number) => void;
}

interface GridOperationConfig {
    width: string;
    operations: GridOperation[];
}

class ConstantEditor extends React.Component<ConstantEditorProps> {
    masterData: ConstantCategory | null = null;
    currentData: ConstantItem | null = null;

    render() {
        const {dispatch, masterData, masterRowData, file} = this.props;
        const masterHeaders: GridHeader[] = [
            {id: 'master-name', name: 'name', label: '名称', filterable: true, editable: true, width: '200px'},
            {id: 'master-label', name: 'label', label: '标题', filterable: true, editable: true}
        ];
        const slaveHeaders: GridHeader[] = [
            {id: 'slave-name', name: 'name', label: '名称', filterable: true, editable: true, width: '160px'},
            {id: 'slave-label', name: 'label', label: '标题', filterable: true, editable: true},
            {
                id: 'slave-type', name: 'type', label: '数据类型', width: '160px', editable: true,
                editorType: 'select',
                selectData: ['String', 'Integer', 'Char', 'Double', 'Long', 'Float', 'BigDecimal', 'Boolean', 'Date', 'List', 'Set', 'Map', 'Enum', 'Object']
            },
        ];
        const masterGridOperationCol: GridOperationConfig = {
            width: '100px',
            operations: [
                {
                    label: '删除',
                    icon: 'glyphicon glyphicon-trash',
                    style: {fontSize: 'var(--rf-font-size-lg)', color: 'var(--rf-danger)', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex: number) {
                        window.bootbox.confirm('真的要删除当前记录？', function (result) {
                            if (!result) return;
                            dispatch(action.deleteMaster(rowIndex));
                            dispatch(action.loadSlaveData({}));
                        });
                    }
                }
            ]
        };
        const slaveGridOperationCol: GridOperationConfig = {
            width: '70px',
            operations: [
                {
                    label: '删除',
                    icon: 'glyphicon glyphicon-trash',
                    style: {fontSize: 'var(--rf-font-size-lg)', color: 'var(--rf-danger)', padding: '0px 10px', cursor: 'pointer'},
                    click: function (rowIndex: number) {
                        window.bootbox.confirm('真的要删除当前记录？', function (result) {
                            if (!result) return;
                            dispatch(action.deleteSlave(rowIndex));
                        });
                    }
                }
            ]
        };
        return (
            <div className="row" style={{margin: '0px'}}>
                <ReferenceDialog/>
                <Splitter orientation='vertical' position='40%'>
                    <div>
                        <div style={{margin: '2px'}}>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-primary" type="button"
                                        onClick={() => {dispatch(action.addMaster());}}>
                                    <i className="glyphicon glyphicon-plus-sign"></i> 添加分类
                                </button>
                            </div>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-danger" type="button"
                                        onClick={() => {dispatch(action.save(false, file));}}>
                                    <i className="rf rf-save"></i> 保存
                                </button>
                                <button className="btn btn-danger" type="button"
                                        onClick={() => {dispatch(action.save(true, file));}}
                                        style={{display: 'none'}}>
                                    <i className="rf rf-savenewversion"></i> 保存为新版本
                                </button>
                            </div>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-info" type="button" onClick={() => {
                                    if (!this.currentData) {
                                        window.bootbox.alert('请先选择一条具体的常量');
                                        return;
                                    }
                                    const title = '常量"' + (this.masterData?.name ?? '') + '.' + this.currentData.name + '"';
                                    const data = {
                                        path: file,
                                        constCategory: this.masterData!.name,
                                        constCategoryLabel: this.masterData!.label,
                                        constLabel: this.currentData.label,
                                        constName: this.currentData.name
                                    };
                                    refEvent.eventEmitter.emit(refEvent.OPEN_REFERENCE_DIALOG, data, title);
                                }}><i className="rf rf-link"></i> 查看引用</button>
                            </div>
                        </div>
                        <Grid headers={masterHeaders} dispatch={dispatch} rows={masterData}
                              operationConfig={masterGridOperationCol} rowClick={(rowData: ConstantCategory) => {
                            this.masterData = rowData;
                            this.currentData = null;
                            dispatch(action.loadSlaveData(rowData));
                        }}></Grid>
                    </div>
                    <div>
                        <div style={{margin: '2px'}}>
                            <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                                <button className="btn btn-primary" type="button"
                                        onClick={() => {dispatch(action.addSlave());}}>
                                    <i className="glyphicon glyphicon-plus"></i> 添加常量
                                </button>
                            </div>
                        </div>
                        <Grid headers={slaveHeaders} dispatch={dispatch}
                              rows={(masterRowData as ConstantCategory).constants || []}
                              operationConfig={slaveGridOperationCol}
                              rowClick={(rowData: ConstantItem) => {
                                  this.currentData = rowData;
                              }}></Grid>
                    </div>
                </Splitter>
            </div>
        );
    }
}

interface ConstantStateTree {
    master: { data: ConstantCategory[] };
    slave: { data: ConstantCategory | {} };
}

function select(state: ConstantStateTree, ownProps: { file: string }): Omit<ConstantEditorProps, 'dispatch'> {
    return {
        masterData: state.master.data || [],
        masterRowData: state.slave.data || {},
        file: ownProps.file
    };
}

export default connect(select)(ConstantEditor);
