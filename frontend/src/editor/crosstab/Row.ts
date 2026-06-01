/**
 * TopRow - A condition row (top side of the crosstab).
 *
 * Top rows bind to a variable or parameter and generate condition cells
 * when inserted. They are rendered as <tr> elements in the table body.
 *
 * Extracted from the crosstab webpack bundle (module 336).
 */

import BaseRowCol from './BaseRowCol.js';
import type CrossTable from './CrossTable';

export class TopRow extends BaseRowCol {
    declare parentCell?: any;
    declare tr: HTMLTableRowElement;

    /**
     * @param table - The parent cross table
     * @param parentCell - The cell from which this row branches (optional)
     */
    constructor(table: CrossTable, parentCell?: any) {
        super(table);
        this.istop = true;
        this.parentCell = parentCell;
        this.tr = document.createElement('tr');
        this.init();
    }

    /**
     * Insert this top row into the table body.
     * If it has a parent cell, it is inserted after the last existing top row
     * and the header cell's rowspan is adjusted.
     */
    init(): void {
        if (this.parentCell) {
            this.table.adjustHeaderCellRowSpan(true);
        }
        const rows = this.table.rows;
        let lastTopRow: BaseRowCol | null = null;
        for (let i = rows.length - 1; i > -1; i--) {
            const row = rows[i];
            if ((row as any).istop) {
                lastTopRow = row;
                break;
            }
        }
        if (lastTopRow) {
            (lastTopRow as any).tr.after(this.tr);
            const insertIndex = rows.indexOf(lastTopRow);
            rows.splice(insertIndex + 1, 0, this);
        } else {
            (this.table as any).body.appendChild(this.tr);
            rows.push(this);
        }
        this.createCells();
    }

    /**
     * Initialize row data from server response.
     *
     * @param data - Row data from server
     */
    initData(data: any): void {
        if (data.bundleDataType) {
            this.bundleData = {
                type: data.bundleDataType,
                variableCategory: data.variableCategory,
                variableLabel: data.variableLabel,
                variableName: data.variableName,
                datatype: data.datatype
            };
        }
        this.rowNumber = data.rowNumber;
    }

    /**
     * Create condition cells across all top-type columns for this top row.
     * Skip left-type columns. Only creates cells if this row has a parent cell.
     */
    createCells(): void {
        if (this.parentCell) {
            const columns = this.table.columns;
            for (const col of columns) {
                // Create condition cells for top-type columns (TopColumn)
                if ((col as any).istop) {
                    this.newConditionCell(this, col, this.parentCell, true);
                }
            }
        }
    }

    /**
     * Remove this row from the table.
     */
    remove(): void {
        this.removeRow();
    }

    /**
     * Serialize this top row to XML.
     * @returns XML representation
     * @throws If bundle data is not configured
     */
    toXml(): string {
        if (!this.bundleData) {
            throw '条件行必须要配置与之绑定的变量!';
        }
        let xml = '<row number="' + (this.table.rows.indexOf(this) + 1) + '" type="top" ';
        xml += this.bundleDataToXml(this.bundleData);
        xml += '></row>';
        return xml;
    }
}

/**
 * LeftRow - A structural row (left side of the crosstab).
 *
 * Left rows are value rows that create value cells (Cell) for top columns
 * and condition cells for left columns. They do not bind to variables.
 *
 * Extracted from the crosstab webpack bundle (module 331).
 */
export class LeftRow extends BaseRowCol {
    declare parentCell?: any;
    declare tr: HTMLTableRowElement;

    /**
     * @param table - The parent cross table
     * @param parentCell - The cell from which this row branches (optional)
     */
    constructor(table: CrossTable, parentCell?: any) {
        super(table);
        this.isleft = true;
        this.parentCell = parentCell;
        this.tr = document.createElement('tr');
        this.init();
    }

    /**
     * Insert this left row into the table body.
     * Position is calculated based on parent cell's row and rowspan.
     */
    init(): void {
        const rows = this.table.rows;
        let insertAfter = rows.length - 1;
        let spanOffset = 0;

        if (this.parentCell) {
            insertAfter = rows.indexOf((this.parentCell as any).row);
            spanOffset = this.parentCell.getRowSpan();
            if (spanOffset > 0) spanOffset--;
        }
        insertAfter += spanOffset;
        rows.splice(insertAfter + 1, 0, this);
        (rows[insertAfter] as any).tr.after(this.tr);
        this.createCells();
        this.spanParentCells();
    }

    /**
     * Initialize row data from server response.
     * @param data - Row data from server
     */
    initData(data: any): void {
        this.rowNumber = data.rowNumber;
    }

    /**
     * Create value cells for top columns and condition cells for left columns.
     */
    createCells(): void {
        if (this.parentCell) {
            const columns = this.table.columns;
            const parentCol = (this.parentCell as any).col;
            const startIdx = columns.indexOf(parentCol);
            for (let i = startIdx; i < columns.length; i++) {
                const col = columns[i];
                // TopColumn (istop) -> value cell, LeftColumn -> condition cell
                if ((col as any).istop) {
                    this.newValueCell(this, col, this.parentCell);
                } else {
                    this.newConditionCell(this, col, this.parentCell);
                }
            }
        }
    }

    /**
     * Increment rowspan of all parent cells to the left of this row's parent cell.
     */
    spanParentCells(): void {
        if (!this.parentCell) return;

        const rows = this.table.rows;
        const parentRow = (this.parentCell as any).row;
        const parentRowIndex = rows.indexOf(parentRow);
        const columns = this.table.columns;
        const parentCol = (this.parentCell as any).col;
        const parentColIndex = columns.indexOf(parentCol);

        for (let ci = parentColIndex - 1; ci > -1; ci--) {
            const col = columns[ci];
            let spanningCell: any = null;
            for (let ri = 0; ri < rows.length; ri++) {
                const row = rows[ri];
                const cell = this.table.getCell(row, col);
                if (cell) {
                    let span = cell.getRowSpan();
                    if (span > 0) span--;
                    if (ri + span >= parentRowIndex) {
                        spanningCell = cell;
                        break;
                    }
                }
            }
            const td = spanningCell.td;
            let rowspan = td.rowSpan;
            rowspan || (rowspan = 1);
            rowspan++;
            td.rowSpan = rowspan;
        }
    }

    /**
     * Remove this row from the table.
     */
    remove(): void {
        this.removeRow();
    }

    /**
     * Serialize this left row to XML.
     * @returns XML representation
     */
    toXml(): string {
        return '<row number="' + (this.table.rows.indexOf(this) + 1) + '" type="left"></row>';
    }
}
