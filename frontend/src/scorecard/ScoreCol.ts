import Col from './Col';

export default class ScoreCol extends Col {
    constructor(table: import('./ScoreCardTable').default, name: string, width: number) {
        super(table);
        this.name = name;
        this.width = width;
        this.type = 'score';
        this.init();
    }

    init(): void {
        const td = document.createElement('td');
        td.style.cssText = 'width: ' + this.width + 'px;padding-right: 0;background: #fded02;border:1px solid #607D8B';
        this.td = td;
        this.td.appendChild(this.buildColResizeTrigger());
        const container = document.createElement('span');
        container.style.cursor = 'pointer';
        container.textContent = this.name;
        this.td.appendChild(container);
        this.scoreCardTable.headerRow.appendChild(this.td);
        this.bindColResize();
    }

    toXml(): string {
        let xml = " score-col-width=\"" + this.width + "\" score-col-name=\"" + this.name + "\"";
        return xml;
    }
}
