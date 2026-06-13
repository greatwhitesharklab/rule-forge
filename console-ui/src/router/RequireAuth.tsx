import {createContext, useEffect, useState} from 'react';
import {Navigate, Outlet} from 'react-router-dom';
import {formPost} from '@/api/client';

/**
 * 当前登录用户 Context。
 *
 * <p>SPA 阶段 2 起,frame 内部组件从 {@code window.__currentUser} 全局改读这个 Context。
 * 由 {@link RequireAuth} 守卫在鉴权通过后注入。
 */
export const CurrentUserContext = createContext<UserInfo | null>(null);

type AuthState = 'loading' | 'ok' | 'unauth';

/**
 * SPA 鉴权守卫(spa-migration-plan.md 阶段 0)。
 *
 * <p>异步检查 {@code POST /frame/currentUser}:
 * <ul>
 *   <li>loading — 显示加载态</li>
 *   <li>未登录(status=false 或请求失败)— {@code <Navigate to="/login"/>}</li>
 *   <li>已登录 — {@link CurrentUserContext.Provider} 包裹 {@code <Outlet/>}</li>
 * </ul>
 *
 * <p>替代 frame.html 的同步 XHR 鉴权(line 14-28,阻塞渲染),改为异步路由守卫。
 */
export function RequireAuth() {
    const [state, setState] = useState<AuthState>('loading');
    const [user, setUser] = useState<UserInfo | null>(null);

    useEffect(() => {
        formPost<{status: boolean; user?: UserInfo}>('/frame/currentUser', {}, {silent: true})
            .then((res) => {
                if (res.status && res.user) {
                    // 兼容 frame 内部组件(TopBar/SidebarToolbar)读 window.__currentUser
                    window.__currentUser = res.user;
                    setUser(res.user);
                    setState('ok');
                } else {
                    setState('unauth');
                }
            })
            .catch(() => setState('unauth'));
    }, []);

    if (state === 'loading') {
        return <div data-testid="require-auth-loading">加载中…</div>;
    }
    if (state === 'unauth') {
        return <Navigate to="/login" replace/>;
    }
    return (
        <CurrentUserContext.Provider value={user}>
            <Outlet/>
        </CurrentUserContext.Provider>
    );
}
