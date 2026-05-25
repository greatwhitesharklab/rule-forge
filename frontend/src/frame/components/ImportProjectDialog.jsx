import React, {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';

export default class ImportProjectDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {isImporting: false, visible: false};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_IMPORT_PROJECT_DIALOG, () => {
            this.setState({visible: true});
        });
        event.eventEmitter.on(event.CLOSE_IMPORT_PROJECT_DIALOG, () => {
            this.setState({visible: false});
        });

        //监听frame的 onload方法
        const $vm = this;
        const oFrm = document.getElementById("hiddenFrame");
        oFrm.onload = oFrm.onreadystatechange = function () {
            if (this.readyState && this.readyState !== "complete") {
            } else {
                // 获取iframe里面的内容
                event.eventEmitter.emit(event.CLOSE_IMPORT_PROJECT_DIALOG);
                const responseText = document.getElementById('hiddenFrame').contentDocument.body.textContent;
                $vm.setState({isImporting: false});
                // 上传完成后的处理
                if (responseText !== "") {
                    const responseData = JSON.parse(responseText);
                    if (responseData && responseData.status) {
                        window.bootbox.alert('导入成功');
                        return;
                    }
                }

                window.bootbox.alert('导入失败');
            }
        }
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_IMPORT_PROJECT_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_IMPORT_PROJECT_DIALOG);
    }

    render() {
        const formId = 'import_xml_form';
        const body = (
            <div>
                <iframe name='hiddenFrame' id="hiddenFrame" style={{display: 'none'}}></iframe>
                <form id={formId} method="post" encType="multipart/form-data" target='hiddenFrame'
                      action={window._server + '/frame/importProject'}>
                    {/*<div className="row" style={{marginBottom: '20px'}}>*/}
                    {/*    <div className="form-group">*/}
                    {/*        <div className="col-xs-4" style={{textAlign: 'right', padding: 0}}>*/}
                    {/*            <label>是否覆盖已存在项目：</label>*/}
                    {/*        </div>*/}
                    {/*        <div className="col-xs-8">*/}
                    {/*            是<input type="radio" name="overwriteProject" className="radio-inline"*/}
                    {/*                     defaultValue="true" defaultChecked style={{marginRight: '30px'}}/>*/}
                    {/*            否<input type="radio" name="overwriteProject" className="radio-inline"*/}
                    {/*                     defaultValue="false"/>*/}
                    {/*        </div>*/}
                    {/*    </div>*/}
                    {/*</div>*/}
                    <div className="row">
                        <div className="form-group">
                            <div className="col-xs-4" style={{textAlign: 'right', padding: 0}}>
                                <label>选择要导入的项目备份文件：</label>
                            </div>
                            <div className="col-xs-8">
                                <input name="file" style={{width: '100%'}} type="file"/>
                            </div>
                        </div>
                    </div>
                </form>
            </div>
        );

        const $vm = this;
        const buttons = [
            {
                name: '导入',
                className: 'btn btn-danger',
                icon: 'fa fa-upload',
                click: function () {
                    if ($vm.state.isImporting) {
                        window.bootbox.alert('正在导入中，请等待');
                        return;
                    }
                    var file = document.querySelector('[name=file]').value;
                    if (!file || file.length < 2) {
                        window.bootbox.alert('请选择要导入的文件');
                        return;
                    }
                    $vm.setState({isImporting: true});
                    document.getElementById(formId).submit();
                }
            }
        ];

        return (<CommonDialog visible={this.state.visible} title="导入项目" body={body} buttons={buttons}/>)
    }
}