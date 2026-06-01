import * as event from '../../components/componentEvent.js';

export class ResourceListDialog {
    type: string;
    select: HTMLElement | null;
    doSuccess: ((type: string, fullPath: string) => void) | null;

    constructor(type: string, select?: HTMLElement | null, doSuccess?: ((type: string, fullPath: string) => void) | null) {
        this.type = type;
        this.select = select || null;
        this.doSuccess = doSuccess || null;
    }

    open(): void {
        const self = this;
        event.eventEmitter.emit(event.OPEN_RESOURCE_LIST_DIALOG, {
            type: this.type,
            file: this.getRequestParameter('file'),
            callback: function (type: string, fullPath: string) {
                if (self.doSuccess) {
                    self.doSuccess(type, fullPath);
                } else {
                    self.doSelectFile(fullPath);
                }
            }
        });
    }

    doSelectFile(selectedFile: string): void {
        const fullPath = 'jcr:' + selectedFile;
        if (this.doSuccess) {
            this.doSuccess(this.type, fullPath);
            return;
        }
        let dup = false;
        if (this.select) {
            this.select.childNodes.forEach(function (node) {
                const path = (node as HTMLElement).textContent;
                if (path === fullPath) {
                    dup = true;
                }
            });
        }
        if (!dup) {
            const self = this;
            const item = document.createElement('a');
            item.href = 'javascript:void(0)';
            item.className = 'list-group-item';
            item.textContent = fullPath;
            item.addEventListener('click', function () {
                if (self.select) {
                    self.select.querySelectorAll('.active').forEach(function (el) {
                        el.classList.remove('active');
                    });
                }
                item.classList.add('active');
            });
            if (this.select) {
                this.select.appendChild(item);
            }
        } else {
            RuleForge.alert('当前库文件已被添加！');
        }
    }

    getRequestParameter(name: string): string | null {
        let value: string | null = null;
        const params = window.location.search.substring(1).split('&');
        for (let i = 0; i < params.length; i++) {
            const param = params[i];
            if (param.indexOf('=') === -1) {
                continue;
            }
            const pair = param.split('=');
            const key = pair[0];
            if (key === name) {
                value = pair[1];
                break;
            }
        }
        return value;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).ResourceListDialog = ResourceListDialog;
