import {createRoot} from 'react-dom/client';
import {lazy, Suspense} from 'react';
import {BrowserRouter, Routes, Route, Navigate} from 'react-router-dom';
import LoginPage from '@/login';
import {RequireAuth} from '@/router/RequireAuth';

/**
 * SPA 根入口(spa-migration-plan.md 阶段 1-2)。
 *
 * <p>根 {@code index.html} → 本文件 → {@link BrowserRouter}。
 * <ul>
 *   <li>{@code /} → {@code <Navigate to="/login"/>}</li>
 *   <li>{@code /login} → {@link LoginPage}</li>
 *   <li>{@code /app} → {@link RequireAuth}(异步鉴权) → {@link FrameApp}(lazy,frame bundle 按需加载)</li>
 * </ul>
 *
 * <p>{@code login.html}/{@code frame.html} 仍可独立访问(MPA 回退,editor 阶段 3 前必需)。
 */
const FrameApp = lazy(() => import('@/frame'));
const VariableEditorRoute = lazy(() => import('@/variable/EditorRoute'));
const ConstantEditorRoute = lazy(() => import('@/constant/EditorRoute'));
const ParameterEditorRoute = lazy(() => import('@/parameter/EditorRoute'));
const ActionEditorRoute = lazy(() => import('@/action/EditorRoute'));
const ResourceEditorRoute = lazy(() => import('@/resource/EditorRoute'));
const PackageEditorRoute = lazy(() => import('@/package/EditorRoute'));
const ClientEditorRoute = lazy(() => import('@/client/EditorRoute'));
const PermissionEditorRoute = lazy(() => import('@/permission/EditorRoute'));
const DrlEditorRoute = lazy(() => import('@/editor/drleditor/EditorRoute'));
const FlowEditorRoute = lazy(() => import('@/flow-bpmn/EditorRoute'));
// V6.20.0 P3:DMN / PMML 只读源查看器
const DmnEditorRoute = lazy(() => import('@/editor/dmn/EditorRoute'));
const PmmlEditorRoute = lazy(() => import('@/editor/pmml/EditorRoute'));
// V7.0.0 item ④:老 7 编辑器(ruleset/decisiontable/scriptdecisiontable/scorecard/
// complexscorecard/crosstab/decisiontree)已下线 — V6.20.0 P2 从文件树入口移除,
// V1 画布接管 RuleSet/DecisionTable/ScoreCard,其余 4 类决策已弃。物理删除目录见本 commit。
const V1FlowDesignerRoute = lazy(() => import('@/v1-flow/EditorRoute'));
const V1LibraryEditorRoute = lazy(() => import('@/v1-flow/LibraryEditorRoute'));

createRoot(document.getElementById('root')!).render(
    <BrowserRouter>
        <Routes>
            <Route path="/" element={<Navigate to="/login" replace/>}/>
            <Route path="/login" element={<LoginPage/>}/>
            {/* V7.0.0:V1 决策流设计器 demo 路由(纯客户端画布,免登录,不调后端)。
                生产集成(保存到项目树)后再移进 /app RequireAuth 内。 */}
            <Route path="/v1-flow" element={<Suspense fallback={<div style={{padding: 24}}>加载中…</div>}><V1FlowDesignerRoute/></Suspense>}/>
            <Route path="/app" element={<RequireAuth/>}>
                <Route index element={<Suspense fallback={<div style={{padding: 24}}>加载中…</div>}><FrameApp/></Suspense>}/>
                <Route path="editor/variable" element={<VariableEditorRoute/>}/>
                <Route path="editor/constant" element={<ConstantEditorRoute/>}/>
                <Route path="editor/parameter" element={<ParameterEditorRoute/>}/>
                <Route path="editor/action" element={<ActionEditorRoute/>}/>
                <Route path="editor/resource" element={<ResourceEditorRoute/>}/>
                <Route path="editor/package" element={<PackageEditorRoute/>}/>
                <Route path="editor/client" element={<ClientEditorRoute/>}/>
                <Route path="editor/permission" element={<PermissionEditorRoute/>}/>
                <Route path="editor/drl" element={<DrlEditorRoute/>}/>
                <Route path="editor/flow" element={<FlowEditorRoute/>}/>
                <Route path="editor/dmn" element={<DmnEditorRoute/>}/>
                <Route path="editor/pmml" element={<PmmlEditorRoute/>}/>
                {/* V7.0.0:V1 决策流设计器(独立全屏画布,不走 frame) */}
                <Route path="v1-flow" element={<V1FlowDesignerRoute/>}/>
                {/* V7.4:V1 库编辑器(vl/cl/pl 四库,从项目树"V1库"分类进入) */}
                <Route path="v1-library" element={<V1LibraryEditorRoute/>}/>
            </Route>
        </Routes>
    </BrowserRouter>,
);
