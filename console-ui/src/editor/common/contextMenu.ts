/**
 * Context menu system for the RuleForge editor.
 *
 * Provides AbstractMenu, Menu, and MenuItem classes that build dropdown
 * context menus with search, sub-menus, and fade animations.
 */

// Ensure the RuleForge global namespace exists
if (!(window as any).RuleForge) {
    (window as any).RuleForge = {} as any;
}

(RuleForge as any).setDomContent = function (container: HTMLElement, text: string): void {
    container.textContent = text;
};

// ---- AbstractMenu ----

class AbstractMenu {
    fadeSpeed: number = 100;
    above: string | boolean = 'auto';
    preventDoubleContext: boolean = true;
    compress: boolean = false;
    _dom: HTMLElement | null = null;
    _rendered: boolean = false;

    createDom(): HTMLElement {
        return document.createElement('div');
    }

    getDom(): HTMLElement {
        if (!this._dom) {
            this._dom = this.createDom();
            (this._dom as any)._ref = this;
        }
        return this._dom;
    }

    render(target?: HTMLElement): void {
        if (!this._rendered) {
            if (target) {
                target.appendChild(this.getDom());
            } else {
                document.body.appendChild(this.getDom());
            }
        }
        this._rendered = true;
    }

    setConfig(config: any): void {
        this.remove();
        (this.constructor as any).call(this, config);
    }

    remove(): void {
        if (this._dom) {
            this._dom.remove();
            this._dom = null;
        }
        this._rendered = false;
    }
}

// ---- Menu ----

class Menu {
    fadeSpeed: number = 100;
    above: string | boolean = 'auto';
    preventDoubleContext: boolean = true;
    compress: boolean = false;
    menuItems: any[] = [];
    onHide?: () => void;
    onShow?: (menu: any) => void;
    search?: HTMLInputElement;
    oldSearchValue?: string;
    parent: any;
    _dom: HTMLElement | null = null;
    _rendered: boolean = false;

    constructor(config: any) {
        Object.assign(this, config);
    }

    createDom(): HTMLElement {
        const compressed = this.compress ? ' compressed-context' : '';
        const ul = document.createElement('ul');
        ul.className = 'dropdown-menu dropdown-context' + compressed;
        ul.style.fontSize = '12px';
        const dom = ul;
        this._dom = dom;
        const menuItems = this.menuItems;
        const self = this;

        if (menuItems.length > 20) {
            const searchContainer = document.createElement('div');
            searchContainer.style.cssText = 'margin-left: 2px;margin-right: 2px';
            searchContainer.innerHTML = "<i class='glyphicon glyphicon-filter' style='color:#006600;margin-left: 2px;margin-right: 2px'></i>  ";
            const searchInput = document.createElement('input');
            searchInput.type = 'text';
            searchInput.className = 'form-control';
            searchInput.placeholder = '输入值后回车查询';
            searchInput.style.cssText = 'width: 85%;display: inline-block;height: 26px;padding: 1px;font-size: 12px;';
            searchContainer.appendChild(searchInput);
            ul.appendChild(searchContainer);
            this.search = searchInput;
            searchInput.addEventListener('click', function (e: Event) {
                e.stopPropagation();
            });
            searchInput.addEventListener('dblclick', function (e: Event) {
                e.stopPropagation();
            });
            searchInput.addEventListener('keypress', function (event: KeyboardEvent) {
                const keynum = (event.keyCode ? event.keyCode : event.which);
                if (keynum !== 13) {
                    return;
                }
                const value = (event.target as HTMLInputElement).value;
                if (self.oldSearchValue && self.oldSearchValue === value) {
                    return;
                }
                self.oldSearchValue = value;
                while (self.menuItems.length > 0) {
                    (self.menuItems[0] as any).remove();
                }
                for (let i = 0; i < menuItems.length; i++) {
                    const item = menuItems[i];
                    const label = item.label;
                    if (!value || value === '') {
                        self.addMenuItem(item);
                    } else if (label && label.indexOf(value) > -1) {
                        self.addMenuItem(item);
                    }
                }
            });
        }
        this.menuItems = [];
        for (let i = 0; i < menuItems.length; i++) {
            this.addMenuItem(menuItems[i]);
        }
        return dom;
    }

    getDom(): HTMLElement {
        if (!this._dom) {
            this._dom = this.createDom();
            (this._dom as any)._ref = this;
        }
        return this._dom;
    }

    render(target?: HTMLElement): void {
        if (!this._rendered) {
            if (target) {
                target.appendChild(this.getDom());
            } else {
                document.body.appendChild(this.getDom());
            }
        }
        this._rendered = true;
    }

    setConfig(config: any): void {
        this.remove();
        Object.assign(this, config);
    }

