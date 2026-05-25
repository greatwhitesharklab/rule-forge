/**
 * HeaderRow - The header row of the complex scorecard grid.
 *
 * Manages condition header cells and action header cells that form the
 * column headers of the scorecard table. Contains the React-based header
 * rendering for condition and action column labels.
 *
 * Extracted from the complexScoreCard webpack bundle (module 353).
 */

import ResizableHeaderCell from './ResizableHeaderCell.js';
import RowContext from './RowContext.js';
/* bootbox is a global */

export default class HeaderRow {
    /**
     * @param {ComplexScoreCard} complexTable - The parent scorecard table
     */
    constructor(complexTable) {
        this.complexTable = complexTable;
        this.conditionHeaders = [];
        this.actionHeaders = [];
        this.tr = document.createElement('tr');
    }

    /**
     * Add a condition header cell for the given condition column.
     *
     * @param {RowContext} rowContext - The row context
     * @param {ConditionColumn} conditionCol - The condition column
     */
    addConditionHeader(rowContext, conditionCol) {
        const headerCell = new ConditionHeaderCell(rowContext, conditionCol);
        this.conditionHeaders.push(headerCell);
        this.tr.appendChild(headerCell.td);
    }

    /**
     * Add an action header cell for the given action column.
     *
     * @param {RowContext} rowContext - The row context
     * @param {ActionColumn} actionCol - The action column
     */
    addActionHeader(rowContext, actionCol) {
        const headerCell = new ActionHeaderCell(rowContext, actionCol);
        this.actionHeaders.push(headerCell);
        this.tr.appendChild(headerCell.td);
    }
}

/**
 * ConditionHeaderCell - Header cell for a condition column.
 *
 * Displays a variable/parameter category selector and context menu
 * for inserting/deleting condition columns.
 *
 * Extracted from the complexScoreCard webpack bundle (module 351).
 */
class ConditionHeaderCell extends ResizableHeaderCell {
    /**
     * @param {RowContext} rowContext - The row context
     * @param {ConditionColumn} conditionCol - The condition column
     */
    constructor(rowContext, conditionCol) {
        super(rowContext);
        this.complexTable = rowContext.complexTable;
        this.conditionCol = conditionCol;
        this.conditionCol.width = 200;
        this.variableCategoryList = [];
        this.parameterList = [];
        this.init(rowContext);
    }

    /**
     * Initialize the condition header cell UI.
     *
     * @param {RowContext} rowContext - The row context
     */
    init(rowContext) {
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
        window._VariableValueArray.push(this);
        window._ParameterValueArray.push(this);

        this.initMenu([]);
        if (window._uruleEditorVariableLibraries) {
            this.initMenu(window._uruleEditorVariableLibraries);
        }
        if (window._uruleEditorParameterLibraries) {
            this.initMenu(window._uruleEditorParameterLibraries, 1);
        }
    }

    /**
     * Insert a new condition column before or after this one.
     *
     * @param {ComplexScoreCard} complexTable - The parent scorecard table
     * @param {boolean} before - True to insert before, false to insert after
     */
    insertColumn(complexTable, before) {
        const rowContext = new RowContext(complexTable);
        rowContext.setRefHeaderCell(this);
        rowContext.setBefore(before);
        complexTable.addConditionColumn(rowContext);
        window._setDirty();
    }

