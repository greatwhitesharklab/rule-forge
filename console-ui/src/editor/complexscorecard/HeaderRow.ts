/**
 * HeaderRow - The header row of the complex scorecard grid.
 *
 * Manages condition header cells and action header cells that form the
 * column headers of the scorecard table.
 */

import ResizableHeaderCell from './ResizableHeaderCell';
import RowContext from './RowContext';

declare const MsgBox: {
    confirm(message: string, callback: () => void): void;
};

export default class HeaderRow {
    complexTable: import('./ComplexScoreCard').default;
    conditionHeaders: ConditionHeaderCell[] = [];
    actionHeaders: ActionHeaderCell[] = [];
    tr: HTMLTableRowElement;

    constructor(complexTable: import('./ComplexScoreCard').default) {
        this.complexTable = complexTable;
        this.conditionHeaders = [];
        this.actionHeaders = [];
        this.tr = document.createElement('tr');
    }

    addConditionHeader(rowContext: RowContext, conditionCol: any): void {
        const headerCell = new ConditionHeaderCell(rowContext, conditionCol);
        this.conditionHeaders.push(headerCell);
        this.tr.appendChild(headerCell.td);
    }

    addActionHeader(rowContext: RowContext, actionCol: any): void {
        const headerCell = new ActionHeaderCell(rowContext, rowContext.actionType, actionCol);
        this.actionHeaders.push(headerCell);
        this.tr.appendChild(headerCell.td);
    }
}

/**
 * ConditionHeaderCell - Header cell for a condition column.
 *
 * Displays a variable/parameter category selector and context menu
 * for inserting/deleting condition columns.
 */
class ConditionHeaderCell extends ResizableHeaderCell {
    complexTable: import('./ComplexScoreCard').default;
    declare conditionCol: any;
    labelContainer!: HTMLSpanElement;
    variableCategoryList: any[] = [];
    parameterList: any[] = [];

    constructor(rowContext: RowContext, conditionCol: any) {
        super(rowContext);
        this.complexTable = rowContext.complexTable;
        this.conditionCol = conditionCol;
        this.conditionCol.width = 200;
        this.variableCategoryList = [];
        this.parameterList = [];
        this.init(rowContext);
    }

    init(rowContext: RowContext): void {
        this.td.style.width = '200px';

        const iconDiv = document.createElement('div');
        iconDiv.innerHTML = '<i class="glyphicon glyphicon-filter"></i>';
        this.td.appendChild(iconDiv);

        const labelContainer = document.createElement('span');
        labelContainer.style.color = '#727171';
        labelContainer.textContent = '请选择参数或变量';
        this.labelContainer = labelContainer;
        iconDiv.appendChild(labelContainer);

        // Register for variable/parameter library updates
        (window as any)._VariableValueArray.push(this);
        (window as any)._ParameterValueArray.push(this);

        this.initMenu([]);
        if ((window as any)._uruleEditorVariableLibraries) {
            this.initMenu((window as any)._uruleEditorVariableLibraries);
        }
        if ((window as any)._uruleEditorParameterLibraries) {
            this.initMenu((window as any)._uruleEditorParameterLibraries, 1);
        }
    }

    insertColumn(complexTable: import('./ComplexScoreCard').default, before: boolean): void {
        const rowContext = new RowContext(complexTable);
        rowContext.setRefHeaderCell(this);
        rowContext.setBefore(before);
        complexTable.addConditionColumn(rowContext);
        window._setDirty?.();
    }

    deleteColumn(complexTable: import('./ComplexScoreCard').default): void {
        if (complexTable.headerRow.conditionHeaders.length < 2) {
            window.bootbox.alert('条件列至少要有一列！');
            return;
        }

        // Remove from variable value array
        const varIndex = (window as any)._VariableValueArray.indexOf(this);
        if (varIndex > -1) {
            (window as any)._VariableValueArray.splice(varIndex, 1);
        }

        // Remove header cell
        let headerIndex = complexTable.headerRow.conditionHeaders.indexOf(this);
        complexTable.headerRow.conditionHeaders.splice(headerIndex, 1);
        this.td.remove();

        // Remove condition cells from all content rows
        for (const row of complexTable.contentRows) {
            let cellToRemove: any = null;
            for (const cell of row.conditionCells) {
                if (cell.conditionCol === this.conditionCol) {
                    cellToRemove = cell;
                    break;
                }
            }
            if (cellToRemove) {
                headerIndex = row.conditionCells.indexOf(cellToRemove);
                row.conditionCells.splice(headerIndex, 1);
                cellToRemove.td.remove();
            }
        }

        // Remove from condition columns
        headerIndex = complexTable.conditionColumns.indexOf(this.conditionCol);
        complexTable.conditionColumns.splice(headerIndex, 1);
        window._setDirty?.();
    }

