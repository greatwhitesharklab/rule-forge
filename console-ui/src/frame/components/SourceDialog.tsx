// V6.12.3 TODO: migrate to @codemirror/* v6 (ScriptEditorPopup.tsx 已用 v6,
//                v5 仍在 SourceDialog + QuickTestDialog 2 处,v6 API 不同需重写)
import CodeMirror from 'codemirror';
// V5.74.5:静态 import xml mode 替换原先的 `await import(...xml.js)` + `window.CodeMirror = ...`。
// Vite 把 UMD/CJS module 转 ESM,xml.js IIFE 通过 CJS 分支(require)拿到 CodeMirror 后
// 调用 CodeMirror.defineMode('xml', ...) 注册,不再需要 window 全局。
// 静态 import 仍按需执行(componentDidMount 走 React 生命周期后才 mount 编辑器),
// 不会破坏 V5.8 系列对源码查看的延迟加载语义。
import 'codemirror/mode/xml/xml.js';
import {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

interface SourceDialogProps {
    dispatch?: (action: unknown) => void;
}

interface SourceDialogState {
    title: string;
    file?: string;
    codeMirror?: CodeMirror.Editor;
    visible: boolean;
}

export default class SourceDialog extends Component<SourceDialogProps, SourceDialogState> {
    editorId: string = '__file_source_editor';

    constructor(props: SourceDialogProps) {
        super(props);
        this.state = {title: '', visible: false};
    }

    componentDidMount() {
        const editorId = this.editorId;
        // V5.101.4:CodeMirror 改懒初始化 —— CommonDialog 换 antd Modal 后,body 在 portal 里,
        // componentDidMount 时 textarea 还没挂到 DOM(原手写 modal 是 display:none 始终在 DOM)。
        // 改成对话框真正打开(OPEN 事件 + setState visible 后)才 init。
        const ensureEditor = (): CodeMirror.Editor => {
            let cm = this.state.codeMirror;
            if (!cm) {
                cm = CodeMirror.fromTextArea(document.getElementById(editorId) as HTMLTextAreaElement, {
                    mode: 'xml',
                    lineNumbers: true
                });
                this.setState({codeMirror: cm});
            }
            return cm;
        };
        event.eventEmitter.on(event.OPEN_SOURCE_DIALOG, (file: string, content: string) => {
            this.setState({file, title: `[${file}]源码`, visible: true});
            setTimeout(function () {
                const cm = ensureEditor();
                const winHeight = window.innerHeight;
                const height = winHeight > 800 ? winHeight - 160 : winHeight;
                cm.setSize('100%', height + 'px');
                cm.setValue(content);
                cm.refresh();
            }, 400);
        });
        event.eventEmitter.on(event.CLOSE_SOURCE_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_SOURCE_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_SOURCE_DIALOG);
    }

    render() {
        const body = (
            <textarea id={this.editorId} rows={10}></textarea>
        );
        const buttons = [{
            name: '保存',
            className: 'btn btn-success',
            icon: 'rf rf-save',
            click: function () {
                const newContent = this.state.codeMirror!.getValue(), fullPath = this.state.file;
                action.saveFileSource(fullPath!, newContent);
            }.bind(this)
        }];
        return (<CommonDialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons} large={true}
                              onClose={() => this.setState({visible: false})}/>);
    }
}
