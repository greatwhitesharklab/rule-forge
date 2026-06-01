import CustomCell from './CustomCell';
import ConditionCell from './ConditionCell';
import ScoreCell from './ScoreCell';
import type ScoreCardTable from './ScoreCardTable';

export default class Row {
    scoreCardTable: ScoreCardTable;
    cells: import('./Cell').default[] = [];
    rowData?: any;
    conditionCell?: ConditionCell;
    scoreCell?: ScoreCell;
    tr?: HTMLTableRowElement;
    attributeRow?: import('./AttributeRow').default;
    conditionRows?: import('./ConditionRow').default[];
    rowType?: string;

    constructor(table: ScoreCardTable) {
        this.scoreCardTable = table;
        this.cells = [];
    }

    newConditionCell(): HTMLTableCellElement {
        let cellData: any = null;
        if (this.rowData) {
            cellData = this.scoreCardTable.getCell(this.rowData.rowNumber, 2);
        }
        this.conditionCell = new ConditionCell(this, this.scoreCardTable.conditionCol, cellData);
        return this.conditionCell.td;
    }

    newScoreCell(): HTMLTableCellElement {
        let cellData: any = null;
        if (this.rowData) {
            cellData = this.scoreCardTable.getCell(this.rowData.rowNumber, 3);
        }
        this.scoreCell = new ScoreCell(this, this.scoreCardTable.scoreCol, cellData);
        return this.scoreCell.td;
    }

    initCustomCells(): void {
        for (const col of this.scoreCardTable.customCols) {
            let cellData: any = null;
            if (this.rowData) {
                cellData = this.scoreCardTable.getCell(this.rowData.rowNumber, col.getColNumber());
            }
            const cell = new CustomCell(this, col, cellData);
            col.customCells.push(cell);
            this.tr!.append(cell.td);
        }
    }

    getRowNumber(): number {
        if (this.attributeRow) {
            //condition row
            const attributeRowNumber = this.attributeRow.getRowNumber();
            const pos = this.attributeRow.conditionRows!.indexOf(this as any) + 1;
            return attributeRowNumber + pos;
        } else {
            //attribute row
            const attributeRows = this.scoreCardTable.attributeRows;
            const pos = attributeRows.indexOf(this as any);
            let rowNum = 0;
            for (let i = 0; i < pos; i++) {
                const row = attributeRows[i];
                rowNum += 1 + row.conditionRows!.length;
            }
            return rowNum + 2;
        }
    }

    cellsToXml(): string {
        let xml = "";
        for (const cell of this.cells) {
            xml += cell.toXml();
        }
        if (this.conditionRows) {
            for (const row of this.conditionRows) {
                xml += row.cellsToXml();
            }
        }
        return xml;
    }
}
