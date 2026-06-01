/**
 * ComplexScoreCard - Main complex scorecard grid class.
 *
 * Manages the overall complex scorecard grid including header row,
 * content rows, condition columns, action columns, property configuration,
 * and scoring action. Provides methods for initializing from server data,
 * adding/removing rows and columns, and serializing to XML.
 */

import HeaderRow from './HeaderRow';
import RowContext from './RowContext';
import ContentRow from './ScoreCardRow';
import ConditionColumn, { ActionColumn } from './ScoreCardColumn';
import TableAction from './TableAction';
import { getParameter, handleResponseError } from '../../Utils.js';
import { save, saveNewVersion, formPost } from '../../api/client.js';
import {
    constantLibraries,
    actionLibraries,
    variableLibraries,
    parameterLibraries,
    refreshActionLibraries,
    refreshConstantLibraries,
    refreshVariableLibraries,
    refreshParameterLibraries,
    refreshFunctionLibraries
} from '../common/URule';
import { Remark } from '../../Remark.js';

declare const MsgBox: {
    confirm(message: string, callback: () => void): void;
};

export default class ComplexScoreCard {
    id: number = 0;
    conditionColumns: ConditionColumn[] = [];
    actionColumns: ActionColumn[] = [];
    contentRows: ContentRow[] = [];
    properties: any[] = [];
    cells: any[] = [];
    remark: any;
    propertyContainer!: HTMLElement;
    table!: HTMLTableElement;
    tbody!: HTMLTableSectionElement;
    headerRow!: HeaderRow;
    tableAction!: TableAction;
    highlightDiv?: HTMLDivElement & { currentTD?: HTMLTableCellElement };

    constructor(container: HTMLElement) {
        this.id = 0;
        this.conditionColumns = [];
        this.actionColumns = [];
        this.contentRows = [];
        this.properties = [];
        this.cells = [];
        this.init(container);
    }

