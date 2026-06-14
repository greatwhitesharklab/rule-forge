/**
 * Collapsible remark widget.
 *
 * Renders a toggle toolbar + editable textarea inside the given container.
 */

export class Remark {
    private remark: string;
    private readonly defaultRemark: string;
    private readonly container: HTMLElement;
    private _collapsed: boolean;
    private icon!: HTMLElement;
    private contentContainer!: HTMLElement;
    private remarkLabel!: HTMLElement;
    private remarkEditor!: HTMLTextAreaElement;

    // Inline SVGs (AntD RightOutlined / DownOutlined paths) — replaced bootstrap
    // glyphicon-circle-arrow-right/down icons after bootstrap removal.
    private static readonly RIGHT_ARROW_SVG =
        '<svg viewBox="64 64 896 896" width="1em" height="1em" fill="currentColor" aria-hidden="true"><path d="M765.7 486.8L314.9 134.7A7.97 7.97 0 00302 141v77.3c0 4.9 2.3 9.6 6.1 12.6l360 281.1-360 281.1c-3.9 3-6.1 7.7-6.1 12.6V883c0 6.7 7.7 10.4 12.9 6.3l450.8-352.1a31.96 31.96 0 000-50.4z"/></svg>';
    private static readonly DOWN_ARROW_SVG =
        '<svg viewBox="64 64 896 896" width="1em" height="1em" fill="currentColor" aria-hidden="true"><path d="M884 256h-75c-5.1 0-9.9 2.5-12.9 6.6L512 654.2 227.9 262.6c-3-4.1-7.8-6.6-12.9-6.6h-75c-6.5 0-10.3 7.4-6.5 12.7l352.6 486.1c12.8 17.6 39 17.6 51.7 0l352.6-486.1c3.9-5.3.1-12.7-6.4-12.7z"/></svg>';

    constructor(container: HTMLElement) {
        this.remark = '';
        this.defaultRemark = '请输入备注内容';
        this.container = container;
        this._collapsed = true;
        this._buildUI();
    }

    private _buildUI(): void {
        const container = this.container;

        const toolbar = document.createElement('div');
        // V5.9.0 a11y: #777 (4.1:1 on #f5f5f5) → #595959 (7.5:1) 4.5:1 达标
        toolbar.style.cssText = 'cursor:pointer;color:#595959;font-size:12px';
        toolbar.textContent = '备注';

        this.icon = document.createElement('i');
        this.icon.className = 'rf-remark-toggle-icon';
        this.icon.innerHTML = Remark.RIGHT_ARROW_SVG;
        toolbar.appendChild(this.icon);

        toolbar.addEventListener('click', () => {
            this._collapsed = !this._collapsed;
            if (this._collapsed) {
                this.contentContainer.style.display = 'none';
                this.icon.innerHTML = Remark.RIGHT_ARROW_SVG;
            } else {
                this.contentContainer.style.display = '';
                this.icon.innerHTML = Remark.DOWN_ARROW_SVG;
            }
        });
        container.appendChild(toolbar);

        this.contentContainer = document.createElement('div');
        this.contentContainer.style.display = 'none';
        container.appendChild(this.contentContainer);

        this.remarkLabel = document.createElement('div');
        this.remarkLabel.style.cssText = 'color:#999;background: #fdfdfd;padding:5px;border:solid 1px #ddd;border-radius: 5px;font-size: 12px';
        this.remarkLabel.textContent = this.defaultRemark;

        this.remarkLabel.addEventListener('click', () => {
            this.remarkEditor.style.display = '';
            this.remarkEditor.focus();
            this.remarkLabel.style.display = 'none';
        });
        this.contentContainer.appendChild(this.remarkLabel);

        this.remarkEditor = document.createElement('textarea');
        this.remarkEditor.className = 'rf-form-control';
        this.remarkEditor.rows = 4;
        this.remarkEditor.value = this.defaultRemark;
        this.remarkEditor.style.display = 'none';

        this.remarkEditor.addEventListener('change', function (this: HTMLTextAreaElement) {
            Remark_instance.remark = this.value;
            if (Remark_instance.remark === '') {
                Remark_instance.remarkLabel.textContent = Remark_instance.defaultRemark;
            } else {
                Remark_instance.remarkLabel.innerHTML = Remark_instance.parseBreak(Remark_instance.remark);
            }
            if (window.setDirty) {
                window.setDirty(true);
            }
            if (window._setDirty) {
                window._setDirty();
            }
        });
        this.remarkEditor.addEventListener('blur', () => {
            this.remarkEditor.style.display = 'none';
            this.remarkLabel.style.display = '';
        });
        this.contentContainer.appendChild(this.remarkEditor);

        // Alias for closure reference
        const Remark_instance = this;
    }

    setData(data: string): void {
        if (!data || data === '') {
            return;
        }
        this.remark = data;
        this.remarkEditor.value = data;
        this.remarkLabel.innerHTML = this.parseBreak(data);
    }

    toXml(): string {
        return '<remark><![CDATA[' + this.remark + ']]></remark>';
    }

    private parseBreak(data: string): string {
        data = data.replace(new RegExp('<', 'gm'), '&lt;');
        data = data.replace(new RegExp('>', 'gm'), '&gt;');
        data = data.replace(new RegExp('\n', 'gm'), '</br>');
        return data;
    }
}

// Backward-compatible global
(window as unknown as Record<string, unknown>).Remark = Remark;
