// V0.1 统一的 Redux store 工厂，收敛各模块 createStore(reducer, applyMiddleware(thunk)) 的样板
import { configureStore, type Action, type Reducer } from '@reduxjs/toolkit';

/**
 * 创建编辑器类页面的 store。
 *
 * - thunk 中间件：使用 RTK 默认配置（等价于原 applyMiddleware(thunk)）。
 * - 关闭 serializableCheck / immutableCheck：遗留的 state / action 中存在
 *   非序列化值（函数、class 实例等），开启会在开发环境刷大量警告并拖慢性能。
 * - devTools：RTK 默认开发环境自动启用（替代已移除的 redux-devtools 包）。
 *
 * 返回 EnhancedStore，与 redux 的 Store 结构兼容，调用处可直接标注 Store。
 */
export function createEditorStore<S, A extends Action>(reducer: Reducer<S, A>, preloadedState?: S) {
    return configureStore({
        reducer,
        preloadedState,
        middleware: (getDefaultMiddleware) =>
            getDefaultMiddleware({
                serializableCheck: false,
                immutableCheck: false,
            }),
    });
}
