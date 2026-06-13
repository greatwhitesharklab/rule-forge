/**
 * V5.45.3 — DRL CodeMirror 5 包装器。
 *
 * <p>thin wrapper:把 {@link drlStreamLanguage} 装到 CodeMirror 5 实例上,暴露
 * {@link getValue} / {@link setValue} / {@link onChange} 三个 API 供 React 层用。
 *
 * <p>CodeMirror 5 文档:https://codemirror.net/5/doc/manual.html
 * (用 5 跟 console-ui scriptdecisiontable 同款,V5.45.3 plan 决定;
 *  V5.46+ 可升级到 CodeMirror 6 重写,本类替换为 CodeMirror 6 包装即可)
 *
 * @since 5.45
 */
import CodeMirror from 'codemirror';
import { drlStreamLanguage } from './drlStreamLanguage';

// CodeMirror 5 StreamLanguage 注册 — V5.45.3 拿到的 export 类型是函数定义。
// 'mymode' 是 mode 名称,CodeMirror 5 用 string 名字引用,这里用 DRL mode 名。
let modeRegistered = false;
function ensureModeRegistered(): void {
    if (modeRegistered) return;
    // StreamLanguage 接受 stream-parser factory,装到 mime type 'text/x-drl'
    // — 5.x 的 StreamLanguage.defineString 模式
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const CM5: any = CodeMirror;
    if (typeof CM5.defineSimpleMode === 'function') {
        // 走 simpleMode 走法失败(StreamLanguage vs SimpleMode 不同)
        // 回退到 StreamLanguage 注册
    }
    if (CM5.StringStream && typeof CM5.StreamLanguage === 'function') {
        // 通用注册:StreamLanguage 把 factory 包成可被 mode 字段引用的对象
        const modeObj = CM5.StreamLanguage(drlStreamLanguage);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (CodeMirror as any).modes = (CodeMirror as any).modes || {};
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (CodeMirror as any).modes['text/x-drl'] = modeObj;
    }
    modeRegistered = true;
}

export class DrlCodeMirror {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    private readonly editor: any;

    constructor(parent: HTMLElement, initialValue: string) {
        ensureModeRegistered();
        this.editor = new (CodeMirror as unknown as new (
            parent: HTMLElement,
            options: object
        ) => { getValue(): string; setValue(s: string): void; on(ev: string, cb: () => void): void; refresh(): void })(
            parent,
            {
                value: initialValue,
                mode: 'text/x-drl',
                lineNumbers: true,
                indentUnit: 4,
                tabSize: 4,
                lineWrapping: true,
            }
        );
    }

    getValue(): string {
        return this.editor.getValue();
    }

    setValue(content: string): void {
        this.editor.setValue(content);
    }

    /**
     * 监听内容变化。
     * @param cb 内容变化时触发,参数是当前完整内容
     */
    onChange(cb: (value: string) => void): void {
        this.editor.on('change', () => cb(this.editor.getValue()));
    }

    /** 同步挂载(CodeMirror 5 lazy render,某些操作需要先 refresh) */
    refresh(): void {
        this.editor.refresh();
    }
}
