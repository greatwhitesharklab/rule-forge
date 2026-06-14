import {useEffect, useState, type ReactNode} from 'react';
import {formPost} from '@/api/client';
import {CurrentUserContext} from './RequireAuth';

/**
 * frame.html 独立入口的鉴权网关(V5.74.2 新增)。
 *
 * <p>背景:frame.html 是 SPA 之前的入口,原本在 inline script 里做同步 XHR
 * (`POST ../api/frame/currentUser`),未登录跳 `login.html`,已登录设
 * `window.__currentUser`。{@code FrameApp} 内部从该全局读 user。
 *
 * <p>V5.74.2 把这段鉴权搬到 React 里:
 * <ul>
 *   <li>{@code frame.html} 不再做同步 XHR,只挂载本组件</li>
 *   <li>本组件用 {@code formPost} 异步拉当前用户</li>
 *   <li>未登录 — {@code window.location.href = 'login.html?redirect=...'}
 *       (保留原 redirect 行为,让 LoginPage 登录后回跳)</li>
 *   <li>已登录 — 用 {@link CurrentUserContext.Provider} 渲染 children</li>
 * </ul>
 *
 * <p>跟 SPA 的 {@link RequireAuth} 不同:frame.html 没有 react-router,
 * 鉴权失败不能用 {@code <Navigate/>},只能整页跳 login.html。
 *
 * <p>本组件仅在 {@code src/frame/main.tsx} 入口用一次,FrameApp 自身不再挂
 * 内层 Provider(由本组件统一供值)。
 */
type AuthState = 'loading' | 'ok' | 'unauth';

export function LegacyAuthGate({children}: {children: ReactNode}) {
    const [state, setState] = useState<AuthState>('loading');
    const [user, setUser] = useState<UserInfo | null>(null);

    useEffect(() => {
        formPost<{status: boolean; user?: UserInfo}>('/frame/currentUser', {}, {silent: true})
            .then((res) => {
                if (res.status && res.user) {
                    setUser(res.user);
                    setState('ok');
                } else {
                    setState('unauth');
                }
            })
            .catch(() => setState('unauth'));
    }, []);

    if (state === 'loading') {
        return <div data-testid="legacy-auth-loading">加载中…</div>;
    }
    if (state === 'unauth') {
        // 保留原 frame.html 行为:整页跳 login.html(无 react-router 可用)。
        // redirect param 让 LoginPage 登录成功后回跳当前 URL。
        window.location.href =
            'login.html?redirect=' + encodeURIComponent(window.location.pathname);
        return null;
    }
    return (
        <CurrentUserContext.Provider value={user}>
            {children}
        </CurrentUserContext.Provider>
    );
}
