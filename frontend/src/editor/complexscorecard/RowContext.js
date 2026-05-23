/**
 * RowContext - Helper object for creating rows and cells in the complex scorecard.
 *
 * Passed around during row/column creation to carry state like the
 * parent table, reference cells, action types, and width hints.
 * Also provides utility methods to find condition/action cells by column.
 *
 * Extracted from the complexScoreCard webpack bundle (module 281).
 */

export default class RowContext {
    /**
     * @param {ComplexScoreCard} complexTable - The parent scorecard table
     */
    constructor(complexTable) {
        this.complexTable = complexTable;
        this.actionType = 0;
    }

    /**
     * Find a condition cell in the given row for the given column,
     * searching upwards through previous rows if not found directly.
     *
     * @param {ContentRow} row - The row to search
     * @param {ConditionColumn} col - The column to search for
     * @returns {ConditionCell|null}
     */
    fetchConditionCell(row, col) {
        let cell = this._findConditionCell(row, col);
        if (cell) return cell;
        const rows = this.complexTable.contentRows;
        for (let i = rows.indexOf(row) - 1; i >= 0; i--) {
            cell = this._findConditionCell(rows[i], col);
            if (cell) break;
        }
        return cell;
    }

    /**
     * Find a condition cell in the given row for the given column.
     *
     * @param {ContentRow} row - The row to search
     * @param {ConditionColumn} col - The column to search for
     * @returns {ConditionCell|null}
     */
    _findConditionCell(row, col) {
        for (const cell of row.conditionCells) {
            if (cell.conditionCol === col) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Find an action cell in the given row for the given column,
     * searching upwards through previous rows if not found directly.
     *
     * @param {ContentRow} row - The row to search
     * @param {ActionColumn} col - The column to search for
     * @returns {ActionCell|null}
     */
    fetchActionCell(row, col) {
        let cell = this._findActionCell(row, col);
        if (cell) return cell;
        const rows = this.complexTable.contentRows;
        for (let i = rows.indexOf(row) - 1; i >= 0; i--) {
            cell = this._findActionCell(rows[i], col);
            if (cell) break;
        }
        return cell;
    }

    /**
     * Find an action cell in the given row for the given column.
     *
     * @param {ContentRow} row - The row to search
     * @param {ActionColumn} col - The column to search for
     * @returns {ActionCell|null}
     */
    _findActionCell(row, col) {
        for (const cell of row.actionCells) {
            if (cell.contentRow === row && cell.actionCol === col) {
                return cell;
            }
        }
        return null;
    }

    setRefHeaderCell(headerCell) {
        this.refHeaderCell = headerCell;
        this.refHeaderCellIndex = this.complexTable.headerRow.conditionHeaders.indexOf(headerCell);
        if (this.refHeaderCellIndex === -1) {
            this.refHeaderCellIndex = this.complexTable.headerRow.actionHeaders.indexOf(headerCell);
        }
    }

    setBefore(before) {
        this.before = before;
    }

    setRefConditionCell(cell) {
        this.refConditionCell = cell;
    }

    setActionType(actionType) {
        this.actionType = actionType;
    }

    setRowCells(cells) {
        this.rowCells = cells;
    }

    setWidth(width) {
        this.width = width;
    }

    setCustomActionHeaderLabel(label) {
        this.customActionHeaderLabel = label;
    }
}
