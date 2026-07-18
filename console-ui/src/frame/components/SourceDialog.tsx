// V7.24:CodeMirror 5 → Monaco 迁移 — 项目编辑器统一为 Monaco(DrlMonaco 同源,
// @monaco-editor/react CDN loader),CodeMirror 5/6 依赖全部移除。
import {Component} from 'react';
import Editor from '@monaco-editor/react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

interface SourceDialogProps {
    dispatch?: (action: unknown) => void;
}

interface SourceDialogState {
    title: string;
    file?: string;
    content: string;
    visible: boolean;
}

export default class SourceDialog extends Component<SourceDialogProps, SourceDialogState> {
    constructor(props: SourceDialogProps) {
        super(props);
        this.state = {title: '', content: '', visible: false};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_SOURCE_DIALOG, (file: string, content: string) => {
            this.setState({file, content, title: `[${file}]源码`, visible: true});
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
        const winHeight = window.innerHeight;
        const height = (winHeight > 800 ? winHeight - 160 : winHeight) + 'px';
        // 只在对话框打开时挂 Monaco(避免 forceRender 的 Modal 提前加载编辑器);
        // key=file 保证切换文件时重建 model。
        const body = this.state.visible ? (
            <Editor
                key={this.state.file}
                height={height}
                defaultLanguage="xml"
                value={this.state.content}
                theme="vs"
                onChange={(value) => this.setState({content: value ?? ''})}
                options={{
                    minimap: {enabled: false},
                    fontSize: 14,
                    lineNumbers: 'on',
                    scrollBeyondLastLine: false,
                    automaticLayout: true,
                }}
            />
        ) : null;
        const buttons = [{
            name: '保存',
            className: 'btn btn-success',
            icon: 'rf rf-save',
            click: () => {
                action.saveFileSource(this.state.file!, this.state.content);
            }
        }];
        return (<CommonDialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons} large={true}
                              onClose={() => this.setState({visible: false})}/>);
    }
}
