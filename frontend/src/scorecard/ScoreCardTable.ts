import AttributeRow from './AttributeRow';
import CustomCol from './CustomCol';
import PropertyConfig from './PropertyConfig';
import TableAction from './TableAction';
import AttributeCol from './AttributeCol';
import ConditionCol from './ConditionCol';
import ScoreCol from './ScoreCol';
import { Remark } from '../Remark.js';

export default class ScoreCardTable {
    remark: any;
    weightSupport = false;
    weightSupportContainer!: HTMLSpanElement;
    weightSupportOption!: HTMLInputElement;
    weightNonsupportOption!: HTMLInputElement;
    propertyConfig!: PropertyConfig;
    attributeRows: AttributeRow[] = [];
    customCols: CustomCol[] = [];
    config: { container: HTMLElement; headers: any[] };
    table!: HTMLTableElement;
    tableAction!: TableAction;
    data: any;
    headerRow!: HTMLTableRowElement;
    body!: HTMLTableSectionElement;
    attributeCol!: AttributeCol;
    conditionCol!: ConditionCol;
    scoreCol!: ScoreCol;

    constructor(config: { container: HTMLElement; headers: any[] }) {
        const remarkContainer = document.createElement('div');
        config.container.appendChild(remarkContainer);
        this.remark = new (Remark as any)(remarkContainer);
        this.weightSupport = false;
        const configContainer = document.createElement('div');
        configContainer.style.cssText = 'margin: 5px;';
        this.weightSupportContainer = document.createElement('span');
        this.weightSupportContainer.style.cssText = 'padding: 8px;margin-right:5px;border:solid 1px #9E9E9E';
        this.weightSupportContainer.textContent = '权重:';
        this.initWeightSupportOptions();
        configContainer.appendChild(this.weightSupportContainer);
        const propertyContainer = document.createElement('span');
        config.container.appendChild(configContainer);
        configContainer.appendChild(propertyContainer);
        this.propertyConfig = new PropertyConfig(propertyContainer);
        this.attributeRows = [];
        this.customCols = [];
        this.config = config;
        const table = document.createElement('table');
        table.className = 'table table-bordered';
        table.style.cssText = 'width: auto;max-width: none;font-size: 12px';
        this.table = table;
        config.container.appendChild(table);
        const actionContainer = document.createElement('div');
        actionContainer.style.cssText = 'border: solid 1px #ddd;border-radius:5px;padding:10px';
        config.container.appendChild(actionContainer);
        this.tableAction = new TableAction(actionContainer);
    }

    initWeightSupportOptions(): void {
        const container = document.createElement('span');
        this.weightSupportContainer.appendChild(container);
        const supportLabel = document.createElement('label');
        supportLabel.className = 'checkbox-inline';
        supportLabel.style.cssText = 'padding-left: 8px;';
        container.appendChild(supportLabel);
        const supportRadio = document.createElement('input');
        supportRadio.type = 'radio';
        supportRadio.name = 'weightSupport';
        supportRadio.value = '支持';
        this.weightSupportOption = supportRadio;
        supportLabel.appendChild(supportRadio);
        supportLabel.appendChild(document.createTextNode('支持'));
        const nonsupportLabel = document.createElement('label');
        nonsupportLabel.className = 'checkbox-inline';
        nonsupportLabel.style.cssText = 'padding-left: 8px;';
        container.appendChild(nonsupportLabel);
        const nonsupportRadio = document.createElement('input');
        nonsupportRadio.type = 'radio';
        nonsupportRadio.name = 'weightSupport';
        nonsupportRadio.value = '不支持';
        this.weightNonsupportOption = nonsupportRadio;
        nonsupportLabel.appendChild(nonsupportRadio);
        nonsupportLabel.appendChild(document.createTextNode('不支持'));
        const _this = this;
        this.weightSupportOption.addEventListener('change', function () {
            if ((this as HTMLInputElement).value === '支持') {
                for (const row of _this.attributeRows) {
                    row.attributeCell.showWeight();
                }
                _this.weightSupport = true;
            }
        });
        this.weightNonsupportOption.addEventListener('change', function () {
            if ((this as HTMLInputElement).value === '不支持') {
                for (const row of _this.attributeRows) {
                    row.attributeCell.hideWeight();
                }
                _this.weightSupport = false;
            }
        });
    }

