import { generateContainer } from '../common/URule';

declare const ruleforge: any;

export class Condition {
    container: HTMLSpanElement;
    arithmetic: any;
    label: HTMLElement;
    valueContainer: HTMLSpanElement;
    menu: MenuInstance;
    type?: string;
    variableValue?: any;
    parameterValue?: any;
    functionValue?: any;
    methodValue?: any;
    operator?: any;
    inputType?: any;

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

    private initMenu(constantLibraries?: any[]): void {
        const self = this;
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '选择变量',
                onClick: function () {
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
                        self.variableValue = new ruleforge.VariableValue(self.arithmetic, null, 'In', null, false);
                        self.valueContainer.appendChild(self.variableValue.getContainer());
                    }
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
                    self.label.style.color = 'white';
                    self.label.textContent = '.';
                    window._setDirty?.();
                }
            }, {
                label: '选择参数',
                onClick: function () {
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
                    self.label.style.color = 'white';
                    self.label.textContent = '.';
                    window._setDirty?.();
                }
            }]
        });
        this.label.addEventListener('click', function (e: Event) {
            self.menu.show(e as MouseEvent);
        });
    }

    initData(data: Record<string, any>): void {
        this.label.style.color = 'white';
        this.label.textContent = '.';
        const leftData = data['left'];
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
            this.variableValue = new ruleforge.VariableValue(this.arithmetic, leftPart, 'In', null, false);
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
        if (this.operator) {
            this.operator.getContainer().style.display = '';
        } else {
            const self = this;
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
        let xml = '<atom op="' + this.operator.getOperator() + '">';
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
        xml += '</atom>';
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

(ruleforge as any).Condition = Condition;
