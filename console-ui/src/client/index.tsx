import '../bootbox.js';
import '../css/iconfont.css';
import '../css/tailwind-base.css';
import React from 'react';
import { createRoot } from 'react-dom/client';
import {applyMiddleware, createStore} from 'redux';
import {Provider} from 'react-redux';
import thunk from 'redux-thunk';

import reducer from './reducer.ts';
import ClientConfigEditor from './component/ClientConfigEditor.tsx';
import * as action from './action.ts';
import {getParameter} from '../Utils.js';

declare const bootbox: BootboxStatic;

document.addEventListener('DOMContentLoaded', function () {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const store: any = createStore(reducer, applyMiddleware(thunk));
    const project = getParameter('project');
    store.dispatch(action.loadData(project));
    createRoot(document.getElementById("container")!).render(
        <Provider store={store}>
            <ClientConfigEditor project={project}/>
        </Provider>,
    );
});
