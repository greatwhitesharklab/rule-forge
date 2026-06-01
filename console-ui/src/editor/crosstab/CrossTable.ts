/**
 * CrossTable - Main crosstab decision table grid class.
 *
 * Manages the overall crosstab grid including rows, columns, cells,
 * the header cell, property configuration, and cross-cell variable binding.
 * Provides methods for initializing from server data, adding/removing rows
 * and columns, and serializing the entire table to XML.
 *
 * Extracted from the crosstab webpack bundle (module 459).
 */

import PropertyConfig from './PropertyConfig.js';
import {LeftColumn, TopColumn} from './Column.js';
import {TopRow, LeftRow} from './Row.js';
import HeaderCell from './HeaderCell.js';
import CrossCellVariableBundle from './CrossCellVariableBundle.js';
import Cell from './Cell.js';
import ConditionCell from './ConditionCell.js';
import {parameterLibraries, variableLibraries, constantLibraries, actionLibraries} from '../common/URule.js';
import {Remark} from '../../Remark.js';
import type BaseRowCol from './BaseRowCol';
import type BaseCell from './BaseCell';

export default class CrossTable {
    seq: number;
    rows: BaseRowCol[];
    columns: BaseRowCol[];
    cells: BaseCell[];
    rowColCellsMap: Map<string, BaseCell>;
    remark: Remark;
    propertyConfig: PropertyConfig;
    crossCellVariableBundle: CrossCellVariableBundle;
    table: HTMLTableElement;
    body?: HTMLTableSectionElement;
    headerCell!: HeaderCell;
    highlightDiv?: HTMLDivElement & { currentTD?: HTMLTableCellElement };

    /**
     * @param options
     * @param options.container - The container element to render into
     */
    constructor(options: { container: HTMLElement }) {
        this.seq = 0;
        this.rows = [];
        this.columns = [];
        this.cells = [];
        this.rowColCellsMap = new Map();

        // Remark section
        const remarkContainer = document.createElement('div');
        remarkContainer.style.cssText = 'margin-left: 5px';
        options.container.appendChild(remarkContainer);
        this.remark = new Remark(remarkContainer);

        // Property configuration
        const propertyContainer = document.createElement('div');
        propertyContainer.style.cssText = 'margin: 5px;';
        const propertySpan = document.createElement('span');
        options.container.appendChild(propertyContainer);
        propertyContainer.appendChild(propertySpan);
        this.propertyConfig = new PropertyConfig(propertySpan);

        // Cross-cell variable bundle (assignment target)
        this.crossCellVariableBundle = new CrossCellVariableBundle();
        options.container.appendChild(this.crossCellVariableBundle.container);
        this.crossCellVariableBundle.initData();

        // Main table element
        const table = document.createElement('table');
        table.className = 'table table-bordered';
        table.style.cssText = 'font-size: 12px;margin-left: 5px;margin-top:10px;width: inherit;';
        this.table = table;
        options.container.appendChild(table);
    }

    /**
     * Initialize the cross table from server data.
     *
     * @param data - The crosstab data from the server
     */
    init(data: any): void {
        this.crossCellVariableBundle.initData(data);
        data = data || {};
        this.remark.setData(data.remark);
        const tbody = document.createElement('tbody');
        this.body = tbody;
        this.table.appendChild(tbody);

        const rows = data.rows || [];
        const columns = data.columns || [];

        // First row is always the initial top row
        let firstRowData: any = {rowNumber: 1};
        if (rows.length > 0) {
            firstRowData = rows[0];
            rows.splice(0, 1);
        }

        // First column is always the initial left column
        let firstColData: any = {columnNumber: 1};
        if (columns.length > 0) {
            firstColData = columns[0];
            columns.splice(0, 1);
        }

        // Create the initial top row and left column
        const initialTopRow = this.addNewTopRow();
        initialTopRow.initData(firstRowData);

        const initialLeftCol = this.addNewLeftColumn();
        initialLeftCol.initData(firstColData);

        // Create the header cell at the intersection
        this.headerCell = new HeaderCell(initialTopRow, initialLeftCol);
        this.headerCell.initData(data.headerCell);

        // Ensure at least one remaining row and column
        if (rows.length === 0) rows.push({});
        if (columns.length === 0) columns.push({});

        // Add remaining columns
        for (const colData of columns) {
            let col: BaseRowCol;
            if (colData.type === 'top') {
                col = this.addNewTopColumn();
            } else {
                col = this.addNewLeftColumn();
            }
            col.initData(colData);
        }

        // Add remaining rows
        for (const rowData of rows) {
            let row: BaseRowCol;
            if (rowData.type === 'left') {
                row = this.addNewLeftRow();
            } else {
                row = this.addNewTopRow();
            }
            row.initData(rowData);
        }

        // Populate cells from the cellsMap
        const cellsMap = data.cellsMap;
        for (let ri = 0; ri < this.rows.length; ri++) {
            const row = this.rows[ri];
            for (let ci = 0; ci < this.columns.length; ci++) {
                const col = this.columns[ci];
                const cellKey = (row as any).rowNumber + ',' + (col as any).columnNumber;
                const cellData = cellsMap.get(cellKey);

                if (cellData) {
                    let cell: BaseCell | null = null;
                    if (cellData.type === 'condition') {
                        cell = new ConditionCell(row, col);
                        if ((row as any).istop) {
                            (cell as ConditionCell).initTopMenu();
                            cell.td.classList.add('top-condition-cell');
                            if ((row as any).bundleData) {
                                (cell as ConditionCell).setBundleData((row as any).bundleData);
                            }
                        } else {
                            (cell as ConditionCell).initLeftMenu();
                            cell.td.classList.add('left-condition-cell');
                        }
                    } else {
                        cell = new Cell(row, col);
                    }
                    (cell as any).initData(cellData);
                    this.cells.push(cell);
                    this.addCell(row, col, cell);
                    if (cell instanceof Cell) {
                        cell.attachColumnStyle();
                    }
                }
            }
        }
    }

