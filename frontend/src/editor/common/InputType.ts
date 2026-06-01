import { generateContainer } from './URule';
import { ComplexArithmetic } from './ComplexArithmetic';
import { SimpleValue } from './SimpleValue';
import { ConstantValue } from './ConstantValue';
import { VariableValue } from './VariableValue';
import { ParameterValue } from './ParameterValue';
import { MethodValue } from './MethodValue';
import { FunctionValue } from './FunctionValue';

// NamedReferenceValue is not yet converted — side-effect import for global registration
import '../ruleforge/NamedReferenceValue.js';

export class InputType {
    container: HTMLSpanElement;
    label: HTMLSpanElement;
    rule: any;
    functionProperty: any;
    variableValue: VariableValue | null = null;
    simpleValue: SimpleValue | null = null;
    referenceValue: any = null;
    methodValue: MethodValue | null = null;
    constantValue: ConstantValue | null = null;
    functionValue: FunctionValue | null = null;
    parameterValue: ParameterValue | null = null;
    dataContainer: HTMLSpanElement;
    type: string = '';
    arithmetic!: ComplexArithmetic;
    menu: any;

    constructor(endInfo?: string | null, tip?: string | null, functionProperty?: any, rule?: any) {
        this.container = document.createElement('span');
        this.label = generateContainer();
        this.rule = rule;
        this.container.appendChild(this.label);
        if (tip) {
            this.label.textContent = tip;
            this.label.style.color = 'gray';
        } else {
            this.label.textContent = '选择值类型';
            this.label.style.color = 'blue';
        }
        this.functionProperty = functionProperty || null;
        this.dataContainer = document.createElement('span');
        this.container.appendChild(this.dataContainer);

        const self = this;
        const onClick = function (menuItem: any) {
            self.setValueType(menuItem.name);
        };
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '输入值',
                name: 'Input',
                onClick: onClick
            }, {
                label: '选择变量',
                name: 'Variable',
                onClick: onClick
            }, {
                label: '选择常量',
                name: 'Constant',
                onClick: onClick
            }, {
                label: '选择参数',
                name: 'Parameter',
                onClick: onClick
            }]
        });
        self.menu.menuItems.push({
            label: '选择方法',
            name: 'Method',
            onClick: onClick
        }, {
            label: '选择函数',
            name: 'CommonFunction',
            onClick: onClick
        });
        this.label.addEventListener('click', function (e: Event) {
            self.menu.show(e);
        });

        if (endInfo) {
            const endInfoContainer = document.createElement('span');
            endInfoContainer.style.cssText = 'color:red;font-size:11pt';
            endInfoContainer.innerHTML = '<strong>' + endInfo + '</strong>';
            this.container.appendChild(endInfoContainer);
        }
    }

    getDisplayContainer(): HTMLSpanElement {
        const container = document.createElement('span');
        if (this.type === 'Input') {
            container.appendChild(this.simpleValue!.getDisplayContainer());
        } else if (this.type === 'Variable' || this.type === 'VariableCategory') {
            container.appendChild(this.variableValue!.getDisplayContainer());
        } else if (this.type === 'Constant') {
            container.appendChild(this.constantValue!.getDisplayContainer());
        } else if (this.type === 'Method') {
            container.appendChild(this.methodValue!.getDisplayContainer());
        } else if (this.type === 'Parameter') {
            container.appendChild(this.parameterValue!.getDisplayContainer());
        } else if (this.type === 'CommonFunction') {
            container.appendChild(this.functionValue!.getDisplayContainer());
        } else if (this.type === 'NamedReference') {
            container.appendChild(this.referenceValue.getDisplayContainer());
        }
        return container;
    }

    setValueType(valueType: string, value?: any): void {
        if (window._setDirty) window._setDirty();
        this.type = valueType;
        if (this.variableValue) {
            this.variableValue.getContainer().style.display = 'none';
        }
        if (this.constantValue) {
            this.constantValue.getContainer().style.display = 'none';
        }
        if (this.simpleValue) {
            this.simpleValue.getContainer().style.display = 'none';
        }
        if (this.referenceValue) {
            this.referenceValue.getContainer().style.display = 'none';
        }
        if (this.methodValue) {
            this.methodValue.getContainer().style.display = 'none';
        }
        if (this.parameterValue) {
            this.parameterValue.getContainer().style.display = 'none';
        }
        if (this.functionValue) {
            this.functionValue.getContainer().style.display = 'none';
        }
        this.label.textContent = '.';
        this.label.style.color = '#FDFDFD';
        switch (valueType) {
            case 'Input':
                if (!this.simpleValue) {
                    this.arithmetic = new ComplexArithmetic(this.rule);
                    this.simpleValue = new SimpleValue(this.arithmetic, value);
                    this.dataContainer.appendChild(this.simpleValue.getContainer());
                }
                this.simpleValue.getContainer().style.display = '';
                this.type = 'Input';
                break;
            case 'NamedReference':
                if (!this.referenceValue) {
                    this.arithmetic = new ComplexArithmetic(this.rule);
                    this.referenceValue = new (ruleforge as Record<string, any>).NamedReferenceValue(this.arithmetic, value, this.rule);
                    this.dataContainer.appendChild(this.referenceValue.getContainer());
                }
                this.referenceValue.getContainer().style.display = '';
                this.type = 'NamedReference';
                break;
            case 'Constant':
                if (!this.constantValue) {
                    this.arithmetic = new ComplexArithmetic(this.rule);
                    this.constantValue = new ConstantValue(this.arithmetic, value);
                    this.dataContainer.appendChild(this.constantValue.getContainer());
                }
                this.constantValue.getContainer().style.display = '';
                this.type = 'Constant';
                break;
            case 'Method':
                if (!this.methodValue) {
                    this.arithmetic = new ComplexArithmetic(this.rule);
                    this.methodValue = new MethodValue(this.arithmetic, value, this.dataContainer);
                    this.dataContainer.appendChild(this.methodValue.getContainer());
                }
                this.methodValue.getContainer().style.display = '';
                this.type = 'Method';
                break;
            case 'Parameter':
                if (!this.parameterValue) {
                    this.arithmetic = new ComplexArithmetic(this.rule);
                    this.parameterValue = new ParameterValue(this.arithmetic, value, 'In');
                    this.dataContainer.appendChild(this.parameterValue.getContainer());
                }
                this.parameterValue.getContainer().style.display = '';
                this.type = 'Parameter';
                break;
            case 'CommonFunction':
                if (!this.functionValue) {
                    this.arithmetic = new ComplexArithmetic(this.rule);
                    this.functionValue = new FunctionValue(this.arithmetic, value, 'In');
                    this.dataContainer.appendChild(this.functionValue.getContainer());
                }
                this.functionValue.getContainer().style.display = '';
                this.type = 'CommonFunction';
                break;
            default:
                if (!this.variableValue) {
                    this.arithmetic = new ComplexArithmetic(this.rule);
                    this.variableValue = new VariableValue(this.arithmetic, value, 'In', this.functionProperty);
                    this.dataContainer.appendChild(this.variableValue.getContainer());
                }
                this.variableValue.getContainer().style.display = '';
                this.type = 'Variable';
                break;
        }
    }

    toXml(): string {
        if (this.type === '') {
            return '';
        }
        let xml = '<value ';
        if (this.type === 'Input') {
            const value = this.simpleValue!.getValue();
            if (!value || value === '') {
                throw '输入值不能为空!';
            }
            xml += ' content="' + value + '"';
        } else if (this.type === 'NamedReference') {
            xml += this.referenceValue.toXml();
        } else if (this.type === 'Variable' || this.type === 'VariableCategory') {
            xml += this.variableValue!.toXml();
            this.type = this.variableValue!.getType();
        } else if (this.type === 'Method') {
            xml += this.methodValue!.toXml();
        } else if (this.type === 'Parameter') {
            xml += this.parameterValue!.toXml();
        } else if (this.type === 'CommonFunction') {
            xml += this.functionValue!.toXml();
        } else {
            xml += this.constantValue!.toXml();
        }
        xml += ' type="' + this.type + '" ';
        xml += '>';
        xml += this.arithmetic.toXml();
        if (this.type === 'Method') {
            const parameters = this.methodValue!.action.parameters;
            for (let i = 0; i < parameters.length; i++) {
                const p = parameters[i];
                xml += p.toXml();
            }
        } else if (this.type === 'CommonFunction') {
            const parameter = this.functionValue!.getParameter();
            xml += parameter.toXml();
        }
        xml += '</value>';
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).InputType = InputType;
