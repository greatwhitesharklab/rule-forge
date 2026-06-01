declare const ruleforge: {
    RuleProperty: new (config: any, name: string, defaultValue: any, editorType: number) => {
        getContainer(): HTMLElement;
        toXml(): string;
    };
};

export default class PropertyConfig {
    container: HTMLElement;
    properties: { getContainer(): HTMLElement; toXml(): string }[] = [];
    nameEditor!: HTMLInputElement;
    propertyContainer!: HTMLElement;
    name?: string;
    menu!: MenuInstance;

    constructor(container: HTMLElement) {
        this.container = container;
        this.properties = [];
        this.init();
    }

    init(): void {
        const self = this;
        const namePropertyContainer = document.createElement('span');
        namePropertyContainer.textContent = '名称：';
        namePropertyContainer.style.cssText = 'padding: 10px';
        this.container.appendChild(namePropertyContainer);
        const nameEditor = document.createElement('input');
        nameEditor.type = 'text';
        nameEditor.className = 'form-control';
        nameEditor.style.cssText = 'display: inline-block;width:160px;height:28px';
        this.nameEditor = nameEditor;
        namePropertyContainer.appendChild(nameEditor);
        nameEditor.addEventListener('change', function () {
            self.name = (this as HTMLInputElement).value;
            window._setDirty?.();
        });

        const propertyContainer = document.createElement('span');
        propertyContainer.style.cssText = 'padding: 10px';
        this.propertyContainer = propertyContainer;
        const addProp = document.createElement('button');
        addProp.type = 'button';
        addProp.className = 'rule-add-property btn btn-link';
        addProp.textContent = '添加属性';
        this.container.appendChild(addProp);
        this.container.appendChild(propertyContainer);
        const onClick = function (menuItem: MenuItemConfig) {
            const prop = new ruleforge.RuleProperty(self, menuItem.name!, menuItem.defaultValue!, menuItem.editorType!);
            self.addProperty(prop);
        };
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: "优先级",
                name: "salience",
                defaultValue: "10",
                editorType: 1,
                onClick: onClick
            }, {
                label: "生效日期",
                name: "effective-date",
                defaultValue: "",
                editorType: 2,
                onClick: onClick
            }, {
                label: "失效日期",
                name: "expires-date",
                defaultValue: "",
                editorType: 2,
                onClick: onClick
            }, {
                label: "是否启用",
                name: "enabled",
                defaultValue: true,
                editorType: 3,
                onClick: onClick
            }, {
                label: "允许调试信息输出",
                name: "debug",
                defaultValue: true,
                editorType: 3,
                onClick: onClick
            }]
        });
        addProp.addEventListener('click', function (e) {
            self.menu.show(e);
        });
    }

    initData(data: any): void {
        this.name = data.name;
        this.nameEditor.value = data.name;
        const salience = data["salience"];
        if (salience) {
            this.addProperty(new ruleforge.RuleProperty(this, "salience", salience, 1));
        }
        const loop = data["loop"];
        if (loop != null) {
            this.addProperty(new ruleforge.RuleProperty(this, "loop", loop, 3));
        }
        const debug = data["debug"];
        if (debug != null) {
            this.addProperty(new ruleforge.RuleProperty(this, "debug", debug, 3));
        }
        const effectiveDate = data["effectiveDate"];
        if (effectiveDate) {
            this.addProperty(new ruleforge.RuleProperty(this, "effective-date", effectiveDate, 2));
        }
        const expiresDate = data["expiresDate"];
        if (expiresDate) {
            this.addProperty(new ruleforge.RuleProperty(this, "expires-date", expiresDate, 2));
        }
        const enabled = data["enabled"];
        if (enabled != null) {
            this.addProperty(new ruleforge.RuleProperty(this, "enabled", enabled, 3));
        }
    }

    addProperty(property: { getContainer(): HTMLElement; toXml(): string }): void {
        this.propertyContainer.appendChild(property.getContainer());
        this.properties.push(property);
        window._setDirty?.();
    }

    toXml(): string {
        if (!this.name || this.name.length < 1) {
            throw "评分卡名称不能为空";
        }
        let xml = " name=\"" + this.name + "\"";
        for (let i = 0; i < this.properties.length; i++) {
            const prop = this.properties[i];
            xml += " " + prop.toXml();
        }
        return xml;
    }
}
