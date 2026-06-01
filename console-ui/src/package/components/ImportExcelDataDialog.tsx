import {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

interface ImportExcelDataDialogProps {}

interface ImportExcelDataDialogState {
    visible: boolean;
    files: string;
    sessionId: string | null;
}

export default class ImportExcelDataDialog extends Component<ImportExcelDataDialogProps, ImportExcelDataDialogState> {
    constructor(props: ImportExcelDataDialogProps) {
        super(props);
        this.state = {visible: false, files: '', sessionId: null};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_IMPORT_EXCEL_DIALOG, (files: string) => {
            this.setState({visible: true, files, sessionId: null});
        });
        event.eventEmitter.on(event.HIDE_IMPORT_EXCEL_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_IMPORT_EXCEL_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_IMPORT_EXCEL_DIALOG);
    }

    handleUpload() {
        const fileInput = document.getElementById("input-file") as HTMLInputElement;
        const file = fileInput.files![0];
        if (!file) {
            window.bootbox.alert('请选择文件');
            return;
        }

        action.importExcelData(this.state.files, file, (result) => {
            if (result.status) {
                window.bootbox.alert('导入Excel成功，共 ' + (result.totalRows || 0) + ' 行数据');
                this.setState({sessionId: result.sessionId || null});
                // 全局存储 sessionId，供批量测试使用
                (window as any)._importSessionId = result.sessionId;
                this.setState({visible: false});
            } else {
                if (result.data && result.data.length > 0) {
                    event.eventEmitter.emit(event.OPEN_IMPORT_EXCEL_ERROR_DIALOG, result.data);
                } else {
                    window.bootbox.alert(result.msg || '导入Excel失败');
                }
            }
        });
    }

    render() {
        const body = (
            <div>
                <input id="input-file" name="file" type="file" accept=".xlsx,.xls" style={{width: '100%'}}/>
                {this.state.sessionId && (
                    <div style={{marginTop: '8px', color: '#5cb85c'}}>
                        <i className="glyphicon glyphicon-ok"/> 已导入，Session ID: {this.state.sessionId}
                    </div>
                )}
            </div>
        );
        const buttons = [
            {
                name: '上传',
                className: 'btn btn-primary',
                icon: 'glyphicon glyphicon-cloud-upload',
                click: () => this.handleUpload()
            }
        ];
        return (<CommonDialog visible={this.state.visible} title="导入Excel" body={body} buttons={buttons}
                               onClose={() => this.setState({visible: false})}/>);
    }
}
