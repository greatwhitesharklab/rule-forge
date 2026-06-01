import React, { Component } from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import Grid from '../../components/grid/component/Grid.jsx';
import * as event from '../../components/componentEvent.js';
import { httpGet } from '../../api/client.js';

interface ConditionListState {
    visible: boolean;
    title: string;
    data: any[];
}

export default class ConditionListDialogComponent extends Component<{}, ConditionListState> {
    callback: ((condition: string) => void) | null = null;

    constructor(props: {}) {
        super(props);
        this.state = { visible: false, title: '常用条件列表', data: [] };
    }

    componentDidMount(): void {
        event.eventEmitter.on(event.OPEN_CONDITION_LIST_DIALOG, (config: any) => {
            this.callback = config.callback;
            this.setState({
                visible: true,
                title: '常用条件列表【' + (config.variable || '') + '】',
                data: config.data || []
            });
        });
        event.eventEmitter.on(event.CLOSE_CONDITION_LIST_DIALOG, () => {
            this.setState({ visible: false });
        });
        event.eventEmitter.on(event.REFRESH_CONDITION_LIST_DIALOG, (config: any) => {
            this._loadData(config);
        });
    }

    componentWillUnmount(): void {
        event.eventEmitter.removeAllListeners(event.OPEN_CONDITION_LIST_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_CONDITION_LIST_DIALOG);
        event.eventEmitter.removeAllListeners(event.REFRESH_CONDITION_LIST_DIALOG);
    }

    _loadData(config: any): void {
        httpGet('ruleforge?action=loadcommonconditions&project=' + config.project
            + '&category=' + config.category
            + '&variable=' + config.variable).then(function (data) {
            this.setState({ data: data || [] });
        }.bind(this)).catch(function () {
            RuleForge.alert('加载常用条件失败');
        });
    }

    handleClose(): void {
        this.setState({ visible: false });
    }

    render(): React.ReactNode {
        const headers = [
            { id: 'cl-name', name: 'name', label: '名称', width: '150px' },
            { id: 'cl-condition', name: 'condition', label: '条件' }
        ];

        const operationConfig = {
            width: '80px',
            operations: [
                {
                    label: '选择',
                    icon: 'rf rf-select',
                    style: { fontSize: '18px', color: '#337ab7', padding: '0px 4px', cursor: 'pointer' },
                    click: function (_rowIndex: number, rowData: any) {
                        if (this.callback) {
                            this.callback(rowData.condition);
                        }
                    }.bind(this)
                }
            ]
        };

        const body = (
            <Grid headers={headers} operationConfig={operationConfig} rows={this.state.data} />
        );
        const buttons = [
            {
                name: '关闭', className: 'btn btn-default',
                click: function () {
                    this.handleClose();
                }.bind(this)
            }
        ];
        return (
            <CommonDialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons} dialogStyle={{ width: '500px' }} onClose={() => this.setState({ visible: false })} />
        );
    }
}