    /**
     * Add a new top (structural) column.
     * @param parentCell - Parent cell for hierarchical columns
     * @returns The created TopColumn
     */
    addNewTopColumn(parentCell?: any): TopColumn {
        return new TopColumn(this, parentCell);
    }

    /**
     * Add a new left (value) row.
     * @param parentCell - Parent cell for hierarchical rows
     * @returns The created LeftRow
     */
    addNewLeftRow(parentCell?: any): LeftRow {
        return new LeftRow(this, parentCell);
    }

    /**
     * Add a new top (condition) row.
     * @param parentCell - Parent cell for hierarchical rows
     * @returns The created TopRow
     */
    addNewTopRow(parentCell?: any): TopRow {
        return new TopRow(this, parentCell);
    }

    /**
     * Add a new left (condition) column.
     * @param parentCell - Parent cell for hierarchical columns
     * @returns The created LeftColumn
     */
    addNewLeftColumn(parentCell?: any): LeftColumn {
        return new LeftColumn(this, parentCell);
    }

    /**
     * Get the header cell's rowspan.
     */
    getHeaderCellRowSpan(): number {
        return this.headerCell.getRowSpan();
    }

    /**
     * Get the header cell's colspan.
     */
    getHeaderCellColSpan(): number {
        return this.headerCell.getColSpan();
    }

    /**
     * Get a cell by its row and column.
     */
    getCell(row: BaseRowCol, col: BaseRowCol): BaseCell | undefined {
        return this.rowColCellsMap.get(row.id + ',' + col.id);
    }

    /**
     * Register a cell in the lookup map.
     */
    addCell(row: BaseRowCol, col: BaseRowCol, cell: BaseCell): void {
        this.rowColCellsMap.set(row.id + ',' + col.id, cell);
    }

    /**
     * Remove a cell from the lookup map.
     */
    removeCell(row: BaseRowCol, col: BaseRowCol): void {
        this.rowColCellsMap.delete(row.id + ',' + col.id);
    }

    /**
     * Adjust the header cell's colspan.
     * @param increment - True to increment, false to decrement
     */
    adjustHeaderCellColSpan(increment: boolean): void {
        if (this.headerCell) {
            this.headerCell.adjustColSpan(increment);
        }
    }

    /**
     * Adjust the header cell's rowspan.
     * @param increment - True to increment, false to decrement
     */
    adjustHeaderCellRowSpan(increment: boolean): void {
        if (this.headerCell) {
            this.headerCell.adjustRowSpan(increment);
        }
    }

    /**
     * Get or create the highlight div used to indicate the active cell.
     * @returns The highlight div element
     */
    getHighlightDiv(): HTMLDivElement & { currentTD?: HTMLTableCellElement } {
        if (this.highlightDiv) {
            const currentTD = this.highlightDiv.currentTD;
            if (currentTD) {
                currentTD.removeEventListener('DOMSubtreeModified', (currentTD as any)._domModifiedHandler);
            }
        } else {
            const div = document.createElement('div') as HTMLDivElement & { currentTD?: HTMLTableCellElement };
            div.style.cssText = 'border:2px solid rgb(82, 146, 247);position:absolute;left: 0;top: 0;';
            this.highlightDiv = div;
        }
        return this.highlightDiv;
    }

    /**
     * Generate the next sequence number for row/column IDs.
     */
    nextSeq(): number {
        return this.seq++;
    }

    /**
     * Serialize the entire crosstab to XML.
     * @returns XML string, or void on error (shows alert)
     */
    toXml(): string | void {
        try {
            let xml = '<?xml version="1.0" encoding="UTF-8"?>';
            xml += '<crosstab ' + this.propertyConfig.toXml() + ' ' + this.crossCellVariableBundle.toXml() + '>';
            xml += this.remark.toXml();
            xml += this.headerCell.toXml();

            // Import libraries
            parameterLibraries.forEach(function (path: string) {
                xml += '<import-parameter-library path="' + path + '"/>';
            });
            variableLibraries.forEach(function (path: string) {
                xml += '<import-variable-library path="' + path + '"/>';
            });
            constantLibraries.forEach(function (path: string) {
                xml += '<import-constant-library path="' + path + '"/>';
            });
            actionLibraries.forEach(function (path: string) {
                xml += '<import-action-library path="' + path + '"/>';
            });

            // Rows
            for (const row of this.rows) {
                xml += (row as any).toXml();
            }

            // Columns
            for (const col of this.columns) {
                xml += (col as any).toXml();
            }

            // Cells
            for (const cell of this.cells) {
                xml += (cell as any).toXml();
            }

            xml += '</crosstab>';
            return xml;
        } catch (error) {
            window.bootbox.alert(error as string);
        }
    }
}
