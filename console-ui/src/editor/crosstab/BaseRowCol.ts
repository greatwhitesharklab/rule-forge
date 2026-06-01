/**
 * BaseRowCol - Base class for Row and Column classes.
 *
 * Provides shared functionality for removing rows/columns,
 * creating condition and value cells, and serializing bundle data to XML.
 *
 * Extracted from the crosstab webpack bundle (module 287).
 */

import Cell from './Cell.js';
import ConditionCell from './ConditionCell.js';
import type CrossTable from './CrossTable';

export interface BundleData {
    type: string;
    variableCategory: string;
    variableLabel?: string;
    variableName?: string;
    datatype?: string;
}

export default class BaseRowCol {
    id: number;
    table: CrossTable;
    istop?: boolean;
    isleft?: boolean;
    bundleData?: BundleData;
    parentCell?: any;
    tr?: HTMLTableRowElement;
    rowNumber?: number;
    columnNumber?: number;

    // Methods defined on subclasses
    initData?(data: any): void;
    remove?(): void;
    toXml(): string { return ''; }
    createCells(): void {}
    spanParentCells(): void {}

    /**
     * @param table - The parent cross table instance
     */
    constructor(table: CrossTable) {
        this.id = table.nextSeq();
        this.table = table;
    }

    /**
     * Remove this row from the table.
     * Handles cell rowspan merging when cells span multiple rows.
     */
    removeRow(): void {
        const columns = this.table.columns;
        const rows = this.table.rows;
        const rowIndex = rows.indexOf(this);
        (this as any).tr.remove();

        for (const col of columns) {
            const cell = this.table.getCell(this, col);
            if (cell) {
                this.table.removeCell(this, col);
                let rowSpan = cell.getRowSpan();
                if (rowSpan > 1) {
                    // Decrement rowspan; ensure at least 1
                    --rowSpan || (rowSpan = 1);
                    cell.td.rowSpan = rowSpan;

                    // Transfer cell to the next row
                    const nextRow = rows[rowIndex + 1];
                    cell.row = nextRow;
                    if ((nextRow as any).istop && (nextRow as any).bundleData) {
                        cell.setBundleData((nextRow as any).bundleData);
                    }
                    this.table.addCell(nextRow, cell.col, cell);

                    // Find the correct insertion point in the DOM
                    let anchorCell: any = null;
                    for (let ci = columns.indexOf(cell.col) + 1; ci < columns.length; ci++) {
                        const nextCol = columns[ci];
                        anchorCell = this.table.getCell(nextRow, nextCol);
                        if (anchorCell) break;
                    }
                    anchorCell.td.before(cell.td);
                } else {
                    cell.remove();
                }
            } else {
                // Cell is part of a span from a previous row; decrement that span
                for (let ri = rowIndex - 1; ri > -1; ri--) {
                    const prevRow = rows[ri];
                    const prevCell = this.table.getCell(prevRow, col);
                    if (prevCell) {
                        let span = prevCell.getRowSpan() - 1;
                        span || (span = 1);
                        prevCell.td.rowSpan = span;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Remove this column from the table.
     * Handles cell colspan merging when cells span multiple columns.
     */
    removeColumn(): void {
        const columns = this.table.columns;
        const colIndex = columns.indexOf(this);
        const rows = this.table.rows;

        for (const row of rows) {
            const cell = this.table.getCell(row, this);
            if (cell) {
                this.table.removeCell(row, this);
                let colSpan = cell.getColSpan();
                if (colSpan > 1) {
                    // Decrement colspan; ensure at least 1
                    --colSpan || (colSpan = 1);
                    cell.td.colSpan = colSpan;

                    // Transfer cell to the next column
                    const nextCol = columns[colIndex + 1];
                    cell.col = nextCol;
                    if ((row as any).isleft && (nextCol as any).bundleData) {
                        cell.setBundleData((nextCol as any).bundleData);
                    }
                    this.table.addCell(row, nextCol, cell);
                } else {
                    cell.remove();
                }
            } else {
                // Cell is part of a span from a previous column; decrement that span
                let prevCell: any = null;
                for (let ci = colIndex - 1; ci > -1; ci--) {
                    const prevCol = columns[ci];
                    prevCell = this.table.getCell(row, prevCol);
                    if (prevCell) break;
                }
                if (prevCell) {
                    let span = prevCell.getColSpan();
                    if (span > 1) {
                        --span || (span = 1);
                        prevCell.td.colSpan = span;
                    }
                }
            }
        }
    }

    /**
     * Create a new condition cell at the given row/column intersection.
     *
     * @param row - The row for the cell
     * @param col - The column for the cell
     * @param parentRowCol - The parent row or column that owns the parent cell
     * @param isTop - Whether this is a top (column-header) condition cell
     * @returns The created ConditionCell
     */
    newConditionCell(row: BaseRowCol, col: BaseRowCol, parentRowCol: BaseRowCol, isTop?: boolean): ConditionCell {
        const cell = new ConditionCell(row, col);
        if (isTop) {
            cell.initTopMenu();
            cell.td.classList.add('top-condition-cell');
            if ((parentRowCol as any).bundleData) {
                cell.setBundleData((parentRowCol as any).bundleData);
            }
        } else {
            cell.initLeftMenu();
            cell.td.classList.add('left-condition-cell');
        }
        if ((this as any).isleft && cell.col.id === (parentRowCol as any).col.id && (parentRowCol as any).bundleData) {
            cell.setBundleData((parentRowCol as any).bundleData);
        }
        this.table.cells.push(cell);
        this.table.addCell(row, col, cell);
        return cell;
    }

    /**
     * Create a new value cell at the given row/column intersection.
     *
     * @param row - The row for the cell
     * @param col - The column for the cell
     * @param parentRowCol - The parent row or column that owns the parent cell
     * @returns The created Cell
     */
    newValueCell(row: BaseRowCol, col: BaseRowCol, parentRowCol: BaseRowCol): Cell {
        const cell = new Cell(row, col);
        this.table.cells.push(cell);
        this.table.addCell(row, col, cell);
        cell.attachColumnStyle();
        return cell;
    }

    /**
     * Serialize bundle data (variable/parameter binding) to XML attributes.
     *
     * @param bundleData - The bundle data object
     * @returns XML attribute string
     */
    bundleDataToXml(bundleData: BundleData): string {
        let xml = ' bundle-data-type="' + bundleData.type + '" ';
        if (bundleData.type === 'variable') {
            if (!bundleData.variableCategory) {
                throw '变量不能为空！';
            }
            xml += 'var-category="' + bundleData.variableCategory + '"';
            if (bundleData.variableName) {
                xml += ' var="' + bundleData.variableName +
                       '" var-label="' + bundleData.variableLabel +
                       '" datatype="' + bundleData.datatype + '"';
            }
        } else {
            if (!bundleData.variableLabel) {
                throw '参数不能为空！';
            }
            xml += ' var-category="参数"' +
                   ' var="' + bundleData.variableName +
                   '" var-label="' + bundleData.variableLabel +
                   '" datatype="' + bundleData.datatype + '"';
        }
        return xml;
    }
}
