import '../../../css/iconfont.css';
import { Component } from 'react';
import CommonDialog from './CommonDialog.tsx';
import CommonTree from '../../tree/component/CommonTree.jsx';
import * as action from '../../componentAction.js';
import * as event from '../../componentEvent.js';
import VersionSelectDialog from './VersionSelectDialog.tsx';

interface KnowledgeTreeDialogConfig {
    project: string | null;
    forLib?: boolean;
    fileType?: string;
    callback: (file: string, version: string) => void;
    searchFileName?: string;
}

interface KnowledgeTreeDialogProps {
    selectDir?: boolean | ((data: TreeNodeData) => void);
}

interface KnowledgeTreeDialogState {
    title: string;
    visible: boolean;
    data?: TreeNodeData;
    fileType?: string;
}

export default class KnowledgeTreeDialog extends Component<KnowledgeTreeDialogProps, KnowledgeTreeDialogState> {
    private _config: KnowledgeTreeDialogConfig | null = null;
    private callback: ((file: string, version: string) => void) | null = null;
    private currentNodeData: TreeNodeData | null = null;

    constructor(props: KnowledgeTreeDialogProps) {
        super(props);
        this.state = { title: '选择资源', visible: false };
    }

    componentDidMount(): void {
        event.eventEmitter.on(event.OPEN_KNOWLEDGE_TREE_DIALOG, (config: KnowledgeTreeDialogConfig) => {
            this._config = config;
            this.callback = config.callback;
            action.loadResourceTreeData({
                project: config.project || '',
                forLib: config.forLib,
                fileType: config.fileType
            }, function (data: TreeNodeData) {
                this.setState({ data, fileType: config.fileType, visible: true });
            }.bind(this));
        });
        event.eventEmitter.on(event.HIDE_KNOWLEDGE_TREE_DIALOG, () => {
            this.setState({ visible: false });
        });
        event.eventEmitter.on(event.TREE_NODE_CLICK, (nodeData: TreeNodeData) => {
            this.currentNodeData = nodeData;
        });
        event.eventEmitter.on(event.TREE_DIR_NODE_CLICK, (nodeData: TreeNodeData) => {
            this.currentNodeData = nodeData;
        });
    }

    search(): void {
        const searchInput = document.querySelector('.resSearchText') as HTMLInputElement | null;
        const searchFileName = searchInput ? searchInput.value : '';
        const config = this._config!;
        action.loadResourceTreeData({
            project: config.project || '',
            forLib: config.forLib,
            fileType: config.fileType,
            searchFileName
        }, function (data: TreeNodeData) {
            this.setState({ data, fileType: config.fileType });
        }.bind(this));
    }

    componentWillUnmount(): void {
        event.eventEmitter.removeAllListeners(event.OPEN_KNOWLEDGE_TREE_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_KNOWLEDGE_TREE_DIALOG);
        event.eventEmitter.removeAllListeners(event.TREE_NODE_CLICK);
    }

    render() {
        const body = (
            <div className='tree' style={{ marginLeft: 'var(--rf-space-3)' }}>
                <div>
                    <input type="text" className="form-control resSearchText" placeholder="请输入要查询的文件名..."
                           style={{ display: 'inline-block', width: '220px' }} />
                    <a href="#" onClick={this.search.bind(this)} style={{ margin: 'var(--rf-space-2)', fontSize: '16px', color: 'var(--rf-primary)' }}><i
                        className="glyphicon glyphicon-search" /></a>
                </div>
                <CommonTree data={this.state.data!} selectDir={this.props.selectDir as ((data: TreeNodeData) => void) | undefined} />
            </div>
        );
        const fileType = this.state.fileType || '';
        const buttons = [
            {
                name: '最新版本',
                className: 'btn btn-danger',
                icon: 'glyphicon glyphicon-floppy-saved',
                click: function (): void {
                    if (this.currentNodeData) {
                        this.callback!(this.currentNodeData.fullPath, 'LATEST');
                        event.eventEmitter.emit(event.HIDE_KNOWLEDGE_TREE_DIALOG);
                    } else {
                        window.bootbox.alert("请先选择一个文件");
                    }
                }.bind(this)
            }
        ];
        buttons.push({
            name: '历史版本',
            className: 'btn btn-primary',
            icon: 'glyphicon glyphicon-hand-up',
            click: function (): void {
                if (this.currentNodeData) {
                    event.eventEmitter.emit(event.OPEN_VERSION_SELECT_DIALOG, {
                        file: this.currentNodeData.fullPath,
                        callback: this.callback
                    });
                } else {
                    window.bootbox.alert("请先选择一个文件");
                }
            }.bind(this)
        });
        return (
            <div>
                <VersionSelectDialog />
                <CommonDialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons} onClose={() => this.setState({ visible: false })} />
            </div>
        );
    }
}
