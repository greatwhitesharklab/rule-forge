import {Component} from 'react';
import * as ACTIONS from '@/frame/action.js';
import * as event from '@/frame/event.js';
import Tree from '@/components/tree/component/Tree.jsx';
import PackageNavigator from '@/package/components/PackageNavigator.tsx';
import {SearchOutlined} from '@ant-design/icons';
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
            window._currentGitTag = fileInfo.gitTag || null;
            event.eventEmitter.emit((event as any).OPEN_FILE, fileInfo);
        }
    }

    handleVersionChange = (_version: string | null, gitTag?: string) => {
        window._currentGitTag = gitTag || null;
    }

    render() {
        const {viewMode} = this.state;
        return (
            <div className="file-tree-panel">
                <div className="file-tree-search">
                    <div className="file-tree-search-wrapper">
                        <SearchOutlined />
                        <input type="text" className="rf-form-control fileSearchText file-tree-search-input"
                               placeholder="搜索文件..."
                               onKeyDown={(e: React.KeyboardEvent<HTMLInputElement>) => {
                                   if (e.key === 'Enter') {
                                       const value = (e.target as HTMLInputElement).value;
                                       this.props.store.dispatch(ACTIONS.setSearchFileName(value));
                                       this.props.store.dispatch(
                                           ACTIONS.loadData(true, selectProjectName(this.props.store.getState() as any), null, value)
                                       );
                                   }
                               }}/>
                    </div>
                    <button className="rf-btn rf-btn-default rf-btn-xs"
                            style={{marginLeft: '4px', padding: '2px 8px'}}
                            onClick={this.toggleViewMode}
                            title={viewMode === 'tree' ? '切换到知识包视图' : '切换到文件树视图'}>
                        <i className={viewMode === 'tree' ? 'rf rf-package' : 'rf rf-tree'}/>
                    </button>
                </div>
                <div className="file-tree-content">
                    {viewMode === 'tree' ? (
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
