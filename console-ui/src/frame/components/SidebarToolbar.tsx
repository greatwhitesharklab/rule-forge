import {Component} from 'react';
import * as ACTIONS from '../action.js';
import * as componentEvent from '../../components/componentEvent.js';
import Tree from '../../components/tree/component/Tree.jsx';
import {formPost} from '../../api/client.js';

interface FileTypeFilter {
    type: string;
    icon: string;
    label: string;
    className?: string;
}

const FILE_TYPE_FILTERS: FileTypeFilter[] = [
    {type: 'all', icon: 'glyphicon glyphicon-th', label: '显示所有文件', className: 'rf rf-check'},
    {type: 'lib', icon: 'rf rf-library', label: '库文件'},
    {type: 'rule', icon: 'rf rf-rule', label: '决策集'},
    {type: 'table', icon: 'rf rf-table', label: '决策表'},
    {type: 'tree', icon: 'rf rf-tree', label: '决策树'},
    {type: 'flow', icon: 'rf rf-flow', label: '决策流'}
];

interface EventModule {
    eventEmitter: {
        on(event: string, listener: (...args: any[]) => void): void;
        emit(event: string, ...args: any[]): void;
    };
    CHANGE_CLASSIFY: string;
    PROJECT_LIST_CHANGE: string;
    PROJECT_FILTER_CHANGE: string;
}

interface SidebarToolbarProps {
    store: {
        dispatch: (action: unknown) => void;
    };
    eventObj: EventModule;
}

interface SidebarToolbarState {
    classifyText: string;
    noClassifyText: string;
    activeFilter: string;
    projects: string[];
    selectedProject: string | null;
}

export default class SidebarToolbar extends Component<SidebarToolbarProps, SidebarToolbarState> {
    constructor(props: SidebarToolbarProps) {
        super(props);
        this.state = {
            classifyText: '✔ 分类展示',
            noClassifyText: '    集中展示',
            activeFilter: 'all',
            projects: [],
            selectedProject: null
        };
    }

    componentDidMount() {
        const {store, eventObj} = this.props;

        eventObj.eventEmitter.on(eventObj.CHANGE_CLASSIFY, (classify: boolean) => {
            window._classify = classify;
            if (classify) {
                this.setState({
                    classifyText: '    分类展示',
                    noClassifyText: '✔ 集中展示'
                });
            } else {
                this.setState({
                    classifyText: '✔ 分类展示',
                    noClassifyText: '    集中展示'
                });
            }
        });

        eventObj.eventEmitter.on(eventObj.PROJECT_LIST_CHANGE, (projectNames: string[]) => {
            this.setState(prevState => ({
                projects: projectNames,
                selectedProject: (prevState.selectedProject && projectNames.includes(prevState.selectedProject))
                    ? prevState.selectedProject : null
            }));
        });

        eventObj.eventEmitter.on(eventObj.PROJECT_FILTER_CHANGE, (name: string) => {
            this.setState({selectedProject: name});
        });
    }

    loadData(classify: boolean | null | undefined, projectName?: string | null, types?: string | null, searchFileName?: string | null) {
        this.props.store.dispatch(ACTIONS.loadData(classify, projectName, types, searchFileName));
    }

    showLoadingThen(fn: () => void) {
        componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
        setTimeout(() => {
            fn();
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }, 200);
    }

    handleClassifyToggle = (classify: boolean) => {
        this.showLoadingThen(() => {
            this.loadData(classify, window._projectName, window._types);
        });
    };

    handleShowAllProjects = (e: React.MouseEvent) => {
        e.preventDefault();
        this.showLoadingThen(() => {
            this.loadData(window._classify);
        });
        window._projectName = null;
        this.setState({selectedProject: null});
    };

    handleSelectProject = (name: string) => {
        window._projectName = name;
        this.showLoadingThen(() => {
            this.loadData(window._classify, window._projectName, window._types, window.searchFileName);
            this.props.eventObj.eventEmitter.emit(this.props.eventObj.PROJECT_FILTER_CHANGE, name);
        });
    };

    handleTypeFilter = (type: string, e: React.MouseEvent) => {
        e.preventDefault();
        window._types = type;
        this.showLoadingThen(() => {
            this.loadData(window._classify, window._projectName, window._types);
        });
        this.setState({activeFilter: type});
    };

    handleSearch = () => {
        const input = document.querySelector('.fileSearchText') as HTMLInputElement;
        window.searchFileName = input.value;
        this.loadData(window._classify, window._projectName, window._types, window.searchFileName);
    };