    updateLabel(data: any): void {
        if (data.variableCategory) {
            const col = this.conditionCol;
            col.variableCategory = data.variableCategory;
            col.variables = data.variables;
            this.labelContainer.innerHTML = col.variableCategory;
            this.labelContainer.style.color = '#1d1d1d';
            if (col.variableCategory === '参数') {
                this.labelContainer.style.fontWeight = 'bold';
            } else {
                this.labelContainer.style.fontWeight = 'normal';
            }
        } else {
            this.labelContainer.innerHTML = '<span style="color: #727171">请选择参数或变量</span>';
        }
    }

    initMenu(libraries: any[], isParameter?: number): void {
        const self = this;
        const col = this.conditionCol;

        const onCategorySelect = function (item: MenuItemConfig) {
            col.variableCategory = item.label;
            self.labelContainer.innerHTML = col.variableCategory;
            self.labelContainer.style.color = '#1d1d1d';

            if (item.label === '参数') {
                self.labelContainer.style.fontWeight = 'bold';
                let allParams: any[] = [];
                for (const vars of (item.variables || [])) {
                    allParams = allParams.concat(vars);
                }
                col.variables = allParams;
            } else {
                self.labelContainer.style.fontWeight = 'normal';
                col.variables = item.variables || [];
            }
            col.refreshConditionCellVariableMenus(item.variables);
        };

        let menuItems: MenuItemConfig[] = [];
        if (isParameter) {
            this.parameterList = libraries;
            menuItems = this.buildVariableMenus(this.variableCategoryList, onCategorySelect)
                .concat(this.buildParameterMenus(libraries, onCategorySelect));
        } else {
            this.variableCategoryList = libraries;
            menuItems = this.buildVariableMenus(libraries, onCategorySelect)
                .concat(this.buildParameterMenus(this.parameterList, onCategorySelect));
        }

        const menuConfig = this.buildMenuConfig(menuItems);
        const menu = new RuleForge.menu.Menu(menuConfig);
        this.td.addEventListener('contextmenu', function (e) {
            menu.show(e);
        });
    }

    buildVariableMenus(libraries: any[], onClick: (item: MenuItemConfig) => void): MenuItemConfig[] {
        const items: MenuItemConfig[] = [];
        if (libraries.length === 0) return items;

        for (const libGroup of libraries) {
            for (const lib of libGroup) {
                items.push({
                    label: lib.name,
                    variables: lib.variables,
                    icon: 'glyphicon glyphicon-tasks',
                    onClick: onClick
                });
            }
        }
        return items;
    }

    buildParameterMenus(libraries: any[], onClick: (item: MenuItemConfig) => void): MenuItemConfig[] {
        const items: MenuItemConfig[] = [];
        if (libraries.length === 0) return items;
        items.push({
            label: '参数',
            variables: libraries,
            icon: 'glyphicon glyphicon-th-list',
            onClick: onClick
        });
        return items;
    }

    buildMenuConfig(variableMenuItems: MenuItemConfig[]): MenuConfig {
        const complexTable = this.complexTable;
        const self = this;

        const config: MenuConfig = {
            menuItems: [{
                label: '插入条件列',
                icon: 'glyphicon glyphicon-filter',
                subMenu: {
                    menuItems: [{
                        label: '前',
                        icon: 'glyphicon glyphicon-chevron-left',
                        onClick: function () {
                            self.insertColumn(complexTable, true);
                        }
                    }, {
                        label: '后',
                        icon: 'glyphicon glyphicon-chevron-right',
                        onClick: function () {
                            self.insertColumn(complexTable, false);
                        }
                    }]
                }
            }, {
                label: '删除当前条件列',
                icon: 'glyphicon glyphicon-minus-sign',
                onClick: function () {
                    MsgBox.confirm('真的要删除当前条件列？', function () {
                        self.deleteColumn(complexTable);
                    });
                }
            }]
        };

        config.menuItems = config.menuItems.concat(variableMenuItems);
        return config;
    }
}

