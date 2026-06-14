import {describe, it, expect, vi, beforeEach, afterEach} from 'vitest';
import {render, waitFor} from '@testing-library/react';
import {LegacyAuthGate} from './LegacyAuthGate';
import {CurrentUserContext} from './RequireAuth';

vi.mock('@/api/client', () => ({
    formPost: vi.fn(),
}));
import {formPost} from '@/api/client';

describe('LegacyAuthGate — frame.html 入口鉴权网关 (V5.74.2)', () => {
    const originalHref = window.location.href;

    beforeEach(() => {
        vi.clearAllMocks();
        // jsdom 不允许直接赋值 location.href,改用 Object.defineProperty。
        Object.defineProperty(window, 'location', {
            value: {href: originalHref, pathname: '/html/frame.html', search: ''},
            writable: true,
            configurable: true,
        });
    });

    afterEach(() => {
        Object.defineProperty(window, 'location', {
            value: {href: originalHref, pathname: '/', search: ''},
            writable: true,
            configurable: true,
        });
    });

    it('GIVEN 鉴权中 WHEN 渲染 THEN 显示加载态', () => {
        vi.mocked(formPost).mockReturnValue(new Promise(() => {/* pending */}) as never);
        const {getByTestId} = render(<LegacyAuthGate><div data-testid="child">child</div></LegacyAuthGate>);
        expect(getByTestId('legacy-auth-loading')).toBeTruthy();
    });

    it('GIVEN currentUser 返回已登录 WHEN 渲染 THEN 渲染 children 并注入 CurrentUserContext', async () => {
        vi.mocked(formPost).mockResolvedValue({status: true, user: {username: 'bob'}} as never);
        let ctxUser: UserInfo | null = null;
        render(
            <LegacyAuthGate>
                <CurrentUserContext.Consumer>
                    {(u) => { ctxUser = u; return <div data-testid="child">child</div>; }}
                </CurrentUserContext.Consumer>
            </LegacyAuthGate>,
        );
        await waitFor(() => expect(ctxUser).not.toBeNull());
        expect((ctxUser as UserInfo).username).toBe('bob');
    });

    it('GIVEN currentUser 返回未登录(status=false) WHEN 渲染 THEN 整页跳 login.html?redirect=...', async () => {
        vi.mocked(formPost).mockResolvedValue({status: false} as never);
        render(<LegacyAuthGate><div data-testid="child">child</div></LegacyAuthGate>);
        await waitFor(() => {
            expect(window.location.href).toContain('login.html?redirect=');
        });
    });

    it('GIVEN currentUser 请求失败 WHEN 渲染 THEN 整页跳 login.html', async () => {
        vi.mocked(formPost).mockRejectedValue(new Error('network') as never);
        render(<LegacyAuthGate><div data-testid="child">child</div></LegacyAuthGate>);
        await waitFor(() => {
            expect(window.location.href).toContain('login.html?redirect=');
        });
    });
});
