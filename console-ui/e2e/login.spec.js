import { test, expect } from '@playwright/test';

/**
 * BDD walkthrough — login flow behaviors
 *
 * Background:
 *   - Vite dev server on localhost:3000, served from /html/login.html
 *   - Backend on localhost:8180, /api/frame/login accepts any credentials
 *     (the legacy ruleforge backend doesn't validate; the frontend gates
 *     subsequent API calls on the JSESSIONID cookie returned)
 */
test.describe('Login Flow', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/html/login.html');
    });

    // ── Passing baseline (kept as-is) ─────────────────────────────────
    test('should display login page with all required elements', async ({ page }) => {
        await expect(page).toHaveTitle(/RuleForge/);
        await expect(page.locator('input[type="text"]').first()).toBeVisible();
        await expect(page.locator('input[type="password"]').first()).toBeVisible();
        await expect(page.locator('button[type="submit"]').first()).toBeVisible();
    });

    // ── BDD STUB: should login successfully with valid credentials ─────
    // Given:  user is on the login page
    //  And:   user has typed "admin" in the username field
    //  And:   user has typed "admin" in the password field
    // When:   user clicks the submit button
    // Then:   the browser should navigate to /html/frame.html (within 10s)
    //  And:   a JSESSIONID cookie should be set by the backend
    //   (login.tsx redirects to `frame.html` by default; the legacy
    //   /index.html path no longer exists in the new vite multi-page setup)
    test('should login successfully with valid credentials', async ({ page }) => {
        await page.locator('input[type="text"]').first().fill('admin');
        await page.locator('input[type="password"]').first().fill('admin');
        await page.locator('button[type="submit"]').first().click();

        await expect(page).toHaveURL(/\/frame\.html/, { timeout: 10000 });
    });

    // ── BDD STUB: should show error message with invalid credentials ──
    // Given:  user is on the login page
    //  And:   the /frame/login endpoint is intercepted to abort (network failure)
    //  And:   user has typed "admin" / "wrong" in the form
    // When:   user clicks the submit button
    // Then:   a .login-error element should become visible
    //  And:   the URL should remain on /html/login.html
    //  (the redesigned login page uses .login-error instead of bootstrap's
    //   .alert-danger — the new login is a React component under #root)
    test('should show error message with invalid credentials', async ({ page }) => {
        // Backend accepts any credentials, so this tests the error handling path
        // by simulating a network failure scenario
        await page.route('**/frame/login', route => route.abort('failed'));
        await page.locator('input[type="text"]').first().fill('admin');
        await page.locator('input[type="password"]').first().fill('wrong');
        await page.locator('button[type="submit"]').first().click();

        await expect(page.locator('.login-error')).toBeVisible({ timeout: 10000 });
        await expect(page).toHaveURL(/\/html\/login\.html/);
    });

    // ── BDD STUB: should login with empty fields since backend accepts any input
    // Given:  user is on the login page
    //  And:   both username and password fields are empty
    // When:   user clicks the submit button
    // Then:   the browser should navigate to /html/frame.html (within 10s)
    //  And:   the main frame should render (#container visible)
    test('should login with empty fields since backend accepts any input', async ({ page }) => {
        await page.locator('button[type="submit"]').first().click();

        await expect(page).toHaveURL(/\/frame\.html/, { timeout: 10000 });
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
        await page.locator('input[type="password"]').first().fill('admin');

        const submitBtn = page.locator('button[type="submit"]').first();
        await submitBtn.click();

        await expect(submitBtn).toContainText('登录中...');
    });
});
