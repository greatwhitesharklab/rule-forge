/**
 * HeaderCell - The top-left corner cell of the crosstab grid.
 *
 * Manages the header cell with editable text, rowspan/colspan adjustments,
 * and XML serialization. Displayed at the intersection of the first top row
 * and first left column.
 *
 * Extracted from the crosstab webpack bundle (module 332).
 */

import type BaseRowCol from './BaseRowCol';

export default class HeaderCell {
    row: BaseRowCol;
    col: BaseRowCol;
    td: HTMLTableCellElement;
    text: string;
    label: HTMLSpanElement;
    editor: HTMLTextAreaElement;
    editorButton: HTMLDivElement;

    /**
     * @param row - The first top row
     * @param col - The first left column
     */
    constructor(row: BaseRowCol, col: BaseRowCol) {
        this.row = row;
        this.col = col;
        const td = document.createElement('td');
        td.style.cssText = 'text-align:center;vertical-align: middle;background: #f8f8f8;color: #4caf50;border: 1px solid #c6c4c4;';
        this.td = td;
        this.init();
    }

    /**
     * Initialize the header cell DOM: display label, text editor, and edit button.
     */
    init(): void {
        (this.row as any).tr.appendChild(this.td);

        this.text = 'TOP/LEFT';
        const label = document.createElement('span');
        label.textContent = 'TOP/LEFT';
        this.label = label;
        this.td.appendChild(label);

        const editor = document.createElement('textarea');
        editor.rows = 3;
        editor.className = 'form-control';
        editor.style.cssText = 'display: none;padding: 1px;';
        this.editor = editor;
        this.td.appendChild(editor);

        const editorButton = document.createElement('div');
        editorButton.style.cssText = 'margin-left: 5px;cursor:pointer';
        editorButton.title = '编辑';
        editorButton.innerHTML = '<i class="glyphicon glyphicon-edit"></i>';
        this.editorButton = editorButton;
        this.td.appendChild(editorButton);

        const self = this;
        editorButton.addEventListener('click', function () {
            self.editor.style.display = '';
            self.editor.value = self.text;
            self.label.style.display = 'none';
            self.editorButton.style.display = 'none';
            self.editor.focus();
        });

        editor.addEventListener('blur', function () {
            self.editor.style.display = 'none';
            self.label.style.display = '';
            let displayText = self.text || '';
            displayText = displayText.replace(new RegExp('\n', 'gm'), '<br>');
            self.label.innerHTML = displayText;
            self.editorButton.style.display = '';
        });

        editor.addEventListener('change', function () {
            self.text = this.value;
            window._setDirty?.();
        });
    }

    /**
     * Initialize header cell data from server response.
     *
     * @param data - Header cell data
     */
    initData(data: any): void {
        if (data) {
            let text = data.text;
            if (text) {
                text = text.replace(new RegExp('\n', 'gm'), '<br>');
                this.text = text;
                this.label.innerHTML = text;
            }
            const rowspan = data.rowspan;
            if (rowspan) {
                this.td.rowSpan = rowspan;
            }
            const colspan = data.colspan;
            if (colspan) {
                this.td.colSpan = colspan;
            }
        }
    }

    /**
     * Adjust the colspan of this header cell.
     * @param increment - True to increment, false to decrement
     */
    adjustColSpan(increment: boolean): void {
        let colspan = this.td.colSpan;
        colspan || (colspan = 1);
        if (increment) {
            colspan++;
        } else {
            colspan--;
        }
        colspan || (colspan = 1);
        this.td.colSpan = colspan;
    }

    /**
     * Adjust the rowspan of this header cell.
     * @param increment - True to increment, false to decrement
     */
    adjustRowSpan(increment: boolean): void {
        let rowspan = this.td.rowSpan;
        rowspan || (rowspan = 1);
        if (increment) {
            rowspan++;
        } else {
            rowspan--;
        }
        rowspan || (rowspan = 1);
        this.td.rowSpan = rowspan;
    }

    /**
     * Get the current rowspan value.
     */
    getRowSpan(): number {
        let rowspan = this.td.rowSpan;
        rowspan || (rowspan = 1);
        return parseInt(String(rowspan));
    }

    /**
     * Get the current colspan value.
     */
    getColSpan(): number {
        let colspan = this.td.colSpan;
        colspan || (colspan = 1);
        return parseInt(String(colspan));
    }

    /**
     * Serialize this header cell to XML.
     * @returns XML representation
     */
    toXml(): string {
        return '<header rowspan="' + this.getRowSpan() + '" colspan="' + this.getColSpan() + '"><![CDATA[' + this.text + ']]></header>';
    }
}
