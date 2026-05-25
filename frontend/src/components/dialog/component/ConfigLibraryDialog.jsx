import {Component} from 'react';
import * as componentEvent from '../../componentEvent.js';
/* bootbox is a global */

const CONFIGS = {
    variable: {
        title: '变量库配置',
        fileType: 'VariableLibrary',
        getLibraries: () => window.variableLibraries,
        refresh: () => window.refreshVariableLibraries(),
        existsMsg: '变量库文件已存在'
    },
    constant: {
        title: '常量库配置',
        fileType: 'ConstantLibrary',
        getLibraries: () => window.constantLibraries,
        refresh: () => window.refreshConstantLibraries(),
        existsMsg: '常量库文件已存在'
    },
    action: {
        title: '动作库配置',
        fileType: 'ActionLibrary',
        getLibraries: () => window.actionLibraries,
        refresh: () => window.refreshActionLibraries(),
        existsMsg: '动作库文件已存在'
    },
    parameter: {
        title: '参数库配置',
        fileType: 'ParameterLibrary',
        getLibraries: () => window.parameterLibraries,
        refresh: () => window.refreshParameterLibraries(),
        existsMsg: '参数库文件已存在'
    }
};

export const OPEN_CONFIG_LIBRARY_DIALOG = 'open_config_library_dialog';
export const CLOSE_CONFIG_LIBRARY_DIALOG = 'close_config_library_dialog';

export default class ConfigLibraryDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {
            visible: false,
            type: null,
            libraries: []
        };
    }

    componentDidMount() {
        componentEvent.eventEmitter.on(OPEN_CONFIG_LIBRARY_DIALOG, (type) => {
            const config = CONFIGS[type];
            if (!config) return;
            this.setState({
                visible: true,
                type: type,
                libraries: [...config.getLibraries()]
            });
        });
        componentEvent.eventEmitter.on(CLOSE_CONFIG_LIBRARY_DIALOG, () => {
            this.setState({visible: false, type: null});
        });
    }

    componentWillUnmount() {
        componentEvent.eventEmitter.removeAllListeners(OPEN_CONFIG_LIBRARY_DIALOG);
        componentEvent.eventEmitter.removeAllListeners(CLOSE_CONFIG_LIBRARY_DIALOG);
    }

    handleAdd = () => {
        const config = CONFIGS[this.state.type];
        componentEvent.eventEmitter.emit(componentEvent.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: window._project,
            fileType: config.fileType,
            callback: (file, version) => {
                let path = 'jcr:' + file;
                if (version !== 'LATEST') {
                    path += ':' + version;
                }
                const libs = config.getLibraries();
                if (libs.indexOf(path) !== -1) {
                    window.bootbox.alert(config.existsMsg);
                    return;
                }
                libs.push(path);
                config.refresh();
                window._setDirty();
                this.setState({libraries: [...libs]});
            }
        });
    };

    handleDelete = (lib) => {
        const config = CONFIGS[this.state.type];
        const libs = config.getLibraries();
        const pos = libs.indexOf(lib);
        if (pos !== -1) {
            libs.splice(pos, 1);
            config.refresh();
            window._setDirty();
            this.setState({libraries: [...libs]});
        }
    };

    handleClose = () => {
        this.setState({visible: false, type: null});
    };

    render() {
        const {visible, type, libraries} = this.state;
        if (!visible || !type) return null;
        const config = CONFIGS[type];

        return (
            <div>
                <div className="modal-backdrop fade in"></div>
                <div className="modal fade in" style={{display: 'block'}} tabIndex="-1" role="dialog">
                    <div className="modal-dialog">
                        <div className="modal-content">
                            <div className="modal-header">
                                <button type="button" className="close" onClick={this.handleClose}>&times;</button>
                                <h4 className="modal-title">{config.title}</h4>
                            </div>
                            <div className="modal-body">
                                <table className="table table-bordered">
                                    <thead>
                                    <tr>
                                        <td>{config.title.replace('配置', '文件')}</td>
                                        <td style={{width: '70px'}}>操作</td>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {libraries.map((lib, index) => (
                                        <tr key={index}>
                                            <td>{lib}</td>
                                            <td>
                                                <button type="button" className="btn btn-link"
                                                        onClick={() => this.handleDelete(lib)}>删除
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                            <div className="modal-footer">
                                <button type="button" className="btn btn-primary" onClick={this.handleAdd}>添加
                                </button>
                                <button type="button" className="btn btn-default" onClick={this.handleClose}>关闭
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}
