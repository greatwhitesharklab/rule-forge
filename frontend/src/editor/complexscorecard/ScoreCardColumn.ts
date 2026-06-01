/**
 * ConditionColumn - A condition column in the complex scorecard grid.
 *
 * Condition columns bind to a variable category and create condition cells
 * in all existing content rows. They also add a header cell to the header row.
 */

import RowContext from './RowContext';

export default class ConditionColumn {
    complexTable: import('./ComplexScoreCard').default;
    id: number;
    variableCategory?: string;
    variables?: any[];
    width: number = 200;
    num?: number;

    constructor(rowContext: RowContext) {
        this.complexTable = rowContext.complexTable;
        this.id = rowContext.complexTable.nextId();
        this.init(rowContext);
    }

    init(rowContext: RowContext): void {
        const complexTable = this.complexTable;

        // Add a condition cell to each existing content row
        for (const row of complexTable.contentRows) {
            row.addNewConditionCell(rowContext, this);
        }

        // Add the header cell
        complexTable.headerRow.addConditionHeader(rowContext, this);
    }

    refreshConditionCellVariableMenus(variables?: any[]): void {
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
 */
export class ActionColumn {
    actionType: number;
    complexTable: import('./ComplexScoreCard').default;
    id: number;
    width: number = 150;
    variableName?: string;
    variableCategory?: string;
    actionHeaderCell?: any;
    num?: number;

    constructor(rowContext: RowContext) {
        this.actionType = rowContext.actionType;
        this.complexTable = rowContext.complexTable;
        this.id = rowContext.complexTable.nextId();
        this.init(rowContext);
    }

    init(rowContext: RowContext): void {
        const complexTable = rowContext.complexTable;

        // Add an action cell to each existing content row
        for (const row of complexTable.contentRows) {
            row.addNewActionCell(rowContext, this);
        }

        // Add the header cell
        complexTable.headerRow.addActionHeader(rowContext, this);
    }
}
