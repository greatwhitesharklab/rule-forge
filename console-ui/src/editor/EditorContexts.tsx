/**
 * V5.x — 编辑器侧共享 Context(替代历史 window._project / window._setDirty / window._dirty 全局变量)。
 *
 * <p>背景:各编辑器(drleditor / flow-bpmn / decisiontree / scorecard / decisiontable / ...)是
 * 独立的 SPA 路由(`/app/editor/<type>`),与 frame `/app` 不共享 Redux store。它们历史上靠
 * {@code window._project}(项目名)+ {@code window._setDirty}(dirty 回调)+ {@code window._dirty}
 * (dirty 标志)在编辑器入口与复用对话框(ConfigLibraryDialog / RuleForgePropertiesPanel)/
 * 内部组件(installLibrariesBridge)之间传值。
 *
 * <p>本模块提供两个 Context:
 * <ul>
 *   <li>{@link ProjectContext} — 当前编辑文件所属项目名(由各 EditorRoute 用 buildProjectNameFromFile
 *       计算后 Provider 提供)。ConfigLibraryDialog / RuleForgePropertiesPanel / QuickTestDialog 等复用
 *       对话框读它来拼 add/test 请求。</li>
 *   <li>{@link DirtyContext} — dirty 通知接口(setDirty: 标记编辑器有未保存改动; clearDirty: 保存后清零)。
 *       EditorToolbar / 安装 libraries bridge 的 effect 读它。</li>
 * </ul>
 *
 * <p>使用约定:每个 EditorRoute 在最外层用 {@code <ProjectContext.Provider value={project}>} 包裹,在挂载
 * {@code EditorToolbar} 或运行 libraries bridge 的子树用 {@code <DirtyContext.Provider value={dirtyApi}>}
 * 包裹。读侧一律 {@code useContext}。
 */
import {createContext, useContext} from 'react';

/** 当前编辑文件所属项目名(原 window._project)。null = 未设置(编辑器尚未挂载/无文件)。 */
export const ProjectContext = createContext<string | null>(null);

/** Dirty 通知接口。 */
export interface DirtyApi {
    /** 标记编辑器有未保存改动(原 window._setDirty() / window.setDirty(true))。 */
    setDirty(): void;
    /** 保存后清零 dirty 标志(原 EditorToolbar.clearDirty)。 */
    clearDirty(): void;
    /** 当前是否 dirty(原 window._dirty)。 */
    isDirty(): boolean;
}

/** 默认 no-op dirty api(等价于历史 window._setDirty 为 undefined 时 {@code window._setDirty?.()} 的行为)。 */
const noopDirtyApi: DirtyApi = {
    setDirty() {},
    clearDirty() {},
    isDirty() {
        return false;
    },
};

export const DirtyContext = createContext<DirtyApi>(noopDirtyApi);

/** 读当前项目名(替代 window._project)。 */
export function useProject(): string | null {
    return useContext(ProjectContext);
}

/** 读 dirty api(替代 window._setDirty / window.setDirty)。 */
export function useDirty(): DirtyApi {
    return useContext(DirtyContext);
}
