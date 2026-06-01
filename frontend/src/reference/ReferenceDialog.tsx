import React, {Component, FormEvent, ChangeEvent, MouseEvent} from 'react';
import CommonDialog from '../components/dialog/component/CommonDialog.jsx';
import * as event from './event.js';
import * as frameEvent from '../frame/event.js';
import {formPost} from '../api/client.js';

interface ReferenceFile {
    path: string;
    type: string;
    name: string;
    editor: string;
}

interface ReferenceRequestData {
    path: string;
    project?: string;
    [key: string]: string | undefined;
}

interface ReferenceDialogOptions {
    fromResourceEditor?: boolean;
}

interface ReferenceDialogState {
    title: string;
    files: ReferenceFile[];
    fromResourceEditor: boolean;
    projectFilter: string;
    projectNames: string[];
    currentData: ReferenceRequestData | null;
    currentInfo: string | null;
    searchText: string;
    showDropdown: boolean;
    visible: boolean;
}

export default class ReferenceDialog extends Component<object, ReferenceDialogState> {
    state: ReferenceDialogState = {
        title: '',
        files: [],
        fromResourceEditor: false,
        projectFilter: '',
        projectNames: [],
        currentData: null,
        currentInfo: null,
        searchText: '',
        showDropdown: false,
        visible: false
    };

    // Bound handler refs for cleanup
    private handleClickOutsideBound: (e: Event) => void;

    constructor(props: object) {
        super(props);

        // Bind methods to this
        this.handleProjectFilterChange = this.handleProjectFilterChange.bind(this);
        this.handleSearchChange = this.handleSearchChange.bind(this);
        this.handleProjectSelect = this.handleProjectSelect.bind(this);
        this.toggleDropdown = this.toggleDropdown.bind(this);
        this.handleClickOutsideBound = this.handleClickOutside.bind(this);
    }

    componentDidMount(): void {
        console.log('ReferenceDialog componentDidMount');

        // Listen for project list change events - use frame's eventEmitter
        frameEvent.eventEmitter.on(frameEvent.PROJECT_LIST_CHANGE, (projectNames: string[]) => {
            console.log('ReferenceDialog received projectNames from event:', projectNames);
            this.setState({ projectNames });
        });

        // Try to get project list proactively (if frame already loaded data)
        this.loadProjectNames();

        // Fetch project list from server
        this.loadProjectNamesFromServer();

        // Add click-outside listener for closing dropdown
        document.addEventListener('click', this.handleClickOutsideBound);

        event.eventEmitter.on(event.OPEN_REFERENCE_DIALOG, (data: ReferenceRequestData, info: string | null, options: ReferenceDialogOptions = {}) => {
            this.setState({visible: true});
            // Smart decode path: decode if contains %, otherwise use as-is
            const path = data.path.includes('%') ? decodeURIComponent(data.path) : data.path;
            // Check if from ruleset (ruleset info usually contains "规则集")
            const isFromRuleset = info && info.includes('规则集');
            const title = isFromRuleset ? `引用文件[${path}]的文件` : `引用文件[${path}]${info}的文件`;

            this.setState({
                fromResourceEditor: options.fromResourceEditor || false,
                projectFilter: '',
                searchText: '',
                showDropdown: false,
                currentData: data,
                currentInfo: info
            });
            this.loadReferenceFiles(data, '', info);
        });
        event.eventEmitter.on(event.CLOSE_REFERENCE_DIALOG, () => {
            this.setState({visible: false});
            // Reset state
            this.setState({
                projectFilter: '',
                searchText: '',
                showDropdown: false
            });
        });
    }

    loadProjectNames(): void {
        console.log('ReferenceDialog loadProjectNames called');
        // Try to get project list from DOM
        const projectMenu = document.getElementById('__project_filter_menu');
        if (projectMenu) {
            const projectNames: string[] = [];
            projectMenu.querySelectorAll('li').forEach(function(li) {
                if (!li.classList.contains('_firstItem')) {
                    const link = li.querySelector('a');
                    const projectName = (link as HTMLElement).textContent?.trim() || '';
                    console.log('Found project name:', projectName);
                    if (projectName) {
                        projectNames.push(projectName);
                    }
                }
            });
            if (projectNames.length > 0) {
                console.log('ReferenceDialog loaded projectNames from DOM:', projectNames);
                this.setState({ projectNames });
            } else {
                console.log('No project names found in DOM');
            }
        } else {
            console.log('Project menu not found in DOM');
        }
    }

    loadProjectNamesFromServer(): void {
        console.log('ReferenceDialog loadProjectNamesFromServer called');
        // Fetch project list directly from server
        formPost<{ repo?: { projectNames?: string[] } }>('/frame/loadProjects', {classify: 'true', projectDetail: 'false'}, { silent: true }).then((data) => {
            if (data && data.repo && data.repo.projectNames) {
                console.log('ReferenceDialog loaded projectNames from server:', data.repo.projectNames);
                this.setState({ projectNames: data.repo.projectNames });
            }
        }).catch((error: unknown) => {
            console.log('Failed to load project names from server:', error);
        });
    }

    loadReferenceFiles(data: ReferenceRequestData, project = '', info: string | null = null): void {
        const requestData: Record<string, string> = Object.assign({}, data);
        if (project) {
            requestData.project = project;
        }

        formPost<ReferenceFile[]>('/common/loadReferenceFiles', requestData, { silent: true }).then((files) => {
            // Smart decode path: decode if contains %, otherwise use as-is
            const path = data.path.includes('%') ? decodeURIComponent(data.path) : data.path;
            // Check if from ruleset to decide whether to include info
            const currentInfo = info || this.state.currentInfo;
            const isFromRuleset = currentInfo && currentInfo.includes('规则集');
            const title = isFromRuleset ? `引用文件[${path}]的文件` : `引用文件[${path}]${currentInfo || ''}的文件`;

            // Server returns decoded paths in file list, use as-is
            this.setState({ files, title });
        }).catch(() => {
            alert('加载引用文件信息失败.');
        });
    }

