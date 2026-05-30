import {ajaxSave, getParameter} from '../../Utils.js';
/* bootbox is a global */
import {saveNewVersion} from "../../Utils";
import * as event from '../../components/componentEvent.js';

window._dirty = false;
window._setDirty = function () {
    if (window._dirty) {
        return;
    }
    window._dirty = true;
    var saveButton = document.getElementById("saveButton");
    saveButton.innerHTML = "<i class='rf rf-save'/> *保存";
    saveButton.classList.remove("disabled");
};

var HandsontableModule = require('handsontable');
var Handsontable = HandsontableModule.default || HandsontableModule;

(function () {
    if (!window.RuleForge) {
        window.RuleForge = {};
    }
    RuleForge.DecisionTable = function (id) {
        var table;
        window._VariableValueArray.push(this);
        window._ParameterValueArray.push(this);
        const container = document.getElementById(id);
        this.hasMod = true;
        this.container = container;
        const self = this;
        var saveButton = `<div class="btn-group btn-group-sm navbar-btn" style="margin-top:0;margin-bottom: 0" role="group" aria-label="...">
									<button id="saveButtonNewVersion" type="button" class="btn btn-default navbar-btn"><i class="rf rf-savenewversion"/> 生成版本</button>
									<button id="saveButton" type="button" class="btn btn-default navbar-btn" ><i class="rf rf-save"/> 保存</button>
								</div>`;
        var testButton = '<div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0;margin-bottom: 0" role="group" aria-label="...">' +
            '<button id="testButton" type="button" class="btn btn-success navbar-btn"><i class="glyphicon glyphicon-flash"/> 快速测试</button>' +
            '</div>';
        var addCriteriaButton = `<button id="addCriteriaButton" type="button" class="btn btn-default btn-sm"><i class="glyphicon glyphicon-plus"/> 添加条件行</button>`;
        var deleteCriteriaButton = `<button id="deleteCriteriaButton" type="button" class="btn btn-default btn-sm"><i class="glyphicon glyphicon-minus"/> 删除条件行</button>`;

        const buttons = `<nav class="navbar navbar-default" style="margin: 5px">
				<div>
					<div>
						<div class="btn-group navbar-btn" style="margin-top:0;margin-bottom: 0;margin-left: 5px" role="group" aria-label="...">
						 ${addCriteriaButton}
						 ${deleteCriteriaButton}
						</div>
						<div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0;margin-bottom: 0" role="group" aria-label="...">
							<button id="configVarButton" type="button" class="btn btn-default"><i class="rf rf-variable"/> 变量库</button>
							<button id="configConstantsButton" type="button" class="btn btn-default"><i class="rf rf-constant"/> 常量库</button>
							<button id="configActionButton" type="button" class="btn btn-default"><i class="rf rf-action"/> 动作库</button>
							<button id="configParameterButton" type="button" class="btn btn-default"><i class="rf rf-parameter"/> 参数库</button>
						</div>
						${saveButton}
						${testButton}
			        </div>
				</div>
			</nav>`;

        container.insertAdjacentHTML('beforeend', buttons);

        var remarkContainer = document.createElement("div");
        remarkContainer.style.cssText = "margin:5px 5px 5px 15px";
        container.appendChild(remarkContainer);
        this.remark = new Remark(remarkContainer);

        this.properties = [];

        var propContainer = document.createElement("div");
        propContainer.style.cssText = "margin:5px 5px 5px 15px;border: solid 1px #dddddd;border-radius:5px";
        container.appendChild(propContainer);
        this.propertyContainer = document.createElement("span");
        this.propertyContainer.style.padding = "10px";
        var addProp = document.createElement("button");
        addProp.type = "button";
        addProp.className = "rule-add-property btn btn-link";
        addProp.textContent = "添加属性";
        propContainer.appendChild(addProp);
        propContainer.appendChild(this.propertyContainer);
        var onClick = function (menuItem) {
            var prop = new ruleforge.RuleProperty(self, menuItem.name, menuItem.defaultValue, menuItem.editorType);
            self.addProperty(prop);
        };
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: "优先级",
                name: "salience",
                defaultValue: "10",
                editorType: 1,
                onClick: onClick
            }, {
                label: "生效日期",
                name: "effective-date",
                defaultValue: "",
                editorType: 2,
                onClick: onClick
            }, {
                label: "失效日期",
                name: "expires-date",
                defaultValue: "",
                editorType: 2,
                onClick: onClick
            }, {
                label: "是否启用",
                name: "enabled",
                defaultValue: true,
                editorType: 3,
                onClick: onClick
            }, {
                label: "允许调试信息输出",
                name: "debug",
                defaultValue: true,
                editorType: 3,
                onClick: onClick
            }]
        });
        addProp.addEventListener('click', function (e) {
            self.menu.show(e);
        });

        document.getElementById("addCriteriaButton").addEventListener('click', function () {
            var cellData = self.getCurrentCellData(),
                row = cellData.row + cellData.rowspan,
                col = cellData.col;
            self.mergeRange(col - 1);
            self.translateRow(row);
            self.createRowData(row);
            self.createCellDataRange(row, row, col, self.getLastColIndex());
            self.insertRow(row - 1);
            self.renderCells();
            self.setDirty();
            document.getElementById("deleteCriteriaButton").classList.remove("disabled");
            self.invoke("render");
        });

        document.getElementById("deleteCriteriaButton").addEventListener('click', function () {
            var highlight = self.getHighlight(),
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
            self.invoke("render");
            if (self.countRows() == 1) {
                this.classList.add("disabled");
            }
        });


        document.getElementById("configVarButton").addEventListener('click', function () {
            if (!self.configVarDialog) {
                self.configVarDialog = new ruleforge.ConfigVariableDialog(self);
            }
            self.configVarDialog.open();
        });

        document.getElementById("configConstantsButton").addEventListener('click', function () {
            if (!self.configConstantDialog) {
                self.configConstantDialog = new ruleforge.ConfigConstantDialog(self);
            }
            self.configConstantDialog.open();
        });

        document.getElementById("configActionButton").addEventListener('click', function () {
            if (!self.configActionDialog) {
                self.configActionDialog = new ruleforge.ConfigActionDialog(self);
            }
            self.configActionDialog.open();
        });

        document.getElementById("configParameterButton").addEventListener('click', function () {
            if (!self.configParameterDialog) {
                self.configParameterDialog = new ruleforge.ConfigParameterDialog(self);
            }
            self.configParameterDialog.open();
        });

        document.getElementById("saveButtonNewVersion").addEventListener('click', function () {
            save(true);
        });
        document.getElementById("saveButton").addEventListener('click', function () {
            save(false);
        });

        document.getElementById("testButton").addEventListener('click', function() {
            let file = getParameter('file')
            let decodedFile = decodeURIComponent(file)
            event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile});
        })

        function save(newVersion) {
            if (!newVersion && document.getElementById("saveButton").classList.contains("disabled")) {
                return false;
            }
            let file = getParameter('file'), xml = self.toXml();
            xml = encodeURIComponent(xml);
            let postData = {content: xml, file, newVersion};
            const url = window._server + '/common/saveFile';
            if (newVersion) {
                saveNewVersion(url, postData, function () {
                    self.resetState();
                    window.bootbox.alert('保存成功!');
                });
            } else {
                ajaxSave(url, postData, function () {
                    self.resetState();
                    window.bootbox.alert('保存成功!');
                })
            }
        }

        self.load();
        window.ht = self;
        var config = {
            "licenseKey": "non-commercial-and-evaluation",
            "type": "ruleforge",
            "manualRowResize": true,
            "manualColumnResize": true,
            "autoWrapCol": true,
            "startCols": self.getColDatas().length,
            "maxRows": 2147483647,
            "startRows": self.getRowDatas().length,
            "fillHandle": false,
            "multiSelect": false,
            "className": "htMiddle",
            "rowHeaders": true,
            "maxCols": 2147483647,
            "mergeCells": true,
            "autoWrapRow": true,
            "outsideClickDeselects": false,
            "colWidths": 120
        };
        table = document.createElement("div");
        table.style.marginLeft = "15px";
        container.appendChild(table);

        self._handsontable = new Handsontable(table, config);
        self._dom = table;
        self._handsontable.ht = self;
        config.colHeaders = function (col) {
            var column = self.getColData(col);
            if (!column) return '';
            var type = column.type,
                category = column.variableCategory == "parameter" ? "参数" : column.variableCategory,
                variable = column.variableLabel,
                width = column.width,
                title = category + "." + variable,
                icon, iconClass;
            self.setColWidth(col, width);
            if (!category || !variable) {
                title = "";
            }
            if (type == "Criteria") {
                iconClass = "glyphicon glyphicon-filter";
                icon = "glyphicon glyphicon-filter";
            } else if (type == "ExecuteMethod") {
                title = "执行方法";
                iconClass = "glyphicon glyphicon-flash";
            } else if (type == "Assignment") {
                iconClass = "glyphicon glyphicon-tasks";
            } else if (type == "ConsolePrint") {
                title = "控制台输出";
                iconClass = "glyphicon glyphicon-print";
            }
            return "<i class='" + iconClass + "' style='line-height:21px;'></i> " + title;
        };
        config.rowHeaders = function (row) {
            var rowData = self.getRowData(row);
            if (rowData && rowData.height) {
                self.setRowHeight(row, rowData.height);
            }
            return row + 1;
        };
        config.cells = function (row, col, prop) {
            return {
                readOnly: true
            };
        };
        self.updateSettings(config);
        self.renderCells();

        self.addHook("afterSelectionEnd", function () {
            var colData = self.getCurrentColData(),
                rowData = self.getCurrentRowData(),
                cellData = self.getCurrentCellData();

            if (colData.type == "Criteria") {
                document.getElementById("addCriteriaButton").classList.remove("disabled");
            } else {
                document.getElementById("addCriteriaButton").classList.add("disabled");
            }
        });

        self.addHook("beforeColumnResize", function (col, size) {
            var colData = self.getColData(col);
            colData.width = size;
            self.setDirty();
            self.invoke("render")
        });

        self.addHook("beforeRowResize", function (row, size) {
            var rowData = self.getRowData(row);
            rowData.height = size;
            self.setDirty();
        });

        self.addHook("afterRender", function () {
            self._dom.querySelectorAll(".htCore tr").forEach(function (tr) {
                var children = tr.children;
                for (var i = 0; i < children.length; i++) {
                    children[i].style.borderRightWidth = "";
                }
                var criteriaCol = Array.from(children)[self.countCriteriaCols()];
                if (criteriaCol) {
                    criteriaCol.style.borderRightWidth = "4px";
                }
            });
        });
        self.initMenu();
        self.resetState();
        table.querySelector(".handsontable").remove();
        self.invoke("render");
    };

    RuleForge.DecisionTable.prototype = {
        addProperty: function (property) {
            this.propertyContainer.appendChild(property.getContainer());
            this.properties.push(property);
            window._setDirty();
        },
        updateSettings: function (options) {
            this._handsontable.updateSettings(options);
        },
        getCellRenderer: function (cellProperties) {
            return this._handsontable.getCellRenderer(cellProperties);
        },

        getValue: function () {
            return this._handsontable.getValue();
        },

        alter: function (operate, index, amount, source) {
            this._handsontable.alter(operate, index, amount, source);
        },

        getCell: function (row, col) {
            return this._handsontable.getCell(row, col);
        },

        getCellMeta: function (row, col) {
            return this._handsontable.getCellMeta(row, col);
        },

        selectCell: function (row, col, row2, col2, scrollToSelection) {
            this._handsontable.selectCell(row, col, row2, col2, scrollToSelection);
        },

        deselectCell: function () {
            this._handsontable.deselectCell();
        },

        getSelected: function () {
            return this._handsontable.getSelected();
        },

        getSelectedRange: function () {
            return this._handsontable.getSelectedRange();
        },

        getMergeInfo: function (row, col) {
            var plugin = this._handsontable.getPlugin('mergeCells');
            if (plugin.mergedCellsCollection) {
                return plugin.mergedCellsCollection.get(row, col);
            }
            return null;
        },

        setMergeInfo: function (info) {
            var plugin = this._handsontable.getPlugin('mergeCells');
            plugin.merge(info.row, info.col, info.row + info.rowspan - 1, info.col + info.colspan - 1);
        },

        removeMergeInfo: function (row, col) {
            var plugin = this._handsontable.getPlugin('mergeCells');
            plugin.unmerge(row, col, row, col);
        },

        clear: function () {
            this._handsontable.clear();
        },

        countRows: function () {
            return this._handsontable.countRows();
        },

        countCols: function () {
            return this._handsontable.countCols();
        },

        colToProp: function (column) {
            return this._handsontable.colToProp();
        },

        getRowHeader: function (row) {
            return this._handsontable.getRowHeader(row);
        },

        getColHeader: function (col) {
            return this._handsontable.getColHeader(col);
        },

        getColWidth: function (col) {
            return this._handsontable.getColWidth();
        },

        getRowHeight: function (row) {
            return this._handsontable.getRowHeight();
        },

        propToCol: function (property) {
            return this._handsontable.propToCol(property);
        },

        addHook: function (name, func) {
            this._handsontable.addHook(name, func);
        },

        invoke: function (methodName, args) {
            if (methodName == "render") {
                if (args === true) {
                    this._handsontable.forceFullRender = false;
                    this._handsontable.view.render();
                } else {
                    this._handsontable.render();

                }
            } else {
                this._handsontable[methodName](args);
            }
        },

        getInstance: function () {
            return this._handsontable;
        },

        setDirty: function () {
            window._setDirty();
        },

        resetState: function () {
            window._dirty = false;
            document.getElementById("saveButton").innerHTML = "<i class='rf rf-save'></i> 保存";
            document.getElementById("saveButton").classList.add("disabled");
        },

        insertRow: function (index) {
            this.alter("insert_row");
            var tbody = this._dom.querySelector(".htCore > tbody");
            if (tbody) {
                var lastRow = tbody.children[tbody.children.length - 1];
                var targetRow = tbody.children[index];
                if (targetRow && lastRow) {
                    targetRow.after(lastRow);
                }
            }
        },

        removeRow: function (index, count) {
            var tbody = this._dom.querySelector(".htCore > tbody");
            if (tbody) {
                for (var r = index; r < index + count; r++) {
                    tbody.appendChild(tbody.children[index]);
                }
            }
            this.alter("remove_row", index, count);
        },

        insertCol: function (index) {
            this.alter("insert_col");
            var rows = this._dom.querySelectorAll(".htCore > tbody > tr");
            rows.forEach(function (tr) {
                var tds = tr.querySelectorAll("td");
                var lastTd = tds[tds.length - 1];
                var targetTd = tds[index];
                if (targetTd && lastTd) {
                    targetTd.after(lastTd);
                }
            });
        },

        removeCol: function (index) {
            var rows = this._dom.querySelectorAll(".htCore > tbody > tr");
            rows.forEach(function (tr) {
                var tds = tr.querySelectorAll("td");
                var targetTd = tds[index];
                var lastTd = tds[tds.length - 1];
                if (targetTd && lastTd) {
                    lastTd.after(targetTd);
                }
            });
            this.alter("remove_col");
        },

        renderSelection: function () {
            var range = this.getSelectedRange();
            if (range) {
                var from = range.getTopLeftCorner();
                var to = range.getBottomRightCorner();
                for (var row = from.row; row <= to.row; row++) {
                    for (var col = from.col; col <= to.col; col++) {
                        this.renderCell(row, col);
                    }
                }
            }

        },

        getTableData: function () {
            return this.decisionTable;
        },

        getCurrentCellData: function () {
            var highlight = this.getHighlight(),
                row = highlight.row,
                col = highlight.col;
            return this.getCellData(row, col);
        },

        getCurrentRowData: function () {
            var highlight = this.getHighlight(),
                row = highlight.row;
            return this.getRowData(row);
        },

        getCurrentColData: function () {
            var highlight = this.getHighlight(),
                col = highlight.col;
            return this.getColData(col);
        },

        getCellDatas: function () {
            var cellMap = this.decisionTable.cellMap;
            if (!this.decisionTable.cells && cellMap) {
                this.decisionTable.cells = [];
                for (var p in cellMap) {
                    this.decisionTable.cells.push(cellMap[p]);
                }
            }
            return this.decisionTable.cells;
        },


        getRowDatas: function () {
            return this.decisionTable.rows || [];
        },

        getColDatas: function () {
            return this.decisionTable.columns || [];
        },

        getColData: function (col) {
            var colDatas = this.getColDatas();
            for (var i = 0; i < colDatas.length; i++) {
                if (colDatas[i].num == col) {
                    return colDatas[i];
                }
            }
        },

        getRowData: function (row) {
            var rowDatas = this.getRowDatas();
            for (var i = 0; i < rowDatas.length; i++) {
                if (rowDatas[i].num == row) {
                    return rowDatas[i];
                }
            }
        },

        getCellData: function (row, col) {
            var cells = this.getCellDatas();
            for (var i = 0; i < cells.length; i++) {
                if (cells[i].row == row && cells[i].col == col) {
                    return cells[i];
                }
            }
            return null;
        },

        getCellDataByCol: function (col) {
            var cells = this.getCellDatas(), result = [];
            for (var i = 0; i < cells.length; i++) {
                if (cells[i].col == col) {
                    result.push(cells[i])
                }
            }
            return result;
        },

        getCellDataByRow: function (row) {
            var cells = this.getCellDatas(), result = [];
            for (var i = 0; i < cells.length; i++) {
                if (cells[i].row == row) {
                    result.push(cells[i])
                }
            }
            return result;
        },

        createCellDataRange: function (fromRow, toRow, fromCol, toCol) {
            for (var r = fromRow; r <= toRow; r++) {
                for (var c = fromCol; c <= toCol; c++) {
                    this.createCellData(r, c);
                }
            }
        },

        createCellData: function (row, col) {
            var cellData = {
                row: row,
                col: col,
                rowspan: 1
            };
            this.getCellDatas().push(cellData);
            return cellData;
        },

        createCellDataByCopyNextCol: function (col) {
            var self = this,
                cellDatas = self.getCellDataByCol(col + 1);
            cellDatas.forEach(function (cellData) {
                var cell = self.createCellData(cellData.row, col);
                cell.rowspan = cellData.rowspan;
            });
        },

        removeCellDataRange: function (fromRow, toRow, fromCol, toCol) {
            for (var r = fromRow; r <= toRow; r++) {
                for (var c = fromCol; c <= toCol; c++) {
                    this.removeCellData(r, c);
                }
            }
        },

        removeCellData: function (row, col) {
            var cellDatas = this.getCellDatas();
            var cellData = this.getCellData(row, col);
            if (cellData) {
                var index = cellDatas.indexOf(cellData);
                cellDatas.splice(index, 1);
            }
        },

        createRowData: function (row) {
            var rowData = {
                num: row,
                height: 40
            };
            this.getRowDatas().push(rowData);
            return rowData;
        },

        removeRowData: function (row) {
            var rowDatas = this.getRowDatas();
            var rowData = this.getRowData(row);
            if (rowData) {
                var index = rowDatas.indexOf(rowData);
                rowDatas.splice(index, 1);
            }
        },

        removeRowDataRange: function (start, count) {
            count = count || 1;
            for (var r = start; r < start + count; r++) {
                this.removeRowData(r);
            }
        },

        createColData: function (col) {
            var colData = {
                num: col
            };
            this.getColDatas().push(colData);
            return colData;
        },

        removeColData: function (col) {
            var colDatas = this.getColDatas();
            var colData = this.getColData(col);
            if (colData) {
                var index = colDatas.indexOf(colData);
                colDatas.splice(index, 1);
            }
        },

        translateRow: function (start, count) {
            count = count || 1;
            if (count > 0) {
                for (var r = this.getLastRowIndex(); r >= start; r--) {
                    this.translateRowHeader(r, count);
                }
                for (var r = this.getLastRowIndex(); r >= start; r--) {
                    for (var c = 0; c < this.countCols(); c++) {
                        this.translateCell(r, c, count, 0);
                    }
                }
            } else if (count < 0) {
                for (var r = start; r < this.countRows(); r++) {
                    this.translateRowHeader(r, count);
                }
                for (var r = start; r < this.countRows(); r++) {
                    for (var c = 0; c < this.countCols(); c++) {
                        this.translateCell(r, c, count, 0);
                    }
                }
            }

        },

        translateCol: function (start, count) {
            count = count || 1;
            if (count > 0) {
                for (var c = this.getLastColIndex(); c >= start; c--) {
                    this.translateColHeader(c, count);
                }
                for (var r = 0; r < this.countRows(); r++) {
                    for (var c = this.getLastColIndex(); c >= start; c--) {
                        this.translateCell(r, c, 0, count);
                    }
                }
            } else if (count < 0) {
                for (var c = start; c < this.countCols(); c++) {
                    this.translateColHeader(c, count);
                }
                for (var r = 0; r < this.countRows(); r++) {
                    for (var c = start; c < this.countCols(); c++) {
                        this.translateCell(r, c, 0, count);
                    }
                }
            }
        },

        translateCell: function (row, col, rowCount, colCount) {
            var cellData = this.getCellData(row, col);
            if (cellData) {
                cellData.row = cellData.row + rowCount;
                cellData.col = cellData.col + colCount;
            }
        },

        translateRowHeader: function (row, count) {
            var rowData = this.getRowData(row);
            if (rowData) {
                rowData.num = rowData.num + count;
            }
        },

        translateColHeader: function (col, count) {
            var colData = this.getColData(col);
            if (colData) {
                colData.num = colData.num + count;
            }
        },

        translateColHeaderRange: function (start, count) {
            count = count || 1;
            for (var c = start; c < this.countCols(); c++) {
                this.translateColHeader(c, count);
            }
        },

        getCellContent: function (row, col) {
            var cellData;
            if (typeof row == "object") {
                cellData = row;
            } else {
                cellData = this.getCellData(row, col);
            }
            if (!cellData.cellContent) {
                var colData = this.getColData(cellData.col),
                    type = colData.type,
                    container = cellData.container;
                if (type == "Criteria") {
                    cellData.cellContent = new ruleforge.CellCondition("<div/>");
                    if (cellData.joint) {
                        cellData.cellContent.initData(cellData.joint);
                    }
                } else if (type == "Assignment") {
                    cellData.cellContent = new ruleforge.CellContent(container);
                    if (cellData.value) {
                        cellData.cellContent.initData(cellData.value);
                    }
                } else if (type == "ConsolePrint") {
                    cellData.cellContent = new ruleforge.CellContent(container);
                    if (cellData.value) {
                        cellData.cellContent.initData(cellData.value);
                    }
                } else if (type == "ExecuteMethod") {
                    cellData.cellContent = new ruleforge.CellExecuteMethod(container);
                    if (cellData.action) {
                        cellData.cellContent.setAction(cellData.action);
                    }
                }
                if (cellData.cellContent.initData) {
                }

            }
            return cellData.cellContent;
        },

        countCriteriaCols: function () {
            var colDatas = this.getColDatas();
            var count = 0;
            for (var i = 0; i < colDatas.length; i++) {
                if (colDatas[i].type == "Criteria") {
                    ++count;
                }
            }
            return count;
        },

        countActionCols: function () {
            var colDatas = this.getColDatas();
            var count = 0;
            for (var i = 0; i < colDatas.length; i++) {
                if (colDatas[i].type != "Criteria") {
                    ++count;
                }
            }
            return count;
        },


        getLastRowIndex: function () {
            return this.countRows() - 1;
        },

        getLastColIndex: function () {
            return this.countCols() - 1;
        },

        getHighlight: function () {
            var range = this._handsontable.getSelectedRange();
            if (range) {
                return range.highlight;
            }
            return null;
        },

        setRowHeight: function (row, height) {
            var instance = this.getInstance();
            if (instance && instance.manualRowHeights) {
                instance.manualRowHeights[row] = height;
            }
        },

        setColWidth: function (col, width) {
            var instance = this.getInstance();
            if (instance && instance.manualColumnWidths) {
                instance.manualColumnWidths[col] = width;
            }
        },

        mergeRange: function (end, rowspan) {
            var cellData = this.getCurrentCellData(),
                row = cellData.row + cellData.rowspan - 1,
                col = cellData.col;
            rowspan = rowspan || 1;
            for (var c = 0; c <= end; c++) {
                this.merge(row, c, rowspan);
            }
        },

        merge: function (row, col, rowspan) {
            var cellData = this.getCellData(row, col);
            while (!cellData) {
                row--;
                cellData = this.getCellData(row, col);
            }
            if (cellData.rowspan + rowspan == 0) {
                this.removeCellData(row, col);
            } else {
                cellData.rowspan = cellData.rowspan + rowspan;
            }
        },

        unmerge: function (row, col) {
            var cellData = this.getCellData(row, col);
            cellData.rowspan = 1;
        },

        renderRowRange: function (start) {
            for (r = start; r < this.countRows(); r++) {
                this.renderCells(r);
            }
        },

        renderColRange: function (start) {
            for (let c = start; c < this.countCols(); c++) {
                this.renderCells(null, c);
            }
        },

        renderCells: function (row, col) {
            if (row && col) {
                this.renderCell(row, col);
            } else if (row) {
                for (var c = 0; c < this.countCols(); c++) {
                    this.renderCell(row, c);
                }
            } else if (col) {
                for (var r = 0; r < this.countRows(); r++) {
                    this.renderCell(r, col);
                }
            } else {
                for (var r = 0; r < this.countRows(); r++) {
                    for (var c = 0; c < this.countCols(); c++) {
                        this.renderCell(r, c);
                    }
                }
            }
        },

        renderCell: function (row, col) {
            var prop = this.colToProp(col),
                cellProperties = this.getCellMeta(row, col),
                renderer = this.getCellRenderer(cellProperties),
                TD = this.getCell(row, col);
            var value = this.getValue();
            renderer(this._handsontable, TD, row, col, prop, value, cellProperties);
            this._handsontable.runHooks('afterRenderer', TD, row, col, prop, value, cellProperties);
        },

        toXml: function () {
            var decisionTable = this.getTableData(),
                cells = decisionTable.cells || [],
                rows = decisionTable.rows || [],
                cols = decisionTable.columns || [],
                libraries = [],
                self = this,
                xml;
            constantLibraries.forEach(function (path) {
                libraries.push({
                    type: "Constant",
                    path: path
                });
            });

            actionLibraries.forEach(function (path) {
                libraries.push({
                    type: "Action",
                    path: path
                });
            });

            variableLibraries.forEach(function (path) {
                libraries.push({
                    type: "Variable",
                    path: path
                });
            });
            parameterLibraries.forEach(function (path) {
                libraries.push({
                    type: "Parameter",
                    path: path
                });
            });
            xml = "<decision-table";
            for (var i = 0; i < this.properties.length; i++) {
                var prop = this.properties[i];
                xml += " " + prop.toXml();
            }
            xml += ">";
            xml += this.remark.toXml();
            libraries.forEach(function (library) {
                var type = library.type,
                    path = library.path;
                if (type == "Variable") {
                    xml += "<import-variable-library path=\"" + path + "\"/>";
                } else if (type == "Constant") {
                    xml += "<import-constant-library path=\"" + path + "\"/>";
                } else if (type == "Action") {
                    xml += "<import-action-library path=\"" + path + "\"/>";
                } else if (type == "Parameter") {
                    xml += "<import-parameter-library path=\"" + path + "\"/>";
                }
            });

            cells.forEach(function (cell) {
                xml += "<cell row=\"" + cell.row + "\" col=\"" + cell.col + "\" rowspan=\"" + cell.rowspan + "\">";
                xml += self.getCellContent(cell).toXml();
                xml += "</cell>"

            });

            rows.forEach(function (row) {
                xml += "<row num=\"" + row.num + "\" height=\"" + row.height + "\"/>"
            });

            cols.forEach(function (col) {
                var variableName = col.variableName;
                if (variableName) {
                    xml += "<col num=\"" + col.num + "\" width=\"" + col.width + "\" type=\"" + col.type + "\" var-category=\"" + (col.variableCategory == "parameter" ? "参数" : col.variableCategory) + "\" var-label=\"" + col.variableLabel + "\" var=\"" + col.variableName + "\" datatype=\"" + col.datatype + "\"/>"
                } else {
                    xml += "<col num=\"" + col.num + "\" width=\"" + col.width + "\" type=\"" + col.type + "\"/>"
                }
            });

            xml += "</decision-table>";
            return xml;

        },
        load: function (callback) {
            var files, version, self, url;
            self = this;
            files = getParameter("file");
            version = getParameter("version");
            url = window._server + '/common/loadXml';
            var xhr = new XMLHttpRequest();
            xhr.open("POST", url, false);
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            xhr.send(new URLSearchParams({files}).toString());
            if (xhr.status === 200) {
                var data = JSON.parse(xhr.responseText);
                var decisionTable = data[0];
                self.remark.setData(decisionTable["remark"]);
                var salience = decisionTable["salience"];
                if (salience) {
                    self.addProperty(new ruleforge.RuleProperty(self, "salience", salience, 1));
                }
                var loop = decisionTable["loop"];
                if (loop != null) {
                    self.addProperty(new ruleforge.RuleProperty(self, "loop", loop, 3));
                }
                var effectiveDate = decisionTable["effectiveDate"];
                if (effectiveDate) {
                    self.addProperty(new ruleforge.RuleProperty(self, "effective-date", effectiveDate, 2));
                }
                var expiresDate = decisionTable["expiresDate"];
                if (expiresDate) {
                    self.addProperty(new ruleforge.RuleProperty(self, "expires-date", expiresDate, 2));
                }
                var enabled = decisionTable["enabled"];
                if (enabled != null) {
                    self.addProperty(new ruleforge.RuleProperty(self, "enabled", enabled, 3));
                }
                var debug = decisionTable["debug"];
                if (debug != null) {
                    self.addProperty(new ruleforge.RuleProperty(self, "debug", debug, 3));
                }

                var libraries = decisionTable.libraries || [];
                libraries.forEach(function (library) {
                    var type, path;
                    type = library.type;
                    path = library.path;
                    switch (type) {
                        case "Constant":
                            constantLibraries.push(path);
                            break;
                        case "Action":
                            actionLibraries.push(path);
                            break;
                        case "Variable":
                            variableLibraries.push(path);
                            break;
                        case "Parameter":
                            parameterLibraries.push(path);
                            break;
                    }
                });
                self.decisionTable = decisionTable;
                refreshActionLibraries();
                refreshConstantLibraries();
                refreshVariableLibraries();
                refreshParameterLibraries();
                refreshFunctionLibraries();
                if (callback) {
                    callback();
                }
            }
        },
        initMenu: function () {
            var self = this, variableLibrary = [];
            var oldVariableLibrary = window._ruleforgeEditorVariableLibraries || [];
            oldVariableLibrary.forEach(function (lib) {
                if (lib.type != "parameter") {
                    variableLibrary.push(lib);
                }
            });
            var parameter = window._ruleforgeEditorParameterLibraries || [];
            if (parameter.length > 0) {
                parameter.forEach(function (p) {
                    variableLibrary.push([{
                        name: "参数",
                        type: "parameter",
                        variables: parameter.length ? p : []
                    }]);
                });
            }
            var onInsert = function (type, width, add) {
                var highlight = self.getHighlight(),
                    row = highlight.row,
                    col = highlight.col + (add || 0);
                self.translateCol(col);
                var column = self.createColData(col);
                column.type = type;
                column.width = width || 200;
                if (type == "Criteria") {
                    self.createCellDataByCopyNextCol(col);
                } else {
                    self.createCellDataRange(0, self.getLastRowIndex(), col, col);
                }
                self.insertCol(col - 1);
                self.renderCells(null, col);
                self.setDirty();
                self.invoke("render");
            };

            var onShow = function () {
                var ationCount = self.countActionCols();
                var criteriaCount = self.countCriteriaCols();
                var menuItem = this.getMenuItem("delete");
                if (!menuItem) return;
                if (menuItem.label == "删除动作列") {
                    if (ationCount == 1) {
                        menuItem.hide();
                    } else {
                        menuItem.show();
                    }
                } else {
                    if (criteriaCount == 1) {
                        menuItem.hide();
                    } else {
                        menuItem.show();
                    }

                }
            };
            const menuItems = [{
                label: "插入条件列",
                icon: "glyphicon glyphicon-filter",
                subMenu: {
                    menuItems: [{
                        label: "前",
                        icon: "glyphicon glyphicon-chevron-left",
                        onClick: function () {
                            onInsert("Criteria", 120);
                        }
                    }, {
                        label: "后",
                        icon: "glyphicon glyphicon-chevron-right",
                        onClick: function () {
                            onInsert("Criteria", 120, 1);
                        }
                    }]
                }
            }, {
                label: "删除条件列",
                icon: "glyphicon glyphicon-minus-sign",
                name: "delete",
                onClick: function () {
                    var highlight = self.getHighlight(),
                        col = highlight.col;
                    self.removeCellDataRange(0, self.getLastRowIndex(), col, col);
                    self.removeColData(col);
                    self.translateCol(col, -1);
                    self.removeCol(col);
                    self.setDirty();
                    self.invoke("render");
                }
            }, {
                label: "插入执行方法动作列",
                icon: "glyphicon glyphicon-flash",
                subMenu: {
                    menuItems: [{
                        label: "前",
                        icon: "glyphicon glyphicon-chevron-left",
                        onClick: function () {
                            onInsert("ExecuteMethod");
                        }
                    }, {
                        label: "后",
                        icon: "glyphicon glyphicon-chevron-right",
                        onClick: function () {
                            onInsert("ExecuteMethod", 200, 1);
                        }
                    }]
                }
            }, {
                label: "插入变量赋值动作列",
                icon: "glyphicon glyphicon-tasks",
                subMenu: {
                    menuItems: [{
                        label: "前",
                        icon: "glyphicon glyphicon-chevron-left",
                        onClick: function () {
                            onInsert("Assignment");
                        }
                    }, {
                        label: "后",
                        icon: "glyphicon glyphicon-chevron-right",
                        onClick: function () {
                            onInsert("Assignment", 200, 1);
                        }

                    }]
                }
            }, {
                label: "插入控制台输出动作列",
                icon: "glyphicon glyphicon-print",
                subMenu: {
                    menuItems: [{
                        label: "前",
                        icon: "glyphicon glyphicon-chevron-left",
                        onClick: function () {
                            onInsert("ConsolePrint");
                        }
                    }, {
                        label: "后",
                        icon: "glyphicon glyphicon-chevron-right",
                        onClick: function () {
                            onInsert("ConsolePrint", 200, 1);
                        }
                    }]
                }
            }, {
                label: "删除动作列",
                name: "delete",
                icon: "glyphicon glyphicon-minus-sign",
                onClick: function () {
                    var highlight = self.getHighlight(),
                        col = highlight.col;
                    self.removeColData(col);
                    self.removeCellDataRange(0, self.getLastRowIndex(), col, col);
                    self.translateCol(col, -1);
                    self.removeCol(col);
                    self.setDirty();
                    self.invoke("render");
                }
            }, {
                label: "配置条件",
                icon: "glyphicon glyphicon-cog",
                onClick: function () {
                    const colData = self.getCurrentColData();
                    if (!colData.variableCategory) {
                        window.bootbox.alert('当前条件列未定义对应的参数或变量，不能进行[配置条件]操作.');
                        return;
                    }
                    const dialogContent = document.createElement("div");
                    let cellData = self.getCurrentCellData(), content = self.getCellContent(cellData);
                    content.renderTo(dialogContent);
                    const category = colData.variableCategory === "parameter" ? "参数" : colData.variableCategory;
                    const caption = category + "." + colData.variableLabel;
                    window.bootbox.dialog(caption, dialogContent, [], [{
                        name: 'hide.bs.modal',
                        callback: function () {
                            self.renderSelection();
                        }
                    }], true);
                }
            }, {
                label: "清空",
                icon: "glyphicon glyphicon-trash",
                name: "clean",
                onClick: function () {
                    var cellData = self.getCurrentCellData();
                    MsgBox.confirm("确定要清空当前单元格吗？", function () {
                        self.getCellContent(cellData).clean();
                        self.renderSelection();
                    });
                }
            }/*, {
					label : "复制",
					icon:"glyphicon glyphicon-copyright-mark",
					name:"copy",
					onClick:function(){
						let cellData=self.getCurrentCellData();
						window._tableCellContent=self.getCellContent(cellData);
					}
				}, {
					label : "粘贴",
					icon:"glyphicon glyphicon-registration-mark",
					name:"paste",
					onClick:function(){
						if(!window._tableCellContent){
							window.bootbox.alert("请先复制目标单元格内容！");
							return;
						}

					}
				}*/];

            var onClick = function (menuItem) {
                var highlight = self.getHighlight(),
                    row = highlight.row,
                    col = highlight.col,
                    parent = menuItem.parent.parent,
                    column = self.getColData(col);
                column.variableCategory = parent.label == "参数" ? "parameter" : parent.label;
                column.variableLabel = menuItem.label;
                column.variableName = menuItem.name;
                column.datatype = menuItem.datatype;
                self.setDirty();
                self.invoke("render");
            };
            var variabeMenuItem = [];
            variableLibrary.forEach(function (categories) {
                categories.forEach(function (category) {
                    var menuItem = {
                        label: category.name == "parameter" ? "参数" : category.name,
                        icon: category.type == "parameter" ? "glyphicon glyphicon-th-list" : "glyphicon glyphicon-tasks"
                    };
                    var variables = category.variables;
                    (variables || []).forEach(function (variable) {
                        if (!menuItem.subMenu) {
                            menuItem.subMenu = {menuItems: []};
                        }
                        var subMenuItem = {
                            icon: "glyphicon glyphicon-tasks",
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

            var criteriaConfig = {
                onShow: onShow,
                menuItems: []
            };

            criteriaConfig.menuItems.push(menuItems[0]);
            criteriaConfig.menuItems.push(menuItems[1]);
            criteriaConfig.menuItems = criteriaConfig.menuItems.concat(variabeMenuItem);

            var actionConfig = {
                onShow: onShow,
                menuItems: []
            };

            var assignmentConfig = {
                onShow: onShow
            };

            actionConfig.menuItems.push(menuItems[2]);
            actionConfig.menuItems.push(menuItems[3]);
            actionConfig.menuItems.push(menuItems[4]);

            actionConfig.menuItems.push(menuItems[5]);

            assignmentConfig.menuItems = actionConfig.menuItems;
            assignmentConfig.menuItems = assignmentConfig.menuItems.concat(variabeMenuItem);

            var criteriaCellConfig = {
                menuItems: [menuItems[6], menuItems[7]]
            };
            var actionCellConfig = {
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

            this.container.addEventListener('contextmenu', function (e) {
                var th = e.target.closest('th');
                var tr = e.target.closest('tr');
                var parent = th || tr;
                if (parent) {
                    var isCriteriaColHeader = parent.querySelectorAll('span.colHeader .glyphicon-filter').length > 0,
                        isAssignmentColHeader = parent.querySelectorAll('span.colHeader .glyphicon-tasks').length > 0,
                        isColHeader = parent.querySelectorAll('span.colHeader').length > 0,
                        isRowHeader = parent.querySelectorAll('span.rowHeader').length > 0 && parent.textContent.trim(),
                        isCell = parent.querySelectorAll('td').length > 0,
                        colData = self.getCurrentColData(),
                        highlight = self.getHighlight(),
                        col = highlight.col,
                        count = self.countCriteriaCols();
                    if (isCell && self.hasMod) {
                        if (count > col && self.criteriaCellMenu.menuItems.length > 0) {
                            self.criteriaCellMenu.show(e);
                        } else if (self.actionCellMenu.menuItems.length > 0) {
                            self.actionCellMenu.show(e);
                        }
                    } else if (isCriteriaColHeader && self.criteriaMenu.menuItems.length > 0) {
                        self.criteriaMenu.show(e);
                    } else if (isAssignmentColHeader && self.assignmentMenu.menuItems.length > 0) {
                        self.assignmentMenu.show(e);
                    } else if (isColHeader && self.actionMenu.menuItems.length > 0) {
                        self.actionMenu.show(e);
                    }
                }
            });
        }
    };
})();
