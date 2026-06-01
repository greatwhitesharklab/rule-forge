import '../bootbox.js';
import '../../node_modules/bootstrapvalidator/dist/css/bootstrapValidator.css';
import '../css/iconfont.css'
import '../css/tailwind-base.css';

import React from 'react'
import { createRoot } from 'react-dom/client'
import {applyMiddleware, createStore, Store} from 'redux'
import thunk from 'redux-thunk'
import {Provider} from 'react-redux'
import reducer from './reducer.js'
import ResourceEditor from './components/ResourceEditor.jsx'
import * as action from './action.js'
import Loading from '../components/loading/component/Loading.tsx';

document.addEventListener('DOMContentLoaded', function () {
    const store: Store = createStore(reducer, applyMiddleware(thunk))
    const file = _getParameter('file')
    if (!file || file.length < 1) {
        window.bootbox.alert('请先指定要加载的变量库文件.')
        return
    }
    (store.dispatch as Function)(action.generateVariableLibrary(file))

    createRoot(document.getElementById("container")!).render(
        <div>
            <Loading show={true}/>
            <Provider store={store}>
                <ResourceEditor file={file}/>
            </Provider>
        </div>,
    )
})

function _getParameter(name: string): string | null {
    const reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)")
    const r = window.location.search.substr(1).match(reg)
    if (r != null) return unescape(r[2])
    return null
}
