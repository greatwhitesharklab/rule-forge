import {test} from '@playwright/test';
import {login} from './helpers.js';

/**
 * V5.9.0 Micro-interaction tour
 *  覆盖: button hover / focus / active 状态
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

test.describe('Micro-interaction tour', () => {
    test('button-hover', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        // hover QuickStart 卡片第一个
        const firstCard = page.locator('.welcome-card').first();
        if (await firstCard.isVisible({timeout: 2000}).catch(() => false)) {
            await firstCard.hover();
            await page.waitForTimeout(300);
        }
        await page.screenshot({path: `${SHOT_DIR}/micro-button-hover.png`, fullPage: false});
    });

    test('button-focus', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        // focus the 选择项目 button
        const projectBtn = page.locator('button:has-text("选择项目")').first();
        if (await projectBtn.isVisible({timeout: 2000}).catch(() => false)) {
            await projectBtn.focus();
            await page.waitForTimeout(300);
        }
        await page.screenshot({path: `${SHOT_DIR}/micro-button-focus.png`, fullPage: false});
    });

    test('activitybar-hover', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        // hover 一个 activity bar icon
        const aiIcon = page.locator('.activity-bar-icon[title="智能分析"]');
        if (await aiIcon.isVisible({timeout: 1000}).catch(() => false)) {
            await aiIcon.hover();
            await page.waitForTimeout(300);
        }
        await page.screenshot({path: `${SHOT_DIR}/micro-activitybar-hover.png`, fullPage: false});
    });

    test('tab-hover', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        // 切到 release,看 7 个 tab
        await page.locator('.activity-bar-icon[title="版本发布"]').click();
        await page.waitForTimeout(1500);
        // hover 第 3 个 tab
        const tab = page.locator('.ant-tabs-tab, [role="tab"]').nth(2);
        if (await tab.isVisible({timeout: 1500}).catch(() => false)) {
            await tab.hover();
            await page.waitForTimeout(300);
        }
        await page.screenshot({path: `${SHOT_DIR}/micro-tab-hover.png`, fullPage: false});
    });

    test('input-focus', async ({page}) => {
        // login 页 input focus
        await page.goto('/html/login.html');
        await page.waitForSelector('.login-container', {timeout: 5000});
        await page.locator('input[type="text"]').focus();
        await page.waitForTimeout(300);
        await page.screenshot({path: `${SHOT_DIR}/micro-input-focus.png`, fullPage: false});
    });
});
