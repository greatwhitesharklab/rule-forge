import * as event from '../../components/componentEvent.js';
import { httpGet } from '../../api/client.js';

export class ConditionListDialog {
    project: string;
    category: string;
    colData: any;
    variable: string = '';

    constructor(project: string, category: string, colData: any) {
        this.project = project;
        this.category = category;
        this.colData = colData;
    }

    open(doSuccess: (condition: string) => void): void {
        const self = this;
        const url = 'ruleforge?action=loadcommonconditions&project=' + this.project + '&category=' + this.category + '&variable=' + this.colData.variableCategory + '.' + this.colData.variableLabel;
        httpGet(url).then(function (data: any) {
            self.variable = self.colData.variableCategory + '.' + self.colData.variableLabel;
            event.eventEmitter.emit(event.OPEN_CONDITION_LIST_DIALOG, {
                variable: self.variable,
                data: data || [],
                callback: doSuccess
            });
        }).catch(function () {
            RuleForge.alert('加载常用条件失败');
        });
    }

    setOption(_option: any): void {
        // Dialog options are now handled by the React component
    }

    refresh(project?: string, category?: string): void {
        event.eventEmitter.emit(event.REFRESH_CONDITION_LIST_DIALOG, {
            project: project || this.project,
            category: category || this.category,
            variable: this.colData.variableCategory + '.' + this.colData.variableLabel
        });
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).ConditionListDialog = ConditionListDialog;
