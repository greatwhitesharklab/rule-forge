import React, {Component} from 'react';
import {connect} from 'react-redux';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import Grid from '../../components/grid/component/Grid.tsx';
import * as action from '../action.js';
import * as event from '../event.js';
import type {ActionMethod} from '../action.js';
import type {ActionRootState} from '../reducer.js';

import {alert} from '@/utils/modal';
import {CloseOutlined, LikeOutlined} from '@ant-design/icons';
interface SelectMethodDialogProps {
    data: ActionMethod[];
    dispatch: Function;
}

interface SelectMethodDialogState {
    visible: boolean;
}

class SelectMethodDialog extends Component<SelectMethodDialogProps, SelectMethodDialogState> {
    constructor(props: SelectMethodDialogProps) {
        super(props);
        this.state = {visible: false};
    }
    componentDidMount() {
        event.eventEmitter.on(event.CLOSE_SELECT_METHOD_DIALOG, () => {
            this.setState({visible: false});
        });
        const {dispatch} = this.props;
        event.eventEmitter.on(event.OPEN_SELECT_METHOD_DIALOG, (beanId: string) => {
            this.setState({visible: true});
            dispatch(action.loadBeanMethods(beanId));
        });
    }
    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.CLOSE_SELECT_METHOD_DIALOG);
    }
    render() {
        const {data, dispatch} = this.props;
        const headers = [
            {id: 'method-methodName', name: 'methodName', label: '方法名称', filterable: true, width: '200px'},
            {id: 'method-name', name: 'name', label: '名称', filterable: true}
        ];
        const operationCol = {
            width: '80px',
            operations: [
                {
                    label: '选择此方法',
                    icon: <LikeOutlined />,
                    style: {fontSize: '18px', color: '#337ab7', padding: '0px 4px', cursor: 'pointer'},
                    click: (_rowIndex: number, rowData: ActionMethod) => {
                        dispatch(action.addSlave(rowData));
                        alert('添加成功.');
                    }
                }
            ]
        };
        const body = (
            <Grid rows={data} headers={headers} operationConfig={operationCol}/>
        );
        const buttons = [
            {
                name: '关闭',
                type: 'primary' as const,
                icon: <CloseOutlined />,
                click: function () {
                    event.eventEmitter.emit(event.CLOSE_SELECT_METHOD_DIALOG);
                }
            }
        ];
        return (
            <CommonDialog visible={this.state.visible} title="选择方法" body={body} buttons={buttons} onClose={() => this.setState({visible: false})}/>
        );
    }
}

function select(state: ActionRootState): { data: ActionMethod[] } {
    return {data: state.methodList.data || []};
}
export default connect(select)(SelectMethodDialog);
