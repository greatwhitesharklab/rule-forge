import { InputType } from './InputType';
import { FunctionProperty } from './FunctionProperty';

export class FunctionParameter {
    container: HTMLSpanElement;
    nameContainer: HTMLSpanElement;
    rule: any;
    name?: string;
    functionProperty?: FunctionProperty;
    inputType!: InputType;

    constructor(rule: any) {
        this.container = document.createElement('span');
        this.nameContainer = document.createElement('span');
        this.rule = rule;
        this.container.appendChild(this.nameContainer);
        this.nameContainer.style.color = 'gray';
    }

    initData(data: any): void {
        if (!data) {
            return;
        }
        this.name = data.name;
        this.nameContainer.textContent = this.name + ':';
        if (data.needProperty || data.property) {
            this.functionProperty = new FunctionProperty();
            this.functionProperty.setProperty({ name: data.property, label: data.propertyLabel });
        }
        this.inputType = new InputType(null, null, this.functionProperty, this.rule);
        const value = data.objectParameter;
        if (value) {
            const valueType = value.valueType;
            this.inputType.setValueType(valueType, value);
        }
        this.container.appendChild(this.inputType.getContainer());
        if (this.functionProperty) {
            const commaSpan = document.createElement('span');
            commaSpan.textContent = '，';
            this.container.appendChild(commaSpan);
            const propLabel = document.createElement('span');
            propLabel.style.color = 'gray';
            propLabel.textContent = '属性:';
            this.container.appendChild(propLabel);
            this.container.appendChild(this.functionProperty.getContainer());
        }
    }

    toXml(): string {
        if (!this.name) {
            return '';
        }
        let xml = '<function-parameter ';
        xml += 'name="' + this.name + '" ';
        if (this.functionProperty) {
            xml += this.functionProperty.toXml();
        }
        xml += '>';
        xml += this.inputType.toXml();
        xml += '</function-parameter>';
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).FunctionParameter = FunctionParameter;
