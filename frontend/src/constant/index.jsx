import '../bootbox.js';
import '../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../css/iconfont.css';

import React from 'react';
import { createRoot } from 'react-dom/client';
import {Provider} from 'react-redux';
import {createStore,applyMiddleware} from 'redux';
import thunk from 'redux-thunk';
import reducer from './reducer.js';
import ConstantEditor from './components/ConstantEditor.jsx';
import * as action from './action.js';

document.addEventListener('DOMContentLoaded', function(){
    const store=createStore(reducer,applyMiddleware(thunk));
    const file=_getParameter('file');
    if(!file || file.length<1){
        window.bootbox.alert('请先指定要加载的常量库文件.');
        return;
    }
    store.dispatch(action.loadMasterData(file));
    createRoot(document.getElementById("container")).render(
        <Provider store={store}>
            <ConstantEditor file={file}/>
        </Provider>,
);
});
function _getParameter(name) {
    var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)");
    var r = window.location.search.substr(1).match(reg);
    if (r != null)return unescape(r[2]);
    return null;
};