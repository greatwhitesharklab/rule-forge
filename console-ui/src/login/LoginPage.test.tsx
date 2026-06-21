import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, fireEvent, screen, waitFor } from '@testing-library/react';

// Mock the API client module used by the real LoginPage.
// The real component uses `formPost('/frame/login', ...)` from ../api/client.js.
const { formPost } = vi.hoisted(() => ({
    formPost: vi.fn(),
}));

vi.mock('../api/client.js', () => ({
    formPost: formPost,
}));

import LoginPage from './index';

describe('V6.12.2 — LoginPage (real component, not mock)', () => {
    let originalLocation: Location;
    let formPostMock: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        formPostMock = formPost as ReturnType<typeof vi.fn>;
        formPostMock.mockReset();
        // Mock window.location so the redirect target is observable.
        originalLocation = window.location;
        delete (window as { location?: unknown }).location;
        (window as unknown as Record<string, unknown>).location = {
            href: '',
            search: '',
        };
    });

    afterEach(() => {
        (window as { location?: unknown }).location = originalLocation;
        vi.restoreAllMocks();
    });

    it('GIVEN the login page WHEN rendered THEN shows brand, two inputs, and submit button', () => {
        render(<LoginPage />);
        expect(screen.getByText('RuleForge')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('请输入用户名')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('请输入密码')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: '登 录' })).toBeInTheDocument();
    });

    it('GIVEN username input WHEN user types THEN state updates', () => {
        render(<LoginPage />);
        const input = screen.getByPlaceholderText('请输入用户名') as HTMLInputElement;
        fireEvent.change(input, { target: { value: 'admin' } });
        expect(input.value).toBe('admin');
    });

    it('GIVEN password input WHEN user types THEN state updates', () => {
        render(<LoginPage />);
        const input = screen.getByPlaceholderText('请输入密码') as HTMLInputElement;
        fireEvent.change(input, { target: { value: 'secret' } });
        expect(input.value).toBe('secret');
    });

    it('GIVEN valid credentials WHEN form submitted THEN formPost called with /frame/login and form data', async () => {
        formPostMock.mockResolvedValue({ status: true, data: undefined, message: '' });
        render(<LoginPage />);

        fireEvent.change(screen.getByPlaceholderText('请输入用户名'), { target: { value: 'admin' } });
        fireEvent.change(screen.getByPlaceholderText('请输入密码'), { target: { value: 'secret' } });
        fireEvent.click(screen.getByRole('button', { name: '登 录' }));

        await waitFor(() => {
            expect(formPostMock).toHaveBeenCalledTimes(1);
        });
        expect(formPostMock.mock.calls[0][0]).toBe('/frame/login');
        expect(formPostMock.mock.calls[0][1]).toEqual({ username: 'admin', password: 'secret' });
    });

    it('GIVEN login success (status true) and no redirect param WHEN submitted THEN window.location.href set to /app', async () => {
        formPostMock.mockResolvedValue({ status: true, data: undefined, message: '' });
        (window as unknown as { location: { href: string; search: string } }).location.search = '';
        render(<LoginPage />);

        fireEvent.change(screen.getByPlaceholderText('请输入用户名'), { target: { value: 'admin' } });
        fireEvent.change(screen.getByPlaceholderText('请输入密码'), { target: { value: 'secret' } });
        fireEvent.click(screen.getByRole('button', { name: '登 录' }));

        await waitFor(() => {
            expect((window as unknown as { location: { href: string } }).location.href).toBe('/app');
        });
    });

    it('GIVEN login success with ?redirect= param WHEN submitted THEN window.location.href set to redirect target', async () => {
        formPostMock.mockResolvedValue({ status: true, data: undefined, message: '' });
        (window as unknown as { location: { href: string; search: string } }).location.search = '?redirect=/app/editor';
        render(<LoginPage />);

        fireEvent.change(screen.getByPlaceholderText('请输入用户名'), { target: { value: 'admin' } });
        fireEvent.change(screen.getByPlaceholderText('请输入密码'), { target: { value: 'secret' } });
        fireEvent.click(screen.getByRole('button', { name: '登 录' }));

        await waitFor(() => {
            expect((window as unknown as { location: { href: string } }).location.href).toBe('/app/editor');
        });
    });

    it('GIVEN login failure (status false) WHEN submitted THEN displays 登录失败 error', async () => {
        formPostMock.mockResolvedValue({ status: false, data: undefined, message: '' });
        render(<LoginPage />);

        fireEvent.change(screen.getByPlaceholderText('请输入用户名'), { target: { value: 'admin' } });
        fireEvent.change(screen.getByPlaceholderText('请输入密码'), { target: { value: 'wrong' } });
        fireEvent.click(screen.getByRole('button', { name: '登 录' }));

        await waitFor(() => {
            expect(screen.getByText('登录失败')).toBeInTheDocument();
        });
    });

    it('GIVEN formPost rejects (network error) WHEN submitted THEN displays 登录失败，请检查用户名和密码', async () => {
        formPostMock.mockRejectedValue(new Error('Network error'));
        render(<LoginPage />);

        fireEvent.change(screen.getByPlaceholderText('请输入用户名'), { target: { value: 'admin' } });
        fireEvent.change(screen.getByPlaceholderText('请输入密码'), { target: { value: 'secret' } });
        fireEvent.click(screen.getByRole('button', { name: '登 录' }));

        await waitFor(() => {
            expect(screen.getByText('登录失败，请检查用户名和密码')).toBeInTheDocument();
        });
    });

    it('GIVEN click submit WHEN formPost is in-flight THEN button shows 登录中... and is disabled', async () => {
        let resolveFn: (v: unknown) => void = () => {};
        formPostMock.mockReturnValue(new Promise((r) => { resolveFn = r; }));
        render(<LoginPage />);

        const button = screen.getByRole('button', { name: '登 录' });
        fireEvent.click(button);

        await waitFor(() => {
            expect(screen.getByText('登录中...')).toBeInTheDocument();
        });
        expect((button as HTMLButtonElement).disabled).toBe(true);

        // Resolve to clean up.
        resolveFn({ status: true, data: undefined, message: '' });
    });
});
