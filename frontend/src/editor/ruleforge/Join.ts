declare const ruleforge: any;

export class Join {
    type: string;
    context: any;
    H: number = 30;
    W: number = 60;
    children: any[] = [];
    joinContainer: HTMLSpanElement;
    joinLabel: HTMLSpanElement;
    parentConnection?: any;
    parent?: any;
    menu!: MenuInstance;

    constructor(context: any) {
        this.type = 'and';
        this.context = context;
        this.joinContainer = document.createElement('span');
        this.joinContainer.className = 'btn btn-default dropdown-toggle rule-join-container';
        this.joinLabel = document.createElement('span');
        this.joinLabel.style.fontSize = '11pt';
        this.joinLabel.textContent = '并且';
        this.joinContainer.appendChild(this.joinLabel);
    }

    initData(data: Record<string, any>): void {
        const criterions = data['criterions'];
        this.setType(data['junctionType']);
        if (!criterions) {
            return;
        }
        for (let i = 0; i < criterions.length; i++) {
            const criterion = criterions[i];
            const junctionType = criterion['junctionType'];
            let isJoin: boolean | string = false;
            if (junctionType) {
                isJoin = true;
            }
            if (criterion['variableCategory']) {
                isJoin = 'named';
            }
            const newConnection = this.addItem(isJoin);
            if (isJoin) {
                newConnection.getJoin().initData(criterion);
            } else {
                newConnection.getCondition().initData(criterion);
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

    init(parentConnection?: any): void {
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
            }, {
                label: '添加联合条件',
                onClick: function () {
                    self.addItem(true);
                }
            }, {
                label: '删除',
                onClick: function () {
                    if (self.children.length > 0) {
                        window.bootbox.alert('请先删除当前连接下子元素！');
                        return;
                    }
                    if (parentConnection) {
                        const parentJoin = parentConnection.getParentJoin();
                        if (parentJoin) {
                            parentJoin.removeConnection(parentConnection);
                        }
                    }
                }
            }]
        });
        this.joinContainer.addEventListener('click', function (e: Event) {
            self.menu.show(e as MouseEvent);
        });

        this.joinContainer.appendChild(joinArrow);
    }

    removeConnection(connection: any): void {
        const pos = this.children.indexOf(connection);
        if (this.children.length > 1) {
            this.resetItemPosition(pos + 1, false);
        }
        connection.remove();
        this.children.splice(pos, 1);
        this.resetContainerSize();
        window._setDirty?.();
    }

    addItem(isJoin: boolean | string): any {
        window._setDirty?.();
        const childrenCount = this.getChildrenCount();
        if (childrenCount > 0 && this.parent) {
            const parentChildren = this.parent.getChildren();
            const pos = parentChildren.indexOf(this.parentConnection);
            this.parent.resetItemPosition(pos + 1, true);
        }
        const totalHeight = childrenCount * this.H;
        const parentLeft = parseInt(this.joinContainer.style.left);
        const parentTop = parseInt(this.joinContainer.style.top);
        const startX = parentLeft + this.W / 2;
        const startY = parentTop + this.H / 5;
        let endX = startX + this.W - 25;
        let endY = startY + totalHeight;
        if (isJoin) {
            endY -= 5;
        }
        const connection = new ruleforge.Connection(this.context, isJoin, this);
        connection.drawPath(startX, startY, endX, endY);
        this.children.push(connection);
        this.resetContainerSize();
        return connection;
    }

    toXml(): string {
        let xml = '<' + this.type + '>';
        for (let i = 0; i < this.children.length; i++) {
            const conn = this.children[i];
            xml += conn.toXml();
        }
        xml += '</' + this.type + '>';
        return xml;
    }

    resetItemPosition(index: number, add: boolean | null): void {
        if (index === -1) {
            return;
        }
        for (let i = index; i < this.children.length; i++) {
            const connection = this.children[i];
            let offset = this.H;
            if (!add) {
                offset = -this.H;
            }
            connection.setEndY(connection.getEndY() + offset);
            if (index === 0) {
                connection.setStartY(connection.getStartY() + offset);
            }
            connection.update(add);
        }
        if (index > 0 && this.parent) {
            const parentChildren = this.parent.getChildren();
            const pos = parentChildren.indexOf(this.parentConnection);
            const parentJoin = this.parentConnection.getParentJoin();
            parentJoin.resetItemPosition(pos + 1, add);
        }
        window._setDirty?.();
    }

    resetContainerSize(): void {
        const container = this.context.getCanvas();
        let height = container.style.height;
        height = parseInt(height);
        const childrenCount = this.context.getTotalChildrenCount();
        const adjustedCount = childrenCount === 0 ? 1 : childrenCount;
        const totalHeight = adjustedCount * this.H + 10;
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

    getChildren(): any[] {
        return this.children;
    }

    getContainer(): HTMLSpanElement {
        return this.joinContainer;
    }
}

(ruleforge as any).Join = Join;
