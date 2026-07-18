import {useEffect, useState} from 'react';
import {Tabs} from 'antd';
import * as event from '@/frame/event.js';
import QuickStart from '@/frame/QuickStart.jsx';
import {EDITOR_REGISTRY} from '@/frame/editorRegistry';
import {editorTabLabel, isEditorType, type EditorType} from '@/frame/editorTypeMap';

/**
 * 应用内编辑器标签宿主(替代原 window.open 新开浏览器标签 + FrameTab/ContentTabBar 空壳)。
 *
 * <p>打开通道:任何位置调 {@link event.openEditorTab} 发 OPEN_EDITOR_TAB 事件,
 * 本宿主监听并完成标签管理。标签模型 {key, editorType, file, label},
 * key = editorType + '|' + file —— 重复打开同 key 只激活已有标签。
 *
 * <p>保活:所有已开编辑器保持挂载(非激活的 display:none 隐藏),保住各自
 * redux store / 撤销状态;只有关闭标签才卸载。无标签时渲染 QuickStart 欢迎页。
 */

interface EditorTab {
    key: string;
    editorType: EditorType;
    file?: string;
    label: string;
}

export default function EditorTabs() {
    const [tabs, setTabs] = useState<EditorTab[]>([]);
    const [activeKey, setActiveKey] = useState<string>('');

    useEffect(() => {
        const handler = (payload: event.OpenEditorTabPayload) => {
            if (!payload || !isEditorType(payload.editorType)) return;
            const editorType: EditorType = payload.editorType;
            const key = editorType + '|' + (payload.file || '');
            const label = payload.label || editorTabLabel(editorType, payload.file);
            setTabs(prev => prev.some(t => t.key === key)
                ? prev
                : [...prev, {key, editorType, file: payload.file, label}]);
            setActiveKey(key);
        };
        event.eventEmitter.on(event.OPEN_EDITOR_TAB, handler);
        return () => {
            event.eventEmitter.removeListener(event.OPEN_EDITOR_TAB, handler);
        };
    }, []);

    const closeTab = (key: string) => {
        const idx = tabs.findIndex(t => t.key === key);
        if (idx === -1) return;
        const next = [...tabs.slice(0, idx), ...tabs.slice(idx + 1)];
        setTabs(next);
        // 关掉的是激活标签 → 激活相邻标签(优先右侧,同原 FrameTab 语义);无标签回到 QuickStart
        if (activeKey === key) {
            setActiveKey(next.length > 0 ? next[Math.min(idx, next.length - 1)].key : '');
        }
    };

    if (tabs.length === 0) {
        return (
            <div className="editor-tabs-host">
                <QuickStart/>
            </div>
        );
    }

    return (
        <div className="editor-tabs-host">
            {/* antd Tabs 仅作标签条;内容由下方 panes 自绘(保活需要,antd TabPane 懒挂载不满足) */}
            <Tabs
                type="editable-card"
                hideAdd
                size="small"
                activeKey={activeKey}
                onChange={setActiveKey}
                onEdit={(targetKey, action) => {
                    if (action === 'remove' && typeof targetKey === 'string') closeTab(targetKey);
                }}
                items={tabs.map(t => ({key: t.key, label: t.label, children: null}))}
            />
            <div className="editor-tabs-panes">
                {tabs.map(t => (
                    <div key={t.key} className="editor-tab-pane"
                         style={{display: t.key === activeKey ? '' : 'none'}}>
                        {EDITOR_REGISTRY[t.editorType].render(t.file)}
                    </div>
                ))}
            </div>
        </div>
    );
}
