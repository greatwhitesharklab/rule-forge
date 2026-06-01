import { generateContainer } from '../common/URule';

declare const ruleforge: any;

export class NamedJoin {
    type: string;
    context: any;
    H: number = 30;
    children: any[] = [];
    container: HTMLSpanElement;
    referenceName: string | null = null;
    variableCategory: any = null;
    variableCategoryName: string | null = null;
    namedLabel: HTMLElement;
    variableCategoryLabel: HTMLElement;
    joinContainer: HTMLSpanElement;
    joinLabel: HTMLSpanElement;
    parentConnection?: any;
    parent?: any;
    menu!: MenuInstance;
    categoryMenu?: MenuInstance;

    constructor(context: any) {
        this.type = 'and';
        this.context = context;
        (window as any)._VariableValueArray.push(this);
        this.container = document.createElement('span');
        this.container.className = 'btn btn-default dropdown-toggle rule-join-container';
        this.container.style.border = 'solid 1px #2196f3';
        this.namedLabel = generateContainer();
        this.namedLabel.style.color = '#9C27B0';
        this.namedLabel.style.cursor = 'pointer';
        this.namedLabel.style.fontSize = '12px';
        this.namedLabel.textContent = '请输入引用名';
        this.container.appendChild(this.namedLabel);
        const namedEditor = document.createElement('input');
        namedEditor.type = 'text';
        namedEditor.className = 'form-control';
        namedEditor.style.width = '100px';
        namedEditor.style.height = '24px';
        this.container.appendChild(namedEditor);
        namedEditor.style.display = 'none';
        const self = this;
        this.namedLabel.addEventListener('click', function () {
            self.namedLabel.style.display = 'none';
            namedEditor.style.display = 'inline';
            namedEditor.value = self.referenceName || '';
            namedEditor.focus();
            self.resetItemPosition(0, null);
        });
        namedEditor.addEventListener('blur', function (this: HTMLInputElement) {
            if (self.referenceName && self.referenceName.length > 0) {
                self.context.deleteFromNamedMap(self.referenceName);
            }
            const value = this.value;
            if (value && value !== '') {
                self.referenceName = value;
                self.namedLabel.textContent = value + ':';
                if (self.variableCategory) {
                    self.context.putToNamedMap(self.referenceName, self.variableCategory);
                }
            } else {
                self.referenceName = null;
                self.namedLabel.textContent = '请输入引用名';
            }
            for (const refValue of self.context.rule.namedReferenceValues) {
                if (refValue) {
                    refValue.initMenu();
                }
            }
            self.namedLabel.style.display = '';
            namedEditor.style.display = 'none';
            self.resetItemPosition(0, null);
        });

        this.variableCategoryLabel = generateContainer();
        this.variableCategoryLabel.style.color = '#03A9F4';
        this.variableCategoryLabel.style.cursor = 'pointer';
        this.variableCategoryLabel.style.fontSize = '12px';
        this.variableCategoryLabel.textContent = '请选择变量对象';
        this.container.appendChild(this.variableCategoryLabel);
        this.initMenu();

        this.joinContainer = document.createElement('span');
        this.container.appendChild(this.joinContainer);
        this.joinLabel = document.createElement('span');
        this.joinLabel.style.fontSize = '11pt';
        this.joinLabel.style.color = '#FF9800';
        this.joinLabel.textContent = '并且';
        this.joinContainer.appendChild(this.joinLabel);
    }

