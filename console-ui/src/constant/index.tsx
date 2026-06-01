import '../bootbox.js';
import '../css/iconfont.css';
import '../css/tailwind-base.css';

import React from 'react';
import {createRoot} from 'react-dom/client';
import {Provider} from 'react-redux';
import {createStore, applyMiddleware, Store} from 'redux';
import thunk from 'redux-thunk';
import reducer from './reducer.js';
import ConstantEditor from './components/ConstantEditor.tsx';
import * as action from './action.js';

document.addEventListener('DOMContentLoaded', function () {
    const store: Store = createStore(reducer, applyMiddleware(thunk));
    const file = _getParameter('file');
    if (!file || file.length < 1) {
        window.bootbox.alert('请先指定要加载的常量库文件.');
        return;
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (store.dispatch as any)(action.loadMasterData(file));
    createRoot(document.getElementById("container")!).render(
        <Provider store={store}>
            <ConstantEditor file={file}/>
        </Provider>,
    );
});

function _getParameter(name: string): string | null {
    const reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)");
    const r = window.location.search.substr(1).match(reg);
    if (r != null) return unescape(r[2]);
    return null;
}
