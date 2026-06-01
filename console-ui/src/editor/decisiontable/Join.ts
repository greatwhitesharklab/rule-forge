/**
 * Join node (AND/OR) in the RETE condition tree.
 *
 * Previously `ruleforge.Join`.
 */

import { Connection } from './Connection.js';

// Context.js is still JS; declares its class on global `ruleforge`
interface ContextLike {
    getCanvas: () => HTMLElement;
    getPaper: () => PaperLike;
    setRootJoin: (join: Join) => void;
    getTotalChildrenCount: () => number;
}

interface PaperLike {
    path: (pathStr: string) => PathElement;
}

interface PathElement {
    attr: (attrs: Record<string, string> | [string, string]) => void;
    remove: () => void;
}

export class Join {
    type: string = 'and';
    private context: ContextLike;
    private H: number = 30;
    private W: number = 60;
    children: Connection[] = [];
    private joinContainer: HTMLElement;
    private joinLabel: HTMLElement;
    parentConnection: Connection | null = null;
    parent: Join | null = null;
    menu: MenuInstanceLike | null = null;

    constructor(context: ContextLike) {
        this.context = context;

        this.joinContainer = document.createElement('span');
        this.joinContainer.className = 'btn btn-default dropdown-toggle';
        this.joinContainer.style.border = 'solid gray 1px';
        this.joinContainer.style.padding = '3px';
        this.joinContainer.style.background = '#fff';

        this.joinLabel = document.createElement('span');
        this.joinLabel.textContent = '并且';
        this.joinContainer.appendChild(this.joinLabel);
    }

    initData(data: Record<string, unknown>): void {
        let conditions: Record<string, unknown>[] = [];
        const criterions = data['conditions'] as Record<string, unknown>[] | undefined;
        const joints = data['joints'] as Record<string, unknown>[] | undefined;
        this.setType(data['type'] as string);
        if (criterions) {
            conditions = criterions;
        }
        if (joints) {
            conditions = conditions.concat(joints);
        }
        if (conditions.length === 0) {
            return;
        }
        for (let i = 0; i < conditions.length; i++) {
            const criterion = conditions[i];
            const junctionType = criterion['type'] as string | undefined;
            const isJoin = !!junctionType;
            const newConnection = this.addItem(isJoin);
            if (isJoin) {
                newConnection.getJoin()!.initData(criterion);
            } else {
                newConnection.getCondition()!.initData(criterion);
            }
        }
    }

    setType(type: string): void {
        this.type = type;
        if (type === 'or') {
            this.joinLabel.textContent = '或者';
        } else {
            this.joinLabel.textContent = '并且';
        }
        window._setDirty?.();
    }

