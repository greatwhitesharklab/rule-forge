import React, {Component} from 'react';
import * as event from '../event.js';
import * as action from '../action.js';
import Grid from '../../components/grid/component/Grid.jsx';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';

export default class FlowDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {visible: false, flows: null, project: '', packageId: ''};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_FLOW_DIALOG, (config) => {
            this.setState({
                visible: true,
                project: config.project,
                packageId: config.packageId,
            });

            this.rowData = null;
            var files = config.files;
            var data = config.data;
            action.loadFlows(files, function (result) {
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
                    click: function (rowIndex, rowData) {
                        const ce = window.parent.componentEvent;
                        ce.eventEmitter.emit(ce.SHOW_LOADING);
                        var flowId = rowData.id;
                        action.testFlow({
                            'project': project,
                            'packageId': packageId,
                            'files': files,
                            'data': [data],
                            'flowId': flowId
                        }, function (result) {
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
                    click: function (rowIndex, rowData) {
                        const ce = window.parent.componentEvent;
                        ce.eventEmitter.emit(ce.SHOW_LOADING);
                        const flowId = rowData.id;
                        action.doBatchTest({
                            'project': project,
                            'packageId': packageId,
                            'files': files,
                            'flowId': flowId
                        }, function (testResult) {
                            ce.eventEmitter.emit(ce.HIDE_LOADING);
                            const errorList = testResult['errorList'];
                            window.bootbox.alert(JSON.stringify(errorList));

                            const datetime = new Date();
                            const filePrefix = '' + datetime.getFullYear() + (datetime.getMonth() + 1) + datetime.getDate()
                                + datetime.getHours() + datetime.getMinutes() + datetime.getSeconds() + '_';
                            document.getElementById("input-prefix").value = filePrefix;
                            document.getElementById(formId).submit();
                        });
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
            <CommonDialog visible={this.state.visible} title='测试决策流' body={body} buttons={buttons}/>
        );
    }
}
