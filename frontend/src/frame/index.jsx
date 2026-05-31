import '@/bootbox.js';
import '@/css/iconfont.css';
import 'codemirror/lib/codemirror.css';
import 'bootstrapvalidator/dist/css/bootstrapValidator.css';
import '../css/tailwind-base.css';
import React from 'react';
import {createRoot} from 'react-dom/client';
import {applyMiddleware, createStore} from 'redux';
import {Provider} from 'react-redux';
import * as ACTIONS from '@/frame/action.js';
import reducer from '@/frame/reducer.js';
import thunk from 'redux-thunk';
import Splitter from '@/components/splitter/component/Splitter.jsx';
import FrameTab from '@/components/frametab/component/FrameTab.jsx';
import ComponentContainer from '@/frame/components/ComponentContainer.jsx';
import TopBar from '@/frame/components/TopBar.jsx';
import ContentTabBar from '@/frame/components/ContentTabBar.jsx';
import ActivityBar from '@/frame/components/ActivityBar.jsx';
import RuleEditorPanel from '@/frame/panels/RuleEditorPanel.jsx';
import MonitoringPanel from '@/frame/panels/MonitoringPanel.jsx';
import DatasourcePanel from '@/datasource/index.jsx';
import PlaceholderPanel from '@/frame/panels/PlaceholderPanel.jsx';
import ReleasePanel from '@/release/index.jsx';
import SimulationPanel from '@/simulation/index.jsx';
import Loading from '@/components/loading/component/Loading.jsx';
import * as event from '@/frame/event.js';
import * as componentEvent from '@/components/componentEvent.js';
import {connect} from 'react-redux';

function SidePanelSwitcher({activePanel, store, eventObj}) {
    switch (activePanel) {
        case 'monitoring':
            return <MonitoringPanel/>;
        case 'datasource':
            return <DatasourcePanel/>;
        case 'release':
            return <ReleasePanel/>;
        case 'simulation':
            return <SimulationPanel/>;
        case 'ai':
            return <PlaceholderPanel panelId="ai"/>;
        case 'settings':
            return <PlaceholderPanel panelId="settings"/>;
        default:
            return <RuleEditorPanel store={store} eventObj={eventObj}/>;
    }
}

const SidePanelConnected = connect(state => ({activePanel: (state.ui && state.ui.activePanel) || 'rules'}))(SidePanelSwitcher);

document.addEventListener('DOMContentLoaded', function () {
    window._types = null;
    window._projectName = null;
    window.componentEvent = componentEvent;
    const store = createStore(reducer, applyMiddleware(thunk));
    store.dispatch(ACTIONS.loadData());

    var contentTabBarRef = null;
    var frameTabRef = null;

    createRoot(document.getElementById("container")).render(
        <div className="app-layout">
            <Loading show={true}/>
            <Provider store={store}>
                <TopBar/>
                <div className="app-body">
                    <ActivityBar/>
                    <Splitter orientation='vertical' position='240px'>
                        <SidePanelConnected store={store} eventObj={event}/>
                        <div className="app-content">
                            <ContentTabBar ref={ref => { contentTabBarRef = ref; }}
                                           getFrameTabRef={() => frameTabRef}/>
                            <div className="content-area">
                                <ComponentContainer/>
                                <FrameTab ref={ref => {
                                    frameTabRef = ref;
                                    if (contentTabBarRef) contentTabBarRef.frameTabRef = ref;
                                }}
                                          welcomePage={window._welcomePage}
                                          onTabsChange={(tabs, activeTab) => {
                                              if (contentTabBarRef) contentTabBarRef.setTabData(tabs, activeTab);
                                          }}/>
                            </div>
                        </div>
                    </Splitter>
                </div>
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
