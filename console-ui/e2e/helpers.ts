/**
 * Test helpers for Playwright e2e tests.
 *
 * V5.74.6:已删 frame.html / login.html MPA 入口,统一走 SPA 根路径:
 *   /login → LoginPage,/app → RequireAuth → FrameApp。
 * Vite 仍把 /api 代理到 8180 后端的 /ruleforge。
 */

import type { Page } from '@playwright/test';

export async function login(page: Page) {
    // Navigate to /login 路由建立 browser context(JSESSIONID 写到这里)。
    await page.goto('/login');

    // Authenticate by making a login API call from within the page's browser context.
    // This ensures the JSESSIONID cookie is set in the browser context that the page uses.
    // We use the absolute path /api/frame/login because the Vite proxy rewrites /api → backend.
    await page.evaluate(async () => {
        const response = await fetch('/api/frame/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ username: 'admin', password: 'admin123' }).toString()
        });
        const result = await response.json();
        if (!result.status) {
            throw new Error('Login failed: ' + JSON.stringify(result));
        }
    });
}

/**
 * Login and navigate to the main frame page (SPA /app route)。
 */
export async function loginAndGotoFrame(page: Page) {
    await login(page);
    await page.goto('/app');
    // SPA 模式 FrameApp 根 div className 是 'app-layout'(FrameApp render() 的第一行 div)。
    // 老 MPA frame.html 的 #container 元素已不存在。
    await page.waitForSelector('.app-layout', { timeout: 10000 });
}
