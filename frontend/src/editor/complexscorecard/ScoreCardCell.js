/**
 * ConditionCell - A cell with condition configuration in the complex scorecard grid.
 *
 * These cells appear at the intersection of content rows and condition columns.
 * They display a variable/parameter selector, a condition joint, and a context
 * menu for configuring conditions, adding/deleting rows, and copy/paste.
 *
 * Extracted from the complexScoreCard webpack bundle (module 349).
 */

import HighlightCell from './HighlightCell.js';
import RowContext from './RowContext.js';
/* bootbox is a global */
import {copyCellData, pasteCellData} from '../crosstab/cellDataUtils.js';

export default class ConditionCell extends HighlightCell {
    /**
     * @param {RowContext} rowContext - The row context
     * @param {ContentRow} contentRow - The content row this cell belongs to
     * @param {ConditionColumn} conditionCol - The condition column this cell belongs to
     */
    constructor(rowContext, contentRow, conditionCol) {
        super(rowContext);
        this.contentRow = contentRow;
        this.conditionCol = conditionCol;
        this.init(rowContext);
    }

    /**
     * Initialize the condition cell UI: variable selector, condition display, and context menu.
     *
     * @param {RowContext} rowContext - The row context
     */
    init(rowContext) {
        const self = this;

        // Variable/parameter selector
        this.propContainer = generateContainer();
        RuleForge.setDomContent(this.propContainer, '请选择属性');
        this.td.appendChild(this.propContainer);
        this.propContainer.style.cssText = 'color: #999; position: relative';
        this.refreshVariableMenus();

        // Condition display
        const container = document.createElement('div');
        container.textContent = '无';
        this.container = container;
        this.td.appendChild(container);

        // Track current condition cell for delete row button
        this.td.addEventListener('click', function () {
            window._currentConditionCell = self;
        });

        // Context menu
        const menuConfig = {
            menuItems: [{
                label: '配置条件',
                icon: 'glyphicon glyphicon-cog',
                onClick: function () {
                    self.configCondition(self.container);
                }
            }, {
                label: '添加条件行',
                icon: 'glyphicon glyphicon-plus-sign',
                onClick: function () {
                    self.insertRow(rowContext.complexTable);
                }
            }, {
                label: '删除条件行',
                icon: 'glyphicon glyphicon-minus-sign',
                onClick: function () {
                    MsgBox.confirm('真的要当前删除条件行？', function () {
                        self.deleteRow(rowContext.complexTable);
                    });
                }
            }, {
                label: '清空条件',
                icon: 'glyphicon glyphicon-trash',
                onClick: function () {
                    MsgBox.confirm('真的要清空当前单元格内容？', function () {
                        if (self.cellCondition) {
                            self.cellCondition.clean();
                            self.container.innerHTML = '无';
                            self.container.style.color = 'gray';
                        }
                        window._setDirty();
                    });
                }
            }, {
                label: '复制',
                icon: 'glyphicon glyphicon-copy',
                onClick: function () {
                    const xml = self.toXml();
                    if (xml && xml !== '') {
                        copyCellData('condition', xml);
                    } else {
                        window.bootbox.alert('当前没有内容可供复制!');
                    }
                }
            }, {
                label: '粘贴',
                icon: 'glyphicon glyphicon-paste',
                onClick: function () {
                    pasteCellData('condition', function (data) {
                        if (self.cellCondition) {
                            self.cellCondition.clean();
                        }
                        self.cellCondition = new ruleforge.CellCondition('<div/>');
                        self.cellCondition.initData(data);
                        self.container.innerHTML = '';
                        self.container.appendChild(self.cellCondition.getDisplayContainer());
                    });
                }
            }]
        };
        const menu = new RuleForge.menu.Menu(menuConfig);
        this.td.addEventListener('contextmenu', function (e) {
            menu.show(e);
        });
    }

    /**
     * Initialize cell data from server response.
     *
     * @param {Object} data - Cell data from server
     * @param {Object} [data.joint] - Condition joint data
     * @param {string} [data.variableLabel] - Variable label
     * @param {string} [data.variableName] - Variable name
     * @param {string} [data.datatype] - Data type
     */
    initData(data) {
        if (data.joint) {
            this.cellCondition = new ruleforge.CellCondition('<div/>');
            this.cellCondition.initData(data.joint);
            this.container.innerHTML = '';
            this.container.appendChild(this.cellCondition.getDisplayContainer());
        }
        if (data.variableLabel) {
            this.variableLabel = data.variableLabel;
            this.variableName = data.variableName;
            this.datatype = data.datatype;
            RuleForge.setDomContent(this.propContainer, this.variableLabel || this.variableName);
            this.propContainer.style.color = '#1d1d1d';
        }
    }

