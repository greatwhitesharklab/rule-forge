import {assign} from 'min-dash';

export default function RuleForgePalette(
    palette, create, elementFactory,
    spaceTool, lassoTool, handTool,
    globalConnect, translate) {

  this._create = create;
  this._elementFactory = elementFactory;
  this._translate = translate;

  palette.registerProvider(this);
}

RuleForgePalette.$inject = [
  'palette', 'create', 'elementFactory',
  'spaceTool', 'lassoTool', 'handTool',
  'globalConnect', 'translate'
];

RuleForgePalette.prototype.getPaletteEntries = function() {
  var actions = {},
      create = this._create,
      elementFactory = this._elementFactory;

  function createAction(type, group, className, title, options) {
    function createListener(event) {
      var shape = elementFactory.createShape(assign({type: type}, options));
      create.start(event, shape);
    }
    return {
      group: group,
      className: className,
      title: title,
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
      action: {click: function(event) { /* hand tool */ }}
    },
    'global-connect-tool': {
      group: 'tools',
      className: 'bpmn-icon-connection-multi',
      title: '连线工具',
      action: {click: function(event) { /* global connect */ }}
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
  });

  return actions;
};
