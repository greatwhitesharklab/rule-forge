/**
 * Condition tree node — holds a comparison operator and child nodes.
 */

import TreeNode from './TreeNode.js';

declare const ruleforge: any;

export class ConditionTreeNode extends TreeNode {
    contentContainer!: HTMLSpanElement;
    operator: any;
    inputType: any;

    constructor(parentNode: TreeNode | null) {
        super(parentNode);
        this.operator = null;
        this.inputType = null;
        this.initNode();
    }

    private initNode(): void {
        const nodeContainer = document.createElement('div');
        nodeContainer.className = 'node conditionNode';
        this.col.appendChild(nodeContainer);
        const self = this;
        const contentContainer = document.createElement('span');
        this.contentContainer = contentContainer;
        nodeContainer.appendChild(contentContainer);
        this.operator = new ruleforge.ComparisonOperator(function () {
            self.inputType = self.operator.getInputType();
            if (self.inputType) {
                contentContainer.appendChild(self.inputType.getContainer());
            }
        });
        contentContainer.appendChild(this.operator.getContainer());

        const operations = document.createElement('span');
        operations.className = 'operations';
        operations.innerHTML = "<i class='icon-ok-circle'></i>";
        nodeContainer.appendChild(operations);
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
                    if (result) self.delete();
                });
            }
        });
        const menu = new RuleForge.menu.Menu({ menuItems });
        operations.addEventListener('click', function (e) {
            menu.show(e);
        });
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
        if (this.childrenNodes.length === 0) {
            throw '条件节点下至少要有一个动作节点.';
        }
        let xml = '<condition-tree-node op="' + this.operator.getOperator() + '">';
        if (this.inputType) {
            xml += this.inputType.toXml();
        }
        this.childrenNodes.forEach(function (childNode: any) {
            xml += childNode.toXml();
        });
        xml += '</condition-tree-node>';
        return xml;
    }
}
