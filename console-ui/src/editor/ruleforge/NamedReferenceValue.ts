import { generateContainer } from '../common/URule';

declare const ruleforge: any;

export class NamedReferenceValue {
    arithmetic: any;
    container: HTMLSpanElement;
    referenceName: string | null = null;
    propertyName: string | null = null;
    propertyLabel: string | null = null;
    datatype?: string;
    rule: any;
    referenceNamelabel: HTMLElement;
    referencePropertylabel: HTMLElement;
    menu?: MenuInstance;
    propertyMenu?: MenuInstance;

    constructor(arithmetic: any, data?: Record<string, any>, rule?: any) {
        this.arithmetic = arithmetic;
        this.container = document.createElement('span');
        this.rule = rule;
        if (rule) {
            rule.namedReferenceValues.push(this);
        }
        this.referenceNamelabel = generateContainer();
        this.container.appendChild(this.referenceNamelabel);
        this.referenceNamelabel.style.color = '#9C27B0';
        this.referenceNamelabel.textContent = '请选择引用变量名';

        this.referencePropertylabel = generateContainer();
        this.referencePropertylabel.style.color = '#673AB7';
        this.container.appendChild(this.referencePropertylabel);
        this.referencePropertylabel.textContent = '请选择变量属性';

        if (arithmetic) {
            this.container.appendChild(arithmetic.getContainer());
        }
        if (data) {
            this.initData(data);
        }
        this.initMenu();
    }

    getDisplayContainer(): HTMLElement {
        const container = document.createElement('span');
        container.textContent = this.propertyName + '.' + this.propertyLabel;
        if (this.arithmetic) {
            const dis = this.arithmetic.getDisplayContainer();
            if (dis) {
                container.appendChild(dis);
            }
        }
        return container;
    }

    initMenu(): void {
        if (!this.rule) {
            return;
        }
        const self = this;
        const refNamedMenuConfig: MenuConfig = { menuItems: [] };
        for (const name of this.rule.namedMap.keys()) {
            refNamedMenuConfig.menuItems.push({
                label: name,
                name: name,
                onClick: function () {
                    self.referenceName = name;
                    self.referenceNamelabel.textContent = self.referenceName + '.';
                    const category = self.rule.namedMap.get(name) || {};
                    const variables = category.variables || [];
                    self.initPropertyMenu(variables);
                    window._setDirty?.();
                }
            });
        }
        if (this.referenceName && this.rule) {
            const category = this.rule.namedMap.get(this.referenceName) || {};
            const variables = category.variables;
            if (variables) {
                self.initPropertyMenu(variables);
            }
        }
        if (self.menu) {
            self.menu.setConfig(refNamedMenuConfig);
        } else {
            self.menu = new RuleForge.menu.Menu(refNamedMenuConfig);
        }
        this.referenceNamelabel.addEventListener('click', function (e: Event) {
            self.menu!.show(e as MouseEvent);
        });
    }

    initPropertyMenu(variables: any[]): void {
        const self = this;
        const propertyMenuConfig: MenuConfig = { menuItems: [] };
        for (const variable of variables) {
            propertyMenuConfig.menuItems.push({
                label: variable.label,
                name: variable.name,
                onClick: function () {
                    self.propertyName = variable.name;
                    self.propertyLabel = variable.label;
                    self.datatype = variable.type;
                    self.referencePropertylabel.textContent = self.propertyLabel!;
                    window._setDirty?.();
                }
            });
        }
        if (self.propertyMenu) {
            self.propertyMenu.setConfig(propertyMenuConfig);
        } else {
            self.propertyMenu = new RuleForge.menu.Menu(propertyMenuConfig);
        }
        self.referencePropertylabel.addEventListener('click', function (e: Event) {
            self.propertyMenu!.show(e as MouseEvent);
        });
    }

    setValue(data: Record<string, any>): void {
        this.referenceName = data['referenceName'];
        this.propertyName = data['propertyName'] || data['variableName'];
        this.propertyLabel = data['propertyLabel'] || data['variableLabel'];
        this.datatype = data['datatype'];
        this.referenceNamelabel.textContent = this.referenceName + '.';
        this.referencePropertylabel.textContent = this.propertyLabel!;
        window._setDirty?.();
    }

    initData(data: Record<string, any>): void {
        this.setValue(data);
        if (this.arithmetic) {
            this.arithmetic.initData(data['arithmetic']);
        }
    }

    toXml(): string {
        if (!this.referenceName || !this.propertyName || this.propertyName === '') {
            throw new Error('引用变量信息不能为空！');
        }
        const xml = 'reference-name="' + this.referenceName + '" property-name="' + this.propertyName + '"' +
            '  property-label="' + this.propertyLabel + '" datatype="' + this.datatype + '"';
        return xml;
    }

    getType(): string {
        return 'NamedReference';
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

(ruleforge as any).NamedReferenceValue = NamedReferenceValue;
