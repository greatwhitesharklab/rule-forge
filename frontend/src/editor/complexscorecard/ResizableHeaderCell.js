/**
 * ResizableHeaderCell - Base header cell with column resize behavior.
 *
 * Extends BaseCell with a drag handle for resizing columns and
 * logic for updating column width and rebuilding the highlight div.
 *
 * Extracted from the complexScoreCard webpack bundle (module 319).
 */

import BaseCell from './BaseCell.js';

export default class ResizableHeaderCell extends BaseCell {
    /**
     * @param {Object} rowContext - The row context providing table reference
     */
    constructor(rowContext) {
        super(rowContext);
        this.td.css('padding-right', '0');
        this.td.append(this.buildColResizeTrigger());
        this.bindColResize(rowContext);
    }

    /**
     * Build the drag handle element for column resizing.
     * @returns {jQuery} The resize trigger span
     */
    buildColResizeTrigger() {
        this.resizeTrigger = $('<span style="cursor: col-resize;width: 3px;height: 20px;float: right;border: solid 2px transparent;">&nbsp;</span>');
        return this.resizeTrigger;
    }

    /**
     * Bind mouse events for column resize drag behavior.
     *
     * @param {Object} rowContext - The row context providing table reference
     */
    bindColResize(rowContext) {
        let isDragging = false;
        let parentTd;
        let startX;
        let startWidth;
        const self = this;

        this.resizeTrigger.mouseover(function () {
            $(this).css('border', 'solid 2px #999');
        });
        this.resizeTrigger.mouseout(function () {
            $(this).css('border', 'solid 2px transparent');
        });
        this.resizeTrigger.mousedown(function (e) {
            parentTd = $(this).parent();
            isDragging = true;
            startX = e.pageX;
            startWidth = parentTd.width();
            e.preventDefault();
        });
        $(document).mousemove(function (e) {
            if (isDragging) {
                const newWidth = startWidth + (e.pageX - startX);
                if (self.actionCol) {
                    self.actionCol.width = newWidth;
                } else {
                    self.conditionCol.width = newWidth;
                }
                parentTd.width(newWidth);
                self._rebuildHighLightDiv(rowContext);
                e.preventDefault();
            }
        });
        $(document).mouseup(function () {
            isDragging = false;
            window._setDirty();
        });
    }

    /**
     * Rebuild the highlight div after column resize.
     *
     * @param {Object} rowContext - The row context providing table reference
     */
    _rebuildHighLightDiv(rowContext) {
        const highlightDiv = rowContext.complexTable.getHighlightDiv();
        const currentTD = highlightDiv.currentTD;
        if (currentTD) {
            const width = currentTD.get(0).clientWidth;
            const height = currentTD.get(0).clientHeight;
            highlightDiv.css({
                width: width + 'px',
                height: height + 'px'
            });
        }
    }
}
