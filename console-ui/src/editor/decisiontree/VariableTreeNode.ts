/**
 * Variable tree node — holds a ConditionLeft widget and child nodes.
 */

import TreeNode from './TreeNode.js';
import { ConditionLeft } from './ConditionLeft.js';

declare const ruleforge: any;

export class VariableTreeNode extends TreeNode {
    allowDelete: boolean;
    condition!: ConditionLeft;

    constructor(parentNode: TreeNode | null, allowDelete?: boolean) {
        super(parentNode);
        this.allowDelete = !!allowDelete;
        this.initNode();
    }

    private initNode(): void {
        const nodeContainer = document.createElement('div');
        nodeContainer.className = 'node varNode';
        this.col.appendChild(nodeContainer);
        const contentContainer = document.createElement('span');
        nodeContainer.appendChild(contentContainer);
        this.condition = new ruleforge.ConditionLeft(contentContainer);
        const self = this;
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
        if (this.allowDelete) {
            menuItems.push({
                name: 'delete',
                label: '删除',
                onClick() {
                    window.bootbox.confirm('真的要删除当前节点？', function (result: boolean) {
                        if (result) self.delete();
                    });
                }
            });
        }
        const menu = new RuleForge.menu.Menu({ menuItems });
        operations.addEventListener('click', function (e) {
            menu.show(e);
        });
    }

    initData(data: Record<string, any>): void {
        if (!data) {
            return;
        }
        const left = data['left'];
        this.condition.initData(left);
        super.initChildrenNodeData(data);
    }

    toXml(): string {
        if (this.childrenNodes.length === 0) {
            throw '变量节点下至少要有一个条件节点.';
        }
        let xml = '<variable-tree-node>';
        xml += this.condition.toXml();
        this.childrenNodes.forEach(function (childNode: any) {
            xml += childNode.toXml();
        });
        xml += '</variable-tree-node>';
        return xml;
    }
}
