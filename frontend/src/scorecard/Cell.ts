export default class Cell {
    row: import('./Row').default;
    col: import('./Col').default;
    td: HTMLTableCellElement;
    type: string;
    variableName?: string;
    variableLabel?: string;
    categoryName?: string;
    datatype?: string;
    weight?: string;
    cellCondition?: { toXml(): string; initData(joint: any): void; renderTo(container: HTMLElement): void; getDisplayContainer(): HTMLElement; };
    inputType?: { toXml(): string; setValueType(valueType: string, value: any): void; getContainer(): HTMLElement };

    constructor(row: import('./Row').default, col: import('./Col').default, cellData?: any) {
        row.cells.push(this);
        this.row = row;
        this.col = col;
        this.init(cellData);
    }

    init(cellData?: any): void {
        const td = document.createElement('td');
        td.style.cssText = 'border:1px solid #607D8B';
        this.td = td;
        this.initCell(cellData);
    }

    initCell(_cellData?: any): void {
        // overridden in subclasses
    }

    toXml(): string {
        let xml = "<card-cell type=\"" + this.type + "\" row=\"" + this.row.getRowNumber() + "\" col=\"" + this.col.getColNumber() + "\"";
        if (this.type === 'attribute') {
            if (!this.variableName) {
                throw "请先选择属性";
            }
            // 获取 category 名称，支持字符串和对象格式
            let categoryName = this.getCategoryName ? this.getCategoryName() : this.categoryName;
            // 如果 getCategoryName() 返回 null，则使用 this.categoryName 作为回退
            if (!categoryName && this.categoryName) {
                categoryName = this.categoryName;
            }
            if (!categoryName) {
                throw "请先选择分类";
            }

            // 验证 datatype
            const datatype = this.datatype || "String";

            if (this.row.scoreCardTable.weightSupport) {
                if (!this.weight) {
                    throw "请先定义[" + this.variableLabel + "]属性的权重值";
                } else {
                    xml += " weight=\"" + this.weight + "\"";
                }
            }
            xml += " var=\"" + this.variableName + "\"";
            xml += " var-label=\"" + (this.variableLabel || this.variableName) + "\"";
            xml += " datatype=\"" + datatype + "\"";
            xml += " category=\"" + categoryName + "\">";
        } else if (this.type === 'condition') {
            xml += ">";
            if (!this.cellCondition) {
                throw "请配置好条件.";
            }
            xml += this.cellCondition.toXml();
        } else if (this.type === 'score') {
            if (!this.inputType) {
                throw "请配置好分值";
            }
            const contentXml = this.inputType.toXml();
            if (contentXml === '') {
                throw "请配置好分值";
            }
            xml += ">";
            xml += contentXml;
        } else if (this.type === 'custom') {
            if (!this.inputType) {
                throw "请配置好自定义值";
            }
            const contentXml = this.inputType.toXml();
            if (contentXml === '') {
                throw "请配置好自定义值";
            }
            xml += ">";
            xml += contentXml;
        }
        xml += "</card-cell>";
        return xml;
    }

    getCategoryName(): string | null {
        return null;
    }
}
