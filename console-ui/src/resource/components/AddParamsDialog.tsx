import React, {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';
import type {ResourceCategory} from '../action.js';

interface AddParamsDialogProps {
    dispatch: (a: unknown) => unknown;
    data?: ResourceCategory;
    file: string;
}

interface AddParamsDialogState {
    title: string;
    visible: boolean;
    name: string;
    label: string;
    defaultValue: string;
    type: string;
    logicComment: string;
    categoryLabel: string;
    dsStatus: number;
    errors: Record<string, string | undefined>;
    create?: boolean;
    rowIndex?: number;
    masterRowData?: ResourceCategory;
}

interface ValidationResult {
    valid: boolean;
    errors: Record<string, string | undefined>;
}

interface DialogEventData {
    create: boolean;
    title: string;
    rowIndex: number;
    data: ResourceCategory;
}

export default class AddParamsDialog extends Component<AddParamsDialogProps, AddParamsDialogState> {
    constructor(props: AddParamsDialogProps) {
        super(props);
        this.state = {
            title: '',
            visible: false,
            name: '',
            label: '',
            defaultValue: '',
            type: 'String',
            logicComment: '',
            categoryLabel: '',
            dsStatus: 0,
            errors: {}
        };
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_CREATE_PARAMS_DIALOG, (data: DialogEventData) => {
            this.setState({visible: true, errors: {}});
            const create = data.create;
            const title = data.title;
            const rowIndex = data.rowIndex;
            const masterRowData = data.data;
            if (create) {
                this.setState({
                    create,
                    title,
                    rowIndex,
                    masterRowData,
                    name: '',
                    label: '',
                    defaultValue: '',
                    type: 'String',
                    logicComment: '',
                    categoryLabel: '',
                    dsStatus: 0
                });
            }
            this.setState({create, title, rowIndex, masterRowData});
        });
        event.eventEmitter.on(event.HIDE_CREATE_PARAMS_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_CREATE_PARAMS_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_CREATE_PARAMS_DIALOG);
    }

    _validate(): ValidationResult {
        const errors: Record<string, string | undefined> = {};
        const {name, label, defaultValue, type, create} = this.state;
        if (create) {
            if (!name || !name.trim()) {
                errors.name = '字段名称不能为空';
            } else if (!/^[A-Za-z]+$/.test(name)) {
                errors.name = '字段名称只能英文输入';
            }
        }
        if (!label || !label.trim()) {
            errors.label = '标题不能为空';
        } else if (!/^[一-龥]+$/.test(label)) {
            errors.label = '标题必须使用中文';
        }
        if (!defaultValue || !defaultValue.trim()) {
            errors.defaultValue = '默认值不能为空';
        }
        if (!type || !type.trim()) {
            errors.type = '数据类型不能为空';
        }
        return {valid: Object.keys(errors).length === 0, errors};
    }

    render() {
        const {dispatch, file} = this.props;
        const body = (
            <div style={{maxHeight: '60vh', overflow: 'auto', padding: '0 15px'}}>
                <div className="row">
                    <div className="form-group col-xs-6">
                        <label>字段名称：</label>
                        <input type="text" name="name" className="form-control"
                            value={this.state.name}
                            onChange={(e) => this.setState({name: e.target.value, errors: {...this.state.errors, name: undefined}})}/>
                        {this.state.errors.name && <div className="text-danger" style={{fontSize:'12px'}}>{this.state.errors.name}</div>}
                    </div>
                    <div className="form-group col-xs-6">
                        <label>标题：</label>
                        <input type="text" name="label" className="form-control"
                            value={this.state.label}
                            onChange={(e) => this.setState({label: e.target.value, errors: {...this.state.errors, label: undefined}})}/>
                        {this.state.errors.label && <div className="text-danger" style={{fontSize:'12px'}}>{this.state.errors.label}</div>}
                    </div>
                </div>
                <div className="row">
                    <div className="form-group col-xs-6">
                        <label>默认值:</label>
                        <input type="text" name="defaultValue" className="form-control"
                            value={this.state.defaultValue}
                            onChange={(e) => this.setState({defaultValue: e.target.value, errors: {...this.state.errors, defaultValue: undefined}})}/>
                        {this.state.errors.defaultValue && <div className="text-danger" style={{fontSize:'12px'}}>{this.state.errors.defaultValue}</div>}
                    </div>
                    <div className="form-group col-xs-6">
                        <label>数据类型:</label>
                        <select name="type" className="form-control"
                            value={this.state.type}
                            onChange={(e) => this.setState({type: e.target.value, errors: {...this.state.errors, type: undefined}})}>
                            {['String', 'Integer', 'Char', 'Double', 'Long', 'Float', 'BigDecimal', 'Boolean', 'Date', 'List', 'Set', 'Map', 'Enum', 'Object']
                                .map(option =>
                                    <option value={option} key={option}>{option}</option>
                                )}
                        </select>
                        {this.state.errors.type && <div className="text-danger" style={{fontSize:'12px'}}>{this.state.errors.type}</div>}
                    </div>
                </div>
                <div className="form-group">
                    <label>实现逻辑:</label>
                    <textarea name="logicComment" className="form-control" rows={3} maxLength={300}
                        value={this.state.logicComment}
                        onChange={(e) => this.setState({logicComment: e.target.value})}/>
                </div>
                <div className="row">
                    <div className="form-group col-xs-6">
                        <label>状态:</label>
                        <select name="dsStatus" className="form-control"
                            value={this.state.dsStatus}
                            onChange={(e) => this.setState({dsStatus: Number(e.target.value)})}>
                            <option value="0">待开发</option>
                            <option value="1">已上线</option>
                        </select>
                    </div>
                </div>

            </div>
        );
        const buttons = [
            {
                name: '保存',
                className: 'btn btn-success',
                icon: 'fa fa-floppy-o',
                click: function (this: AddParamsDialog) {
                    var {valid, errors} = this._validate();
                    if (!valid) {
                        this.setState({errors});
                        return;
                    }
                    var {rowIndex, create, masterRowData, name, label, type, defaultValue, logicComment, categoryLabel, dsStatus} = this.state;
                    if (create) {
                        dispatch(action.addVariable({
                            clazz: masterRowData!.clazz,
                            name: name || '',
                            label: label || '',
                            dataType: type || '',
                            defaultVal: defaultValue || '',
                            logicComment: logicComment || '',
                            categoryLabel: categoryLabel || '',
                            dsStatus: String(dsStatus)
                        }, file));
                    }
                    event.eventEmitter.emit(event.SHOW_LOADING);
                    event.eventEmitter.emit(event.HIDE_CREATE_PARAMS_DIALOG);
                }.bind(this)
            }
        ];
        return (<CommonDialog title={this.state.title} body={body} buttons={buttons} visible={this.state.visible} onClose={() => this.setState({visible: false})}/>);
    };
}