    /**
     * Delete this condition column from the table.
     *
     * @param {ComplexScoreCard} complexTable - The parent scorecard table
     */
    deleteColumn(complexTable) {
        if (complexTable.headerRow.conditionHeaders.length < 2) {
            window.bootbox.alert('条件列至少要有一列！');
            return;
        }

        // Remove from variable value array
        const varIndex = window._VariableValueArray.indexOf(this);
        if (varIndex > -1) {
            window._VariableValueArray.splice(varIndex, 1);
        }

        // Remove header cell
        let headerIndex = complexTable.headerRow.conditionHeaders.indexOf(this);
        complexTable.headerRow.conditionHeaders.splice(headerIndex, 1);
        this.td.remove();

        // Remove condition cells from all content rows
        for (const row of complexTable.contentRows) {
            let cellToRemove = null;
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
        window._setDirty();
    }

    /**
     * Update the label with variable category data from server.
     *
     * @param {Object} data - Column data from server
     * @param {string} [data.variableCategory] - Variable category name
     */
    updateLabel(data) {
        if (data.variableCategory) {
            const col = this.conditionCol;
            col.variableCategory = data.variableCategory;
            col.variables = data.variables;
            this.labelContainer.innerHTML = col.variableCategory;
            this.labelContainer.style.color = '#1d1d1d';
            if (this.variableCategory === '参数') {
                this.labelContainer.style.fontWeight = 'bold';
            } else {
                this.labelContainer.style.fontWeight = 'normal';
            }
        } else {
            this.labelContainer.innerHTML = '<span style="color: #727171">请选择参数或变量</span>';
        }
    }

    /**
     * Initialize the variable/parameter category menu.
     *
     * @param {Array} libraries - Library data
     * @param {number} [isParameter] - If truthy, treat as parameter libraries
     */
    initMenu(libraries, isParameter) {
        const self = this;
        const col = this.conditionCol;

        const onCategorySelect = function (item) {
            col.variableCategory = item.label;
            self.labelContainer.innerHTML = col.variableCategory;
            self.labelContainer.style.color = '#1d1d1d';

            if (item.label === '参数') {
                self.labelContainer.style.fontWeight = 'bold';
                let allParams = [];
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

        let menuItems = [];
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
        const menu = new URule.menu.Menu(menuConfig);
        this.td.addEventListener('contextmenu', function (e) {
            menu.show(e);
        });
    }

    /**
     * Build menu items for variable categories.
     *
     * @param {Array} libraries - Variable library data
     * @param {Function} onClick - Click handler
     * @returns {Array} Menu items
     */
    buildVariableMenus(libraries, onClick) {
        const items = [];
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

    /**
     * Build menu items for parameter categories.
     *
     * @param {Array} libraries - Parameter library data
     * @param {Function} onClick - Click handler
     * @returns {Array} Menu items
     */
    buildParameterMenus(libraries, onClick) {
        const items = [];
        if (libraries.length === 0) return items;
        items.push({
            label: '参数',
            variables: libraries,
            icon: 'glyphicon glyphicon-th-list',
            onClick: onClick
        });
        return items;
    }

    /**
     * Build the full menu config with column operations and variable items.
     *
     * @param {Array} variableMenuItems - Variable/parameter menu items
     * @returns {Object} Menu configuration
     */
    buildMenuConfig(variableMenuItems) {
        const complexTable = this.complexTable;
        const self = this;

        const config = {
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
 *
 * Extracted from the complexScoreCard webpack bundle (module 350).
 */
class ActionHeaderCell extends ResizableHeaderCell {
    /**
     * @param {RowContext} rowContext - The row context
     * @param {number} actionType - 0=regular, 1=score, 2=custom
     * @param {ActionColumn} actionCol - The action column
     */
    constructor(rowContext, actionType, actionCol) {
        super(rowContext);
        this.complexTable = rowContext.complexTable;
        this.actionCol = actionCol;
        actionCol.actionHeaderCell = this;
        this.actionCol.width = 150;
        this.variableCategoryList = [];
        this.parameterList = [];
        this.init(rowContext, actionType);
    }

    /**
     * Initialize the action header cell UI.
     *
     * @param {RowContext} rowContext - The row context
     * @param {number} actionType - The action type
     */
    init(rowContext, actionType) {
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

    /**
     * Insert a new custom action column.
     *
     * @param {ComplexScoreCard} complexTable - The parent scorecard table
     * @param {number} actionType - The action type for the new column
     */
    insertActionColumn(complexTable, actionType) {
        const self = this;
        bootbox.prompt('请输入自定义列名称：', function (name) {
            if (name) {
                const rowContext = new RowContext(complexTable);
                rowContext.setCustomActionHeaderLabel(name);
                rowContext.setActionType(actionType);
                rowContext.setRefHeaderCell(self);
                complexTable.addActionColumn(rowContext);
                window._setDirty();
            } else {
                window.bootbox.alert('自定义列名不能为空！');
            }
        });
    }

    /**
     * Delete this action column from the table.
     *
     * @param {ComplexScoreCard} complexTable - The parent scorecard table
     */
    deleteColumn(complexTable) {
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
        window._setDirty();
    }

    /**
     * Update the label text.
     *
     * @param {Object} [data] - Optional data with customLabel
     */
    updateLabel(data) {
        if (data) {
            this.customLabel = data;
            this.labelContainer.textContent = data;
        } else {
            this.labelContainer.textContent = '';
        }
    }

    /**
     * Build the context menu for this header cell.
     */
    buildMenu() {
        const self = this;
        const complexTable = this.complexTable;

        const config = {
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

        const menu = new URule.menu.Menu(config);
        this.td.addEventListener('contextmenu', function (e) {
            menu.show(e);
        });
    }
}
