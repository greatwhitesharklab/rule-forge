import { ComplexArithmetic } from './ComplexArithmetic';
import { FunctionParameter } from './FunctionParameter';
import { functionValueArray, generateContainer } from './URule';

export class FunctionValue {
    arithmetic: ComplexArithmetic | null;
    container: HTMLSpanElement;
    rule: any;
    leftParn: HTMLSpanElement;
    rightParn: HTMLSpanElement;
    label: HTMLSpanElement;
    functionContainer: HTMLSpanElement;
    functionLabel: string = '';
    functionName: string = '';
    parameter!: FunctionParameter;
    firstParameter: any = null;
    menu: any;

    constructor(arithmetic: ComplexArithmetic | null, data?: any, rule?: any) {
        this.arithmetic = arithmetic;
        this.container = document.createElement('span');
        this.rule = rule;
        this.leftParn = document.createElement('span');
        this.leftParn.style.color = 'blue';
        this.leftParn.textContent = '(';
        this.rightParn = document.createElement('span');
        this.rightParn.style.color = 'blue';
        this.rightParn.textContent = ')';
        this.label = generateContainer();
        this.container.appendChild(this.label);
        this.label.style.color = '#008080';
        this.functionContainer = document.createElement('span');
        this.container.appendChild(this.functionContainer);
        this.label.textContent = '请选择函数';
        if (arithmetic) {
            this.container.appendChild(arithmetic.getContainer());
        }
        if (data) {
            this.setFunction(data);
            if (arithmetic) {
                arithmetic.initData(data['arithmetic']);
            }
        }
        functionValueArray.push(this);
        this.initMenu();
    }

    getDisplayContainer(): HTMLSpanElement {
        const container = document.createElement('span');
        container.textContent = this.functionName;
        if (this.arithmetic) {
            const dis = this.arithmetic.getDisplayContainer();
            if (dis) {
                container.appendChild(dis);
            }
        }
        return container;
    }

    toXml(): string {
        if (!this.functionLabel) {
            throw '请选择函数';
        }
        if (!this.functionName) {
            throw '请选择函数';
        }
        let xml = ' function-label="' + this.functionLabel + '"';
        xml += ' function-name="' + this.functionName + '"';
        return xml;
    }

    initMenu(functionLibraries?: unknown[]): void {
        let data: any = window._ruleforgeEditorFunctionLibraries;
        if (functionLibraries) {
            data = functionLibraries;
        }
        const self = this;
        const onClick = function (menuItem: any) {
            self.setFunction({
                parameter: menuItem.parameter,
                label: menuItem.label,
                name: menuItem.name
            });
        };
        const config: any = { menuItems: [] };
        (data || []).forEach(function (item: any) {
            config.menuItems.push({
                name: item.name,
                label: item.label,
                parameter: item.argument,
                onClick: onClick
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

    initData(data: any): void {
        if (data) {
            this.setFunction(data);
        }
    }

    setFunction(data: any): void {
        if (window._setDirty) window._setDirty();
        this.functionContainer.innerHTML = '';
        this.label.textContent = data.label;
        this.functionContainer.appendChild(this.leftParn);
        this.functionLabel = data.label;
        this.functionName = data.name;
        this.parameter = new FunctionParameter(this.rule);
        this.parameter.initData(data.parameter);
        this.functionContainer.appendChild(this.parameter.getContainer());
        this.functionContainer.appendChild(this.rightParn);
    }

    getFirstParameter(): any {
        return this.firstParameter;
    }

    getParameter(): FunctionParameter {
        return this.parameter;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).FunctionValue = FunctionValue;
