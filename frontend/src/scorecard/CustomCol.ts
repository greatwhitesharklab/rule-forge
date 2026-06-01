import Col from './Col';

export default class CustomCol extends Col {
    customCells: import('./CustomCell').default[] = [];

    constructor(table: import('./ScoreCardTable').default, name: string, width?: number) {
        super(table);
        this.customCells = [];
        this.name = name;
        this.width = width || 160;
        this.type = 'custom';
        this.init();
    }

    init(): void {
        const _this = this;
        const td = document.createElement('td');
        td.style.cssText = 'width:' + this.width + 'px;background: #dbfd60;border:1px solid #607D8B;padding-right: 0';
        td.textContent = this.name;
        this.td = td;
        const del = document.createElement('span');
        del.style.cssText = 'color: #999;margin-left: 4px;cursor: pointer';
        del.innerHTML = "<i class='glyphicon glyphicon-remove'/>";
        this.td.appendChild(del);
        del.addEventListener('click', function () {
            window.bootbox.confirm("真的要删除当前列？", function (result) {
                if (!result) return;
                _this.remove();
            });
        });
        this.td.appendChild(this.buildColResizeTrigger());
        for (const row of this.scoreCardTable.attributeRows) {
            row.addCustomCol(this);
        }
        this.scoreCardTable.headerRow.appendChild(this.td);
        this.bindColResize();
    }

    remove(): void {
        for (const row of this.scoreCardTable.attributeRows) {
            row.removeCustomCol(this);
        }
        const pos = this.scoreCardTable.customCols.indexOf(this);
        this.scoreCardTable.customCols.splice(pos, 1);
        for (const cell of this.customCells) {
            cell.td.remove();
        }
        this.td.remove();
        window._setDirty?.();
    }

    toXml(): string {
        let xml = "<custom-col col-number=\"" + this.getColNumber() + "\" name=\"" + this.name + "\" width=\"" + this.width + "\"/>";
        return xml;
    }
}
