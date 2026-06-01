import {Component} from 'react';
import * as event from '../event.js';
import * as action from '../action.js';
import * as componentEvent from '../../components/componentEvent.js';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';

const NAME_REGEXP = /^(?!_)(?!-)[一-龥_a-zA-Z0-9_-]{1,}$/;

interface RenameDialogProps {
    dispatch: (action: unknown) => void;
}

interface RenameDialogState {
    name: string;
    fileName: string;
    extName: string;
    parentPath: string;
    fullPath: string;
    visible: boolean;
    errors: { fileName?: string };
}

export default class RenameDialog extends Component<RenameDialogProps, RenameDialogState> {
    constructor(props: RenameDialogProps) {
        super(props);
        this.state = {name: '', fileName: '', extName: '', parentPath: '', fullPath: '', visible: false, errors: {}};
        this._validate = this._validate.bind(this);
    }

    componentDidMount() {
        event.eventEmitter.on(event.SHOW_RENAME_DIALOG, (data: TreeNodeData) => {
            const fullPath: string = data.fullPath, name: string = data.name, pos = fullPath.lastIndexOf('/');
            const parentPath = fullPath.substring(0, pos);
            const pointPOS = name.indexOf(".");
            const fileName = (pointPOS === -1 ? name : name.substring(0, pointPOS));
            const extName = (pointPOS === -1 ? '' : name.substring(pointPOS, name.length));
            this.setState({name, fileName, extName, parentPath, fullPath, visible: true, errors: {}});
        });
        event.eventEmitter.on(event.HIDE_RENAME_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    async _validate(): Promise<{ valid: boolean; errors: { fileName?: string } }> {
        const value = this.state.fileName;
        const errors: { fileName?: string } = {};
        if (!value || !value.trim()) {
            errors.fileName = '文件名不能为空';
        } else if (!NAME_REGEXP.test(value)) {
            errors.fileName = '名称只能包含中文及英文字母、数字、下划线、中划线,且不能以下划线、中划线开头';
        } else {
            const fullFileName = this.state.parentPath + '/' + value;
            const resp = await fetch(window._server + '/frame/fileExistCheck', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'fullFileName=' + encodeURIComponent(fullFileName)
            });
            const result = await resp.json();
            if (result === false || (typeof result === 'object' && result.valid === false)) {
                errors.fileName = '文件名已存在';
            }
        }
        return {valid: Object.keys(errors).length === 0, errors};
    }

    render() {
        const {dispatch} = this.props;
        const buttons = [{
            name: '确定',
            className: 'btn btn-success',
            icon: 'rf rf-save',
            click: async function () {
                const {valid, errors} = await this._validate();
                if (!valid) {
                    this.setState({errors});
                    return;
                }
                const {parentPath, fullPath} = this.state;
                const newName = parentPath + '/' + this.state.fileName + this.state.extName;
                componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                setTimeout(function () {
                    dispatch(action.rename(fullPath, newName));
                }, 100);
            }.bind(this)
        }];
        const body = (
            <div className="form-group">
                <label>名称</label>
                <input type="text" className="form-control" name="newFileNameForRename" value={this.state.fileName}
                       onChange={function (e: React.ChangeEvent<HTMLInputElement>) {
                           this.setState({fileName: e.target.value, errors: {}});
                       }.bind(this)}></input>
                {this.state.errors.fileName && <div className="text-danger" style={{fontSize: '12px'}}>{this.state.errors.fileName}</div>}
            </div>
        );
        return (<CommonDialog visible={this.state.visible} body={body} buttons={buttons} title='重命名'
                              onClose={() => this.setState({visible: false})}/>);
    }
}
