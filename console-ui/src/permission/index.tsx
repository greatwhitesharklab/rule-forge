import '../css/iconfont.css';
import '../css/tailwind-base.css';

import React from 'react';
import { createRoot } from 'react-dom/client';
import {Provider} from 'react-redux';
import {Store} from 'redux';
import {createEditorStore} from '../store/createEditorStore';
import PermissionConfigEditor from './components/PermissionConfigEditor.jsx';
import reducer from './reducer.js';
import * as action from './action.js';

document.addEventListener('DOMContentLoaded', function () {
    const store: Store = createEditorStore(reducer);
    (store.dispatch as Function)(action.loadMasterData());
    createRoot(document.getElementById("container")!).render(
        <Provider store={store}>
            <PermissionConfigEditor/>
        </Provider>,
    );
});
