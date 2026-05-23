/**
 * BaseRow - Simple base class for content rows in the complex scorecard.
 *
 * Creates a <tr> element with fixed height.
 *
 * Extracted from the complexScoreCard webpack bundle (module 318).
 */

export default class BaseRow {
    constructor() {
        this.tr = $('<tr style="height: 30px;"></tr>');
    }
}