    /**
     * Refresh the variable/parameter menus based on the condition column's variables.
     */
    refreshVariableMenus() {
        const variables = this.conditionCol.variables || [];
        const clickFn = this.buildClickFunction();
        const menuItems = [];

        for (const v of variables) {
            menuItems.push({
                icon: 'glyphicon glyphicon-tasks',
                name: v.name,
                label: v.label,
                datatype: v.type,
                onClick: clickFn
            });
        }

        const self = this;
        if (this.propContainer.menu) {
            this.propContainer.menu.setConfig({menuItems: menuItems});
        } else {
            this.propContainer.menu = new RuleForge.menu.Menu({menuItems: menuItems});
            this.propContainer.addEventListener('click', function (e) {
                self.propContainer.menu.show(e);
            });
        }
    }

    /**
     * Build the click handler for variable menu items.
     * @returns {Function}
     */
    buildClickFunction() {
        const self = this;
        return function (item) {
            self.variableLabel = item.label;
            self.variableName = item.name;
            self.datatype = item.datatype;
            self.propContainer.innerHTML = self.variableLabel;
            self.propContainer.style.color = '#1d1d1d';
        };
    }

    /**
     * Open the condition configuration dialog.
     *
     * @param {HTMLElement} container - The container element to update after dialog closes
     */
    configCondition(container) {
        const dialog = document.createElement('div');
        if (!this.cellCondition) {
            this.cellCondition = new ruleforge.CellCondition('<div/>');
        }
        const self = this;
        this.cellCondition.renderTo(dialog);
        window.bootbox.dialog('配置条件', dialog, [], [{
            name: 'hide.bs.modal',
            callback: function () {
                container.innerHTML = '';
                container.appendChild(self.cellCondition.getDisplayContainer());
            }
        }], true);
    }

    /**
     * Increment the rowspan of this cell's TD element.
     */
    increaseRowSpan() {
        let rowspan = this.td.rowSpan;
        rowspan || (rowspan = 1);
        rowspan++;
        this.rowSpan = rowspan;
        this.td.rowSpan = rowspan;
    }

    /**
     * Insert a new row after this cell's row.
     *
     * @param {ComplexScoreCard} complexTable - The parent scorecard table
     */
    insertRow(complexTable) {
        const rowContext = new RowContext(complexTable);
        rowContext.setRefConditionCell(this);
        complexTable.addRow(rowContext);
    }

    /**
     * Delete all rows spanned by this condition cell.
     *
     * @param {ComplexScoreCard} complexTable - The parent scorecard table
     */
    deleteRow(complexTable) {
        let rowspan = this.td.rowSpan;
        const spanCount = rowspan;

        if (rowspan === complexTable.contentRows.length) {
            window.bootbox.alert('条件行至少要保留一行!');
            return;
        }

        rowspan || (rowspan = 1);
        const rowContext = new RowContext(complexTable);
        const rowIndex = complexTable.contentRows.indexOf(this.contentRow);
        const endIndex = rowIndex + rowspan;

        for (let i = rowIndex; i < endIndex; i++) {
            const row = complexTable.contentRows[i];

            // Process condition cells from right to left to avoid index issues
            for (let ci = complexTable.conditionColumns.length - 1; ci >= 0; ci--) {
                const col = complexTable.conditionColumns[ci];
                const cell = rowContext._findConditionCell(row, col);

                if (cell) {
                    let cellRowspan = cell.td.rowSpan;
                    if (cellRowspan > 1 && cellRowspan > spanCount) {
                        cellRowspan--;
                        cell.td.rowSpan = cellRowspan;

                        // Transfer cell to the next available row
                        const nextCol = complexTable.conditionColumns[ci + 1];
                        const info = this._findNextRowCellInfo(i + 1, complexTable, nextCol);
                        const targetRow = info.targetRow;
                        cell.contentRow = targetRow;

                        const targetCell = info.targetCell;
                        const targetIndex = info.targetRowConditionCellIndex;
                        targetCell.td.before(cell.td);
                        targetRow.conditionCells.splice(targetIndex, 0, cell);
                    }
                } else {
                    // Cell is part of a span from a previous row
                    const spannedCell = rowContext.fetchConditionCell(row, col);
                    let spannedRowspan = spannedCell.td.rowSpan;
                    spannedRowspan--;
                    spannedCell.td.rowSpan = spannedRowspan;
                }
            }
            row.tr.remove();
        }

        complexTable.contentRows.splice(rowIndex, rowspan);
    }

