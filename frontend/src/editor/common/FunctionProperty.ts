import { renderReact } from '../../components/react-bridge.js';
import FunctionPropertyWidget from '../../components/widgets/FunctionPropertyWidget.jsx';

export class FunctionProperty {
    container: HTMLSpanElement;
    widgetRoot: HTMLSpanElement;
    widgetRef: any = null;

    constructor() {
        this.container = document.createElement('span');
        this.widgetRoot = document.createElement('span');
        this.container.appendChild(this.widgetRoot);
        const self = this;
        renderReact(FunctionPropertyWidget, {
            onDirty: function () { if (window._setDirty) window._setDirty(); },
            ref: function (ref: any) { self.widgetRef = ref; },
        }, this.widgetRoot);
    }

    toXml(): string {
        if (this.widgetRef) {
            return this.widgetRef.toXml();
        }
        return '';
    }

    initMenu(data: any): void {
        if (this.widgetRef) {
            this.widgetRef.initMenu(data);
        }
    }

    setProperty(data: any): void {
        if (this.widgetRef) {
            this.widgetRef.setProperty(data);
        }
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).FunctionProperty = FunctionProperty;
