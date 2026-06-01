import { renderReact } from '../../components/react-bridge.js';
import VariableValueWidget from '../../components/widgets/VariableValueWidget.jsx';
import { ComplexArithmetic } from './ComplexArithmetic';
import { variableValueArray } from './URule';

export class VariableValue {
    arithmetic: ComplexArithmetic | null;
    container: HTMLSpanElement;
    widgetRoot: HTMLSpanElement;
    functionProperty: any;
    widgetRef: any = null;

    constructor(arithmetic: ComplexArithmetic | null, data?: any, act?: string, functionProperty?: any) {
        this.arithmetic = arithmetic;
        this.container = document.createElement('span');
        this.widgetRoot = document.createElement('span');
        this.container.appendChild(this.widgetRoot);
        this.functionProperty = functionProperty;
        if (arithmetic) {
            this.container.appendChild(arithmetic.getContainer());
        }
        const self = this;
        renderReact(VariableValueWidget, {
            initialData: data,
            libraries: window._ruleforgeEditorVariableLibraries as any,
            act: act,
            onDirty: function () { if (window._setDirty) window._setDirty(); },
            onFunctionPropertyUpdate: function (variables: any) {
                if (self.functionProperty && self.functionProperty.initMenu) {
                    self.functionProperty.initMenu(variables);
                }
            },
            ref: function (ref: any) { self.widgetRef = ref; },
        }, this.widgetRoot);
        this.initMenu();
        variableValueArray.push(this);
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

    initMenu(variableLibraries?: unknown[]): void {
        const data = variableLibraries || window._ruleforgeEditorVariableLibraries;
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

    getType(): string {
        if (this.widgetRef) {
            return this.widgetRef.getType();
        }
        return 'VariableCategory';
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).VariableValue = VariableValue;
