import {Component} from 'react';
import {Button, Table} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';
import {seeFileVersions} from '../action.js';
import {formatDate} from '../../Utils.ts';
import {isEditorType, type EditorType} from '../editorTypeMap';

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
        const auditText = (status: number | string): string => {
            switch (+status) {
                case 0: return '草稿';
                case 10: return '测试中';
                case 20: return '审批中';
                case 90: return '通过';
                case 91: return '拒绝';
                default: return '';
            }
        };
        const columns: ColumnsType<VersionRow> = [
            {title: '版本号', dataIndex: 'name', key: 'name', width: 80},
            {title: '修改后', dataIndex: 'afterComment', key: 'after',
                render: (v: string) => <pre>{v}</pre>},
            {title: '审批状态', dataIndex: 'auditStatus', key: 'audit', width: 80,
                render: (v: number | string) => auditText(v)},
            {title: '测试审批状态', dataIndex: 'testAuditStatus', key: 'test', width: 120,
                render: (v: number | string | null) => (v !== null && v !== undefined && v !== '') ? auditText(v) : ''},
            {title: '创建人', dataIndex: 'createUser', key: 'user', width: 100},
            {title: '创建时间', dataIndex: 'createDate', key: 'date', width: 160,
                render: (v: string) => formatDate(v, 'yyyy-MM-dd HH:mm:ss')},
            {
                title: '操作', key: 'op', width: 100,
                render: (_: unknown, row: VersionRow) => (
                    <>
                        <Button type="link" style={{padding: 0}} onClick={() => {
                            // 历史版本走应用内编辑器标签:file 带 ':版本号' 后缀,原样透传给编辑器组件。
                            const dataType = data.type as string;
                            if (isEditorType(dataType)) {
                                event.openEditorTab({
                                    editorType: dataType as EditorType,
                                    file: (data.fullPath as string) + ':' + row.name,
                                });
                            }
                            // 无编辑器的老类型(如老 .rs.xml):no-op —— 原 iframe fallback
                            // 已随 FrameTab 拆除。
                        }}>打开</Button>
                        <Button type="link" style={{padding: 0, marginLeft: 8}} onClick={() => {
                            const fullPath = (data.fullPath as string) + ':' + row.name;
                            action.seeFileSource({fullPath} as TreeNodeData);
                        }}>源码</Button>
                    </>
                )
            },
        ];
        const body = (
            <div>
                <Table<VersionRow> rowKey="name" dataSource={list} columns={columns}
                    pagination={false} size="small"
                    style={{tableLayout: 'fixed', wordBreak: 'break-all'}}/>

                <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 12, marginTop: 8}}>
                    <Button size="small" disabled={((data['page'] as number) || 1) <= 1}
                            onClick={() => {
                                const queryData = {...data} as Record<string, unknown>;
                                queryData['page'] = Math.max(1, ((data['page'] as number) || 1) - 1);
                                seeFileVersions(queryData as TreeNodeData & { rpp?: string; page?: number });
                            }}>上一页</Button>
                    <span style={{fontSize: 13}}>第 {(data['page'] as number) || 1} 页</span>
                    <Button size="small"
                            onClick={() => {
                                const queryData = {...data} as Record<string, unknown>;
                                queryData['page'] = ((data['page'] as number) || 0) + 1;
                                seeFileVersions(queryData as TreeNodeData & { rpp?: string; page?: number });
                            }}>下一页</Button>
                </div>
            </div>
        );
        return (<CommonDialog visible={this.state.visible} body={body} title={this.state.title} buttons={[]} large={true} dialogStyle={{
            minWidth: '700px'
        }} onClose={() => this.setState({visible: false})}/>);
    }
}
