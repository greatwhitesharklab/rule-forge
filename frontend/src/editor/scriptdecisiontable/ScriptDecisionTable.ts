/**
 * ScriptDecisionTable — the main editor class for the script-decision-table rule type.
 *
 * Previously `RuleForge.DecisionTable` (IIFE in scriptdecisiontable folder).
 */

import HandsontableModule from 'handsontable';
const Handsontable = (HandsontableModule as any).default || HandsontableModule;

import { getParameter } from '../../Utils.js';
import { save, saveNewVersion, formPost } from '../../api/client.js';
import '../../../node_modules/codemirror/addon/hint/show-hint.js';
import '../../../node_modules/codemirror/addon/mode/simple.js';
import './then_mode.js';
import './table_if_mode.js';
import './table_print_mode.js';
import './table-if-hint.js';
import './ScriptRenderers.js';

import {
    constantLibraries,
    actionLibraries,
    variableLibraries,
    parameterLibraries,
    variableValueArray,
    parameterValueArray,
    refreshActionLibraries,
    refreshConstantLibraries,
    refreshVariableLibraries,
    refreshParameterLibraries
} from '../common/URule.js';

interface CellData {
    row: number;
    col: number;
    rowspan: number;
    container?: HTMLElement;
    script?: string;
    codeMirror?: any;
}

interface RowData {
    num: number;
    height: number;
}

interface ColData {
    num: number;
    type?: string;
    width?: number;
    variableCategory?: string;
    variableLabel?: string;
    variableName?: string;
    datatype?: string;
}

interface DecisionTableData {
    cells?: CellData[];
    cellMap?: Record<string, CellData>;
    rows?: RowData[];
    columns?: ColData[];
    libraries?: Array<{ type: string; path: string }>;
}

interface RuleforgeNamespace {
    ConfigVariableDialog: new (table: ScriptDecisionTable) => { open: () => void };
    ConfigConstantDialog: new (table: ScriptDecisionTable) => { open: () => void };
    ConfigActionDialog: new (table: ScriptDecisionTable) => { open: () => void };
    ConfigParameterDialog: new (table: ScriptDecisionTable) => { open: () => void };
}

export class ScriptDecisionTable {
    hasMod: boolean = true;
    container: HTMLElement;
    decisionTable!: DecisionTableData;
    configVarDialog: any;
    configConstantDialog: any;
    configActionDialog: any;
    configParameterDialog: any;
    criteriaMenu: any;
    assignmentMenu: any;
    actionMenu: any;
    criteriaCellMenu: any;
    actionCellMenu: any;
    _handsontable: any;
    _dom!: HTMLElement;
    dialogCondition: any;

