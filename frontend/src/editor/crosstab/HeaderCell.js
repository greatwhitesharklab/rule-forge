/**
 * HeaderCell - The top-left corner cell of the crosstab grid.
 *
 * Manages the header cell with editable text, rowspan/colspan adjustments,
 * and XML serialization. Displayed at the intersection of the first top row
 * and first left column.
 *
 * Extracted from the crosstab webpack bundle (module 332).
 */

export default class HeaderCell {
    /**
     * @param {TopRow} row - The first top row
     * @param {LeftColumn} col - The first left column
     */
    constructor(row, col) {
        this.row = row;
        this.col = col;
        this.td = $('<td style="text-align:center;vertical-align: middle;background: #f8f8f8;color: #4caf50;border: 1px solid #c6c4c4;"></td>');
        this.init();
    }

    /**
     * Initialize the header cell DOM: display label, text editor, and edit button.
     */
    init() {
        this.row.tr.append(this.td);

        this.text = 'TOP/LEFT';
        this.label = $('<span>TOP/LEFT</span>');
        this.td.append(this.label);

        this.editor = $('<textarea rows="3" class="form-control" style="display: none;padding: 1px;"></textarea>');
        this.td.append(this.editor);

        this.editorButton = $('<div style="margin-left: 5px;cursor:pointer" title="编辑"><i class="glyphicon glyphicon-edit"></i></div>');
        this.td.append(this.editorButton);

        const self = this;
        this.editorButton.click(function () {
            self.editor.show();
            self.editor.val(self.text);
            self.label.hide();
            self.editorButton.hide();
            self.editor.focus();
        });

        this.editor.blur(function () {
            self.editor.hide();
            self.label.show();
            let displayText = self.text || '';
            displayText = displayText.replace(new RegExp('\n', 'gm'), '<br>');
            self.label.html(displayText);
            self.editorButton.show();
        });

        this.editor.change(function () {
            self.text = $(this).val();
            window._setDirty();
        });
    }

    /**
     * Initialize header cell data from server response.
     *
     * @param {Object} data - Header cell data
     * @param {string} [data.text] - Display text
     * @param {number} [data.rowspan] - Row span
     * @param {number} [data.colspan] - Column span
     */
    initData(data) {
        if (data) {
            let text = data.text;
            if (text) {
                text = text.replace(new RegExp('\n', 'gm'), '<br>');
                this.text = text;
                this.label.html(text);
            }
            const rowspan = data.rowspan;
            if (rowspan) {
                this.td.prop('rowspan', rowspan);
            }
            const colspan = data.colspan;
            if (colspan) {
                this.td.prop('colspan', colspan);
            }
        }
    }

    /**
     * Adjust the colspan of this header cell.
     * @param {boolean} increment - True to increment, false to decrement
     */
    adjustColSpan(increment) {
        let colspan = this.td.prop('colspan');
        colspan || (colspan = 1);
        if (increment) {
            colspan++;
        } else {
            colspan--;
        }
        colspan || (colspan = 1);
        this.td.prop('colspan', colspan);
    }

    /**
     * Adjust the rowspan of this header cell.
     * @param {boolean} increment - True to increment, false to decrement
     */
    adjustRowSpan(increment) {
        let rowspan = this.td.prop('rowspan');
        rowspan || (rowspan = 1);
        if (increment) {
            rowspan++;
        } else {
            rowspan--;
        }
        rowspan || (rowspan = 1);
        this.td.prop('rowspan', rowspan);
    }

    /**
     * Get the current rowspan value.
     * @returns {number}
     */
    getRowSpan() {
        let rowspan = this.td.prop('rowspan');
        rowspan || (rowspan = 1);
        return parseInt(rowspan);
    }

    /**
     * Get the current colspan value.
     * @returns {number}
     */
    getColSpan() {
        let colspan = this.td.prop('colspan');
        colspan || (colspan = 1);
        return parseInt(colspan);
    }

    /**
     * Serialize this header cell to XML.
     * @returns {string} XML representation
     */
    toXml() {
        return '<header rowspan="' + this.getRowSpan() + '" colspan="' + this.getColSpan() + '"><![CDATA[' + this.text + ']]></header>';
    }
}
