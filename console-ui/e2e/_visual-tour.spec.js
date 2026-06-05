import {test, expect} from '@playwright/test';
import {login} from './helpers.js';

/**
 * V5.9.0 视觉 tour + 视觉 diff 回归保护
 *  - 截图到 /home/fredgu/git_home/ruleforge/step5-screenshots/ (临时,不入库)
 *  - release panel 的 toHaveScreenshot() 走 baseline 回归
 */

const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

test.describe('Visual tour', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
    });

    test('frame-default', async ({page}) => {
        await page.screenshot({path: `${SHOT_DIR}/frame-default.png`, fullPage: false});
    });

    test('monitoring', async ({page}) => {
        await page.locator('.activity-bar-icon[title="监控告警"]').click();
        await page.waitForTimeout(2000);
        await page.screenshot({path: `${SHOT_DIR}/panel-monitoring.png`, fullPage: false});
    });

    test('release', async ({page}) => {
        await page.locator('.activity-bar-icon[title="版本发布"]').click();
        await page.waitForTimeout(2000);
        // 截图到 step5-screenshots
        await page.screenshot({path: `${SHOT_DIR}/panel-release.png`, fullPage: false});
        // V5.9.0: 视觉 diff — release panel 全貌
        // maxDiffPixels 1000 / ratio 0.03 比 datasource 略宽,因为 release 顶部有 projectName 文字
        // (空状态时显示 "请先选择一个项目",有数据时显示项目名,baseline 锁的是空状态)
        await expect(page).toHaveScreenshot('release-panel-baseline.png', {
            maxDiffPixels: 1000,
            maxDiffPixelRatio: 0.03,
            animations: 'disabled',
        });
    });

    test('simulation', async ({page}) => {
        await page.locator('.activity-bar-icon[title="规则仿真"]').click();
        await page.waitForTimeout(2000);
        await page.screenshot({path: `${SHOT_DIR}/panel-simulation.png`, fullPage: false});
    });

    test('agent', async ({page}) => {
        await page.locator('.activity-bar-icon[title="智能分析"]').click();
        await page.waitForTimeout(2000);
        await page.screenshot({path: `${SHOT_DIR}/panel-agent.png`, fullPage: false});
    });

    test('settings', async ({page}) => {
        await page.locator('.activity-bar-icon[title="系统设置"]').click();
        await page.waitForTimeout(2000);
        await page.screenshot({path: `${SHOT_DIR}/panel-settings.png`, fullPage: false});
    });
});
