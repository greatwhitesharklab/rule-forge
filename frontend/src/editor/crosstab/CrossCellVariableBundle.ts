/**
 * CrossCellVariableBundle - Manages the assignment target variable for cross cells.
 *
 * Provides a UI for selecting a variable or parameter that the cross cell
 * values will be assigned to. This appears above the crosstab grid.
 *
 * Extracted from the crosstab webpack bundle (module 457 / original module 109).
 */

import {generateContainer} from '../common/URule.js';

declare const ruleforge: any;

export default class CrossCellVariableBundle {
    container: HTMLDivElement;
    assignTargetContainer: HTMLElement;
    variableTarget: any;
    parameterTarget: any;
    menu: MenuInstance;
    assignTargetType?: string;

    constructor() {
        const container = document.createElement('div');
        container.style.cssText = 'margin-left: 15px;';
        container.innerHTML = '<span style="color: #747474;">选择交叉单元格值要赋予的对象：</span>';
        this.container = container;
        this.assignTargetContainer = generateContainer();
        container.appendChild(this.assignTargetContainer);
        this.assignTargetContainer.style.color = '#2196F3';
        this.init();
    }

    /**
     * Initialize the variable/parameter selection UI.
     */
    init(): void {
        this.variableTarget = new ruleforge.VariableValue(null, null, 'Out');
        this.parameterTarget = new ruleforge.ParameterValue(null, null, 'Out');

        this.variableTarget.getContainer().style.display = 'none';
        this.parameterTarget.getContainer().style.display = 'none';

        this.container.appendChild(this.variableTarget.getContainer());
        this.container.appendChild(this.parameterTarget.getContainer());

        const self = this;
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '选择变量',
                onClick: function () {
                    self.parameterTarget.getContainer().style.display = 'none';
                    self.variableTarget.getContainer().style.display = '';
                    self.assignTargetType = 'variable';
                    self.assignTargetContainer.textContent = '.';
                    self.assignTargetContainer.style.color = 'white';
                }
            }, {
                label: '选择参数',
                onClick: function () {
                    self.variableTarget.getContainer().style.display = 'none';
                    self.parameterTarget.getContainer().style.display = '';
                    self.assignTargetType = 'parameter';
                    self.assignTargetContainer.textContent = '.';
                    self.assignTargetContainer.style.color = 'white';
                }
            }]
        });

        this.assignTargetContainer.addEventListener('click', function (e: MouseEvent) {
            self.menu.show(e);
        });

        this.assignTargetContainer.textContent = '选择...';
    }

    /**
     * Initialize the bundle data from server response.
     *
     * @param data - Cross-cell variable bundle data
     */
    initData(data?: any): void {
        if (data) {
            const valueData = {
                variableCategory: data.assignVariableCategory,
                variableName: data.assignVariable,
                variableLabel: data.assignVariableLabel,
                datatype: data.assignDatatype
            };

            if (data.assignTargetType === 'variable') {
                this.variableTarget.getContainer().style.display = '';
                this.parameterTarget.getContainer().style.display = 'none';
                this.variableTarget.setValue(valueData);
            } else if (data.assignTargetType === 'parameter') {
                this.variableTarget.getContainer().style.display = 'none';
                this.parameterTarget.getContainer().style.display = '';
                this.parameterTarget.setValue(valueData);
            } else {
                this.assignTargetContainer.textContent = '请选择要赋值的对象...';
                this.assignTargetContainer.style.color = '#03A9F4';
            }

            if (data.assignTargetType) {
                this.assignTargetType = data.assignTargetType;
                this.assignTargetContainer.textContent = '.';
                this.assignTargetContainer.style.color = '#fff';
            }
        }
    }

    /**
     * Serialize the cross-cell variable bundle to XML attributes.
     * @returns XML attribute string
     * @throws If no assignment target has been selected
     */
    toXml(): string {
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
