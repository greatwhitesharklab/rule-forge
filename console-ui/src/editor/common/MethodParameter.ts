import { InputType } from './InputType';

export class MethodParameter {
    inputType: InputType;
    container: HTMLSpanElement;
    name: string = '';
    type: string = '';

    constructor(rule: any) {
        this.inputType = new InputType(null, null, null, rule);
        this.container = this.inputType.getContainer();
    }

    initData(data: Record<string, any>): void {
        if (!data) {
            return;
        }
        this.name = data['name'];
        this.type = data['type'];
        if (!data['value']) {
            return;
        }
        const value = data['value'];
        if (!value['valueType']) {
            return;
        }
        this.inputType.setValueType(value['valueType'], value);
    }

    toXml(): string {
        let xml = '<parameter name="' + this.name + '" type="' + this.type + '">';
        xml += this.inputType.toXml();
        xml += '</parameter>';
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }

    getInputType(): InputType {
        return this.inputType;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).MethodParameter = MethodParameter;
