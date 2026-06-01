import { generateContainer } from '../common/URule';

declare const ruleforge: any;

export class NamedCondition {
    context: any;
    parentJoin: any;
    variable: any;
    container: HTMLSpanElement;
    arithmetic: any;
    label: HTMLElement;
    valueContainer: HTMLSpanElement;
    menu: MenuInstance;
    variableName?: string;
    variableLabel?: string;
    datatype?: string;
    operator?: any;
    inputType?: any;

    constructor(context: any, parentContainer: HTMLElement, parentJoin: any) {
        this.context = context;
        this.parentJoin = parentJoin;
        this.variable = null;
        this.container = document.createElement('span');
        parentContainer.appendChild(this.container);
        this.arithmetic = new ruleforge.SimpleArithmetic();

        this.label = generateContainer();
        this.container.appendChild(this.label);
        this.label.style.color = '#673AB7';
        this.label.textContent = '请选择属性';
        this.valueContainer = document.createElement('span');
        this.container.appendChild(this.valueContainer);
        this.initMenu();
    }

    private initMenu(): void {
        const self = this;
        const menuItems: MenuItemConfig[] = [];
        let variables: any[] = [];
        if (this.parentJoin.variableCategory) {
            variables = this.parentJoin.variableCategory.variables || [];
        }
        for (const variable of variables) {
            menuItems.push({
                label: variable.label,
                name: variable.name,
                variables: [variable],
                onClick: function (item: MenuItemConfig) {
                    self.variableName = variable.name;
                    self.variableLabel = variable.label;
                    self.datatype = variable.type;
                    self.label.textContent = item.label;
                    if (self.operator) {
                        self.operator.getContainer().style.display = '';
                    } else {
                        self.operator = new ruleforge.ComparisonOperator(function () {
                            self.inputType = self.operator.getInputType();
                            if (self.inputType) {
                                self.container.appendChild(self.inputType.getContainer());
                            }
                        });
                        self.container.appendChild(self.operator.getContainer());
                    }
                    window._setDirty?.();
                }
            });
        }
        this.menu = new RuleForge.menu.Menu({ menuItems });
        this.label.addEventListener('click', function (e: Event) {
            self.menu.show(e as MouseEvent);
        });
    }

    initData(data: Record<string, any>): void {
        this.variableName = data['variableName'];
        this.variableLabel = data['variableLabel'];
        this.datatype = data['datatype'];
        this.label.textContent = this.variableLabel!;
        const self = this;
        if (this.operator) {
            this.operator.getContainer().style.display = '';
        } else {
            this.operator = new ruleforge.ComparisonOperator(function () {
                self.inputType = self.operator.getInputType();
                if (self.inputType) {
                    self.container.appendChild(self.inputType.getContainer());
                }
            });
            this.container.appendChild(this.operator.getContainer());
        }
        const op = data['op'];
        this.operator.setOperator(op);
        this.operator.initRightValue(data['value']);
        this.inputType = this.operator.getInputType();
        if (this.inputType) {
            this.container.appendChild(this.inputType.getContainer());
        }
    }

    toXml(): string {
        if (!this.variableName) {
            throw new Error('请定义条件.');
        }
        let xml = '<named-criteria op="' + this.operator.getOperator() +
            '" var="' + this.variableName +
            '" var-label="' + this.variableLabel +
            '" datatype="' + this.datatype + '">';
        if (this.inputType) {
            xml += this.inputType.toXml();
        }
        xml += '</named-criteria>';
        return xml;
    }

    getVariableValue(): any {
        return this.variable;
    }

    getOperator(): any {
        return this.operator;
    }

    getInputType(): any {
        return this.inputType;
    }
}

(ruleforge as any).NamedCondition = NamedCondition;
