import '@/css/iconfont.css';
import 'codemirror/lib/codemirror.css';
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
// V7.7.2:BatchTestDialog 删除 — 老 .rp 知识包批测废弃,V1 决策流不走批测
import * as event from '@/frame/event.js';
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
        // V5.101:wrap 在 flex:1 容器里 —— 否则面板在 app-body(flex row)里塌成内容宽度
        // (实测 monitoring 只有 117px,因为面板自身没 flex:1)。host 撑满,面板撑满 host。
        return (
            <div className="app-panel-host">
                <SidePanelConnected store={store} eventObj={eventObj}/>
            </div>
        );
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
 * 由 SPA 路由 /app(经 RequireAuth 守卫)挂载;frame.html 独立入口已随阶段 5 删除。
 *
 * <p>当前用户由父级 {@link CurrentUserContext.Provider} 提供(RequireAuth 的 Provider
 * 包裹 FrameApp)。FrameApp 自身不再挂 Provider(V5.74.2 改造)。
 */
export default function FrameApp() {
    const store = useMemo(() => {
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
                {/* V7.7.2:BatchTestDialog 删除(.rp 废弃) */}
            </Provider>
        </div>
    );
}
