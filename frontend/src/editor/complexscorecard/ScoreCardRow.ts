/**
 * ContentRow - A content row in the complex scorecard grid.
 *
 * Each content row contains condition cells (one per condition column)
 * and action cells (one per action column). Rows support cell
 * initialization from server data and adding new cells when
 * columns are inserted.
 */

import BaseRow from './BaseRow';
import ConditionCell from './ScoreCardCell';
import { ActionCell } from './ScoreCardCell';

export default class ContentRow extends BaseRow {
    id: number;
    conditionCells: ConditionCell[] = [];
    actionCells: ActionCell[] = [];
    num: number = 0;

    constructor(rowContext: import('./RowContext').default) {
        super();
        this.id = rowContext.complexTable.nextId();
        this.conditionCells = [];
        this.actionCells = [];
        this.init(rowContext);
    }

    init(rowContext: import('./RowContext').default): void {
        this.tr = document.createElement('tr');
        this.initNewConditionCell(rowContext);
        this.initNewActionCell(rowContext);
    }

    initNewConditionCell(rowContext: import('./RowContext').default): void {
        const complexTable = rowContext.complexTable;
        const refConditionCell = rowContext.refConditionCell;

        if (refConditionCell) {
            // Inserting a new row - increase rowspan for cells before the ref column
            const refRow = refConditionCell.contentRow;
            const refCol = refConditionCell.conditionCol;
            const refColIndex = complexTable.conditionColumns.indexOf(refCol);

            for (let i = 0; i < complexTable.conditionColumns.length; i++) {
                const col = complexTable.conditionColumns[i];
                if (i < refColIndex) {
                    rowContext.fetchConditionCell(refRow, col).increaseRowSpan();
                } else {
                    const cell = new ConditionCell(rowContext, this, col);
                    this.conditionCells.push(cell);
                    this.tr.appendChild(cell.td);
                }
            }
        } else {
            // Loading from server data
            const rowCells = rowContext.rowCells;
            let cellIndex = 0;

            for (const col of complexTable.conditionColumns) {
                if (rowCells) {
                    const cellData = this.findCell(rowCells, cellIndex);
                    if (cellData) {
                        const cell = new ConditionCell(rowContext, this, col);
                        this.conditionCells.push(cell);
                        this.tr.appendChild(cell.td);
                        cell.initData(cellData);
                        const rowspan = cellData.rowspan;
                        cell.td.rowSpan = rowspan;
                    }
                } else {
                    const cell = new ConditionCell(rowContext, this, col);
                    this.conditionCells.push(cell);
                    this.tr.appendChild(cell.td);
                }
                cellIndex++;
            }
        }
    }

    findCell(cells: any[], colIndex: number): any {
        for (const cell of cells) {
            if (cell.col === colIndex) {
                return cell;
            }
        }
        return null;
    }

    initNewActionCell(rowContext: import('./RowContext').default): void {
        const rowCells = rowContext.rowCells;
        const complexTable = rowContext.complexTable;
        let cellIndex = complexTable.conditionColumns.length;

        for (const actionCol of complexTable.actionColumns) {
            const cell = new ActionCell(rowContext, this, actionCol);
            this.actionCells.push(cell);
            this.tr.appendChild(cell.td);

            if (rowCells) {
                const cellData = this.findCell(rowCells, cellIndex);
                if (cellData) {
                    cell.initData(cellData);
                }
            }
            cellIndex++;
        }
    }

    addNewConditionCell(rowContext: import('./RowContext').default, conditionCol: any): void {
        const complexTable = rowContext.complexTable;

        // If inserting at the end
        if (!rowContext.before && rowContext.refHeaderCellIndex === complexTable.headerRow.conditionHeaders.length - 1) {
            const cell = new ConditionCell(rowContext, this, conditionCol);
            const len = this.conditionCells.length;
            if (len === 0) {
                this.tr.querySelector(':first-child')!.before(cell.td);
            } else {
                this.conditionCells[len - 1].td.after(cell.td);
            }
            this.conditionCells.push(cell);
            return;
        }

        // Find the reference cell and insert before or after it
        const refCol = rowContext.refHeaderCell.conditionCol;
        let refCell: ConditionCell | null = null;
        let refIndex = -1;

        for (let i = 0; i < this.conditionCells.length; i++) {
            const cell = this.conditionCells[i];
            if (cell.conditionCol === refCol) {
                refCell = cell;
                refIndex = i;
                break;
            }
        }

        if (refCell) {
            const newCell = new ConditionCell(rowContext, this, conditionCol);
            const rowspan = refCell.td.rowSpan;
            if (rowspan) {
                newCell.td.rowSpan = rowspan;
            }
            if (rowContext.before) {
                refCell.td.before(newCell.td);
                this.conditionCells.splice(refIndex, 0, newCell);
            } else {
                refCell.td.after(newCell.td);
                this.conditionCells.splice(refIndex + 1, 0, newCell);
            }
        }
    }

    addNewActionCell(rowContext: import('./RowContext').default, actionCol: any): void {
        const complexTable = rowContext.complexTable;
        const refHeader = rowContext.refHeaderCell;
        const newCell = new ActionCell(rowContext, this, actionCol);
        const refIndex = complexTable.headerRow.actionHeaders.indexOf(refHeader);
        const refCell = this.actionCells[refIndex];

        if (rowContext.before) {
            refCell.td.before(newCell.td);
            if (refIndex === 0) {
                this.actionCells[0].td.style.borderLeft = 'inherit';
            }
            this.actionCells.splice(refIndex, 0, newCell);
        } else {
            refCell.td.after(newCell.td);
            this.actionCells.splice(refIndex + 1, 0, newCell);
        }
    }
}
