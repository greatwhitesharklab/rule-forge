import {Component} from 'react';
import Dialog from '../../components/dialog/component/Dialog.jsx';
import * as componentEvent from '../../components/componentEvent.js';
import * as event from '../event.js';
import * as action from '../action.js';
import {formPost} from '../../api/client.js';

const NAME_REGEXP = /^(?!_)(?!-)[一-龥_a-zA-Z0-9_-]{1,}$/;

interface CreateProjectDialogProps {
    dispatch: (action: unknown) => void;
}

interface CreateProjectDialogState {
    visible: boolean;
    newProjectName: string;
    nodeData?: TreeNodeData;
    errors: { newProjectName?: string };
}

export default class CreateProjectDialog extends Component<CreateProjectDialogProps, CreateProjectDialogState> {
    constructor(props: CreateProjectDialogProps) {
        super(props);
        this.state = {visible: false, newProjectName: '', errors: {}};
        this._validate = this._validate.bind(this);
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_NEW_PROJECT_DIALOG, (nodeData: TreeNodeData) => {
            this.setState({nodeData, visible: true, newProjectName: '', errors: {}});
        });
        event.eventEmitter.on(event.CLOSE_NEW_PROJECT_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_NEW_PROJECT_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_NEW_PROJECT_DIALOG);
    }

    async _validate(): Promise<{ valid: boolean; errors: { newProjectName?: string } }> {
        const value = this.state.newProjectName;
        const errors: { newProjectName?: string } = {};
        if (!value || !value.trim()) {
            errors.newProjectName = '项目名不能为空';
        } else if (!NAME_REGEXP.test(value)) {
            errors.newProjectName = '名称只能包含中文及英文字母、数字、下划线、中划线,且不能以下划线、中划线开头';
        } else {
            const result = await formPost<boolean | { valid?: boolean }>('/frame/projectExistCheck', {newProjectName: value});
            if (result === false || (typeof result === 'object' && result.valid === false)) {
                errors.newProjectName = '项目已存在';
            }
        }
        return {valid: Object.keys(errors).length === 0, errors};
    }

    render() {
        const {dispatch} = this.props;
        const body = (
            <div className="form-group">
                <label>新项目名称</label>
                <input type="text" className="form-control" name="newProjectName" value={this.state.newProjectName}
                       onChange={function (e: React.ChangeEvent<HTMLInputElement>) { this.setState({newProjectName: e.target.value, errors: {}}) }.bind(this)}></input>
                {this.state.errors.newProjectName && <div className="text-danger" style={{fontSize: '12px'}}>{this.state.errors.newProjectName}</div>}
            </div>
        );
        const buttons = [];
        buttons.push(
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
                    const newProjectName = this.state.newProjectName;
                    setTimeout(function () {
                        dispatch(action.createNewProject(newProjectName));
                    }.bind(this), 200);
                }.bind(this)
            }
        );
        return (
            <Dialog visible={this.state.visible} title="创建新项目" body={body} buttons={buttons}
                    onClose={() => this.setState({visible: false})}></Dialog>
        );
    }
}
