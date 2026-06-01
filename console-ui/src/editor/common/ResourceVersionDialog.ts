import * as event from '../../components/componentEvent.js';
import { httpGet } from '../../api/client.js';

export class ResourceVersionDialog {
    path: string;

    constructor(path: string) {
        this.path = path;
    }

    open(doSuccess: (file: string) => void): void {
        const self = this;
        const url = 'ruleforge?action=loadversion&file=' + this.path;
        httpGet(url).then(function (data: any) {
            event.eventEmitter.emit(event.OPEN_RESOURCE_VERSION_DIALOG, {
                path: self.path,
                data: data || [],
                callback: doSuccess
            });
        }).catch(function () {
            RuleForge.alert('加载版本信息失败');
        });
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).ResourceVersionDialog = ResourceVersionDialog;
