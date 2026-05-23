/**
 * ConditionColumn - A condition column in the complex scorecard grid.
 *
 * Condition columns bind to a variable category and create condition cells
 * in all existing content rows. They also add a header cell to the header row.
 *
 * Extracted from the complexScoreCard webpack bundle (module 347).
 */

import RowContext from './RowContext.js';

export default class ConditionColumn {
    /**
     * @param {RowContext} rowContext - The row context
     */
    constructor(rowContext) {
        this.complexTable = rowContext.complexTable;
        this.id = rowContext.complexTable.nextId();
        this.init(rowContext);
    }

    /**
     * Create condition cells in all existing content rows and add a header cell.
     *
     * @param {RowContext} rowContext - The row context
     */
    init(rowContext) {
        const complexTable = this.complexTable;

        // Add a condition cell to each existing content row
        for (const row of complexTable.contentRows) {
            row.addNewConditionCell(rowContext, this);
        }

        // Add the header cell
        complexTable.headerRow.addConditionHeader(rowContext, this);
    }

    /**
     * Refresh variable menus in all condition cells belonging to this column.
     */
    refreshConditionCellVariableMenus() {
        const contentRows = this.complexTable.contentRows;
        for (const row of contentRows) {
            for (const cell of row.conditionCells) {
                if (cell.conditionCol === this) {
                    cell.refreshVariableMenus();
                }
            }
        }
    }
}

/**
 * ActionColumn - An action column in the complex scorecard grid.
 *
 * Action columns represent score columns or custom columns. They create
 * action cells in all existing content rows and add a header cell to the header row.
 *
 * Extracted from the complexScoreCard webpack bundle (module 346).
 */
export class ActionColumn {
    /**
     * @param {RowContext} rowContext - The row context
     */
    constructor(rowContext) {
        this.actionType = rowContext.actionType;
        this.id = rowContext.complexTable.nextId();
        this.init(rowContext);
    }

    /**
     * Create action cells in all existing content rows and add a header cell.
     *
     * @param {RowContext} rowContext - The row context
     */
    init(rowContext) {
        const complexTable = rowContext.complexTable;

        // Add an action cell to each existing content row
        for (const row of complexTable.contentRows) {
            row.addNewActionCell(rowContext, this);
        }

        // Add the header cell
        complexTable.headerRow.addActionHeader(rowContext, this);
    }
}
