import {Component} from 'react';
import Dialog from '../../components/dialog/component/Dialog.jsx';
import * as componentEvent from '../../components/componentEvent.js';
import * as event from '../event.js';
import * as action from '../action.js';
import {formPost} from '../../api/client.js';

const NAME_REGEXP = /^(?!_)(?!-)[一-龥_a-zA-Z0-9_-]{1,}$/;

interface CreateFileDialogProps {
    dispatch: (action: unknown) => void;
}

interface CreateFileDialogState {
    title: string;
    fileType: string;
    type?: string;
    visible: boolean;
    newFileName: string;
    nodeData?: TreeNodeData;
    errors: { newFileName?: string };
}

export default class CreateFileDialog extends Component<CreateFileDialogProps, CreateFileDialogState> {
    constructor(props: CreateFileDialogProps) {
        super(props);
        this.state = {title: '', fileType: '', visible: false, newFileName: '', errors: {}};
        this._validate = this._validate.bind(this);
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_CREATE_FILE_DIALOG, (data: { fileType: string; nodeData: TreeNodeData }) => {
            const fileType = data.fileType;
            const type = action.buildType(fileType);
            let title = "创建一个";
            title += type + '文件';
            this.setState({title, type, fileType, nodeData: data.nodeData, newFileName: '', errors: {}});
            this.setState({visible: true});
        });
        event.eventEmitter.on(event.CLOSE_CREATE_FILE_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_CREATE_FILE_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_CREATE_FILE_DIALOG);
    }

    async _validate(): Promise<{ valid: boolean; errors: { newFileName?: string } }> {
        const value = this.state.newFileName;
        const errors: { newFileName?: string } = {};
        if (!value || !value.trim()) {
            errors.newFileName = '文件名不能为空';
        } else if (!NAME_REGEXP.test(value)) {
            errors.newFileName = '名称只能包含中文及英文字母、数字、下划线、中划线,且不能以下划线、中划线开头';
        } else {
            const fullFileName = this.state.nodeData!.fullPath + '/' + value + '.' + this.state.fileType;
            const result = await formPost<boolean | { valid?: boolean }>('/frame/fileExistCheck', {fullFileName});
            if (result === false || (typeof result === 'object' && result.valid === false)) {
                errors.newFileName = '文件名已存在';
            }
        }
        return {valid: Object.keys(errors).length === 0, errors};
    }

    render() {
        const {dispatch} = this.props;
        const body = (
            <div className="form-group">
                <label>文件名称</label>
                <input className="form-control" name="newFileName" value={this.state.newFileName}
                       onChange={function (e: React.ChangeEvent<HTMLInputElement>) { this.setState({newFileName: e.target.value, errors: {}}) }.bind(this)}/>
                {this.state.errors.newFileName && <div className="text-danger" style={{fontSize: '12px'}}>{this.state.errors.newFileName}</div>}
            </div>
        );
        const buttons = [
            {
                name: '保存',
                className: 'btn btn-success',
                icon: 'fa fa-floppy-o',
                click: async function () {
                    const {valid, errors} = await this._validate();
                    if (!valid) {
                        this.setState({errors});
                        return;
                    }
                    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                    const {fileType, nodeData} = this.state;
                    const newFileName = this.state.newFileName;
                    setTimeout(function () {
                        dispatch(action.createNewFile(newFileName, fileType, nodeData!));
                    }, 200);
                }.bind(this)
            }
        ];
        return (
            <Dialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons}
                    onClose={() => this.setState({visible: false})}/>
        );
    }
}
