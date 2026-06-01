/**
 * RowContext - Helper object for creating rows and cells in the complex scorecard.
 *
 * Passed around during row/column creation to carry state like the
 * parent table, reference cells, action types, and width hints.
 * Also provides utility methods to find condition/action cells by column.
 */

export default class RowContext {
    complexTable: import('./ComplexScoreCard').default;
    actionType: number;
    refHeaderCell?: any;
    refHeaderCellIndex: number = -1;
    before?: boolean;
    refConditionCell?: any;
    rowCells?: any[];
    width?: number;
    customActionHeaderLabel?: string;

    constructor(complexTable: import('./ComplexScoreCard').default) {
        this.complexTable = complexTable;
        this.actionType = 0;
    }

    fetchConditionCell(row: any, col: any): any {
        let cell = this._findConditionCell(row, col);
        if (cell) return cell;
        const rows = this.complexTable.contentRows;
        for (let i = rows.indexOf(row) - 1; i >= 0; i--) {
            cell = this._findConditionCell(rows[i], col);
            if (cell) break;
        }
        return cell;
    }

    _findConditionCell(row: any, col: any): any {
        for (const cell of row.conditionCells) {
            if (cell.conditionCol === col) {
                return cell;
            }
        }
        return null;
    }

    fetchActionCell(row: any, col: any): any {
        let cell = this._findActionCell(row, col);
        if (cell) return cell;
        const rows = this.complexTable.contentRows;
        for (let i = rows.indexOf(row) - 1; i >= 0; i--) {
            cell = this._findActionCell(rows[i], col);
            if (cell) break;
        }
        return cell;
    }

    _findActionCell(row: any, col: any): any {
        for (const cell of row.actionCells) {
            if (cell.contentRow === row && cell.actionCol === col) {
                return cell;
            }
        }
        return null;
    }

    setRefHeaderCell(headerCell: any): void {
        this.refHeaderCell = headerCell;
        this.refHeaderCellIndex = this.complexTable.headerRow.conditionHeaders.indexOf(headerCell);
        if (this.refHeaderCellIndex === -1) {
            this.refHeaderCellIndex = this.complexTable.headerRow.actionHeaders.indexOf(headerCell);
        }
    }

    setBefore(before: boolean): void {
        this.before = before;
    }

    setRefConditionCell(cell: any): void {
        this.refConditionCell = cell;
    }

    setActionType(actionType: number): void {
        this.actionType = actionType;
    }

    setRowCells(cells: any[]): void {
        this.rowCells = cells;
    }

    setWidth(width: number): void {
        this.width = width;
    }

    setCustomActionHeaderLabel(label: string): void {
        this.customActionHeaderLabel = label;
    }
}
