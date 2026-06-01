import {Component} from 'react';
import Dialog from '../../components/dialog/component/Dialog.jsx';
import * as componentEvent from '../../components/componentEvent.js';
import * as event from '../event.js';
import * as action from '../action.js';

const NAME_REGEXP = /^(?!_)(?!-)[一-龥_a-zA-Z0-9_-]{1,}$/;

interface UpdateProjectDialogProps {
    dispatch: (action: unknown) => void;
}

interface UpdateProjectDialogState {
    data: TreeNodeData;
    visible: boolean;
    projectName: string;
    errors: { projectName?: string };
}

const EMPTY_DATA: TreeNodeData = {id: '', name: '', type: '', fullPath: ''};

export default class UpdateProjectDialog extends Component<UpdateProjectDialogProps, UpdateProjectDialogState> {
    constructor(props: UpdateProjectDialogProps) {
        super(props);
        this.state = {data: EMPTY_DATA, visible: false, projectName: '', errors: {}};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_UPDATE_PROJECT_DIALOG, (data: TreeNodeData) => {
            this.setState({data, projectName: data.name, errors: {}});
            this.setState({visible: true});
        });
        event.eventEmitter.on(event.CLOSE_UPDATE_PROJECT_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_UPDATE_PROJECT_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_UPDATE_PROJECT_DIALOG);
    }

    _validate(): { valid: boolean; errors: { projectName?: string } } {
        const value = this.state.projectName;
        const errors: { projectName?: string } = {};
        if (!value || !value.trim()) {
            errors.projectName = '项目名称不能为空';
        } else if (!NAME_REGEXP.test(value)) {
            errors.projectName = '名称只能包含中文及英文字母、数字、下划线、中划线,且不能以下划线、中划线开头';
        }
        return {valid: Object.keys(errors).length === 0, errors};
    }

    render() {
        const {dispatch} = this.props;
        const body = (
            <div className="form-group">
                <label>项目名称</label>
                <input type="text" className="form-control" name="projectName" value={this.state.projectName}
                       onChange={function (e: React.ChangeEvent<HTMLInputElement>) { this.setState({projectName: e.target.value, errors: {}}) }.bind(this)}></input>
                {this.state.errors.projectName && <div className="text-danger" style={{fontSize: '12px'}}>{this.state.errors.projectName}</div>}
            </div>
        );
        const buttons = [];
        buttons.push(
            {
                name: '保存',
                className: 'btn btn-success',
                icon: 'fa fa-floppy-o',
                click: function () {
                    const {valid, errors} = this._validate();
                    if (!valid) {
                        this.setState({errors});
                        return;
                    }
                    const data = this.state.data;
                    const newProjectName = this.state.projectName;
                    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                    setTimeout(function () {
                        dispatch(action.fileRename(data, newProjectName));
                    }, 200);
                }.bind(this)
            }
        );
        return (
            <Dialog visible={this.state.visible} title="项目名称修改" body={body} buttons={buttons}
                    onClose={() => this.setState({visible: false})}></Dialog>
        );
    }
}
