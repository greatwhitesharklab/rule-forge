/**
 * ConditionLeft — left-hand side of a condition expression in the decision-tree editor.
 *
 * Allows selecting variable / parameter / method / function as the condition source.
 */

import { generateContainer } from '../common/URule.js';

declare const ruleforge: any;

export class ConditionLeft {
    container: HTMLSpanElement;
    arithmetic: any;
    label: HTMLElement;
    valueContainer: HTMLSpanElement;
    menu: MenuInstance;
    type?: string;
    variableValue?: any;
    parameterValue?: any;
    methodValue?: any;
    functionValue?: any;
    inputType?: any;
    operator?: any;

    constructor(parentContainer: HTMLElement) {
        this.container = document.createElement('span');
        parentContainer.appendChild(this.container);
        this.arithmetic = new ruleforge.SimpleArithmetic();

        this.label = generateContainer();
        this.container.appendChild(this.label);
        this.label.style.color = 'blue';
        this.label.textContent = '请选择类型';
        this.valueContainer = document.createElement('span');
        this.container.appendChild(this.valueContainer);
        this.initMenu();
    }

    private initMenu(): void {
        const self = this;
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '选择变量',
                onClick() {
                    self.type = 'variable';
                    if (self.parameterValue) {
                        self.parameterValue.getContainer().style.display = 'none';
                    }
                    if (self.functionValue) {
                        self.functionValue.getContainer().style.display = 'none';
                    }
                    if (self.methodValue) {
                        self.methodValue.getContainer().style.display = 'none';
                    }
                    if (self.variableValue) {
                        self.variableValue.getContainer().style.display = '';
                    } else {
                        self.variableValue = new ruleforge.VariableValue(self.arithmetic, null, 'In');
                        self.valueContainer.appendChild(self.variableValue.getContainer());
                    }
                    self.label.style.color = 'white';
                    self.label.textContent = '.';
                    window._setDirty!();
                }
            }, {
                label: '选择参数',
                onClick() {
                    self.type = 'parameter';
                    if (self.variableValue) {
                        self.variableValue.getContainer().style.display = 'none';
                    }
                    if (self.methodValue) {
                        self.methodValue.getContainer().style.display = 'none';
                    }
                    if (self.functionValue) {
                        self.functionValue.getContainer().style.display = 'none';
                    }
                    if (self.parameterValue) {
                        self.parameterValue.getContainer().style.display = '';
                    } else {
                        self.parameterValue = new ruleforge.ParameterValue(self.arithmetic, null, 'In');
                        self.valueContainer.appendChild(self.parameterValue.getContainer());
                    }
                    self.label.style.color = 'white';
                    self.label.textContent = '.';
                    window._setDirty!();
                }
            }, {
                label: '选择方法',
                onClick() {
                    self.type = 'method';
                    if (self.variableValue) {
                        self.variableValue.getContainer().style.display = 'none';
                    }
                    if (self.parameterValue) {
                        self.parameterValue.getContainer().style.display = 'none';
                    }
                    if (self.functionValue) {
                        self.functionValue.getContainer().style.display = 'none';
                    }
                    if (self.methodValue) {
                        self.methodValue.getContainer().style.display = '';
                    } else {
                        self.methodValue = new ruleforge.MethodValue(self.arithmetic, null);
                        self.valueContainer.appendChild(self.methodValue.getContainer());
                    }
                    self.label.style.color = 'white';
                    self.label.textContent = '.';
                    window._setDirty!();
                }
            }, {
                label: '选择函数',
                onClick() {
                    self.type = 'commonfunction';
                    if (self.variableValue) {
                        self.variableValue.getContainer().style.display = 'none';
                    }
                    if (self.parameterValue) {
                        self.parameterValue.getContainer().style.display = 'none';
                    }
                    if (self.methodValue) {
                        self.methodValue.getContainer().style.display = 'none';
                    }
                    if (self.functionValue) {
                        self.functionValue.getContainer().style.display = '';
                    } else {
                        self.functionValue = new ruleforge.FunctionValue(self.arithmetic, null, 'In');
                        self.valueContainer.appendChild(self.functionValue.getContainer());
                    }
                    self.label.style.color = 'white';
                    self.label.textContent = '.';
                    window._setDirty!();
                }
            }]
        });
        this.label.addEventListener('click', function (e) {
            self.menu.show(e);
        });
    }

    initData(leftData: Record<string, any>): void {
        if (!leftData) {
            return;
        }
        this.label.style.color = 'white';
        this.label.textContent = '.';
        const leftPart = leftData['leftPart'];
        leftPart.arithmetic = leftData['arithmetic'];
        this.type = leftData['type'];
        if (!this.type) {
            this.type = 'variable';
        }
        if (this.type === 'parameter') {
            if (this.variableValue) {
                this.variableValue.getContainer().style.display = 'none';
            }
            if (this.methodValue) {
                this.methodValue.getContainer().style.display = 'none';
            }
            if (this.functionValue) {
                this.functionValue.getContainer().style.display = 'none';
            }
            this.parameterValue = new ruleforge.ParameterValue(this.arithmetic, leftPart, 'In');
            this.valueContainer.appendChild(this.parameterValue.getContainer());
        } else if (this.type === 'variable') {
            if (this.parameterValue) {
                this.parameterValue.getContainer().style.display = 'none';
            }
            if (this.methodValue) {
                this.methodValue.getContainer().style.display = 'none';
            }
            if (this.functionValue) {
                this.functionValue.getContainer().style.display = 'none';
            }
            this.variableValue = new ruleforge.VariableValue(this.arithmetic, leftPart, 'In');
            this.valueContainer.appendChild(this.variableValue.getContainer());
        } else if (this.type === 'method') {
            if (this.parameterValue) {
                this.parameterValue.getContainer().style.display = 'none';
            }
            if (this.variableValue) {
                this.variableValue.getContainer().style.display = 'none';
            }
            if (this.functionValue) {
                this.functionValue.getContainer().style.display = 'none';
            }
            this.methodValue = new ruleforge.MethodValue(this.arithmetic, leftPart);
            this.valueContainer.appendChild(this.methodValue.getContainer());
        } else if (this.type === 'commonfunction') {
            if (this.parameterValue) {
                this.parameterValue.getContainer().style.display = 'none';
            }
            if (this.variableValue) {
                this.variableValue.getContainer().style.display = 'none';
            }
            if (this.methodValue) {
                this.methodValue.getContainer().style.display = 'none';
            }
            this.functionValue = new ruleforge.FunctionValue(this.arithmetic, leftPart);
            this.valueContainer.appendChild(this.functionValue.getContainer());
        }
    }

    toXml(): string {
        let xml = '';
        xml += '<left ';
        if (this.type === 'variable') {
            xml += this.variableValue.toXml();
        } else if (this.type === 'parameter') {
            xml += this.parameterValue.toXml();
        } else if (this.type === 'method') {
            xml += this.methodValue.toXml();
        } else if (this.type === 'commonfunction') {
            xml += this.functionValue.toXml();
        }
        xml += ' type="' + this.type + '">';
        if (this.type === 'method') {
            const parameters = this.methodValue.action.parameters;
            for (let i = 0; i < parameters.length; i++) {
                const p = parameters[i];
                xml += p.toXml();
            }
        } else if (this.type === 'commonfunction') {
            xml += this.functionValue.getParameter().toXml();
        }
        xml += this.arithmetic.toXml();
        xml += '</left>';
        if (this.inputType) {
            xml += this.inputType.toXml();
        }
        return xml;
    }

    getVariableValue(): any {
        return this.variableValue;
    }

    getOperator(): any {
        return this.operator;
    }

    getInputType(): any {
        return this.inputType;
    }
}

// Backward-compatible global
(window as unknown as Record<string, unknown>).ruleforge = (window as unknown as Record<string, unknown>).ruleforge || {};
((window as unknown as Record<string, unknown>).ruleforge as Record<string, unknown>).ConditionLeft = ConditionLeft;
