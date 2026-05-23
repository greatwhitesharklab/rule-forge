/**
 * HighlightCell - Base cell with highlight/selection behavior.
 *
 * Extends BaseCell with click and right-click handlers that position
 * a highlight div over the cell to indicate the active selection.
 *
 * Extracted from the complexScoreCard webpack bundle (module 317).
 */

import BaseCell from './BaseCell.js';

export default class HighlightCell extends BaseCell {
    /**
     * @param {Object} rowContext - The row context providing table reference
     */
    constructor(rowContext) {
        super(rowContext);

        const complexTable = rowContext.complexTable;
        const highlightHandler = function () {
            const td = $(this);
            const width = td.get(0).clientWidth;
            const height = td.get(0).clientHeight;
            const highlightDiv = complexTable.getHighlightDiv();
            highlightDiv.css({
                width: width + 'px',
                height: height + 'px'
            });
            td.prepend(highlightDiv);
            highlightDiv.currentTD = td;
            td.on('DOMSubtreeModified', function () {
                const w = td.get(0).clientWidth;
                const h = td.get(0).clientHeight;
                highlightDiv.css({
                    width: w + 'px',
                    height: h + 'px'
                });
            });
        };
        this.td.click(highlightHandler);
        this.td.contextmenu(highlightHandler);
    }
}
