import {test, expect} from '@playwright/test';
import {login} from './helpers.js';

/**
 * V5.9.0 Frame tree tour — 截图真实 file tree + splitter + menu
 *  打开 demo project,看完整 layout
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

test.describe('Frame tree tour', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(1000);
    });

    test('frame-with-project', async ({page}) => {
        // 尝试点击 "选择项目" 下拉(Welcome 页有)
        const projectDropdown = page.locator('button:has-text("选择项目")').first();
        if (await projectDropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await projectDropdown.click();
            await page.waitForTimeout(500);
            // 选第一个项目
            const firstProject = page.locator('.dropdown-menu li a, .ant-dropdown-menu li').first();
            if (await firstProject.isVisible({timeout: 2000}).catch(() => false)) {
                await firstProject.click();
                await page.waitForTimeout(1500);
            }
        }
        await page.screenshot({path: `${SHOT_DIR}/frame-with-project.png`, fullPage: false});
    });

    test('frame-with-ds', async ({page}) => {
        // 切到 datasource panel(数据源总是有内容)
        await page.locator('.activity-bar-icon[title="数据源"]').click();
        await page.waitForTimeout(2000);
        await page.screenshot({path: `${SHOT_DIR}/frame-ds-full.png`, fullPage: false});
    });

    test('frame-with-release', async ({page}) => {
        // 切到 release panel
        await page.locator('.activity-bar-icon[title="版本发布"]').click();
        await page.waitForTimeout(2000);
        await page.screenshot({path: `${SHOT_DIR}/frame-release-full.png`, fullPage: false});
    });
});
