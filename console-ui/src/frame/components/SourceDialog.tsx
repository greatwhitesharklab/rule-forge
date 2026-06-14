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
        const codeMirror = CodeMirror.fromTextArea(document.getElementById(editorId) as HTMLTextAreaElement, {
            mode: 'xml',
            lineNumbers: true
        });
        event.eventEmitter.on(event.OPEN_SOURCE_DIALOG, (file: string, content: string) => {
            this.setState({file, codeMirror, title: `[${file}]源码`, visible: true});
            setTimeout(function () {
                const winHeight = window.innerHeight;
                const height = winHeight > 800 ? winHeight - 160 : winHeight;
                codeMirror.setSize('100%', height + 'px');
                codeMirror.setValue(content);
                codeMirror.refresh();
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
