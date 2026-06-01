import {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import {ImportErrorItem} from '../action.js';

interface ImportExcelErrorDialogProps {}

interface ImportExcelErrorDialogState {
    visible: boolean;
    data: ImportErrorItem[];
}

export default class ImportExcelErrorDialog extends Component<ImportExcelErrorDialogProps, ImportExcelErrorDialogState> {
    constructor(props: ImportExcelErrorDialogProps) {
        super(props);
        this.state = {visible: false, data: []};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_IMPORT_EXCEL_ERROR_DIALOG, (data: ImportErrorItem[]) => {
            console.log('失败结果列表', data)
            this.setState({visible: true, data});
        });
        event.eventEmitter.on(event.HIDE_IMPORT_EXCEL_ERROR_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_IMPORT_EXCEL_ERROR_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_IMPORT_EXCEL_ERROR_DIALOG);
    }

    render() {
        const body = (
            <div>
                {this.state.data.slice(0, 10).map((item, index) => {
                    return <div style={{color: 'red'}} key={index}>{index+1}：{item.sheetName} 第{item.sheetRowId+1}行，{item.sheetFieldName} {item.errorMsg}</div>
                })}
                {this.state.data.length > 10 && <div style={{color: 'red'}}>......共{this.state.data.length}条，仅显示前10条，其他请下载详情查看</div>}
                <form id="formId" method="post"
                    action={window._server + '/packageeditor/exportBatchTestExcel'}>
                    <input id="input-prefix" name="prefix" type="hidden"/>
                </form>
            </div>
        );
        const _this = this;
        const buttons = [
            {
                name: '下载',
                className: 'btn btn-primary',
                click: function () {
                    const datetime = new Date();
                    const filePrefix = '' + datetime.getFullYear() + (datetime.getMonth() + 1) + datetime.getDate()
                            + datetime.getHours() + datetime.getMinutes() + datetime.getSeconds() + '_';
                    // 下载excel
                    (document.getElementById("input-prefix") as HTMLInputElement).value = filePrefix;
                    (document.getElementById("formId") as HTMLFormElement).submit();
                }
            },
            {
                name: 'OK',
                className: 'btn btn-primary',
                click: function () {
                    _this.setState({visible: false});
                }
            }
        ];
        return (<CommonDialog visible={this.state.visible} title="导入Excel失败" body={body} buttons={buttons} onClose={() => this.setState({visible: false})}/>);

    }
}
