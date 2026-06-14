import {test} from '@playwright/test';
import {login} from './helpers';

/**
 * V5.9.0 Error/Loading/Empty state tour
 *  覆盖: 各 panel 的 loading 状态 / 空状态 / 错误状态
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

test.describe('Error/Loading/Empty state tour', () => {
    test('release-empty', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        await page.locator('.activity-bar-icon[title="版本发布"]').click();
        await page.waitForTimeout(800);
        await page.screenshot({path: `${SHOT_DIR}/state-release-empty.png`, fullPage: false});
    });

    test('monitoring-empty', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        await page.locator('.activity-bar-icon[title="监控告警"]').click();
        await page.waitForTimeout(1500);
        await page.screenshot({path: `${SHOT_DIR}/state-monitoring-empty.png`, fullPage: false});
    });

    test('simulation-empty', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        await page.locator('.activity-bar-icon[title="规则仿真"]').click();
        await page.waitForTimeout(1500);
        await page.screenshot({path: `${SHOT_DIR}/state-simulation-empty.png`, fullPage: false});
    });

    test('agent-empty', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        await page.locator('.activity-bar-icon[title="智能分析"]').click();
        await page.waitForTimeout(1500);
        await page.screenshot({path: `${SHOT_DIR}/state-agent-empty.png`, fullPage: false});
    });

    test('datasource-empty', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        await page.locator('.activity-bar-icon[title="数据源"]').click();
        await page.waitForTimeout(1500);
        await page.screenshot({path: `${SHOT_DIR}/state-datasource-empty.png`, fullPage: false});
    });

    test('editor-error-404', async ({page}) => {
        // /common/loadXml 现在 返 404,前端的 bootbox 应该弹 'file not found'
        await login(page);
        await page.goto('/app/editor/scorecard?file=/project/nonexistent.xml');
        await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
        // 等 bootbox (bootbox 会创建 .modal-dialog 在 body 末尾)
        await page.waitForSelector('.modal-dialog, .bootbox', {timeout: 8000}).catch(() => {});
        await page.waitForTimeout(500);
        await page.screenshot({path: `${SHOT_DIR}/state-error-modal.png`, fullPage: false});
        // 关闭
        const okBtn = page.locator('.modal-dialog .btn-primary, .bootbox .btn-primary, .modal .btn').first();
        if (await okBtn.isVisible({timeout: 1000}).catch(() => false)) {
            await okBtn.click();
            await page.waitForTimeout(500);
        }
        await page.screenshot({path: `${SHOT_DIR}/state-editor-empty.png`, fullPage: false});
    });

    test('frame-loading-initial', async ({page}) => {
        // 用 route 延迟关键请求,截 loading 状态
        await page.route('**/api/frame/loadProjects', async (route) => {
            await new Promise((r) => setTimeout(r, 2000));
            await route.continue();
        });
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(500);
        await page.screenshot({path: `${SHOT_DIR}/state-frame-loading.png`, fullPage: false});
    });
});
