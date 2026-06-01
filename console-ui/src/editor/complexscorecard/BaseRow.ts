/**
 * BaseRow - Simple base class for content rows in the complex scorecard.
 *
 * Creates a <tr> element with fixed height.
 */

export default class BaseRow {
    tr: HTMLTableRowElement;

    constructor() {
        const tr = document.createElement('tr');
        tr.style.height = '30px';
        this.tr = tr;
    }
}