    constructor(id: string) {
        let table: HTMLElement;
        variableValueArray.push(this as any);
        parameterValueArray.push(this as any);
        const container = document.getElementById(id)!;
        this.container = container;
        const self = this;

        // Disable arrow key navigation for Handsontable
        const keyCodes = (Handsontable.helper as any).KEY_CODES || (Handsontable.helper as any).keyCode;
        if (keyCodes) {
            keyCodes.ARROW_UP = 'none';
            keyCodes.ARROW_DOWN = 'none';
            keyCodes.ARROW_LEFT = 'none';
            keyCodes.ARROW_RIGHT = 'none';
        }

        const saveButton = `<div class="btn-group btn-group-sm navbar-btn" style="margin-top:0px;margin-bottom: 0px" role="group" aria-label="...">
            <button id="saveButton" type="button" class="btn btn-default navbar-btn" ><i class="rf rf-save"></i> 保存</button>
            <button id="saveButtonNewVersion" type="button" class="btn btn-default navbar-btn" style="display: none;"><i class="rf rf-savenewversion"></i> 生成版本</button>
        </div>`;
        const addCriteriaButton = `<button id="addCriteriaButton" type="button" class="btn btn-default btn-sm"><i class="glyphicon glyphicon-plus"></i> 添加条件行</button>`;
        const deleteCriteriaButton = `<button id="deleteCriteriaButton" type="button" class="btn btn-default btn-sm"><i class="glyphicon glyphicon-minus"></i> 删除条件行</button>`;
        const buttons = `<nav class="navbar navbar-default" style="margin: 5px">
            <div>
                <div>
                    <div class="btn-group btn-group-sm navbar-btn" style="margin-top:0px;margin-bottom: 0px;margin-left: 5px" role="group" aria-label="...">
                     ${addCriteriaButton}
                     ${deleteCriteriaButton}
                    </div>
                    <div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0px;margin-bottom: 0px" role="group" aria-label="...">
                        <button id="configVarButton" type="button" class="btn btn-default"><i class="rf rf-variable"></i> 变量库</button>
                        <button id="configConstantsButton" type="button" class="btn btn-default"><i class="rf rf-constant"></i> 常量库</button>
                        <button id="configActionButton" type="button" class="btn btn-default"><i class="rf rf-action"></i> 动作库</button>
                        <button id="configParameterButton" type="button" class="btn btn-default"><i class="rf rf-parameter"></i> 参数库</button>
                    </div>
                    ${saveButton}
                </div>
            </div>
        </nav>`;

        container.insertAdjacentHTML('beforeend', buttons);

        document.getElementById('addCriteriaButton')!.addEventListener('click', function (): void {
            const cellData = self.getCurrentCellData(),
                row = cellData.row + cellData.rowspan,
                col = cellData.col;
            self.mergeRange(col - 1);
            self.translateRow(row);
            self.createRowData(row);
            self.createCellDataRange(row, row, col, self.getLastColIndex());
            self.insertRow(row - 1);
            self.renderCells();
            self.setDirty();
            document.getElementById('deleteCriteriaButton')!.classList.remove('disabled');
            self.invoke('render');
        });

        document.getElementById('deleteCriteriaButton')!.addEventListener('click', function (): void {
            const highlight = self.getHighlight(),
                row = highlight.row,
                col = highlight.col,
                cell = self.getCellData(row, col),
                rowspan = cell.rowspan;
            self.mergeRange(col - 1, -rowspan);
            self.removeRowDataRange(row, rowspan);
            self.removeCellDataRange(row, row + rowspan - 1, col, self.getLastColIndex());
            self.translateRow(row + rowspan, -rowspan);
            self.removeRow(row, rowspan);
            self.renderCells();
            self.setDirty();
            self.invoke('render');
            if (self.countRows() === 1) {
                this.classList.add('disabled');
            }
        });

        const ruleforge = (window as unknown as { ruleforge: RuleforgeNamespace }).ruleforge;

        document.getElementById('configVarButton')!.addEventListener('click', function (): void {
            if (!self.configVarDialog) {
                self.configVarDialog = new ruleforge.ConfigVariableDialog(self);
            }
            self.configVarDialog.open();
        });

        document.getElementById('configConstantsButton')!.addEventListener('click', function (): void {
            if (!self.configConstantDialog) {
                self.configConstantDialog = new ruleforge.ConfigConstantDialog(self);
            }
            self.configConstantDialog.open();
        });

        document.getElementById('configActionButton')!.addEventListener('click', function (): void {
            if (!self.configActionDialog) {
                self.configActionDialog = new ruleforge.ConfigActionDialog(self);
            }
            self.configActionDialog.open();
        });

        document.getElementById('configParameterButton')!.addEventListener('click', function (): void {
            if (!self.configParameterDialog) {
                self.configParameterDialog = new ruleforge.ConfigParameterDialog(self);
            }
            self.configParameterDialog.open();
        });

        document.getElementById('saveButton')!.addEventListener('click', function (): void {
            doSave(false);
        });

        document.getElementById('saveButtonNewVersion')!.addEventListener('click', function (): void {
            doSave(true);
        });

        function doSave(newVersion: boolean): void {
            if (document.getElementById('saveButton')!.classList.contains('disabled')) {
                return;
            }
            let file = getParameter('file'), xml = self.toXml();
            xml = encodeURI(xml);
            const postData: Record<string, string> = { content: xml, file, newVersion: String(newVersion) };
            const url = '/common/saveFile';
            if (newVersion) {
                saveNewVersion(url, { file, content: xml }).then(function (): void {
                    self.resetState();
                }).catch(function () {});
            } else {
                save(url, postData).then(function (): void {
                    self.resetState();
                });
            }
        }

        self.load().catch(function () {});
        (window as any).ht = self;
        const config: Record<string, any> = {
            'licenseKey': 'non-commercial-and-evaluation',
            'type': 'ruleforge',
            'manualRowResize': true,
            'manualColumnResize': true,
            'autoWrapCol': true,
            'startCols': self.getColDatas().length,
            'maxRows': 2147483647,
            'startRows': self.getRowDatas().length,
            'fillHandle': false,
            'multiSelect': false,
            'className': 'htMiddle',
            'rowHeaders': true,
            'maxCols': 2147483647,
            'mergeCells': true,
            'autoWrapRow': true,
            'outsideClickDeselects': false,
            'colWidths': 120
        };
        table = document.createElement('div');
        table.style.marginLeft = '15px';
        container.appendChild(table);

        self._handsontable = new Handsontable(table, config);
        self._dom = table;
        self._handsontable.ht = self;
        config.colHeaders = function (col: number): string {
            const column = self.getColData(col);
            if (!column) return '';
            const type = column.type,
                category = column.variableCategory === 'parameter' ? '参数' : column.variableCategory,
                variable = column.variableLabel,
                width = column.width;
            let title: string = (category || '') + '.' + (variable || '');
            let iconClass = '';
            self.setColWidth(col, width);
            if (!category || !variable) {
                title = '';
            }
            if (type === 'Criteria') {
                iconClass = 'glyphicon glyphicon-filter';
            } else if (type === 'ExecuteMethod') {
                title = '执行方法';
                iconClass = 'glyphicon glyphicon-flash';
            } else if (type === 'Assignment') {
                iconClass = 'glyphicon glyphicon-tasks';
            } else if (type === 'ConsolePrint') {
                title = '控制台输出';
                iconClass = 'glyphicon glyphicon-print';
            }
            return "<i class='" + iconClass + "' style='line-height:21px;'></i> " + title;
        };
        config.rowHeaders = function (row: number): number {
            const rowData = self.getRowData(row);
            if (rowData && rowData.height) {
                self.setRowHeight(row, rowData.height);
            }
            return row + 1;
        };
        config.cells = function (): { readOnly: boolean } {
            return {
                readOnly: true
            };
        };
        self.updateSettings(config);
        self.renderCells();

        self.addHook('afterSelectionEnd', function (): void {
            const colData = self.getCurrentColData();

            if (colData.type === 'Criteria') {
                document.getElementById('addCriteriaButton')!.classList.remove('disabled');
                if (self.dialogCondition && self.dialogCondition.isShow) {
                    const project = self.getRequestParameter('project');
                    if (colData.variableCategory) {
                        self.dialogCondition.setOption({ title: '常用条件列表【' + colData.variableCategory + '.' + colData.variableLabel + '】' });
                    } else {
                        self.dialogCondition.setOption({ title: '常用条件列表' });
                    }
                    self.dialogCondition.refresh(project, 'scriptdecisiontable', colData.variableName);
                }
            } else {
                document.getElementById('addCriteriaButton')!.classList.add('disabled');
                if (self.dialogCondition && self.dialogCondition.isShow) {
                    const project = self.getRequestParameter('project');
                    self.dialogCondition.setOption({ title: '动作列不支持插入条件！' });
                    self.dialogCondition.refresh(project, 'scriptdecisiontable', '');
                }
            }
        });

        self.addHook('beforeColumnResize', function (col: number, size: number): void {
            const colData = self.getColData(col);
            colData.width = size;
            self.setDirty();
            self.invoke('render');
        });

        self.addHook('beforeRowResize', function (row: number, size: number): void {
            const rowData = self.getRowData(row);
            rowData.height = size;
            self.setDirty();
        });

        self.addHook('afterRender', function (): void {
            self._dom.querySelectorAll('.htCore tr').forEach(function (tr: Element): void {
                const children = (tr as HTMLElement).children;
                for (let i = 0; i < children.length; i++) {
                    (children[i] as HTMLElement).style.borderRightWidth = '';
                }
                const criteriaCol = Array.from(children)[self.countCriteriaCols()];
                if (criteriaCol) {
                    (criteriaCol as HTMLElement).style.borderRightWidth = '3px';
                }
            });
        });
        self.initMenu();
        self.resetState();
        table.querySelector('.handsontable')!.remove();
        self.invoke('render');
    }

