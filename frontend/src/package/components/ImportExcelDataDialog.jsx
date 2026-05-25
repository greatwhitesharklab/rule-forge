import React, {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';

export default class ImportExcelDataDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {visible: false, files: ''};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_IMPORT_EXCEL_DIALOG, (files) => {
            this.setState({visible: true, files});
        });
        event.eventEmitter.on(event.HIDE_IMPORT_EXCEL_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_IMPORT_EXCEL_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_IMPORT_EXCEL_DIALOG);
    }

    render() {
        const iframeName = 'upload_excel_iframe';
        const formId = 'upload_excel_form';
        const body = (
            <div>
                <form id={formId} method="post" encType="multipart/form-data" target={iframeName}
                      action={window._server + '/packageeditor/importExcelTemplate'}>
                    <input id="input-file" name="file" type="file" style={{width: '100%'}}/>
                    <input type="hidden" name="targetFiles" value={this.state.files}/>
                </form>
                <iframe name={iframeName} height="0px" width="0px" frameBorder="0" onLoad={(e) => {
                    console.log('导入complete',e)
                    try {
                        let jsonData = JSON.parse(e.target.contentDocument.body.textContent);
                        if(jsonData.status) {
                            window.bootbox.alert('导入Excel成功');
                            this.setState({visible: false});
                        } else {
                            event.eventEmitter.emit(event.OPEN_IMPORT_EXCEL_ERROR_DIALOG, jsonData.data);
                        }
                    } catch (error) {
                        window.bootbox.alert('导入Excel失败');
                    }
                    
                }}/>
            </div>
        );
        const buttons = [
            {
                name: '上传',
                className: 'btn btn-primary',
                icon: 'glyphicon glyphicon-cloud-upload',
                click: function () {
                    document.getElementById(formId).submit();
                    document.getElementById("input-file").value = null;
                }
            }
        ];
        return (<CommonDialog visible={this.state.visible} title="导入Excel" body={body} buttons={buttons}/>);
    }
}