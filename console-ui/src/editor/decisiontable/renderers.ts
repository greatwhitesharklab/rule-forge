/**
 * Custom Handsontable renderer and cell type registration.
 *
 * Previously `renderers.js`.
 */

import HandsontableModule from 'handsontable';
const Handsontable = (HandsontableModule as any).default || HandsontableModule;
import $ from 'jquery';

import { CellCondition } from './CellCondition.js';
import { CellContent } from './CellContent.js';

function applySpanProperties(TD: HTMLElement, cellData: { rowspan: number } | null): void {
    if (cellData) {
        const rowspan = cellData.rowspan;
        TD.style.display = 'table-cell';
        if (rowspan > 1) {
            TD.setAttribute('rowspan', String(rowspan));
        } else {
            TD.removeAttribute('rowspan');
        }
    } else {
        TD.style.display = 'none';
        TD.removeAttribute('rowspan');
        TD.removeAttribute('colspan');
    }
}

// The renderer function references the global `ht` (DecisionTable instance)
// and `ruleforge` namespace classes, set up by DecisionTable.js
const RuleForgeRenderer = function (
    instance: any,
    TD: HTMLElement,
    row: number,
    col: number,
    prop: any,
    value: any,
    cellProperties: any
): void {
    (Handsontable.renderers as any).cellDecorator.apply(this, arguments as any);
    if (!value && cellProperties.placeholder) {
        value = cellProperties.placeholder;
    }
    const ht = (window as any).ht;
    let cellData: any = ht.getCellData(row, col),
        content: any;
    applySpanProperties(TD, cellData);
    if (!cellData) {
        return;
    }
    cellData.container = TD;
    content = ht.getCellContent(cellData);
    if (content instanceof CellCondition) {
        $(TD).empty();
        const disContainer = content.getDisplayContainer();
        $(TD).append($('<div/>').append(disContainer));
    } else if (content instanceof CellContent) {
        $(TD).append(content.inputType.getContainer());
    } else {
        // other cell types
    }
};

(Handsontable.renderers as any).RuleForgeRenderer = RuleForgeRenderer;
(Handsontable.renderers as any).registerRenderer('ruleforge', RuleForgeRenderer);

(function (): void {
    (Handsontable.cellTypes as any).registerCellType('ruleforge', {
        editor: Handsontable.editors.TextEditor,
        renderer: (Handsontable.renderers as any).RuleForgeRenderer
    });
})();
