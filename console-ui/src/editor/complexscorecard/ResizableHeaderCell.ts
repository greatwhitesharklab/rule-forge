/**
 * ResizableHeaderCell - Base header cell with column resize behavior.
 *
 * Extends BaseCell with a drag handle for resizing columns and
 * logic for updating column width and rebuilding the highlight div.
 */

import BaseCell from './BaseCell';

export default class ResizableHeaderCell extends BaseCell {
    resizeTrigger!: HTMLSpanElement;
    conditionCol?: any;
    actionCol?: any;

    constructor(rowContext: import('./RowContext').default) {
        super(rowContext);
        this.td.style.paddingRight = '0';
        this.td.appendChild(this.buildColResizeTrigger());
        this.bindColResize(rowContext);
    }

    buildColResizeTrigger(): HTMLSpanElement {
        const resizeTrigger = document.createElement('span');
        resizeTrigger.style.cssText = 'cursor: col-resize;width: 3px;height: 20px;float: right;border: solid 2px transparent;';
        resizeTrigger.innerHTML = '&nbsp;';
        this.resizeTrigger = resizeTrigger;
        return resizeTrigger;
    }

    bindColResize(rowContext: import('./RowContext').default): void {
        let isDragging = false;
        let parentTd: HTMLElement | null = null;
        let startX = 0;
        let startWidth = 0;
        const self = this;

        this.resizeTrigger.addEventListener('mouseover', function () {
            this.style.border = 'solid 2px #999';
        });
        this.resizeTrigger.addEventListener('mouseout', function () {
            this.style.border = 'solid 2px transparent';
        });
        this.resizeTrigger.addEventListener('mousedown', function (e) {
            parentTd = (this as HTMLElement).parentElement;
            isDragging = true;
            startX = e.pageX;
            startWidth = parentTd!.clientWidth;
            e.preventDefault();
        });
        document.addEventListener('mousemove', function (e) {
            if (isDragging) {
                const newWidth = startWidth + (e.pageX - startX);
                if (self.actionCol) {
                    self.actionCol.width = newWidth;
                } else {
                    self.conditionCol.width = newWidth;
                }
                parentTd!.style.width = newWidth + 'px';
                self._rebuildHighLightDiv(rowContext);
                e.preventDefault();
            }
        });
        document.addEventListener('mouseup', function () {
            isDragging = false;
            window._setDirty?.();
        });
    }

    _rebuildHighLightDiv(rowContext: import('./RowContext').default): void {
        const highlightDiv = rowContext.complexTable.getHighlightDiv();
        const currentTD = (highlightDiv as any).currentTD;
        if (currentTD) {
            const width = currentTD.clientWidth;
            const height = currentTD.clientHeight;
            highlightDiv.style.width = width + 'px';
            highlightDiv.style.height = height + 'px';
        }
    }
}
