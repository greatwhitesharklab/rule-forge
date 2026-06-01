import { Component, ReactNode } from 'react';
import CommonDialog from './CommonDialog.tsx';
import * as event from '../../componentEvent.js';
import * as action from '../../componentAction.js';
import CodeMirror from 'codemirror';
import '../../../../node_modules/codemirror/lib/codemirror.css';
import '../../../../node_modules/codemirror/theme/3024-day.css';

interface VariableItem {
    label: string;
    defaultValue?: string;
    [key: string]: unknown;
}

interface VariableCategory {
    name: string;
    variables: VariableItem[];
}

interface LogItem {
    project?: string | null;
    fileName?: string;
    version?: string;
}

interface QuickTestDialogProps {}

interface QuickTestDialogState {
    title: string;
    versionsList: Array<{ name: string; createDate: string }>;
    testRuleSets: unknown[];
    selectedVersion: string;
    orderNo: string;
    variableData: VariableCategory[];
    creditScore: string;
    showLog: boolean;
    type: 'form' | 'json';
    jsonInput: string;
    fileType: string;
    visible: boolean;
    project?: string | null;
    file?: string;
    resultData?: VariableCategory[];
    logData?: LogItem[];
}

export default class QuickTestDialog extends Component<QuickTestDialogProps, QuickTestDialogState> {
    private editor: CodeMirror.Editor | null = null;

    constructor(props: QuickTestDialogProps) {
        super(props);
        this.state = {
            title: '',
            versionsList: [],
            testRuleSets: [],
            selectedVersion: '',
            orderNo: '',
            variableData: [],
            creditScore: '',
            showLog: false,
            type: 'form',
            jsonInput: '{\n  \"key\": \"value\"\n}',
            fileType: '',
            visible: false
        };
    }

    componentDidMount(): void {
        this.editor = CodeMirror.fromTextArea(document.getElementById('json-editor') as HTMLTextAreaElement, {
            mode: "javascript",
            lineNumbers: true,
            theme: "3024-day",
            autoCloseBrackets: true,
            matchBrackets: true,
            foldGutter: true,
            gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
            indentUnit: 4,
            smartIndent: true
        });
        event.eventEmitter.on(event.OPEN_QUICK_TEST_DIALOG, (config: { file: string; project: string | null; type?: string }) => {
            const file = config.file;

            action.loadFileVersions(file, function (data: TreeNodeData[]) {
                this.setState({
                    project: config.project,
                    versionsList: data as unknown as Array<{ name: string; createDate: string }>,
                    title: "对当前文件进行测试",
                    file,
                    fileType: config.type || '',
                    testRuleSets: [],
                    selectedVersion: '',
                    orderNo: '',
                    variableData: [],
                    creditScore: '',
                    type: 'form',
                    resultData: [],
                    visible: true
                });
            }.bind(this));
        });
        event.eventEmitter.on(event.HIDE_QUICK_TEST_DIALOG, () => {
            this.setState({ visible: false });
        });
    }

    componentWillUnmount(): void {
        event.eventEmitter.removeAllListeners(event.OPEN_QUICK_TEST_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_QUICK_TEST_DIALOG);
    }

    componentDidUpdate(): void {
        if (this.editor) {
            this.editor.refresh();
        }
    }

