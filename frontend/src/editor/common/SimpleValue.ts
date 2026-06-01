import { renderReact } from '../../components/react-bridge.js';
import SimpleValueWidget from '../../components/widgets/SimpleValueWidget.jsx';
import { ComplexArithmetic } from './ComplexArithmetic';

export class SimpleValue {
    container: HTMLSpanElement;
    widgetRoot: HTMLSpanElement;
    arithmetic: ComplexArithmetic | null;
    widgetRef: any = null;

    constructor(arithmetic: ComplexArithmetic | null, data?: any) {
        this.container = document.createElement('span');
        this.widgetRoot = document.createElement('span');
        this.container.appendChild(this.widgetRoot);
        this.arithmetic = arithmetic;
        if (arithmetic) {
            this.container.appendChild(arithmetic.getContainer());
        }
        const self = this;
        renderReact(SimpleValueWidget, {
            initialData: data,
            onDirty: function () { if (window._setDirty) window._setDirty(); },
            ref: function (ref: any) { self.widgetRef = ref; },
        }, this.widgetRoot);
    }

    getDisplayContainer(): HTMLSpanElement {
        const container = document.createElement('span');
        container.textContent = this.widgetRef ? this.widgetRef.getDisplayText() : '';
        if (this.arithmetic) {
            const dis = this.arithmetic.getDisplayContainer();
            if (dis) {
                container.appendChild(dis);
            }
        }
        return container;
    }

    initData(data: any): void {
        if (!data) {
            return;
        }
        if (this.widgetRef) {
            this.widgetRef.initData(data);
        }
        if (this.arithmetic) {
            this.arithmetic.initData(data['arithmetic']);
        }
    }

    getValue(): string {
        if (this.widgetRef) {
            return this.widgetRef.getValue();
        }
        return '';
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).SimpleValue = SimpleValue;
