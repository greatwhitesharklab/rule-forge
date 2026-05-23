/**
 * BaseCell - Minimal base class for all cell types in the crosstab grid.
 *
 * Provides core functionality: ID generation, row/column references,
 * DOM positioning (initCell), rowspan/colspan accessors, and removal.
 * Subclassed by ValueCell and ConditionCell.
 *
 * Extracted from the crosstab webpack bundle (module 334).
 */

export default class BaseCell {
    /**
     * @param {Row} row - The row this cell belongs to
     * @param {Column} col - The column this cell belongs to
     */
    constructor(row, col) {
        this.id = row.table.nextSeq();
        this.table = row.table;
        this.row = row;
        this.col = col;
        this.td = $('<td class="cell" style="vertical-align:middle;position: relative"></td>');
        this.initCell();

        // Click handler for highlighting the row/column
        const self = this;
        const highlightHandler = function () {
            const td = $(this);
            const width = td.get(0).clientWidth;
            const height = td.get(0).clientHeight;
            const highlightDiv = row.table.getHighlightDiv();
            highlightDiv.css({
                width: width + 'px',
                height: height + 'px'
            });
            td.prepend(highlightDiv);
            highlightDiv.currentTD = td;
            td.on('DOMSubtreeModified', function () {
                const w = td.get(0).clientWidth;
                const h = td.get(0).clientHeight;
                highlightDiv.css({
                    width: w + 'px',
                    height: h + 'px'
                });
            });
        };
        this.td.click(highlightHandler);
        this.td.contextmenu(highlightHandler);
    }

    /**
     * Get the rowspan of this cell's TD element.
     * @returns {number}
     */
    getRowSpan() {
        let rowspan = this.td.prop('rowspan');
        rowspan || (rowspan = 0);
        return rowspan;
    }

    /**
     * Get the colspan of this cell's TD element.
     * @returns {number}
     */
    getColSpan() {
        let colspan = this.td.prop('colspan');
        colspan || (colspan = 0);
        return colspan;
    }

    /**
     * Insert this cell's TD into the row at the correct position.
     * Looks for the nearest preceding cell in the column order.
     */
    initCell() {
        const columns = this.table.columns;
        let prevCell = null;
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
            this.row.tr.append(this.td);
        }
    }

    /**
     * Remove this cell from the table and DOM.
     */
    remove() {
        const cellIndex = this.table.cells.indexOf(this);
        this.table.cells.splice(cellIndex, 1);
        this.td.remove();
    }
}
