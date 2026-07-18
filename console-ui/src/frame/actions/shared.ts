// V7.24:从 frame/action.ts 拆分 — thunk 共享的 dispatch/getState 与 UI 过滤参数读取
// (内部模块:仅供 actions/ 内其他模块引用,不经 frame/action.ts 对外导出)

/**
 * 从 thunk 的 getState() 提取当前 UI 过滤参数(projectName / classify / types / searchFileName
 * / currentGitTag)。
 * 替代历史 window._projectName / window._classify / window._types / window.searchFileName /
 * window._currentGitTag。
 * getState 类型用 Function 兼容 redux-thunk 默认签名,内部 cast 后读字段。
 */
export function readUiFilters(getState: Function): {
    projectName: string | null;
    classify: boolean;
    types: string | null;
    searchFileName: string | null;
    currentGitTag: string | null;
} {
    const st = getState() as {
        ui?: {
            projectName?: string | null;
            classify?: boolean;
            types?: string | null;
            searchFileName?: string | null;
            currentGitTag?: string | null;
        };
    };
    const ui = (st && st.ui) || {};
    return {
        projectName: ui.projectName ?? null,
        classify: ui.classify ?? true,
        types: ui.types ?? null,
        searchFileName: ui.searchFileName ?? null,
        currentGitTag: ui.currentGitTag ?? null,
    };
}

// Global dispatch / getState used by context-menu callbacks (set during loadData thunk
// execution so the menu click closures — invoked later — can dispatch + read store).
// 通过 live binding 对外提供读;写必须走 _setDispatch/_setGetState(ESM 导入绑定只读)。
export let _dispatch: Function = () => {};
export let _getState: Function = () => ({ui: {}});

export function _setDispatch(dispatch: Function) {
    _dispatch = dispatch;
}

export function _setGetState(getState: Function) {
    _getState = getState;
}
