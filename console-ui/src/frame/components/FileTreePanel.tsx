import {Component} from 'react';
import * as ACTIONS from '@/frame/action.js';
import * as event from '@/frame/event.js';
import Tree from '@/components/tree/component/Tree.jsx';
import PackageNavigator from '@/package/components/PackageNavigator.tsx';
import {selectProjectName} from '@/frame/reducer.ts';

interface FileTreePanelProps {
    store: {
        dispatch: (action: unknown) => void;
        getState: () => { ui?: { projectName?: string | null } };
    };
}

interface FileTreePanelState {
    viewMode: 'tree' | 'package';
}

/**
 * V5.101:文件搜索已迁入顶栏(TopBar),这里只剩文件树 / 知识包视图切换 + 列表。
 * 搜索逻辑(setSearchFileName + loadData)在 TopBar._handleSearch。
 */
export default class FileTreePanel extends Component<FileTreePanelProps, FileTreePanelState> {
    constructor(props: FileTreePanelProps) {
        super(props);
        this.state = {
            viewMode: 'tree'
        };
    }

    toggleViewMode = () => {
        this.setState(prev => ({viewMode: prev.viewMode === 'tree' ? 'package' : 'tree'}));
    }

    handlePackageFileSelect = (fileInfo: { path: string; name: string; version?: string; gitTag?: string }) => {
        const {store} = this.props;
        if (store && store.dispatch) {
            // V5.74.3:写 Redux,见 seeFileSource thunk 通过 getState() 读 currentGitTag
            store.dispatch(ACTIONS.setCurrentGitTag(fileInfo.gitTag || null));
            event.eventEmitter.emit((event as any).OPEN_FILE, fileInfo);
        }
    }

    handleVersionChange = (_version: string | null, gitTag?: string) => {
        // V5.74.3:同上,改 Redux
        this.props.store.dispatch(ACTIONS.setCurrentGitTag(gitTag || null));
    }

    render() {
        const {viewMode} = this.state;
        const isTree = viewMode === 'tree';
        return (
            <div className="file-tree-panel">
                <div className="file-tree-toolbar">
                    <button className="file-tree-view-toggle"
                            onClick={this.toggleViewMode}
                            title={isTree ? '切换到知识包视图' : '切换到文件树视图'}>
                        <i className={isTree ? 'rf rf-package' : 'rf rf-tree'}/>
                        <span>{isTree ? '知识包视图' : '文件树视图'}</span>
                    </button>
                </div>
                <div className="file-tree-content">
                    {isTree ? (
                        <Tree draggable={true}/>
                    ) : (
                        <PackageNavigator
                            project={selectProjectName(this.props.store.getState() as any) || ''}
                            onFileSelect={this.handlePackageFileSelect}
                            onVersionChange={this.handleVersionChange}
                        />
                    )}
                </div>
            </div>
        );
    }
}
