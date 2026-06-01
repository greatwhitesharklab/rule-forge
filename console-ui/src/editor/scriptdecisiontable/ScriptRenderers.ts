/**
 * Custom Handsontable renderer and cell type for script decision tables.
 *
 * Uses CodeMirror for in-cell editing of script content.
 *
 * Previously `ScriptRenderers.js`.
 */

import CodeMirror from 'codemirror';
import HandsontableModule from 'handsontable';
const Handsontable = (HandsontableModule as any).default || HandsontableModule;
import $ from 'jquery';

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
    let cellData: any = ht.getCellData(row, col);
    let content: any, codeMirror: any;
    const colData = ht.getColData(col);
    const type = colData.type;
    let mode: string, scriptType: string;

    applySpanProperties(TD, cellData);
    if (!cellData || cellData.codeMirror) {
        if (cellData && cellData.codeMirror) {
            cellData.codeMirror.setValue(cellData.script || '');
        }
        return;
    }
    cellData.container = TD;
    $(TD).empty();
    const container = $('<div/>');
    const editor = $('<textarea/>')[0];
    container.append(editor);

    (CodeMirror as any).commands.autocomplete = function (cm: any): void {
        cm.showHint({ hint: (CodeMirror as any).hint['if'] });
    };

    if (type === 'Criteria') {
        mode = 'if';
        scriptType = 'DecisionNode';
    } else if (type === 'ConsolePrint') {
        mode = 'print';
        scriptType = 'ScriptNode';
    } else {
        mode = 'then';
        scriptType = 'ScriptNode';
    }

    codeMirror = (CodeMirror as any).fromTextArea(editor, {
        mode: mode,
        extraKeys: { 'Alt-/': 'autocomplete' },
        autofocus: false
    });

    codeMirror.on('change', function (cm: any, e: any): void {
        if (e.text === '.') {
            (CodeMirror as any).commands.autocomplete(codeMirror);
        }
        cellData.script = cm.getValue();
        ht.setDirty();
        setTimeout(function (): void {
            ht.invoke('render');
        }, 200);
    });

    codeMirror.on('keyup', function (_cm: any, e: any): void {
        if (e.keyCode === 13) {
            ht.invoke('render');
        }
    });

    codeMirror.setSize('100%', '100%');
    $(TD).append(container).click(function (): void {
        codeMirror.focus();
    });
    codeMirror.setValue(cellData.script || '');
    cellData.codeMirror = codeMirror;
};

(Handsontable.renderers as any).RuleForgeRenderer = RuleForgeRenderer;
(Handsontable.renderers as any).registerRenderer('ruleforge', RuleForgeRenderer);

(function (): void {
    (Handsontable.cellTypes as any).registerCellType('ruleforge', {
        editor: Handsontable.editors.TextEditor,
        renderer: (Handsontable.renderers as any).RuleForgeRenderer
    });
})();
