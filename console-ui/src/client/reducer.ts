import * as ACTIONS from './action.ts';

interface ClientConfigState {
    data: Array<{ name?: string; client?: string }>;
    /** 首次加载是否完成(LOADED_DATA 置 true) */
    loaded?: boolean;
    /** 加载失败原因(LOAD_FAILED 写入,LOADED_DATA 清零) */
    loadError?: unknown;
}

export default function clientConfig(state: ClientConfigState = {data: []}, action: { type: string; data?: unknown; index?: number; error?: unknown }): ClientConfigState {
    // NOTE: Original code used `state.prototype` which evaluates to undefined for plain objects,
    // so Object.assign({}, undefined, {data}) effectively returns {data}.
    // We replicate this behavior explicitly.
    switch (action.type) {
        case ACTIONS.LOADED_DATA:
            return {data: action.data as Array<{ name?: string; client?: string }>, loaded: true, loadError: null} as ClientConfigState;
        case ACTIONS.LOAD_FAILED:
            // 保留已有数据,仅记录失败原因(EditorRoute gate 渲染统一错误态)
            return Object.assign({}, state, {loadError: action.error});
        case ACTIONS.DEL: {
            const index = action.index!;
            const newData = [...state.data];
            newData.splice(index, 1);
            // V7 SPA 走查:保留 loaded/loadError 等加载态字段,避免编辑操作把 gate 打回 loading
            return Object.assign({}, state, {data: newData});
        }
        case ACTIONS.ADD: {
            const data = [...state.data];
            data.push({});
            return Object.assign({}, state, {data});
        }
        default:
            return state;
    }
}
