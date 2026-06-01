import {Component} from 'react';
import * as event from '../event.js';
import * as action from '../action.js';
import {
    FlowInfo,
    SimulatorCategory,
} from '../action.js';
import Grid from '../../components/grid/component/Grid.tsx';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';

interface FlowDialogProps {}

interface FlowDialogState {
    visible: boolean;
    flows: FlowInfo[] | null;
    project: string;
    packageId: string;
    files?: string;
    data?: SimulatorCategory[];
}

export default class FlowDialog extends Component<FlowDialogProps, FlowDialogState> {
    rowData: unknown;

    constructor(props: FlowDialogProps) {
        super(props);
        this.state = {visible: false, flows: null, project: '', packageId: ''};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_FLOW_DIALOG, (config: { project: string; packageId: string; files: string; data: SimulatorCategory[] }) => {
            this.setState({
                visible: true,
                project: config.project,
                packageId: config.packageId,
            });

            this.rowData = null;
            const files = config.files;
            const data = config.data;
            action.loadFlows(files, function (this: FlowDialog, result: FlowInfo[]) {
                this.setState({files, data, flows: result});
                const ce = window.parent.componentEvent;
                ce.eventEmitter.emit(ce.HIDE_LOADING);
            }.bind(this));
        });
        event.eventEmitter.on(event.HIDE_FLOW_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_FLOW_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_FLOW_DIALOG);
    }

    render() {
        const formId = 'export-test-excel-form';

        const headers = [
            {id: 'f-id', name: 'id', label: '决策流ID', filterable: true},
        ];
        const {files, data, project, packageId} = this.state;
        const gridOperationCol = {
            width: '70px',
            operations: [
                {
                    label: '测试',
                    icon: 'glyphicon glyphicon-flash',
                    style: {fontSize: '20px', color: '#d9534f', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex: number, rowData: FlowInfo) {
                        const ce = window.parent.componentEvent;
                        ce.eventEmitter.emit(ce.SHOW_LOADING);
                        const flowId = rowData.id;
                        action.testFlow({
                            'project': project,
                            'packageId': packageId,
                            'files': files,
                            'data': [data],
                            'flowId': flowId
                        }, function (result: Record<string, unknown>) {
                            event.eventEmitter.emit(event.REFRESH_SIMULATOR_DATA, result);
                            ce.eventEmitter.emit(ce.HIDE_LOADING);
                            window.bootbox.alert("决策流[" + flowId + "]执行完成，" + result.info);
                        });
                    }
                },
                {
                    label: '批量测试',
                    icon: 'glyphicon glyphicon-send',
                    style: {fontSize: '20px', color: '#d9534f', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex: number, rowData: FlowInfo) {
                        const flowId = rowData.id;
                        const sessionId = (window as any)._importSessionId;

                        if (sessionId) {
                            // 新流程：使用 sessionId 异步执行
                            const ce = window.parent.componentEvent;
                            ce.eventEmitter.emit(ce.SHOW_LOADING);
                            action.startBatchTest(sessionId, {
                                files: files,
                                flowId: flowId,
                                project: project,
                                packageId: packageId
                            }, function (result: { sessionId?: string }) {
                                ce.eventEmitter.emit(ce.HIDE_LOADING);
                                if (result.sessionId) {
                                    // 打开批量测试对话框显示进度
                                    event.eventEmitter.emit(event.OPEN_BATCH_TEST_DIALOG, {
                                        files: files,
                                        sessionId: result.sessionId
                                    });
                                }
                            });
                        } else {
                            // 旧流程兼容：无 sessionId 时走同步批量测试
                            const ce = window.parent.componentEvent;
                            ce.eventEmitter.emit(ce.SHOW_LOADING);
                            action.doBatchTest({
                                'project': project,
                                'packageId': packageId,
                                'files': files,
                                'flowId': flowId
                            }, function (testResult: Record<string, unknown>) {
                                ce.eventEmitter.emit(ce.HIDE_LOADING);
                                const errorList = testResult['errorList'];
                                window.bootbox.alert(JSON.stringify(errorList));

                                const datetime = new Date();
                                const filePrefix = '' + datetime.getFullYear() + (datetime.getMonth() + 1) + datetime.getDate()
                                    + datetime.getHours() + datetime.getMinutes() + datetime.getSeconds() + '_';
                                (document.getElementById("input-prefix") as HTMLInputElement).value = filePrefix;
                                (document.getElementById(formId) as HTMLFormElement).submit();
                            });
                        }
                    }
                }
            ]
        };
        let body = (<div></div>);
        if (this.state.flows) {
            body = (
                <div>
                    <Grid headers={headers} operationConfig={gridOperationCol} rows={this.state.flows}/>
                    <form id={formId} method="post"
                          action={window._server + '/packageeditor/exportBatchTestExcel'}>
                        <input id="input-prefix" name="prefix" type="hidden"/>
                    </form>
                </div>
            );
        }
        const buttons = [
            {
                name: '关闭',
                className: 'btn btn-primary',
                icon: 'fa fa-close',
                click: function () {
                    event.eventEmitter.emit(event.HIDE_FLOW_DIALOG);
                }
            }
        ];

        return (
            <CommonDialog visible={this.state.visible} title='测试决策流' body={body} buttons={buttons} onClose={() => this.setState({visible: false})}/>
        );
    }
}
