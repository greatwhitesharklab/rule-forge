import React, {Component} from 'react';
import * as ACTIONS from '../action.js';
import * as componentEvent from '../../components/componentEvent.js';
import Tree from '../../components/tree/component/Tree.jsx';

const FILE_TYPE_FILTERS = [
    {type: 'all', icon: 'glyphicon glyphicon-th', label: '显示所有文件', className: 'rf rf-check'},
    {type: 'lib', icon: 'rf rf-library', label: '库文件'},
    {type: 'rule', icon: 'rf rf-rule', label: '决策集'},
    {type: 'table', icon: 'rf rf-table', label: '决策表'},
    {type: 'tree', icon: 'rf rf-tree', label: '决策树'},
    {type: 'flow', icon: 'rf rf-flow', label: '决策流'}
];

export default class SidebarToolbar extends Component {
    constructor(props) {
        super(props);
        this.state = {
            classifyText: '✔ 分类展示',
            noClassifyText: '    集中展示',
            activeFilter: 'all',
            projects: [],
            selectedProject: null
        };
    }

    componentDidMount() {
        const {store, eventObj} = this.props;

        eventObj.eventEmitter.on(eventObj.CHANGE_CLASSIFY, classify => {
            window._classify = classify;
            if (classify) {
                this.setState({
                    classifyText: '    分类展示',
                    noClassifyText: '✔ 集中展示'
                });
            } else {
                this.setState({
                    classifyText: '✔ 分类展示',
                    noClassifyText: '    集中展示'
                });
            }
        });

        eventObj.eventEmitter.on(eventObj.PROJECT_LIST_CHANGE, projectNames => {
            this.setState({
                projects: projectNames,
                selectedProject: null
            });
        });

        eventObj.eventEmitter.on(eventObj.PROJECT_FILTER_CHANGE, name => {
            this.setState({selectedProject: name});
        });
    }

    loadData(classify, projectName, types, searchFileName) {
        this.props.store.dispatch(ACTIONS.loadData(classify, projectName, types, searchFileName));
    }

    showLoadingThen(fn) {
        componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
        setTimeout(() => {
            fn();
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }, 200);
    }

    handleClassifyToggle = (classify) => {
        this.showLoadingThen(() => {
            this.loadData(classify, window._projectName, window._types);
        });
    };

    handleShowAllProjects = (e) => {
        e.preventDefault();
        this.showLoadingThen(() => {
            this.loadData(window._classify);
        });
        window._projectName = null;
        this.setState({selectedProject: null});
    };

    handleSelectProject = (name) => {
        window._projectName = name;
        this.showLoadingThen(() => {
            this.loadData(window._classify, window._projectName, window._types, window.searchFileName);
            this.props.eventObj.eventEmitter.emit(this.props.eventObj.PROJECT_FILTER_CHANGE, name);
        });
    };

    handleTypeFilter = (type, e) => {
        e.preventDefault();
        window._types = type;
        this.showLoadingThen(() => {
            this.loadData(window._classify, window._projectName, window._types);
        });
        this.setState({activeFilter: type});
    };

    handleSearch = (e) => {
        e.preventDefault();
        window.searchFileName = document.querySelector('.fileSearchText').value;
        this.loadData(window._classify, window._projectName, window._types, window.searchFileName);
    };

    handleAuthorityConfig = (e) => {
        e.preventDefault();
        componentEvent.eventEmitter.emit(componentEvent.TREE_NODE_CLICK, {
            id: 'security_config_',
            name: '资源权限配置',
            fullPath: 'security_config_',
            path: './html/permission-config-editor.html'
        });
    };

    handleLogout = (e) => {
        e.preventDefault();
        fetch(window._server + '/frame/logout', {method: 'POST'}).then(function() {
            window.location.href = 'html/login.html';
        });
    };

    render() {
        const {classifyText, noClassifyText, activeFilter, projects, selectedProject} = this.state;

        return (
            <div>
                <div style={{
                    border: 'solid 1px #ddd',
                    height: '35px',
                    background: '#f5f5f5',
                    padding: '5px 10px'
                }}>
                    <span className="dropdown" style={{margin: '5px'}}>
                        <a href="#" className="dropdown-toggle" data-toggle="dropdown" title="知识库内容展示方式">
                            <i className="rf rf-display" style={{fontSize: '12pt'}}/> <b className="caret"/>
                        </a>
                        <ul className="dropdown-menu">
                            <li><a href="#" onClick={(e) => { e.preventDefault(); this.handleClassifyToggle(true); }}>{classifyText}</a></li>
                            <li><a href="#" onClick={(e) => { e.preventDefault(); this.handleClassifyToggle(false); }}>{noClassifyText}</a></li>
                        </ul>
                    </span>

                    <span className="dropdown" style={{margin: '5px'}}>
                        <a href="#" className="dropdown-toggle" data-toggle="dropdown" title="项目过滤">
                            <i className="rf rf-list" style={{fontSize: '12pt'}}/> <b className="caret"/>
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

                    <span className="dropdown" style={{margin: '5px'}}>
                        <a href="#" className="dropdown-toggle" data-toggle="dropdown" title="文件类型过滤">
                            <i className="rf rf-type" style={{fontSize: '12pt'}}/> <b className="caret"/>
                        </a>
                        <ul className="dropdown-menu">
                            {FILE_TYPE_FILTERS.map(ft => (
                                <li key={ft.type}>
                                    <a href="#" onClick={(e) => this.handleTypeFilter(ft.type, e)}>
                                        <i className={activeFilter === ft.type ? 'rf rf-check' : ''}/>
                                        {' '}<i className={ft.icon}/> {ft.label}
                                    </a>
                                </li>
                            ))}
                        </ul>
                    </span>

                    <span className="dropdown" style={{margin: '5px'}}>
                        <a href="#" className="dropdown-toggle" data-toggle="dropdown" title="权限配置">
                            <i className="rf rf-authority" style={{fontSize: '12pt'}}/> <b className="caret"/>
                        </a>
                        <ul className="dropdown-menu">
                            <li><a href="#" onClick={this.handleAuthorityConfig}>资源权限配置</a></li>
                        </ul>
                    </span>

                    <span style={{float: 'right', margin: '5px 10px'}}>
                        <span style={{color: '#666', marginRight: 10}}>
                            <i className="glyphicon glyphicon-user"/> {window.__currentUser ? window.__currentUser.username : ''}
                        </span>
                        <a href="#" title="退出登录" onClick={this.handleLogout}>
                            <i className="glyphicon glyphicon-log-out" style={{fontSize: '12pt'}}/>
                        </a>
                    </span>
                </div>
                <div className='tree' style={{marginLeft: '10px'}}>
                    <div style={{margin: '10px 0px 5px 2px'}}>
                        <input type="text" className="form-control fileSearchText" placeholder="输入要查询的文件名..."
                               style={{display: 'inline-block', width: '170px'}}/>
                        <a href="#" onClick={this.handleSearch} style={{margin: '6px', fontSize: '16px'}}>
                            <i className="glyphicon glyphicon-search"/>
                        </a>
                    </div>
                    <Tree draggable={true} treeType={'public'}/>
                    <Tree draggable={true}/>
                </div>
            </div>
        );
    }
}
