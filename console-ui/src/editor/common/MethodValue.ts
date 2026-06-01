import { ComplexArithmetic } from './ComplexArithmetic';
import { MethodAction } from './MethodAction';
import { actionTypeArray, generateContainer } from './URule';

export class MethodValue {
    arithmetic: ComplexArithmetic | null;
    container: HTMLSpanElement;
    rightParn: HTMLSpanElement;
    label: HTMLSpanElement;
    fetchLength: boolean = false;
    uppercase: boolean = false;
    lowercase: boolean = false;
    fetchSize: boolean = false;
    actionContainer: HTMLSpanElement;
    action!: MethodAction;
    menu: any;

    constructor(arithmetic: ComplexArithmetic | null, data?: any, parentContainer?: HTMLSpanElement) {
        this.arithmetic = arithmetic;
        this.container = document.createElement('span');
        this.rightParn = document.createElement('span');
        this.rightParn.style.color = 'blue';
        this.rightParn.textContent = ']';
        this.label = generateContainer();
        this.container.appendChild(this.label);
        this.label.style.color = 'blue';
        this.actionContainer = document.createElement('span');
        this.container.appendChild(this.actionContainer);
        this.label.textContent = '请选择方法';
        if (arithmetic) {
            this.container.appendChild(arithmetic.getContainer());
        }
        if (data) {
            this.setAction(data);
            arithmetic!.initData(data['arithmetic']);
        }
        actionTypeArray.push(this);
        this.initMenu();
    }

    initMenu(actionLibraries?: unknown[]): void {
        let data: any = window._ruleforgeEditorActionLibraries;
        if (actionLibraries) {
            data = actionLibraries;
        }
        const self = this;
        const onClick = function (menuItem: any) {
            const parent = menuItem.parent.parent;
            self.setAction({
                beanLabel: parent.label,
                beanId: parent.name,
                methodLabel: menuItem.label,
                methodName: menuItem.name,
                parameters: menuItem.parameters
            });
        };
        const config: any = { menuItems: [] };
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
        this.label.addEventListener('click', function (e: Event) {
            self.menu.show(e);
        });
    }

    setAction(data: Record<string, any>): void {
        if (window._setDirty) window._setDirty();
        if (this.action) {
            this.action.getContainer().remove();
        }
        this.action = new MethodAction();
        this.label.textContent = '[';
        this.actionContainer.appendChild(this.action.getContainer());
        this.actionContainer.appendChild(this.rightParn);
        this.action.initData(data);
    }

    getDisplayContainer(): HTMLSpanElement {
        let method = '';
        if (this.action) {
            method = this.action.methodLabel;
        }
        const container = document.createElement('span');
        container.textContent = method;
        if (this.arithmetic) {
            const dis = this.arithmetic.getDisplayContainer();
            if (dis) {
                container.appendChild(dis);
            }
        }
        return container;
    }

    toXml(): string {
        if (!this.action.bean || this.action.name === '') {
            throw '执行方法不能为空！';
        }
        let xml = 'bean-name="' + this.action.bean + '"';
        xml += ' bean-label="' + this.action.name + '"';
        xml += ' method-name="' + this.action.method + '"';
        xml += ' method-label="' + this.action.methodLabel + '"';
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).MethodValue = MethodValue;
