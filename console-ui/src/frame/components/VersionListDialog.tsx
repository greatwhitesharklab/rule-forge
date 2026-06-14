import {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';
import {seeFileVersions} from '../action.js';
import * as componentEvent from '../../components/componentEvent.js';
import {formatDate, buildEditorUrl, editorPathToSpaSegment} from '../../Utils.ts';

interface VersionRow {
    name: string;
    afterComment: string;
    auditStatus: number | string;
    testAuditStatus: number | string | null;
    createUser: string;
    createDate: number;
    [key: string]: unknown;
}

interface VersionListDialogProps {
    dispatch?: (action: unknown) => void;
}

interface VersionListDialogState {
    title: string;
    list: VersionRow[];
    num: number;
    data: Record<string, unknown>;
    visible: boolean;
}

const EMPTY_DATA: Record<string, unknown> = {};

export default class VersionListDialog extends Component<VersionListDialogProps, VersionListDialogState> {
    constructor(props: VersionListDialogProps) {
        super(props);
        this.state = {title: '', list: [], num: 0, data: EMPTY_DATA, visible: false};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_FILE_VERSION_DIALOG, (config: { files: VersionRow[]; data: Record<string, unknown>; num: number }) => {
            const {files, data, num} = config;
            const file = data.fullPath as string;
            this.setState({title: `${file}文件版本列表`, list: files, num: num, data, visible: true});
        });
        event.eventEmitter.on(event.CLOSE_FILE_VERSION_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_FILE_VERSION_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_FILE_VERSION_DIALOG);
    }

    render() {
        const {list, data} = this.state;
        const body = (
            <div>
                <table className="table table-bordered table-hover"
                       style={{tableLayout: 'fixed', wordBreak: 'break-all'}}>
                    <thead>
                    <tr>
                        <td style={{width: '80px'}}>版本号</td>
                        <td>修改后</td>
                        <td style={{width: '80px'}}>审批状态</td>
                        <td style={{width: '120px'}}>测试审批状态</td>
                        <td style={{width: '100px'}}>创建人</td>
                        <td style={{width: '160px'}}>创建时间</td>
                        <td style={{width: '100px'}}>操作</td>
                    </tr>
                    </thead>
                    <tbody>
                    {list.map(function (row: VersionRow, index: number) {
                        let auditStatusStr = "";
                        switch (+row.auditStatus) {
                            case 0:
                                auditStatusStr = "草稿";
                                break;
                            case 10:
                                auditStatusStr = "测试中";
                                break;
                            case 20:
                                auditStatusStr = "审批中";
                                break;
                            case 90:
                                auditStatusStr = "通过";
                                break;
                            case 91:
                                auditStatusStr = "拒绝";
                                break;
                        }
                        let testAuditStatus = '';
                        if (row.testAuditStatus !== null) {
                            switch (+row.testAuditStatus) {
                                case 0:
                                    testAuditStatus = "草稿";
                                    break;
                                case 10:
                                    testAuditStatus = "测试中";
                                    break;
                                case 20:
                                    testAuditStatus = "审批中";
                                    break;
                                case 90:
                                    testAuditStatus = "通过";
                                    break;
                                case 91:
                                    testAuditStatus = "拒绝";
                                    break;
                            }
                        }
                        return (
                            <tr key={index}>
                                <td>{row.name}</td>
                                <td><pre>{row.afterComment}</pre></td>
                                <td>{auditStatusStr}</td>
                                <td>{testAuditStatus}</td>
                                <td>{row.createUser}</td>
                                <td>{formatDate(row.createDate, 'yyyy-MM-dd HH:mm:ss')}</td>
                                <td>
                                    <button type="button" className="btn btn-link" style={{padding: '0'}}
                                            onClick={() => {
                                                // SPA 化:历史版本也走 /app/editor/<type>?file=<path>:<version> 新标签打开。
                                                // editorPath 形如 /html/editor.html?type=flowbpmn → 映射到 flow。
                                                // 无 SPA 路由的 type(如 ul)回退到原 iframe + TREE_NODE_CLICK。
                                                const spaSegment = editorPathToSpaSegment(data.editorPath as string);
                                                if (spaSegment) {
                                                    let spaFile: string;
                                                    if (data.type === 'resourcePackage') {
                                                        const packageName = (data.fullPath as string).split("/")[1];
                                                        spaFile = packageName + '.rp:' + row.name;
                                                    } else {
                                                        spaFile = (data.fullPath as string) + ':' + row.name;
                                                    }
                                                    window.open('/app/editor/' + spaSegment + '?file=' + encodeURIComponent(spaFile), '_blank');
                                                    return;
                                                }

                                                // fallback:无 SPA 路由的 type,保留原 iframe 逻辑
                                                let url = buildEditorUrl((data.editorPath as string), (data.fullPath as string) + ':' + row.name);
                                                let fullPath = (data.fullPath as string) + ':' + row.name;
                                                let name = (data.name as string) + ':' + row.name;
                                                if (data.type === 'resourcePackage') {
                                                    const packageName = (data.fullPath as string).split("/")[1];
                                                    url = buildEditorUrl((data.editorPath as string), packageName + '.rp:' + row.name);
                                                    fullPath = '/' + packageName + ':' + row.name;
                                                    name = data.name as string;
                                                }

                                                const config: Record<string, unknown> = {
                                                    id: (data.id as string) + ':' + row.name,
                                                    name: name,
                                                    fullPath: fullPath,
                                                    path: url,
                                                    active: true
                                                };
                                                componentEvent.eventEmitter.emit(componentEvent.TREE_NODE_CLICK, config);
                                            }}>打开
                                    </button>
                                    <button type="button" className="btn btn-link"
                                            style={{padding: '0', marginLeft: '8px'}}
                                            onClick={() => {
                                                const fullPath = (data.fullPath as string) + ':' + row.name;
                                                action.seeFileSource({fullPath} as TreeNodeData);
                                            }}>源码
                                    </button>
                                </td>
                            </tr>
                        );
                    })}
                    </tbody>
                </table>

                <nav aria-label="分页">
                    <ul className="pagination">
                        <li>
                            <a href="#" aria-label="上一页"
                               onClick={() => {
                                   const queryData = {...data} as Record<string, unknown>;
                                   queryData['page'] = ((data['page'] as number) || 1) - 1;
                                   if ((queryData['page'] as number) < 1) {
                                       queryData['page'] = 1;
                                   }
                                   seeFileVersions(queryData as TreeNodeData & { rpp?: string; page?: number });
                               }}>
                                <span aria-hidden="true">&laquo;</span>
                            </a>
                        </li>
                        <li>
                            <a href="#" aria-label="下一页"
                               onClick={() => {
                                   const queryData = {...data} as Record<string, unknown>;
                                   queryData['page'] = ((data['page'] as number) || 0) + 1;
                                   seeFileVersions(queryData as TreeNodeData & { rpp?: string; page?: number });
                               }}>
                                <span aria-hidden="true">&raquo;</span>
                            </a>
                        </li>
                    </ul>
                </nav>
            </div>
        );
        return (<CommonDialog visible={this.state.visible} body={body} title={this.state.title} buttons={[]} large={true} dialogStyle={{
            minWidth: '700px'
        }} onClose={() => this.setState({visible: false})}/>);
    }
}
