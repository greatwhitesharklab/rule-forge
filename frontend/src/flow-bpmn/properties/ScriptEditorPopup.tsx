import {Component, createRef} from 'react';
import {EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter} from '@codemirror/view';
import {EditorState} from '@codemirror/state';
import {javascript} from '@codemirror/lang-javascript';
import {linter, lintGutter} from '@codemirror/lint';
import {autocompletion} from '@codemirror/autocomplete';
import {defaultKeymap, history, historyKeymap} from '@codemirror/commands';
import {syntaxHighlighting, defaultHighlightStyle, foldGutter, indentOnInput} from '@codemirror/language';
import {bracketMatching} from '@codemirror/language';
import {closeBrackets, closeBracketsKeymap} from '@codemirror/autocomplete';
import {searchKeymap} from '@codemirror/search';
import {formPost} from '../../api/client.js';

function buildLintFunction(type: string) {
    return function (view: any): any[] | Promise<any[]> {
        const text = view.state.doc.toString();
        if (!text || text.trim().length === 0) return [];
        return formPost('/common/scriptValidation', {type, content: text}).then(function (result: any[]) {
            if (!result) return [];
            return result.map(function (item: any) {
                const line = view.state.doc.line(item.line || 1);
                return {
                    from: line.from,
                    to: line.to,
                    severity: 'error',
                    message: item.message || item.msg || ''
                };
            });
        }).catch(function () {
            return [];
        });
    };
}

interface ScriptEditorPopupProps {
    visible: boolean;
    value: string;
    title?: string;
    lintType?: string;
    onUpdate?: (value: string) => void;
    onConfirm?: (value: string) => void;
    onCancel?: () => void;
}

interface ScriptEditorPopupState {
    value: string;
}

export default class ScriptEditorPopup extends Component<ScriptEditorPopupProps, ScriptEditorPopupState> {
    state: ScriptEditorPopupState = {value: ''};
    containerRef = createRef<HTMLDivElement>();
    private view: EditorView | null = null;

    componentDidMount() {
        this.setState({value: this.props.value || ''});
    }

    componentDidUpdate(prevProps: ScriptEditorPopupProps) {
        if (this.props.value !== prevProps.value && this.props.value !== this.state.value) {
            this.setState({value: this.props.value || ''});
        }
    }

    initEditor() {
        if (this.view) return;
        const container = this.containerRef.current;
        if (!container) return;

        const lintType = this.props.lintType || 'Script';
        const onUpdate = this.props.onUpdate;

        const state = EditorState.create({
            doc: this.state.value,
            extensions: [
                lineNumbers(),
                highlightActiveLineGutter(),
                highlightActiveLine(),
                history(),
                foldGutter(),
                indentOnInput(),
                bracketMatching(),
                closeBrackets(),
                autocompletion(),
                lintGutter(),
                linter(buildLintFunction(lintType), {delay: 500}),
                keymap.of([
                    ...closeBracketsKeymap,
                    ...defaultKeymap,
                    ...searchKeymap,
                    ...historyKeymap,
                ]),
                syntaxHighlighting(defaultHighlightStyle, {fallback: true}),
                javascript(),
                EditorView.updateListener.of(function (update: any) {
                    if (update.docChanged && onUpdate) {
                        onUpdate(update.state.doc.toString());
                    }
                }),
                EditorView.theme({
                    '&': {height: '100%'},
                    '.cm-scroller': {overflow: 'auto'}
                })
            ]
        });

        this.view = new EditorView({
            state: state,
            parent: container
        });
    }

    componentWillUnmount() {
        if (this.view) {
            this.view.destroy();
            this.view = null;
        }
    }

    handleOk = () => {
        const value = this.view ? this.view.state.doc.toString() : this.state.value;
        if (this.props.onConfirm) {
            this.props.onConfirm(value);
        }
    };

    handleCancel = () => {
        if (this.props.onCancel) {
            this.props.onCancel();
        }
    };

    render() {
        if (!this.props.visible) return null;

        setTimeout(() => this.initEditor(), 0);

        return (
            <div className="rf-script-popup-overlay" onClick={this.handleCancel}>
                <div className="rf-script-popup" onClick={e => e.stopPropagation()}>
                    <div className="rf-script-popup-header">
                        <span className="rf-script-popup-title">{this.props.title || '编辑脚本'}</span>
                        <button className="rf-script-popup-close" onClick={this.handleCancel}>&times;</button>
                    </div>
                    <div className="rf-script-popup-body" ref={this.containerRef}></div>
                    <div className="rf-script-popup-footer">
                        <button className="btn btn-sm btn-default" onClick={this.handleCancel}>取消</button>
                        <button className="btn btn-sm btn-primary" onClick={this.handleOk} style={{marginLeft: 8}}>确定</button>
                    </div>
                </div>
            </div>
        );
    }
}
