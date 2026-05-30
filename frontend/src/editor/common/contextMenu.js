(function () {
    if (!window.RuleForge) {
        window.RuleForge = {};
    }
    RuleForge.setDomContent = function (container, text) {
        container.textContent = text;
    };
    RuleForge.menu = {};
    RuleForge.menu.AbstractMenu = function (config) {
    };
    RuleForge.menu.AbstractMenu.prototype = {
        fadeSpeed: 100,
        above: 'auto',
        preventDoubleContext: true,
        compress: false,
        createDom: function () {

        }, getDom: function () {
            if (!this._dom) {
                this._dom = this.createDom();
                this._dom._ref = this;
            }
            return this._dom;
        }, render: function (target) {
            if (!this._rendered) {
                if (target) {
                    target.appendChild(this.getDom());
                } else {
                    document.body.appendChild(this.getDom());
                }
            }
            this._rendered = true;
        }, setConfig: function (config) {
            this.remove();
            this.constructor.call(this, config);

        }, remove: function () {
            if (this._dom) {
                this._dom.remove();
                this._dom = null;
            }
            this._rendered = false;
        }
    };

    RuleForge.menu.Menu = function (config) {
        RuleForge.menu.Menu.prototype.superClass.call(this, config);
        Object.assign(this, config);
    };

    RuleForge.menu.Menu.prototype = new RuleForge.menu.AbstractMenu();
    RuleForge.menu.Menu.prototype.superClass = RuleForge.menu.AbstractMenu;
    RuleForge.menu.Menu.prototype.constructor = RuleForge.menu.Menu;

    RuleForge.menu.Menu.prototype.createDom = function () {
        var compressed, dom, menuItems, ul;
        compressed = this.compress ? ' compressed-context' : '';
        ul = document.createElement("ul");
        ul.className = 'dropdown-menu dropdown-context' + compressed;
        ul.style.fontSize = '12px';
        dom = ul;
        this._dom = dom;
        menuItems = this.menuItems;
        var self = this;
        if (menuItems.length > 20) {
            var searchContainer = document.createElement("div");
            searchContainer.style.cssText = "margin-left: 2px;margin-right: 2px";
            searchContainer.innerHTML = "<i class='glyphicon glyphicon-filter' style='color:#006600;margin-left: 2px;margin-right: 2px'></i>  ";
            this.search = document.createElement("input");
            this.search.type = "text";
            this.search.className = "form-control";
            this.search.placeholder = "输入值后回车查询";
            this.search.style.cssText = "width: 85%;display: inline-block;height: 26px;padding: 1px;font-size: 12px;";
            searchContainer.appendChild(this.search);
            ul.appendChild(searchContainer);
            this.search.addEventListener("click", function (e) {
                e.stopPropagation();
            });
            this.search.addEventListener("dblclick", function (e) {
                e.stopPropagation();
            });
            this.search.addEventListener("keypress", function (event) {
                var keynum = (event.keyCode ? event.keyCode : event.which);
                if (keynum !== '13' && keynum !== 13) {
                    return;
                }
                var value = this.value;
                if (self.oldSearchValue && self.oldSearchValue === value) {
                    return;
                }
                self.oldSearchValue = value;
                while (self.menuItems.length > 0) {
                    self.menuItems[0].remove();
                }
                for (var i = 0; i < menuItems.length; i++) {
                    var item = menuItems[i];
                    var label = item.label;
                    if (!value || value === "") {
                        self.addMenuItem(item);
                    } else if (label && label.indexOf(value) > -1) {
                        self.addMenuItem(item);
                    }
                }
            });
        }
        this.menuItems = [];
        for (var i = 0; i < menuItems.length; i++) {
            this.addMenuItem(menuItems[i]);
        }
        return dom;
    };

    RuleForge.menu.Menu.prototype.addMenuItem = function (menuItem) {
        var item;
        if (menuItem instanceof RuleForge.menu.MenuItem) {
            item = menuItem;
        } else {
            if (menuItem.$type) {
                item = eval("(RuleForge.menu." + menuItem.$type + "(menuItem))")
            } else {
                item = new RuleForge.menu.MenuItem(menuItem);
            }
        }
        item.parent = this;
        item.render(this.getDom());
        this.menuItems.push(item);
        return item;
    };

    RuleForge.menu.Menu.prototype.getMenuItem = function (nameOrIndex) {
        var target;
        for (var i = 0; i < this.menuItems.length; i++) {
            target = this.menuItems[i];
            if (typeof nameOrIndex === "string") {
                if (target.name === nameOrIndex) {
                    return target;
                }
            } else {
                return this.menuItems[nameOrIndex];
            }
            if (target.subMenu) {
                target = target.subMenu.getMenuItem(nameOrIndex);
                if (target) {
                    return target;
                }
            }
        }
    };

    RuleForge.menu.Menu.prototype.remove = function () {
        RuleForge.menu.Menu.prototype.superClass.prototype.remove.call(this);
        if (this.parent) {
            this.parent.subMenu = null;
            this.parent.getDom().classList.remove("dropdown-submenu");
        }
    };

    RuleForge.menu.Menu.prototype.show = function (e) {
        e.preventDefault();
        e.stopPropagation();
        this.render();
        document.querySelectorAll('.modal').forEach(function(el) {
            el.removeAttribute('tabindex');
        });
        var dd = this.getDom();
        var target = e.target, z = 3;
        while (target && target !== document.body) {
            var pz = target.style.zIndex || window.getComputedStyle(target).zIndex;
            if (!isNaN(pz) && pz !== '0') {
                z = parseInt(pz) + 1;
                break;
            }
            target = target.parentElement;
        }
        dd.style.zIndex = z;
        document.querySelectorAll('.dropdown-context:not(.dropdown-context-sub)').forEach(function(el) {
            el.style.display = 'none';
        });
        if (typeof this.above == 'boolean' && this.above) {
            dd.classList.add('dropdown-context-up');
            dd.style.top = (e.pageY - 20 - dd.offsetHeight) + 'px';
            dd.style.left = (e.pageX - 13) + 'px';
            dd.style.opacity = '0';
            dd.style.display = '';
            dd.style.transition = 'opacity ' + (this.fadeSpeed / 1000) + 's';
            requestAnimationFrame(function() { dd.style.opacity = '1'; });
        } else if (typeof this.above == 'string' && this.above == 'auto') {
            dd.classList.remove('dropdown-context-up');
            var autoH = dd.offsetHeight + 12;
            if ((e.pageY + autoH) > (document.documentElement.scrollHeight + 10) && e.pageY > autoH) {
                dd.classList.add('dropdown-context-up');
                dd.style.top = (e.pageY - 20 - autoH) + 'px';
                dd.style.left = (e.pageX - 13) + 'px';
                dd.style.opacity = '0';
                dd.style.display = '';
                dd.style.transition = 'opacity ' + (this.fadeSpeed / 1000) + 's';
                requestAnimationFrame(function() { dd.style.opacity = '1'; });
            } else {
                dd.style.top = (e.pageY + 10) + 'px';
                dd.style.left = (e.pageX - 13) + 'px';
                dd.style.opacity = '0';
                dd.style.display = '';
                dd.style.transition = 'opacity ' + (this.fadeSpeed / 1000) + 's';
                requestAnimationFrame(function() { dd.style.opacity = '1'; });
            }
        }
        if (this.onShow) {
            this.onShow(this);
        }
    };

    RuleForge.menu.Menu.prototype.hide = function () {
        var dom = this._dom;
        if (dom && dom.offsetParent !== null) {
            if (this.onHide) {
                this.onHide(this);
            }
            var fadeSpeed = this.fadeSpeed;
            dom.style.transition = 'opacity ' + (fadeSpeed / 1000) + 's';
            dom.style.opacity = '0';
            setTimeout(function () {
                dom.style.display = '';
                dom.style.opacity = '';
                dom.style.transition = '';
                dom.querySelectorAll('.drop-left').forEach(function(el) {
                    el.classList.remove('drop-left');
                });
            }, fadeSpeed);
            if (this.parent) {
                this.parent.parent.hide();
            }
        }

    };

    RuleForge.menu.MenuItem = function (config) {
        RuleForge.menu.MenuItem.prototype.superClass.call(this, config);
        Object.assign(this, config);
    };

    RuleForge.menu.MenuItem.prototype = new RuleForge.menu.AbstractMenu();
    RuleForge.menu.MenuItem.prototype.superClass = RuleForge.menu.AbstractMenu;
    RuleForge.menu.MenuItem.prototype.constructor = RuleForge.menu.MenuItem;

    RuleForge.menu.MenuItem.prototype.createDom = function () {
        var li, iconAndLabel, self;
        self = this;
        li = document.createElement("li");
        li.style.cursor = "default";
        this._dom = li;
        if (this.icon) {
            iconAndLabel = "<i class='" + this.icon + "'></i> " + this.label;
        } else {
            iconAndLabel = this.label;
        }

        li.addEventListener("mouseenter", function () {
            var siblings = li.parentElement ? li.parentElement.children : [];
            for (var s = 0; s < siblings.length; s++) {
                var sibling = siblings[s];
                if (sibling !== li && sibling.classList.contains("dropdown-submenu")) {
                    var subMenus = sibling.querySelectorAll("ul.dropdown-context-sub");
                    for (var j = 0; j < subMenus.length; j++) {
                        var subMenuEl = subMenus[j];
                        var menu = subMenuEl._ref;
                        if (menu) {
                            subMenuEl.style.transition = 'opacity ' + (menu.fadeSpeed/1000) + 's';
                            subMenuEl.style.opacity = '0';
                            setTimeout(function() { subMenuEl.style.display = 'none'; }, menu.fadeSpeed);
                        }
                    }
                }
            }
        });

        if (this.type === "divider") {
            li.classList.add("divider");
            li.innerHTML = iconAndLabel;
        } else if (this.type == "header") {
            li.classList.add("nav-header");
            li.innerHTML = iconAndLabel;
        } else {
            li.innerHTML = "<a>" + iconAndLabel + "</a>";
            if (this.subMenu) {
                this.setSubMenu(this.subMenu);
            }
        }
        if (self.onClick) {
            if (this.parent && this.parent.search) {
                li.addEventListener("click", function (e) {
                    e.stopPropagation();
                });
                li.addEventListener("dblclick", function (e) {
                    self.onClick(self, {event: e});
                    e.preventDefault();
                    e.stopPropagation();
                    self.parent.hide();
                });
            } else {
                li.addEventListener("click", function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    self.onClick(self, {event: e});
                    self.parent.hide();
                });
            }
        }
        return li;
    };


    RuleForge.menu.MenuItem.prototype.setSubMenu = function (menu) {
        var dom, self;
        self = this;
        dom = self.getDom();
        if (menu instanceof RuleForge.menu.Menu) {
            self.subMenu = menu;
        } else {
            self.subMenu = new RuleForge.menu.Menu(menu);
        }
        self.subMenu.parent = this;
        dom.className = "dropdown-submenu";
        dom.addEventListener("mouseenter", function () {
            var sub = dom.querySelector(".dropdown-context-sub:first-child"),
                subWidth, subLeft, collision;
            if (sub) {
                subWidth = sub.offsetWidth;
                subLeft = sub.offsetLeft;
                collision = (subWidth + subLeft) > window.innerWidth;
                if (collision) {
                    sub.classList.add('drop-left');
                }
            }
            self.subMenu.getDom().style.display = '';
            self.subMenu.getDom().style.opacity = '1';
        });
        this.subMenu.render(dom);
        this.subMenu.getDom().classList.add("dropdown-context-sub");
        return this.subMenu;

    };

    RuleForge.menu.MenuItem.prototype.remove = function () {
        RuleForge.menu.MenuItem.prototype.superClass.prototype.remove.call(this);
        var i;
        i = this.parent.menuItems.indexOf(this);
        this.parent.menuItems.splice(i, 1);
    };

    RuleForge.menu.MenuItem.prototype.show = function () {
        this._dom.style.display = "";
    };

    RuleForge.menu.MenuItem.prototype.hide = function () {
        this._dom.style.display = "none";
    };


    document.addEventListener("dblclick", function () {
        document.querySelectorAll('.dropdown-context').forEach(function (el) {
            var menu;
            menu = el._ref;
            if (menu) menu.hide();
        });
    });

    if (RuleForge.menu.AbstractMenu.preventDoubleContext) {
        document.addEventListener('contextmenu', function (e) {
            if (e.target.closest && e.target.closest('.dropdown-context')) {
                e.preventDefault();
            }
        });
    }

})();