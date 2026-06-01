import {Component} from 'react';
import ScriptEditorPopup from './ScriptEditorPopup.jsx';
import * as componentEvent from '../../components/componentEvent.js';
import {formPost} from '../../api/client.js';
import './ruleforge-properties.css';

interface FlowItem {
    flowElement: any;
    flowBo: any;
    targetName: string;
}

interface RuleItem {
    name: string;
    file: string;
    version: string;
    eventBean: string;
}

interface PropertiesPanelProps {
    eventBus?: any;
    modeling?: any;
    elementRegistry?: any;
    commandStack?: any;
    moddle?: any;
    canvas?: any;
}

interface PropertiesPanelState {
    element: any;
    packages: any[];
    showScriptEditor: boolean;
    scriptEditorValue: string;
    scriptEditorTitle: string;
    scriptEditorLintType: string;
    scriptEditorCallback: ((val: string) => void) | null;
}

export default class RuleForgePropertiesPanel extends Component<PropertiesPanelProps, PropertiesPanelState> {
    state: PropertiesPanelState = {
        element: null,
        packages: [],
        showScriptEditor: false,
        scriptEditorValue: '',
        scriptEditorTitle: '',
        scriptEditorLintType: 'Script',
        scriptEditorCallback: null
    };

    componentDidMount() {
        const {eventBus} = this.props;
        if (eventBus) {
            eventBus.on('selection.changed', this.handleSelectionChanged);
            eventBus.on('element.changed', this.handleElementChanged);
            eventBus.on('connection.added', this.handleConnectionsChanged);
            eventBus.on('connection.removed', this.handleConnectionsChanged);
        }
    }

    componentWillUnmount() {
        const {eventBus} = this.props;
        if (eventBus) {
            eventBus.off('selection.changed', this.handleSelectionChanged);
            eventBus.off('element.changed', this.handleElementChanged);
            eventBus.off('connection.added', this.handleConnectionsChanged);
            eventBus.off('connection.removed', this.handleConnectionsChanged);
        }
    }

    handleSelectionChanged = (e: any) => {
        const sel = e.newSelection;
        if (sel && sel.length === 1) {
            this.setState({element: sel[0]}, () => this.loadPackagesIfNeeded());
        } else {
            this.setState({element: null});
        }
    };

    handleElementChanged = (e: any) => {
        if (this.state.element && e.element === this.state.element) {
            this.forceUpdate();
        }
    };

    handleConnectionsChanged = () => {
        this.forceUpdate();
    };

    loadPackagesIfNeeded() {
        const taskType = this.getTaskType();
        if (taskType === 'package') {
            formPost('/packageeditor/loadPackages', {project: window._project || ''}, {silent: true}).then((data: any) => {
                this.setState({packages: data || []});
            }).catch(() => this.setState({packages: []}));
        }
    }

    getBusinessObject(): any {
        const el = this.state.element;
        if (!el) return null;
        return el.businessObject;
    }

    getExtensionAttr(name: string): string {
        const bo = this.getBusinessObject();
        if (!bo) return '';
        if (bo[name] !== undefined && bo[name] !== null) return bo[name];
        return bo.$attrs['ruleforge:' + name] || '';
    }

    setExtensionAttr(name: string, value: string) {
        const bo = this.getBusinessObject();
        if (!bo) return;
        const {modeling} = this.props;
        if (!modeling) return;
        const props: Record<string, any> = {};
        props['ruleforge:' + name] = value || undefined;
        modeling.updateProperties(this.state.element, props);
    }

    updateName(e: React.ChangeEvent<HTMLInputElement>) {
        const {modeling} = this.props;
        if (modeling && this.state.element) {
            modeling.updateProperties(this.state.element, {name: e.target.value});
        }
    }

