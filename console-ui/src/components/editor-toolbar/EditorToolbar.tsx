import { Component } from 'react';
import * as componentEvent from '../../components/componentEvent.js';
import { OPEN_CONFIG_LIBRARY_DIALOG } from '../dialog/component/ConfigLibraryDialog.jsx';
import {DirtyApi, DirtyContext} from '../../editor/EditorContexts';

interface EditorToolbarProps {
    onSave?: (saveNewVersion: boolean) => void;
    onReady?: (api: { clearDirty: () => void }) => void;
    showVariable?: boolean;
    showConstant?: boolean;
    showAction?: boolean;
    showParameter?: boolean;
    extraButtons?: React.ReactNode;
}

interface EditorToolbarState {
    dirty: boolean;
}

/**
 * 编辑器顶部工具栏(保存 / 保存新版本 / 打开库浏览器)。
 *
 * <p>dirty tracking 由本组件内部 state 管理;并通过 {@link DirtyContext} 把 {@link DirtyApi}
 * 暴露给子树(替代历史 {@code window._setDirty} / {@code window._dirty} 全局变量)。
 * 子树内任意组件可 {@code useDirty().setDirty()} 通知本工具栏变脏;保存成功后
 * 调用 {@code clearDirty()}。
 */
export default class EditorToolbar extends Component<EditorToolbarProps, EditorToolbarState> {
    constructor(props: EditorToolbarProps) {
        super(props);
        this.state = { dirty: false };
        this._setDirty = this._setDirty.bind(this);
        this.clearDirty = this.clearDirty.bind(this);
    }

    /**
     * 用 ref 而非 state 跟踪最新 dirty 值 — {@link DirtyApi.isDirty} 可能在 render 之间被调用,
     * 直接读 state.dirty 会拿到陈旧值。setState 时同步更新 ref 保证一致。
     */
    private dirtyRef: {current: boolean} = {current: false};

    /**
     * 对外暴露的 dirty api(提供给子树 via {@link DirtyContext})。setDirty / clearDirty
     * 内部走类方法,isDirty 读 ref(避免 closure 拿到旧 state)。
     */
    private readonly dirtyApi: DirtyApi = {
        setDirty: () => {
            this._setDirty();
        },
        clearDirty: () => {
            this.clearDirty();
        },
        isDirty: () => {
            return this.dirtyRef.current;
        },
    };

    componentDidMount() {
        if (this.props.onReady) {
            this.props.onReady({ clearDirty: this.clearDirty });
        }
    }

    _setDirty() {
        if (this.state.dirty) return;
        this.dirtyRef.current = true;
        this.setState({ dirty: true });
    }

    clearDirty() {
        if (!this.state.dirty) return;
        this.dirtyRef.current = false;
        this.setState({ dirty: false });
    }

    handleSave = () => {
        if (this.props.onSave) this.props.onSave(false);
    };

    handleSaveNewVersion = () => {
        if (this.props.onSave) this.props.onSave(true);
    };

    openLibraryDialog = (type: string) => {
        componentEvent.eventEmitter.emit(OPEN_CONFIG_LIBRARY_DIALOG, type);
    };

    render() {
        const { dirty } = this.state;
        const {
            showVariable = true,
            showConstant = true,
            showAction = true,
            showParameter = true,
            extraButtons
        } = this.props;

        return (
            <DirtyContext.Provider value={this.dirtyApi}>
                <div className="rf-btn-toolbar" style={{
                    borderBottom: '1px solid var(--rf-border-split)',
                    padding: 'var(--rf-space-2) var(--rf-space-3)',
                    margin: '0',
                    borderRadius: '0',
                    background: 'var(--rf-bg-container)'
                }}>
                    <div className="rf-btn-group rf-btn-group-sm">
                        <button type="button" className={'btn btn-default' + (dirty ? '' : ' disabled')}
                            onClick={this.handleSave}>
                            <i className="rf rf-save"></i> {dirty ? '*保存' : '保存'}
                        </button>
                        <button type="button" className={'btn btn-default' + (dirty ? '' : ' disabled')}
                            onClick={this.handleSaveNewVersion}>
                            <i className="rf rf-savenewversion"></i> {dirty ? '*保存新版本' : '保存新版本'}
                        </button>
                    </div>
                    <div className="rf-btn-group rf-btn-group-sm">
                        {showVariable && (
                            <button type="button" className="rf-btn rf-btn-default"
                                onClick={() => this.openLibraryDialog('variable')}>
                                <i className="rf rf-variable"></i> 变量库
                            </button>
                        )}
                        {showConstant && (
                            <button type="button" className="rf-btn rf-btn-default"
                                onClick={() => this.openLibraryDialog('constant')}>
                                <i className="rf rf-constant"></i> 常量库
                            </button>
                        )}
                        {showAction && (
                            <button type="button" className="rf-btn rf-btn-default"
                                onClick={() => this.openLibraryDialog('action')}>
                                <i className="rf rf-action"></i> 动作库
                            </button>
                        )}
                        {showParameter && (
                            <button type="button" className="rf-btn rf-btn-default"
                                onClick={() => this.openLibraryDialog('parameter')}>
                                <i className="rf rf-parameter"></i> 参数库
                            </button>
                        )}
                        {extraButtons}
                    </div>
                </div>
            </DirtyContext.Provider>
        );
    }
}