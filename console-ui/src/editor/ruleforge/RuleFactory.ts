import Sortable from 'sortablejs';
import { constantLibraries, variableLibraries, parameterLibraries, actionLibraries } from '../common/URule';
import { Remark } from '../../Remark.js';

declare const ruleforge: any;

export class RuleFactory {
    container: HTMLElement & { _dirty?: boolean; rules: any[]; remark?: any };
    file: string | null = null;

    constructor(container: HTMLElement) {
        this.container = container as HTMLElement & { _dirty?: boolean; rules: any[]; remark?: any };
        this.container._dirty = false;
        this.container.rules = [];

        const remarkContainer = document.createElement('div');
        remarkContainer.style.margin = '5px';
        remarkContainer.style.padding = '5px';
        container.appendChild(remarkContainer);
        this.container.remark = new Remark(remarkContainer);

        const _this = container as HTMLElement & { rules: any[] };
        Sortable.create(container, {
            delay: 200,
            onEnd: function (evt: Sortable.SortableEvent) {
                if (evt.oldIndex !== evt.newIndex) {
                    const children = _this.querySelectorAll('div');
                    children.forEach(function (div: Element, index: number) {
                        const id = (div as HTMLElement).id;
                        const rules = _this.rules;
                        let targetRule: any = null;
                        for (const rule of rules) {
                            if (rule.uuid === id) {
                                targetRule = rule;
                                break;
                            }
                        }
                        if (targetRule) {
                            const pos = rules.indexOf(targetRule);
                            rules.splice(pos, 1);
                            rules.splice(index, 0, targetRule);
                        }
                    });
                    window._setDirty?.();
                }
            }
        });
    }

    setFile(file: string): void {
        this.file = file;
    }

    addRule(data?: Record<string, any>): any {
        const self = this.container;
        const ruleContainer = document.createElement('div');
        ruleContainer.className = 'well';
        ruleContainer.style.margin = '5px';
        ruleContainer.style.padding = '8px';
        ruleContainer.style.backgroundColor = '#fdfdfd';
        self.appendChild(ruleContainer);
        const rule = new ruleforge.Rule(self, ruleContainer, data);
        self.rules.push(rule);
        window._setDirty?.();
        return rule;
    }

    addLoopRule(data?: Record<string, any>): any {
        const self = this.container;
        const ruleContainer = document.createElement('div');
        ruleContainer.className = 'well';
        ruleContainer.style.margin = '5px';
        ruleContainer.style.padding = '8px';
        ruleContainer.style.borderColor = '#337AB7';
        ruleContainer.style.backgroundColor = '#fdfdfd';
        self.appendChild(ruleContainer);
        const rule = new ruleforge.LoopRule(self, ruleContainer, data);
        self.rules.push(rule);
        window._setDirty?.();
        return rule;
    }

    toXml(): string {
        const self = this.container;
        let xml = '<?xml version="1.0" encoding="UTF-8"?>';
        xml += '<rule-set>';
        parameterLibraries.forEach(function (item: string) {
            xml += '<import-parameter-library path="' + item + '"/>';
        });
        variableLibraries.forEach(function (item: string) {
            xml += '<import-variable-library path="' + item + '"/>';
        });
        constantLibraries.forEach(function (item: string) {
            xml += '<import-constant-library path="' + item + '"/>';
        });
        actionLibraries.forEach(function (item: string) {
            xml += '<import-action-library path="' + item + '"/>';
        });
        xml += self.remark!.toXml();
        for (let i = 0; i < self.rules.length; i++) {
            xml += self.rules[i].toXml();
        }
        xml += '</rule-set>';
        return xml;
    }

    loadData(ruleset: Record<string, any>): void {
        const self = this.container;
        self.remark!.setData(ruleset['remark']);
        const rules = ruleset['rules'];
        for (let i = 0; i < rules.length; i++) {
            const rule = rules[i];
            if (rule.loopRule) {
                this.addLoopRule(rule);
            } else {
                this.addRule(rule);
            }
        }
    }
}

(window as any).RuleFactory = RuleFactory;