    getTaskType(): string {
        const bo = this.getBusinessObject();
        if (!bo) return '';
        if (bo.$type === 'bpmn:StartEvent') return 'start';
        if (bo.$type === 'bpmn:EndEvent') return 'end';
        if (bo.$type === 'bpmn:ScriptTask') return 'script';
        if (bo.$type === 'bpmn:ExclusiveGateway') return 'decision';
        if (bo.$type === 'bpmn:ParallelGateway') return this.inferParallelType();
        if (bo.$type === 'bpmn:ServiceTask') {
            const tt = this.getExtensionAttr('taskType');
            if (tt === 'rulesPackage') return 'rulesPackage';
            if (tt === 'action') return 'action';
            if (tt === 'package') return 'package';
            return 'rule';
        }
        return '';
    }

    inferParallelType(): string {
        const el = this.state.element;
        if (!el) return 'fork';
        const incoming = (el.incoming || []).length;
        return incoming > 1 ? 'join' : 'fork';
    }

    getOutgoingFlows(): FlowItem[] {
        const el = this.state.element;
        if (!el || !el.outgoing) return [];
        const {elementRegistry} = this.props;
        return el.outgoing.map((conn: any) => {
            const flowBo = conn.businessObject;
            let targetName = '';
            if (flowBo.targetRef && flowBo.targetRef.id && elementRegistry) {
                const targetEl = elementRegistry.get(flowBo.targetRef.id);
                if (targetEl && targetEl.businessObject) {
                    targetName = targetEl.businessObject.name || flowBo.targetRef.id;
                }
            }
            return {flowElement: conn, flowBo, targetName};
        });
    }

    getFlowExtensionAttr(flowBo: any, name: string): string {
        if (!flowBo) return '';
        if (flowBo[name] !== undefined && flowBo[name] !== null) return flowBo[name];
        return flowBo.$attrs['ruleforge:' + name] || '';
    }

    setFlowExtensionAttr(flowElement: any, name: string, value: string) {
        const flowBo = flowElement.businessObject;
        const props: Record<string, any> = {};
        props['ruleforge:' + name] = value || undefined;
        const {modeling} = this.props;
        if (modeling) {
            modeling.updateModdleProperties(flowElement, flowBo, props);
        }
    }

