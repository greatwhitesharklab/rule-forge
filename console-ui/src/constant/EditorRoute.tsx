import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {createStore, applyMiddleware, Store} from 'redux';
import thunk from 'redux-thunk';
import {Provider} from 'react-redux';
import reducer from './reducer';
import ConstantEditor from './components/ConstantEditor';
import * as action from './action';

/**
 * 常量库 (constantLibrary, .cl.xml) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/constant?file=/project/xxx.cl.xml}。
 * 复现 {@code constant/index.tsx} 的挂载逻辑(store 创建 + loadMasterData + Provider 包裹),
 * 只是去掉 {@code createRoot(#container)},直接 return JSX。替代原 iframe
 * {@code editor.html?type=constant}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const store: Store = useMemo(() => createStore(reducer, applyMiddleware(thunk)), []);
    useEffect(() => {
        if (!file || file.length < 1) {
            return;
        }
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (store.dispatch as any)(action.loadMasterData(file));
    }, [file, store]);

    return (
        <Provider store={store}>
            <ConstantEditor file={file}/>
        </Provider>
    );
}