    // ---- Handsontable wrappers ----

    updateSettings(options: Record<string, any>): void {
        this._handsontable.updateSettings(options);
    }

    getCellRenderer(cellProperties: any): any {
        return this._handsontable.getCellRenderer(cellProperties);
    }

    getValue(): any {
        return this._handsontable.getValue();
    }

    alter(operate: string, index?: number, amount?: number, source?: any): void {
        this._handsontable.alter(operate, index, amount, source);
    }

    getCell(row: number, col: number): HTMLElement {
        return this._handsontable.getCell(row, col);
    }

    getCellMeta(row: number, col: number): any {
        return this._handsontable.getCellMeta(row, col);
    }

    selectCell(row: number, col: number, row2?: number, col2?: number, scrollToSelection?: boolean): void {
        this._handsontable.selectCell(row, col, row2, col2, scrollToSelection);
    }

    deselectCell(): void {
        this._handsontable.deselectCell();
    }

    getSelected(): any {
        return this._handsontable.getSelected();
    }

    getSelectedRange(): any {
        return this._handsontable.getSelectedRange();
    }

    getMergeInfo(row: number, col: number): any {
        return this._handsontable.mergeCells.mergedCellInfoCollection.getInfo(row, col);
    }

    setMergeInfo(info: any): void {
        this._handsontable.mergeCells.mergedCellInfoCollection.setInfo(info);
    }

    removeMergeInfo(row: number, col: number): any {
        return this._handsontable.mergeCells.mergedCellInfoCollection.removeInfo(row, col);
    }

    clear(): void {
        this._handsontable.clear();
    }

    countRows(): number {
        return this._handsontable.countRows();
    }

    countCols(): number {
        return this._handsontable.countCols();
    }

    colToProp(_column?: number): any {
        return this._handsontable.colToProp();
    }

    getRowHeader(row: number): any {
        return this._handsontable.getRowHeader(row);
    }

    getColHeader(col: number): any {
        return this._handsontable.getColHeader(col);
    }

    getColWidth(_col?: number): any {
        return this._handsontable.getColWidth();
    }

    getRowHeight(_row?: number): any {
        return this._handsontable.getRowHeight();
    }

    propToCol(property: any): any {
        return this._handsontable.propToCol(property);
    }

    addHook(name: string, func: (...args: any[]) => void): void {
        this._handsontable.addHook(name, func);
    }

    invoke(methodName: string, args?: any): void {
        if (methodName === 'render') {
            if (args === true) {
                this._handsontable.forceFullRender = false;
                this._handsontable.view.render();
            } else {
                this._handsontable.render();
            }
        } else {
            this._handsontable[methodName](args);
        }
    }

    getInstance(): any {
        return this._handsontable;
    }