    render() {
        const body = (
            <div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <div className="form-group" style={{ display: 'flex', alignItems: 'center' }}>
                        <label style={{ fontSize: '15px', width: '120px', color: 'var(--rf-text-primary)' }}>输入数据</label>
                        <select value={this.state.type} className="form-control" onChange={(e) => {
                            this.setState({ type: e.target.value as 'form' | 'json' });
                        }}>
                            <option value="form">表单</option>
                            <option value="json">json</option>
                        </select>
                    </div>
                    <div className="form-group" style={{ display: 'flex', alignItems: 'center' }}>
                        <input type="text"
                            className="form-control"
                            name="packageName"
                            style={{ display: this.state.type === 'form' ? 'block' : 'none' }}
                            value={this.state.orderNo} placeholder='订单号'
                            onChange={(e) => this.setState({ orderNo: e.target.value })} />
                        <button id="search" type="button" className="btn navbar-btn" style={{ marginLeft: 'var(--rf-space-3)', display: this.state.type === 'form' ? 'block' : 'none' }} onClick={() => {
                            console.log('订单号', this.state);
                            if (this.state.type === 'json') {
                                return;
                            }

                            if (!this.state.orderNo) {
                                alert('请输入订单号');
                                return;
                            }
                            const params = {
                                projectId: this.state.project,
                                appId: this.state.orderNo,
                                filePath: `jcr:${this.state.file},${this.state.selectedVersion}`,
                            };
                            action.loadVariableCategories(params, (data: VariableCategory[]) => {
                                console.log('数据源', data);
                                this.setState({
                                    variableData: data
                                });
                                this.editor!.setValue(JSON.stringify(data, null, 2));
                                this.editor!.refresh();
                            });
                        }}>查询</button>
                    </div>
                </div>
                {this.state.type === 'form' && (this.state.variableData || []).map((item, key) => (
                    <div key={key}>
                        <label>{item.name}</label>
                        <div style={{ display: 'flex', flexWrap: 'wrap', paddingLeft: 'var(--rf-space-3)' }}>
                            {((item.variables || []).map((ele, i) => (
                                <div key={i} className="form-group" style={{ marginLeft: 'var(--rf-space-3)', display: 'flex', alignItems: 'center' }}>
                                    <label style={{ minWidth: '80px', textAlign: 'right', color: 'var(--rf-text-secondary)' }}>{ele.label}</label>
                                    <input type="text" className="form-control" style={{ marginLeft: 'var(--rf-space-3)' }} value={ele.defaultValue || ''} onChange={e => this.setState({
                                        variableData: this.state.variableData.map((vd, key2) => {
                                            if (key === key2) {
                                                vd.variables[i].defaultValue = e.target.value;
                                            }
                                            return vd;
                                        })
                                    })} />
                                </div>
                            )))}
                        </div>
                    </div>
                ))}
                <div className="form-group" style={{ height: '300px', marginTop: 'var(--rf-space-3)', display: this.state.type === 'json' ? 'block' : 'none' }}>
                    <textarea id='json-editor'></textarea>
                </div>
                <div style={{ display: 'flex', alignItems: 'center' }}>
                    <label style={{ fontSize: '15px', color: 'var(--rf-text-primary)' }}>输出数据</label>
                    <button style={{
                        color: 'var(--rf-primary)',
                        marginLeft: 'var(--rf-space-3)',
                        cursor: 'pointer',
                        border: 0,
                        background: 'var(--rf-bg-container)',
                        display: this.state.showLog ? 'block' : 'none'
                    }} onClick={() => {
                        const logContent = (this.state.logData || []).map(item => `<p>》》》规则（RuleSet：${decodeURIComponent(item.fileName || '')}，${item.version}），已被添加到执行队列；</p>`);
                        console.log('logContent', logContent);
                        window.bootbox.alert({ title: '日志', message: logContent.join('') });
                    }}>查看详细日志</button>
                </div>
                {(this.state.resultData || []).map((item, key) => (
                    <div key={key}>
                        <label>{item.name}</label>
                        <div style={{ display: 'flex', flexWrap: 'wrap' }}>
                            {(item.variables || []).map((ele, ei) => (
                                <div key={ei} className="form-group" style={{ marginLeft: 'var(--rf-space-3)', display: 'flex', alignItems: 'center' }}>
                                    <label style={{ minWidth: '80px', textAlign: 'right', color: 'var(--rf-text-secondary)' }}>{ele.label}</label>
                                    <input type="text" className="form-control" style={{ marginLeft: 'var(--rf-space-3)' }} readOnly value={ele.defaultValue || ''} />
                                </div>
                            ))}
                        </div>
                    </div>
                ))}
            </div>
        );
        const htmlContent: ReactNode = (
            <div style={{ display: 'flex', alignItems: 'center' }}>
                <div className="">
                    <select className="form-control" value={this.state.selectedVersion} onChange={(e) => {
                        this.setState({ selectedVersion: e.target.value });
                        const params = {
                            projectId: this.state.project,
                            appId: this.state.orderNo,
                            filePath: `jcr:${this.state.file},${e.target.value}`,
                            ruleName: ''
                        };
                        action.loadVariableCategories(params, (data: VariableCategory[]) => {
                            console.log('数据源', data);
                            this.setState({
                                variableData: data
                            });
                            this.editor!.setValue(JSON.stringify(data, null, 2));
                            this.editor!.refresh();
                        });
                    }}>
                        <option value="">版本号</option>
                        {(this.state.versionsList || []).map(version => (
                            <option
                                key={version.createDate}
                                value={version.name}
                            >
                                {version.name}
                            </option>
                        ))}
                    </select>
                </div>
                <div className="" style={{ marginLeft: 'var(--rf-space-3)' }}>
                    <button id="testButton" type="button" className="btn btn-success navbar-btn" onClick={() => {
                        if (!this.state.selectedVersion) {
                            alert('请选择版本号');
                            return;
                        }
                        console.log('变量数据源', this.state.variableData);
                        let logData = this.state.logData || [];
                        logData.push({
                            project: this.state.project,
                            fileName: this.state.file,
                            version: this.state.selectedVersion,
                        });

                        let params: Record<string, unknown> = {
                            filePath: `jcr:${this.state.file},${this.state.selectedVersion}`,
                            data: this.state.variableData
                        };
                        if (this.state.type === 'json') {
                            try {
                                params.data = JSON.parse(this.editor!.getValue());
                            } catch (error) {
                                window.bootbox.alert('JSON格式错误，请检查输入内容');
                                return;
                            }
                        }

                        action.beginTest(params, this.state.type, (data) => {
                            this.setState({
                                showLog: true,
                                resultData: data as VariableCategory[],
                                logData
                            });
                        });
                    }}>开始测试</button>
                </div>
            </div>
        );
        const buttons = [];

        return (
            <CommonDialog visible={this.state.visible} title={this.state.title} body={body} htmlContent={htmlContent} buttons={buttons} large={true} onClose={() => this.setState({ visible: false })} />
        );
    }
}
