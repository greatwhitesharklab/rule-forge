/**
 * Cell execute-method wrapper — renders an action method selector in a Handsontable cell.
 *
 * Previously `ruleforge.CellExecuteMethod`.
 */

import { generateContainer, actionTypeArray } from '../common/URule.js';

// MethodAction.js is still JS
interface MethodActionLike {
    getContainer: () => HTMLElement;
    initData: (data: Record<string, unknown>) => void;
    toXml: () => string;
}

interface ActionData {
    beanLabel: string;
    beanId: string;
    methodLabel: string;
    methodName: string;
    parameters: unknown[];
}

export class CellExecuteMethod {
    private parentContainer: HTMLElement;
    private container: HTMLElement;
    private action: MethodActionLike | null = null;
    menu: MenuInstanceLike | null = null;

    constructor(element: HTMLElement | ArrayLike<HTMLElement>) {
        this.parentContainer = (element as ArrayLike<HTMLElement>)[0] || (element as HTMLElement);
        this.parentContainer.style.height = '40px';
        this.parentContainer.style.width = '100%';
        this.container = generateContainer();
        this.container.textContent = '无';
        this.container.style.color = 'gray';
        this.parentContainer.appendChild(this.container);
        actionTypeArray.push(this as unknown as { initMenu: (data: unknown[]) => void });
        this.initMenu();
    }

    initMenu(actionLibraries?: unknown[]): void {
        const data = actionLibraries || (window as unknown as Record<string, unknown[]>)._ruleforgeEditorActionLibraries;
        const self = this;

        const onClick = function (menuItem: MenuItemConfigLike): void {
            const parent = menuItem.parent!.parent!;
            self.setAction({
                beanLabel: parent.label,
                beanId: parent.name,
                methodLabel: menuItem.label,
                methodName: menuItem.name!,
                parameters: menuItem.parameters
            });
        };

        const config: MenuConfigLike = { menuItems: [] as MenuItemConfigLike[] };
        (data || []).forEach(function (item: any) {
            const springBeans: any[] = item.springBeans || [];
            springBeans.forEach(function (springBean: any) {
                const menuItem: MenuItemConfigLike = {
                    name: springBean.id,
                    label: springBean.name
                };
                const methods: any[] = springBean.methods || [];
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
            self.menu = new RuleForge.menu.Menu(config as any);
        }

        this.container.addEventListener('click', function (e) {
            self.menu!.show(e);
        });
    }

    clean(): void {
        window._setDirty?.();
        if (this.action) {
            this.action.getContainer().remove();
        }
        this.container.textContent = '无';
        this.container.style.color = 'gray';
        this.action = null;
    }

    setAction(data: ActionData): void {
        window._setDirty?.();
        if (this.action) {
            this.action.getContainer().remove();
        }
        this.action = new ((window as unknown as { ruleforge: { MethodAction: new () => MethodActionLike } }).ruleforge).MethodAction();
        this.container.textContent = '.';
        this.container.style.color = 'white';
        this.parentContainer.appendChild(this.action.getContainer());
        this.action.initData(data as unknown as Record<string, unknown>);
    }

    toXml(): string {
        if (this.action) {
            return this.action.toXml();
        }
        return '';
    }
}

// Minimal menu types (from global.d.ts)
interface MenuConfigLike {
    menuItems: MenuItemConfigLike[];
    onShow?: () => void;
}

interface MenuItemConfigLike {
    name?: string;
    label: string;
    icon?: string;
    parent?: MenuItemConfigLike;
    subMenu?: { menuItems: MenuItemConfigLike[] };
    parameters?: unknown[];
    onClick?: (menuItem: MenuItemConfigLike) => void;
}

interface MenuInstanceLike {
    show: (e: MouseEvent) => void;
    hide: () => void;
    setConfig: (config: MenuConfigLike) => void;
}

// Backward-compatible global assignment
(window as unknown as Record<string, unknown>).ruleforge =
    (window as unknown as Record<string, Record<string, unknown>>).ruleforge || {};
((window as unknown as Record<string, Record<string, unknown>>).ruleforge).CellExecuteMethod = CellExecuteMethod;
