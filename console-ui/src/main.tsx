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
const EditorRoute = lazy(() => import('@/editor/ruleforge/react/EditorRoute'));
const VariableEditorRoute = lazy(() => import('@/variable/EditorRoute'));
const ConstantEditorRoute = lazy(() => import('@/constant/EditorRoute'));
const ParameterEditorRoute = lazy(() => import('@/parameter/EditorRoute'));
const ActionEditorRoute = lazy(() => import('@/action/EditorRoute'));
const ResourceEditorRoute = lazy(() => import('@/resource/EditorRoute'));
const PackageEditorRoute = lazy(() => import('@/package/EditorRoute'));
const ClientEditorRoute = lazy(() => import('@/client/EditorRoute'));
const PermissionEditorRoute = lazy(() => import('@/permission/EditorRoute'));
const DrlEditorRoute = lazy(() => import('@/editor/drleditor/EditorRoute'));
const DecisionTableEditorRoute = lazy(() => import('@/editor/decisiontable/react/EditorRoute'));

createRoot(document.getElementById('root')!).render(
    <BrowserRouter>
        <Routes>
            <Route path="/" element={<Navigate to="/login" replace/>}/>
            <Route path="/login" element={<LoginPage/>}/>
            <Route path="/app" element={<RequireAuth/>}>
                <Route index element={<Suspense fallback={<div style={{padding: 24}}>加载中…</div>}><FrameApp/></Suspense>}/>
                <Route path="editor/ruleset" element={<EditorRoute/>}/>
                <Route path="editor/variable" element={<VariableEditorRoute/>}/>
                <Route path="editor/constant" element={<ConstantEditorRoute/>}/>
                <Route path="editor/parameter" element={<ParameterEditorRoute/>}/>
                <Route path="editor/action" element={<ActionEditorRoute/>}/>
                <Route path="editor/resource" element={<ResourceEditorRoute/>}/>
                <Route path="editor/package" element={<PackageEditorRoute/>}/>
                <Route path="editor/client" element={<ClientEditorRoute/>}/>
                <Route path="editor/permission" element={<PermissionEditorRoute/>}/>
                <Route path="editor/drl" element={<DrlEditorRoute/>}/>
                <Route path="editor/decisiontable" element={<DecisionTableEditorRoute/>}/>
            </Route>
        </Routes>
    </BrowserRouter>,
);