    handleAuthorityConfig = (e: React.MouseEvent) => {
        e.preventDefault();
        componentEvent.eventEmitter.emit(componentEvent.TREE_NODE_CLICK, {
            id: 'security_config_',
            name: '资源权限配置',
            fullPath: 'security_config_',
            path: './html/editor.html?type=permission'
        });
    };

    handleLogout = (e: React.MouseEvent) => {
        e.preventDefault();
        formPost('/frame/logout', {}).then(function () {
            window.location.href = 'html/login.html';
        });
    };

    render() {
        const {classifyText, noClassifyText, activeFilter, projects, selectedProject} = this.state;

        return (
            <div className="sidebar-container">
                {/* Sidebar top toolbar */}
                <div className="sidebar-toolbar">
                    <div className="sidebar-toolbar-actions">
                        <span className="dropdown">
                            <a href="#" className="sidebar-tool-btn dropdown-toggle" data-toggle="dropdown" title="知识库内容展示方式">
                                <i className="rf rf-display"/> <b className="caret"/>
                            </a>
                            <ul className="dropdown-menu">
                                <li><a href="#" onClick={(e) => { e.preventDefault(); this.handleClassifyToggle(true); }}>{classifyText}</a></li>
                                <li><a href="#" onClick={(e) => { e.preventDefault(); this.handleClassifyToggle(false); }}>{noClassifyText}</a></li>
                            </ul>
                        </span>

                        <span className="dropdown">
                            <a href="#" className="sidebar-tool-btn dropdown-toggle" data-toggle="dropdown" title="项目过滤">
                                <i className="rf rf-list"/> <b className="caret"/>
                            </a>
                            <ul className="dropdown-menu">
                                <li>
                                    <a href="#" onClick={this.handleShowAllProjects} style={{marginLeft: selectedProject ? '22px' : '0px'}}>
                                        <i className={!selectedProject ? 'rf rf-check' : ''}/> 显示所有项目
                                    </a>
                                </li>
                                {projects.map(name => (
                                    <li key={name}>
                                        <a href="#" onClick={(e) => { e.preventDefault(); this.handleSelectProject(name); }}
                                           style={{marginLeft: selectedProject === name ? '0px' : '22px'}}>
                                            <i className={selectedProject === name ? 'rf rf-check' : ''}/> {name}
                                        </a>
                                    </li>
                                ))}
                            </ul>
                        </span>

                        <span className="dropdown">
                            <a href="#" className="sidebar-tool-btn dropdown-toggle" data-toggle="dropdown" title="文件类型过滤">
                                <i className="rf rf-type"/> <b className="caret"/>
                            </a>
                            <ul className="dropdown-menu">
                                {FILE_TYPE_FILTERS.map(ft => (
                                    <li key={ft.type}>
                                        <a href="#" onClick={(e) => this.handleTypeFilter(ft.type, e)}>
                                            <i className={activeFilter === ft.type ? 'rf rf-check' : ''}/>
                                            {' '}<i className={ft.icon}/> {ft.label}
                                        </a>
                                    </li>
                                ))}
                            </ul>
                        </span>

                        <span className="dropdown">
                            <a href="#" className="sidebar-tool-btn dropdown-toggle" data-toggle="dropdown" title="权限配置">
                                <i className="rf rf-authority"/>
                            </a>
                            <ul className="dropdown-menu">
                                <li><a href="#" onClick={this.handleAuthorityConfig}>资源权限配置</a></li>
                            </ul>
                        </span>
                    </div>
                </div>

                {/* Search */}
                <div className="sidebar-search">
                    <div className="sidebar-search-wrapper">
                        <i className="glyphicon glyphicon-search sidebar-search-icon"/>
                        <input type="text" className="form-control fileSearchText sidebar-search-input"
                               placeholder="搜索文件..."
                               onKeyDown={(e) => { if (e.key === 'Enter') this.handleSearch(); }}/>
                    </div>
                </div>

                {/* File tree */}
                <div className="sidebar-tree">
                    <Tree draggable={true} treeType={'public'}/>
                    <Tree draggable={true}/>
                </div>

                {/* User area */}
                <div className="sidebar-user">
                    <div className="sidebar-user-avatar">
                        {(window.__currentUser && window.__currentUser.username)
                            ? window.__currentUser.username.charAt(0).toUpperCase() : 'U'}
                    </div>
                    <span className="sidebar-user-name">
                        {window.__currentUser ? window.__currentUser.username : ''}
                    </span>
                    <a href="#" className="sidebar-user-logout" title="退出登录" onClick={this.handleLogout}>
                        <i className="glyphicon glyphicon-log-out"/>
                    </a>
                </div>
            </div>
        );
    }
}
