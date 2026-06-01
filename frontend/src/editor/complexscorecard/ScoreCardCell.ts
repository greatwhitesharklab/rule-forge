/**
 * ConditionCell - A cell with condition configuration in the complex scorecard grid.
 *
 * These cells appear at the intersection of content rows and condition columns.
 * They display a variable/parameter selector, a condition joint, and a context
 * menu for configuring conditions, adding/deleting rows, and copy/paste.
 */

import HighlightCell from './HighlightCell';
import RowContext from './RowContext';
import { generateContainer } from '../common/URule';
import { copyCellData, pasteCellData } from '../crosstab/cellDataUtils.js';

declare const MsgBox: {
    confirm(message: string, callback: () => void): void;
};

export default class ConditionCell extends HighlightCell {
    contentRow: any;
    conditionCol: any;
    propContainer!: HTMLElement;
    container!: HTMLElement;
    cellCondition?: any;
    variableLabel?: string;
    variableName?: string;
    datatype?: string;

    constructor(rowContext: RowContext, contentRow: any, conditionCol: any) {
        super(rowContext);
        this.contentRow = contentRow;
        this.conditionCol = conditionCol;
        this.init(rowContext);
    }

    init(rowContext: RowContext): void {
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
            (window as any)._currentConditionCell = self;
        });

        // Context menu
        const menuConfig: MenuConfig = {
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
                        window._setDirty?.();
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
                    pasteCellData('condition', function (data: any) {
                        if (self.cellCondition) {
                            self.cellCondition.clean();
                        }
                        self.cellCondition = new (ruleforge as any).CellCondition('<div/>');
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

    initData(data: any): void {
        if (data.joint) {
            this.cellCondition = new (ruleforge as any).CellCondition('<div/>');
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

    refreshVariableMenus(): void {
        const variables = this.conditionCol.variables || [];
        const clickFn = this.buildClickFunction();
        const menuItems: MenuItemConfig[] = [];

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
        if ((this.propContainer as any).menu) {
            ((this.propContainer as any).menu as MenuInstance).setConfig({ menuItems: menuItems });
        } else {
            (this.propContainer as any).menu = new RuleForge.menu.Menu({ menuItems: menuItems });
            this.propContainer.addEventListener('click', function (e) {
                ((self.propContainer as any).menu as MenuInstance).show(e);
            });
        }
    }

    buildClickFunction(): (item: MenuItemConfig) => void {
        const self = this;
        return function (item: MenuItemConfig) {
            self.variableLabel = item.label;
            self.variableName = item.name;
            self.datatype = item.datatype;
            self.propContainer.innerHTML = self.variableLabel || '';
            self.propContainer.style.color = '#1d1d1d';
        };
    }

    configCondition(container: HTMLElement): void {
        const dialog = document.createElement('div');
        if (!this.cellCondition) {
            this.cellCondition = new (ruleforge as any).CellCondition('<div/>');
        }
        const self = this;
        this.cellCondition.renderTo(dialog);
        window.bootbox.dialog({
            title: '配置条件',
            message: dialog.outerHTML,
            closeButton: true,
            callback: function () {
                container.innerHTML = '';
                container.appendChild(self.cellCondition.getDisplayContainer());
            }
        });
    }

    increaseRowSpan(): void {
        let rowspan: number = this.td.rowSpan;
        rowspan || (rowspan = 1);
        rowspan++;
        this.td.rowSpan = rowspan;
    }

    insertRow(complexTable: any): void {
        const rowContext = new RowContext(complexTable);
        rowContext.setRefConditionCell(this);
        complexTable.addRow(rowContext);
    }

    deleteRow(complexTable: any): void {
        let rowspan: number = this.td.rowSpan;
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
                    let cellRowspan: number = cell.td.rowSpan;
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
                    let spannedRowspan: number = spannedCell.td.rowSpan;
                    spannedRowspan--;
                    spannedCell.td.rowSpan = spannedRowspan;
                }
            }
            row.tr.remove();
        }

        complexTable.contentRows.splice(rowIndex, rowspan);
    }

    _findNextRowCellInfo(startRowIdx: number, complexTable: any, nextCol: any): {
        targetCell: any;
        targetRow: any;
        targetRowConditionCellIndex: number;
    } {
        let targetCell: any = null;
        let targetRow: any = null;
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

    toXml(): string {
        return this.cellCondition ? this.cellCondition.toXml() : '';
    }
}

/**
 * ActionCell - A cell with value input in the complex scorecard grid.
 *
 * These cells appear at the intersection of content rows and action columns
 * (score columns or custom columns). They contain an InputType widget
 * for entering values and a context menu for clear/copy/paste.
 */
export class ActionCell extends HighlightCell {
    contentRow: any;
    actionCol: any;
    container!: HTMLElement;
    inputType: any;

    constructor(rowContext: RowContext, contentRow: any, actionCol: any) {
        super(rowContext);
        this.contentRow = contentRow;
        this.actionCol = actionCol;
        this.init();
    }

    init(): void {
        const self = this;

        const container = document.createElement('div');
        container.style.cssText = 'position: relative;';
        this.container = container;
        this.td.appendChild(container);

        // Click clears current condition cell selection
        this.td.addEventListener('click', function () {
            (window as any)._currentConditionCell = null;
        });

        this.inputType = new (ruleforge as any).InputType(null, '无');
        container.appendChild(this.inputType.getContainer());

        // Context menu
        const menuConfig: MenuConfig = {
            menuItems: [{
                label: '清空',
                icon: 'glyphicon glyphicon-trash',
                onClick: function () {
                    MsgBox.confirm('真的要清空当前单元格内容吗？', function () {
                        self.inputType.getContainer().remove();
                        self.inputType = new (ruleforge as any).InputType(null, '无');
                        self.container.appendChild(self.inputType.getContainer());
                        window._setDirty?.();
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
                    pasteCellData('value', function (data: any) {
                        self.inputType.getContainer().remove();
                        self.inputType = new (ruleforge as any).InputType(null, '无');
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

    toXml(): string {
        return this.inputType.toXml();
    }

    initData(data: any): void {
        if (data && data.value) {
            const value = data.value;
            this.inputType.setValueType(value.valueType, value);
        }
    }
}