    init(data: any): void {
        this.data = data || {};
        if (this.data.weightSupport) {
            this.weightSupport = true;
            this.weightSupportOption.checked = true;
        } else {
            this.weightSupport = false;
            this.weightNonsupportOption.checked = true;
        }
        this.remark.setData(data["remark"]);
        this.propertyConfig.initData(data);
        this.tableAction.initData(data);
        const header = document.createElement('thead');
        this.table.appendChild(header);
        this.headerRow = document.createElement('tr');
        header.appendChild(this.headerRow);
        this.body = document.createElement('tbody');
        this.table.appendChild(this.body);
        this.initAttributeCol(data);
        this.initConditionCol(data);
        this.initScoreCol(data);
        const customCols = data.customCols || [];
        for (const colData of customCols) {
            this.addCustomCol(colData);
        }
        const rows = data.rows || [];
        for (const rowData of rows) {
            this.addAttributeRow(rowData);
        }
    }

    getCell(row: number, col: number): any {
        if (!this.data) {
            return null;
        }
        const cells = this.data.cells;
        for (const cell of cells) {
            if (cell.row === row && cell.col === col) {
                // 如果是attribute类型的cell且有category信息，尝试找到对应的category对象
                if (cell.type === 'attribute') {
                    // 处理category字段，可能是category或variableCategory
                    const categoryValue = cell.category || cell.variableCategory;
                    if (categoryValue && typeof categoryValue === 'string') {
                        const categoryName = categoryValue;
                        const allCategories = this.attributeCol.getAllCategories();
                        if (allCategories && allCategories.length > 0) {
                            const categoryObj = allCategories.find(cat => cat.name === categoryName);
                            if (categoryObj) {
                                cell.category = categoryObj;
                            }
                        }
                        // 如果category数据还未加载，保留原始字符串，稍后处理
                    }
                }
                return cell;
            }
        }
        throw "Cell [" + row + "," + col + "] not exist.";
    }

    initAttributeCol(data: any): void {
        const width = data.attributeColWidth || '200';
        const name = data.attributeColName || '属性';
        this.attributeCol = new AttributeCol(this, name, width);
    }

    initConditionCol(data: any): void {
        const width = data.conditionColWidth || '220';
        const name = data.conditionColName || '条件';
        this.conditionCol = new ConditionCol(this, name, width);
    }

    initScoreCol(data: any): void {
        const width = data.scoreColWidth || '180';
        const name = data.scoreColName || '分值';
        this.scoreCol = new ScoreCol(this, name, width);
    }

    addAttributeRow(rowData?: any): void {
        const attributeRow = new AttributeRow(this, rowData);
        this.attributeRows.push(attributeRow);
        this.body.appendChild(attributeRow.tr);
        if (rowData) {
            attributeRow.initConditionRows(rowData);
        }
        window._setDirty?.();
    }

    addCustomCol(colData?: any): void {
        if (colData) {
            const col = new CustomCol(this, colData.name, colData.width);
            this.customCols.push(col);
            window._setDirty?.();
        } else {
            const _this = this;
            window.bootbox.prompt("请输入列名", function (name) {
                if (!name || name.length < 1) {
                    return;
                }
                const col = new CustomCol(_this, name);
                _this.customCols.push(col);
                window._setDirty?.();
            });
        }
    }

    toXml(): string {
        if (this.attributeRows.length === 0) {
            throw "属性至少要有一行";
        }
        let xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        xml += "<scorecard weight-support=\"" + this.weightSupport + "\" " + this.propertyConfig.toXml() + this.attributeCol.toXml() + this.conditionCol.toXml() + this.scoreCol.toXml() + this.tableAction.toXml() + ">";
        xml += this.remark.toXml();
        (window as any).parameterLibraries.forEach(function (item: string) {
            xml += "<import-parameter-library path=\"" + item + "\"/>";
        });
        (window as any).variableLibraries.forEach(function (item: string) {
            xml += "<import-variable-library path=\"" + item + "\"/>";
        });
        (window as any).constantLibraries.forEach(function (item: string) {
            xml += "<import-constant-library path=\"" + item + "\"/>";
        });
        (window as any).actionLibraries.forEach(function (item: string) {
            xml += "<import-action-library path=\"" + item + "\"/>";
        });
        for (const row of this.attributeRows) {
            xml += row.cellsToXml();
        }
        for (const row of this.attributeRows) {
            xml += row.toXml();
        }
        for (const col of this.customCols) {
            xml += col.toXml();
        }
        xml += "</scorecard>";
        return xml;
    }
}