    openKnowledgeTree(callback: (file: string, version: string) => void) {
        componentEvent.eventEmitter.emit(componentEvent.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: window._project,
            callback
        });
    }

    openScriptEditor(currentValue: string, title: string, lintType: string, callback: (val: string) => void) {
        this.setState({
            showScriptEditor: true,
            scriptEditorValue: currentValue || '',
            scriptEditorTitle: title || '编辑脚本',
            scriptEditorLintType: lintType || 'Script',
            scriptEditorCallback: callback
        });
    }

    handleScriptConfirm = (value: string) => {
        if (this.state.scriptEditorCallback) {
            this.state.scriptEditorCallback(value);
        }
        this.setState({showScriptEditor: false, scriptEditorCallback: null});
    };

    handleScriptCancel = () => {
        this.setState({showScriptEditor: false, scriptEditorCallback: null});
    };

    getTitle(): string {
        const bo = this.getBusinessObject();
        const taskType = this.getTaskType();
        const titles: Record<string, string> = {
            start: '开始节点', end: '结束节点', decision: '决策节点',
            fork: '分支节点', join: '聚合节点', rule: '规则节点',
            action: '动作节点', script: '脚本节点', package: '知识包节点',
            rulesPackage: '规则包节点'
        };
        return titles[taskType] || (bo ? bo.$type : '');
    }

    renderField(label: string, value: string, onChange: (e: any) => void, placeholder?: string, type?: string) {
        return (
            <div className="rf-prop-group">
                <label>{label}</label>
                {type === 'textarea' ? (
                    <textarea value={value || ''} onChange={onChange} placeholder={placeholder || ''} rows={4}/>
                ) : (
                    <input type={type || 'text'} value={value || ''} onChange={onChange} placeholder={placeholder || ''}/>
                )}
            </div>
        );
    }

    renderFileField(label: string, value: string, onValueChange: (e: any) => void, placeholder?: string) {
        return (
            <div className="rf-prop-group">
                <label>{label}</label>
                <div className="rf-prop-file-row">
                    <input type="text" value={value || ''} onChange={onValueChange} placeholder={placeholder || ''}/>
                    <button className="btn btn-sm btn-default" title="选择文件"
                            onClick={() => this.openKnowledgeTree((file, version) => {
                                let path = 'jcr:' + file;
                                if (version !== 'LATEST') path += ':' + version;
                                onValueChange({target: {value: path}});
                            })}>
                        <i className="glyphicon glyphicon-search"/>
                    </button>
                </div>
            </div>
        );
    }

    renderNameField() {
        const bo = this.getBusinessObject();
        return this.renderField('名称', bo.name, (e: React.ChangeEvent<HTMLInputElement>) => this.updateName(e), '节点名称');
    }

    renderEventBeanField() {
        return this.renderField('事件Bean', this.getExtensionAttr('eventBean'),
            (e: React.ChangeEvent<HTMLInputElement>) => this.setExtensionAttr('eventBean', e.target.value), 'Spring Bean ID（可选）');
    }

    renderRuleProps() {
        return (
            <>
                {this.renderFileField('规则文件', this.getExtensionAttr('file'),
                    (e: React.ChangeEvent<HTMLInputElement>) => this.setExtensionAttr('file', e.target.value), '选择或输入规则文件路径')}
                {this.renderField('项目', this.getExtensionAttr('project'),
                    (e: React.ChangeEvent<HTMLInputElement>) => this.setExtensionAttr('project', e.target.value), '项目名称')}
                {this.renderField('版本', this.getExtensionAttr('version'),
                    (e: React.ChangeEvent<HTMLInputElement>) => this.setExtensionAttr('version', e.target.value), '留空使用最新版本')}
            </>
        );
    }

    renderActionProps() {
        return (
            <>
                {this.renderField('动作Bean', this.getExtensionAttr('bean'),
                    (e: React.ChangeEvent<HTMLInputElement>) => this.setExtensionAttr('bean', e.target.value), '实现FlowAction接口的Spring Bean ID')}
                {this.renderField('方法名', this.getExtensionAttr('method'),
                    (e: React.ChangeEvent<HTMLInputElement>) => this.setExtensionAttr('method', e.target.value), '方法名称')}
            </>
        );
    }

    renderScriptProps() {
        const bo = this.getBusinessObject();
        const script = bo.script || '';
        const preview = script ? script.split('\n').slice(0, 3).join('\n') + (script.split('\n').length > 3 ? '\n...' : '') : '';
        return (
            <div className="rf-prop-group">
                <label>动作脚本</label>
                {preview ? (
                    <pre className="rf-script-preview" onClick={() => this.openScriptEditor(
                        script, '编辑脚本', 'Script',
                        (val) => {
                            const {modeling} = this.props;
                            if (modeling && this.state.element) {
                                modeling.updateProperties(this.state.element, {script: val});
                            }
                        }
                    )}>{preview}</pre>
                ) : null}
                <button className="btn btn-sm btn-primary rf-script-edit-btn" onClick={() => this.openScriptEditor(
                    script, '编辑脚本', 'Script',
                    (val) => {
                        const {modeling} = this.props;
                        if (modeling && this.state.element) {
                            modeling.updateProperties(this.state.element, {script: val});
                        }
                    }
                )}>
                    <i className="glyphicon glyphicon-edit"/> {preview ? '编辑脚本' : '编写脚本'}
                </button>
            </div>
        );
    }

    renderDecisionProps() {
        const decisionType = this.getExtensionAttr('decisionType') || 'condition';
        const flows = this.getOutgoingFlows();

        return (
            <>
                <div className="rf-prop-group">
                    <label>决策类型</label>
                    <select value={decisionType} onChange={(e) => this.setExtensionAttr('decisionType', e.target.value)}>
                        <option value="condition">条件</option>
                        <option value="percent">百分比</option>
                    </select>
                </div>
                {flows.length > 0 && (
                    <div className="rf-prop-group">
                        <label>分支配置</label>
                        <table className="rf-branch-table">
                            <thead>
                                <tr>
                                    <th>目标</th>
                                    {decisionType === 'condition' ? <th>条件脚本</th> : <th>百分比(%)</th>}
                                </tr>
                            </thead>
                            <tbody>
                                {flows.map((flow, idx) => (
                                    <tr key={idx}>
                                        <td className="rf-branch-target">{flow.targetName || '(未连接)'}</td>
                                        {decisionType === 'condition' ? (
                                            <td>
                                                <div className="rf-branch-condition">
                                                    <span className="rf-branch-condition-text">
                                                        {(this.getFlowExtensionAttr(flow.flowBo, 'conditionScript') || '').substring(0, 30)}
                                                    </span>
                                                    <button className="btn btn-xs btn-default" onClick={() => {
                                                        const current = this.getFlowExtensionAttr(flow.flowBo, 'conditionScript') || '';
                                                        this.openScriptEditor(current, '编辑条件脚本', 'Script', (val) => {
                                                            this.setFlowExtensionAttr(flow.flowElement, 'conditionScript', val);
                                                        });
                                                    }}>
                                                        <i className="glyphicon glyphicon-edit"/>
                                                    </button>
                                                </div>
                                            </td>
                                        ) : (
                                            <td>
                                                <input type="number" min="0" max="100"
                                                       value={this.getFlowExtensionAttr(flow.flowBo, 'percent') || ''}
                                                       onChange={(e) => this.setFlowExtensionAttr(flow.flowElement, 'percent', e.target.value)}
                                                       placeholder="%"/>
                                            </td>
                                        )}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
                {flows.length === 0 && (
                    <div className="rf-prop-hint">请连接目标节点以配置分支条件</div>
                )}
            </>
        );
    }

    renderPackageProps() {
        return (
            <div className="rf-prop-group">
                <label>知识包</label>
                <select value={this.getExtensionAttr('packageId') || ''}
                        onChange={(e) => this.setExtensionAttr('packageId', e.target.value)}>
                    <option value="">请选择知识包</option>
                    {this.state.packages.map((pkg: any, idx: number) => (
                        <option key={idx} value={pkg.id || pkg.name}>{pkg.name || pkg.id}</option>
                    ))}
                </select>
            </div>
        );
    }

    renderRulesPackageProps() {
        let rulesList: RuleItem[] = [];
        try {
            const raw = this.getExtensionAttr('rulesList');
            rulesList = raw ? JSON.parse(raw) : [];
        } catch (_e) {
            rulesList = [];
        }

        const updateRules = (newList: RuleItem[]) => {
            this.setExtensionAttr('rulesList', JSON.stringify(newList));
        };

        return (
            <div className="rf-prop-group">
                <label>规则列表</label>
                {rulesList.length > 0 ? (
                    <table className="rf-rules-table">
                        <thead>
                            <tr>
                                <th style={{width: 30}}>序号</th>
                                <th>规则名称</th>
                                <th style={{width: 120}}>操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            {rulesList.map((rule, idx) => (
                                <tr key={idx}>
                                    <td className="rf-text-center">{idx + 1}</td>
                                    <td>{rule.name || '(未命名)'}</td>
                                    <td>
                                        <button className="btn btn-xs btn-default" title="编辑"
                                                onClick={() => this.openRuleEditDialog(rulesList, idx, updateRules)}>
                                            <i className="glyphicon glyphicon-edit"/>
                                        </button>
                                        <button className="btn btn-xs btn-default" title="删除"
                                                onClick={() => {
                                                    const newList = rulesList.filter((_, i) => i !== idx);
                                                    updateRules(newList);
                                                }}>
                                            <i className="glyphicon glyphicon-trash"/>
                                        </button>
                                        <button className="btn btn-xs btn-default" title="上移" disabled={idx === 0}
                                                onClick={() => {
                                                    if (idx === 0) return;
                                                    const newList = [...rulesList];
                                                    [newList[idx - 1], newList[idx]] = [newList[idx], newList[idx - 1]];
                                                    updateRules(newList);
                                                }}>
                                            <i className="glyphicon glyphicon-arrow-up"/>
                                        </button>
                                        <button className="btn btn-xs btn-default" title="下移" disabled={idx === rulesList.length - 1}
                                                onClick={() => {
                                                    if (idx === rulesList.length - 1) return;
                                                    const newList = [...rulesList];
                                                    [newList[idx], newList[idx + 1]] = [newList[idx + 1], newList[idx]];
                                                    updateRules(newList);
                                                }}>
                                            <i className="glyphicon glyphicon-arrow-down"/>
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                ) : (
                    <div className="rf-prop-hint">暂无规则，点击下方按钮添加</div>
                )}
                <button className="btn btn-sm btn-primary" style={{marginTop: 8, width: '100%'}}
                        onClick={() => {
                            const newList = [...rulesList, {name: '', file: '', version: '', eventBean: ''}];
                            this.openRuleEditDialog(newList, newList.length - 1, updateRules);
                        }}>
                    <i className="glyphicon glyphicon-plus"/> 添加规则
                </button>
            </div>
        );
    }

    openRuleEditDialog(rulesList: RuleItem[], idx: number, updateCallback: (list: RuleItem[]) => void) {
        const rule = rulesList[idx];
        const tempRule = {...rule};

        const container = document.createElement('div');
        container.className = 'rf-rule-edit-overlay';
        container.innerHTML = `
            <div class="rf-rule-edit-dialog">
                <div class="rf-rule-edit-header">
                    <span>编辑规则</span>
                    <button class="rf-rule-edit-close">&times;</button>
                </div>
                <div class="rf-rule-edit-body">
                    <div class="rf-prop-group">
                        <label>规则名称</label>
                        <input type="text" class="rf-rule-name" value="${tempRule.name || ''}" placeholder="规则名称"/>
                    </div>
                    <div class="rf-prop-group">
                        <label>目标规则文件</label>
                        <div class="rf-prop-file-row">
                            <input type="text" class="rf-rule-file" value="${tempRule.file || ''}" placeholder="选择或输入文件路径"/>
                            <button class="btn btn-sm btn-default rf-rule-browse" title="选择文件">
                                <i class="glyphicon glyphicon-search"/>
                            </button>
                        </div>
                    </div>
                    <div class="rf-prop-group">
                        <label>文件版本</label>
                        <input type="text" class="rf-rule-version" value="${tempRule.version || ''}" placeholder="留空使用最新版本"/>
                    </div>
                    <div class="rf-prop-group">
                        <label>事件Bean</label>
                        <input type="text" class="rf-rule-event-bean" value="${tempRule.eventBean || ''}" placeholder="Spring Bean ID（可选）"/>
                    </div>
                </div>
                <div class="rf-rule-edit-footer">
                    <button class="btn btn-sm btn-default rf-rule-cancel">取消</button>
                    <button class="btn btn-sm btn-primary rf-rule-save" style="margin-left:8px">保存</button>
                </div>
            </div>
        `;
        document.body.appendChild(container);

        const nameInput = container.querySelector('.rf-rule-name') as HTMLInputElement;
        const fileInput = container.querySelector('.rf-rule-file') as HTMLInputElement;
        const versionInput = container.querySelector('.rf-rule-version') as HTMLInputElement;
        const eventBeanInput = container.querySelector('.rf-rule-event-bean') as HTMLInputElement;

        nameInput.oninput = () => { tempRule.name = nameInput.value; };
        fileInput.oninput = () => { tempRule.file = fileInput.value; };
        versionInput.oninput = () => { tempRule.version = versionInput.value; };
        eventBeanInput.oninput = () => { tempRule.eventBean = eventBeanInput.value; };

        (container.querySelector('.rf-rule-browse')! as HTMLElement).onclick = () => {
            componentEvent.eventEmitter.emit(componentEvent.OPEN_KNOWLEDGE_TREE_DIALOG, {
                project: window._project,
                callback: (file: string, version: string) => {
                    let path = 'jcr:' + file;
                    if (version !== 'LATEST') path += ':' + version;
                    fileInput.value = path;
                    tempRule.file = path;
                    versionInput.value = version;
                    tempRule.version = version;
                }
            });
        };

        const close = () => document.body.removeChild(container);
        (container.querySelector('.rf-rule-edit-close')! as HTMLElement).onclick = close;
        (container.querySelector('.rf-rule-cancel')! as HTMLElement).onclick = close;
        container.onclick = (e) => { if (e.target === container) close(); };

        (container.querySelector('.rf-rule-save')! as HTMLElement).onclick = () => {
            rulesList[idx] = tempRule;
            updateCallback(rulesList);
            close();
        };
    }

    removeImport(imports: any[], idx: number) {
        const {canvas, modeling} = this.props;
        if (!canvas || !modeling) return;
        const rootElement = canvas.getRootElement();
        const newList = imports.filter((_: any, i: number) => i !== idx);
        modeling.updateProperties(rootElement, {'ruleforge:imports': JSON.stringify(newList)});
        this.forceUpdate();
    }

    renderImportsPanel() {
        const {canvas} = this.props;
        if (!canvas) return null;
        let imports: any[] = [];
        try {
            const rootElement = canvas.getRootElement();
            const bo = rootElement.businessObject;
            const raw = bo.imports || bo.$attrs['ruleforge:imports'] || '';
            if (typeof raw === 'string') {
                imports = raw ? JSON.parse(raw) : [];
            } else if (Array.isArray(raw)) {
                imports = raw;
            } else {
                imports = [];
            }
        } catch (_e) {
            imports = [];
        }

        const typeLabels: Record<string, string> = {
            VariableLibrary: '变量库',
            ConstantLibrary: '常量库',
            ActionLibrary: '动作库',
            ParameterLibrary: '参数库'
        };

        return (
            <div className="rf-properties-panel">
                <div className="rf-properties-title">流程属性</div>
                <div className="rf-prop-group">
                    <label>已导入的库</label>
                    {imports.length > 0 ? (
                        <table className="rf-branch-table">
                            <thead>
                                <tr>
                                    <th>类型</th>
                                    <th>路径</th>
                                    <th style={{width: 40}}></th>
                                </tr>
                            </thead>
                            <tbody>
                                {imports.map((imp: any, idx: number) => (
                                    <tr key={idx}>
                                        <td>{typeLabels[imp.type] || imp.type}</td>
                                        <td style={{fontSize: 10, maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}>
                                            {imp.path}
                                        </td>
                                        <td>
                                            <button className="btn btn-xs btn-default" title="移除"
                                                    onClick={() => this.removeImport(imports, idx)}>
                                                <i className="glyphicon glyphicon-remove"/>
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    ) : (
                        <div className="rf-prop-hint">暂无导入的库，使用工具栏按钮添加</div>
                    )}
                </div>
                <ScriptEditorPopup
                    visible={this.state.showScriptEditor}
                    value={this.state.scriptEditorValue}
                    title={this.state.scriptEditorTitle}
                    lintType={this.state.scriptEditorLintType}
                    onConfirm={this.handleScriptConfirm}
                    onCancel={this.handleScriptCancel}
                />
            </div>
        );
    }

    render() {
        const el = this.state.element;
        if (!el || !el.businessObject) {
            return this.renderImportsPanel();
        }

        const taskType = this.getTaskType();

        return (
            <div className="rf-properties-panel">
                <div className="rf-properties-title">{this.getTitle()}</div>
                {this.renderNameField()}
                {taskType === 'rule' && this.renderRuleProps()}
                {taskType === 'action' && this.renderActionProps()}
                {taskType === 'script' && this.renderScriptProps()}
                {taskType === 'decision' && this.renderDecisionProps()}
                {taskType === 'package' && this.renderPackageProps()}
                {taskType === 'rulesPackage' && this.renderRulesPackageProps()}
                {this.renderEventBeanField()}
                <ScriptEditorPopup
                    visible={this.state.showScriptEditor}
                    value={this.state.scriptEditorValue}
                    title={this.state.scriptEditorTitle}
                    lintType={this.state.scriptEditorLintType}
                    onConfirm={this.handleScriptConfirm}
                    onCancel={this.handleScriptCancel}
                />
            </div>
        );
    }
}
