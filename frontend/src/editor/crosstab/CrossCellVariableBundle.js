/**
 * CrossCellVariableBundle - Manages the assignment target variable for cross cells.
 *
 * Provides a UI for selecting a variable or parameter that the cross cell
 * values will be assigned to. This appears above the crosstab grid.
 *
 * Extracted from the crosstab webpack bundle (module 457 / original module 109).
 */

export default class CrossCellVariableBundle {
    constructor() {
        this.container = $('<div style="margin-left: 15px;"><span style="color: #747474;">选择交叉单元格值要赋予的对象：</span></div>');
        this.assignTargetContainer = generateContainer();
        this.container.append(this.assignTargetContainer);
        this.assignTargetContainer.css({
            color: '#2196F3'
        });
        this.init();
    }

    /**
     * Initialize the variable/parameter selection UI.
     */
    init() {
        this.variableTarget = new ruleforge.VariableValue(null, null, 'Out');
        this.parameterTarget = new ruleforge.ParameterValue(null, null, 'Out');

        this.variableTarget.getContainer().hide();
        this.parameterTarget.getContainer().hide();

        this.container.append(this.variableTarget.getContainer());
        this.container.append(this.parameterTarget.getContainer());

        const self = this;
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '选择变量',
                onClick: function () {
                    self.parameterTarget.getContainer().hide();
                    self.variableTarget.getContainer().show();
                    self.assignTargetType = 'variable';
                    RuleForge.setDomContent(self.assignTargetContainer, '.');
                    self.assignTargetContainer.css({
                        color: 'white'
                    });
                }
            }, {
                label: '选择参数',
                onClick: function () {
                    self.variableTarget.getContainer().hide();
                    self.parameterTarget.getContainer().show();
                    self.assignTargetType = 'parameter';
                    RuleForge.setDomContent(self.assignTargetContainer, '.');
                    self.assignTargetContainer.css({
                        color: 'white'
                    });
                }
            }]
        });

        this.assignTargetContainer.click(function (e) {
            self.menu.show(e);
        });

        RuleForge.setDomContent(this.assignTargetContainer, '选择...');
    }

    /**
     * Initialize the bundle data from server response.
     *
     * @param {Object} [data] - Cross-cell variable bundle data
     * @param {string} [data.assignTargetType] - "variable" or "parameter"
     * @param {string} [data.assignVariableCategory] - Variable category
     * @param {string} [data.assignVariable] - Variable name
     * @param {string} [data.assignVariableLabel] - Display label
     * @param {string} [data.assignDatatype] - Data type
     */
    initData(data) {
        if (data) {
            const valueData = {
                variableCategory: data.assignVariableCategory,
                variableName: data.assignVariable,
                variableLabel: data.assignVariableLabel,
                datatype: data.assignDatatype
            };

            if (data.assignTargetType === 'variable') {
                this.variableTarget.getContainer().show();
                this.parameterTarget.getContainer().hide();
                this.variableTarget.setValue(valueData);
            } else if (data.assignTargetType === 'parameter') {
                this.variableTarget.getContainer().hide();
                this.parameterTarget.getContainer().show();
                this.parameterTarget.setValue(valueData);
            } else {
                RuleForge.setDomContent(this.assignTargetContainer, '请选择要赋值的对象...');
                this.assignTargetContainer.css('color', '#03A9F4');
            }

            if (data.assignTargetType) {
                this.assignTargetType = data.assignTargetType;
                RuleForge.setDomContent(this.assignTargetContainer, '.');
                this.assignTargetContainer.css('color', '#fff');
            }
        }
    }

    /**
     * Serialize the cross-cell variable bundle to XML attributes.
     * @returns {string} XML attribute string
     * @throws {string} If no assignment target has been selected
     */
    toXml() {
        if (!this.assignTargetType) {
            throw '请选择交叉单元格值要赋予的对象！';
        }
        let xml = ' assign-target-type="' + this.assignTargetType + '" ';
        if (this.assignTargetType === 'variable') {
            xml += this.variableTarget.toXml();
        } else if (this.assignTargetType === 'parameter') {
            xml += this.parameterTarget.toXml();
        }
        return xml;
    }
}
