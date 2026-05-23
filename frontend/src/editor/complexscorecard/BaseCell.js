/**
 * BaseCell - Minimal base class for all cell types in the complex scorecard grid.
 *
 * Provides core functionality: ID generation, row/column references,
 * DOM positioning, rowspan accessors, and highlight behavior.
 *
 * Extracted from the complexScoreCard webpack bundle (module 317).
 */

export default class BaseCell {
    /**
     * @param {Object} rowContext - The row context providing table reference
     */
    constructor(rowContext) {
        this.id = rowContext.complexTable.nextId();
        this.td = $('<td style="position: relative;"></td>');
    }
}
