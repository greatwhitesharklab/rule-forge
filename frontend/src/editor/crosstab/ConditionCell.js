/**
 * ConditionCell - A cell with condition configuration in the crosstab grid.
 *
 * These cells appear at the intersection of condition rows and condition columns.
 * They support context menus for adding/removing rows and columns,
 * configuring conditions, and variable/parameter assignment.
 *
 * Extracted from the crosstab webpack bundle (module 334).
 */

import BaseCell from './BaseCell.js';
/* bootbox is a global */
import {copyCellData, pasteCellData} from './cellDataUtils.js';

export default class ConditionCell extends BaseCell {
    /**
     * @param {Row} row - The row this cell belongs to
     * @param {Column} col - The column this cell belongs to
     */
    constructor(row, col) {
        super(row, col);
        this.initCellContent();
    }

    /**
     * Initialize the condition cell content: variable/parameter target selector,
     * bundle menu, and condition display.
     */
    initCellContent() {
        const self = this;

        const wrapper = document.createElement('div');
        wrapper.style.cssText = 'position: relative';
        this.td.appendChild(wrapper);

        // Property container (click to open variable/parameter menu)
        this.propContainer = generateContainer();
        this.propContainer.style.color = '#a7a7a7';
        wrapper.appendChild(this.propContainer);

        // Variable target selector
        this.variableTarget = new ruleforge.VariableValue(null, null, 'Out');
        this.variableTarget.registerClick(function (data) {
            self.unifyBundleData(data);
        });

        // Parameter target selector
        this.parameterTarget = new ruleforge.ParameterValue(null, null, 'Out');
        this.parameterTarget.registerClick(function (data) {
            self.unifyBundleData(data);
        });

        this.variableTarget.getContainer().style.display = 'none';
        this.parameterTarget.getContainer().style.display = 'none';
        wrapper.appendChild(this.variableTarget.getContainer());
        wrapper.appendChild(this.parameterTarget.getContainer());

        // Bundle selection menu (variable or parameter)
        this.bundleMenu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '选择变量',
                onClick: function () {
                    self.parameterTarget.getContainer().style.display = 'none';
                    self.variableTarget.getContainer().style.display = '';
                    self.assignTargetType = 'variable';
                    self.propContainer.textContent = '.';
                    self.propContainer.style.color = 'white';
                }
            }, {
                label: '选择参数',
                onClick: function () {
                    self.variableTarget.getContainer().style.display = 'none';
                    self.parameterTarget.getContainer().style.display = '';
                    self.assignTargetType = 'parameter';
                    self.propContainer.textContent = '.';
                    self.propContainer.style.color = 'white';
                }
            }]
        });

        this.propContainer.addEventListener('click', function (e) {
            self.bundleMenu.show(e);
        });

        // Set placeholder text based on position
        let placeholder = '选择条件行属性';
        if (!this.row.istop) {
            placeholder = '选择条件列属性';
        }
        this.propContainer.textContent = placeholder;

        // Condition display area
        const conditionWrapper = document.createElement('span');
        conditionWrapper.style.cssText = 'position: relative';
        this.td.appendChild(conditionWrapper);
        const conditionContainer = document.createElement('span');
        conditionContainer.innerHTML = '<span style="color:#999">无</span>';
        this.conditionContainer = conditionContainer;
        conditionWrapper.appendChild(conditionContainer);

        this.cellCondition = new ruleforge.CellCondition('<div/>');
        conditionContainer.innerHTML = '';
        conditionContainer.appendChild(this.cellCondition.getDisplayContainer());
    }

    /**
     * Initialize condition cell data from server response.
     *
     * @param {Object} data - Cell data from server
     * @param {Object} [data.joint] - Condition joint data
     * @param {number} [data.rowspan] - Row span
     * @param {number} [data.colspan] - Column span
     */
    initData(data) {
        if (data.joint) {
            this.cellCondition.initData(data.joint);
            this.conditionContainer.innerHTML = '';
            this.conditionContainer.appendChild(this.cellCondition.getDisplayContainer());
        }
        if (this.row.bundleData) {
            this.setBundleData(this.row.bundleData);
        }
        if (this.col.bundleData) {
            this.setBundleData(this.col.bundleData);
        }
        const rowspan = data.rowspan;
        if (rowspan) {
            this.td.rowSpan = rowspan;
        }
        const colspan = data.colspan;
        if (colspan) {
            this.td.colSpan = colspan;
        }
    }

    /**
     * Propagate bundle data to all cells in the same row (for top rows)
     * or same column (for left rows).
     *
     * @param {Object} data - The bundle data (variable or parameter selection)
     */
    unifyBundleData(data) {
        this.bundleData = data;
        if (this.row.istop) {
            this.row.bundleData = data;
        }
        if (this.row.isleft) {
            this.col.bundleData = data;
        }

        const rows = this.table.rows;
        const columns = this.table.columns;

        if (this.row.istop) {
            // Propagate to all cells in the same top row
            for (const col of columns) {
                const cell = this.table.getCell(this.row, col);
                if (cell && cell !== this) {
                    cell.setBundleData(data);
                }
            }
        } else {
            // Propagate to all cells in the same left column
            for (const row of rows) {
                const cell = this.table.getCell(row, this.col);
                if (cell && cell !== this) {
                    cell.setBundleData(data);
                }
            }
        }
    }

    /**
     * Set the bundle data on this cell and update the UI.
     *
     * @param {Object} data - The bundle data
     * @param {string} data.type - "variable" or "parameter"
     */
    setBundleData(data) {
        this.bundleData = data;
        if (this.row.istop) {
            this.row.bundleData = data;
        }
        if (this.row.isleft) {
            this.col.bundleData = data;
        }

        if (data.type === 'variable') {
            this.parameterTarget.getContainer().style.display = 'none';
            this.variableTarget.getContainer().style.display = '';
            this.assignTargetType = 'variable';
            this.propContainer.textContent = '.';
            this.propContainer.style.color = 'white';
            this.variableTarget.setValue(data);
        } else {
            this.variableTarget.getContainer().style.display = 'none';
            this.parameterTarget.getContainer().style.display = '';
            this.assignTargetType = 'parameter';
            this.propContainer.textContent = '.';
            this.propContainer.style.color = 'white';
            this.parameterTarget.setValue(data);
        }
    }

    /**
     * Refresh the attribute cell menus with the given properties.
     *
     * @param {Array} properties - Array of property objects with label/name
     */
    refreshAttributeCellMenus(properties) {
        const menuItems = [];
        const self = this;

        const buildMenuItem = function (prop) {
            menuItems.push({
                label: prop.label || prop.name,
                onClick: function () {
                    self.variable = prop;
                    self.propContainer.textContent = prop.label || prop.name;
                }
            });
        };

        for (const prop of properties) {
            buildMenuItem(prop);
        }

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
     * Initialize the context menu for a top condition cell.
     * Includes options for configuring conditions, adding rows/columns,
     * deleting, clearing, copying, and pasting.
     */
    initTopMenu() {
        this.istop = true;
        const self = this;

        this.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '配置条件',
                icon: 'glyphicon glyphicon-cog',
                onClick: function () {
                    const dialog = document.createElement('div');
                    if (!self.cellCondition) {
                        self.cellCondition = new ruleforge.CellCondition('<div/>');
                    }
                    self.cellCondition.renderTo(dialog);
                    window.bootbox.dialog('配置条件', dialog, [], [{
                        name: 'hide.bs.modal',
                        callback: function () {
                            self.conditionContainer.innerHTML = '';
                            self.conditionContainer.appendChild(self.cellCondition.getDisplayContainer());
                        }
                    }], true);
                }
            }, {
                label: '添加条件列',
                icon: 'glyphicon glyphicon-plus',
                name: 'salience',
                defaultValue: '10',
                editorType: 1,
                onClick: function () {
                    self.row.table.addNewTopColumn(self);
                    window._setDirty();
                }
            }, {
                label: '添加条件行',
                icon: 'glyphicon glyphicon-plus-sign',
                onClick: function () {
                    self.row.table.addNewTopRow(self);
                    window._setDirty();
                }
            }, {
                label: '删除行',
                icon: 'glyphicon glyphicon-minus',
                onClick: function () {
                    MsgBox.confirm('真的要删除当前单元格所在的所有行？', function () {
                        self.removeRow();
                        window._setDirty();
                    });
                }
            }, {
                label: '删除列',
                icon: 'glyphicon glyphicon-minus-sign',
                onClick: function () {
                    MsgBox.confirm('真的要删除当前单元格所在的所有列？', function () {
                        self.removeCol();
                        window._setDirty();
                    });
                }
            }, {
                label: '清空条件',
                icon: 'glyphicon glyphicon-trash',
                onClick: function () {
                    MsgBox.confirm('真的要清空当前单元格条件？', function () {
                        if (self.cellCondition) {
                            self.cellCondition.clean();
                            self.conditionContainer.innerHTML = '无';
                            self.conditionContainer.style.color = 'gray';
                        }
                        window._setDirty();
                    });
                }
            }, {
                label: '复制',
                icon: 'glyphicon glyphicon-copy',
                onClick: function () {
                    const xml = self.cellCondition.toXml();
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
                        self.conditionContainer.innerHTML = '';
                        self.conditionContainer.appendChild(self.cellCondition.getDisplayContainer());
                    });
                }
            }]
        });

        this.td.addEventListener('contextmenu', function (e) {
            self.menu.show(e);
        });
    }

    /**
     * Initialize the context menu for a left condition cell.
     * Similar to top menu but with different icon assignments for row/column operations.
     */
    initLeftMenu() {
        this.istop = false;
        const self = this;

        this.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '配置条件',
                icon: 'glyphicon glyphicon-cog',
                onClick: function () {
                    const dialog = document.createElement('div');
                    if (!self.cellCondition) {
                        self.cellCondition = new ruleforge.CellCondition('<div/>');
                    }
                    self.cellCondition.renderTo(dialog);
                    window.bootbox.dialog('配置条件', dialog, [], [{
                        name: 'hide.bs.modal',
                        callback: function () {
                            self.conditionContainer.innerHTML = '';
                            self.conditionContainer.appendChild(self.cellCondition.getDisplayContainer());
                        }
                    }], true);
                }
            }, {
                label: '添加条件列',
                icon: 'glyphicon glyphicon-plus',
                name: 'salience',
                defaultValue: '10',
                editorType: 1,
                onClick: function () {
                    self.row.table.addNewLeftColumn(self);
                    window._setDirty();
                }
            }, {
                label: '添加条件行',
                icon: 'glyphicon glyphicon-plus-sign',
                onClick: function () {
                    self.row.table.addNewLeftRow(self);
                    window._setDirty();
                }
            }, {
                label: '删除行',
                icon: 'glyphicon glyphicon-minus-sign',
                onClick: function () {
                    MsgBox.confirm('真的要删除当前单元格所在的所有行？', function () {
                        self.removeRow();
                        window._setDirty();
                    });
                }
            }, {
                label: '删除列',
                icon: 'glyphicon glyphicon-minus',
                onClick: function () {
                    MsgBox.confirm('真的要删除当前单元格所在的所有列？', function () {
                        self.removeCol();
                        window._setDirty();
                    });
                }
            }, {
                label: '清空条件',
                icon: 'glyphicon glyphicon-trash',
                onClick: function () {
                    MsgBox.confirm('真的要清空当前单元格条件？', function () {
                        if (self.cellCondition) {
                            self.cellCondition.clean();
                            self.conditionContainer.innerHTML = '无';
                            self.conditionContainer.style.color = 'gray';
                        }
                        window._setDirty();
                    });
                }
            }, {
                label: '复制',
                icon: 'glyphicon glyphicon-copy',
                onClick: function () {
                    const xml = self.cellCondition.toXml();
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
                        self.conditionContainer.innerHTML = '';
                        self.conditionContainer.appendChild(self.cellCondition.getDisplayContainer());
                    });
                }
            }]
        });

        this.td.addEventListener('contextmenu', function (e) {
            self.menu.show(e);
        });
    }

    /**
     * Remove all rows spanned by this condition cell.
     */
    removeRow() {
        if (this.rowCanRemove()) {
            let span = this.getRowSpan();
            span || (span = 1);
            const rows = this.table.rows;
            const startIdx = rows.indexOf(this.row);
            const endIdx = startIdx + span;

            for (let i = startIdx; i < endIdx; i++) {
                const row = rows[i];
                row.remove();

                // If this is a top row and it contains the header cell, move header to next row
                if (this.istop) {
                    this.table.adjustHeaderCellRowSpan(false);
                    if (this.table.headerCell.row.id === row.id) {
                        const nextRow = rows[i + 1];
                        this.table.headerCell.row = nextRow;
                        nextRow.tr.prepend(this.table.headerCell.td);
                    }
                }
            }

            const removeCount = endIdx - startIdx;
            rows.splice(startIdx, removeCount);
        } else {
            window.bootbox.alert('当前行至少要保留一行，不能被删除！');
        }
    }

    /**
     * Remove all columns spanned by this condition cell.
     */
    removeCol() {
        if (this.colCanRemove()) {
            let span = this.getColSpan();
            span || (span = 1);
            const columns = this.table.columns;
            const startIdx = columns.indexOf(this.col);
            const endIdx = startIdx + span;

            for (let i = startIdx; i < endIdx; i++) {
                columns[i].remove();
                if (!this.istop) {
                    this.table.adjustHeaderCellColSpan(false);
                }
            }

            const removeCount = endIdx - startIdx;
            columns.splice(startIdx, removeCount);
        } else {
            window.bootbox.alert('当前列至少要保留一列，不能被删除！');
        }
    }

    /**
     * Check if the row can be removed (at least one row of the same type must remain).
     * @returns {boolean}
     */
    rowCanRemove() {
        let count = 0;
        const rows = this.table.rows;
        if (this.row.istop) {
            for (let i = 0; i < rows.length; i++) {
                if (rows[i].istop) count++;
            }
        } else {
            for (let i = 0; i < rows.length; i++) {
                if (!rows[i].istop) count++;
            }
        }
        return count > 1 && this.getRowSpan() !== count;
    }

    /**
     * Check if the column can be removed (at least one column of the same type must remain).
     * @returns {boolean}
     */
    colCanRemove() {
        let count = 0;
        const columns = this.table.columns;
        if (this.col.istop) {
            for (let i = 0; i < columns.length; i++) {
                if (columns[i].istop) count++;
            }
        } else {
            for (let i = 0; i < columns.length; i++) {
                if (!columns[i].istop) count++;
            }
        }
        return count > 1 && this.getColSpan() !== count;
    }

    /**
     * Serialize this condition cell to XML.
     * @returns {string} XML representation
     */
    toXml() {
        let xml = '<condition-cell row="' + (this.table.rows.indexOf(this.row) + 1) +
                  '" col="' + (this.table.columns.indexOf(this.col) + 1) +
                  '" rowspan="' + this.getRowSpan() +
                  '" colspan="' + this.getColSpan() + '">';
        xml += this.cellCondition.toXml();
        xml += '</condition-cell>';
        return xml;
    }
}