    initMenu(variableLibraries?: any[]): void {
        let data = (window as any)._ruleforgeEditorVariableLibraries;
        if (variableLibraries) {
            data = variableLibraries;
        }
        if (!data) {
            return;
        }
        const self = this;
        const config: MenuConfig = { menuItems: [] };
        for (const categories of data) {
            for (const category of categories) {
                const menuItem: MenuItemConfig = {
                    label: category.name,
                    name: category.name,
                    onClick: function (item: MenuItemConfig) {
                        if (self.children.length > 0) {
                            window.bootbox.confirm('当前节点下已配置了条件，此操作将会清这些条件，你确定吗？', function (result: boolean) {
                                if (result) {
                                    self.variableCategory = item;
                                    self.variableCategoryName = (item as any).category ? (item as any).category.name : item.label;
                                    self.context.putToNamedMap(self.referenceName, self.variableCategory);
                                    self.variableCategoryLabel.textContent = item.label;
                                    self.resetItemPosition(0, null);
                                    for (const child of self.children) {
                                        child.remove();
                                    }
                                    self.children = [];
                                }
                            });
                        } else {
                            self.variableCategory = item;
                            self.variableCategoryName = (item as any).category ? (item as any).category.name : item.label;
                            self.context.putToNamedMap(self.referenceName, self.variableCategory);
                            self.variableCategoryLabel.textContent = item.label;
                            self.resetItemPosition(0, null);
                        }
                        for (const refValue of self.context.rule.namedReferenceValues) {
                            if (refValue) {
                                refValue.initMenu();
                            }
                        }
                    }
                };
                (menuItem as any).category = category;
                config.menuItems.push(menuItem);
            }
        }
        if (self.categoryMenu) {
            self.categoryMenu.setConfig(config);
        } else {
            self.categoryMenu = new RuleForge.menu.Menu(config);
        }
        this.variableCategoryLabel.addEventListener('click', function (e: Event) {
            if (!self.referenceName) {
                window.bootbox.alert('请先输入引用名称.');
                return;
            }
            self.categoryMenu!.show(e as MouseEvent);
        });
        if (this.variableCategoryName) {
            for (const categories of data) {
                for (const category of categories) {
                    if (category.name === this.variableCategoryName) {
                        this.variableCategory = category;
                        break;
                    }
                }
                if (this.variableCategory) {
                    break;
                }
            }
            if (this.variableCategory) {
                this.context.putToNamedMap(this.referenceName, this.variableCategory);
                for (const conn of this.children) {
                    conn.getCondition().initMenu();
                }
            }
        }
        for (const refValue of self.context.rule.namedReferenceValues) {
            if (refValue) {
                refValue.initMenu();
            }
        }
    }

    initData(data: Record<string, any>): void {
        this.referenceName = data['referenceName'];
        this.variableCategoryName = data['variableCategory'];
        this.namedLabel.textContent = this.referenceName + ':';
        this.variableCategoryLabel.textContent = this.variableCategoryName!;
        const items = data['items'];
        this.setType(data['junctionType']);
        if (!items) {
            return;
        }
        for (const item of items) {
            const newConnection = this.addItem(false);
            newConnection.getCondition().initData(item);
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
                    if (self.referenceName) {
                        self.context.deleteFromNamedMap(self.referenceName);
                    }
                    for (const refValue of self.context.rule.namedReferenceValues) {
                        if (refValue) {
                            refValue.initMenu();
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
        if (!this.variableCategoryName || !this.referenceName) {
            window.bootbox.alert('请先定义变量引用名及变量对象!');
            return;
        }
        window._setDirty?.();
        const childrenCount = this.getChildrenCount();
        if (childrenCount > 0 && this.parent) {
            const parentChildren = this.parent.getChildren();
            const pos = parentChildren.indexOf(this.parentConnection);
            this.parent.resetItemPosition(pos + 1, true);
        }
        const totalHeight = childrenCount * this.H;
        const parentLeft = parseInt(this.container.style.left);
        const parentTop = parseInt(this.container.style.top);
        const startX = parentLeft + this.container.offsetWidth - 15;
        const startY = parentTop + this.H / 5;
        const endX = startX + 40;
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
        if (!this.referenceName || !this.variableCategoryName) {
            throw new Error('请定义引用条件信息.');
        }
        let xml = '<named-atom junction-type="' + this.type +
            '" reference-name="' + this.referenceName +
            '" var-category="' + this.variableCategoryName + '">';
        for (let i = 0; i < this.children.length; i++) {
            const conn = this.children[i];
            xml += conn.toXml();
        }
        xml += '</named-atom>';
        return xml;
    }

    resetItemPosition(index: number, add: boolean | null): void {
        if (index === -1) {
            return;
        }
        for (let i = index; i < this.children.length; i++) {
            const connection = this.children[i];
            if (add === null) {
                const parentLeft = parseInt(this.container.style.left);
                const startX = parentLeft + this.container.offsetWidth - 15;
                const endX = startX + 40;
                connection.setStartX(startX);
                connection.setEndX(endX);
            } else {
                let offset = this.H;
                if (!add) {
                    offset = -this.H;
                }
                connection.setEndY(connection.getEndY() + offset);
                if (index === 0) {
                    connection.setStartY(connection.getStartY() + offset);
                }
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
        return this.container;
    }
}

(ruleforge as any).NamedJoin = NamedJoin;
