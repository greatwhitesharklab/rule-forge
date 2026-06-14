import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {applyMiddleware, createStore, Store} from 'redux';
import thunk from 'redux-thunk';
import {Provider} from 'react-redux';
import reducer from './reducer';
import PermissionConfigEditor from './components/PermissionConfigEditor';
import * as action from './action';

/**
 * 权限配置 (permission) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/permission}(无 file 参数 — 权限配置是全局单例,
 * 不是项目文件)。复现 {@code permission/index.tsx} 的挂载逻辑(store 创建 +
 * loadMasterData + Provider 包裹 PermissionConfigEditor),只是去掉
 * {@code createRoot(#container)},直接 return JSX。替代原 iframe
 * {@code editor.html?type=permission}。
 *
 * <p>原 iframe 入口从 TopBar / SidebarToolbar 下拉菜单触发;SPA 化后改为
 * {@code window.open('/app/editor/permission', '_blank')}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const store: Store = useMemo(() => createStore(reducer, applyMiddleware(thunk)), []);
    useEffect(() => {
        // loadMasterData() 无参,与 index.tsx 一致;仅挂载后触发一次。
        (store.dispatch as Function)(action.loadMasterData());
    }, [file, store]);

    return (
        <Provider store={store}>
            <PermissionConfigEditor/>
        </Provider>
    );
}
