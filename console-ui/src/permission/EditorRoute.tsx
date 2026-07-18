import {useEffect, useMemo} from 'react';
import {applyMiddleware, createStore, Store} from 'redux';
import thunk from 'redux-thunk';
import {connect, Provider} from 'react-redux';
import reducer, {PermissionRootState} from './reducer';
import PermissionConfigEditor from './components/PermissionConfigEditor';
import * as action from './action';
import EditorLoadState from '../editor/EditorLoadState';

interface LoadGateProps {
    masterData?: unknown[];
    loadError?: unknown;
    dispatch: (a: unknown) => unknown;
}

/**
 * 加载态门卫:失败 → 统一错误态(EditorLoadState,带重试);未加载完成 → loading;
 * 就绪 → 编辑器本体。权限配置是全局单例(无 file 参数),故无 no-file 空态。
 */
function LoadGate({masterData, loadError, dispatch}: LoadGateProps) {
    if (loadError) {
        return (
            <EditorLoadState
                status="error"
                error={loadError}
                onRetry={() => (dispatch as Function)(action.loadMasterData())}
            />
        );
    }
    if (!masterData) {
        return <EditorLoadState status="loading"/>;
    }
    return <PermissionConfigEditor/>;
}

const ConnectedLoadGate = connect((state: PermissionRootState) => ({
    masterData: state.master.masterData,
    loadError: state.master.loadError,
}))(LoadGate);

/**
 * 权限配置 (permission) 编辑器本体:store 创建 + loadMasterData 加载 +
 * EditorLoadState 门禁 + Provider 包裹。
 * 可被路由壳或应用内标签宿主渲染 — 全局单例、无 props,不依赖 react-router。
 */
export function PermissionEditor() {
    const store: Store = useMemo(() => createStore(reducer, applyMiddleware(thunk)), []);
    useEffect(() => {
        // loadMasterData() 无参,与 index.tsx 一致;仅挂载后触发一次。
        (store.dispatch as Function)(action.loadMasterData());
    }, [store]);

    return (
        <Provider store={store}>
            <ConnectedLoadGate/>
        </Provider>
    );
}

/**
 * 权限配置 (permission) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/permission}(无 file 参数 — 权限配置是全局单例,
 * 不是项目文件)。复现 {@code permission/index.tsx} 的挂载逻辑(store 创建 +
 * loadMasterData + Provider 包裹 PermissionConfigEditor),只是去掉
 * {@code createRoot(#container)},直接 return JSX。替代原 iframe
 * {@code editor.html?type=permission}。
 *
 * <p>原 iframe 入口从 TopBar 下拉菜单触发;SPA 化后改为
 * {@code window.open('/app/editor/permission', '_blank')}。
 *
 * <p>V7 SPA 走查:加载中/失败统一走 {@link LoadGate}(此前失败只弹一次 alert,页面白屏)。
 *
 * <p>本壳无 searchParams 可取(全局单例),直接渲染 {@link PermissionEditor}。
 */
export default function EditorRoute() {
    return <PermissionEditor/>;
}