    init(container: HTMLElement): void {
        const self = this;

        // Remark
        const remarkContainer = document.createElement('div');
        remarkContainer.style.cssText = 'margin:5px 5px 5px 15px';
        container.appendChild(remarkContainer);
        this.remark = new (Remark as any)(remarkContainer);

        // Property configuration
        const propertyContainer = document.createElement('div');
        propertyContainer.style.cssText = 'margin:5px 5px 5px 15px;';
        container.appendChild(propertyContainer);
        const propertySpan = document.createElement('span');
        propertySpan.style.cssText = 'padding: 10px';
        this.propertyContainer = propertySpan;
        const addPropertyButton = document.createElement('button');
        addPropertyButton.type = 'button';
        addPropertyButton.className = 'rule-add-property btn btn-link';
        addPropertyButton.textContent = '添加属性';
        propertyContainer.appendChild(addPropertyButton);
        propertyContainer.appendChild(propertySpan);

        const onPropertyClick = function (menuItem: MenuItemConfig) {
            const prop = new (ruleforge as any).RuleProperty(self, menuItem.name, menuItem.defaultValue, menuItem.editorType);
            self.propertyContainer.appendChild(prop.getContainer());
            self.properties.push(prop);
            window._setDirty?.();
        };

        // Property menu
        const menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '优先级',
                name: 'salience',
                defaultValue: '10',
                editorType: 1,
                onClick: onPropertyClick
            }, {
                label: '生效日期',
                name: 'effective-date',
                defaultValue: '',
                editorType: 2,
                onClick: onPropertyClick
            }, {
                label: '失效日期',
                name: 'expires-date',
                defaultValue: '',
                editorType: 2,
                onClick: onPropertyClick
            }, {
                label: '是否启用',
                name: 'enabled',
                defaultValue: true,
                editorType: 3,
                onClick: onPropertyClick
            }, {
                label: '允许调试信息输出',
                name: 'debug',
                defaultValue: true,
                editorType: 3,
                onClick: onPropertyClick
            }]
        });

        addPropertyButton.addEventListener('click', function (e) {
            menu.show(e);
        });

        // Main table
        const table = document.createElement('table');
        table.className = 'table table-bordered';
        table.style.cssText = 'width: max-content;max-width: none;margin-left: 15px';
        this.table = table;
        container.appendChild(table);
        const tbody = document.createElement('tbody');
        this.tbody = tbody;
        table.appendChild(tbody);

        // Header row
        this.headerRow = new HeaderRow(this);
        table.appendChild(this.headerRow.tr);

        // Create an initial empty row
        new RowContext(this);

        // Load file
        this.loadFile(this._buildLoadDataFunction());

        // Scoring action section
        const actionContainer = document.createElement('div');
        actionContainer.style.cssText = 'padding-left:10px';
        container.appendChild(actionContainer);
        this.tableAction = new TableAction(actionContainer, true);
    }

    save(isNewVersion: boolean): void {
        const self = this;
        const file = getParameter('file');
        let xml: string | null = null;
        try {
            xml = self.toXml();
        } catch (e: any) {
            window.bootbox.alert(e);
            return;
        }

        const postData: Record<string, string> = {
            content: encodeURIComponent(xml),
            file: file,
            newVersion: String(isNewVersion)
        };
        const saveUrl = '/common/saveFile';

        if (isNewVersion) {
            saveNewVersion(saveUrl, { file, content: encodeURIComponent(xml) }).then(function () {
                window.bootbox.alert('保存成功!', function () {
                    self.resetState();
                });
            }).catch(function () {});
        } else {
            save(saveUrl, postData).then(function () {
                window.bootbox.alert('保存成功!', function () {
                    self.resetState();
                });
            });
        }
    }

    addCriteriaRow(): void {
        const rowContext = new RowContext(this);
        rowContext.setRefConditionCell((window as any)._currentConditionCell);
        this.addRow(rowContext);
    }

    deleteCriteriaRow(): void {
        if (!(window as any)._currentConditionCell) {
            window.bootbox.alert('请先选中目标行的一个条件单元格');
            return;
        }
        MsgBox.confirm('真的要删除当前单元格所在的所有行？', () => {
            (window as any)._currentConditionCell.deleteRow(this);
            (window as any)._currentConditionCell = null;
        });
    }

    resetState(): void {
        if ((window as any).cancelDirty) (window as any).cancelDirty();
    }

    _buildLoadDataFunction(): (data: any) => void {
        const self = this;
        const rowContext = new RowContext(this);

        return function (data: any) {
            self.tableAction.initData(data);

            // Process columns
            for (const colData of data.columns) {
                const type = colData.type;
                rowContext.setWidth(colData.width);

                if (type === 'Criteria') {
                    const col = self.addConditionColumn(rowContext);
                    self._findHeaderCell(col, false)!.updateLabel(colData);
                } else if (type === 'Score') {
                    rowContext.setActionType(1);
                    self.addActionColumn(rowContext);
                } else if (type === 'Custom') {
                    rowContext.setActionType(2);
                    rowContext.setCustomActionHeaderLabel(colData.customLabel);
                    self.addActionColumn(rowContext);
                }
            }

            // Process rows
            let rowIndex = 0;
            for (const rowData of data.rows) {
                const rowCells = self._findRowCells(rowIndex, data.cellMap);
                rowContext.setRowCells(rowCells);
                self.addRow(rowContext);
                rowIndex++;
            }

            (window as any)._VariableValueArray.push(self);
            (window as any)._ParameterValueArray.push(self);
            (window as any).cancelDirty();
        };
    }

    initMenu(): void {
        for (const col of this.conditionColumns) {
            if (!col.variables) {
                const category = col.variableCategory;
                col.variables = this._findVariables(category);
                col.refreshConditionCellVariableMenus();
            }
        }
    }

    _findVariables(category: string): any[] | undefined {
        if (category === '参数') {
            let allParams: any[] = [];
            if ((window as any)._uruleEditorParameterLibraries) {
                for (const lib of (window as any)._uruleEditorParameterLibraries) {
                    allParams = allParams.concat(lib);
                }
            }
            return allParams;
        }

        if ((window as any)._uruleEditorVariableLibraries) {
            for (const libGroup of (window as any)._uruleEditorVariableLibraries) {
                for (const lib of libGroup) {
                    if (lib.name === category) {
                        return lib.variables;
                    }
                }
            }
        }
        return undefined;
    }

    _findHeaderCell(col: any, isAction: boolean): any {
        const headers = isAction ? this.headerRow.actionHeaders : this.headerRow.conditionHeaders;

        for (const header of headers) {
            if (isAction) {
                if (header.actionCol === col) {
                    return header;
                }
            } else {
                if (header.conditionCol === col) {
                    return header;
                }
            }
        }
        return null;
    }

    _findRowCells(rowIndex: number, cellMap: Record<string, any>): any[] {
        const cells: any[] = [];
        for (const key in cellMap) {
            const cell = cellMap[key];
            if (cell.row === rowIndex) {
                cells.push(cell);
            }
        }
        return cells;
    }

    loadFile(callback: (data: any) => void): void {
        const self = this;
        const file = getParameter('file');
        let loadPath = '/common/loadXml';
        const doImport = getParameter('doImport');
        if (doImport && doImport.length > 1) {
            loadPath += '?doImport=true';
        }

        formPost(loadPath, { files: file }).then(function (response: any[]) {
            const data = response[0];
            self.remark.setData(data.remark);

            // Load properties
            const salience = data.salience;
            if (salience) {
                self.addProperty(new (ruleforge as any).RuleProperty(self, 'salience', salience, 1));
            }
            const loop = data.loop;
            if (loop != null) {
                self.addProperty(new (ruleforge as any).RuleProperty(self, 'loop', loop, 3));
            }
            const effectiveDate = data.effectiveDate;
            if (effectiveDate) {
                self.addProperty(new (ruleforge as any).RuleProperty(self, 'effective-date', effectiveDate, 2));
            }
            const expiresDate = data.expiresDate;
            if (expiresDate) {
                self.addProperty(new (ruleforge as any).RuleProperty(self, 'expires-date', expiresDate, 2));
            }
            const enabled = data.enabled;
            if (enabled != null) {
                self.addProperty(new (ruleforge as any).RuleProperty(self, 'enabled', enabled, 3));
            }
            const debug = data.debug;
            if (debug != null) {
                self.addProperty(new (ruleforge as any).RuleProperty(self, 'debug', debug, 3));
            }

            // Load libraries
            const libraries = data.libraries || [];
            libraries.forEach(function (lib: any) {
                const type = lib.type;
                const path = lib.path;
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
                }
            });
            refreshActionLibraries();
            refreshConstantLibraries();
            refreshVariableLibraries();
            refreshParameterLibraries();
            refreshFunctionLibraries();

            if (callback) callback(data);
        }).catch(function (error: any) {
            document.body.innerHTML = '';
            handleResponseError(error, '服务端错误：');
        });
    }

    addProperty(prop: any): void {
        this.propertyContainer.appendChild(prop.getContainer());
        this.properties.push(prop);
        window._setDirty?.();
    }

    addRow(rowContext: RowContext): void {
        const row = new ContentRow(rowContext);
        const refConditionCell = rowContext.refConditionCell;

        if (refConditionCell) {
            const refRow = refConditionCell.contentRow;
            let insertIndex = this.contentRows.indexOf(refRow);
            const rowspan = refConditionCell.td.rowSpan;
            if (rowspan) {
                insertIndex = insertIndex + rowspan - 1;
            }
            const targetRow = this.contentRows[insertIndex];
            targetRow.tr.after(row.tr);
            this.contentRows.splice(insertIndex + 1, 0, row);
        } else {
            this.tbody.appendChild(row.tr);
            this.contentRows.push(row);
        }

        this.rebuildBorder();
        window._setDirty?.();
    }

    addConditionColumn(rowContext: RowContext): ConditionColumn {
        const col = new ConditionColumn(rowContext);

        if (rowContext.refHeaderCell) {
            const index = rowContext.refHeaderCellIndex;
            if (rowContext.before) {
                this.conditionColumns.splice(index, 0, col);
            } else {
                this.conditionColumns.splice(index + 1, 0, col);
            }
        } else {
            this.conditionColumns.push(col);
        }

        this.rebuildBorder();
        return col;
    }

    addActionColumn(rowContext: RowContext): ActionColumn {
        const col = new ActionColumn(rowContext);

        if (rowContext.refHeaderCell) {
            const index = rowContext.refHeaderCellIndex;
            if (rowContext.before) {
                this.actionColumns.splice(index, 0, col);
            } else {
                this.actionColumns.splice(index + 1, 0, col);
            }
        } else {
            this.actionColumns.push(col);
        }

        this.rebuildBorder();
        return col;
    }

    rebuildBorder(): void {
        if (this.headerRow.actionHeaders.length === 0) return;

        this.headerRow.actionHeaders[0].td.style.borderLeft = 'solid 3px #9d9d9d';
        for (const row of this.contentRows) {
            row.actionCells[0].td.style.borderLeft = 'solid 3px #9d9d9d';
        }
    }

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

    nextId(): number {
        return this.id++;
    }

    toXml(): string {
        let xml = '<?xml version="1.0" encoding="UTF-8"?>';
        xml += '<complex-scorecard ' + this.tableAction.toXml();

        for (const prop of this.properties) {
            xml += ' ' + prop.toXml();
        }
        xml += '>';
        xml += this.remark.toXml();

        // Import libraries
        const libraries = this._buildLibraries();
        for (const lib of libraries) {
            const type = lib.type;
            const path = lib.path;
            if (type === 'Variable') {
                xml += '<import-variable-library path="' + path + '"/>';
            } else if (type === 'Constant') {
                xml += '<import-constant-library path="' + path + '"/>';
            } else if (type === 'Action') {
                xml += '<import-action-library path="' + path + '"/>';
            } else if (type === 'Parameter') {
                xml += '<import-parameter-library path="' + path + '"/>';
            }
        }

        // Rows
        const allCells: any[] = [];
        this.contentRows.forEach(function (row, index) {
            row.num = index;
            xml += '<row num="' + row.num + '" height="30"/>';
            allCells.push(...row.conditionCells, ...row.actionCells);
        });

        // Condition columns
        this.conditionColumns.forEach(function (col, index) {
            col.num = index;
            if (!col.variableCategory) {
                throw '第[' + (col.num + 1) + ']条件列未定义具体变量或参数！';
            }
            xml += '<col num="' + col.num + '" width="' + col.width + '" type="Criteria" var-category="' +
                (col.variableCategory === 'parameter' ? '参数' : col.variableCategory) + '"/>';
        });

        // Action columns
        const conditionColCount = this.conditionColumns.length;
        this.actionColumns.forEach(function (col, index) {
            col.num = conditionColCount + index;
            const variableName = col.variableName;
            const actionType = col.actionType;
            let colType = 'Criteria';
            if (actionType === 1) colType = 'Score';
            else if (actionType === 2) colType = 'Custom';

            if (variableName) {
                xml += '<col num="' + col.num + '" width="' + col.width + '" type="' + colType +
                    '" var-category="' + (col.variableCategory === 'parameter' ? '参数' : col.variableCategory) + '"/>';
            } else if (actionType === 2) {
                xml += '<col num="' + col.num + '" width="' + col.width + '" type="' + colType +
                    '" custom-label="' + col.actionHeaderCell.customLabel + '"/>';
            } else {
                xml += '<col num="' + col.num + '" width="' + col.width + '" type="' + colType + '"/>';
            }
        });

        // Cells
        allCells.forEach(function (cell) {
            const rowspan = cell.td.rowSpan;
            if (cell.conditionCol) {
                if (!cell.variableLabel) {
                    throw '请选择条件条件格[' + (cell.contentRow.num + 1) + ',' + (cell.conditionCol.num + 1) + ']对应的对象属性！';
                }
                xml += '<cell row="' + cell.contentRow.num + '" col="' + cell.conditionCol.num +
                    '" rowspan="' + rowspan + '" var-label="' + cell.variableLabel +
                    '" var="' + cell.variableName + '" datatype="' + cell.datatype + '">';
            } else {
                xml += '<cell row="' + cell.contentRow.num + '" col="' + cell.actionCol.num +
                    '" rowspan="' + rowspan + '">';
            }
            xml += cell.toXml();
            xml += '</cell>';
        });

        xml += '</complex-scorecard>';
        return xml;
    }

    _buildLibraries(): Array<{ type: string; path: string }> {
        const libraries: Array<{ type: string; path: string }> = [];
        constantLibraries.forEach(function (path) {
            libraries.push({ type: 'Constant', path: path });
        });
        actionLibraries.forEach(function (path) {
            libraries.push({ type: 'Action', path: path });
        });
        variableLibraries.forEach(function (path) {
            libraries.push({ type: 'Variable', path: path });
        });
        parameterLibraries.forEach(function (path) {
            libraries.push({ type: 'Parameter', path: path });
        });
        return libraries;
    }
}
