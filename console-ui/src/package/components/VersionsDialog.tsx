import {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';
import {
    ResourcePackage,
    ResourceItem,
    DiffItem,
} from '../action.js';
import Grid from '../../components/grid/component/Grid.jsx';

interface VersionsDialogProps {
    dispatch: (a: unknown) => void;
    project: string;
}

interface VersionsDialogState {
    visible: boolean;
    title: string;
    rowData: ResourcePackage | null;
    diffList: DiffItem[];
    versionComment: string;
}

export default class VersionsDialog extends Component<VersionsDialogProps, VersionsDialogState> {
    constructor(props: VersionsDialogProps) {
        super(props);
        this.state = {
            visible: false,
            title: '',
            rowData: null,
            diffList: [],
            versionComment: ''
        };
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_VERSION_DIALOG, (rowData: ResourcePackage) => {
            console.log('生成版本', rowData)
            this.setState({visible: true, title: "生成版本", rowData, diffList: [], versionComment: ''})
        });
        event.eventEmitter.on(event.HIDE_VERSION_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_VERSION_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_VERSION_DIALOG);
    }

    render() {
        const {dispatch, project} = this.props;
        // 引用规则列表数据
        const masterData = this.state.diffList;
        const masterGridHeaders = [
            {id: 'm-type', name: 'type', label: '类型', filterable: true, width: '80px'},
            {id: 'm-path', name: 'path', label: '名称', filterable: true},
            {id: 'm-name', name: 'name', label: '引用节点', filterable: true},
            {id: 'm-version', name: 'version', label: '使用版本', width: '80px'},
        ];
        const masterGridOperationCol = {
            width: '60px',
            operations: [
                {
                    label: '差异',
                    style: {fontSize: '16px', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex: number, rowData: DiffItem) {
                        action.getFileDiff({
                            filePath: rowData.path,
                            targetVersion: rowData.version,
                            originVersion: rowData.version
                        })
                    }
                }
            ]
        };
        // 知识包文件列表
        const resourceData: ResourceItem[] = this.state.rowData && this.state.rowData.resourceItems || [];
        const resourceGridHeaders = [
            {id: 're-name', name: 'name', label: '名称', hideFilterRow: true},
            {id: 're-path', name: 'path', label: '资源文件路径', hideFilterRow: true}
        ];

        const body = (
            <div>
                <div className="form-group row">
                    <div className="col-xs-6">
                        <label style={{textAlign: 'right', width: '100px'}}>知识包编码：</label>
                        <span>{this.state.rowData && this.state.rowData.id || ''}</span>

                    </div>
                    <div className="col-xs-6">
                        <label style={{textAlign: 'right', width: '100px'}}>知识包名称：</label>
                        <span>{this.state.rowData && this.state.rowData.name || ''}</span>
                    </div>
                </div>
                <div className="form-group" style={{display: 'flex'}}>
                    <label style={{textAlign: 'right', width: '100px'}}>版本说明：</label>
                    <input style={{flex: 1}} type="text" className="form-control" name="packageName"
                        value={this.state.versionComment}
                        onChange={e => this.setState({ versionComment: e.target.value })}/>

                </div>
                <div className="form-group" style={{display: 'flex'}}>
                    <label style={{textAlign: 'right', width: '100px'}}>知识包文件：</label>
                    <div style={{flex: 1}}>
                        <Grid headers={resourceGridHeaders} dispatch={dispatch} rows={resourceData} rowClick={(rowData: ResourceItem) => {
                            setTimeout(() => {
                                action.getPackageDiffList(this.props.project, rowData.path, (list: DiffItem[]) => {
                                    list.forEach(item => {
                                        item.targetVersion = item.version
                                    })
                                    this.setState({ diffList: list })
                                });
                            }, 100);
                        }}/>
                    </div>
                </div>
                <div className="form-group" style={{display: 'flex'}}>
                    <label style={{textAlign: 'right', width: '100px'}}>引用规则：</label>
                </div>
                <div style={{paddingLeft: '10px', maxHeight: '300px', overflow: 'auto'}}>
                    <Grid headers={masterGridHeaders} dispatch={dispatch} rows={masterData}
                        operationConfig={masterGridOperationCol}/>
                </div>

            </div>
        );
        const buttons = [
            {
                name: '保存',
                className: 'btn btn-success',
                icon: 'fa fa-floppy-o',
                click: function (this: VersionsDialog) {
                    const associatedFiles = this.state.diffList.map(item => `${item.path}:${item.targetVersion}`)
                    dispatch(action.save(true, project, associatedFiles, this.state.versionComment, this.state.rowData!.id, function () {
                        event.eventEmitter.emit(event.HIDE_VERSION_DIALOG);
                    }))
                }.bind(this)
            }
        ];
        const htmlContent = (
            <div>
                <span style={{fontSize: '14px'}}>项目名称：<span style={{color: 'red'}}>{project || ''}</span></span>
            </div>
        )
        return (<CommonDialog visible={this.state.visible} title={this.state.title} htmlContent={htmlContent} body={body} buttons={buttons} onClose={() => this.setState({visible: false})}/>);
    };
}
