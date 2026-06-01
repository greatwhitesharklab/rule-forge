import Row from './Row';
import CustomCell from './CustomCell';

export default class ConditionRow extends Row {
    constructor(table: import('./ScoreCardTable').default, attributeRow: import('./AttributeRow').default, rowData?: any) {
        super(table);
        this.attributeRow = attributeRow;
        this.rowData = rowData;
        this.rowType = "condition";
        this.tr = document.createElement('tr');
        this.tr.style.cssText = 'min-height: 25px';
        this.tr.appendChild(this.newConditionCell());
        this.tr.appendChild(this.newScoreCell());
        this.initCustomCells();
    }

    addCustomCol(customCol: import('./CustomCol').default): void {
        const cell = new CustomCell(this, customCol);
        customCol.customCells.push(cell);
        this.tr!.appendChild(cell.td);
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
    }

    remove(): void {
        this.attributeRow!.removeConditionRow(this);
    }

    toXml(): string {
        let xml = "<condition-row row-number=\"" + this.getRowNumber() + "\">";
        xml += "</condition-row>";
        return xml;
    }
}
