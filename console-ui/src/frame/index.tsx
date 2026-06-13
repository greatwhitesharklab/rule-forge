import '@/css/iconfont.css';
import 'codemirror/lib/codemirror.css';
import 'bootstrapvalidator/dist/css/bootstrapValidator.css';
import '../css/tailwind-base.css';
import {useEffect, useMemo} from 'react';
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
import GitStatusPanel from '@/frame/panels/GitStatusPanel.tsx';
import AuditLogPanel from '@/frame/panels/AuditLogPanel.tsx';
import DatasourcePanel from '@/datasource/index.tsx';
import PlaceholderPanel from '@/frame/panels/PlaceholderPanel.jsx';
import ReleasePanel from '@/release/index.tsx';
import SimulationPanel from '@/simulation/index.tsx';
import AgentPanel from '@/agent/index.tsx';
import UserManagementPanel from '@/admin/UserManagementPanel.tsx';
import Loading from '@/components/loading/component/Loading.tsx';
// V5.8.0: 挂在 frame 顶层而不是 PackageEditor 里,这样从任何 panel
// (FlowEditor / DatasourcePanel / ...) 触发 OPEN_BATCH_TEST_DIALOG 都能开
import BatchTestDialog from '@/package/components/BatchTestDialog.tsx';
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
            return <AgentPanel/>;
        case 'gitStatus':
            return <GitStatusPanel/>;
        case 'userMgmt':
            return <UserManagementPanel/>;
        case 'auditLog':
            return <AuditLogPanel/>;
        case 'settings':
            return <PlaceholderPanel panelId="settings"/>;
        default:
            return <RuleEditorPanel store={store} eventObj={eventObj}/>;
    }
}

const SidePanelConnected = connect((state: { ui?: { activePanel?: string } }) => ({activePanel: (state.ui && state.ui.activePanel) || 'rules'}))(SidePanelSwitcher);

/**
 * V5.8.4+ layout:当 activePanel != 'rules'(用户点了 ActivityBar 切到 DatasourcePanel /
 * MonitoringPanel / ReleasePanel 等专用面板),直接把面板撑满整个 content 区,
 * 不再保留右侧 FrameTab welcome 页(避免"panel + welcome"叠加的视觉混乱)。
 *
 * activePanel === 'rules' 时维持原 Splitter:SidePanelSwitcher 240px + app-content 右侧。
 */
function AppBody({activePanel, store, eventObj}: {activePanel: string; store: Store; eventObj: typeof event}) {
    var contentTabBarRef: any = null;
    var frameTabRef: any = null;

    if (activePanel !== 'rules') {
        // 专用面板激活:撑满 content,没有右侧 welcome
        return <SidePanelConnected store={store} eventObj={eventObj}/>;
    }

    return (
        <Splitter orientation='vertical' position='240px'>
            <SidePanelConnected store={store} eventObj={eventObj}/>
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
    );
}

const AppBodyConnected = connect((state: { ui?: { activePanel?: string } }) => ({activePanel: (state.ui && state.ui.activePanel) || 'rules'}))(AppBody);

/**
 * Frame 主框架组件(SPA 阶段 2:从 DOMContentLoaded 回调提取)。
 *
 * <p>store 创建 + 副作用(loadData / EXPAND_TREE_NODE 监听)随组件生命周期。
 * 被 SPA 路由 /app(经 RequireAuth 守卫) 和 frame.html 独立入口(src/frame/main.tsx) 共享。
 * frame 内部组件仍读 window.__currentUser(RequireAuth / frame.main 都会设置它)。
 */
export default function FrameApp() {
    const store = useMemo(() => {
        window._types = null;
        window._projectName = null;
        window.componentEvent = componentEvent;
        const s = createStore(reducer, applyMiddleware(thunk));
        (s.dispatch as Function)(ACTIONS.loadData());
        return s;
    }, []);

    useEffect(() => {
        const handler = (nodeData: TreeNodeData) => {
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
        };
        event.eventEmitter.on(event.EXPAND_TREE_NODE, handler);
        // FrameApp 常驻(frame 主页不卸载),监听器随页面生命周期;event 模块 EventEmitter 也未暴露 off。
    }, []);

    return (
        <div className="app-layout">
            <Loading show={true}/>
            <Provider store={store}>
                <TopBar/>
                <div className="app-body">
                    <ActivityBar/>
                    <AppBodyConnected store={store} eventObj={event}/>
                </div>
                {/* V5.8.0:全局 BatchTestDialog,任意 panel 触发 OPEN_BATCH_TEST_DIALOG 都能弹 */}
                <BatchTestDialog/>
            </Provider>
        </div>
    );
}
