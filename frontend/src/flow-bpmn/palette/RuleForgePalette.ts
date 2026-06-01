import {assign} from 'min-dash';

export default function RuleForgePalette(
    palette: any, create: any, elementFactory: any,
    _spaceTool: any, _lassoTool: any, _handTool: any,
    _globalConnect: any, translate: any) {

    (this as any)._create = create;
    (this as any)._elementFactory = elementFactory;
    (this as any)._translate = translate;

    palette.registerProvider(this);
}

(RuleForgePalette as any).$inject = [
    'palette', 'create', 'elementFactory',
    'spaceTool', 'lassoTool', 'handTool',
    'globalConnect', 'translate'
];

(RuleForgePalette as any).prototype.getPaletteEntries = function() {
    const actions: Record<string, any> = {};
    const create = (this as any)._create;
    const elementFactory = (this as any)._elementFactory;

    function createAction(type: string, group: string, className: string, title: string, options?: Record<string, string>) {
        function createListener(event: any) {
            const shape = elementFactory.createShape(assign({type}, options));
            create.start(event, shape);
        }
        return {
            group,
            className,
            title,
            action: {
                dragstart: createListener,
                click: createListener
            }
        };
    }

    assign(actions, {
        'hand-tool': {
            group: 'tools',
            className: 'bpmn-icon-hand-tool',
            title: '拖拽工具',
            action: {click: function(_event: any) { /* hand tool */ }}
        },
        'global-connect-tool': {
            group: 'tools',
            className: 'bpmn-icon-connection-multi',
            title: '连线工具',
            action: {click: function(_event: any) { /* global connect */ }}
        },
        'tool-separator': {
            group: 'tools',
            separator: true
        },
        'create.start-event': createAction(
            'bpmn:StartEvent', 'event', 'bpmn-icon-start-event-none', '开始节点'
        ),
        'create.end-event': createAction(
            'bpmn:EndEvent', 'event', 'bpmn-icon-end-event-none', '结束节点'
        ),
        'create.exclusive-gateway': createAction(
            'bpmn:ExclusiveGateway', 'gateway', 'bpmn-icon-gateway-xor', '决策节点（条件分支）'
        ),
        'create.parallel-gateway-fork': createAction(
            'bpmn:ParallelGateway', 'gateway', 'bpmn-icon-gateway-parallel', '分支节点（并行）'
        ),
        'gateway-separator': {
            group: 'gateway',
            separator: true
        },
        'create.rule-task': createAction(
            'bpmn:ServiceTask', 'ruleforge', 'rf-icon-rule', '规则节点',
            {'ruleforge:taskType': 'rule'}
        ),
        'create.action-task': createAction(
            'bpmn:ServiceTask', 'ruleforge', 'rf-icon-action', '动作节点',
            {'ruleforge:taskType': 'action'}
        ),
        'create.script-task': createAction(
            'bpmn:ScriptTask', 'ruleforge', 'rf-icon-script', '脚本节点'
        ),
        'create.package-task': createAction(
            'bpmn:ServiceTask', 'ruleforge', 'rf-icon-package', '知识包节点',
            {'ruleforge:taskType': 'package'}
        ),
        'create.rules-package-task': createAction(
            'bpmn:ServiceTask', 'ruleforge', 'rf-icon-rules-package', '规则包节点',
            {'ruleforge:taskType': 'rulesPackage'}
        ),
    });

    return actions;
};
