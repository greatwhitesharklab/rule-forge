import { Component } from 'react';
import Grid from '../../../components/grid/component/Grid.jsx';
import CommonDialog from './CommonDialog.tsx';
import * as event from '../../componentEvent.js';
import * as action from '../../componentAction.js';

interface VersionSelectDialogProps {}

interface VersionSelectDialogState {
    title: string;
    visible: boolean;
    data?: TreeNodeData[];
}

export default class VersionSelectDialog extends Component<VersionSelectDialogProps, VersionSelectDialogState> {
    private callback: ((file: string, version: string) => void) | null = null;
    private file: string = '';

    constructor(props: VersionSelectDialogProps) {
        super(props);
        this.state = { title: '', visible: false };
    }

    componentDidMount(): void {
        event.eventEmitter.on(event.OPEN_VERSION_SELECT_DIALOG, (config: { file: string; callback: (file: string, version: string) => void }) => {
            const file = config.file;
            this.callback = config.callback;
            this.file = file;
            action.loadFileVersions(file, function (data: TreeNodeData[]) {
                this.setState({ data, title: "选择文件[" + file + "]的版本", visible: true });
            }.bind(this));
        });
        event.eventEmitter.on(event.HIDE_VERSION_SELECT_DIALOG, () => {
            this.setState({ visible: false });
        });
    }

    componentWillUnmount(): void {
        event.eventEmitter.removeAllListeners(event.OPEN_VERSION_SELECT_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_VERSION_SELECT_DIALOG);
    }

    render() {
        const headers = [
            { id: 'v-name', name: 'name', label: '版本名称', filterable: true, width: '100px' },
            { id: 'v-comment', name: 'comment', label: '版本描述' },
            { id: 'v-createUser', name: 'createUser', label: '创建人', filterable: true, width: '140px' },
            { id: 'v-createDate', name: 'createDate', label: '创建日期', width: '140px', dateFormat: 'yyyy-MM-dd HH:mm:ss' }
        ];

        const operationConfig = {
            width: '75px',
            operations: [
                {
                    label: '选择该版本',
                    icon: 'rf rf-select',
                    style: { fontSize: '18px', color: 'var(--rf-primary)', padding: '0px 4px', cursor: 'pointer' },
                    click: function (_rowIndex: number, rowData: TreeNodeData): void {
                        this.callback!(this.file, rowData.name);
                        event.eventEmitter.emit(event.HIDE_VERSION_SELECT_DIALOG);
                        event.eventEmitter.emit(event.HIDE_KNOWLEDGE_TREE_DIALOG);
                    }.bind(this)
                }
            ]
        };

        const body = (
            <Grid headers={headers} operationConfig={operationConfig} rows={this.state.data || []} />
        );
        const buttons = [];
        return (
            <CommonDialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons} large={true} onClose={() => this.setState({ visible: false })} />
        );
    }
}
