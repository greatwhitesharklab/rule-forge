/**
 * TableAction - Scoring configuration for the complex scorecard.
 *
 * Provides UI for selecting scoring type (sum, weighted sum, custom),
 * scoring assignment target (variable or parameter), and custom Bean ID.
 *
 * This version supports the `notWeight` flag to hide the weighted sum option.
 *
 * Extracted from the complexScoreCard webpack bundle (module 291).
 */

export default class TableAction {
    /**
     * @param {HTMLElement} container - The container element to render into
     * @param {boolean} notWeight - If true, hide the weighted sum option
     */
    constructor(container, notWeight) {
        this.scoringType = 'sum';
        this.notWeight = notWeight;
        this.container = container;
        this.initScoringSetting();
        this.initAssignSetting();
    }

    /**
     * Initialize data from server response.
     *
     * @param {Object} data - Server data
     * @param {string} [data.scoringType] - Scoring type
     * @param {string} [data.scoringBean] - Custom scoring Bean ID
     * @param {string} [data.assignTargetType] - Assignment target type
     */
    initData(data) {
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
                this.variableTarget.getContainer().style.display = '';
                this.variableTarget.setValue(data);
                RuleForge.setDomContent(this.assignTargetContainer, '.');
                this.assignTargetContainer.style.color = 'white';
            } else if (assignTargetType === 'parameter') {
                this.parameterTarget.getContainer().style.display = '';
                this.parameterTarget.setValue(data);
                RuleForge.setDomContent(this.assignTargetContainer, '.');
                this.assignTargetContainer.style.color = 'white';
            } else {
                RuleForge.setDomContent(this.assignTargetContainer, '不赋值');
                this.assignTargetContainer.style.color = '#999';
            }
        }
    }

    /**
     * Initialize the scoring type selector UI.
     */
    initScoringSetting() {
        const container = document.createElement('div');
        container.style.cssText = 'margin: 5px;';
        container.textContent = '得分计算方式：';
        this.container.appendChild(container);

        const select = document.createElement('select');
        select.className = 'form-control';
        select.style.cssText = 'display: inline-block;width:120px;height:30px;padding: 3px;';
        this.scoringSettingSelect = select;
        container.appendChild(select);
        select.innerHTML = '<option value="sum" selected>求和</option>';
        if (!this.notWeight) {
            select.innerHTML += '<option value="weightsum">加权求和</option>';
        }
        select.innerHTML += '<option value="custom">自定义</option>';

        const customContainer = document.createElement('span');
        customContainer.style.cssText = 'margin: 15px;';
        customContainer.textContent = '自定义计算得分的Bean ID：';
        this.customContainer = customContainer;
        container.appendChild(customContainer);
        customContainer.style.display = 'none';

        const customBeanEditor = document.createElement('input');
        customBeanEditor.type = 'text';
        customBeanEditor.className = 'form-control';
        customBeanEditor.style.cssText = 'width: 200px;display: inline-block';
        this.customBeanEditor = customBeanEditor;
        customContainer.appendChild(customBeanEditor);

        const self = this;
        customBeanEditor.addEventListener('change', function () {
            self.customScoringBean = this.value;
        });
        select.addEventListener('change', function () {
            self.scoringType = this.value;
            if (self.scoringType === 'custom') {
                self.customContainer.style.display = '';
            } else {
                self.customContainer.style.display = 'none';
            }
        });
    }

    /**
     * Initialize the assignment target selector UI.
     */
    initAssignSetting() {
        const container = document.createElement('div');
        container.style.cssText = 'margin: 15px 5px';
        container.textContent = '将得分值赋给：';
        this.container.appendChild(container);

        this.assignTargetContainer = generateContainer();
        container.appendChild(this.assignTargetContainer);
        RuleForge.setDomContent(this.assignTargetContainer, '请选择值类型');
        this.assignTargetContainer.style.color = 'blue';

        this.variableTarget = new ruleforge.VariableValue(null, null, 'Out');
        this.parameterTarget = new ruleforge.ParameterValue(null, null, 'Out');
        this.variableTarget.getContainer().style.display = 'none';
        this.parameterTarget.getContainer().style.display = 'none';
        container.appendChild(this.variableTarget.getContainer());
        container.appendChild(this.parameterTarget.getContainer());

        const self = this;
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '选择变量',
                onClick: function () {
                    self.parameterTarget.getContainer().style.display = 'none';
                    self.variableTarget.getContainer().style.display = '';
                    self.assignTargetType = 'variable';
                    RuleForge.setDomContent(self.assignTargetContainer, '.');
                    self.assignTargetContainer.style.color = 'white';
                }
            }, {
                label: '选择参数',
                onClick: function () {
                    self.variableTarget.getContainer().style.display = 'none';
                    self.parameterTarget.getContainer().style.display = '';
                    self.assignTargetType = 'parameter';
                    RuleForge.setDomContent(self.assignTargetContainer, '.');
                    self.assignTargetContainer.style.color = 'white';
                }
            }, {
                label: '不赋值',
                onClick: function () {
                    self.variableTarget.getContainer().style.display = 'none';
                    self.parameterTarget.getContainer().style.display = 'none';
                    self.assignTargetType = 'none';
                    RuleForge.setDomContent(self.assignTargetContainer, '不赋值');
                    self.assignTargetContainer.style.color = '#999';
                }
            }]
        });
        this.assignTargetContainer.addEventListener('click', function (e) {
            self.menu.show(e);
        });
    }

    /**
     * Serialize the table action configuration to XML attributes.
     * @returns {string} XML attribute string
     * @throws {string} If required fields are missing
     */
    toXml() {
        if (!this.scoringType) {
            throw '请选择得分计算方式';
        }
        if (!this.assignTargetType) {
            throw '请选择得分赋值对象';
        }
        if (this.scoringType === 'custom' && (!this.customScoringBean || this.customScoringBean.length < 1)) {
            throw '请输入自定义计算得分的Bean ID';
        }
        let xml = ' scoring-type="' + this.scoringType + '" assign-target-type="' + this.assignTargetType + '" ';
        if (this.assignTargetType === 'variable') {
            xml += this.variableTarget.toXml();
        } else if (this.assignTargetType === 'parameter') {
            xml += this.parameterTarget.toXml();
        }
        if (this.scoringType === 'custom') {
            xml += ' custom-scoring-bean="' + this.customScoringBean + '"';
        }
        return xml;
    }
}