    componentWillUnmount(): void {
        // Clean up event listeners
        frameEvent.eventEmitter.removeAllListeners(frameEvent.PROJECT_LIST_CHANGE);
        event.eventEmitter.removeAllListeners(event.OPEN_REFERENCE_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_REFERENCE_DIALOG);
        document.removeEventListener('click', this.handleClickOutsideBound);
    }

    handleClickOutside(e: Event): void {
        // Check if click is outside the search box or dropdown
        const searchContainer = (e.target as HTMLElement).closest('.project-search-container');
        if (!searchContainer) {
            this.setState({ showDropdown: false });
        }
    }

    handleProjectFilterChange(e: ChangeEvent<HTMLSelectElement>): void {
        const selectedProject = e.target.value;
        this.setState({ projectFilter: selectedProject });

        // Re-fetch files with filter applied
        if (this.state.currentData) {
            this.loadReferenceFiles(this.state.currentData, selectedProject, this.state.currentInfo);
        }
    }

    handleSearchChange(e: ChangeEvent<HTMLInputElement>): void {
        const searchText = e.target.value;
        this.setState({ searchText });
    }

    handleProjectSelect(projectName: string): void {
        this.setState({
            projectFilter: projectName,
            searchText: projectName,
            showDropdown: false
        });

        // Re-fetch files with selected project filter
        if (this.state.currentData) {
            this.loadReferenceFiles(this.state.currentData, projectName, this.state.currentInfo);
        }
    }

    toggleDropdown(): void {
        this.setState(prevState => ({ showDropdown: !prevState.showDropdown }));
    }

    getFilteredProjectNames(): string[] {
        const { projectNames, searchText } = this.state;
        if (!searchText) return projectNames;
        return projectNames.filter(name =>
            name.toLowerCase().includes(searchText.toLowerCase())
        );
    }

    render(): React.ReactNode {
        const { fromResourceEditor, projectFilter, files, searchText, showDropdown } = this.state;
        const filteredProjectNames = this.getFilteredProjectNames();

        console.log('ReferenceDialog render - projectNames:', this.state.projectNames);
        console.log('ReferenceDialog render - fromResourceEditor:', fromResourceEditor);

        const body = (
            <div>
                {fromResourceEditor && (
                    <div style={{ marginBottom: '10px' }}>
                        <label style={{ marginRight: '8px', fontWeight: 'bold' }}>项目名称:</label>
                        <div style={{ position: 'relative', display: 'inline-block' }} className="project-search-container">
                            <input
                                type="text"
                                value={searchText}
                                onChange={this.handleSearchChange}
                                onFocus={() => this.setState({ showDropdown: true })}
                                placeholder="搜索项目名称..."
                                style={{
                                    minWidth: '150px',
                                    padding: '4px',
                                    border: '1px solid #ccc',
                                    borderRadius: '3px'
                                }}
                            />
                            {showDropdown && (
                                <div style={{
                                    position: 'absolute',
                                    top: '100%',
                                    left: 0,
                                    right: 0,
                                    backgroundColor: 'white',
                                    border: '1px solid #ccc',
                                    borderRadius: '3px',
                                    maxHeight: '200px',
                                    overflowY: 'auto',
                                    zIndex: 1000,
                                    boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                                }}>
                                    <div
                                        style={{
                                            padding: '4px 8px',
                                            cursor: 'pointer',
                                            borderBottom: '1px solid #eee'
                                        }}
                                        onClick={() => this.handleProjectSelect('')}
                                    >
                                        全部项目
                                    </div>
                                    {filteredProjectNames.map(name => (
                                        <div
                                            key={name}
                                            style={{
                                                padding: '4px 8px',
                                                cursor: 'pointer',
                                                borderBottom: '1px solid #eee',
                                                backgroundColor: projectFilter === name ? '#f0f0f0' : 'white'
                                            }}
                                            onClick={() => this.handleProjectSelect(name)}
                                        >
                                            {name}
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                )}
                <table className="table table-bordered">
                    <thead>
                        <tr><td>文件路径</td><td style={{width:'100px'}}>类型</td><td style={{width:'80px'}}>操作</td></tr>
                    </thead>
                    <tbody>
                    {
                        files.map(function (file: ReferenceFile, index: number) {
                            return (
                                <tr key={index}>
                                    <td>{file.path}</td>
                                    <td>{file.type}</td>
                                    <td><button type="button" className="btn btn-link" style={{padding:'5px 5px'}} onClick={function() {
                                        const editorPath = '/html' + file.editor;
                                        const url = '.' + editorPath + '?file=' + file.path;
                                        console.log('url:', url);
                                        const config = {
                                            id: file.path,
                                            name: file.name,
                                            fullPath: file.path,
                                            path: url,
                                            active: true
                                        };
                                        // Trigger TREE_NODE_CLICK event to open file in the right iframe
                                        (window.parent as unknown as { componentEvent: { eventEmitter: { emit: (event: string, data: unknown) => void }; TREE_NODE_CLICK: string } }).componentEvent.eventEmitter.emit(
                                            (window.parent as unknown as { componentEvent: { TREE_NODE_CLICK: string } }).componentEvent.TREE_NODE_CLICK,
                                            config
                                        );
                                    }}>设计器中打开</button></td>
                                </tr>
                            );
                        })
                    }
                    </tbody>
                </table>
            </div>
        );
        return (<CommonDialog buttons={[]} body={body} title={this.state.title} visible={this.state.visible} onClose={() => this.setState({visible: false})}/>);
    }
}
