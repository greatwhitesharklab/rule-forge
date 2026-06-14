import React, {Component} from 'react';
import {apiBase} from '@/api/client';
import Dialog from '../../components/dialog/component/Dialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

import {alert} from '@/utils/modal';
import {CloudUploadOutlined} from '@ant-design/icons';
interface ImportXmlDialogProps {
    dispatch: Function;
}

interface ImportXmlDialogState {
    visible: boolean;
    rowIndex: number;
}

export default class ImportXmlDialog extends Component<ImportXmlDialogProps, ImportXmlDialogState> {
    constructor(props: ImportXmlDialogProps) {
        super(props);
        this.state = {visible: false, rowIndex: -1};
    }
    componentDidMount() {
        event.eventEmitter.on(event.OPEN_IMPORT_XML_DIALOG, (rowIndex: number) => {
            this.setState({visible: true, rowIndex});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_IMPORT_XML_DIALOG);
    }

    render() {
        const dispatch = this.props.dispatch;
        const formId = 'import_xml_form';
        const $vm = this;
        const body = (
            <div>
                <form id={formId}>
                    <input name="file" style={{width: '100%'}} type="file"/>
                </form>
            </div>
        );
        const buttons = [
            {
                name: '上传',
                className: 'btn btn-danger',
                icon: <CloudUploadOutlined />,
                click: function () {
                    const fileInput = document.querySelector('#' + formId + ' [name=file]') as HTMLInputElement;
                    const file = fileInput && fileInput.files && fileInput.files[0];
                    if (!file) {
                        alert('请选择要上传的文件');
                        return;
                    }
                    const formData = new FormData();
                    formData.append('file', file);
                    fetch(apiBase() + '/variableeditor/importXml', {
                        method: 'POST',
                        body: formData,
                    })
                        .then(function (response: Response) {
                            return response.text();
                        })
                        .then(function (content: string) {
                            if (!content || content === '') {
                                return;
                            }
                            try {
                                const jsonResult = JSON.parse(content);
                                dispatch(action.importFields($vm.state.rowIndex, jsonResult));
                                $vm.setState({visible: false});
                            } catch (error) {
                                alert('上传文件不合法');
                            }
                        })
                        .catch(function () {
                            alert('上传文件不合法');
                        });
                }
            }
        ];
        return (
            <Dialog title="导入对象属性XML文件" body={body} buttons={buttons} visible={this.state.visible}/>
        );
    }
}
