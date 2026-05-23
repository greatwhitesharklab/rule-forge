/**
 * ComplexScoreCard - Main complex scorecard grid class.
 *
 * Manages the overall complex scorecard grid including header row,
 * content rows, condition columns, action columns, property configuration,
 * and scoring action. Provides methods for initializing from server data,
 * adding/removing rows and columns, and serializing to XML.
 *
 * Extracted from the complexScoreCard webpack bundle (module 354).
 */

import HeaderRow from './HeaderRow.js';
import RowContext from './RowContext.js';
import ContentRow from './ScoreCardRow.js';
import ConditionColumn from './ScoreCardColumn.js';
import {ActionColumn} from './ScoreCardColumn.js';
import TableAction from './TableAction.js';
import {getParameter, ajaxSave} from '../../Utils.js';
import {MsgBox} from 'flowdesigner';

export default class ComplexScoreCard {
    /**
     * @param {jQuery} container - The container element to render into
     */
    constructor(container) {
        this.id = 0;
        this.conditionColumns = [];
        this.actionColumns = [];
        this.contentRows = [];
        this.properties = [];
        this.cells = [];
        this.init(container);
    }

    /**
     * Build the entire UI: toolbar, remark, property config, table, and scoring action.
     *
     * @param {jQuery} container - The container element
     */
    init(container) {
        const self = this;

        // Toolbar
        container.append(`
            <nav class="navbar navbar-default" style="margin: 5px">
                <div>
                    <div>
                        <div class="btn-group btn-group-sm navbar-btn" style="margin-top:5px;margin-bottom: 0;margin-left: 5px" >
                            <button id="saveButtonNewVersion" type="button" class="btn btn-default btn-sm"><i class="rf rf-savenewversion" style="font-size: 14px"/> 生成版本</button>
                            <button id="saveButton" type="button" class="btn btn-default btn-sm" ><i class="rf rf-save" style="font-size: 14px"/> 保存</button>
                            <div class="btn-group btn-group-sm navbar-btn" style="margin-top:3px;margin-bottom: 0;margin-left: 5px" >
                                <button id="addCriteriaButton" type="button" class="btn btn-default btn-sm"><i class="glyphicon glyphicon-plus" style="font-size: 16px"/> 添加条件行</button>
                                <button id="deleteCriteriaButton" type="button" class="btn btn-default btn-sm"><i class="glyphicon glyphicon-minus" style="font-size: 16px"/> 删除条件行</button>
                            </div>
                        </div>
                        <div class="btn-group btn-group-sm navbar-btn" style="margin-top:5px;margin-bottom: 0">
                            <button id="configVarButton" type="button" class="btn btn-default btn-sm"><i class="rf rf-variable" style="font-size: 13px"/> 变量库</button>
                            <button id="configConstantsButton" type="button" class="btn btn-default btn-sm"><i class="rf rf-constant" style="font-size: 13px"/> 常量库</button>
                            <button id="configActionButton" type="button" class="btn btn-default btn-sm"><i class="rf rf-action" style="font-size: 13px"/> 动作库</button>
                            <button id="configParameterButton" type="button" class="btn btn-default btn-sm"><i class="rf rf-parameter" style="font-size: 13px"/> 参数库</button>
                        </div>
                    </div>
                </div>
            </nav>`);

        // Remark
        const remarkContainer = $("<div style='margin:5px 5px 5px 15px'></div>");
        container.append(remarkContainer);
        this.remark = new Remark(remarkContainer);

        // Property configuration
        const propertyContainer = $("<div style='margin:5px 5px 5px 15px;'></div>");
        container.append(propertyContainer);
        this.propertyContainer = $('<span>');
        this.propertyContainer.css({padding: '10px'});
        const addPropertyButton = $("<button type='button' class='rule-add-property btn btn-link'>添加属性</button>");
        propertyContainer.append(addPropertyButton);
        propertyContainer.append(this.propertyContainer);

        const onPropertyClick = function (menuItem) {
            const prop = new urule.RuleProperty(self, menuItem.name, menuItem.defaultValue, menuItem.editorType);
            self.propertyContainer.append(prop.getContainer());
            self.properties.push(prop);
            window._setDirty();
        };

        /**
         * Save the scorecard file.
         * @param {boolean} isNewVersion - Whether to save as a new version
         */
        function save(isNewVersion) {
            if (!isNewVersion && $('#saveButton').hasClass('disabled')) return false;

            const file = getParameter('file');
            let xml = null;
            try {
                xml = self.toXml();
            } catch (e) {
                bootbox.alert(e);
                return;
            }

            const postData = {
                content: encodeURIComponent(xml),
                file: file,
                newVersion: isNewVersion
            };
            const saveUrl = window._server + '/common/saveFile';

            if (isNewVersion) {
                $.ajax({
                    url: window._server + '/common/checkFileDirty',
                    type: 'post',
                    data: {
                        filePath: file,
                        content: encodeURIComponent(xml)
                    },
                    success: function (res) {
                        if (res.status) {
                            if (res.data) {
                                let decodedFileName = decodeURIComponent(file);
                                if (decodedFileName.includes('%')) {
                                    decodedFileName = decodeURIComponent(decodedFileName);
                                }
                                bootbox.confirm('是否对【' + decodedFileName + '】生成新版本?', function (confirmed) {
                                    if (confirmed) {
                                        ajaxSave(saveUrl, postData, function (res) {
                                            if (res.status) {
                                                bootbox.alert('保存成功!', function () {
                                                    self.resetState();
                                                });
                                            } else {
                                                bootbox.alert(res.message || '保存失败');
                                            }
                                        });
                                    }
                                });
                            } else {
                                bootbox.alert('与最新版本无差异，无需生成新版本');
                            }
                        } else {
                            bootbox.alert("<span style='color: red'>服务端出错</span>");
                        }
                    }
                });
            } else {
                ajaxSave(saveUrl, postData, function () {
                    bootbox.alert('保存成功!', function () {
                        self.resetState();
                    });
                });
            }
        }

        // Property menu
        self.menu = new URule.menu.Menu({
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

        addPropertyButton.click(function (e) {
            self.menu.show(e);
        });

        // Bind toolbar buttons
        $('#addCriteriaButton').click(function () {
            const rowContext = new RowContext(self);
            rowContext.setRefConditionCell(window._currentConditionCell);
            self.addRow(rowContext);
        });

        $('#deleteCriteriaButton').click(function () {
            if (!window._currentConditionCell) {
                bootbox.alert('请先选中目标行的一个条件单元格');
                return;
            }
            MsgBox.confirm('真的要删除当前单元格所在的所有行？', function () {
                window._currentConditionCell.deleteRow(self);
                window._currentConditionCell = null;
            });
        });

        $('#configVarButton').click(function () {
            if (!self.configVarDialog) {
                self.configVarDialog = new urule.ConfigVariableDialog(self);
            }
            self.configVarDialog.open();
        });

        $('#configConstantsButton').click(function () {
            if (!self.configConstantDialog) {
                self.configConstantDialog = new urule.ConfigConstantDialog(self);
            }
            self.configConstantDialog.open();
        });

        $('#configActionButton').click(function () {
            if (!self.configActionDialog) {
                self.configActionDialog = new urule.ConfigActionDialog(self);
            }
            self.configActionDialog.open();
        });

        $('#configParameterButton').click(function () {
            if (!self.configParameterDialog) {
                self.configParameterDialog = new urule.ConfigParameterDialog(self);
            }
            self.configParameterDialog.open();
        });

        $('#saveButton').click(function () {
            save(false);
        });

        $('#saveButtonNewVersion').click(function () {
            save(true);
        });

        // Main table
        this.table = $('<table class="table table-bordered" style="width: max-content;max-width: none;margin-left: 15px"></table>');
        container.append(this.table);
        this.tbody = $('<tbody></tbody>');
        this.table.append(this.tbody);

        // Header row
        this.headerRow = new HeaderRow(this);
        this.table.append(this.headerRow.tr);

        // Create an initial empty row
        new RowContext(this);

        // Load file
        this.loadFile(this._buildLoadDataFunction());

        // Scoring action section
        const actionContainer = $('<div style="padding-left:10px"></div>');
        container.append(actionContainer);
        this.tableAction = new TableAction(actionContainer, true);
    }

    /**
     * Reset the dirty state after saving.
     */
    resetState() {
        window.cancelDirty();
    }

    /**
     * Build the data loading function that processes server response.
     *
     * @returns {Function} A function that takes server data and populates the table
     */
    _buildLoadDataFunction() {
        const self = this;
        const rowContext = new RowContext(this);

        return function (data) {
            self.tableAction.initData(data);

            // Process columns
            for (const colData of data.columns) {
                const type = colData.type;
                rowContext.setWidth(colData.width);

                if (type === 'Criteria') {
                    const col = self.addConditionColumn(rowContext);
                    self._findHeaderCell(col, false).updateLabel(colData);
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

            window._VariableValueArray.push(self);
            window._ParameterValueArray.push(self);
            window.cancelDirty();
        };
    }

    /**
     * Initialize variable menus for all condition columns.
     */
    initMenu() {
        for (const col of this.conditionColumns) {
            if (!col.variables) {
                const category = col.variableCategory;
                col.variables = this._findVariables(category);
                col.refreshConditionCellVariableMenus();
            }
        }
    }

    /**
     * Find variables for a given category name.
     *
     * @param {string} category - Variable category name
     * @returns {Array|undefined} Array of variable objects
     */
    _findVariables(category) {
        if (category === '参数') {
            let allParams = [];
            if (window._uruleEditorParameterLibraries) {
                for (const lib of window._uruleEditorParameterLibraries) {
                    allParams = allParams.concat(lib);
                }
            }
            return allParams;
        }

        if (window._uruleEditorVariableLibraries) {
            for (const libGroup of window._uruleEditorVariableLibraries) {
                for (const lib of libGroup) {
                    if (lib.name === category) {
                        return lib.variables;
                    }
                }
            }
        }
        return undefined;
    }

    /**
     * Find a header cell by its column.
     *
     * @param {ConditionColumn|ActionColumn} col - The column
     * @param {boolean} isAction - Whether to search action headers
     * @returns {HeaderCell|null}
     */
    _findHeaderCell(col, isAction) {
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

    /**
     * Find all cells for a given row index in the cell map.
     *
     * @param {number} rowIndex - The row index
     * @param {Object} cellMap - Map of row indices to cell data arrays
     * @returns {Array} Cell data for the specified row
     */
    _findRowCells(rowIndex, cellMap) {
        const cells = [];
        for (const key in cellMap) {
            const cell = cellMap[key];
            if (cell.row === rowIndex) {
                cells.push(cell);
            }
        }
        return cells;
    }

    /**
     * Load the scorecard file from the server.
     *
     * @param {Function} callback - Called with the server data
     */
    loadFile(callback) {
        const self = this;
        const file = getParameter('file');
        let loadUrl = window._server + '/common/loadXml';
        const doImport = getParameter('doImport');
        if (doImport && doImport.length > 1) {
            loadUrl += '?doImport=true';
        }

        $.ajax({
            url: loadUrl,
            type: 'POST',
            data: {files: file},
            error: function (error) {
                $(window.document.body).empty();
                if (error && error.status === 401) {
                    bootbox.alert('权限不足，不能进行此操作.');
                } else if (error && error.responseText) {
                    try {
                        const result = JSON.parse(error.responseText);
                        bootbox.alert("<span style='color: red'>服务端错误：" + result.errorMsg + '</span>');
                    } catch (e) {
                        bootbox.alert("<span style='color: red'>服务端错误：" + error.responseText + '</span>');
                    }
                } else {
                    bootbox.alert("<span style='color: red'>服务端出错</span>");
                }
            },
            success: function (response) {
                const data = response[0];
                self.remark.setData(data.remark);

                // Load properties
                const salience = data.salience;
                if (salience) {
                    self.addProperty(new urule.RuleProperty(self, 'salience', salience, 1));
                }
                const loop = data.loop;
                if (loop != null) {
                    self.addProperty(new urule.RuleProperty(self, 'loop', loop, 3));
                }
                const effectiveDate = data.effectiveDate;
                if (effectiveDate) {
                    self.addProperty(new urule.RuleProperty(self, 'effective-date', effectiveDate, 2));
                }
                const expiresDate = data.expiresDate;
                if (expiresDate) {
                    self.addProperty(new urule.RuleProperty(self, 'expires-date', expiresDate, 2));
                }
                const enabled = data.enabled;
                if (enabled != null) {
                    self.addProperty(new urule.RuleProperty(self, 'enabled', enabled, 3));
                }
                const debug = data.debug;
                if (debug != null) {
                    self.addProperty(new urule.RuleProperty(self, 'debug', debug, 3));
                }

                // Load libraries
                const libraries = data.libraries || [];
                $.each(libraries, function (index, lib) {
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
            }
        });
    }

    /**
     * Add a property to the configuration.
     *
     * @param {RuleProperty} prop - The property to add
     */
    addProperty(prop) {
        this.propertyContainer.append(prop.getContainer());
        this.properties.push(prop);
        window._setDirty();
    }

    /**
     * Add a new content row.
     *
     * @param {RowContext} rowContext - The row context
     */
    addRow(rowContext) {
        const row = new ContentRow(rowContext);
        const refConditionCell = rowContext.refConditionCell;

        if (refConditionCell) {
            const refRow = refConditionCell.contentRow;
            let insertIndex = this.contentRows.indexOf(refRow);
            const rowspan = refConditionCell.td.prop('rowspan');
            if (rowspan) {
                insertIndex = insertIndex + rowspan - 1;
            }
            const targetRow = this.contentRows[insertIndex];
            targetRow.tr.after(row.tr);
            this.contentRows.splice(insertIndex + 1, 0, row);
        } else {
            this.tbody.append(row.tr);
            this.contentRows.push(row);
        }

        this.rebuildBorder();
        window._setDirty();
    }

    /**
     * Add a new condition column.
     *
     * @param {RowContext} rowContext - The row context
     * @returns {ConditionColumn} The new condition column
     */
    addConditionColumn(rowContext) {
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

    /**
     * Add a new action column.
     *
     * @param {RowContext} rowContext - The row context
     * @returns {ActionColumn} The new action column
     */
    addActionColumn(rowContext) {
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

    /**
     * Rebuild the border styling between condition and action columns.
     * Adds a thicker left border to the first action column header and cells.
     */
    rebuildBorder() {
        if (this.headerRow.actionHeaders.length === 0) return;

        this.headerRow.actionHeaders[0].td.css('border-left', 'solid 3px #9d9d9d');
        for (const row of this.contentRows) {
            row.actionCells[0].td.css('border-left', 'solid 3px #9d9d9d');
        }
    }

    /**
     * Get or create the highlight div used to indicate the active cell.
     * @returns {jQuery}
     */
    getHighlightDiv() {
        if (this.highlightDiv) {
            const currentTD = this.highlightDiv.currentTD;
            if (currentTD) {
                currentTD.off('DOMSubtreeModified');
            }
        } else {
            this.highlightDiv = $('<div style="border:2px solid rgb(82, 146, 247);position:absolute;left: 0;top: 0;"></div>');
        }
        return this.highlightDiv;
    }

    /**
     * Generate the next unique ID.
     * @returns {number}
     */
    nextId() {
        return this.id++;
    }

    /**
     * Serialize the entire complex scorecard to XML.
     * @returns {string} XML string
     * @throws {string} On validation error
     */
    toXml() {
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
        const allCells = [];
        $.each(this.contentRows, function (index, row) {
            row.num = index;
            xml += '<row num="' + row.num + '" height="30"/>';
            allCells.push(...row.conditionCells, ...row.actionCells);
        });

        // Condition columns
        $.each(this.conditionColumns, function (index, col) {
            col.num = index;
            if (!col.variableCategory) {
                throw '第[' + (col.num + 1) + ']条件列未定义具体变量或参数！';
            }
            xml += '<col num="' + col.num + '" width="' + col.width + '" type="Criteria" var-category="' +
                   (col.variableCategory === 'parameter' ? '参数' : col.variableCategory) + '"/>';
        });

        // Action columns
        const conditionColCount = this.conditionColumns.length;
        $.each(this.actionColumns, function (index, col) {
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
        $.each(allCells, function (index, cell) {
            const rowspan = cell.td.prop('rowspan');
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

    /**
     * Build the libraries list for XML serialization.
     * @returns {Array} Array of {type, path} objects
     */
    _buildLibraries() {
        const libraries = [];
        $.each(constantLibraries, function (index, path) {
            libraries.push({type: 'Constant', path: path});
        });
        $.each(actionLibraries, function (index, path) {
            libraries.push({type: 'Action', path: path});
        });
        $.each(variableLibraries, function (index, path) {
            libraries.push({type: 'Variable', path: path});
        });
        $.each(parameterLibraries, function (index, path) {
            libraries.push({type: 'Parameter', path: path});
        });
        return libraries;
    }
}
