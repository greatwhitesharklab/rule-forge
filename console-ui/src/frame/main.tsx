import {createRoot} from 'react-dom/client';
import FrameApp from './index.tsx';

/**
 * frame.html 独立入口(SPA 之前的访问方式 + 回退)。
 *
 * <p>frame.html 的 inline script 已设置 window._server/_welcomePage 并完成同步 XHR 鉴权
 * (未登录跳 login.html + 设 window.__currentUser)。本文件只把 FrameApp 挂到 #container。
 *
 * <p>SPA 根入口是 /src/main.tsx,经 /app 路由 + RequireAuth 异步鉴权渲染同一个 FrameApp。
 */
createRoot(document.getElementById('container')!).render(<FrameApp/>);
