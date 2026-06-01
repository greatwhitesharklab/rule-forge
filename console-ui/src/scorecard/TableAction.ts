import { generateContainer } from '../editor/common/URule.js';

declare const ruleforge: {
    VariableValue: new (a: any, b: any, c: string) => {
        getContainer(): HTMLElement;
        setValue(data: any): void;
        toXml(): string;
    };
    ParameterValue: new (a: any, b: any, c: string) => {
        getContainer(): HTMLElement;
        setValue(data: any): void;
        toXml(): string;
    };
};

export default class TableAction {
    container: HTMLElement;
    scoringType?: string;
    customScoringBean?: string;
    assignTargetType?: string;
    scoringSettingSelect!: HTMLSelectElement;
    customContainer!: HTMLSpanElement;
    customBeanEditor!: HTMLInputElement;
    assignTargetContainer!: HTMLElement;
    variableTarget!: ReturnType<typeof ruleforge.VariableValue extends new (...args: any) => infer T ? () => T : never>;
    parameterTarget!: ReturnType<typeof ruleforge.ParameterValue extends new (...args: any) => infer T ? () => T : never>;
    menu!: MenuInstance;

    constructor(container: HTMLElement) {
        this.container = container;
        this.initScoringSetting();
        this.initAssignSetting();
    }

    initData(data: any): void {
        const scoringType = data.scoringType;
        if (scoringType) {
            this.scoringSettingSelect.value = scoringType;
            this.scoringType = scoringType;
            if (scoringType === 'custom') {
                this.customContainer.style.display = '';
                this.customScoringBean = data.scoringBean;
                this.customBeanEditor.value = this.customScoringBean;
            }
        }
        const assignTargetType = data.assignTargetType;
        if (assignTargetType) {
            this.assignTargetType = assignTargetType;
            if (assignTargetType === 'variable') {
                (this.variableTarget as any).getContainer().style.display = '';
                (this.variableTarget as any).setValue(data);
                this.assignTargetContainer.textContent = ".";
                this.assignTargetContainer.style.color = "white";
            } else if (assignTargetType === 'parameter') {
                (this.parameterTarget as any).getContainer().style.display = '';
                (this.parameterTarget as any).setValue(data);
                this.assignTargetContainer.textContent = ".";
                this.assignTargetContainer.style.color = "white";
            } else {
                this.assignTargetContainer.textContent = "不赋值";
                this.assignTargetContainer.style.color = "#999";
            }
        }
    }

    initScoringSetting(): void {
        const scoringSettingContainer = document.createElement('div');
        scoringSettingContainer.style.cssText = 'margin: 5px;';
        scoringSettingContainer.textContent = '得分计算方式：';
        this.container.appendChild(scoringSettingContainer);
        const select = document.createElement('select');
        select.className = 'form-control';
        select.style.cssText = 'display: inline-block;width:120px;height:30px;padding: 3px;';
        this.scoringSettingSelect = select;
        scoringSettingContainer.appendChild(select);
        select.innerHTML = '<option value="sum">求和</option>';
        select.innerHTML += '<option value="weightsum">加权求和</option>';
        select.innerHTML += '<option value="custom">自定义</option>';
        const customContainer = document.createElement('span');
        customContainer.style.cssText = 'margin: 15px;';
        customContainer.textContent = '自定义计算得分的Bean ID：';
        this.customContainer = customContainer;
        scoringSettingContainer.appendChild(customContainer);
        customContainer.style.display = 'none';
        const customBeanEditor = document.createElement('input');
        customBeanEditor.type = 'text';
        customBeanEditor.className = 'form-control';
        customBeanEditor.style.cssText = 'width: 200px;display: inline-block';
        this.customBeanEditor = customBeanEditor;
        customContainer.appendChild(customBeanEditor);
        const _this = this;
        customBeanEditor.addEventListener('change', function () {
            _this.customScoringBean = (this as HTMLInputElement).value;
        });

        select.addEventListener('change', function () {
            _this.scoringType = (this as HTMLSelectElement).value;
            if (_this.scoringType === 'custom') {
                _this.customContainer.style.display = '';
            } else {
                _this.customContainer.style.display = 'none';
            }
        });
    }

    initAssignSetting(): void {
        const assignSettingContainer = document.createElement('div');
        assignSettingContainer.style.cssText = 'margin: 15px 5px';
        assignSettingContainer.textContent = '将得分值赋给：';
        this.container.appendChild(assignSettingContainer);
        this.assignTargetContainer = generateContainer();
        assignSettingContainer.appendChild(this.assignTargetContainer);
        this.assignTargetContainer.textContent = "请选择值类型";
        this.assignTargetContainer.style.color = "blue";
        this.variableTarget = new ruleforge.VariableValue(null, null, "Out") as any;
        this.parameterTarget = new ruleforge.ParameterValue(null, null, "Out") as any;
        (this.variableTarget as any).getContainer().style.display = 'none';
        (this.parameterTarget as any).getContainer().style.display = 'none';
        assignSettingContainer.appendChild((this.variableTarget as any).getContainer());
        assignSettingContainer.appendChild((this.parameterTarget as any).getContainer());
        const self = this;
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: "选择变量",
                onClick: function () {
                    (self.parameterTarget as any).getContainer().style.display = 'none';
                    (self.variableTarget as any).getContainer().style.display = '';
                    self.assignTargetType = "variable";
                    self.assignTargetContainer.textContent = ".";
                    self.assignTargetContainer.style.color = "white";
                }
            }, {
                label: "选择参数",
                onClick: function () {
                    (self.variableTarget as any).getContainer().style.display = 'none';
                    (self.parameterTarget as any).getContainer().style.display = '';
                    self.assignTargetType = "parameter";
                    self.assignTargetContainer.textContent = ".";
                    self.assignTargetContainer.style.color = "white";
                }
            }, {
                label: "不赋值",
                onClick: function () {
                    (self.variableTarget as any).getContainer().style.display = 'none';
                    (self.parameterTarget as any).getContainer().style.display = 'none';
                    self.assignTargetType = "none";
                    self.assignTargetContainer.textContent = "不赋值";
                    self.assignTargetContainer.style.color = "#999";
                }
            }]
        });
        this.assignTargetContainer.addEventListener('click', function (e) {
            self.menu.show(e);
        });
    }

    toXml(): string {
        if (!this.scoringType) {
            throw "请选择得分计算方式";
        }
        if (!this.assignTargetType) {
            throw "请选择得分赋值对象";
        }
        if (this.scoringType === 'custom' && (!this.customScoringBean || this.customScoringBean.length < 1)) {
            throw "请输入自定义计算得分的Bean ID";
        }
        let xml = " scoring-type=\"" + this.scoringType + "\" assign-target-type=\"" + this.assignTargetType + "\" ";
        if (this.assignTargetType === 'variable') {
            xml += (this.variableTarget as any).toXml();
        } else if (this.assignTargetType === 'parameter') {
            xml += (this.parameterTarget as any).toXml();
        }
        if (this.scoringType === 'custom') {
            xml += " custom-scoring-bean=\"" + this.customScoringBean + "\"";
        }
        return xml;
    }
}
