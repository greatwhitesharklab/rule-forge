import '../bootbox.js';
import '../css/iconfont.css';
import '../css/theme.css';
import '../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../../node_modules/codemirror/lib/codemirror.css';
import '../../node_modules/bootstrapvalidator/dist/css/bootstrapValidator.css';
import React from 'react';
import {createRoot} from 'react-dom/client';
import {applyMiddleware, createStore} from 'redux';
import {Provider} from 'react-redux';
import * as ACTIONS from './action.js';
import reducer from './reducer.js';
import thunk from 'redux-thunk';
import Splitter from '../components/splitter/component/Splitter.jsx';
import FrameTab from '../components/frametab/component/FrameTab.jsx';
import ComponentContainer from './components/ComponentContainer.jsx';
import SidebarToolbar from './components/SidebarToolbar.jsx';
import Loading from '../components/loading/component/Loading.jsx';
import * as event from './event.js';
import * as componentEvent from '../components/componentEvent.js';

document.addEventListener('DOMContentLoaded', function () {
    window._types = null;
    window._projectName = null;
    window.componentEvent = componentEvent;
    const store = createStore(reducer, applyMiddleware(thunk));
    store.dispatch(ACTIONS.loadData());

    createRoot(document.getElementById("container")).render(
        <div>
            <Loading show={true}/>
            <Provider store={store}>
                <Splitter orientation='vertical' position='20%'>
                    <SidebarToolbar store={store} eventObj={event}/>
                    <div>
                        <ComponentContainer/>
                        <FrameTab welcomePage={window._welcomePage}/>
                    </div>
                </Splitter>
            </Provider>
        </div>,
    );

    event.eventEmitter.on(event.EXPAND_TREE_NODE, (nodeData) => {
        const spanEl = document.getElementById('node-' + nodeData.id);
        if (spanEl) {
            const liEl = spanEl.parentElement;
            if (liEl) {
                const parentLi = liEl.closest('li.parent_li');
                if (parentLi) {
                    const liChildren = parentLi.querySelectorAll(':scope > ul > li');
                    liChildren.forEach(function(child) { child.style.display = ''; });
                }
            }
            const firstI = spanEl.querySelector('i:first-child');
            if (firstI) {
                firstI.classList.add('rf-minus');
                firstI.classList.remove('rf-plus');
            }
        }
    });
});