    /**
     * Find the next row cell info for transferring cells during row deletion.
     *
     * @param {number} startRowIdx - The row index to start searching from
     * @param {ComplexScoreCard} complexTable - The parent scorecard table
     * @param {ConditionColumn} nextCol - The next column to look for
     * @returns {Object} Object with targetCell, targetRow, and targetRowConditionCellIndex
     */
    _findNextRowCellInfo(startRowIdx, complexTable, nextCol) {
        let targetCell = null;
        let targetRow = null;
        let targetIndex = -1;

        for (let i = startRowIdx; i < complexTable.contentRows.length; i++) {
            targetIndex = -1;
            const row = complexTable.contentRows[i];
            for (const cell of row.conditionCells) {
                if (cell.conditionCol === nextCol) {
                    targetCell = cell;
                    targetRow = row;
                    targetIndex++;
                    break;
                }
            }
            if (targetCell) break;
        }

        return {
            targetCell: targetCell,
            targetRow: targetRow,
            targetRowConditionCellIndex: targetIndex
        };
    }

    /**
     * Serialize this condition cell to XML.
     * @returns {string} XML representation of the cell condition
     */
    toXml() {
        return this.cellCondition ? this.cellCondition.toXml() : '';
    }
}

/**
 * ActionCell - A cell with value input in the complex scorecard grid.
 *
 * These cells appear at the intersection of content rows and action columns
 * (score columns or custom columns). They contain an InputType widget
 * for entering values and a context menu for clear/copy/paste.
 *
 * Extracted from the complexScoreCard webpack bundle (module 348).
 */
export class ActionCell extends HighlightCell {
    /**
     * @param {RowContext} rowContext - The row context
     * @param {ContentRow} contentRow - The content row this cell belongs to
     * @param {ActionColumn} actionCol - The action column this cell belongs to
     */
    constructor(rowContext, contentRow, actionCol) {
        super(rowContext);
        this.contentRow = contentRow;
        this.actionCol = actionCol;
        this.init();
    }

    /**
     * Initialize the action cell UI: input type widget and context menu.
     */
    init() {
        const self = this;

        const container = document.createElement('div');
        container.style.cssText = 'position: relative;';
        this.container = container;
        this.td.appendChild(container);

        // Click clears current condition cell selection
        this.td.addEventListener('click', function () {
            window._currentConditionCell = null;
        });

        this.inputType = new ruleforge.InputType(null, '无');
        container.appendChild(this.inputType.getContainer());

        // Context menu
        const menuConfig = {
            menuItems: [{
                label: '清空',
                icon: 'glyphicon glyphicon-trash',
                onClick: function () {
                    MsgBox.confirm('真的要清空当前单元格内容吗？', function () {
                        self.inputType.getContainer().remove();
                        self.inputType = new ruleforge.InputType(null, '无');
                        self.container.appendChild(self.inputType.getContainer());
                        window._setDirty();
                    });
                }
            }, {
                label: '复制',
                icon: 'glyphicon glyphicon-copy',
                onClick: function () {
                    const xml = self.toXml();
                    if (xml && xml !== '') {
                        copyCellData('value', xml);
                    } else {
                        window.bootbox.alert('当前没有内容可供复制!');
                    }
                }
            }, {
                label: '粘贴',
                icon: 'glyphicon glyphicon-paste',
                onClick: function () {
                    pasteCellData('value', function (data) {
                        self.inputType.getContainer().remove();
                        self.inputType = new ruleforge.InputType(null, '无');
                        self.container.appendChild(self.inputType.getContainer());
                        self.inputType.setValueType(data.valueType, data);
                    });
                }
            }]
        };
        const menu = new RuleForge.menu.Menu(menuConfig);
        this.td.addEventListener('contextmenu', function (e) {
            menu.show(e);
        });
    }

    /**
     * Serialize this action cell to XML.
     * @returns {string} XML representation
     */
    toXml() {
        return this.inputType.toXml();
    }

    /**
     * Initialize cell data from server response.
     *
     * @param {Object} data - Cell data from server
     * @param {Object} [data.value] - Value data for InputType
     */
    initData(data) {
        if (data && data.value) {
            const value = data.value;
            this.inputType.setValueType(value.valueType, value);
        }
    }
}
