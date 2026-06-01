import Sortable from 'sortablejs';
import { Remark } from '../../Remark.js';

declare const ruleforge: any;
declare const MsgBox: { confirm(message: string, callback: () => void): void };

export class LoopRule {
    uuid: string;
    namedMap: Map<string, any>;
    namedReferenceValues: any[];
    parent: any;
    container: HTMLElement;
    data: Record<string, any> | undefined;
    actions: any[];
    elseActions: any[];
    loopStartActions: any[];
    loopEndActions: any[];
    properties: any[];
    ruleContainer!: HTMLDivElement;
    name: string = 'rule';
    remark!: any;
    nameContainer!: HTMLDivElement;
    label!: HTMLSpanElement;
    nameEditor!: HTMLInputElement;
    nameLabel!: HTMLSpanElement;
    menu!: MenuInstance;
    propertyContainer!: HTMLSpanElement;
    loopTargetContainer!: HTMLDivElement;
    loopTargetInputType!: any;
    loopStartLabel!: HTMLSpanElement;
    loopStartActionContainer!: HTMLDivElement;
    addLoopStartActionButton!: HTMLSpanElement;
    ifLabel!: HTMLDivElement;
    conditionContainer!: HTMLDivElement;
    thenLabel!: HTMLSpanElement;
    actionContainer!: HTMLDivElement;
    addActionButton!: HTMLSpanElement;
    elseContainer!: HTMLDivElement;
    elseLabel!: HTMLSpanElement;
    elseActionContainer!: HTMLDivElement;
    addElseActionButton!: HTMLSpanElement;
    loopEndLabel!: HTMLSpanElement;
    loopEndActionContainer!: HTMLDivElement;
    addLoopEndActionButton!: HTMLSpanElement;
    join!: any;

    constructor(parent: any, container: HTMLElement, data?: Record<string, any>) {
        this.uuid = Math.uuid()!;
        this.namedMap = new Map();
        this.namedReferenceValues = [];
        container.id = this.uuid;
        this.parent = parent;
        this.container = container;
        this.data = data;
        this.actions = [];
        this.elseActions = [];
        this.loopStartActions = [];
        this.loopEndActions = [];
        this.properties = [];
        this.init();
        this.initData();
    }

    private init(): void {
        this.ruleContainer = document.createElement('div');
        this.container.appendChild(this.ruleContainer);
        this.initRemark();
        this.initHeader();
        this.initLoopTarget();
        this.initLoopStart();
        this.initIf();
        this.initThen();
        this.initElse();
        this.initLoopEnd();
    }

