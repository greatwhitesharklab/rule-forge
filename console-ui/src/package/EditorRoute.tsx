import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {applyMiddleware, createStore, Store} from 'redux';
import thunk from 'redux-thunk';
import {Provider} from 'react-redux';
import reducer from './reducer';
import PackageEditor from './components/PackageEditor';
import * as action from './action';

/**
 * 知识包 (package, .rp) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/package?file=/project/xxx.rp}。
 * 复现 {@code package/index.tsx} 的挂载逻辑(store 创建 + loadMasterData +
 * loadPackageConfig + Provider 包裹 PackageEditor),只是去掉
 * {@code createRoot(#container)},直接 return JSX。替代原 iframe
 * {@code editor.html?type=package}。
 *
 * <p>{@code file} 形如 {@code /project/<packageName>.rp},{@code project} = file 去掉
 * {@code .rp} 后缀(与 index.tsx 的 {@code _getParameter("file").replace('.rp', '')} 一致)。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const project = file.replace('.rp', '');
    const store: Store = useMemo(() => createStore(reducer, applyMiddleware(thunk)), []);
    useEffect(() => {
        if (!file || file.length < 1) {
            return;
        }
        (store.dispatch as Function)(action.loadMasterData(project));
        (store.dispatch as Function)(action.loadPackageConfig(project));
    }, [file, project, store]);

    return (
        <Provider store={store}>
            <PackageEditor project={project}/>
        </Provider>
    );
}
