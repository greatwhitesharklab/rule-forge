/**
 * 编辑器统一加载/错误/空态组件(V7 SPA 走查整改)。
 *
 * <p>所有 SPA 编辑器路由(/app/editor/*)共用三种边界状态:
 * <ul>
 *   <li>{@code no-file} — URL 无 ?file= 参数:引导空态(antd Empty),提示从文件树打开</li>
 *   <li>{@code loading} — 文件加载中:Spin</li>
 *   <li>{@code error} — 加载失败:antd Result 错误态,原因经 {@link formatLoadError} 格式化
 *       (HTTP status / 后端 message),可带重试按钮</li>
 * </ul>
 *
 * <p>历史问题:各编辑器直接 {@code String(err)} 渲染,而 api/client.ts 在非 2xx 时
 * reject 原始 {@link Response} 对象,页面显示 "加载失败: [object Response]"。
 * 统一走本组件后禁止再出现该形态。
 */
import React from 'react';
import {Button, Empty, Result, Spin} from 'antd';

/** 编辑器加载状态枚举。 */
export type EditorLoadStatus = 'no-file' | 'loading' | 'error';

export interface EditorLoadStateProps {
    status: EditorLoadStatus;
    /** status === 'error' 时的原始错误对象(Response / Error / 后端 envelope 均可,自动格式化) */
    error?: unknown;
    /** status === 'error' 时的重试回调;不传则不显示重试按钮 */
    onRetry?: () => void;
    /** no-file 引导文案(默认提示从主界面文件树打开) */
    emptyDescription?: React.ReactNode;
    /** error 标题(默认 "加载失败") */
    errorTitle?: string;
}

/**
 * 把任意加载错误格式化成用户可读的字符串。
 *
 * <p>优先级:Response(HTTP status + statusText)→ 带 message 的对象(后端
 * {@code {status:false,message}} envelope)→ Error.message → string → JSON。
 */
export function formatLoadError(err: unknown): string {
    if (err === null || err === undefined) {
        return '未知错误';
    }
    // api/client.ts 在非 2xx 时 reject 原始 Response —— 必须取 HTTP 状态,
    // 否则 String(response) 会显示成 "[object Response]"。
    if (err instanceof Response) {
        const statusText = err.statusText ? ' ' + err.statusText : '';
        return 'HTTP ' + err.status + statusText;
    }
    // 后端 save/load envelope:{status: false, message: "..."} — 直接展示后端原因。
    if (typeof err === 'object' && 'message' in err && typeof (err as {message?: unknown}).message === 'string') {
        return (err as {message: string}).message;
    }
    if (err instanceof Error) {
        return err.message || String(err);
    }
    if (typeof err === 'string') {
        return err;
    }
    try {
        return JSON.stringify(err);
    } catch {
        return String(err);
    }
}

const containerStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flex: 1,
    minHeight: 320,
    height: '100%',
};

const EditorLoadState: React.FC<EditorLoadStateProps> = ({
    status,
    error,
    onRetry,
    emptyDescription,
    errorTitle,
}) => {
    if (status === 'loading') {
        return (
            <div style={containerStyle} data-testid="editor-load-state">
                <Spin size="large"/>
                <span style={{marginLeft: 12, color: '#666'}}>加载中…</span>
            </div>
        );
    }
    if (status === 'no-file') {
        return (
            <div style={containerStyle} data-testid="editor-load-state">
                <Empty
                    description={emptyDescription ??
                        '未指定要编辑的文件 — 请从主界面文件树选择文件,打开对应编辑器。'}
                />
            </div>
        );
    }
    return (
        <div style={containerStyle} data-testid="editor-load-state">
            <Result
                status="error"
                title={errorTitle ?? '加载失败'}
                subTitle={formatLoadError(error)}
                extra={onRetry
                    ? [<Button key="retry" type="primary" onClick={onRetry}>重试</Button>]
                    : undefined}
            />
        </div>
    );
};

export default EditorLoadState;
