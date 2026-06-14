import {createRoot} from 'react-dom/client';
import FrameApp from './index.tsx';
import {LegacyAuthGate} from '@/router/LegacyAuthGate';

/**
 * frame.html 独立入口(SPA 之前的访问方式 + 回退,V5.74.6 删除)。
 *
 * <p>挂载流程:
 * <ul>
 *   <li>frame.html(已删同步 XHR,见 commit 035c59925 系列)— 单一 inline 脚本设
 *       {@code window._server} / {@code window._welcomePage},再挂载本文件</li>
 *   <li>{@link LegacyAuthGate} 异步鉴权({@code POST /frame/currentUser}):
 *       <ul>
 *         <li>未登录 — 整页跳 {@code login.html?redirect=...}</li>
 *         <li>已登录 — {@link CurrentUserContext.Provider} 包裹 {@code FrameApp}</li>
 *       </ul>
 *   </li>
 *   <li>{@code FrameApp} 内部不再读 {@code window.__currentUser},由 Provider 供值</li>
 * </ul>
 *
 * <p>SPA 根入口是 /src/main.tsx,经 /app 路由 + RequireAuth 异步鉴权渲染同一个 FrameApp。
 */
createRoot(document.getElementById('container')!).render(
    <LegacyAuthGate>
        <FrameApp/>
    </LegacyAuthGate>,
);