    setDirty(): void {
        window._setDirty?.();
    }

    resetState(): void {
        window._dirty = false;
        document.getElementById('saveButton')!.innerHTML = "<i class='rf rf-save'></i> 保存";
        document.getElementById('saveButton')!.classList.add('disabled');
    }

    // ---- Row/Col DOM manipulation ----

    insertRow(index: number): void {
        this.alter('insert_row');
        const tbody = this._dom.querySelector('.htCore > tbody');
        if (tbody) {
            const lastRow = tbody.children[tbody.children.length - 1];
            const targetRow = tbody.children[index];
            if (targetRow && lastRow) {
                targetRow.after(lastRow);
            }
        }
    }

    removeRow(index: number, count: number): void {
        const tbody = this._dom.querySelector('.htCore > tbody');
        if (tbody) {
            for (let r = index; r < index + count; r++) {
                tbody.appendChild(tbody.children[index]);
            }
        }
        this.alter('remove_row', index, count);
    }

    insertCol(index: number): void {
        this.alter('insert_col');
        const rows = this._dom.querySelectorAll('.htCore > tbody > tr');
        rows.forEach(function (tr: Element): void {
            const tds = tr.querySelectorAll('td');
            const lastTd = tds[tds.length - 1];
            const targetTd = tds[index];
            if (targetTd && lastTd) {
                targetTd.after(lastTd);
            }
        });
    }

    removeCol(index: number): void {
        const rows = this._dom.querySelectorAll('.htCore > tbody > tr');
        rows.forEach(function (tr: Element): void {
            const tds = tr.querySelectorAll('td');
            const targetTd = tds[index];
            const lastTd = tds[tds.length - 1];
            if (targetTd && lastTd) {
                lastTd.after(targetTd);
            }
        });
        this.alter('remove_col');
    }

    // ---- Selection rendering ----

    renderSelection(): void {
        const range = this.getSelectedRange();
        if (range) {
            const from = range.getTopLeftCorner();
            const to = range.getBottomRightCorner();
            for (let row = from.row; row <= to.row; row++) {
                for (let col = from.col; col <= to.col; col++) {
                    this.renderCell(row, col);
                }
            }
        }
    }

    // ---- Data accessors ----

    getTableData(): DecisionTableData {
        return this.decisionTable;
    }

    getCurrentCellData(): CellData {
        const highlight = this.getHighlight();
        return this.getCellData(highlight.row, highlight.col);
    }

    getCurrentRowData(): RowData {
        const highlight = this.getHighlight();
        return this.getRowData(highlight.row);
    }

    getCurrentColData(): ColData {
        const highlight = this.getHighlight();
        return this.getColData(highlight.col);
    }

    getCellDatas(): CellData[] {
        const cellMap = this.decisionTable.cellMap;
        if (!this.decisionTable.cells && cellMap) {
            this.decisionTable.cells = [];
            for (const p in cellMap) {
                this.decisionTable.cells.push(cellMap[p]);
            }
        }
        return this.decisionTable.cells!;
    }

    getRowDatas(): RowData[] {
        return this.decisionTable.rows || [];
    }

    getColDatas(): ColData[] {
        return this.decisionTable.columns || [];
    }

    getColData(col: number): ColData | undefined {
        const colDatas = this.getColDatas();
        for (let i = 0; i < colDatas.length; i++) {
            if (colDatas[i].num === col) {
                return colDatas[i];
            }
        }
        return undefined;
    }

    getRowData(row: number): RowData | undefined {
        const rowDatas = this.getRowDatas();
        for (let i = 0; i < rowDatas.length; i++) {
            if (rowDatas[i].num === row) {
                return rowDatas[i];
            }
        }
        return undefined;
    }

    getCellData(row: number, col: number): CellData | null {
        const cells = this.getCellDatas();
        for (let i = 0; i < cells.length; i++) {
            if (cells[i].row === row && cells[i].col === col) {
                return cells[i];
            }
        }
        return null;
    }

    getCellDataByCol(col: number): CellData[] {
        const cells = this.getCellDatas(), result: CellData[] = [];
        for (let i = 0; i < cells.length; i++) {
            if (cells[i].col === col) {
                result.push(cells[i]);
            }
        }
        return result;
    }

    getCellDataByRow(row: number): CellData[] {
        const cells = this.getCellDatas(), result: CellData[] = [];
        for (let i = 0; i < cells.length; i++) {
            if (cells[i].row === row) {
                result.push(cells[i]);
            }
        }
        return result;
    }

    // ---- Data creation/removal ----

    createCellDataRange(fromRow: number, toRow: number, fromCol: number, toCol: number): void {
        for (let r = fromRow; r <= toRow; r++) {
            for (let c = fromCol; c <= toCol; c++) {
                this.createCellData(r, c);
            }
        }
    }

    createCellData(row: number, col: number): CellData {
        const cellData: CellData = {
            row: row,
            col: col,
            rowspan: 1
        };
        this.getCellDatas().push(cellData);
        return cellData;
    }

