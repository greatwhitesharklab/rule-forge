/* bootbox is a global */
import Cell from './Cell.js';

export default class ConditionCell extends Cell {
    constructor(row, col, cellData) {
        super(row, col, cellData);
        this.type = "condition";
    }

    initCell(cellData) {
        const _this = this;
        const container = document.createElement('div');
        this.td.appendChild(container);
        const conditionContainer = document.createElement('span');
        conditionContainer.innerHTML = '<span style="color:#999">无</span>';
        container.appendChild(conditionContainer);
        const configCondition = document.createElement('span');
        configCondition.className = 'attribute-operation';
        configCondition.style.cssText = 'color:#3c763d;margin-left: 5px';
        configCondition.innerHTML = '<i class="glyphicon glyphicon-cog" style="cursor: pointer" title="配置条件"/>';
        container.appendChild(configCondition);
        if (cellData) {
            this.cellCondition = new ruleforge.CellCondition("<div/>");
            this.cellCondition.initData(cellData.joint);
            conditionContainer.innerHTML = '';
            conditionContainer.appendChild(this.cellCondition.getDisplayContainer());
        }
        configCondition.addEventListener('click', function () {
            const dialogContent = document.createElement("div");
            if (!_this.cellCondition) {
                _this.cellCondition = new ruleforge.CellCondition("<div/>");
            }
            _this.cellCondition.renderTo(dialogContent);
            const caption = "配置条件";
            window.bootbox.dialog(caption, dialogContent, [], [{
                name: 'hide.bs.modal',
                callback: function () {
                    conditionContainer.innerHTML = '';
                    conditionContainer.appendChild(_this.cellCondition.getDisplayContainer());
                }
            }], true);
        });

        if (this.row.rowType && this.row.rowType === 'condition') {
            const _this = this;
            const del = document.createElement('span');
            del.className = 'attribute-operation';
            del.style.cssText = 'color: #03A9F4;margin-left: 3px';
            del.innerHTML = '<i class="glyphicon glyphicon-trash" style="cursor: pointer" title="删除当前行"/>';
            container.appendChild(del);
            del.addEventListener('click', function () {
                bootbox.confirm("真的要删除？", function (result) {
                    if (!result) return;
                    _this.row.remove();
                });
            });
        }
    }
}
