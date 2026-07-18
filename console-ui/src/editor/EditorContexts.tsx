/**
 * V5.x — 编辑器侧共享 Context(替代历史 window._project / window._setDirty / window._dirty 全局变量)。
 *
 * <p>背景:各编辑器(drleditor / flow-bpmn / decisiontree / scorecard / decisiontable / ...)是
 * 独立的 SPA 路由(`/app/editor/<type>`),与 frame `/app` 不共享 Redux store。它们历史上靠
 * {@code window._project}(项目名)+ {@code window._setDirty}(dirty 回调)+ {@code window._dirty}
 * (dirty 标志)在编辑器入口与复用对话框(RuleForgePropertiesPanel)/
 * 内部组件(installLibrariesBridge)之间传值。
 *
 * <p>本模块提供两个 Context + 一个 hook:
 * <ul>
 *   <li>{@link ProjectContext} — 当前编辑文件所属项目名(由各 EditorRoute 用 buildProjectNameFromFile
 *       计算后 Provider 提供)。RuleForgePropertiesPanel / QuickTestDialog 等复用
 *       对话框读它来拼 add/test 请求。</li>
 *   <li>{@link DirtyContext} — dirty 通知接口(setDirty: 标记编辑器有未保存改动; clearDirty: 保存后清零)。
 *       EditorToolbar / 安装 libraries bridge 的 effect 读它。</li>
 *   <li>{@link useDirtyApi} — EditorRoute 用的 hook:构造 DirtyApi + 文件路径变化时自动清零 +
 *       返回 setter(让父组件在保存成功后调)。</li>
 * </ul>
 *
 * <p>使用约定:每个 EditorRoute 在最外层用 {@code <ProjectContext.Provider value={project}>} 包裹,在挂载
 * {@code EditorToolbar} 或运行 libraries bridge 的子树用 {@code <DirtyContext.Provider value={dirtyApi}>}
 * 包裹。读侧一律 {@code useContext}。
 */
import {createContext, useContext, useEffect, useMemo, useRef, useState} from 'react';

/** 当前编辑文件所属项目名(原 window._project)。null = 未设置(编辑器尚未挂载/无文件)。 */
export const ProjectContext = createContext<string | null>(null);

/** Dirty 通知接口。 */
export interface DirtyApi {
    /** 标记编辑器有未保存改动(原 window._setDirty() / window.setDirty(true)) */
    setDirty(): void;
    /** 保存后清零 dirty 标志(原 EditorToolbar.clearDirty) */
    clearDirty(): void;
    /** 当前是否 dirty(原 window._dirty) */
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

/**
 * EditorRoute 用的 hook — 构造 DirtyApi + 文件路径变化时自动清零 dirty 状态。
 *
 * <p>设计要点:
 * <ul>
 *   <li>state 触发 React 重渲染(让 toolbar 反映脏态);ref 提供 isDirty() 实时读最新值
 *       (避免闭包拿到旧 state)</li>
 *   <li>file 变化时 useEffect 同步清零 ref + state(新文件 = 未保存状态)</li>
 *   <li>返回 dirtyApi + isDirty getter(toolbar 直接读最新值渲染按钮文案)</li>
 * </ul>
 *
 * @param file 当前编辑文件路径(URL query ?file=...)。变化时 dirty 自动清零。
 * @returns dirtyApi (setDirty / clearDirty / isDirty) + isDirty getter
 */
export function useDirtyApi(file: string): {dirtyApi: DirtyApi; isDirty: boolean} {
    const [, setDirtyState] = useState(false);
    const dirtyRef = useRef(false);

    const dirtyApi = useMemo<DirtyApi>(() => ({
        setDirty: () => {
            if (dirtyRef.current) return;
            dirtyRef.current = true;
            setDirtyState(true);
        },
        clearDirty: () => {
            if (!dirtyRef.current) return;
            dirtyRef.current = false;
            setDirtyState(false);
        },
        isDirty: () => dirtyRef.current,
    }), []);

    // 文件路径变化时清零 dirty(新文件 = 未保存状态)。
    useEffect(() => {
        dirtyRef.current = false;
        setDirtyState(false);
    }, [file]);

    return {dirtyApi, isDirty: dirtyRef.current};
}