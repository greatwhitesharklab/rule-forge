import {test} from '@playwright/test';

/**
 * V5.9.0 Login 视觉 tour
 *  3 个状态: empty / 错误 / 加载中
 *  截图真实 login.html(不通过 helpers 注入 session)
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

test.describe('Login tour', () => {
    test.beforeEach(async ({context}) => {
        // 清掉所有 cookie,确保 login 页正常显示
        await context.clearCookies();
    });

    test('login-empty', async ({page}) => {
        await page.goto('/html/login.html');
        await page.waitForSelector('.login-container', {timeout: 5000});
        await page.waitForTimeout(500);
        await page.screenshot({path: `${SHOT_DIR}/login-empty.png`, fullPage: false});
    });

    test('login-error', async ({page}) => {
        // dev 后端会自动 auth,需要 mock 返 status:false
        await page.route('**/api/frame/login', async (route) => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({status: false, message: '用户名或密码错误'}),
            });
        });
        await page.goto('/html/login.html');
        await page.waitForSelector('.login-container', {timeout: 5000});
        await page.fill('input[type="text"]', 'wronguser');
        await page.fill('input[type="password"]', 'wrongpassword');
        await page.click('.login-submit-btn');
        // 等错误信息出现
        await page.waitForSelector('.login-error', {timeout: 5000});
        await page.waitForTimeout(500);
        await page.screenshot({path: `${SHOT_DIR}/login-error.png`, fullPage: false});
    });

    test('login-loading', async ({page}) => {
        await page.goto('/html/login.html');
        await page.waitForSelector('.login-container', {timeout: 5000});
        await page.fill('input[type="text"]', 'admin');
        await page.fill('input[type="password"]', 'admin');
        // 用 route 把请求延迟,确保能截到 loading 状态
        await page.route('**/api/frame/login', async (route) => {
            await new Promise((r) => setTimeout(r, 1500));
            await route.continue();
        });
        // 不等结果,直接点 + 截图
        await page.click('.login-submit-btn');
        await page.waitForSelector('.login-btn-spinner', {timeout: 2000});
        await page.waitForTimeout(300);
        await page.screenshot({path: `${SHOT_DIR}/login-loading.png`, fullPage: false});
    });
});
