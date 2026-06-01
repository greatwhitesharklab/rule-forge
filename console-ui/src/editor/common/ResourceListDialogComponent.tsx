import React, { Component } from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import CommonTree from '../../components/tree/component/CommonTree.jsx';
import * as event from '../../components/componentEvent.js';
import { buildData } from '../../components/componentAction.js';
import { formPost } from '../../api/client.js';

interface ResourceListState {
    visible: boolean;
    treeData: any;
}

export default class ResourceListDialogComponent extends Component<{}, ResourceListState> {
    callback: ((type: string, fullPath: string) => void) | null = null;
    type: string | null = null;
    currentNodeData: any = null;

    constructor(props: {}) {
        super(props);
        this.state = { visible: false, treeData: null };
    }

    componentDidMount(): void {
        event.eventEmitter.on(event.OPEN_RESOURCE_LIST_DIALOG, (config: any) => {
            this.type = config.type;
            this.callback = config.callback;
            this.currentNodeData = null;
            formPost('ruleforge?action=loadrepo&filetype=' + config.type + '&path=' + config.file, {}).then(function (data: any) {
                buildData(data);
                this.setState({ treeData: data, visible: true });
            }.bind(this)).catch(function () {
                window.bootbox.alert('加载资源失败');
            });
        });
        event.eventEmitter.on(event.CLOSE_RESOURCE_LIST_DIALOG, () => {
            this.setState({ visible: false });
        });
        event.eventEmitter.on(event.TREE_NODE_CLICK, (nodeData: any) => {
            this.currentNodeData = nodeData;
        });
    }

    componentWillUnmount(): void {
        event.eventEmitter.removeAllListeners(event.OPEN_RESOURCE_LIST_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_RESOURCE_LIST_DIALOG);
        event.eventEmitter.removeAllListeners(event.TREE_NODE_CLICK);
    }

    getSelectedFile(): string | null {
        if (!this.currentNodeData) {
            window.bootbox.alert('请先选择一个库文件！');
            return null;
        }
        return this.currentNodeData.fullPath;
    }

    handleConfirm(): void {
        const selectedFile = this.getSelectedFile();
        if (!selectedFile) return;
        const fullPath = 'jcr:' + selectedFile;
        this.callback!(this.type!, fullPath);
        this.setState({ visible: false });
    }

    handleSelectVersion(): void {
        const selectedFile = this.getSelectedFile();
        if (!selectedFile) return;
        // Use the global ruleforge.ResourceVersionDialog (not yet converted to TS)
        const versionDialog = new (ruleforge as Record<string, any>).ResourceVersionDialog(selectedFile);
        versionDialog.open(function (file: string) {
            const fullPath = 'jcr:' + file;
            this.callback!(this.type!, fullPath);
            this.setState({ visible: false });
        }.bind(this));
    }

    handleCancel(): void {
        this.setState({ visible: false });
    }

    render(): React.ReactNode {
        const body = (
            <div className='tree' style={{ marginLeft: '10px' }}>
                <CommonTree data={this.state.treeData} />
            </div>
        );
        const buttons = [
            {
                name: '确认', className: 'btn btn-primary',
                click: function () {
                    this.handleConfirm();
                }.bind(this)
            },
            {
                name: '选择版本', className: 'btn btn-default',
                click: function () {
                    this.handleSelectVersion();
                }.bind(this)
            },
            {
                name: '取消', className: 'btn btn-default',
                click: function () {
                    this.handleCancel();
                }.bind(this)
            }
        ];
        return (
            <CommonDialog visible={this.state.visible} title='库列表' body={body} buttons={buttons} onClose={() => this.setState({ visible: false })} />
        );
    }
}
