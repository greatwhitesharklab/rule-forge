/**
 * ConditionNode — canvas-based condition tree node with comparison operator.
 */

import BaseNode from './BaseNode.js';

declare const ruleforge: any;

export default class ConditionNode extends BaseNode {
    contentContainer!: HTMLSpanElement;
    operator: any;
    inputType: any;

    constructor(context: any, parentNode: BaseNode | null) {
        super(context, parentNode);
        this.operator = null;
        this.inputType = null;
        this.init();
    }

    private init(): void {
        this.nodeContainer = document.createElement('div');
        this.nodeContainer.className = 'node conditionNode';
        this.context.container.appendChild(this.nodeContainer);
        this.initBindResizeEvent();
        const self = this;
        this.contentContainer = document.createElement('span');
        this.nodeContainer.appendChild(this.contentContainer);
        this.operator = new ruleforge.ComparisonOperator(function () {
            self.inputType = self.operator.getInputType();
            if (self.inputType) {
                self.contentContainer.appendChild(self.inputType.getContainer());
            }
        });
        this.contentContainer.appendChild(this.operator.getContainer());

        const operations = document.createElement('span');
        operations.className = 'operations';
        operations.innerHTML = "<i class='glyphicon glyphicon-ok-circle'></i>";
        this.nodeContainer.appendChild(operations);
        const menuItems: MenuItemConfig[] = [];
        menuItems.push({
            name: 'addCondition',
            label: '添加条件',
            onClick() {
                self.addChild('condition');
            }
        });
        menuItems.push({
            name: 'addVariable',
            label: '添加变量',
            onClick() {
                self.addChild('variable');
            }
        });
        menuItems.push({
            name: 'addAction',
            label: '添加动作',
            onClick() {
                self.addChild('action');
            }
        });
        menuItems.push({
            name: 'delete',
            label: '删除',
            onClick() {
                window.bootbox.confirm('真的要删除当前节点？', function (result: boolean) {
                    if (result) {
                        self.delete();
                    }
                });
            }
        });
        const menu = new RuleForge.menu.Menu({ menuItems });
        operations.addEventListener('click', function (e) {
            menu.show(e);
        });
        this.nodeHeight = this.nodeContainer.offsetHeight + 15;
        this.nodeWidth = this.nodeContainer.offsetWidth;
    }

    initData(data: Record<string, any>): void {
        if (!data) {
            return;
        }
        const op = data['op'];
        this.operator.setOperator(op);
        const value = data['value'];
        this.operator.initRightValue(value);
        this.inputType = this.operator.getInputType();
        if (this.inputType) {
            this.contentContainer.appendChild(this.inputType.getContainer());
        }
        super.initChildrenNodeData(data);
    }

    toXml(): string {
        if (this.children.length === 0) {
            throw '条件节点下至少要有一个动作节点.';
        }
        let xml = '<condition-tree-node op="' + this.operator.getOperator() + '">';
        if (this.inputType) {
            xml += this.inputType.toXml();
        }
        this.children.forEach(function (connection: any) {
            xml += connection.node.toXml();
        });
        xml += '</condition-tree-node>';
        return xml;
    }
}
