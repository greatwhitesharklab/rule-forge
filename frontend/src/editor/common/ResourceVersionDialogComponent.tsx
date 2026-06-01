import React, { Component } from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import Grid from '../../components/grid/component/Grid.jsx';
import * as event from '../../components/componentEvent.js';

interface ResourceVersionState {
    visible: boolean;
    title: string;
    data: any[];
    selectedIndex: number;
}

export default class ResourceVersionDialogComponent extends Component<{}, ResourceVersionState> {
    callback: ((file: string) => void) | null = null;
    path: string = '';

    constructor(props: {}) {
        super(props);
        this.state = { visible: false, title: '', data: [], selectedIndex: -1 };
    }

    componentDidMount(): void {
        event.eventEmitter.on(event.OPEN_RESOURCE_VERSION_DIALOG, (config: any) => {
            this.callback = config.callback;
            this.path = config.path;
            this.setState({
                visible: true,
                title: '版本列表【' + config.path + '】',
                data: config.data || [],
                selectedIndex: -1
            });
        });
        event.eventEmitter.on(event.CLOSE_RESOURCE_VERSION_DIALOG, () => {
            this.setState({ visible: false });
        });
    }

    componentWillUnmount(): void {
        event.eventEmitter.removeAllListeners(event.OPEN_RESOURCE_VERSION_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_RESOURCE_VERSION_DIALOG);
    }

    handleConfirm(): void {
        if (this.state.selectedIndex < 0 || !this.state.data[this.state.selectedIndex]) {
            RuleForge.alert('请选择一个版本！');
            return;
        }
        const selectedRow = this.state.data[this.state.selectedIndex];
        this.callback!(this.path + ':' + selectedRow.name);
        this.setState({ visible: false });
    }

    handleCancel(): void {
        this.setState({ visible: false });
    }

    handleRowClick(rowIndex: number): void {
        this.setState({ selectedIndex: rowIndex });
    }

    render(): React.ReactNode {
        const headers = [
            { id: 'rv-name', name: 'name', label: '版本号', width: '120px' },
            { id: 'rv-createUser', name: 'createUser', label: '创建人', width: '120px' },
            { id: 'rv-createDate', name: 'createDate', label: '创建时间', width: '180px', dateFormat: 'yyyy-MM-dd HH:mm:ss' }
        ];

        const operationConfig = {
            width: '80px',
            operations: [
                {
                    label: '选择',
                    icon: 'rf rf-select',
                    style: { fontSize: '18px', color: '#337ab7', padding: '0px 4px', cursor: 'pointer' },
                    click: function (_rowIndex: number, rowData: any) {
                        this.callback!(this.path + ':' + rowData.name);
                        this.setState({ visible: false });
                    }.bind(this)
                }
            ]
        };

        const body = (
            <Grid headers={headers} operationConfig={operationConfig} rows={this.state.data} />
        );
        const buttons = [
            {
                name: '确认', className: 'btn btn-primary',
                click: function () {
                    this.handleConfirm();
                }.bind(this)
            },
            {
                name: '取消', className: 'btn btn-default',
                click: function () {
                    this.handleCancel();
                }.bind(this)
            }
        ];
        return (
            <CommonDialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons} dialogStyle={{ width: '700px' }} onClose={() => this.setState({ visible: false })} />
        );
    }
}
