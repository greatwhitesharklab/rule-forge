import '../bootbox.js';
import '../css/iconfont.css';
import '../css/tailwind-base.css';

import React from 'react';
import {createRoot} from 'react-dom/client';
import {createStore, applyMiddleware, Store} from 'redux';
import {Provider} from 'react-redux';
import thunk from 'redux-thunk';
import reducer from './reducer.js';
import ParameterEditor from './components/ParameterEditor.tsx';
import * as action from './action.js';
import {getParameter} from '../Utils.js';

document.addEventListener('DOMContentLoaded', function () {
    const store: Store = createStore(reducer, applyMiddleware(thunk));
    const file = getParameter("file");
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (store.dispatch as any)(action.loadData(file));
    createRoot(document.getElementById("container")!).render(
        <Provider store={store}>
            <ParameterEditor file={file}/>
        </Provider>,
    );
});
