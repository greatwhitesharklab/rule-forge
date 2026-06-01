import {Component} from 'react';
import FileTreePanel from '@/frame/components/FileTreePanel.jsx';
import * as ACTIONS from '@/frame/action.js';
import * as event from '@/frame/event.js';
import {Store} from 'redux';

interface RuleEditorPanelProps {
    store: Store;
    eventObj: typeof event;
}

interface RuleEditorPanelState {
    projects: string[];
    selectedProject: string | null;
    dropdownOpen: boolean;
}

export default class RuleEditorPanel extends Component<RuleEditorPanelProps, RuleEditorPanelState> {
    constructor(props: RuleEditorPanelProps) {
        super(props);
        this.state = {
            projects: [],
            selectedProject: null,
            dropdownOpen: false
        };
        this._handleClickOutside = this._handleClickOutside.bind(this);
    }

    componentDidMount() {
        const {eventObj} = this.props;
        eventObj.eventEmitter.on(eventObj.PROJECT_LIST_CHANGE, (projectNames: string[]) => {
            // Only update projects list if this is a full list (no specific project selected)
            // Backend returns full list only when no projectName is specified
            if (projectNames.length > 1 || this.state.projects.length === 0) {
                this.setState({projects: projectNames});
            }
            if (projectNames.length > 0 && !this.state.selectedProject) {
                this._selectProject(projectNames[0]);
            }
        });
        eventObj.eventEmitter.on(event.PROJECT_SELECT, (projectName: string) => {
            this.setState({selectedProject: projectName, dropdownOpen: false});
        });
        document.addEventListener('click', this._handleClickOutside as EventListener);

        // Fetch full project list in case the initial load already completed
        // before this component mounted
        if (this.state.projects.length === 0) {
            (this.props.store.dispatch as Function)(ACTIONS.loadData(true));
        }
    }

    componentWillUnmount() {
        document.removeEventListener('click', this._handleClickOutside as EventListener);
    }

    _handleClickOutside(e: MouseEvent) {
        if (this.state.dropdownOpen && !(e.target as Element).closest('.panel-project-selector')) {
            this.setState({dropdownOpen: false});
        }
    }

    _selectProject(name: string) {
        window._projectName = name;
        this.setState({selectedProject: name});
        (this.props.store.dispatch as Function)(ACTIONS.loadData(true, name));
        this.props.eventObj.eventEmitter.emit(this.props.eventObj.PROJECT_FILTER_CHANGE, name);
    }

    _handleCreateProject() {
        this.props.eventObj.eventEmitter.emit(event.OPEN_NEW_PROJECT_DIALOG, {type: 'root'});
        this.setState({dropdownOpen: false});
    }

    render() {
        const {projects, selectedProject, dropdownOpen} = this.state;
        return (
            <div style={{height: '100%', display: 'flex', flexDirection: 'column', background: '#fff'}}>
                <div className="panel-project-selector">
                    <button className="panel-project-btn" onClick={(e) => {
                        e.stopPropagation();
                        this.setState({dropdownOpen: !dropdownOpen});
                    }}>
                        <i className="rf rf-project" style={{marginRight: 6, fontSize: 14}}/>
                        <span>{selectedProject || '选择项目'}</span>
                        <i className="glyphicon glyphicon-chevron-down" style={{marginLeft: 6, fontSize: 10, opacity: 0.5}}/>
                    </button>
                    {dropdownOpen && (
                        <div className="panel-project-dropdown">
                            {projects.map(name => (
                                <div key={name}
                                     className={'panel-dropdown-item' + (name === selectedProject ? ' active' : '')}
                                     onClick={() => this._selectProject(name)}>
                                    <i className={name === selectedProject ? 'rf rf-check' : ''} style={{width: 16}}/>
                                    {name}
                                </div>
                            ))}
                            <div className="panel-dropdown-divider"/>
                            <div className="panel-dropdown-item" onClick={() => this._handleCreateProject()}>
                                <i className="rf rf-createpro" style={{width: 16, fontSize: 12}}/>
                                创建新项目
                            </div>
                        </div>
                    )}
                </div>
                <div style={{flex: 1, overflow: 'auto'}}>
                    <FileTreePanel store={this.props.store}/>
                </div>
            </div>
        );
    }
}
