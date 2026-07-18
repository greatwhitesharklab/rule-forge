import '../css/iconfont.css'
import '../css/tailwind-base.css';

import React from 'react'
import { createRoot } from 'react-dom/client'
import {createEditorStore} from '../store/createEditorStore'
import {Provider} from 'react-redux'
import reducer from './reducer.js'
import * as action from './action.js'
import VariableEditor from './components/VariableEditor.jsx'

import {alert} from '@/utils/modal';
document.addEventListener('DOMContentLoaded', function () {
    const store = createEditorStore(reducer)
    const file = _getParameter('file')
    if (!file || file.length < 1) {
        alert('请先指定要加载的变量库文件.')
        return
    }
    (store.dispatch as Function)(action.loadMasterData(file))

    createRoot(document.getElementById("container")!).render(
        <div style={{height: '100%', display: 'flex', flexDirection: 'column'}}>
            <Provider store={store}>
                <VariableEditor file={file}/>
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
