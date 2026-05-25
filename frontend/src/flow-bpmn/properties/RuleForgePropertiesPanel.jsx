import {Component} from 'react';
import './ruleforge-properties.css';

export default class RuleForgePropertiesPanel extends Component {
    state = {element: null};

    componentDidMount() {
        if (this.props.eventBus) {
            this.props.eventBus.on('selection.changed', (e) => {
                const selection = e.newSelection;
                if (selection && selection.length === 1) {
                    this.setState({element: selection[0]});
                } else {
                    this.setState({element: null});
                }
            });
        }
    }

    getBusinessObject() {
        const el = this.state.element;
        if (!el) return null;
        return el.businessObject;
    }

    getExtensionAttr(name) {
        const bo = this.getBusinessObject();
        if (!bo) return '';
        return bo.$attrs['ruleforge:' + name] || '';
    }

    setExtensionAttr(name, value) {
        const bo = this.getBusinessObject();
        if (!bo) return;
        const modeling = this.props.modeling;
        if (!modeling) return;

        const attrs = {...bo.$attrs};
        if (value) {
            attrs['ruleforge:' + name] = value;
        } else {
            delete attrs['ruleforge:' + name];
        }
        modeling.updateProperties(this.state.element, attrs);
    }

    getTaskType() {
        return this.getExtensionAttr('taskType') || this.inferTaskType();
    }

    inferTaskType() {
        const bo = this.getBusinessObject();
        if (!bo) return '';
        if (bo.$type === 'bpmn:ScriptTask') return 'script';
        if (bo.$type === 'bpmn:ExclusiveGateway') return 'decision';
        if (bo.$type === 'bpmn:ParallelGateway') return 'parallel';
        if (bo.$type === 'bpmn:StartEvent') return 'start';
        if (bo.$type === 'bpmn:EndEvent') return 'end';
        if (bo.$type === 'bpmn:ServiceTask') {
            if (this.getExtensionAttr('bean')) return 'action';
            if (this.getExtensionAttr('packageId')) return 'package';
            return 'rule';
        }
        return '';
    }

    updateName(e) {
        const modeling = this.props.modeling;
        if (modeling && this.state.element) {
            modeling.updateProperties(this.state.element, {name: e.target.value});
        }
    }

    render() {
        const el = this.state.element;
        if (!el) {
            return <div className="rf-properties-panel">
                <div className="rf-properties-empty">选择一个元素查看属性</div>
            </div>;
        }

        const bo = this.getBusinessObject();
        const taskType = this.getTaskType();

        return (
            <div className="rf-properties-panel">
                <div className="rf-properties-title">{this.getTitle(bo, taskType)}</div>
                <div className="rf-properties-group">
                    <label>名称</label>
                    <input type="text" value={bo.name || ''}
                           onChange={(e) => this.updateName(e)}/>
                </div>
                {taskType === 'rule' && this.renderRuleProps()}
                {taskType === 'action' && this.renderActionProps()}
                {taskType === 'script' && this.renderScriptProps()}
                {taskType === 'package' && this.renderPackageProps()}
                {taskType === 'decision' && this.renderDecisionProps()}
                {this.renderEventBeanProp()}
            </div>
        );
    }

    getTitle(bo, taskType) {
        const titles = {
            start: '开始节点', end: '结束节点', decision: '决策节点',
            parallel: '并行网关', rule: '规则节点', action: '动作节点',
            script: '脚本节点', package: '知识包节点'
        };
        return titles[taskType] || bo.$type;
    }

    renderRuleProps() {
        return <>
            <div className="rf-properties-group">
                <label>规则文件</label>
                <input type="text" value={this.getExtensionAttr('file')}
                       onChange={(e) => this.setExtensionAttr('file', e.target.value)}
                       placeholder="例: project/rules/rule.xml"/>
            </div>
            <div className="rf-properties-group">
                <label>项目</label>
                <input type="text" value={this.getExtensionAttr('project')}
                       onChange={(e) => this.setExtensionAttr('project', e.target.value)}
                       placeholder="项目名称"/>
            </div>
            <div className="rf-properties-group">
                <label>版本</label>
                <input type="text" value={this.getExtensionAttr('version')}
                       onChange={(e) => this.setExtensionAttr('version', e.target.value)}
                       placeholder="留空使用最新版本"/>
            </div>
        </>;
    }

    renderActionProps() {
        return <>
            <div className="rf-properties-group">
                <label>Spring Bean</label>
                <input type="text" value={this.getExtensionAttr('bean')}
                       onChange={(e) => this.setExtensionAttr('bean', e.target.value)}
                       placeholder="Bean ID"/>
            </div>
            <div className="rf-properties-group">
                <label>方法名</label>
                <input type="text" value={this.getExtensionAttr('method')}
                       onChange={(e) => this.setExtensionAttr('method', e.target.value)}
                       placeholder="方法名称"/>
            </div>
        </>;
    }

    renderScriptProps() {
        const bo = this.getBusinessObject();
        return <div className="rf-properties-group">
            <label>脚本</label>
            <textarea value={bo.script || ''} rows={6}
                      onChange={(e) => {
                          const modeling = this.props.modeling;
                          if (modeling && this.state.element) {
                              modeling.updateProperties(this.state.element, {script: e.target.value});
                          }
                      }}
                      placeholder="脚本内容"/>
        </div>;
    }

    renderPackageProps() {
        return <div className="rf-properties-group">
            <label>知识包ID</label>
            <input type="text" value={this.getExtensionAttr('packageId')}
                   onChange={(e) => this.setExtensionAttr('packageId', e.target.value)}
                   placeholder="知识包标识"/>
        </div>;
    }

    renderDecisionProps() {
        return <div className="rf-properties-group">
            <label>决策类型</label>
            <select value={this.getExtensionAttr('decisionType') || 'condition'}
                    onChange={(e) => this.setExtensionAttr('decisionType', e.target.value)}>
                <option value="condition">条件分支</option>
                <option value="percent">百分比分配</option>
            </select>
        </div>;
    }

    renderEventBeanProp() {
        return <div className="rf-properties-group">
            <label>事件Bean</label>
            <input type="text" value={this.getExtensionAttr('eventBean')}
                   onChange={(e) => this.setExtensionAttr('eventBean', e.target.value)}
                   placeholder="事件处理Bean（可选）"/>
        </div>;
    }
}
