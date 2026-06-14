import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {applyMiddleware, createStore, Store} from 'redux';
import thunk from 'redux-thunk';
import {Provider} from 'react-redux';
import reducer from './reducer';
import ClientConfigEditor from './component/ClientConfigEditor';
import * as action from './action';

/**
 * 客户端推送配置 (client) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/client?project=<projectName>}。复现
 * {@code client/index.tsx} 的挂载逻辑(store 创建 + loadData(project) + Provider 包裹
 * ClientConfigEditor),只是去掉 {@code createRoot(#container)},直接 return JSX。
 *
 * <p>注意:client 原先读 {@code ?project=} 而非 {@code ?file=}。本路由保留
 * {@code project} 参数;若 URL 带的是 {@code file}(/project/<name> 形式)则取其最后
 * 一段作为 project 名,保持两种入口都能用。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const project = params.get('project') || (file ? file.split('/').pop() : '');
    const store: Store = useMemo(() => createStore(reducer, applyMiddleware(thunk)), []);
    useEffect(() => {
        if (project === null) {
            return;
        }
        (store.dispatch as Function)(action.loadData(project));
    }, [project, store]);

    return (
        <Provider store={store}>
            <ClientConfigEditor project={project}/>
        </Provider>
    );
}
