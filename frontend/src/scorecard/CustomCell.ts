import Cell from './Cell';

export default class CustomCell extends Cell {
    constructor(row: import('./Row').default, col: import('./Col').default, cellData?: any) {
        super(row, col, cellData);
        this.type = "custom";
    }

    initCell(cellData?: any): void {
        const container = document.createElement('div');
        this.inputType = new (ruleforge as any).InputType(null, "无");
        container.appendChild((this.inputType as any).getContainer());
        if (cellData && cellData.value) {
            const value = cellData.value;
            (this.inputType as any).setValueType(value.valueType, value);
        }
        this.td.appendChild(container);
    }
}