    createCellDataByCopyNextCol(col: number): void {
        const self = this,
            cellDatas = self.getCellDataByCol(col + 1);
        cellDatas.forEach(function (cellData: CellData): void {
            const cell = self.createCellData(cellData.row, col);
            cell.rowspan = cellData.rowspan;
        });
    }

    removeCellDataRange(fromRow: number, toRow: number, fromCol: number, toCol: number): void {
        for (let r = fromRow; r <= toRow; r++) {
            for (let c = fromCol; c <= toCol; c++) {
                this.removeCellData(r, c);
            }
        }
    }

    removeCellData(row: number, col: number): void {
        const cellDatas = this.getCellDatas();
        const cellData = this.getCellData(row, col);
        if (cellData) {
            const index = cellDatas.indexOf(cellData);
            cellDatas.splice(index, 1);
        }
    }

    createRowData(row: number): RowData {
        const rowData: RowData = {
            num: row,
            height: 40
        };
        this.getRowDatas().push(rowData);
        return rowData;
    }

    removeRowData(row: number): void {
        const rowDatas = this.getRowDatas();
        const rowData = this.getRowData(row);
        if (rowData) {
            const index = rowDatas.indexOf(rowData);
            rowDatas.splice(index, 1);
        }
    }

    removeRowDataRange(start: number, count?: number): void {
        count = count || 1;
        for (let r = start; r < start + count; r++) {
            this.removeRowData(r);
        }
    }

    createColData(col: number): ColData {
        const colData: ColData = {
            num: col
        };
        this.getColDatas().push(colData);
        return colData;
    }

    removeColData(col: number): void {
        const colDatas = this.getColDatas();
        const colData = this.getColData(col);
        if (colData) {
            const index = colDatas.indexOf(colData);
            colDatas.splice(index, 1);
        }
    }

    // ---- Translation (row/col shifting) ----

    translateRow(start: number, count?: number): void {
        count = count || 1;
        if (count > 0) {
            for (let r = this.getLastRowIndex(); r >= start; r--) {
                this.translateRowHeader(r, count);
            }
            for (let r = this.getLastRowIndex(); r >= start; r--) {
                for (let c = 0; c < this.countCols(); c++) {
                    this.translateCell(r, c, count, 0);
                }
            }
        } else if (count < 0) {
            for (let r = start; r < this.countRows(); r++) {
                this.translateRowHeader(r, count);
            }
            for (let r = start; r < this.countRows(); r++) {
                for (let c = 0; c < this.countCols(); c++) {
                    this.translateCell(r, c, count, 0);
                }
            }
        }
    }

    translateCol(start: number, count?: number): void {
        count = count || 1;
        if (count > 0) {
            for (let c = this.getLastColIndex(); c >= start; c--) {
                this.translateColHeader(c, count);
            }
            for (let r = 0; r < this.countRows(); r++) {
                for (let c = this.getLastColIndex(); c >= start; c--) {
                    this.translateCell(r, c, 0, count);
                }
            }
        } else if (count < 0) {
            for (let c = start; c < this.countCols(); c++) {
                this.translateColHeader(c, count);
            }
            for (let r = 0; r < this.countRows(); r++) {
                for (let c = start; c < this.countCols(); c++) {
                    this.translateCell(r, c, 0, count);
                }
            }
        }
    }

    translateCell(row: number, col: number, rowCount: number, colCount: number): void {
        const cellData = this.getCellData(row, col);
        if (cellData) {
            cellData.row = cellData.row + rowCount;
            cellData.col = cellData.col + colCount;
        }
    }

    translateRowHeader(row: number, count: number): void {
        const rowData = this.getRowData(row);
        if (rowData) {
            rowData.num = rowData.num + count;
        }
    }

    translateColHeader(col: number, count: number): void {
        const colData = this.getColData(col);
        if (colData) {
            colData.num = colData.num + count;
        }
    }

    translateColHeaderRange(start: number, count?: number): void {
        count = count || 1;
        for (let c = start; c < this.countCols(); c++) {
            this.translateColHeader(c, count);
        }
    }

    // ---- Counting helpers ----

    countCriteriaCols(): number {
        const colDatas = this.getColDatas();
        let count = 0;
        for (let i = 0; i < colDatas.length; i++) {
            if (colDatas[i].type === 'Criteria') {
                ++count;
            }
        }
        return count;
    }

    countActionCols(): number {
        const colDatas = this.getColDatas();
        let count = 0;
        for (let i = 0; i < colDatas.length; i++) {
            if (colDatas[i].type !== 'Criteria') {
                ++count;
            }
        }
        return count;
    }

    getLastRowIndex(): number {
        return this.countRows() - 1;
    }

    getLastColIndex(): number {
        return this.countCols() - 1;
    }

    getHighlight(): { row: number; col: number } | null {
        const range = this._handsontable.getSelectedRange();
        if (range) {
            return range.highlight;
        }
        return null;
    }

    setRowHeight(row: number, height: number): void {
        const inst = this.getInstance();
        if (inst && inst.manualRowHeights) {
            inst.manualRowHeights[row] = height;
        }
    }

    setColWidth(col: number, width: number | undefined): void {
        const inst = this.getInstance();
        if (inst && inst.manualColumnWidths) {
            inst.manualColumnWidths[col] = width;
        }
    }

