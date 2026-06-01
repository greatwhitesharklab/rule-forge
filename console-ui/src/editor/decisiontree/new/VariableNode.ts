/**
 * VariableNode — canvas-based variable tree node with ConditionLeft widget.
 */

import BaseNode from './BaseNode.js';
import { ConditionLeft } from '../ConditionLeft.js';

declare const ruleforge: any;

export default class VariableNode extends BaseNode {
    disabledDel: boolean;
    condition!: ConditionLeft;

    constructor(context: any, parentNode: BaseNode | null, disabledDel?: boolean) {
        super(context, parentNode);
        this.disabledDel = !!disabledDel;
        this.init();
    }

    private init(): void {
        this.nodeContainer = document.createElement('div');
        this.nodeContainer.className = 'node varNode';
        this.context.container.appendChild(this.nodeContainer);
        this.initBindResizeEvent();
        const contentContainer = document.createElement('span');
        this.nodeContainer.appendChild(contentContainer);
        this.condition = new ruleforge.ConditionLeft(contentContainer);
        const self = this;
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
        if (!this.disabledDel) {
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
        }
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
        const left = data['left'];
        this.condition.initData(left);
        super.initChildrenNodeData(data);
    }

    toXml(): string {
        if (this.children.length === 0) {
            throw '变量节点下至少要有一个条件节点.';
        }
        let xml = '<variable-tree-node>';
        xml += this.condition.toXml();
        this.children.forEach(function (connection: any) {
            xml += connection.node.toXml();
        });
        xml += '</variable-tree-node>';
        return xml;
    }
}