    remove(): void {
        if (this._dom) {
            this._dom.remove();
            this._dom = null;
        }
        this._rendered = false;
        if ((this as any).parent) {
            (this as any).parent.subMenu = null;
            (this as any).parent.getDom().classList.remove('dropdown-submenu');
        }
    }

    addMenuItem(menuItem: any): any {
        let item: any;
        if (menuItem instanceof MenuItem) {
            item = menuItem;
        } else {
            if (menuItem.$type) {
                item = eval('(new ' + menuItem.$type + '(menuItem))');
            } else {
                item = new MenuItem(menuItem);
            }
        }
        item.parent = this;
        item.render(this.getDom());
        this.menuItems.push(item);
        return item;
    }

    getMenuItem(nameOrIndex: string | number): any {
        let target: any;
        for (let i = 0; i < this.menuItems.length; i++) {
            target = this.menuItems[i];
            if (typeof nameOrIndex === 'string') {
                if (target.name === nameOrIndex) {
                    return target;
                }
            } else {
                return this.menuItems[nameOrIndex as number];
            }
            if (target.subMenu) {
                target = target.subMenu.getMenuItem(nameOrIndex);
                if (target) {
                    return target;
                }
            }
        }
    }

    show(e: Event): void {
        e.preventDefault();
        e.stopPropagation();
        this.render();
        document.querySelectorAll('.modal').forEach(function (el) {
            el.removeAttribute('tabindex');
        });
        const dd = this.getDom();
        const mouseEvent = e as MouseEvent;
        let target = e.target as HTMLElement | null;
        let z = 3;
        while (target && target !== document.body) {
            const pz = target.style.zIndex || window.getComputedStyle(target).zIndex;
            if (!isNaN(pz as any) && pz !== '0') {
                z = parseInt(pz) + 1;
                break;
            }
            target = target.parentElement;
        }
        dd.style.zIndex = z.toString();
        document.querySelectorAll('.dropdown-context:not(.dropdown-context-sub)').forEach(function (el) {
            (el as HTMLElement).style.display = 'none';
        });
        if (typeof this.above === 'boolean' && this.above) {
            dd.classList.add('dropdown-context-up');
            dd.style.top = (mouseEvent.pageY - 20 - dd.offsetHeight) + 'px';
            dd.style.left = (mouseEvent.pageX - 13) + 'px';
            dd.style.opacity = '0';
            dd.style.display = '';
            dd.style.transition = 'opacity ' + (this.fadeSpeed / 1000) + 's';
            requestAnimationFrame(function () { dd.style.opacity = '1'; });
        } else if (typeof this.above === 'string' && this.above === 'auto') {
            dd.classList.remove('dropdown-context-up');
            const autoH = dd.offsetHeight + 12;
            if ((mouseEvent.pageY + autoH) > (document.documentElement.scrollHeight + 10) && mouseEvent.pageY > autoH) {
                dd.classList.add('dropdown-context-up');
                dd.style.top = (mouseEvent.pageY - 20 - autoH) + 'px';
                dd.style.left = (mouseEvent.pageX - 13) + 'px';
                dd.style.opacity = '0';
                dd.style.display = '';
                dd.style.transition = 'opacity ' + (this.fadeSpeed / 1000) + 's';
                requestAnimationFrame(function () { dd.style.opacity = '1'; });
            } else {
                dd.style.top = (mouseEvent.pageY + 10) + 'px';
                dd.style.left = (mouseEvent.pageX - 13) + 'px';
                dd.style.opacity = '0';
                dd.style.display = '';
                dd.style.transition = 'opacity ' + (this.fadeSpeed / 1000) + 's';
                requestAnimationFrame(function () { dd.style.opacity = '1'; });
            }
        }
        if (this.onShow) {
            this.onShow(this);
        }
    }

    hide(): void {
        const dom = this._dom;
        if (dom && dom.offsetParent !== null) {
            if (this.onHide) {
                this.onHide();
            }
            const fadeSpeed = this.fadeSpeed;
            dom.style.transition = 'opacity ' + (fadeSpeed / 1000) + 's';
            dom.style.opacity = '0';
            setTimeout(function () {
                dom.style.display = '';
                dom.style.opacity = '';
                dom.style.transition = '';
                dom.querySelectorAll('.drop-left').forEach(function (el) {
                    el.classList.remove('drop-left');
                });
            }, fadeSpeed);
            if ((this as any).parent) {
                (this as any).parent.parent.hide();
            }
        }
    }
}

// ---- MenuItem ----

class MenuItem {
    fadeSpeed: number = 100;
    above: string | boolean = 'auto';
    preventDoubleContext: boolean = true;
    compress: boolean = false;
    name?: string;
    label: string = '';
    icon?: string;
    type?: string;
    subMenu?: any;
    onClick?: (item: any, opts?: any) => void;
    parent: any;
    parameters?: any[];
    _dom: HTMLElement | null = null;
    _rendered: boolean = false;
    search?: HTMLInputElement;

