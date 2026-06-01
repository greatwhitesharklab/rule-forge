import { renderReact } from '../../components/react-bridge.js';
import ParameterValueWidget from '../../components/widgets/ParameterValueWidget.jsx';
import { ComplexArithmetic } from './ComplexArithmetic';
import { parameterValueArray } from './URule';

export class ParameterValue {
    arithmetic: ComplexArithmetic | null;
    container: HTMLSpanElement;
    widgetRoot: HTMLSpanElement;
    widgetRef: any = null;

    constructor(arithmetic: ComplexArithmetic | null, data?: any, act?: string) {
        this.arithmetic = arithmetic;
        this.container = document.createElement('span');
        this.widgetRoot = document.createElement('span');
        this.container.appendChild(this.widgetRoot);
        if (arithmetic) {
            this.container.appendChild(arithmetic.getContainer());
        }
        const self = this;
        renderReact(ParameterValueWidget, {
            initialData: data,
            libraries: window._ruleforgeEditorParameterLibraries as any,
            act: act,
            onDirty: function () { if (window._setDirty) window._setDirty(); },
            ref: function (ref: any) { self.widgetRef = ref; },
        }, this.widgetRoot);
        this.initMenu();
        parameterValueArray.push(this);
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

    initMenu(parameterLibraries?: unknown[]): void {
        const data = parameterLibraries || window._ruleforgeEditorParameterLibraries;
        if (this.widgetRef && data) {
            this.widgetRef.initMenu(data);
        }
    }

    setValue(data: any): void {
        if (this.widgetRef) {
            this.widgetRef.setValue(data);
        }
    }

    initData(data: any): void {
        if (!data) return;
        this.setValue(data);
        if (this.arithmetic) {
            this.arithmetic.initData(data['arithmetic']);
        }
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
(ruleforge as Record<string, any>).ParameterValue = ParameterValue;
