import { NextType } from './NextType';
import { generateContainer } from './URule';

export class ComplexArithmetic {
    container: HTMLSpanElement;
    operator: string = '';
    rule: any;
    selectorLabel: HTMLSpanElement;
    nextType: NextType | null = null;
    info: string = '';
    menu: any;

    constructor(rule: any) {
        this.container = document.createElement('span');
        this.rule = rule;
        this.selectorLabel = generateContainer();
        this.selectorLabel.style.fontWeight = 'blod';
        this.container.appendChild(this.selectorLabel);

        const self = this;
        const onClick = function (menu: any) {
            self.setOperator(menu.name);
        };
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '+',
                name: 'Add',
                onClick: onClick
            }, {
                label: '-',
                name: 'Sub',
                onClick: onClick
            }, {
                label: 'x',
                name: 'Mul',
                onClick: onClick
            }, {
                label: '÷',
                name: 'Div',
                onClick: onClick
            }, {
                label: '%',
                name: 'Mod',
                onClick: onClick
            }, {
                label: '删除',
                onClick: function () {
                    if (window._setDirty) window._setDirty();
                    if (self.nextType) {
                        self.nextType.getContainer().remove();
                        self.nextType = null;
                        self.selectorLabel.textContent = '.';
                        self.selectorLabel.style.color = '#fff';
                        self.selectorLabel.style.paddingLeft = '0px';
                        self.selectorLabel.style.paddingRight = '0px';
                    }
                }
            }]
        });
        this.selectorLabel.addEventListener('click', function (e: Event) {
            self.menu.show(e);
        });
    }

    setOperator(operator: string): void {
        if (window._setDirty) window._setDirty();
        this.operator = operator;
        switch (operator) {
            case 'Add': this.info = '+'; break;
            case 'Sub': this.info = '-'; break;
            case 'Mul': this.info = 'x'; break;
            case 'Div': this.info = '÷'; break;
            case 'Mod': this.info = '%'; break;
        }
        this.selectorLabel.textContent = this.info;
        this.selectorLabel.style.color = 'green';
        this.selectorLabel.style.paddingLeft = '4px';
        this.selectorLabel.style.paddingRight = '4px';
        if (!this.nextType) {
            this.nextType = new NextType(this.rule);
            this.container.appendChild(this.nextType.getContainer());
        }
    }

    initData(data: any): void {
        if (!data) {
            return;
        }
        const type = data['type'];
        this.setOperator(type);
        this.nextType!.initData(data);
    }

    getDisplayContainer(): HTMLSpanElement | null {
        if (this.nextType) {
            const container = document.createElement('span');
            container.textContent = this.info;
            container.appendChild(this.nextType.getDisplayContainer()!);
            return container;
        }
        return null;
    }

    toXml(): string {
        if (!this.nextType) {
            return '';
        }
        let xml = '<complex-arith type="' + this.operator + '">';
        xml += this.nextType.toXml();
        xml += '</complex-arith>';
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).ComplexArithmetic = ComplexArithmetic;
