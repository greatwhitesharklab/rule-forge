/**
 * HighlightCell - Base cell with highlight/selection behavior.
 *
 * Extends BaseCell with click and right-click handlers that position
 * a highlight div over the cell to indicate the active selection.
 */

import BaseCell from './BaseCell';

export default class HighlightCell extends BaseCell {
    constructor(rowContext: import('./RowContext').default) {
        super(rowContext);

        const complexTable = rowContext.complexTable;
        const self = this;
        const highlightHandler = function (this: HTMLTableCellElement) {
            const tdEl = this;
            const width = tdEl.clientWidth;
            const height = tdEl.clientHeight;
            const highlightDiv = complexTable.getHighlightDiv();
            highlightDiv.style.width = width + 'px';
            highlightDiv.style.height = height + 'px';
            tdEl.prepend(highlightDiv);
            (highlightDiv as any).currentTD = tdEl;
            tdEl.addEventListener('DOMSubtreeModified', function () {
                const w = tdEl.clientWidth;
                const h = tdEl.clientHeight;
                highlightDiv.style.width = w + 'px';
                highlightDiv.style.height = h + 'px';
            });
        };
        this.td.addEventListener('click', highlightHandler);
        this.td.addEventListener('contextmenu', highlightHandler);
    }
}
