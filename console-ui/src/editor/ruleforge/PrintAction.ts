declare const ruleforge: any;

export class PrintAction {
    container: HTMLSpanElement;
    beforeContainer: HTMLSpanElement;
    inputType: any;
    inputTypeContainer: HTMLElement;
    afterContainer: HTMLSpanElement;

    constructor(rule?: any) {
        this.container = document.createElement('span');
        this.beforeContainer = document.createElement('span');
        this.beforeContainer.textContent = '';
        this.container.appendChild(this.beforeContainer);
        this.inputType = new ruleforge.InputType(null, null, null, rule);
        this.inputTypeContainer = this.inputType.getContainer();
        this.container.appendChild(this.inputTypeContainer);
        this.afterContainer = document.createElement('span');
        this.afterContainer.textContent = '';
        this.container.appendChild(this.afterContainer);
    }

    initData(data?: Record<string, any>): void {
        if (!data) {
            return;
        }
        const value = data['value'];
        if (!value) {
            return;
        }
        this.inputType.setValueType(value['valueType'], value);
    }

    toXml(): string {
        let xml = '<console-print>';
        xml += this.inputType.toXml();
        xml += '</console-print>';
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

(ruleforge as any).PrintAction = PrintAction;
