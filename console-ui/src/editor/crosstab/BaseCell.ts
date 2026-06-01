/**
 * BaseCell - Minimal base class for all cell types in the crosstab grid.
 *
 * Provides core functionality: ID generation, row/column references,
 * DOM positioning (initCell), rowspan/colspan accessors, and removal.
 * Subclassed by ValueCell and ConditionCell.
 *
 * Extracted from the crosstab webpack bundle (module 334).
 */

import type CrossTable from './CrossTable';
import type BaseRowCol from './BaseRowCol';

export default class BaseCell {
    id: number;
    table: CrossTable;
    row: BaseRowCol;
    col: BaseRowCol;
    td: HTMLTableCellElement;

    // Methods defined on subclasses
    setBundleData?(data: any): void;
    initData?(data: any): void;

    /**
     * @param row - The row this cell belongs to
     * @param col - The column this cell belongs to
     */
    constructor(row: BaseRowCol, col: BaseRowCol) {
        this.id = row.table.nextSeq();
        this.table = row.table;
        this.row = row;
        this.col = col;
        const td = document.createElement('td');
        td.className = 'cell';
        td.style.cssText = 'vertical-align:middle;position: relative';
        this.td = td;
        this.initCell();

        // Click handler for highlighting the row/column
        const highlightHandler = function (this: HTMLTableCellElement) {
            const tdEl = this;
            const width = tdEl.clientWidth;
            const height = tdEl.clientHeight;
            const highlightDiv = row.table.getHighlightDiv();
            highlightDiv.style.width = width + 'px';
            highlightDiv.style.height = height + 'px';
            tdEl.prepend(highlightDiv);
            (highlightDiv as any).currentTD = tdEl;
            tdEl.addEventListener('DOMSubtreeModified', function () {
                const w = tdEl.clientWidth;
                const h = tdEl.clientHeight;
                highlightDiv.style.width = w + 'px';
                highlightDiv.style.height = h + 'px';
            });
        };
        this.td.addEventListener('click', highlightHandler);
        this.td.addEventListener('contextmenu', highlightHandler);
    }

    /**
     * Get the rowspan of this cell's TD element.
     */
    getRowSpan(): number {
        let rowspan = this.td.rowSpan;
        rowspan || (rowspan = 0);
        return rowspan;
    }

    /**
     * Get the colspan of this cell's TD element.
     */
    getColSpan(): number {
        let colspan = this.td.colSpan;
        colspan || (colspan = 0);
        return colspan;
    }

    /**
     * Insert this cell's TD into the row at the correct position.
     * Looks for the nearest preceding cell in the column order.
     */
    initCell(): void {
        const columns = this.table.columns;
        let prevCell: BaseCell | null = null;
        for (let ci = columns.indexOf(this.col) - 1; ci > -1; ci--) {
            const prevCol = columns[ci];
            const cell = this.table.getCell(this.row, prevCol);
            if (cell) {
                prevCell = cell;
                break;
            }
        }
        if (prevCell) {
            prevCell.td.after(this.td);
        } else {
            (this.row as any).tr.appendChild(this.td);
        }
    }

    /**
     * Remove this cell from the table and DOM.
     */
    remove(): void {
        const cellIndex = this.table.cells.indexOf(this);
        this.table.cells.splice(cellIndex, 1);
        this.td.remove();
    }
}
