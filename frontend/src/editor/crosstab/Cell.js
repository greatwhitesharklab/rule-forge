/**
 * Cell (ValueCell) - Regular intersection value cell in the crosstab grid.
 *
 * These cells appear at the intersection of left rows and top columns.
 * They contain an InputType for entering values and support column/row
 * highlighting and context menus for clear/copy/paste operations.
 *
 * Extends BaseCell.
 *
 * Extracted from the crosstab webpack bundle (module 335).
 */

import BaseCell from './BaseCell.js';
/* bootbox is a global */
import {copyCellData, pasteCellData} from './cellDataUtils.js';

export default class Cell extends BaseCell {
    /**
     * @param {Row} row - The row this cell belongs to
     * @param {Column} col - The column this cell belongs to
     */
    constructor(row, col) {
        super(row, col);
        this.initCellContent();
        this.initMenu();
    }

    /**
     * Initialize cell content with an InputType widget.
     *
     * @param {Object} [data] - Initial value data
     */
    initCellContent(data) {
        const container = document.createElement('div');
        container.style.cssText = 'position: relative';
        this.container = container;
        this.inputType = new ruleforge.InputType(null, '无');
        container.appendChild(this.inputType.getContainer());
        if (data && data.value) {
            const value = data.value;
            this.inputType.setValueType(value.valueType, value);
        }
        this.td.appendChild(container);
    }

    /**
     * Initialize cell data from server response.
     *
     * @param {Object} data - Cell data from server
     * @param {Object} [data.value] - Value data for InputType
     */
    initData(data) {
        if (data.value) {
            const value = data.value;
            this.inputType.setValueType(value.valueType, value);
        }
    }

    /**
     * Attach column-style highlighting behavior.
     * When the cell is clicked, the entire row and column are highlighted,
     * and all other cells are reset to their default backgrounds.
     */
    attachColumnStyle() {
        const rows = this.table.rows;
        const columns = this.table.columns;
        const self = this;

        this.td.addEventListener('click', function () {
            // Reset all cell backgrounds
            for (const row of rows) {
                for (const col of columns) {
                    const cell = self.table.getCell(row, col);
                    if (cell) {
                        if (cell instanceof Cell) {
                            // ValueCell - reset to default
                            cell.td.style.background = 'none';
                        } else if (cell.row.istop) {
                            // Top condition cell
                            cell.td.style.background = '#f3f8ff';
                        } else {
                            // Left condition cell
                            cell.td.style.background = '#f5fdf1';
                        }
                    }
                }
            }

            // Highlight current column
            const highlightCol = self.col;
            for (const row of rows) {
                const cell = self.table.getCell(row, highlightCol);
                if (cell) {
                    cell.td.style.background = '#fcf8e3';
                }
            }

            // Highlight current row
            const highlightRow = self.row;
            for (const col of columns) {
                const cell = self.table.getCell(highlightRow, col);
                if (cell) {
                    cell.td.style.background = '#fcf8e3';
                }
            }
        });
    }

    /**
     * Initialize the context menu for clear/copy/paste operations.
     */
    initMenu() {
        const self = this;
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
                    const xml = self.inputType.toXml();
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
     * Serialize this cell to XML.
     * @returns {string} XML representation
     */
    toXml() {
        let xml = '<value-cell row="' + (this.table.rows.indexOf(this.row) + 1) +
                  '" col="' + (this.table.columns.indexOf(this.col) + 1) + '">';
        xml += this.inputType.toXml();
        xml += '</value-cell>';
        return xml;
    }
}
