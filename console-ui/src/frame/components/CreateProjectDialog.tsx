import {Component} from 'react';
import {Input} from 'antd';
import Dialog from '../../components/dialog/component/Dialog.jsx';
import * as componentEvent from '../../components/componentEvent.js';
import * as event from '../event.js';
import * as action from '../action.js';
import {formPost} from '../../api/client.js';
import {SaveOutlined} from '@ant-design/icons';

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
        this._save = this._save.bind(this);
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

    // 保存(按钮点击 / 输入框回车共用,校验走同一套 _validate)
    async _save() {
        const {valid, errors} = await this._validate();
        if (!valid) {
            this.setState({errors});
            return;
        }
        componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
        const newProjectName = this.state.newProjectName;
        const {dispatch} = this.props;
        setTimeout(function () {
            dispatch(action.createNewProject(newProjectName));
        }, 200);
    }

    render() {
        const body = (
            <div className="ff-group">
                <label>新项目名称</label>
                <Input name="newProjectName" value={this.state.newProjectName} autoFocus placeholder='请输入新项目名称'
                       onPressEnter={this._save}
                       onChange={function (e: React.ChangeEvent<HTMLInputElement>) { this.setState({newProjectName: e.target.value, errors: {}}) }.bind(this)}/>
                {this.state.errors.newProjectName && <div  style={{fontSize: '12px', color: 'var(--rf-danger)'}}>{this.state.errors.newProjectName}</div>}
            </div>
        );
        const buttons = [
            {
                name: '取消',
                type: 'default' as const,
                click: () => this.setState({visible: false}),
            },
            {
                name: '保存',
                type: 'primary' as const,
                icon: <SaveOutlined />,
                click: this._save,
            }
        ];
        return (
            <Dialog visible={this.state.visible} title="创建新项目" body={body} buttons={buttons}
                    onClose={() => this.setState({visible: false})}></Dialog>
        );
    }
}
