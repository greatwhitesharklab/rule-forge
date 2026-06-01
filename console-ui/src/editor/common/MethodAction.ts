import { MethodParameter } from './MethodParameter';

export class MethodAction {
    parameters: MethodParameter[] = [];
    rule: any;
    container: HTMLSpanElement;
    nameContainer: HTMLSpanElement;
    bean: string = '';
    name: string = '';
    method: string = '';
    methodLabel: string = '';
    parameterCount: number = 0;

    constructor(rule?: any) {
        this.rule = rule;
        this.container = document.createElement('span');
        this.nameContainer = document.createElement('span');
        this.container.appendChild(this.nameContainer);
        this.nameContainer.style.color = 'darkblue';
    }

    initData(data: Record<string, any>): void {
        if (!data) {
            return;
        }
        this.bean = data['beanId'];
        this.name = data['beanLabel'];
        this.method = data['methodName'];
        this.methodLabel = data['methodLabel'];
        const parameters = data['parameters'];
        this.parameterCount = 0;
        if (parameters) {
            this.parameterCount = parameters.length;
        }
        if (this.parameterCount === 0) {
            this.nameContainer.textContent = this.methodLabel;
            const parameterLabel = document.createElement('span');
            parameterLabel.style.color = 'gray';
            parameterLabel.textContent = '(无参数)';
            this.container.appendChild(parameterLabel);
        } else {
            this.nameContainer.textContent = this.methodLabel + '(';
        }
        if (this.parameterCount === 0) {
            return;
        }
        for (let i = 0; i < this.parameterCount; i++) {
            const p = parameters[i];
            if (i > 0) {
                const comma = document.createElement('span');
                comma.textContent = ';';
                this.container.appendChild(comma);
            }
            if (this.parameterCount > 0) {
                const seqLabel = document.createElement('span');
                seqLabel.style.color = 'purple';
                seqLabel.innerHTML = '&nbsp;' + p['name'] + ':';
                this.container.appendChild(seqLabel);
            }
            const parameter = new MethodParameter(this.rule);
            this.parameters.push(parameter);
            this.container.appendChild(parameter.getContainer());
            parameter.initData(p);
        }
        const rightParen = document.createElement('span');
        rightParen.textContent = ')';
        this.container.appendChild(rightParen);
    }

    toXml(): string {
        if (!this.name || this.name === '') {
            throw '请选择要执行的方法！';
        }
        let xml = '<execute-method bean="' + this.bean + '" bean-label="' + this.name + '" method-label="' + this.methodLabel + '" method-name="' + this.method + '">';
        for (let i = 0; i < this.parameters.length; i++) {
            const p = this.parameters[i];
            xml += p.toXml();
        }
        xml += '</execute-method>';
        return xml;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).MethodAction = MethodAction;
