import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {createStore, applyMiddleware, Store} from 'redux';
import thunk from 'redux-thunk';
import {Provider} from 'react-redux';
import reducer from './reducer';
import ParameterEditor from './components/ParameterEditor';
import * as action from './action';

/**
 * 参数库 (parameterLibrary, .pl.xml) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/parameter?file=/project/xxx.pl.xml}。
 * 复现 {@code parameter/index.tsx} 的挂载逻辑(store 创建 + loadData + Provider 包裹),
 * 只是去掉 {@code createRoot(#container)},直接 return JSX。注意:parameter 的加载
 * action 是 {@code loadData},不是 {@code loadMasterData}。替代原 iframe
 * {@code editor.html?type=parameter}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const store: Store = useMemo(() => createStore(reducer, applyMiddleware(thunk)), []);
    useEffect(() => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (store.dispatch as any)(action.loadData(file));
    }, [file, store]);

    return (
        <Provider store={store}>
            <ParameterEditor file={file}/>
        </Provider>
    );
}
