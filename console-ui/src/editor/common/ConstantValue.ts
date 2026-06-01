import { renderReact } from '../../components/react-bridge.js';
import ConstantValueWidget from '../../components/widgets/ConstantValueWidget.jsx';
import { ComplexArithmetic } from './ComplexArithmetic';
import { constantValueArray } from './URule';

export class ConstantValue {
    arithmetic: ComplexArithmetic | null;
    container: HTMLSpanElement;
    widgetRoot: HTMLSpanElement;
    widgetRef: any = null;

    constructor(arithmetic: ComplexArithmetic | null, data?: any) {
        this.arithmetic = arithmetic;
        this.container = document.createElement('span');
        this.widgetRoot = document.createElement('span');
        this.container.appendChild(this.widgetRoot);
        if (arithmetic) {
            this.container.appendChild(arithmetic.getContainer());
        }
        const self = this;
        renderReact(ConstantValueWidget, {
            initialData: data,
            libraries: window._ruleforgeEditorConstantLibraries,
            onDirty: function () { if (window._setDirty) window._setDirty(); },
            ref: function (ref: any) { self.widgetRef = ref; },
        }, this.widgetRoot);
        this.initMenu();
        constantValueArray.push(this);
    }

    initMenu(constantLibraries?: unknown[]): void {
        const data = constantLibraries || window._ruleforgeEditorConstantLibraries;
        if (this.widgetRef && data) {
            this.widgetRef.initMenu(data);
        }
    }

    getDisplayContainer(): HTMLSpanElement {
        const container = document.createElement('span');
        container.textContent = this.widgetRef ? this.widgetRef.getDisplayLabel() : '';
        if (this.arithmetic) {
            const dis = this.arithmetic.getDisplayContainer();
            if (dis) {
                container.appendChild(dis);
            }
        }
        return container;
    }

    toXml(): string {
        if (this.widgetRef) {
            return this.widgetRef.toXml();
        }
        return '';
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).ConstantValue = ConstantValue;
