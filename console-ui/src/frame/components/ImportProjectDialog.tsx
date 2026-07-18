import {Component} from 'react';
import {apiBase} from '@/api/client';
import {UploadOutlined} from '@ant-design/icons';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';

import {alert} from '@/utils/modal';
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
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_IMPORT_PROJECT_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_IMPORT_PROJECT_DIALOG);
    }

    render() {
        const formId = 'import_xml_form';
        const body = (
            <div>
                <form id={formId}>
                    <div className="ff-row">
                        <div className="ff-group">
                            <div className="ff-col-4" style={{textAlign: 'right', padding: 0}}>
                                <label>选择要导入的项目备份文件：</label>
                            </div>
                            <div className="ff-col-8">
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
                danger: true,
                icon: <UploadOutlined />,
                click: function () {
                    if ($vm.state.isImporting) {
                        alert('正在导入中，请等待');
                        return;
                    }
                    const fileInput = document.querySelector('[name=file]') as HTMLInputElement;
                    const file = fileInput && fileInput.files && fileInput.files[0];
                    if (!file) {
                        alert('请选择要导入的文件');
                        return;
                    }
                    $vm.setState({isImporting: true});
                    const formData = new FormData();
                    formData.append('file', file);
                    fetch(apiBase() + '/frame/importProject', {
                        method: 'POST',
                        body: formData,
                    })
                        .then(function (response: Response) {
                            return response.text().then(function (responseText: string) {
                                return {responseText, ok: response.ok};
                            });
                        })
                        .then(function (result: { responseText: string; ok: boolean }) {
                            event.eventEmitter.emit(event.CLOSE_IMPORT_PROJECT_DIALOG);
                            $vm.setState({isImporting: false});
                            if (result.responseText !== "") {
                                try {
                                    const responseData = JSON.parse(result.responseText) as ImportResponse;
                                    if (responseData && responseData.status) {
                                        alert('导入成功');
                                        return;
                                    }
                                } catch (e) {
                                    // fall through to failure
                                }
                            }
                            alert('导入失败');
                        })
                        .catch(function () {
                            event.eventEmitter.emit(event.CLOSE_IMPORT_PROJECT_DIALOG);
                            $vm.setState({isImporting: false});
                            alert('导入失败');
                        });
                }
            }
        ];

        return (<CommonDialog visible={this.state.visible} title="导入项目" body={body} buttons={buttons}
                              onClose={() => this.setState({visible: false})}/>)
    }
}