    private initData(): void {
        if (!this.data) {
            return;
        }
        this.name = this.data['name'];
        this.nameLabel.innerText = this.name;
        const salience = this.data['salience'];
        if (salience) {
            this.addProperty(new ruleforge.RuleProperty(this, 'salience', salience, 1));
        }
        const loop = this.data['loop'];
        if (loop != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'loop', loop, 3));
        }
        const effectiveDate = this.data['effectiveDate'];
        if (effectiveDate) {
            this.addProperty(new ruleforge.RuleProperty(this, 'effective-date', effectiveDate, 2));
        }
        const expiresDate = this.data['expiresDate'];
        if (expiresDate) {
            this.addProperty(new ruleforge.RuleProperty(this, 'expires-date', expiresDate, 2));
        }
        const enabled = this.data['enabled'];
        if (enabled != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'enabled', enabled, 3));
        }
        const debug = this.data['debug'];
        if (debug != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'debug', debug, 3));
        }
        const activationGroup = this.data['activationGroup'];
        if (activationGroup) {
            this.addProperty(new ruleforge.RuleProperty(this, 'activation-group', activationGroup, 1));
        }
        const agendaGroup = this.data['agendaGroup'];
        if (agendaGroup) {
            this.addProperty(new ruleforge.RuleProperty(this, 'agenda-group', agendaGroup, 1));
        }
        const autoFocus = this.data['autoFocus'];
        if (autoFocus != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'auto-focus', autoFocus, 3));
        }
        const ruleflowGroup = this.data['ruleflowGroup'];
        if (ruleflowGroup) {
            this.addProperty(new ruleforge.RuleProperty(this, 'ruleflow-group', autoFocus, 1));
        }
        const remark = this.data['remark'];
        this.remark.setData(remark);

        const loopTarget = this.data['loopTarget'];
        if (loopTarget) {
            const value = loopTarget.value;
            if (value) {
                const valueType = value.valueType;
                this.loopTargetInputType.setValueType(valueType, value);
            }
        }

        const loopStart = this.data['loopStart'];
        if (loopStart) {
            const actions = loopStart.actions;
            if (actions) {
                for (let i = 0; i < actions.length; i++) {
                    const action = actions[i];
                    this.addLoopStartAction(action);
                }
            }
        }

        const lhs = this.data['lhs'];
        if (lhs) {
            const criterion = lhs['criterion'];
            if (criterion) {
                this.initCriterion(criterion);
            } else {
                this.initCriterion();
            }
        } else {
            this.initCriterion();
        }
        const rhs = this.data['rhs'];
        if (rhs) {
            const actions = rhs['actions'];
            if (actions) {
                for (let i = 0; i < actions.length; i++) {
                    const action = actions[i];
                    this.addAction(action);
                }
            }
        }
        const elseData = this.data['other'];
        if (elseData) {
            const actions = elseData['actions'];
            if (actions) {
                for (let i = 0; i < actions.length; i++) {
                    const action = actions[i];
                    this.addAction(action, true);
                }
            }
        }

        const loopEnd = this.data['loopEnd'];
        if (loopEnd) {
            const actions = loopEnd.actions;
            if (actions) {
                for (let i = 0; i < actions.length; i++) {
                    const action = actions[i];
                    this.addLoopEndAction(action);
                }
            }
        }
    }

    initTopJoin(): void {
        const context = new ruleforge.Context(this.conditionContainer, this);
        this.join = new ruleforge.Join(context);
        this.join.init(null);
        this.join.initTopJoin(this.conditionContainer);
        this.join.setType('and');
    }

    private initCriterion(criterion?: Record<string, any>): void {
        const context = new ruleforge.Context(this.conditionContainer, this);
        this.join = new ruleforge.Join(context);
        this.join.init(null);
        this.join.initTopJoin(this.conditionContainer);
        let junctionType = 'and';
        if (criterion) {
            junctionType = criterion['junctionType'];
        }
        this.join.setType(junctionType);
        if (criterion) {
            this.join.initData(criterion);
        }
    }

    addProperty(property: any): void {
        this.propertyContainer.appendChild(property.getContainer());
        this.properties.push(property);
        window._setDirty?.();
    }

    private initRemark(): void {
        const remarkContainer = document.createElement('div');
        this.remark = new Remark(remarkContainer);
        this.ruleContainer.appendChild(remarkContainer);
    }

    private initHeader(): void {
        this.nameContainer = document.createElement('div');
        this.ruleContainer.appendChild(this.nameContainer);
        this.label = document.createElement('span');
        this.label.style.lineHeight = '30px';
        this.label.innerHTML = '<Strong>循环规则 <Strong>';
        this.nameContainer.appendChild(this.label);
        this.nameEditor = document.createElement('input');
        this.nameEditor.type = 'text';
        this.nameEditor.className = 'form-control rule-text-editor';
        this.nameEditor.style.display = 'none';
        this.nameLabel = document.createElement('span');
        this.nameLabel.textContent = this.name;
        this.label.appendChild(this.nameEditor);
        this.label.appendChild(this.nameLabel);
        const self = this;
        this.nameLabel.addEventListener('click', function () {
            self.nameLabel.style.display = 'none';
            self.nameEditor.style.display = '';
            self.nameEditor.value = self.name;
            self.nameEditor.focus();
        });

        this.nameEditor.addEventListener('blur', function () {
            self.name = self.nameEditor.value;
            self.nameEditor.style.display = 'none';
            self.nameLabel.style.display = '';
            self.nameLabel.textContent = self.name;
            window._setDirty?.();
        });
        this.nameEditor.style.display = 'none';
        const del = document.createElement('i');
        del.className = 'glyphicon glyphicon-remove rule-delete';
        del.style.verticalAlign = 'middle';
        del.style.cursor = 'pointer';
        del.addEventListener('click', function () {
            const msg = '真的要删除规则' + self.name + '？';
            MsgBox.confirm(msg, function () {
                const pos = self.parent.rules.indexOf(self);
                self.parent.rules.splice(pos, 1);
                self.container.remove();
                window._setDirty?.();
            });
        });

        this.nameContainer.appendChild(del);

        this.propertyContainer = document.createElement('span');
        this.propertyContainer.style.padding = '10px';

        const addProp = document.createElement('span');
        addProp.className = 'rule-add-property';
        addProp.textContent = '添加属性';
        const onClick = function (menuItem: MenuItemConfig) {
            const prop = new ruleforge.RuleProperty(self, menuItem.name, (menuItem as any).defaultValue, (menuItem as any).editorType);
            self.addProperty(prop);
        };
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '优先级',
                name: 'salience',
                onClick: onClick
            } as any, {
                label: '生效日期',
                name: 'effective-date',
                onClick: onClick
            } as any, {
                label: '失效日期',
                name: 'expires-date',
                onClick: onClick
            } as any, {
                label: '是否启用',
                name: 'enabled',
                onClick: onClick
            } as any, {
                label: '允许调试信息输出',
                name: 'debug',
                onClick: onClick
            } as any, {
                label: '互斥组',
                name: 'activation-group',
                onClick: onClick
            } as any, {
                label: '执行组',
                name: 'agenda-group',
                onClick: onClick
            } as any, {
                label: '自动获取焦点',
                name: 'auto-focus',
                onClick: onClick
            } as any]
        });
        addProp.addEventListener('click', function (e: Event) {
            self.menu.show(e as MouseEvent);
        });
        this.ruleContainer.appendChild(addProp);
        this.ruleContainer.appendChild(this.propertyContainer);
    }

    private initLoopStart(): void {
        this.loopStartLabel = document.createElement('span');
        this.loopStartLabel.innerHTML = '<strong>开始前动作</strong>';
        this.ruleContainer.appendChild(this.loopStartLabel);
        this.loopStartActionContainer = document.createElement('div');
        this.loopStartActionContainer.style.padding = '5px';
        const _this = this;
        Sortable.create(this.loopStartActionContainer, {
            delay: 200,
            onEnd: function (evt: Sortable.SortableEvent) {
                if (evt.oldIndex !== evt.newIndex) {
                    const children = _this.loopStartActionContainer.querySelectorAll('div');
                    children.forEach(function (div: Element, index: number) {
                        const id = (div as HTMLElement).id;
                        const actions = _this.loopStartActions;
                        let targetAction: any = null;
                        for (const action of actions) {
                            if (action.uuid === id) {
                                targetAction = action;
                                break;
                            }
                        }
                        if (targetAction) {
                            const pos = actions.indexOf(targetAction);
                            actions.splice(pos, 1);
                            actions.splice(index, 0, targetAction);
                        }
                    });
                    window._setDirty?.();
                }
            }
        });
        this.ruleContainer.appendChild(this.loopStartActionContainer);
        this.addLoopStartActionButton = document.createElement('span');
        this.addLoopStartActionButton.className = 'rule-add-action';
        this.addLoopStartActionButton.textContent = '添加动作';
        const self = this;
        this.addLoopStartActionButton.addEventListener('click', function () {
            self.addLoopStartAction();
        });
        this.loopStartLabel.appendChild(this.addLoopStartActionButton);
    }

    addLoopStartAction(data?: Record<string, any>): void {
        const self = this;
        const actionDiv = document.createElement('div');
        actionDiv.style.padding = '5px';
        const del = document.createElement('i');
        del.className = 'glyphicon glyphicon-remove rule-delete-action';
        actionDiv.appendChild(del);
        const action = new ruleforge.ActionType(actionDiv);
        del.addEventListener('click', function () {
            const pos = self.loopStartActions.indexOf(action);
            self.loopStartActions.splice(pos, 1);
            actionDiv.remove();
            window._setDirty?.();
        });
        this.loopStartActions.push(action);
        this.loopStartActionContainer.appendChild(actionDiv);
        if (data) {
            action.initData(data);
        }
        window._setDirty?.();
    }

    private initIf(): void {
        this.ifLabel = document.createElement('div');
        this.ifLabel.style.marginLeft = '5px';
        this.ifLabel.style.color = '#337ab7';
        this.ifLabel.innerHTML = '<strong>如果</strong>';
        this.ruleContainer.appendChild(this.ifLabel);
        this.conditionContainer = document.createElement('div');
        this.conditionContainer.style.marginLeft = '5px';
        this.conditionContainer.style.height = '40px';
        this.conditionContainer.style.position = 'relative';
        this.ruleContainer.appendChild(this.conditionContainer);
    }

    private initLoopTarget(): void {
        const loopTargetLabel = document.createElement('div');
        loopTargetLabel.innerHTML = '<strong>循环对象</strong>';
        this.ruleContainer.appendChild(loopTargetLabel);
        this.loopTargetContainer = document.createElement('div');
        this.loopTargetContainer.style.padding = '5px';
        this.ruleContainer.appendChild(this.loopTargetContainer);
        this.loopTargetInputType = new ruleforge.InputType();
        this.loopTargetContainer.appendChild(this.loopTargetInputType.getContainer());
    }

    private initThen(): void {
        this.thenLabel = document.createElement('span');
        this.thenLabel.style.marginLeft = '5px';
        this.thenLabel.style.color = '#337ab7';
        this.thenLabel.innerHTML = '<strong>那么</strong>';
        this.ruleContainer.appendChild(this.thenLabel);
        this.actionContainer = document.createElement('div');
        this.actionContainer.style.padding = '5px';
        this.ruleContainer.appendChild(this.actionContainer);

        const _this = this;
        Sortable.create(this.actionContainer, {
            delay: 200,
            onEnd: function (evt: Sortable.SortableEvent) {
                if (evt.oldIndex !== evt.newIndex) {
                    const children = _this.actionContainer.querySelectorAll('div');
                    children.forEach(function (div: Element, index: number) {
                        const id = (div as HTMLElement).id;
                        const actions = _this.actions;
                        let targetAction: any = null;
                        for (const action of actions) {
                            if (action.uuid === id) {
                                targetAction = action;
                                break;
                            }
                        }
                        if (targetAction) {
                            const pos = actions.indexOf(targetAction);
                            actions.splice(pos, 1);
                            actions.splice(index, 0, targetAction);
                        }
                    });
                    window._setDirty?.();
                }
            }
        });

        this.addActionButton = document.createElement('span');
        this.addActionButton.className = 'rule-add-action';
        this.addActionButton.textContent = '添加动作';
        const self = this;
        this.addActionButton.addEventListener('click', function () {
            self.addAction();
        });
        this.thenLabel.appendChild(this.addActionButton);
    }

    private initElse(): void {
        this.elseContainer = document.createElement('div');
        this.elseContainer.style.marginTop = '5px';
        this.ruleContainer.appendChild(this.elseContainer);
        this.elseLabel = document.createElement('span');
        this.elseLabel.style.marginLeft = '5px';
        this.elseLabel.style.color = '#337ab7';
        this.elseLabel.innerHTML = '<strong>否则</strong>';
        this.elseContainer.appendChild(this.elseLabel);
        this.elseActionContainer = document.createElement('div');
        this.elseActionContainer.style.padding = '5px';
        this.elseContainer.appendChild(this.elseActionContainer);

        const _this = this;
        Sortable.create(this.elseActionContainer, {
            delay: 200,
            onEnd: function (evt: Sortable.SortableEvent) {
                if (evt.oldIndex !== evt.newIndex) {
                    const children = _this.elseActionContainer.querySelectorAll('div');
                    children.forEach(function (div: Element, index: number) {
                        const id = (div as HTMLElement).id;
                        const actions = _this.elseActions;
                        let targetAction: any = null;
                        for (const action of actions) {
                            if (action.uuid === id) {
                                targetAction = action;
                                break;
                            }
                        }
                        if (targetAction) {
                            const pos = actions.indexOf(targetAction);
                            actions.splice(pos, 1);
                            actions.splice(index, 0, targetAction);
                        }
                    });
                    window._setDirty?.();
                }
            }
        });

        this.addElseActionButton = document.createElement('span');
        this.addElseActionButton.className = 'rule-add-action';
        this.addElseActionButton.textContent = '添加动作';
        const self = this;
        this.addElseActionButton.addEventListener('click', function () {
            self.addAction(null, true);
        });
        this.elseLabel.appendChild(this.addElseActionButton);
    }

    addAction(data?: Record<string, any> | null, iselse?: boolean): void {
        const self = this;
        const actionDiv = document.createElement('div');
        actionDiv.style.padding = '5px';
        const del = document.createElement('i');
        del.className = 'glyphicon glyphicon-remove rule-delete-action';
        actionDiv.appendChild(del);
        const action = new ruleforge.ActionType(actionDiv, this);
        del.addEventListener('click', function () {
            if (iselse) {
                const pos = self.elseActions.indexOf(action);
                self.elseActions.splice(pos, 1);
            } else {
                const pos = self.actions.indexOf(action);
                self.actions.splice(pos, 1);
            }
            actionDiv.remove();
            window._setDirty?.();
        });
        if (iselse) {
            this.elseActions.push(action);
            this.elseActionContainer.appendChild(actionDiv);
        } else {
            this.actions.push(action);
            this.actionContainer.appendChild(actionDiv);
        }
        if (data) {
            action.initData(data);
        }
        window._setDirty?.();
    }

    private initLoopEnd(): void {
        this.loopEndLabel = document.createElement('span');
        this.loopEndLabel.innerHTML = '<strong>结束后动作</strong>';
        this.ruleContainer.appendChild(this.loopEndLabel);
        this.loopEndActionContainer = document.createElement('div');
        this.loopEndActionContainer.style.padding = '5px';
        this.ruleContainer.appendChild(this.loopEndActionContainer);

        const _this = this;
        Sortable.create(this.loopEndActionContainer, {
            delay: 200,
            onEnd: function (evt: Sortable.SortableEvent) {
                if (evt.oldIndex !== evt.newIndex) {
                    const children = _this.loopEndActionContainer.querySelectorAll('div');
                    children.forEach(function (div: Element, index: number) {
                        const id = (div as HTMLElement).id;
                        const actions = _this.loopEndActions;
                        let targetAction: any = null;
                        for (const action of actions) {
                            if (action.uuid === id) {
                                targetAction = action;
                                break;
                            }
                        }
                        if (targetAction) {
                            const pos = actions.indexOf(targetAction);
                            actions.splice(pos, 1);
                            actions.splice(index, 0, targetAction);
                        }
                    });
                    window._setDirty?.();
                }
            }
        });

        this.addLoopEndActionButton = document.createElement('span');
        this.addLoopEndActionButton.className = 'rule-add-action';
        this.addLoopEndActionButton.textContent = '添加动作';
        const self = this;
        this.addLoopEndActionButton.addEventListener('click', function () {
            self.addLoopEndAction();
        });
        this.loopEndLabel.appendChild(this.addLoopEndActionButton);
    }

    addLoopEndAction(data?: Record<string, any>): void {
        const self = this;
        const actionDiv = document.createElement('div');
        actionDiv.style.padding = '5px';
        const del = document.createElement('i');
        del.className = 'glyphicon glyphicon-remove rule-delete-action';
        actionDiv.appendChild(del);
        const action = new ruleforge.ActionType(actionDiv);
        del.addEventListener('click', function () {
            const pos = self.loopEndActions.indexOf(action);
            self.loopEndActions.splice(pos, 1);
            actionDiv.remove();
            window._setDirty?.();
        });
        this.loopEndActions.push(action);
        this.loopEndActionContainer.appendChild(actionDiv);
        if (data) {
            action.initData(data);
        }
        window._setDirty?.();
    }

    toXml(): string {
        let xml = '<loop-rule name="' + this.name + '"';
        for (let i = 0; i < this.properties.length; i++) {
            const prop = this.properties[i];
            xml += ' ' + prop.toXml();
        }
        xml += '>';
        xml += this.remark.toXml();
        const loopTargetValue = this.loopTargetInputType.toXml();
        if (loopTargetValue === '') {
            throw new Error('循环规则的【循环对象】必须要定义');
        }
        xml += '<loop-target>' + loopTargetValue + '</loop-target>';
        if (this.loopStartActions.length > 0) {
            xml += '<loop-start>';
            for (let i = 0; i < this.loopStartActions.length; i++) {
                const action = this.loopStartActions[i];
                xml += action.toXml();
            }
            xml += '</loop-start>';
        }
        xml += '<if>';
        xml += this.join.toXml();
        xml += '</if>';
        xml += '<then>';
        for (let i = 0; i < this.actions.length; i++) {
            const action = this.actions[i];
            xml += action.toXml();
        }
        xml += '</then>';
        xml += '<else>';
        for (let i = 0; i < this.elseActions.length; i++) {
            const action = this.elseActions[i];
            xml += action.toXml();
        }
        xml += '</else>';
        if (this.loopEndActions.length > 0) {
            xml += '<loop-end>';
            for (let i = 0; i < this.loopEndActions.length; i++) {
                const action = this.loopEndActions[i];
                xml += action.toXml();
            }
            xml += '</loop-end>';
        }
        xml += '</loop-rule>';
        return xml;
    }
}

(ruleforge as any).LoopRule = LoopRule;
