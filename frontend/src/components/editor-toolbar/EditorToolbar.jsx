import React, {Component} from 'react';
import * as componentEvent from '../../components/componentEvent.js';
import {OPEN_CONFIG_LIBRARY_DIALOG} from '../dialog/component/ConfigLibraryDialog.jsx';

export default class EditorToolbar extends Component {
    constructor(props) {
        super(props);
        this.state = {dirty: false};
        this._setDirty = this._setDirty.bind(this);
        this.clearDirty = this.clearDirty.bind(this);
    }

    componentDidMount() {
        window._setDirty = this._setDirty;
        if (this.props.onReady) {
            this.props.onReady({clearDirty: this.clearDirty});
        }
    }

    _setDirty() {
        if (this.state.dirty) return;
        window._dirty = true;
        this.setState({dirty: true});
    }

    clearDirty() {
        if (!this.state.dirty) return;
        window._dirty = false;
        this.setState({dirty: false});
    }

    handleSave = () => {
        if (this.props.onSave) this.props.onSave(false);
    };

    handleSaveNewVersion = () => {
        if (this.props.onSave) this.props.onSave(true);
    };

    openLibraryDialog = (type) => {
        componentEvent.eventEmitter.emit(OPEN_CONFIG_LIBRARY_DIALOG, type);
    };

    render() {
        const {dirty} = this.state;
        const {
            showVariable = true,
            showConstant = true,
            showAction = true,
            showParameter = true,
            extraButtons
        } = this.props;

        return (
            <div className="btn-toolbar" style={{
                border: 'solid 1px #d0d0d0',
                padding: '5px',
                margin: '3px',
                borderRadius: '5px',
                background: '#fdfdfd'
            }}>
                <div className="btn-group btn-group-sm">
                    <button type="button" className={'btn btn-default' + (dirty ? '' : ' disabled')}
                            onClick={this.handleSave}>
                        <i className="rf rf-save"></i> {dirty ? '*保存' : '保存'}
                    </button>
                    <button type="button" className={'btn btn-default' + (dirty ? '' : ' disabled')}
                            onClick={this.handleSaveNewVersion}>
                        <i className="rf rf-savenewversion"></i> {dirty ? '*保存新版本' : '保存新版本'}
                    </button>
                </div>
                <div className="btn-group btn-group-sm">
                    {showVariable && (
                        <button type="button" className="btn btn-default"
                                onClick={() => this.openLibraryDialog('variable')}>
                            <i className="rf rf-variable"></i> 变量库
                        </button>
                    )}
                    {showConstant && (
                        <button type="button" className="btn btn-default"
                                onClick={() => this.openLibraryDialog('constant')}>
                            <i className="rf rf-constant"></i> 常量库
                        </button>
                    )}
                    {showAction && (
                        <button type="button" className="btn btn-default"
                                onClick={() => this.openLibraryDialog('action')}>
                            <i className="rf rf-action"></i> 动作库
                        </button>
                    )}
                    {showParameter && (
                        <button type="button" className="btn btn-default"
                                onClick={() => this.openLibraryDialog('parameter')}>
                            <i className="rf rf-parameter"></i> 参数库
                        </button>
                    )}
                    {extraButtons}
                </div>
            </div>
        );
    }
}
