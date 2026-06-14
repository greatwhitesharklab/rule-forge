import { Component } from 'react';
import QuickStart from '../../../frame/QuickStart.jsx';
import * as event from '../../componentEvent.js';
import * as action from '../../../frame/action.js';

interface TabData {
    name: string;
    fullPath: string;
    path: string;
    project?: string;
}

interface TabInfo {
    fullPath: string;
    originalFullPath: string;
    label: string;
}

interface FrameTabProps {
    welcomePage?: string;
    onTabsChange?: (tabs: TabInfo[], activeFullPath: string | null) => void;
}

interface FrameTabState {
    data: TabData[];
    activeFullPath: string | null;
}

export default class FrameTab extends Component<FrameTabProps, FrameTabState> {
    constructor(props: FrameTabProps) {
        super(props);
        this.state = { data: [], activeFullPath: null };
    }

    addTab(newTabData: TabData) {
        const data = [...this.state.data];
        let exist = false;
        const fullPath = this._processFullPath(newTabData.fullPath);

        for (const item of data) {
            if (this._processFullPath(item.fullPath) === fullPath) {
                exist = true;
            }
        }
        if (!exist) {
            data.push(newTabData);
        }
        this.setState({ data, activeFullPath: fullPath }, () => {
            this._notifyParent();
        });
    }

    activateTab(fullPath: string) {
        this.setState({ activeFullPath: fullPath }, () => {
            this._notifyParent();
        });
    }

    closeTab(fullPath: string) {
        const data = [...this.state.data];
        const idx = data.findIndex(item => this._processFullPath(item.fullPath) === fullPath);
        if (idx === -1) return;

        data.splice(idx, 1);
        let newActive = this.state.activeFullPath;
        if (this.state.activeFullPath === fullPath) {
            if (data.length > 0) {
                newActive = this._processFullPath(data[Math.min(idx, data.length - 1)].fullPath);
            } else {
                newActive = null;
            }
        }
        this.setState({ data, activeFullPath: newActive }, () => {
            this._notifyParent();
        });
    }

    closeAllTabs() {
        this.setState({ data: [], activeFullPath: null }, () => {
            this._notifyParent();
        });
    }

    closeOtherTabs(keepFullPath: string) {
        const data = this.state.data.filter(
            item => this._processFullPath(item.fullPath) === keepFullPath
        );
        this.setState({ data, activeFullPath: keepFullPath }, () => {
            this._notifyParent();
        });
    }

    componentDidMount() {
        event.eventEmitter.on(event.TREE_NODE_CLICK, (data: TabData) => {
            this.addTab(data);
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.TREE_NODE_CLICK);
    }

    _notifyParent() {
        if (this.props.onTabsChange) {
            this.props.onTabsChange(this.getTabData(), this.state.activeFullPath);
        }
    }

    _processFullPath(fullPath: string): string {
        return fullPath.replace(new RegExp('/', 'gm'), '')
            .replace(new RegExp('\\.', 'gm'), '')
            .replace(new RegExp(':', 'gm'), '');
    }

    _buildTabLabel(item: TabData): string {
        const fileName = item.name;
        const pointPos = fileName.indexOf('.');
        const fileType = fileName.substring(pointPos + 1);
        let type = '';
        if (fileType === '推送客户端配置') {
            type = '>>' + item.project;
        } else if (fileType === '资源权限配置') {
            type = 'AUTH';
        } else if (fileType === '客户端访问权限配置') {
            type = 'AUTH';
        } else {
            type = action.buildType(fileType);
        }
        if (type === 'package') {
            type = item.fullPath.substring(1);
        }
        return (type === 'AUTH') ? fileName : type + ':' + fileName;
    }

    getTabData(): TabInfo[] {
        return this.state.data.map(item => ({
            fullPath: this._processFullPath(item.fullPath),
            originalFullPath: item.fullPath,
            label: this._buildTabLabel(item),
        }));
    }

    render() {
        // SPA 化后所有编辑器走 window.open('/app/editor/<type>') 新标签打开,
        // FrameTab 不再托管 iframe 内容区。这里只负责:
        //  - 维护 tab 数据结构(addTab / activateTab / closeTab)供 ContentTabBar 联动
        //  - 内容区显示 QuickStart(无论 tab 列表是否为空,实际编辑器都在独立窗口)
        return <QuickStart />;
    }
}