    constructor(config: any) {
        Object.assign(this, config);
    }

    createDom(): HTMLElement {
        const self = this;
        const li = document.createElement('li');
        li.style.cursor = 'default';
        this._dom = li;
        let iconAndLabel: string;
        if (this.icon) {
            iconAndLabel = "<i class='" + this.icon + "'></i> " + this.label;
        } else {
            iconAndLabel = this.label;
        }

        li.addEventListener('mouseenter', function () {
            const siblings = li.parentElement ? li.parentElement.children : [];
            for (let s = 0; s < siblings.length; s++) {
                const sibling = siblings[s] as HTMLElement;
                if (sibling !== li && sibling.classList.contains('dropdown-submenu')) {
                    const subMenus = sibling.querySelectorAll('ul.dropdown-context-sub');
                    for (let j = 0; j < subMenus.length; j++) {
                        const subMenuEl = subMenus[j] as HTMLElement;
                        const menu = (subMenuEl as any)._ref;
                        if (menu) {
                            subMenuEl.style.transition = 'opacity ' + (menu.fadeSpeed / 1000) + 's';
                            subMenuEl.style.opacity = '0';
                            setTimeout(function () { subMenuEl.style.display = 'none'; }, menu.fadeSpeed);
                        }
                    }
                }
            }
        });

        if (this.type === 'divider') {
            li.classList.add('divider');
            li.innerHTML = iconAndLabel;
        } else if (this.type === 'header') {
            li.classList.add('nav-header');
            li.innerHTML = iconAndLabel;
        } else {
            li.innerHTML = '<a>' + iconAndLabel + '</a>';
            if (this.subMenu) {
                this.setSubMenu(this.subMenu);
            }
        }
        if (self.onClick) {
            if (this.parent && this.parent.search) {
                li.addEventListener('click', function (e: Event) {
                    e.stopPropagation();
                });
                li.addEventListener('dblclick', function (e: Event) {
                    self.onClick!(self, { event: e });
                    e.preventDefault();
                    e.stopPropagation();
                    self.parent.hide();
                });
            } else {
                li.addEventListener('click', function (e: Event) {
                    e.preventDefault();
                    e.stopPropagation();
                    self.onClick!(self, { event: e });
                    self.parent.hide();
                });
            }
        }
        return li;
    }

    getDom(): HTMLElement {
        if (!this._dom) {
            this._dom = this.createDom();
            (this._dom as any)._ref = this;
        }
        return this._dom;
    }

    render(target?: HTMLElement): void {
        if (!this._rendered) {
            if (target) {
                target.appendChild(this.getDom());
            } else {
                document.body.appendChild(this.getDom());
            }
        }
        this._rendered = true;
    }

    setSubMenu(menu: any): any {
        const self = this;
        const dom = self.getDom();
        if (menu instanceof Menu) {
            self.subMenu = menu;
        } else {
            self.subMenu = new Menu(menu);
        }
        self.subMenu.parent = this;
        dom.className = 'dropdown-submenu';
        dom.addEventListener('mouseenter', function () {
            const sub = dom.querySelector('.dropdown-context-sub:first-child') as HTMLElement | null;
            if (sub) {
                const subWidth = sub.offsetWidth;
                const subLeft = sub.offsetLeft;
                const collision = (subWidth + subLeft) > window.innerWidth;
                if (collision) {
                    sub.classList.add('drop-left');
                }
            }
            self.subMenu.getDom().style.display = '';
            self.subMenu.getDom().style.opacity = '1';
        });
        self.subMenu.render(dom);
        self.subMenu.getDom().classList.add('dropdown-context-sub');
        return self.subMenu;
    }

    remove(): void {
        if (this._dom) {
            this._dom.remove();
            this._dom = null;
        }
        this._rendered = false;
        const i = this.parent.menuItems.indexOf(this);
        this.parent.menuItems.splice(i, 1);
    }

    show(): void {
        if (this._dom) this._dom.style.display = '';
    }

    hide(): void {
        if (this._dom) this._dom.style.display = 'none';
    }
}

// Register on the RuleForge namespace
(RuleForge as any).menu = { Menu, MenuItem, AbstractMenu };

// Global event listeners for context menu behavior
document.addEventListener('dblclick', function () {
    document.querySelectorAll('.dropdown-context').forEach(function (el) {
        const menu = (el as any)._ref;
        if (menu) menu.hide();
    });
});

document.addEventListener('contextmenu', function (e) {
    if ((e.target as HTMLElement).closest && (e.target as HTMLElement).closest('.dropdown-context')) {
        e.preventDefault();
    }
});
