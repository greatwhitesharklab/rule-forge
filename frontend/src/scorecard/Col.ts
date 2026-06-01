import type ScoreCardTable from './ScoreCardTable';

export default class Col {
    scoreCardTable: ScoreCardTable;
    type: string;
    width: number;
    name: string;
    td: HTMLTableCellElement;
    resizeTrigger: HTMLSpanElement;

    constructor(table: ScoreCardTable) {
        this.scoreCardTable = table;
    }

    buildColResizeTrigger(): HTMLSpanElement {
        const resizeTrigger = document.createElement('span');
        resizeTrigger.className = 'col-resize-trigger';
        resizeTrigger.innerHTML = '&nbsp;';
        this.resizeTrigger = resizeTrigger;
        return resizeTrigger;
    }

    getColNumber(): number {
        switch (this.type) {
            case "attribute":
                return 1;
            case "condition":
                return 2;
            case "score":
                return 3;
        }
        const pos = this.scoreCardTable.customCols.indexOf(this as any);
        return pos + 4;
    }

    bindColResize(): void {
        let resizeStart = false;
        let resizeTargetCol: HTMLElement | null = null;
        let resizeStartX = 0;
        let resizeStartWidth = 0;
        const _this = this;
        this.resizeTrigger.addEventListener('mousedown', function (e) {
            resizeTargetCol = (this as HTMLElement).parentElement;
            resizeStart = true;
            resizeStartX = e.pageX;
            resizeStartWidth = resizeTargetCol!.clientWidth;
            e.preventDefault();
        });
        document.addEventListener('mousemove', function (e) {
            if (resizeStart) {
                const newWidth = resizeStartWidth + (e.pageX - resizeStartX);
                _this.width = newWidth;
                resizeTargetCol!.style.width = newWidth + 'px';
                e.preventDefault();
            }
        });
        document.addEventListener('mouseup', function () {
            resizeStart = false;
            window._setDirty?.();
        });
    }
}
