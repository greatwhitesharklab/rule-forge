import AttributeCell from './AttributeCell';
import Row from './Row';
import ConditionRow from './ConditionRow';
import CustomCell from './CustomCell';

export default class AttributeRow extends Row {
    conditionRows: ConditionRow[] = [];
    attributeCell!: AttributeCell;
    declare tr: HTMLTableRowElement;

    constructor(table: import('./ScoreCardTable').default, rowData?: any) {
        super(table);
        this.rowData = rowData;
        this.conditionRows = [];
        this.tr = document.createElement('tr');
        this.tr.style.cssText = 'min-height: 25px';
        this.tr.appendChild(this.newAttributeCell());
        this.tr.appendChild(this.newConditionCell());
        this.tr.appendChild(this.newScoreCell());
        this.initCustomCells();
    }

    addCustomCol(customCol: import('./CustomCol').default): void {
        const cell = new CustomCell(this, customCol);
        this.tr.appendChild(cell.td);
        customCol.customCells.push(cell);
        for (const row of this.conditionRows) {
            row.addCustomCol(customCol);
        }
    }

    removeCustomCol(customCol: import('./CustomCol').default): void {
        const posArray: number[] = [];
        for (let i = 0; i < this.cells.length; i++) {
            const cell = this.cells[i];
            if (cell.col === customCol) {
                posArray.push(i);
            }
        }
        for (const pos of posArray) {
            this.cells.splice(pos, 1);
        }
        for (const row of this.conditionRows) {
            row.removeCustomCol(customCol);
        }
    }

    initConditionRows(rowData?: any): void {
        if (!rowData) return;
        const conditionRows = rowData.conditionRows || [];
        for (const conditionRowData of conditionRows) {
            this.addConditionRow(conditionRowData);
        }
    }

    newAttributeCell(): HTMLTableCellElement {
        let cellData: any = null;
        if (this.rowData) {
            cellData = this.scoreCardTable.getCell(this.rowData.rowNumber, 1);
        }
        this.attributeCell = new AttributeCell(this, this.scoreCardTable.attributeCol, cellData);
        return this.attributeCell.td;
    }

    remove(): void {
        const pos = this.scoreCardTable.attributeRows.indexOf(this);
        this.scoreCardTable.attributeRows.splice(pos, 1);
        for (const row of this.conditionRows) {
            row.tr.remove();
        }
        this.tr.remove();
    }

    removeConditionRow(conditionRow: ConditionRow): void {
        const pos = this.conditionRows.indexOf(conditionRow);
        this.conditionRows.splice(pos, 1);
        let rowSpan: number = this.attributeCell.td.rowSpan;
        if (!rowSpan) {
            rowSpan = 0;
        } else {
            rowSpan = rowSpan - 1;
        }
        this.attributeCell.td.rowSpan = rowSpan;
        conditionRow.tr.remove();
    }

    addConditionRow(conditionRowData?: any): void {
        const newConditionRow = new ConditionRow(this.scoreCardTable, this, conditionRowData);
        let rowSpan: number = this.attributeCell.td.rowSpan;
        if (!rowSpan) {
            rowSpan = 2;
        } else {
            rowSpan = rowSpan + 1;
        }
        this.attributeCell.td.rowSpan = rowSpan;
        if (this.conditionRows.length > 0) {
            this.conditionRows[this.conditionRows.length - 1].tr.after(newConditionRow.tr);
        } else {
            this.tr.after(newConditionRow.tr);
        }
        this.conditionRows.push(newConditionRow);
    }

    toXml(): string {
        let xml = "<attribute-row row-number=\"" + this.getRowNumber() + "\">";
        for (const row of this.conditionRows) {
            xml += row.toXml();
        }
        xml += "</attribute-row>";
        return xml;
    }
}