/**
 * ActionHeaderCell - Header cell for an action column.
 *
 * Displays a label (score or custom) and context menu
 * for inserting/deleting custom columns.
 */
class ActionHeaderCell extends ResizableHeaderCell {
    complexTable: import('./ComplexScoreCard').default;
    declare actionCol: any;
    actionType?: number;
    labelContainer!: HTMLSpanElement;
    customLabel?: string;

    constructor(rowContext: RowContext, actionType: number, actionCol: any) {
        super(rowContext);
        this.complexTable = rowContext.complexTable;
        this.actionCol = actionCol;
        actionCol.actionHeaderCell = this;
        this.actionCol.width = 150;
        this.init(rowContext, actionType);
    }

    init(rowContext: RowContext, actionType: number): void {
        this.td.style.width = '150px';

        const wrapper = document.createElement('div');
        this.td.appendChild(wrapper);
        this.actionType = actionType;

        if (actionType === 1) {
            // Score column
            wrapper.appendChild(document.createElement('span'));
            const labelContainer = document.createElement('span');
            labelContainer.innerHTML = '<span style="color: #ff7734;"><strong>分值</strong></span>';
            this.labelContainer = labelContainer;
            wrapper.appendChild(labelContainer);
        } else if (actionType === 2) {
            // Custom column
            const labelContainer = document.createElement('span');
            labelContainer.style.color = '#31708f';
            this.labelContainer = labelContainer;
            wrapper.appendChild(labelContainer);
        }

        this.buildMenu();
    }

    insertActionColumn(complexTable: import('./ComplexScoreCard').default, actionType: number): void {
        const self = this;
        window.bootbox.prompt('请输入自定义列名称：', function (name) {
            if (name) {
                const rowContext = new RowContext(complexTable);
                rowContext.setCustomActionHeaderLabel(name);
                rowContext.setActionType(actionType);
                rowContext.setRefHeaderCell(self);
                complexTable.addActionColumn(rowContext);
                window._setDirty?.();
            } else {
                window.bootbox.alert('自定义列名不能为空！');
            }
        });
    }

    deleteColumn(complexTable: import('./ComplexScoreCard').default): void {
        // Remove from action headers
        let index = complexTable.headerRow.actionHeaders.indexOf(this);
        complexTable.headerRow.actionHeaders.splice(index, 1);
        this.td.remove();

        // Remove action cells from all content rows
        for (const row of complexTable.contentRows) {
            const cell = row.actionCells[index];
            row.actionCells.splice(index, 1);
            cell.td.remove();
        }

        // Remove from action columns
        index = complexTable.actionColumns.indexOf(this.actionCol);
        complexTable.actionColumns.splice(index, 1);
        if (index === 0) {
            complexTable.rebuildBorder();
        }
        window._setDirty?.();
    }

    updateLabel(data?: any): void {
        if (data) {
            this.customLabel = data;
            this.labelContainer.textContent = data;
        } else {
            this.labelContainer.textContent = '';
        }
    }

    buildMenu(): void {
        const self = this;
        const complexTable = this.complexTable;

        const config: MenuConfig = {
            menuItems: [{
                label: '插入自定义列',
                icon: 'glyphicon glyphicon-tasks',
                onClick: function () {
                    self.insertActionColumn(complexTable, 2);
                }
            }]
        };

        if (this.actionType === 2) {
            config.menuItems.push({
                label: '删除当前自定义列',
                icon: 'glyphicon glyphicon-minus-sign',
                onClick: function () {
                    MsgBox.confirm('真的要删除当前列？', function () {
                        self.deleteColumn(complexTable);
                    });
                }
            });
        }

        const menu = new RuleForge.menu.Menu(config);
        this.td.addEventListener('contextmenu', function (e) {
            menu.show(e);
        });
    }
}