    init(parentConnection: Connection | null): void {
        if (parentConnection) {
            this.parentConnection = parentConnection;
            this.parent = parentConnection.getParentJoin();
        }
        const joinArrow = document.createElement('i');
        joinArrow.className = 'glyphicon glyphicon-chevron-down rule-join-node';
        const self = this;
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '并且',
                onClick: function () {
                    self.setType('and');
                }
            }, {
                label: '或者',
                onClick: function () {
                    self.setType('or');
                }
            }, {
                label: '添加条件',
                onClick: function () {
                    self.addItem(false);
                }
            }]
        } as MenuConfig);
        this.joinContainer.addEventListener('click', function (e) {
            self.menu!.show(e);
        });
        this.joinContainer.appendChild(joinArrow);
    }

    clean(): void {
        while (this.children.length > 0) {
            const connection = this.children[0];
            this.removeConnection(connection);
        }
    }

    removeConnection(connection: Connection): void {
        const pos = this.children.indexOf(connection);
        if (this.children.length > 1) {
            this.resetItemPosition(pos + 1, false);
        }
        connection.remove();
        this.children.splice(pos, 1);
        this.resetContainerSize();
        window._setDirty?.();
    }

    addItem(isJoin: boolean): Connection {
        window._setDirty?.();
        const childrenCount = this.getChildrenCount();
        if (childrenCount > 0 && this.parent) {
            const parentChildren = this.parent.getChildren();
            const pos = parentChildren.indexOf(this.parentConnection!);
            this.parent.resetItemPosition(pos + 1, true);
        }
        const totalHeight = childrenCount * this.H;
        const parentLeft = parseInt(this.joinContainer.style.left) || 0;
        const parentTop = parseInt(this.joinContainer.style.top) || 0;
        const startX = parentLeft + this.W / 2;
        const startY = parentTop + this.H / 5;
        let endX = startX + this.W - 25;
        let endY = startY + totalHeight;
        if (isJoin) {
            endY -= 5;
        }
        const connection = new Connection(this.context, isJoin, this);
        connection.drawPath(startX, startY, endX, endY);
        this.children.push(connection);
        this.resetContainerSize();
        return connection;
    }

    toXml(): string {
        let xml = '<joint type="' + this.type + '">';
        for (let i = 0; i < this.children.length; i++) {
            const conn = this.children[i];
            xml += conn.toXml();
        }
        xml += '</joint>';
        return xml;
    }

    resetItemPosition(index: number, add: boolean): void {
        if (index === -1) {
            return;
        }
        for (let i = index; i < this.children.length; i++) {
            const connection = this.children[i];
            const offset = add ? this.H : -this.H;
            connection.setEndY(connection.getEndY() + offset);
            if (index === 0) {
                connection.setStartY(connection.getStartY() + offset);
            }
            connection.update(add);
        }
        if (index > 0 && this.parent) {
            const parentChildren = this.parent.getChildren();
            const pos = parentChildren.indexOf(this.parentConnection!);
            const parentJoin = this.parentConnection!.getParentJoin();
            parentJoin.resetItemPosition(pos + 1, add);
        }
        window._setDirty?.();
    }

    resetContainerSize(): void {
        const container = this.context.getCanvas();
        let height = parseInt(container.style.height) || 0;
        let childrenCount = this.context.getTotalChildrenCount();
        if (childrenCount === 0) childrenCount = 1;
        const totalHeight = childrenCount * this.H + 10;
        container.style.height = totalHeight + 'px';
    }

    getChildrenCount(): number {
        let total = 0;
        for (let i = 0; i < this.children.length; i++) {
            const child = this.children[i].getJoin();
            if (child) {
                let count = child.getChildrenCount();
                if (count === 0) {
                    count = 1;
                }
                total += count;
            } else {
                total++;
            }
        }
        return total;
    }

    initTopJoin(container: HTMLElement): void {
        const left = 5;
        const top = 5;
        this.joinContainer.style.position = 'absolute';
        this.joinContainer.style.left = left + 'px';
        this.joinContainer.style.top = top + 'px';
        container.appendChild(this.joinContainer);
        this.context.setRootJoin(this);
    }

    getDisplayContainer(): HTMLElement | null {
        if (this.children.length === 0) {
            return null;
        }
        const container = document.createElement('span');
        for (let i = 0; i < this.children.length; i++) {
            const child = this.children[i];
            const childDisplayContainer = child.getDisplayContainer();
            if (!childDisplayContainer) {
                continue;
            }
            if (i > 0) {
                if (this.type === 'or') {
                    const orSpan = document.createElement('span');
                    orSpan.style.cssText = 'color:green';
                    orSpan.textContent = ' 或 ';
                    container.appendChild(orSpan);
                } else {
                    const andSpan = document.createElement('span');
                    andSpan.style.cssText = 'color:red';
                    andSpan.textContent = ' 并且 ';
                    container.appendChild(andSpan);
                }
            }
            container.appendChild(childDisplayContainer);
        }
        return container;
    }

    getChildren(): Connection[] {
        return this.children;
    }

    getContainer(): HTMLElement {
        return this.joinContainer;
    }
}

// Minimal types for menu (from global.d.ts)
interface MenuConfig {
    menuItems: MenuItemConfig[];
    onShow?: () => void;
}

interface MenuItemConfig {
    name?: string;
    label: string;
    icon?: string;
    datatype?: string;
    act?: string;
    parent?: MenuItemConfig;
    subMenu?: { menuItems: MenuItemConfig[] };
    onClick?: (menuItem: MenuItemConfig) => void;
    show?: () => void;
    hide?: () => void;
}

interface MenuInstanceLike {
    show: (e: MouseEvent) => void;
    hide: () => void;
    setConfig: (config: MenuConfig) => void;
}

// Backward-compatible global assignment
(window as unknown as Record<string, unknown>).ruleforge =
    (window as unknown as Record<string, Record<string, unknown>>).ruleforge || {};
((window as unknown as Record<string, Record<string, unknown>>).ruleforge).Join = Join;
