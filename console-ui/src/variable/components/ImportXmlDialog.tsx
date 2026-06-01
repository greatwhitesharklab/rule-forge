import React, {Component} from 'react';
import Dialog from '../../components/dialog/component/Dialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

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
        const iFrameName = 'upload_iframe';
        const formId = 'import_xml_form';
        const body = (
            <div>
                <form id={formId} method="post" encType="multipart/form-data" target={iFrameName}
                      action={window._server + '/variableeditor/importXml'}>
                    <input name="file" style={{width: '100%'}} type="file"/>
                </form>
                <iframe name={iFrameName} height="0px" width="0px" frameBorder="0" onLoad={(e: React.SyntheticEvent<HTMLIFrameElement>) => {
                    try {
                        const content = (e.target as HTMLIFrameElement).contentDocument?.body?.textContent;
                        if (!content || content === '') {
                            return;
                        }
                        let jsonResult = JSON.parse(content);
                        dispatch(action.importFields(this.state.rowIndex, jsonResult));
                        this.setState({visible: false});
                    } catch (error) {
                        window.bootbox.alert('上传文件不合法');
                    }
                }}/>
            </div>
        );
        const buttons = [
            {
                name: '上传',
                className: 'btn btn-danger',
                icon: 'glyphicon glyphicon-cloud-upload',
                click: function () {
                    (document.getElementById(formId) as HTMLFormElement).submit();
                }
            }
        ];
        return (
            <Dialog title="导入对象属性XML文件" body={body} buttons={buttons} visible={this.state.visible}/>
        );
    }
}
