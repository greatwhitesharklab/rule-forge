import Col from './Col';

export default class ConditionCol extends Col {
    constructor(table: import('./ScoreCardTable').default, name: string, width: number) {
        super(table);
        this.name = name;
        this.width = width;
        this.type = 'condition';
        this.init();
    }

    init(): void {
        const td = document.createElement('td');
        td.style.cssText = 'width: ' + this.width + 'px;padding-right: 0;background: #607D8B;color: #ffffff;border:1px solid #607D8B';
        td.textContent = this.name;
        this.td = td;
        this.td.appendChild(this.buildColResizeTrigger());
        this.scoreCardTable.headerRow.appendChild(this.td);
        this.bindColResize();
    }

    toXml(): string {
        let xml = " condition-col-width=\"" + this.width + "\" condition-col-name=\"" + this.name + "\"";
        return xml;
    }
}
