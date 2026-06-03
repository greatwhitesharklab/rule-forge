import {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

interface BatchTestDialogProps {}

interface BatchTestDialogState {
    visible: boolean;
    data: unknown;
    files: string | null;
    sessionId: string | null;
    progress: number;
    status: string;
    totalRows: number;
    errorCount: number;
    successCount: number;
    polling: boolean;
}

export default class BatchTestDialog extends Component<BatchTestDialogProps, BatchTestDialogState> {
    private _pollTimer: ReturnType<typeof setInterval> | null;

    constructor(props: BatchTestDialogProps) {
        super(props);
        this.state = {
            visible: false,
            data: null,
            files: null,
            sessionId: null,
            progress: 0,
            status: '',
            totalRows: 0,
            errorCount: 0,
            successCount: 0,
            polling: false
        };
        this._pollTimer = null;
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_BATCH_TEST_DIALOG, (config: { data?: unknown; files?: string; sessionId?: string }) => {
            this.setState({
                visible: true,
                data: config.data,
                files: config.files || null,
                sessionId: config.sessionId || (window as any)._importSessionId || null,
                progress: 0,
                status: '',
                totalRows: 0,
                errorCount: 0,
                successCount: 0,
                polling: false
            });
        });
        event.eventEmitter.on(event.HIDE_BATCH_TEST_DIALOG, () => {
            this.stopPolling();
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        this.stopPolling();
        event.eventEmitter.removeAllListeners(event.OPEN_BATCH_TEST_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_BATCH_TEST_DIALOG);
    }

    stopPolling() {
        if (this._pollTimer) {
            clearInterval(this._pollTimer);
            this._pollTimer = null;
        }
    }

    _buildExportPrefix(): string {
        const d = new Date();
        return '' + d.getFullYear() + (d.getMonth() + 1) + d.getDate()
            + d.getHours() + d.getMinutes() + d.getSeconds() + '_';
    }

    startBatchTest() {
        const {files, sessionId, data} = this.state;

        // V5.8.0: 外部已经 start 的情况(DatasourcePanel 调 FLOW+DATASOURCE 模式),
        // 直接进入轮询就行,不再调老 action.startBatchTest
        if ((data as any)?.skipStart === true && sessionId) {
            this.setState({status: 'RUNNING', polling: true});
            this.startPolling(sessionId);
            return;
        }

        if (!sessionId) {
            window.bootbox.alert('请先导入 Excel 数据');
            return;
        }

        const ce = window.parent.componentEvent;
        ce.eventEmitter.emit(ce.SHOW_LOADING);

        action.startBatchTest(sessionId, {files}, (result) => {
            ce.eventEmitter.emit(ce.HIDE_LOADING);
            if (result.sessionId) {
                this.setState({status: 'RUNNING', polling: true});
                this.startPolling(result.sessionId);
            }
        });
    }

    startPolling(sessionId: string) {
        this.stopPolling();
        this._pollTimer = setInterval(() => {
            action.getBatchTestProgress(sessionId, (progress) => {
                this.setState({
                    progress: Math.round((progress.progress || 0) * 100),
                    status: progress.status,
                    totalRows: progress.totalRows || 0,
                    errorCount: progress.errorCount || 0,
                    successCount: progress.successCount || 0,
                });

                if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
                    this.stopPolling();
                    this.setState({polling: false});
                    const ce = window.parent.componentEvent;
                    ce.eventEmitter.emit(ce.HIDE_LOADING);
                    if (progress.status === 'COMPLETED') {
                        window.bootbox.alert(
                            '批量测试完成！共 ' + progress.totalRows + ' 行，成功 ' +
                            (progress.successCount || 0) + ' 行，失败 ' + (progress.errorCount || 0) + ' 行'
                        );
                    } else {
                        window.bootbox.alert('批量测试执行失败');
                    }
                }
            });
        }, 1000);
    }

    render() {
        const {status, progress, totalRows, successCount, errorCount, polling, sessionId} = this.state;

        let body;
        if (polling || status === 'RUNNING') {
            body = (
                <div style={{padding: '15px'}}>
                    <h4>批量测试执行中...</h4>
                    <div className="progress" style={{marginBottom: '10px'}}>
                        <div className="progress-bar progress-bar-striped active"
                             role="progressbar" style={{width: progress + '%'}}>
                            {progress}%
                        </div>
                    </div>
                    <div className="row">
                        <div className="col-xs-4">总行数: {totalRows}</div>
                        <div className="col-xs-4 text-success">成功: {successCount}</div>
                        <div className="col-xs-4 text-danger">失败: {errorCount}</div>
                    </div>
                </div>
            );
        } else if (status === 'COMPLETED') {
            const exportFormId = 'batch-test-export-form';
            body = (
                <div style={{padding: '15px'}}>
                    <h4 className="text-success">批量测试完成</h4>
                    <div className="row" style={{marginBottom: '10px'}}>
                        <div className="col-xs-4">总行数: {totalRows}</div>
                        <div className="col-xs-4 text-success">成功: {successCount}</div>
                        <div className="col-xs-4 text-danger">失败: {errorCount}</div>
                    </div>
                    <form id={exportFormId} method="post"
                          action={window._server + '/packageeditor/exportBatchTestExcel'}>
                        <input name="prefix" type="hidden" value={this._buildExportPrefix()}/>
                        <input name="sessionId" type="hidden" value={sessionId || ''}/>
                    </form>
                    <button className="btn btn-success" onClick={() => (document.getElementById(exportFormId) as HTMLFormElement).submit()}>
                        <i className="glyphicon glyphicon-download"/> 导出测试结果
                    </button>
                </div>
            );
        } else {
            body = (
                <div style={{padding: '15px'}}>
                    <p>准备执行批量测试</p>
                    {sessionId ? (
                        <p>Session ID: {sessionId}</p>
                    ) : (
                        <p className="text-warning">未检测到导入数据，请先上传 Excel</p>
                    )}
                </div>
            );
        }

        const buttons = [
            {
                name: '开始测试',
                className: 'btn btn-danger',
                icon: 'glyphicon glyphicon-flash',
                click: () => this.startBatchTest(),
                disabled: !sessionId || polling
            }
        ];
        return (<CommonDialog visible={this.state.visible} large={true}
                               title='对导入的Excel数据进行批量测试' body={body} buttons={buttons}
                               onClose={() => this.setState({visible: false})}/>);
    }
}
