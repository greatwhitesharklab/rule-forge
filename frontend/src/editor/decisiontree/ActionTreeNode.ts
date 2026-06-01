/**
 * Action tree node — holds one or more ActionType widgets.
 */

import TreeNode from './TreeNode.js';

declare const ruleforge: any;

export class ActionTreeNode extends TreeNode {
    nodeContainer!: HTMLDivElement;
    actionsContainer!: HTMLSpanElement;
    actionTypes: any[];

    constructor(parentNode: TreeNode | null) {
        super(parentNode);
        this.actionTypes = [];
        this.initNode();
    }

    private initNode(): void {
        this.nodeContainer = document.createElement('div');
        this.nodeContainer.className = 'node actionNode';
        this.col.appendChild(this.nodeContainer);
        this.actionsContainer = document.createElement('span');
        this.actionsContainer.style.display = 'inline-block';
        this.nodeContainer.appendChild(this.actionsContainer);
        this.addAction();
        const self = this;
        const operations = document.createElement('span');
        operations.className = 'operations';
        operations.innerHTML = "<i class='icon-ok-circle'></i>";
        this.nodeContainer.appendChild(operations);
        const menuItems: MenuItemConfig[] = [];
        menuItems.push({
            name: 'delete',
            label: '删除',
            onClick() {
                window.bootbox.confirm('真的要删除当前节点？', function (result: boolean) {
                    if (result) self.delete();
                });
            }
        });
        menuItems.push({
            name: 'addAction',
            label: '添加动作',
            onClick() {
                self.addAction(true);
            }
        });
        const menu = new RuleForge.menu.Menu({ menuItems });
        operations.addEventListener('click', function (e) {
            menu.show(e);
        });
    }

    addAction(notfirst?: boolean): any {
        const actionContainer = document.createElement('span');
        if (notfirst) {
            actionContainer.style.display = 'block';
        }
        const delIcon = document.createElement('i');
        delIcon.className = 'icon-minus-sign icon-large';
        delIcon.style.cssText = 'color: #ac2925;padding-right: 5px';
        actionContainer.appendChild(delIcon);
        this.actionsContainer.appendChild(actionContainer);
        const newActionType = new ruleforge.ActionType(actionContainer);
        this.actionTypes.push(newActionType);
        (actionContainer as any).actionType = newActionType;
        const self = this;
        delIcon.addEventListener('click', function () {
            if (self.actionTypes.length === 1) {
                RuleForge.alert('动作至少要有一个.');
                return;
            }
            let pos = -1;
            self.actionTypes.forEach(function (at: any, i: number) {
                if (at === (actionContainer as any).actionType) {
                    pos = i;
                    return false;
                }
            });
            if (pos !== -1) {
                self.actionTypes.splice(pos, 1);
                actionContainer.remove();
            } else {
                RuleForge.alert('未找到要删除的动作对象.');
            }
        });
        return newActionType;
    }

    initData(data: Record<string, any>): void {
        if (!data) {
            return;
        }
        const actions = data['actions'];
        if (!actions || actions.length === 0) {
            return;
        }
        this.actionTypes[0].parentContainer.remove();
        this.actionTypes.splice(0, 1);
        for (let i = 0; i < actions.length; i++) {
            const action = actions[i];
            const newActionType = this.addAction(i !== 0);
            newActionType.initData(action);
        }
    }

    toXml(): string {
        let xml = '<action-tree-node>';
        this.actionTypes.forEach(function (actionType: any) {
            xml += actionType.toXml();
        });
        xml += '</action-tree-node>';
        return xml;
    }
}
