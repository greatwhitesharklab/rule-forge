import { generateContainer } from '../common/URule';

declare const ruleforge: any;

export class RuleProperty {
    parent: any;
    value: any;
    editorType: number;
    container: HTMLSpanElement;
    name: string;
    radioName: string;
    yesRadio: HTMLInputElement | null = null;
    noRadio: HTMLInputElement | null = null;

    constructor(parent: any, name: string, defaultValue: any, editorType: number) {
        this.parent = parent;
        this.value = defaultValue;
        this.editorType = editorType;
        this.container = document.createElement('span');
        this.container.className = 'rule-property';
        const nameContainer = document.createElement('span');
        this.name = name;
        const label = this.getLabel();
        nameContainer.textContent = label + '：';
        this.container.appendChild(nameContainer);
        const valueContainer = document.createElement('span');
        const valueLabel = generateContainer();
        let dv = defaultValue;
        if (dv === '') dv = '无';
        valueLabel.style.color = '#000';
        valueLabel.textContent = dv;
        valueContainer.appendChild(valueLabel);
        this.container.appendChild(valueContainer);
        let editor: HTMLInputElement | null = null;
        this.radioName = Math.uuid!(15);
        if (editorType === 1) {
            editor = document.createElement('input');
            editor.type = 'text';
            editor.size = 30;
            editor.className = 'form-control rule-text-editor';
        } else if (editorType === 2) {
            editor = document.createElement('input');
            editor.type = 'datetime';
            editor.size = 30;
            editor.className = 'form-control rule-text-editor';
            editor.title = '日期格式为:yyyy-MM-dd HH:mm:ss，如2016-10-11 12:50:06';
        } else if (editorType === 3) {
            this.yesRadio = document.createElement('input');
            this.yesRadio.type = 'radio';
            this.yesRadio.value = '是';
            this.yesRadio.name = this.radioName;
            this.noRadio = document.createElement('input');
            this.noRadio.type = 'radio';
            this.noRadio.value = '否';
            this.noRadio.name = this.radioName;
        }
        const self = this;
        if (editorType !== 3) {
            editor!.addEventListener('blur', function () {
                self.value = editor!.value;
                editor!.style.display = 'none';
                if (self.value === '') {
                    valueLabel.textContent = '无';
                } else {
                    valueLabel.textContent = self.value;
                }
                valueLabel.style.display = '';
                window._setDirty?.();
            });
            valueLabel.addEventListener('click', function () {
                valueLabel.style.display = 'none';
                editor!.value = self.value;
                editor!.style.display = '';
                editor!.focus();
            });
            this.container.appendChild(editor!);
            editor!.style.display = 'none';
            if (editorType === 2) {
                if (dv !== '无') {
                    this.value = dv;
                    valueLabel.textContent = this.value;
                }
            }
        } else {
            if (defaultValue === true) {
                this.yesRadio!.checked = true;
            } else {
                this.noRadio!.checked = true;
            }
            this.yesRadio!.addEventListener('change', function () {
                window._setDirty?.();
            });
            this.noRadio!.addEventListener('change', function () {
                window._setDirty?.();
            });
            valueLabel.style.display = 'none';
            this.container.appendChild(this.yesRadio!);
            this.container.appendChild(this.noRadio!);
        }
        const del = document.createElement('i');
        del.className = 'glyphicon glyphicon-remove rule-property-del';
        del.addEventListener('click', function () {
            self.container.remove();
            const pos = self.parent.properties.indexOf(self);
            self.parent.properties.splice(pos, 1);
            window._setDirty?.();
        });
        this.container.appendChild(del);
    }

    getLabel(): string {
        let label = '';
        if (this.name === 'salience') {
            label = '优先级';
        } else if (this.name === 'loop') {
            label = '允许循环触发';
        } else if (this.name === 'effective-date') {
            label = '生效日期';
        } else if (this.name === 'expires-date') {
            label = '失效日期';
        } else if (this.name === 'enabled') {
            label = '是否启用';
        } else if (this.name === 'debug') {
            label = '允许调试信息输出';
        } else if (this.name === 'activation-group') {
            label = '互斥组';
        } else if (this.name === 'agenda-group') {
            label = '执行组';
        } else if (this.name === 'auto-focus') {
            label = '自动获取焦点';
        } else if (this.name === 'ruleflow-group') {
            label = '规则流组';
        }
        return label;
    }

    toXml(): string {
        let xml = this.name;
        if (this.editorType === 3) {
            if (this.yesRadio!.checked) {
                xml += '="true"';
            } else {
                xml += '="false"';
            }
        } else {
            if (!this.value || this.value === '') {
                throw new Error('请输入属性' + this.name + '的具体值!');
            }
            xml += '="' + this.value + '"';
        }
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

(ruleforge as any).RuleProperty = RuleProperty;
