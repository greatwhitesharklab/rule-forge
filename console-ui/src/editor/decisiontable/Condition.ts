/**
 * A single condition node (operator + right-hand value) in the RETE tree.
 *
 * Previously `ruleforge.Condition`.
 */

// Context.js, ComparisonOperator.js, InputType.js are still JS side-effects
// that attach classes to the global `ruleforge` namespace.
interface ComparisonOperatorInstance {
    getContainer: () => HTMLElement;
    getOperator: () => string;
    setOperator: (op: string) => void;
    initRightValue: (data: Record<string, unknown>) => void;
    getInputType: () => InputTypeLike | null;
}

declare const ruleforge: {
    ComparisonOperator: new (callback: () => void) => ComparisonOperatorInstance;
};

interface InputTypeLike {
    getContainer: () => HTMLElement;
    getDisplayContainer: () => HTMLElement;
    toXml: () => string;
    type: string;
    setValueType: (valueType: string, data: Record<string, unknown>) => void;
}

export class Condition {
    private container: HTMLElement;
    private operator: ComparisonOperatorInstance;
    private inputType: InputTypeLike | null = null;

    constructor(parentContainer: HTMLElement) {
        this.container = document.createElement('span');
        parentContainer.append(this.container);
        const self = this;
        this.operator = new ruleforge.ComparisonOperator(function () {
            self.inputType = self.operator.getInputType();
            if (self.inputType) {
                self.container.appendChild(self.inputType.getContainer());
            }
        });
        this.container.appendChild(this.operator.getContainer());
    }

    initData(data: Record<string, unknown>): void {
        const op = data['op'] as string;
        this.operator.setOperator(op);
        this.operator.initRightValue(data['value'] as Record<string, unknown>);
        this.inputType = this.operator.getInputType();
        if (this.inputType) {
            this.container.appendChild(this.inputType.getContainer());
        }
    }

    getDisplayContainer(): HTMLElement {
        const container = document.createElement('span');
        const operator = this.operator.getContainer().textContent || '';
        const opSpan = document.createElement('span');
        opSpan.style.cssText = 'color:blue';
        opSpan.textContent = operator;
        container.appendChild(opSpan);
        if (this.inputType) {
            container.appendChild(this.inputType.getDisplayContainer());
        }
        return container;
    }

    toXml(): string {
        let xml = '<condition op="' + this.operator.getOperator() + '">';
        if (this.inputType) {
            xml += this.inputType.toXml();
        }
        xml += '</condition>';
        return xml;
    }

    getOperator(): ComparisonOperatorInstance {
        return this.operator;
    }

    getInputType(): InputTypeLike | null {
        return this.inputType;
    }
}

// Backward-compatible global assignment
(window as unknown as Record<string, unknown>).ruleforge =
    (window as unknown as Record<string, Record<string, unknown>>).ruleforge || {};
((window as unknown as Record<string, Record<string, unknown>>).ruleforge).Condition = Condition;
