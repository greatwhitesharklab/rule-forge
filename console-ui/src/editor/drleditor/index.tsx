/**
 * V5.78.3 — DRL 4 编辑器 React 入口(Monaco 版)。
 *
 * <p>V5.45.3 CodeMirror 5 prototype → V5.78.3 Monaco + IDE providers
 * (autocomplete / hover / diagnostics 红线)。布局不变:
 * <ul>
 *   <li>顶部 toolbar:文件路径 + "Source format: DRL 4" badge + 保存按钮 +
 *       "保存新版本" 按钮(走现有 save + saveNewVersion API)</li>
 *   <li>主区:Monaco editor,DRL 4 Monarch syntax highlight + IDE features</li>
 *   <li>侧栏:`imports` 列表 + `ruleNames` 列表(从 {@link loadDrlFile} 返的
 *       payload 拿)</li>
 * </ul>
 *
 * <p>dirty tracking:Monaco onChange 事件 → {@link useDirty} 通知 frame。
 *
 * <p>V5.78.3 新增(原 V5.45.3 deferred):
 * <ul>
 *   <li>live syntax-error diagnostics(走 /api/ide/parse,300ms debounce)</li>
 *   <li>autocomplete(走 /api/ide/complete,keyword + declared field)</li>
 *   <li>hover popup(走 /api/ide/hover,markdown)</li>
 * </ul>
 *
 * @since 5.78
 */
import '../../css/tailwind-base.css';

import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Button, Space, Tag, message } from 'antd';
import { loadDrlFile, type DrlFilePayload } from '../../api/drlEditor';
import { DEFAULT_DIALECT } from '../../api/drlDialect';
import { save, saveNewVersion } from '../../api/client';
import { getParameter } from '../../Utils';
import { DrlMonaco } from './DrlMonaco';
import {useDirty} from '../../editor/EditorContexts';
import EditorLoadState from '../EditorLoadState';

const DrlEditor: React.FC<{ file: string }> = ({ file }) => {
    const dirty = useDirty();
    const [payload, setPayload] = useState<DrlFilePayload | null>(null);
    // 保留原始错误对象,交给 EditorLoadState 格式化 —— loadDrlFile 失败时 reject 的是
    // 原始 Response,String(e) 会显示成 "加载失败: [object Response]"(走查已暴露)。
    const [error, setError] = useState<unknown>(null);
    // 重试计数:错误态点"重试"时 +1,触发 useEffect 重新加载
    const [reloadKey, setReloadKey] = useState(0);
    // 当前 Monaco 内容(给 save 用)
    const currentContentRef = useRef<string>('');

    // 阶段 1:加载文件
    useEffect(() => {
        let cancelled = false;
        loadDrlFile(file)
            .then(p => {
                if (cancelled) return;
                setPayload(p);
                currentContentRef.current = p.content;
            })
            .catch(e => {
                if (cancelled) return;
                setError(e);
            });
        return () => {
            cancelled = true;
        };
    }, [file, reloadKey]);

    const handleContentChange = (value: string) => {
        currentContentRef.current = value;
        dirty.setDirty();
    };

    const handleSave = async () => {
        if (!file) return;
        const content = currentContentRef.current;
        try {
            await save(`/project${file}`, {
                content: encodeURIComponent(content),
                file,
                newVersion: 'false',
            });
            dirty.clearDirty();
            message.success('保存成功');
        } catch (e) {
            message.error('保存失败: ' + String(e));
        }
    };

    const handleSaveNewVersion = async () => {
        if (!file) return;
        const content = currentContentRef.current;
        try {
            await saveNewVersion(`/project${file}`, {
                content: encodeURIComponent(content),
                file,
            });
            dirty.clearDirty();
            message.success('已保存新版本');
        } catch (e) {
            message.error('保存新版本失败: ' + String(e));
        }
    };

    if (error) {
        return (
            <EditorLoadState
                status="error"
                error={error}
                onRetry={() => {
                    setError(null);
                    setReloadKey(k => k + 1);
                }}
            />
        );
    }
    if (!payload) {
        return <EditorLoadState status="loading"/>;
    }

    return (
        <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
            {/* 顶部 toolbar */}
            <div style={{
                padding: '8px 16px',
                borderBottom: '1px solid #d9d9d9',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
            }}>
                <Space>
                    <span style={{ fontWeight: 500 }}>{file}</span>
                    <Tag color="blue">Source format: {DEFAULT_DIALECT}</Tag>
                </Space>
                <Space>
                    <Button type="primary" onClick={handleSave}>保存</Button>
                    <Button onClick={handleSaveNewVersion}>保存新版本</Button>
                </Space>
            </div>

            {/* 主区 + 侧栏 */}
            <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
                <div style={{ flex: 1, overflow: 'hidden' }}>
                    <DrlMonaco
                        initialValue={payload.content}
                        onChange={handleContentChange}
                    />
                </div>
                <div style={{
                    width: 240,
                    borderLeft: '1px solid #d9d9d9',
                    padding: 12,
                    overflow: 'auto',
                }}>
                    <h4 style={{ margin: '0 0 8px' }}>Imports ({payload.imports.length})</h4>
                    <ul style={{ paddingLeft: 16, margin: 0 }}>
                        {payload.imports.map(p => <li key={p}>{p}</li>)}
                    </ul>
                    <h4 style={{ margin: '16px 0 8px' }}>Rules ({payload.ruleNames.length})</h4>
                    <ul style={{ paddingLeft: 16, margin: 0 }}>
                        {payload.ruleNames.map(n => <li key={n}>{n}</li>)}
                    </ul>
                </div>
            </div>
        </div>
    );
};

// 入口挂载(legacy 模式保留 — 仅当页面存在 #container 时才挂载;SPA 路由走 EditorRoute 走 default export)
const file = getParameter('file');
// 注:window._project 不再设置,改由 EditorRoute 通过 ProjectContext 提供。

const root = document.getElementById('container');
// 无 file 参数时不挂载编辑器(SPA 路由由 EditorRoute 渲染引导空态;legacy 页面直接留白)
if (root && file) {
    createRoot(root).render(<DrlEditor file={file} />);
}

export default DrlEditor;
