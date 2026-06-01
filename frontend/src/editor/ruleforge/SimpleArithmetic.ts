import { generateContainer } from '../common/URule';

declare const ruleforge: any;

export class SimpleArithmetic {
    container: HTMLSpanElement;
    selectorLabel: HTMLElement;
    operator: string | null = '';
    value: any = null;
    simpleArithmetic!: any;
    menu: MenuInstance;

    constructor() {
        this.container = document.createElement('span');
        this.selectorLabel = generateContainer();
        this.selectorLabel.textContent = '.';
        this.selectorLabel.style.color = '#FFF';
        this.container.appendChild(this.selectorLabel);
        const self = this;
        const onClick = function (menuItem: MenuItemConfig) {
            self.setOperator(menuItem.name);
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
                    if (self.value) {
                        self.value.getContainer().remove();
                        self.operator = null;
                        self.value = null;
                        self.selectorLabel.textContent = '.';
                        self.selectorLabel.style.color = '#FFF';
                        self.selectorLabel.style.paddingLeft = '0px';
                        self.selectorLabel.style.paddingRight = '0px';
                    }
                }
            }]
        });
        this.selectorLabel.addEventListener('click', function (e: Event) {
            self.menu.show(e as MouseEvent);
        });
    }

    initData(data: Record<string, any> | null): void {
        if (!data) {
            return;
        }
        const type = data['type'];
        this.setOperator(type);
        this.value.initData(data['value']);
    }

    setOperator(operator: string | null): void {
        window._setDirty?.();
        this.operator = operator;
        let info = '';
        switch (operator) {
            case 'Add':
                info = '+';
                break;
            case 'Sub':
                info = '-';
                break;
            case 'Mul':
                info = 'x';
                break;
            case 'Div':
                info = '÷';
                break;
            case 'Mod':
                info = '%';
                break;
        }
        this.selectorLabel.style.color = 'green';
        this.selectorLabel.style.fontWeight = 'bold';
        this.selectorLabel.style.paddingLeft = '5px';
        this.selectorLabel.style.paddingRight = '5px';
        this.selectorLabel.textContent = info;
        if (!this.value) {
            this.simpleArithmetic = new ruleforge.SimpleArithmetic();
            this.value = new ruleforge.SimpleValue(this.simpleArithmetic);
            this.container.appendChild(this.value.getContainer());
        }
    }

    toXml(): string {
        if (!this.operator || this.operator === '') {
            return '';
        }
        if (!this.value) {
            throw new Error('请输入具体值！');
        }
        const value = this.value.getValue();
        if (value === '') {
            throw new Error('请输入具体值！');
        }
        let xml = '<simple-arith type="' + this.operator + '" value="' + value + '">';
        xml += this.simpleArithmetic.toXml();
        xml += '</simple-arith>';
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

(ruleforge as any).SimpleArithmetic = SimpleArithmetic;
