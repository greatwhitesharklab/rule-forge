import React, {Component} from 'react';
import {connect} from 'react-redux';
import Grid from '../../components/grid/component/Grid.tsx';
import * as action from '../action.ts';

interface ClientConfigEditorProps {
    data: Array<{ name?: string; client?: string }>;
    dispatch: (a: unknown) => unknown;
    project: string | null;
}

class ClientConfigEditor extends Component<ClientConfigEditorProps> {
    render() {
        const {data, dispatch, project} = this.props;
        const headers = [
            {id: 'c-name', name: 'name', label: '客户端名称', filterable: true, editable: true, width: '220px'},
            {id: 'c-url', name: 'client', label: '客户端地址', filterable: true, editable: true}
        ];
        const operationConfig = {
            width: '100px',
            operations: [
                {
                    label: '删除',
                    icon: 'glyphicon glyphicon-trash',
                    style: {fontSize: '16px', color: '#d9534f', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex: number) {
                        window.bootbox.confirm('真的要删除当前记录？', function (result: boolean) {
                            if (!result) return;
                            dispatch(action.del(rowIndex));
                        });
                    }
                }
            ]
        };
        return (
            <div>
                <div style={{margin: '2px'}}>
                    <div className="btn-group" style={{margin: '2px'}}>
                        <button className="btn btn-primary" type="button" onClick={e => {
                            dispatch(action.add())
                        }}><i className="glyphicon glyphicon-plus-sign"/> 添加
                        </button>
                    </div>
                    <div className="btn-group" style={{margin: '2px'}}>
                        <button className="btn btn-danger" type="button" onClick={() => {
                            action.save(data, project);
                        }}><i className="glyphicon glyphicon-floppy-saved"/> 保存
                        </button>
                    </div>
                </div>
                <Grid headers={headers} rows={data} dispatch={dispatch} operationConfig={operationConfig}/>
            </div>
        );
    }
}

interface ClientConfigStateTree {
    data: Array<{ name?: string; client?: string }>;
}

function select(state: ClientConfigStateTree = {data: []}): Omit<ClientConfigEditorProps, 'dispatch' | 'project'> {
    return {data: state.data || []};
}

export default connect(select)(ClientConfigEditor);
