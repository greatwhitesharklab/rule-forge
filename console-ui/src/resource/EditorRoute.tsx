import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {applyMiddleware, createStore, Store} from 'redux';
import thunk from 'redux-thunk';
import {Provider} from 'react-redux';
import reducer from './reducer';
import ResourceEditor from './components/ResourceEditor';
import * as action from './action';
import Loading from '../components/loading/component/Loading';

/**
 * 公共资源 (resource) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/resource?file=/xxx}。
 * 复现 {@code resource/index.tsx} 的挂载逻辑(store 创建 + generateVariableLibrary +
 * Provider 包裹 + Loading 覆盖层),只是去掉 {@code createRoot(#container)},
 * 直接 return JSX。替代原 iframe {@code editor.html?type=resource}。
 *
 * <p>注意:resource 的加载 action 是 {@code generateVariableLibrary}(沿用 index.tsx),
 * 且保留 index.tsx 的 {@code <Loading show={true}/>} 覆盖层原样不简化。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const store: Store = useMemo(() => createStore(reducer, applyMiddleware(thunk)), []);
    useEffect(() => {
        if (!file || file.length < 1) {
            return;
        }
        (store.dispatch as Function)(action.generateVariableLibrary(file));
    }, [file, store]);

    return (
        <div>
            <Loading show={true}/>
            <Provider store={store}>
                <ResourceEditor file={file}/>
            </Provider>
        </div>
    );
}