    // ---- Merge helpers ----

    mergeRange(end: number, rowspan?: number): void {
        const cellData = this.getCurrentCellData(),
            row = cellData.row + cellData.rowspan - 1,
            col = cellData.col;
        rowspan = rowspan || 1;
        for (let c = 0; c <= end; c++) {
            this.merge(row, c, rowspan);
        }
    }

    merge(row: number, col: number, rowspan: number): void {
        let cellData = this.getCellData(row, col);
        while (!cellData) {
            row--;
            cellData = this.getCellData(row, col);
        }
        if (cellData!.rowspan + rowspan === 0) {
            this.removeCellData(row, col);
        } else {
            cellData!.rowspan = cellData!.rowspan + rowspan;
        }
    }

    unmerge(row: number, col: number): void {
        const cellData = this.getCellData(row, col);
        cellData!.rowspan = 1;
    }

    // ---- Rendering ----

    renderRowRange(start: number): void {
        for (let r = start; r < this.countRows(); r++) {
            this.renderCells(r);
        }
    }

    renderColRange(start: number): void {
        for (let c = start; c < this.countCols(); c++) {
            this.renderCells(undefined, c);
        }
    }

    renderCells(row?: number, col?: number): void {
        if (row !== undefined && row > 0 && col !== undefined && col > 0) {
            this.renderCell(row, col);
        } else if (row !== undefined && row > 0) {
            for (let c = 0; c < this.countCols(); c++) {
                this.renderCell(row, c);
            }
        } else if (col !== undefined && col > 0) {
            for (let r = 0; r < this.countRows(); r++) {
                this.renderCell(r, col);
            }
        } else {
            for (let r = 0; r < this.countRows(); r++) {
                for (let c = 0; c < this.countCols(); c++) {
                    this.renderCell(r, c);
                }
            }
        }
    }

    renderCell(row: number, col: number): void {
        const prop = this.colToProp(col),
            cellProperties = this.getCellMeta(row, col),
            renderer = this.getCellRenderer(cellProperties),
            TD = this.getCell(row, col);
        const value = this.getValue();
        renderer(this._handsontable, TD, row, col, prop, value, cellProperties);
        this._handsontable.runHooks('afterRenderer', TD, row, col, prop, value, cellProperties);
    }

    // ---- XML serialization ----

    toXml(): string {
        const decisionTable = this.getTableData(),
            cells = decisionTable.cells || [],
            rows = decisionTable.rows || [],
            cols = decisionTable.columns || [],
            libraries: Array<{ type: string; path: string }> = [],
            self = this;
        let xml: string;

        constantLibraries.forEach(function (path: string): void {
            libraries.push({ type: 'Constant', path: path });
        });

        actionLibraries.forEach(function (path: string): void {
            libraries.push({ type: 'Action', path: path });
        });

        variableLibraries.forEach(function (path: string): void {
            libraries.push({ type: 'Variable', path: path });
        });

        parameterLibraries.forEach(function (path: string): void {
            libraries.push({ type: 'Parameter', path: path });
        });

        xml = '<script-decision-table>';

        libraries.forEach(function (library: { type: string; path: string }): void {
            const type = library.type,
                path = library.path;
            if (type === 'Variable') {
                xml += '<import-variable-library path="' + path + '"/>';
            } else if (type === 'Constant') {
                xml += '<import-constant-library path="' + path + '"/>';
            } else if (type === 'Action') {
                xml += '<import-action-library path="' + path + '"/>';
            } else if (type === 'Parameter') {
                xml += '<import-parameter-library path="' + path + '"/>';
            }
        });

        cells.forEach(function (cell: CellData): void {
            xml += '<script-cell row="' + cell.row + '" col="' + cell.col + '" rowspan="' + cell.rowspan + '">';
            xml += '<![CDATA[' + (cell.script || '') + ']]>';
            xml += '</script-cell>';
        });

        rows.forEach(function (row: RowData): void {
            xml += '<row num="' + row.num + '" height="' + row.height + '"/>';
        });

        cols.forEach(function (col: ColData): void {
            const variableName = col.variableName;
            if (variableName) {
                xml += '<col num="' + col.num + '" width="' + col.width + '" type="' + col.type + '" var-category="' + (col.variableCategory === 'parameter' ? '参数' : col.variableCategory) + '" var-label="' + col.variableLabel + '" var="' + col.variableName + '" datatype="' + col.datatype + '"/>';
            } else {
                xml += '<col num="' + col.num + '" width="' + col.width + '" type="' + col.type + '"/>';
            }
        });

        xml += '</script-decision-table>';
        return xml;
    }

    // ---- Loading ----

    async load(callback?: () => void): Promise<void> {
        const self = this;
        const files = self.getRequestParameter('file');
        const data = await formPost<any[]>('/common/loadXml', { files: files! });
        const decisionTable = data[0];
        const libraries = decisionTable.libraries || [];
        libraries.forEach(function (library: { type: string; path: string }): void {
            const type = library.type,
                path = library.path;
            switch (type) {
                case 'Constant':
                    constantLibraries.push(path);
                    break;
                case 'Action':
                    actionLibraries.push(path);
                    break;
                case 'Variable':
                    variableLibraries.push(path);
                    break;
                case 'Parameter':
                    parameterLibraries.push(path);
                    break;
            }
        });
        self.decisionTable = decisionTable;
        refreshActionLibraries();
        refreshConstantLibraries();
        refreshVariableLibraries();
        refreshParameterLibraries();
        if (callback) {
            callback();
        }
    }

