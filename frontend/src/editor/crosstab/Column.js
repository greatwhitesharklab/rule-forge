/**
 * LeftColumn - A condition column (left side of the crosstab).
 *
 * Left columns bind to a variable or parameter and generate condition cells
 * when inserted. They extend BaseRowCol and use its removeColumn() for deletion.
 *
 * Extracted from the crosstab webpack bundle (module 329).
 */

import BaseRowCol from './BaseRowCol.js';

export class LeftColumn extends BaseRowCol {
    /**
     * @param {CrossTable} table - The parent cross table
     * @param {Cell} parentCell - The cell from which this column branches (optional)
     */
    constructor(table, parentCell) {
        super(table);
        this.parentCell = parentCell;
        this.init();
    }

    /**
     * Insert this column into the table's column list.
     * If it has a parent cell, it is inserted right after the last left-type column.
     */
    init() {
        if (this.parentCell) {
            this.table.adjustHeaderCellColSpan(true);
        }
        const columns = this.table.columns;
        let afterColumn = null;
        for (let i = columns.length - 1; i > -1; i--) {
            const col = columns[i];
            // Check for left-type column (not top-type)
            if (!col.istop) {
                afterColumn = col;
                break;
            }
        }
        if (afterColumn) {
            const insertIndex = columns.indexOf(afterColumn);
            columns.splice(insertIndex + 1, 0, this);
        } else {
            columns.push(this);
        }
        this.createCells();
    }

    /**
     * Initialize column data from server response.
     *
     * @param {Object} data - Column data from server
     * @param {string} [data.bundleDataType] - "variable" or "parameter"
     * @param {number} data.columnNumber - Column number for cell mapping
     */
    initData(data) {
        if (data.bundleDataType) {
            this.bundleData = {
                type: data.bundleDataType,
                variableCategory: data.variableCategory,
                variableLabel: data.variableLabel,
                variableName: data.variableName,
                datatype: data.datatype
            };
        }
        this.columnNumber = data.columnNumber;
    }

    /**
     * Create condition cells in all top rows for this left column.
     * Only creates cells if this column has a parent (sub-column).
     */
    createCells() {
        if (this.parentCell) {
            const rows = this.table.rows;
            for (const row of rows) {
                // Create condition cells only for non-top rows (LeftRow)
                if (!row.istop) {
                    this.newConditionCell(row, this, this.parentCell);
                }
            }
        }
    }

    /**
     * Remove this column from the table.
     */
    remove() {
        this.removeColumn();
    }

    /**
     * Serialize this column to XML.
     * @returns {string} XML representation
     * @throws {string} If bundle data is not configured
     */
    toXml() {
        if (!this.bundleData) {
            throw '条件列必须要配置与之绑定的变量!';
        }
        let xml = '<column number="' + (this.table.columns.indexOf(this) + 1) + '" type="left"';
        xml += this.bundleDataToXml(this.bundleData);
        xml += '></column>';
        return xml;
    }
}

/**
 * TopColumn - A structural column (top side of the crosstab).
 *
 * Top columns are used for grouping and create condition cells in non-top rows.
 * They do not bind to variables themselves.
 *
 * Extracted from the crosstab webpack bundle (module 330).
 */
export class TopColumn extends BaseRowCol {
    /**
     * @param {CrossTable} table - The parent cross table
     * @param {Cell} parentCell - The cell from which this column branches (optional)
     */
    constructor(table, parentCell) {
        super(table);
        this.istop = true;
        this.parentCell = parentCell;
        this.init();
    }

    /**
     * Insert this top column into the table's column list and adjust parent spans.
     */
    init() {
        const columns = this.table.columns;
        if (this.parentCell) {
            const parentCol = this.parentCell.col;
            const parentIndex = columns.indexOf(parentCol);
            let span = this.parentCell.getColSpan();
            if (span) {
                span--;
            }
            columns.splice(parentIndex + span + 1, 0, this);
        } else {
            columns.push(this);
        }
        this.createCells();
        this.spanParentCells();
    }

    /**
     * Initialize column data from server response.
     * @param {Object} data - Column data from server
     * @param {number} data.columnNumber - Column number for cell mapping
     */
    initData(data) {
        this.columnNumber = data.columnNumber;
    }

    /**
     * Create value cells (for left rows) or condition cells (for top rows)
     * depending on the row type, identified by the istop flag.
     */
    createCells() {
        if (this.parentCell) {
            const parentRow = this.parentCell.row;
            const rows = this.table.rows;
            const startIdx = rows.indexOf(parentRow);
            for (let i = startIdx; i < rows.length; i++) {
                const row = rows[i];
                // Use istop flag to determine row type (avoids circular import)
                if (row.istop) {
                    this.newConditionCell(row, this, this.parentCell, true);
                } else {
                    this.newValueCell(row, this, this.parentCell);
                }
            }
        }
    }

    /**
     * Increment colspan of all parent cells above this column.
     */
    spanParentCells() {
        if (!this.parentCell) return;

        const parentRow = this.parentCell.row;
        const rows = this.table.rows;
        const columns = this.table.columns;
        const parentCol = this.parentCell.col;
        const parentColIndex = columns.indexOf(parentCol);

        for (let ri = rows.indexOf(parentRow) - 1; ri > -1; ri--) {
            const row = rows[ri];
            let spanningCell = null;
            for (let ci = parentColIndex; ci > -1; ci--) {
                const col = columns[ci];
                const cell = this.table.getCell(row, col);
                if (cell) {
                    let span = cell.getColSpan();
                    if (span > 0) span--;
                    if (ci + span >= parentColIndex) {
                        spanningCell = cell;
                        break;
                    }
                }
            }
            const td = spanningCell.td;
            let colspan = td.prop('colspan');
            colspan || (colspan = 1);
            colspan++;
            td.prop('colspan', colspan);
        }
    }

    /**
     * Remove this column from the table.
     */
    remove() {
        this.removeColumn();
    }

    /**
     * Serialize this top column to XML.
     * @returns {string} XML representation
     */
    toXml() {
        return '<column number="' + (this.table.columns.indexOf(this) + 1) + '" type="top"></column>';
    }
}
