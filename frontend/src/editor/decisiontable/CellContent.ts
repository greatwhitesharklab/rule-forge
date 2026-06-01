/**
 * Cell content wrapper — renders an input-type editor inside a Handsontable cell.
 *
 * Previously `ruleforge.CellContent`.
 */

// InputType.js is still JS
interface InputTypeLike {
    getContainer: () => HTMLElement;
    getDisplayContainer: () => HTMLElement;
    toXml: () => string;
    type: string;
    setValueType: (valueType: string, data: Record<string, unknown>) => void;
}

declare const ruleforge: {
    InputType: new (config: null, label: string) => InputTypeLike;
};

export class CellContent {
    container: HTMLElement;
    inputType: InputTypeLike;

    constructor(element: HTMLElement | ArrayLike<HTMLElement>) {
        this.container = (element as ArrayLike<HTMLElement>)[0] || (element as HTMLElement);
        this.container.style.height = '40px';
        this.container.style.width = '100%';
        this.inputType = new ruleforge.InputType(null, '无');
        this.container.appendChild(this.inputType.getContainer());
    }

    clean(): void {
        if (this.inputType) {
            this.inputType.getContainer().remove();
        }
        this.inputType = new ruleforge.InputType(null, '无');
        this.container.appendChild(this.inputType.getContainer());
        window._setDirty?.();
    }

    initData(data: Record<string, unknown>): void {
        this.inputType.setValueType(data['valueType'] as string, data);
    }

    toXml(): string {
        if (this.inputType.type === '') {
            return '';
        }
        return this.inputType.toXml();
    }
}

// Backward-compatible global assignment
(window as unknown as Record<string, unknown>).ruleforge =
    (window as unknown as Record<string, Record<string, unknown>>).ruleforge || {};
((window as unknown as Record<string, Record<string, unknown>>).ruleforge).CellContent = CellContent;