    getRequestParameter(name: string): string | null {
        let value: string | null = null;
        const params = window.location.search.substring(1).split('&');
        for (let i = 0; i < params.length; i++) {
            const param = params[i];
            if (param.indexOf('=') === -1) {
                continue;
            }
            const pair = param.split('=');
            const key = pair[0];
            if (key === name) {
                value = pair[1];
                break;
            }
        }
        return value;
    }

    // ---- Context menu initialization ----

    initMenu(): void {
        const self = this;
        const variableLibrary: any[] = [];
        const project = getParameter('project');
        const oldVariableLibrary = (window as any)._ruleforgeEditorVariableLibraries || [];
        oldVariableLibrary.forEach(function (lib: any): void {
            if (lib.type !== 'parameter') {
                variableLibrary.push(lib);
            }
        });
        const parameter = (window as any)._ruleforgeEditorParameterLibraries || [];
        if (parameter.length > 0) {
            parameter.forEach(function (p: any): void {
                variableLibrary.push([{
                    name: 'parameter',
                    type: 'parameter',
                    variables: parameter.length ? p : []
                }]);
            });
        }

        const onInsert = function (type: string, width?: number, add?: number): void {
            const highlight = self.getHighlight()!,
                col = highlight.col + (add || 0);
            self.translateCol(col);
            const column = self.createColData(col);
            column.type = type;
            column.width = width || 200;
            if (type === 'Criteria') {
                self.createCellDataByCopyNextCol(col);
            } else {
                self.createCellDataRange(0, self.getLastRowIndex(), col, col);
            }
            self.insertCol(col - 1);
            self.renderCells();
            self.setDirty();
            self.invoke('render');
        };

        const onShow = function (this: any): void {
            const actionCount = self.countActionCols();
            const criteriaCount = self.countCriteriaCols();
            const menuItem = this.getMenuItem('delete');
            if (!menuItem) return;
            if (menuItem.label === '删除动作列') {
                if (actionCount === 1) {
                    menuItem.hide();
                } else {
                    menuItem.show();
                }
            } else {
                if (criteriaCount === 1) {
                    menuItem.hide();
                } else {
                    menuItem.show();
                }
            }
        };

        const menuItems: any[] = [{
            label: '插入条件列',
            icon: 'glyphicon glyphicon-filter',
            subMenu: {
                menuItems: [{
                    label: '前',
                    icon: 'glyphicon glyphicon-chevron-left',
                    onClick: function (): void {
                        onInsert('Criteria', 120);
                    }
                }, {
                    label: '后',
                    icon: 'glyphicon glyphicon-chevron-right',
                    onClick: function (): void {
                        onInsert('Criteria', 120, 1);
                    }
                }]
            }
        }, {
            label: '删除条件列',
            icon: 'glyphicon glyphicon-minus-sign',
            name: 'delete',
            onClick: function (): void {
                const highlight = self.getHighlight()!,
                    col = highlight.col;
                self.removeCellDataRange(0, self.getLastRowIndex(), col, col);
                self.removeColData(col);
                self.translateCol(col, -1);
                self.removeCol(col);
                self.renderCells();
                self.setDirty();
                self.invoke('render');
            }
        }, {
            label: '插入执行方法动作列',
            icon: 'glyphicon glyphicon-flash',
            subMenu: {
                menuItems: [{
                    label: '前',
                    icon: 'glyphicon glyphicon-chevron-left',
                    onClick: function (): void {
                        onInsert('ExecuteMethod');
                    }
                }, {
                    label: '后',
                    icon: 'glyphicon glyphicon-chevron-right',
                    onClick: function (): void {
                        onInsert('ExecuteMethod', 200, 1);
                    }
                }]
            }
        }, {
            label: '插入变量赋值动作列',
            icon: 'glyphicon glyphicon-tasks',
            subMenu: {
                menuItems: [{
                    label: '前',
                    icon: 'glyphicon glyphicon-chevron-left',
                    onClick: function (): void {
                        onInsert('Assignment');
                    }
                }, {
                    label: '后',
                    icon: 'glyphicon glyphicon-chevron-right',
                    onClick: function (): void {
                        onInsert('Assignment', 200, 1);
                    }
                }]
            }
        }, {
            label: '插入控制台输出动作列',
            icon: 'glyphicon glyphicon-print',
            subMenu: {
                menuItems: [{
                    label: '前',
                    icon: 'glyphicon glyphicon-chevron-left',
                    onClick: function (): void {
                        onInsert('ConsolePrint');
                    }
                }, {
                    label: '后',
                    icon: 'glyphicon glyphicon-chevron-right',
                    onClick: function (): void {
                        onInsert('ConsolePrint', 200, 1);
                    }
                }]
            }
        }, {
            label: '删除动作列',
            name: 'delete',
            icon: 'glyphicon glyphicon-minus-sign',
            onClick: function (): void {
                const highlight = self.getHighlight()!,
                    col = highlight.col;
                self.removeColData(col);
                self.removeCellDataRange(0, self.getLastRowIndex(), col, col);
                self.translateCol(col, -1);
                self.removeCol(col);
                self.setDirty();
                self.invoke('render');
            }
        }];

        const onClick = function (menuItem: any): void {
            const highlight = self.getHighlight()!,
                col = highlight.col,
                parent = menuItem.parent.parent,
                column = self.getColData(col)!;
            column.variableCategory = parent.label === '参数' ? 'parameter' : parent.label;
            column.variableLabel = menuItem.label;
            column.variableName = menuItem.name;
            column.datatype = menuItem.datatype;
            self.setDirty();
            self.invoke('render');
        };

        const variabeMenuItem: any[] = [];
        variableLibrary.forEach(function (categories: any[]): void {
            categories.forEach(function (category: any): void {
                const menuItem: any = {
                    label: category.name === 'parameter' ? '参数' : category.name,
                    icon: category.type === 'parameter' ? 'glyphicon glyphicon-th-list' : 'glyphicon glyphicon-tasks'
                };
                const variables = category.variables;
                (variables || []).forEach(function (variable: any): void {
                    if (!menuItem.subMenu) {
                        menuItem.subMenu = { menuItems: [] };
                    }
                    const subMenuItem = {
                        icon: 'glyphicon glyphicon-tasks',
                        name: variable.name,
                        label: variable.label,
                        datatype: variable.type,
                        act: variable.act,
                        onClick: onClick
                    };
                    menuItem.subMenu.menuItems.push(subMenuItem);
                });
                variabeMenuItem.push(menuItem);
            });
        });

        const criteriaConfig: any = {
            onShow: onShow,
            menuItems: []
        };
        criteriaConfig.menuItems.push(menuItems[0]);
        criteriaConfig.menuItems.push(menuItems[1]);
        criteriaConfig.menuItems = criteriaConfig.menuItems.concat(variabeMenuItem);

        const actionConfig: any = {
            onShow: onShow,
            menuItems: []
        };
        const assignmentConfig: any = {
            onShow: onShow
        };

        actionConfig.menuItems.push(menuItems[2]);
        actionConfig.menuItems.push(menuItems[3]);
        actionConfig.menuItems.push(menuItems[4]);
        actionConfig.menuItems.push(menuItems[5]);
        assignmentConfig.menuItems = actionConfig.menuItems;
        assignmentConfig.menuItems = assignmentConfig.menuItems.concat(variabeMenuItem);

        const criteriaCellConfig: any = {
            menuItems: [menuItems[7], menuItems[6]]
        };
        const actionCellConfig: any = {
            menuItems: [menuItems[7]]
        };

        if (!self.criteriaMenu) {
            self.criteriaMenu = new RuleForge.menu.Menu(criteriaConfig);
        } else {
            self.criteriaMenu.setConfig(criteriaConfig);
        }
        if (!self.assignmentMenu) {
            self.assignmentMenu = new RuleForge.menu.Menu(assignmentConfig);
        } else {
            self.assignmentMenu.setConfig(assignmentConfig);
        }
        if (!self.actionMenu) {
            self.actionMenu = new RuleForge.menu.Menu(actionConfig);
        } else {
            self.actionMenu.setConfig(actionConfig);
        }
        if (!self.criteriaCellMenu) {
            self.criteriaCellMenu = new RuleForge.menu.Menu(criteriaCellConfig);
        } else {
            self.criteriaCellMenu.setConfig(criteriaCellConfig);
        }
        if (!self.actionCellMenu) {
            self.actionCellMenu = new RuleForge.menu.Menu(actionCellConfig);
        } else {
            self.actionCellMenu.setConfig(actionCellConfig);
        }

        self.container.addEventListener('contextmenu', function (e: MouseEvent): void {
            const th = (e.target as HTMLElement).closest('th');
            const tr = (e.target as HTMLElement).closest('tr');
            const parent = th || tr;
            if (parent) {
                const isCriteriaColHeader = parent.querySelectorAll('span.colHeader .glyphicon-filter').length > 0,
                    isAssignmentColHeader = parent.querySelectorAll('span.colHeader .glyphicon-tasks').length > 0,
                    isColHeader = parent.querySelectorAll('span.colHeader').length > 0,
                    count = self.countCriteriaCols();
                if (isCriteriaColHeader && self.criteriaMenu.menuItems.length > 0) {
                    self.criteriaMenu.show(e);
                } else if (isAssignmentColHeader && self.assignmentMenu.menuItems.length > 0) {
                    self.assignmentMenu.show(e);
                } else if (isColHeader && self.actionMenu.menuItems.length > 0) {
                    self.actionMenu.show(e);
                }
            }
        });
    }
}

// Expose as RuleForge.DecisionTable for backward compatibility
if (!(window as any).RuleForge) {
    (window as any).RuleForge = {};
}
(window as any).RuleForge.DecisionTable = ScriptDecisionTable;
