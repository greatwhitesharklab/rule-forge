import React, {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import CommonTree from '../../components/tree/component/CommonTree.jsx';
import * as event from '../../components/componentEvent.js';
import {buildData} from '../../components/componentAction.js';

export default class ResourceListDialogComponent extends Component {
    constructor(props) {
        super(props);
        this.state = {visible: false, treeData: null};
        this.callback = null;
        this.type = null;
        this.currentNodeData = null;
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_RESOURCE_LIST_DIALOG, (config) => {
            this.type = config.type;
            this.callback = config.callback;
            this.currentNodeData = null;
            var url = (window.ruleforgeServer || '') + "ruleforge?action=loadrepo&filetype=" + config.type + "&path=" + config.file;
            fetch(url, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'}
            }).then(function (response) {
                if (!response.ok) throw response;
                return response.json();
            }).then(function (data) {
                buildData(data);
                this.setState({treeData: data, visible: true});
            }.bind(this)).catch(function () {
                window.bootbox.alert("加载资源失败");
            });
        });
        event.eventEmitter.on(event.CLOSE_RESOURCE_LIST_DIALOG, () => {
            this.setState({visible: false});
        });
        event.eventEmitter.on(event.TREE_NODE_CLICK, (nodeData) => {
            this.currentNodeData = nodeData;
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_RESOURCE_LIST_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_RESOURCE_LIST_DIALOG);
        event.eventEmitter.removeAllListeners(event.TREE_NODE_CLICK);
    }

    getSelectedFile() {
        if (!this.currentNodeData) {
            window.bootbox.alert("请先选择一个库文件！");
            return null;
        }
        return this.currentNodeData.fullPath;
    }

    handleConfirm() {
        var selectedFile = this.getSelectedFile();
        if (!selectedFile) return;
        var fullPath = "jcr:" + selectedFile;
        this.callback(this.type, fullPath);
        this.setState({visible: false});
    }

    handleSelectVersion() {
        var selectedFile = this.getSelectedFile();
        if (!selectedFile) return;
        var versionDialog = new ruleforge.ResourceVersionDialog(selectedFile);
        versionDialog.open(function (file) {
            var fullPath = "jcr:" + file;
            this.callback(this.type, fullPath);
            this.setState({visible: false});
        }.bind(this));
    }

    handleCancel() {
        this.setState({visible: false});
    }

    render() {
        var body = (
            <div className='tree' style={{marginLeft: '10px'}}>
                <CommonTree data={this.state.treeData}/>
            </div>
        );
        var buttons = [
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
            <CommonDialog visible={this.state.visible} title="库列表" body={body} buttons={buttons}/>
        );
    }
}
