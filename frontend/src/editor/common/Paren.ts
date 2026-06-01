import { InputType } from './InputType';
import { ComplexArithmetic } from './ComplexArithmetic';

export class Paren {
    container: HTMLSpanElement;
    parenContainer: HTMLSpanElement;
    inputType: InputType;
    arithmetic: ComplexArithmetic;

    constructor(rule: any) {
        this.container = document.createElement('span');
        const leftParen = document.createElement('span');
        leftParen.textContent = '(';
        leftParen.style.cssText = 'color:#000;fontWeight:blod;padding-left:3px;padding-right:3px;';
        const rightParen = document.createElement('span');
        rightParen.textContent = ')';
        rightParen.style.cssText = 'color:#000;fontWeight:blod;padding-left:3px;padding-right:3px;';
        this.parenContainer = document.createElement('span');
        this.container.appendChild(leftParen);
        this.container.appendChild(this.parenContainer);
        this.container.appendChild(rightParen);
        this.inputType = new InputType(null, null, null, rule);
        this.parenContainer.appendChild(this.inputType.getContainer());
        this.arithmetic = new ComplexArithmetic(rule);
        this.container.appendChild(this.arithmetic.getContainer());
    }

    initData(data: Record<string, any>): void {
        const value = data['value'];
        const valueType = value['valueType'];
        this.inputType.setValueType(valueType, value);
        this.arithmetic.initData(data['arithmetic']);
    }

    getDisplayContainer(): HTMLSpanElement {
        return this.inputType.getDisplayContainer();
    }

    toXml(): string {
        if (!this.inputType) {
            throw '请输入括号内容!';
        }
        let xml = '<paren>';
        xml += this.inputType.toXml();
        if (this.arithmetic) {
            xml += this.arithmetic.toXml();
        }
        xml += '</paren>';
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).Paren = Paren;
