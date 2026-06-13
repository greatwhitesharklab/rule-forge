/**
 * V5.45.3 — DRL 4 编辑器 React 入口。
 *
 * <p>布局:
 * <ul>
 *   <li>顶部 toolbar:文件路径 + "Source format: DRL 4" badge + 保存按钮 +
 *       "保存新版本" 按钮(走现有 save + saveNewVersion API)</li>
 *   <li>主区:CodeMirror 5 text editor,DRL 4 syntax highlight</li>
 *   <li>侧栏:`imports` 列表 + `ruleNames` 列表(从 {@link loadDrlFile} 返的
 *       payload 拿)</li>
 * </ul>
 *
 * <p>dirty tracking:CodeMirror `change` 事件 → {@link window._setDirty} 通知 frame。
 *
 * <p>不做(显式 scope-out,留 V5.46+):
 * <ul>
 *   <li>live syntax-error reporting(grammar error offset 暂未序列化到响应)</li>
 *   <li>cross-navigation 钩子(ruleset-editor 切到 DRL mode 按钮)</li>
 *   <li>version selector UI(用现有 saveNewVersion)</li>
 *   <li>多人协作</li>
 * </ul>
 *
 * @since 5.45
 */
import '../../../node_modules/codemirror/lib/codemirror.css';
import '../../css/tailwind-base.css';

import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Button, Space, Tag, message } from 'antd';
import { loadDrlFile, type DrlFilePayload } from '../../api/drlEditor';
import { DEFAULT_DIALECT } from '../../api/drlDialect';
import { save, saveNewVersion } from '../../api/client';
import { buildProjectNameFromFile, getParameter } from '../../Utils';
import { DrlCodeMirror } from './DrlCodeMirror';

// window._setDirty 全局类型在 src/global.d.ts 已经声明(无参数版),
// V5.45.3 编辑器不重新 declare,直接用。

const DrlEditor: React.FC<{ file: string }> = ({ file }) => {
    const editorParentRef = useRef<HTMLDivElement>(null);
    const cmRef = useRef<DrlCodeMirror | null>(null);
    const [payload, setPayload] = useState<DrlFilePayload | null>(null);
    const [error, setError] = useState<string | null>(null);

    // 阶段 1:加载文件
    useEffect(() => {
        let cancelled = false;
        loadDrlFile(file)
            .then(p => {
                if (cancelled) return;
                setPayload(p);
                cmRef.current?.setValue(p.content);
                cmRef.current?.refresh();
            })
            .catch(e => {
                if (cancelled) return;
                setError(String(e));
            });
        return () => {
            cancelled = true;
        };
    }, [file]);

    // 阶段 2:挂载 CodeMirror
    useEffect(() => {
        if (!editorParentRef.current) return;
        const cm = new DrlCodeMirror(editorParentRef.current, '');
        cmRef.current = cm;
        cm.onChange(() => {
            window._setDirty?.();
        });
        return () => {
            cmRef.current = null;
        };
    }, []);

    const handleSave = async () => {
        if (!cmRef.current || !file) return;
        const content = cmRef.current.getValue();
        try {
            await save(`/project${file}`, {
                content: encodeURIComponent(content),
                file,
                newVersion: 'false',
            });
            window._setDirty?.();
            message.success('保存成功');
        } catch (e) {
            message.error('保存失败: ' + String(e));
        }
    };

    const handleSaveNewVersion = async () => {
        if (!cmRef.current || !file) return;
        const content = cmRef.current.getValue();
        try {
            await saveNewVersion(`/project${file}`, {
                content: encodeURIComponent(content),
                file,
            });
            window._setDirty?.();
            message.success('已保存新版本');
        } catch (e) {
            message.error('保存新版本失败: ' + String(e));
        }
    };

    if (error) {
        return <div style={{ padding: 16, color: 'red' }}>加载失败: {error}</div>;
    }
    if (!payload) {
        return <div style={{ padding: 16 }}>加载中…</div>;
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
                <div ref={editorParentRef} style={{ flex: 1, overflow: 'auto' }} />
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

// 入口挂载
const file = getParameter('file');
window._project = buildProjectNameFromFile(file);

const root = document.getElementById('container');
if (root) {
    createRoot(root).render(<DrlEditor file={file} />);
}

export default DrlEditor;
