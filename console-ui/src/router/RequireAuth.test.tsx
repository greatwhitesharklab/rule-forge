import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, waitFor} from '@testing-library/react';
import {MemoryRouter, Routes, Route} from 'react-router-dom';
import {RequireAuth, CurrentUserContext} from './RequireAuth';

vi.mock('@/api/client', () => ({
    formPost: vi.fn(),
}));
import {formPost} from '@/api/client';

function renderProtected() {
    return render(
        <MemoryRouter initialEntries={['/protected']}>
            <Routes>
                <Route path="/protected" element={<RequireAuth/>}>
                    <Route index element={<div data-testid="protected-content">受保护内容</div>}/>
                </Route>
                <Route path="/login" element={<div data-testid="login-page">登录页</div>}/>
            </Routes>
        </MemoryRouter>,
    );
}

describe('RequireAuth — SPA 鉴权守卫 (阶段 0)', () => {
    beforeEach(() => vi.clearAllMocks());

    it('GIVEN currentUser 返回已登录 WHEN 渲染 THEN 渲染受保护子路由(Outlet)', async () => {
        vi.mocked(formPost).mockResolvedValue({status: true, user: {username: 'admin', admin: true}} as never);
        const {getByTestId} = renderProtected();
        await waitFor(() => expect(getByTestId('protected-content')).toBeTruthy());
    });

    it('GIVEN currentUser 返回未登录(status=false) WHEN 渲染 THEN 重定向到 /login', async () => {
        vi.mocked(formPost).mockResolvedValue({status: false} as never);
        const {getByTestId} = renderProtected();
        await waitFor(() => expect(getByTestId('login-page')).toBeTruthy());
    });

    it('GIVEN currentUser 请求失败 WHEN 渲染 THEN 重定向到 /login', async () => {
        vi.mocked(formPost).mockRejectedValue(new Error('network'));
        const {getByTestId} = renderProtected();
        await waitFor(() => expect(getByTestId('login-page')).toBeTruthy());
    });

    it('GIVEN 已登录 WHEN 渲染 THEN 通过 CurrentUserContext 提供当前用户', async () => {
        vi.mocked(formPost).mockResolvedValue({status: true, user: {username: 'alice'}} as never);
        let ctxUser: UserInfo | null = null;
        render(
            <MemoryRouter initialEntries={['/protected']}>
                <Routes>
                    <Route path="/protected" element={<RequireAuth/>}>
                        <Route index element={
                            <CurrentUserContext.Consumer>
                                {(u) => { ctxUser = u; return <div data-testid="consumer">consumer</div>; }}
                            </CurrentUserContext.Consumer>
                        }/>
                    </Route>
                </Routes>
            </MemoryRouter>,
        );
        await waitFor(() => expect(ctxUser).not.toBeNull());
        expect((ctxUser as UserInfo).username).toBe('alice');
    });
});
