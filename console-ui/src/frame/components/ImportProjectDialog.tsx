import {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';

interface ImportProjectDialogProps {
    dispatch?: (action: unknown) => void;
}

interface ImportProjectDialogState {
    isImporting: boolean;
    visible: boolean;
}

interface ImportResponse {
    status?: boolean;
}

export default class ImportProjectDialog extends Component<ImportProjectDialogProps, ImportProjectDialogState> {
    constructor(props: ImportProjectDialogProps) {
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

        const $vm = this;
        const oFrm = document.getElementById("hiddenFrame") as HTMLIFrameElement & { readyState?: string; onreadystatechange?: ((this: any) => void) | null };
        oFrm.onload = oFrm.onreadystatechange = function () {
            if (oFrm.readyState && oFrm.readyState !== "complete") {
                // still loading
            } else {
                event.eventEmitter.emit(event.CLOSE_IMPORT_PROJECT_DIALOG);
                const frame = document.getElementById('hiddenFrame') as HTMLIFrameElement;
                const responseText = frame.contentDocument!.body.textContent || '';
                $vm.setState({isImporting: false});
                if (responseText !== "") {
                    const responseData = JSON.parse(responseText) as ImportResponse;
                    if (responseData && responseData.status) {
                        window.bootbox.alert('导入成功');
                        return;
                    }
                }

                window.bootbox.alert('导入失败');
            }
        };
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
                    const fileInput = document.querySelector('[name=file]') as HTMLInputElement;
                    const file = fileInput ? fileInput.value : '';
                    if (!file || file.length < 2) {
                        window.bootbox.alert('请选择要导入的文件');
                        return;
                    }
                    $vm.setState({isImporting: true});
                    (document.getElementById(formId) as HTMLFormElement).submit();
                }
            }
        ];

        return (<CommonDialog visible={this.state.visible} title="导入项目" body={body} buttons={buttons}
                              onClose={() => this.setState({visible: false})}/>)
    }
}
