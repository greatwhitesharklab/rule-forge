/**
 * BaseCell - Minimal base class for all cell types in the complex scorecard grid.
 *
 * Provides core functionality: ID generation, row/column references,
 * DOM positioning, rowspan accessors, and highlight behavior.
 */

export default class BaseCell {
    id: number;
    td: HTMLTableCellElement;

    constructor(rowContext: import('./RowContext').default) {
        this.id = rowContext.complexTable.nextId();
        const td = document.createElement('td');
        td.style.cssText = 'position: relative;';
        this.td = td;
    }
}
