import { test, expect } from '@playwright/test';

/**
 * BDD walkthrough — root page smoke tests
 *
 * Background:
 *   - Vite dev server on localhost:3000 serves the React app from /
 *   - Vite multi-page config: /index.html is the main frame entry
 *   - For the empty / to serve something, vite typically redirects or
 *     returns the default index page
 */
test.describe('RuleForge 前端', () => {

    // ── BDD STUB: 首页应可访问 ─────────────────────────────────────────
    // Given:  RuleForge 前端 vite dev server 已启动并响应
    // When:   客户端向 GET /html/frame.html 发起请求(vite 多页应用入口)
    // Then:   服务器应返回 HTTP 200(frame.html 由 vite 静态服务)
    test('首页应可访问', async ({ page }) => {
        const response = await page.goto('/html/frame.html');
        expect(response.status()).toBe(200);
    });

    // ── BDD STUB: 页面应加载成功 ───────────────────────────────────────
    // Given:  RuleForge 前端 vite dev server 已启动
    // When:   浏览器导航到 /html/frame.html(主框架入口)
    // Then:   页面 body 元素应可见(说明没有崩溃,JS 至少跑了初始化)
    test('页面应加载成功', async ({ page }) => {
        await page.goto('/html/frame.html');
        // 只要页面加载不报错即可
        const body = page.locator('body');
        await expect(body).toBeVisible();
    });
});
