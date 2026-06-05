/**
 * 轻量 Antd Modal 封装 — 替代老 bootbox。
 *
 * 区别:这个不是 bootbox 兼容层。这是把 Antd Modal / message 包成简单的
 * `alert(msg)` / `confirm(msg, cb)` / `prompt(msg, cb)` 形式,call site 保持
 * 一行,内部走 Antd + Input。
 *
 * 用法:
 *   import {alert, confirm, prompt} from '../utils/modal';
 *   alert('保存成功');
 *   confirm('确认删除?', (ok) => ok && doDelete());
 *   prompt('请输入名称', (name) => name && doCreate(name));
 */
import React, {useState} from 'react';
import {Modal, Input, message as antdMessage, App} from 'antd';
import {createRoot} from 'react-dom/client';

let appMessageApi: ReturnType<typeof App.useApp>['message'] | null = null;

/**
 * 让 React 组件树内部的 App context 把 message API 传过来。
 * 没有传的话,降级用 static `antdMessage` (也能用,只是没有 AntdProvider 主题)。
 */
export function bindMessageApi(api: ReturnType<typeof App.useApp>['message']) {
    appMessageApi = api;
}

function notify(level: 'info' | 'success' | 'warning' | 'error', msg: string) {
    if (appMessageApi) {
        appMessageApi[level](msg);
    } else {
        antdMessage[level](msg);
    }
}

/**
 * 信息提示 — 走 Antd message (顶部 toast) 而不是 Modal (更轻量)。
 * 调用方语法跟 bootbox.alert 一致: alert(msg, cb?)
 */
export function alert(msg: string, callback?: () => void): void {
    if (!msg) {
        if (callback) callback();
        return;
    }
    // 错误 / 失败 / 异常 走 error 样式,其它走 info
    const isError = /失败|错误|异常|出错|Error|Failed|Exception/i.test(msg);
    if (isError) {
        notify('error', msg);
    } else {
        notify('info', msg);
    }
    if (callback) callback();
}

/**
 * 确认弹窗 — Antd Modal.confirm 包装成 callback 形式 (跟 bootbox 一致)
 */
export function confirm(msg: string, callback: (result: boolean) => void): void {
    Modal.confirm({
        title: '确认',
        content: msg,
        okText: '确定',
        cancelText: '取消',
        onOk: () => {
            callback(true);
        },
        onCancel: () => {
            callback(false);
        },
    });
}

/**
 * 自定义弹窗 — 把字符串 HTML 塞进 Antd Modal 渲染。
 * 替代 `bootbox.dialog({title, message, onhide, ...})`。
 */
export interface DialogOptions {
    title?: string;
    message: string; // HTML 字符串
    onhide?: () => void;
    callback?: () => void; // 任意按钮/关闭时调用 (兼容 bootbox.dialog callback)
    buttons?: Record<string, {label?: string; className?: string; callback?: () => void}>;
    closeButton?: boolean;
    size?: 'small' | 'large' | string; // 兼容 bootbox.dialog size
}

export function dialog(options: DialogOptions): {close: () => void} {
    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);
    let closeFn: () => void = () => {};

    const Dialog: React.FC = () => {
        const [open, setOpen] = useState(true);
        const close = () => {
            setOpen(false);
            if (options.onhide) options.onhide();
            if (options.callback) options.callback();
            setTimeout(() => {
                root.unmount();
                if (document.body.contains(container)) {
                    document.body.removeChild(container);
                }
            }, 200);
        };
        closeFn = close;

        const buttonEntries = options.buttons ? Object.entries(options.buttons) : [];
        return (
            <Modal
                title={options.title}
                open={open}
                footer={buttonEntries.length > 0 ? buttonEntries.map(([key, btn]) => (
                    <button
                        key={key}
                        className={`btn ${btn.className || 'btn-default'}`}
                        onClick={() => {
                            if (btn.callback) btn.callback();
                            close();
                        }}
                    >
                        {btn.label || key}
                    </button>
                )) : undefined}
                onCancel={close}
            >
                <div dangerouslySetInnerHTML={{__html: options.message}}/>
            </Modal>
        );
    };

    root.render(<Dialog/>);
    return {close: () => closeFn()};
}

/**
 * 输入弹窗 — Antd 没有原生 prompt,自己组装一个 Modal + Input。
 * callback 收到 string (用户输入的值) 或 null (用户取消)。
 */
export function prompt(msg: string, callback: (result: string | null) => void): void {
    // Antd Modal.confirm 不能动态控制 Input value,所以单独建一个 Modal 实例
    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);

    // 闭包内唯一 value — 避免连续触发 prompt 共享模块级变量导致竞态
    let value = '';
    // 防重入:已触发关闭后,Antd onOk 还会二次 fire (Modal 的 onOk + Input 的 onPressEnter 都会走),
    // 用 closed 标志位防止 callback 重复调用
    let closed = false;
    const finish = (result: string | null) => {
        if (closed) return;
        closed = true;
        // 先 unmount React,再延迟一帧删 DOM — 否则 React 还在 reconciliation 阶段
        // 突然没了父节点会抛 "removeChild" 警告
        root.unmount();
        setTimeout(() => {
            if (document.body.contains(container)) {
                document.body.removeChild(container);
            }
            callback(result);
        }, 0);
    };

    const PromptModal: React.FC = () => {
        const [open, setOpen] = useState(true);
        return (
            <Modal
                title="请输入"
                open={open}
                okText="确定"
                cancelText="取消"
                onOk={() => {
                    setOpen(false);
                    finish(value);
                }}
                onCancel={() => {
                    setOpen(false);
                    finish(null);
                }}
            >
                <p style={{marginBottom: 8}}>{msg}</p>
                <Input
                    autoFocus
                    defaultValue=""
                    onChange={(e) => {
                        value = e.target.value;
                    }}
                    onPressEnter={(e) => {
                        const v = (e.target as HTMLInputElement).value;
                        setOpen(false);
                        finish(v);
                    }}
                />
            </Modal>
        );
    };

    root.render(<PromptModal/>);
}
