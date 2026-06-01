import { InputType } from './InputType';
import { Paren } from './Paren';
import { generateContainer } from './URule';

export class NextType {
    container: HTMLSpanElement;
    rule: any;
    inputType: InputType | null = null;
    paren: Paren | null = null;
    selectorLabel: HTMLSpanElement;
    menu: any;

    constructor(rule: any) {
        this.container = document.createElement('span');
        this.rule = rule;
        this.selectorLabel = generateContainer();
        this.selectorLabel.style.fontWeight = 'blod';
        this.selectorLabel.style.color = '#fff';
        this.container.appendChild(this.selectorLabel);
        this.selectorLabel.textContent = '.';

        const self = this;
        const onClick = function (menu: any) {
            const type = menu.name;
            self.doNext(type);
            if (window._setDirty) window._setDirty();
        };
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '值',
                name: 'value',
                onClick: onClick
            }, {
                label: '括号',
                name: 'paren',
                onClick: onClick
            }]
        });
        this.selectorLabel.addEventListener('click', function (e: Event) {
            self.menu.show(e);
        });
    }

    initData(data: Record<string, any>): void {
        const value = data['value'];
        const valueType = value['valueType'];
        if (valueType === 'Paren') {
            this.doNext('paren');
            this.paren!.initData(value);
        } else {
            this.doNext('value');
            this.inputType!.setValueType(valueType, value);
        }
    }

    toXml(): string {
        if (this.paren) {
            return this.paren.toXml();
        } else if (this.inputType) {
            return this.inputType.toXml();
        }
        return '';
    }

    getDisplayContainer(): HTMLSpanElement | null {
        if (this.inputType) {
            return this.inputType.getDisplayContainer();
        } else if (this.paren) {
            return this.paren.getDisplayContainer();
        }
        return null;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }

    doNext(type: string): void {
        if (type === 'value') {
            if (this.paren) {
                this.paren.getContainer().remove();
                this.paren = null;
            }
            if (!this.inputType) {
                this.inputType = new InputType(null, null, null, this.rule);
                this.container.appendChild(this.inputType.getContainer());
            }
        } else if (type === 'paren') {
            if (this.inputType) {
                this.inputType.getContainer().remove();
                this.inputType = null;
            }
            if (!this.paren) {
                this.paren = new Paren(this.rule);
                this.container.appendChild(this.paren.getContainer());
            }
        }
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).NextType = NextType;
