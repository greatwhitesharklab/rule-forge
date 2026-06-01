import React, {Component} from 'react';
import {connect} from 'react-redux';
import Grid from '../../components/grid/component/Grid.tsx';
import * as action from '../action.js';
import type {ParameterItem} from '../action.js';
import * as refEvent from '../../reference/event.js';
import ReferenceDialog from '../../reference/ReferenceDialog.tsx';

interface ParameterEditorProps {
    dispatch: (a: unknown) => unknown;
    data: ParameterItem[];
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

class ParameterEditor extends Component<ParameterEditorProps> {
    currentData: ParameterItem | null = null;

    render() {
        const {data, file, dispatch} = this.props;
        const headers: GridHeader[] = [
            {id: 'p-name', name: 'name', label: '名称', filterable: true, editable: true, width: '260px'},
            {id: 'p-label', name: 'label', label: '标题', filterable: true, editable: true, width: '260px'},
            {
                id: 'p-type', name: 'type', label: '数据类型', editable: true,
                editorType: 'select',
                selectData: ['String', 'Integer', 'Char', 'Double', 'Long', 'Float', 'BigDecimal', 'Boolean', 'Date', 'List', 'Set', 'Map', 'Enum', 'Object']
            }
        ];
        const operationConfig: GridOperationConfig = {
            width: '100px',
            operations: [
                {
                    label: '删除',
                    icon: 'glyphicon glyphicon-trash',
                    style: {fontSize: 'var(--rf-font-size-lg)', color: 'var(--rf-danger)', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex: number) {
                        window.bootbox.confirm('真的要删除当前记录？', function (result) {
                            if (!result) return;
                            dispatch(action.del(rowIndex));
                        });
                    }
                }
            ]
        };
        return (
            <div>
                <ReferenceDialog/>
                <div style={{margin: '2px'}}>
                    <div className="btn-group btn-group-sm" style={{margin: '2px'}}>
                        <button className="btn btn-primary" type="button"
                                onClick={() => {dispatch(action.add());}}>
                            <i className="glyphicon glyphicon-plus-sign"></i> 添加
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
                                window.bootbox.alert('请先选择一条具体的参数');
                                return;
                            }
                            const title = '参数"' + this.currentData.name + '"';
                            const refData = {
                                path: file,
                                varLabel: this.currentData.label,
                                varName: this.currentData.name
                            };
                            refEvent.eventEmitter.emit(refEvent.OPEN_REFERENCE_DIALOG, refData, title);
                        }}><i className="rf rf-link"></i> 查看引用</button>
                    </div>
                </div>
                <Grid headers={headers} rows={data} dispatch={dispatch} operationConfig={operationConfig}
                      rowClick={(rowData: ParameterItem) => {
                          this.currentData = rowData;
                      }}/>
            </div>
        );
    }
}

interface ParameterStateTree {
    data: ParameterItem[];
}

function select(state: ParameterStateTree, ownProps: { file: string }): Omit<ParameterEditorProps, 'dispatch'> {
    return {data: state.data || [], file: ownProps.file};
}

export default connect(select)(ParameterEditor);
