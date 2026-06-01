import { generateContainer, actionTypeArray } from '../common/URule';

declare const ruleforge: any;

export class ActionType {
    uuid: string;
    rule: any;
    parentContainer: HTMLElement;
    type: string;
    container: HTMLElement;
    action: any;
    menu: MenuInstance | null = null;

    constructor(parentContainer: HTMLElement, rule?: any) {
        this.uuid = Math.uuid()!;
        parentContainer.id = this.uuid;
        this.rule = rule;
        this.parentContainer = parentContainer;
        this.type = '';
        this.init();
        (window as any)._ActionTypeArray.push(this);
        this.initMenu();
    }

    toXml(): string {
        if (this.type === 'execute-function') {
            let xml = '<execute-function ';
            xml += this.action.toXml();
            xml += '>';
            xml += this.action.getParameter().toXml();
            xml += '</execute-function>';
            return xml;
        } else {
            return this.action.toXml();
        }
    }

    initData(data: Record<string, any> | undefined): void {
        if (!data) {
            return;
        }
        const actionType = data['actionType'];
        this.setAction(actionType, data);
    }

    private init(): void {
        this.container = generateContainer();
        this.container.textContent = '请选择动作类型';
        this.container.style.color = 'green';
        this.parentContainer.appendChild(this.container);
        this.action = null;
    }

    initMenu(actionLibraries?: any[]): void {
        let data = (window as any)._ruleforgeEditorActionLibraries;
        if (actionLibraries) {
            data = actionLibraries;
        }
        const self = this;
        const onClick = function (menuItem: MenuItemConfig) {
            const parent = menuItem.parent!.parent!;
            self.setAction('ExecuteMethod', {
                beanLabel: parent.label,
                beanId: parent.name,
                methodLabel: menuItem.label,
                methodName: menuItem.name,
                parameters: (menuItem as any).parameters
            });
        };
        const config: MenuConfig = {
            menuItems: [{
                label: '打印内容到控制台',
                onClick: function () {
                    self.setAction('ConsolePrint');
                }
            }, {
                label: '变量赋值',
                onClick: function () {
                    self.setAction('VariableAssign');
                }
            }, {
                label: '执行函数',
                onClick: function () {
                    self.setAction('ExecuteCommonFunction');
                }
            }]
        };
        (data || []).forEach(function (item: any) {
            const springBeans = item.springBeans || [];
            springBeans.forEach(function (springBean: any) {
                const menuItem: any = {
                    name: springBean.id,
                    label: springBean.name
                };
                const methods = springBean.methods || [];
                methods.forEach(function (method: any) {
                    if (!menuItem.subMenu) {
                        menuItem.subMenu = { menuItems: [] };
                    }
                    menuItem.subMenu.menuItems.push({
                        name: method.methodName,
                        label: method.name,
                        parameters: method.parameters,
                        onClick: onClick
                    });
                });
                config.menuItems.push(menuItem);
            });
        });
        if (self.menu) {
            self.menu.setConfig(config);
        } else {
            self.menu = new RuleForge.menu.Menu(config);
        }
        this.container.addEventListener('click', function (e: Event) {
            self.menu!.show(e as MouseEvent);
        });
    }

    private initDefaultMenuData(): Array<{ name: string; fun: () => void }> {
        const self = this;
        const menuData: Array<{ name: string; fun: () => void }> = [];
        menuData.push({
            name: '打印内容到控制台',
            fun: function () {
                self.setAction('ConsolePrint');
            }
        });
        menuData.push({
            name: '变量赋值',
            fun: function () {
                self.setAction('VariableAssign');
            }
        });
        return menuData;
    }

    setAction(type: string, data?: any): void {
        window._setDirty?.();
        if (this.action) {
            this.action.getContainer().remove();
        }
        switch (type) {
            case 'ConsolePrint':
                this.action = new ruleforge.PrintAction(this.rule);
                this.container.textContent = '输出:';
                this.type = 'console-print';
                break;
            case 'ExecuteMethod':
                this.action = new ruleforge.MethodAction(this.rule);
                this.container.textContent = '执行方法:';
                this.type = 'execute-method';
                break;
            case 'VariableAssign':
                this.action = new ruleforge.AssignmentAction(this.rule);
                this.container.textContent = '变量赋值:';
                this.type = 'var-assign';
                break;
            case 'ExecuteCommonFunction':
                this.action = new ruleforge.FunctionValue(null, null, this.rule);
                this.container.textContent = '执行函数:';
                this.type = 'execute-function';
                break;
        }
        this.parentContainer.appendChild(this.action.getContainer());
        this.action.initData(data);
    }
}

// Register on ruleforge namespace for backward compatibility
(ruleforge as any).ActionType = ActionType;
