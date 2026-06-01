import '@/bootbox.js';
import '@/css/iconfont.css';
import 'codemirror/lib/codemirror.css';
import 'bootstrapvalidator/dist/css/bootstrapValidator.css';
import '../css/tailwind-base.css';
import {createRoot} from 'react-dom/client';
import {applyMiddleware, createStore} from 'redux';
import {Provider} from 'react-redux';
import * as ACTIONS from '@/frame/action.js';
import reducer from '@/frame/reducer.js';
import thunk from 'redux-thunk';
import Splitter from '@/components/splitter/component/Splitter.tsx';
import FrameTab from '@/components/frametab/component/FrameTab.tsx';
import ComponentContainer from '@/frame/components/ComponentContainer.tsx';
import TopBar from '@/frame/components/TopBar.tsx';
import ContentTabBar from '@/frame/components/ContentTabBar.tsx';
import ActivityBar from '@/frame/components/ActivityBar.tsx';
import RuleEditorPanel from '@/frame/panels/RuleEditorPanel.jsx';
import MonitoringPanel from '@/frame/panels/MonitoringPanel.jsx';
import DatasourcePanel from '@/datasource/index.tsx';
import PlaceholderPanel from '@/frame/panels/PlaceholderPanel.jsx';
import ReleasePanel from '@/release/index.tsx';
import SimulationPanel from '@/simulation/index.tsx';
import Loading from '@/components/loading/component/Loading.tsx';
import * as event from '@/frame/event.js';
import * as componentEvent from '@/components/componentEvent.js';
import {connect} from 'react-redux';
import {Store} from 'redux';

interface SidePanelSwitcherProps {
    activePanel: string;
    store: Store;
    eventObj: typeof event;
}

function SidePanelSwitcher({activePanel, store, eventObj}: SidePanelSwitcherProps) {
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

const SidePanelConnected = connect((state: { ui?: { activePanel?: string } }) => ({activePanel: (state.ui && state.ui.activePanel) || 'rules'}))(SidePanelSwitcher);

document.addEventListener('DOMContentLoaded', function () {
    window._types = null;
    window._projectName = null;
    window.componentEvent = componentEvent;
    const store = createStore(reducer, applyMiddleware(thunk));
    (store.dispatch as Function)(ACTIONS.loadData());

    var contentTabBarRef: any = null;
    var frameTabRef: any = null;

    createRoot(document.getElementById("container")!).render(
        <div className="app-layout">
            <Loading show={true}/>
            <Provider store={store}>
                <TopBar/>
                <div className="app-body">
                    <ActivityBar/>
                    <Splitter orientation='vertical' position='240px'>
                        <SidePanelConnected store={store} eventObj={event}/>
                        <div className="app-content">
                            <ContentTabBar ref={(ref: any) => { contentTabBarRef = ref; }}
                                           getFrameTabRef={() => frameTabRef}/>
                            <div className="content-area">
                                <ComponentContainer/>
                                <FrameTab ref={(ref: any) => {
                                    frameTabRef = ref;
                                    if (contentTabBarRef) contentTabBarRef.frameTabRef = ref;
                                }}
                                          welcomePage={window._welcomePage}
                                          onTabsChange={(tabs: unknown, activeTab: string) => {
                                              if (contentTabBarRef) contentTabBarRef.setTabData(tabs, activeTab);
                                          }}/>
                            </div>
                        </div>
                    </Splitter>
                </div>
            </Provider>
        </div>,
    );

    event.eventEmitter.on(event.EXPAND_TREE_NODE, (nodeData: TreeNodeData) => {
        const spanEl = document.getElementById('node-' + nodeData.id);
        if (spanEl) {
            const liEl = spanEl.parentElement;
            if (liEl) {
                const parentLi = liEl.closest('li.parent_li');
                if (parentLi) {
                    const liChildren = parentLi.querySelectorAll(':scope > ul > li');
                    liChildren.forEach(function(child: Element) { (child as HTMLElement).style.display = ''; });
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
