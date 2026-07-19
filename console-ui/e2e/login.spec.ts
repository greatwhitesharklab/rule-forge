import { test, expect } from '@playwright/test';

/**
 * BDD walkthrough — login flow behaviors
 *
 * Background:
 *   - SPA on PLAYWRIGHT_BASE_URL, login page at /login
 *   - Backend on localhost:8180, /api/frame/login 真实校验口令
 *     (合法账号 admin/admin123;成功后前端跳 /app)
 */
test.describe('Login Flow', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/login');
    });

    // ── Passing baseline (kept as-is) ─────────────────────────────────
    test('should display login page with all required elements', async ({ page }) => {
        await expect(page).toHaveTitle(/RuleForge/);
        await expect(page.locator('input[type="text"]').first()).toBeVisible();
        await expect(page.locator('input[type="password"]').first()).toBeVisible();
        await expect(page.locator('button[type="submit"]').first()).toBeVisible();
    });

    // Given:  user is on the login page
    //  And:   user has typed "admin" / "admin123" in the form
    // When:   user clicks the submit button
    // Then:   the browser should navigate to /app (within 10s)
    //  (SPA 阶段 5:登录成功 window.location.href 跳 /app,frame.html 已删;
    //   后端 /frame/login 现在真实校验口令,合法账号是 admin/admin123)
    test('should login successfully with valid credentials', async ({ page }) => {
        await page.locator('input[type="text"]').first().fill('admin');
        await page.locator('input[type="password"]').first().fill('admin123');
        await page.locator('button[type="submit"]').first().click();

        await expect(page).toHaveURL(/\/app/, { timeout: 10000 });
    });

    // Given:  user is on the login page
    //  And:   user has typed "admin" / "wrong" in the form
    // When:   user clicks the submit button
    // Then:   a .login-error element should become visible
    //  And:   the URL should remain on /login
    //  (后端现在真实校验口令,错误口令返回 status:false → .login-error 显示)
    test('should show error message with invalid credentials', async ({ page }) => {
        await page.locator('input[type="text"]').first().fill('admin');
        await page.locator('input[type="password"]').first().fill('wrong');
        await page.locator('button[type="submit"]').first().click();

        await expect(page.locator('.login-error')).toBeVisible({ timeout: 10000 });
        await expect(page).toHaveURL(/\/login/);
    });

    // Given:  user is on the login page
    //  And:   both username and password fields are empty
    // When:   user clicks the submit button
    // Then:   a .login-error element should become visible
    //  And:   the URL should remain on /login
    //  (后端真实校验口令后,空字段同样返回 status:false)
    test('should show error message when submitting empty fields', async ({ page }) => {
        await page.locator('button[type="submit"]').first().click();

        await expect(page.locator('.login-error')).toBeVisible({ timeout: 10000 });
        await expect(page).toHaveURL(/\/login/);
    });

    // ── BDD STUB: should show loading state while logging in ──────────
    // Given:  user is on the login page
    //  And:   user has typed valid credentials in the form
    // When:   user clicks the submit button
    // Then:   the button text should immediately change to "登录中..."
    //  And:   after the request completes, the button should revert
    //  (Flaky fix: 用 page.route() 给 /api/frame/login 注入 200ms 延迟,
    //  让 "登录中..." loading 状态可被 observe。本机后端 <50ms 就响应了,
    //  之前靠时间窗撞运气,现在稳定慢 200ms。)
    test('should show loading state while logging in', async ({ page }) => {
        // 注入 200ms 延迟,让 loading state 可观察
        await page.route('**/api/frame/login', async (route) => {
            await new Promise((resolve) => setTimeout(resolve, 200));
            await route.continue();
        });

        await page.locator('input[type="text"]').first().fill('admin');
        await page.locator('input[type="password"]').first().fill('admin123');

        const submitBtn = page.locator('button[type="submit"]').first();
        await submitBtn.click();

        await expect(submitBtn).toContainText('登录中...');
    });
});
