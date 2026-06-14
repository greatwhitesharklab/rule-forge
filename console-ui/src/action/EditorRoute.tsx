import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {applyMiddleware, createStore, Store} from 'redux';
import thunk from 'redux-thunk';
import {Provider} from 'react-redux';
import reducer from './reducer';
import * as action from './action';
import ActionEditor from './components/ActionEditor';

/**
 * 动作库 (actionLibrary, .al.xml) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/action?file=/project/xxx.al.xml}。
 * 复现 {@code action/index.tsx} 的挂载逻辑(store 创建 + loadMasterData + Provider 包裹),
 * 只是去掉 {@code createRoot(#container)},直接 return JSX。替代原 iframe
 * {@code editor.html?type=action}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const store: Store = useMemo(() => createStore(reducer, applyMiddleware(thunk)), []);
    useEffect(() => {
        if (!file || file.length < 1) {
            return;
        }
        (store.dispatch as Function)(action.loadMasterData(file));
    }, [file, store]);

    return (
        <Provider store={store}>
            <ActionEditor file={file}/>
        </Provider>
    );
}
