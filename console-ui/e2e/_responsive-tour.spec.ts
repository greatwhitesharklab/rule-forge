import {test} from '@playwright/test';
import {login} from './helpers';

/**
 * V5.9.0 响应式 tour
 *  覆盖 3 个 viewport: desktop / tablet / mobile
 *  (dark mode 未实现,跳过)
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

test.describe('Responsive tour', () => {
    test('desktop-frame', async ({page}) => {
        await page.setViewportSize({width: 1440, height: 900});
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        await page.screenshot({path: `${SHOT_DIR}/resp-desktop-frame.png`, fullPage: false});
    });

    test('tablet-frame', async ({page}) => {
        await page.setViewportSize({width: 768, height: 1024});
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        await page.screenshot({path: `${SHOT_DIR}/resp-tablet-frame.png`, fullPage: false});
    });

    test('mobile-frame', async ({page}) => {
        await page.setViewportSize({width: 375, height: 667});
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        await page.screenshot({path: `${SHOT_DIR}/resp-mobile-frame.png`, fullPage: false});
    });

    test('mobile-login', async ({page}) => {
        await page.setViewportSize({width: 375, height: 667});
        await page.goto('/html/login.html');
        await page.waitForSelector('.login-container', {timeout: 5000});
        await page.waitForTimeout(500);
        await page.screenshot({path: `${SHOT_DIR}/resp-mobile-login.png`, fullPage: false});
    });

    test('tablet-editor', async ({page}) => {
        await page.setViewportSize({width: 768, height: 1024});
        await login(page);
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
        await page.waitForTimeout(2000);
        // dismiss 任何 error
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({timeout: 500}).catch(() => false)) {
            await okBtn.click();
        }
        await page.screenshot({path: `${SHOT_DIR}/resp-tablet-editor.png`, fullPage: false});
    });
});
