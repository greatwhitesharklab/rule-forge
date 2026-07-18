import '../css/iconfont.css';
import '../css/tailwind-base.css';

import React from 'react';
import {createRoot} from 'react-dom/client';
import {Provider} from 'react-redux';
import {createEditorStore} from '../store/createEditorStore';
import reducer from './reducer.js';
import ConstantEditor from './components/ConstantEditor.tsx';
import * as action from './action.js';

import {alert} from '@/utils/modal';
document.addEventListener('DOMContentLoaded', function () {
    const store = createEditorStore(reducer);
    const file = _getParameter('file');
    if (!file || file.length < 1) {
        alert('请先指定要加载的常量库文件.');
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
