/**
 * ContentRow - A content row in the complex scorecard grid.
 *
 * Each content row contains condition cells (one per condition column)
 * and action cells (one per action column). Rows support cell
 * initialization from server data and adding new cells when
 * columns are inserted.
 *
 * Extracted from the complexScoreCard webpack bundle (module 350).
 */

import BaseRow from './BaseRow.js';
import ConditionCell from './ScoreCardCell.js';
import {ActionCell} from './ScoreCardCell.js';

export default class ContentRow extends BaseRow {
    /**
     * @param {RowContext} rowContext - The row context
     */
    constructor(rowContext) {
        super();
        this.id = rowContext.complexTable.nextId();
        this.conditionCells = [];
        this.actionCells = [];
        this.init(rowContext);
    }

    /**
     * Initialize the row by creating condition and action cells.
     *
     * @param {RowContext} rowContext - The row context
     */
    init(rowContext) {
        this.tr = $('<tr></tr>');
        this.initNewConditionCell(rowContext);
        this.initNewActionCell(rowContext);
    }

    /**
     * Create condition cells for this row.
     * If inserting after an existing cell, cells before the insertion point
     * get their rowspan increased, and new cells are created after.
     * Otherwise, cells are created for all condition columns (possibly with data).
     *
     * @param {RowContext} rowContext - The row context
     */
    initNewConditionCell(rowContext) {
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
                    this.tr.append(cell.td);
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
                        this.tr.append(cell.td);
                        cell.initData(cellData);
                        const rowspan = cellData.rowspan;
                        cell.td.prop('rowspan', rowspan);
                    }
                } else {
                    const cell = new ConditionCell(rowContext, this, col);
                    this.conditionCells.push(cell);
                    this.tr.append(cell.td);
                }
                cellIndex++;
            }
        }
    }

    /**
     * Find a cell in the data array by column index.
     *
     * @param {Array} cells - Array of cell data objects
     * @param {number} colIndex - Column index to find
     * @returns {Object|null} Cell data or null
     */
    findCell(cells, colIndex) {
        for (const cell of cells) {
            if (cell.col === colIndex) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Create action cells for this row.
     *
     * @param {RowContext} rowContext - The row context
     */
    initNewActionCell(rowContext) {
        const rowCells = rowContext.rowCells;
        const complexTable = rowContext.complexTable;
        let cellIndex = complexTable.conditionColumns.length;

        for (const actionCol of complexTable.actionColumns) {
            const cell = new ActionCell(rowContext, this, actionCol);
            this.actionCells.push(cell);
            this.tr.append(cell.td);

            if (rowCells) {
                const cellData = this.findCell(rowCells, cellIndex);
                if (cellData) {
                    cell.initData(cellData);
                }
            }
            cellIndex++;
        }
    }

    /**
     * Add a new condition cell when a condition column is inserted.
     *
     * @param {RowContext} rowContext - The row context
     * @param {ConditionColumn} conditionCol - The new condition column
     */
    addNewConditionCell(rowContext, conditionCol) {
        const complexTable = rowContext.complexTable;

        // If inserting at the end
        if (!rowContext.before && rowContext.refHeaderCellIndex === complexTable.headerRow.conditionHeaders.length - 1) {
            const cell = new ConditionCell(rowContext, this, conditionCol);
            const len = this.conditionCells.length;
            if (len === 0) {
                this.tr.children(':first-child').before(cell.td);
            } else {
                this.conditionCells[len - 1].td.after(cell.td);
            }
            this.conditionCells.push(cell);
            return;
        }

        // Find the reference cell and insert before or after it
        const refCol = rowContext.refHeaderCell.conditionCol;
        let refCell = null;
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
            const rowspan = refCell.td.prop('rowspan');
            if (rowspan) {
                newCell.td.prop('rowspan', rowspan);
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

    /**
     * Add a new action cell when an action column is inserted.
     *
     * @param {RowContext} rowContext - The row context
     * @param {ActionColumn} actionCol - The new action column
     */
    addNewActionCell(rowContext, actionCol) {
        const complexTable = rowContext.complexTable;
        const refHeader = rowContext.refHeaderCell;
        const newCell = new ActionCell(rowContext, this, actionCol);
        const refIndex = complexTable.headerRow.actionHeaders.indexOf(refHeader);
        const refCell = this.actionCells[refIndex];

        if (rowContext.before) {
            refCell.td.before(newCell.td);
            if (refIndex === 0) {
                this.actionCells[0].td.css('border-left', 'inherit');
            }
            this.actionCells.splice(refIndex, 0, newCell);
        } else {
            refCell.td.after(newCell.td);
            this.actionCells.splice(refIndex + 1, 0, newCell);
        }
    }
}
