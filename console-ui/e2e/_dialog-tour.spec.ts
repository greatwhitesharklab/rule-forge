import {test} from '@playwright/test';
import {login} from './helpers';

/**
 * V5.9.0 Dialog 视觉 tour
 *  触发尽可能多的 dialog 来截图: QuickStart 选项目 + tree 右键 + editor 各种右击
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

test.describe('Dialog tour', () => {
    test('frame-create-project-dialog', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
        // QuickStart 有 "新建项目" 入口(在选择项目下拉里)
        const dropdown = page.locator('button:has-text("选择项目")').first();
        if (await dropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await dropdown.click();
            await page.waitForTimeout(300);
            // 下拉里可能有 "新建项目" 链接
            const newProjectLink = page.locator('a:has-text("新建项目"), a:has-text("创建项目"), li:has-text("新建项目"), li:has-text("创建项目")').first();
            if (await newProjectLink.isVisible({timeout: 1000}).catch(() => false)) {
                await newProjectLink.click();
                await page.waitForTimeout(500);
            }
        }
        await page.screenshot({path: `${SHOT_DIR}/dialog-create-project.png`, fullPage: false});
        // 关闭可能打开的 modal
        await page.keyboard.press('Escape').catch(() => {});
    });

    test('package-flow-dialog', async ({page}) => {
        await login(page);
        // 切到 package 视图
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
        // 选第一个项目
        const dropdown = page.locator('button:has-text("选择项目")').first();
        if (await dropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await dropdown.click();
            await page.waitForTimeout(500);
            const firstProject = page.locator('.dropdown-menu li a, .ant-dropdown-menu li').first();
            if (await firstProject.isVisible({timeout: 2000}).catch(() => false)) {
                await firstProject.click();
                await page.waitForTimeout(1500);
            }
        }
        // 切到 package 视图(package navigator button)
        const packageBtn = page.locator('button:has-text("知识包"), button:has-text("包"), .view-toggle, [class*="package-view"]').first();
        if (await packageBtn.isVisible({timeout: 1000}).catch(() => false)) {
            await packageBtn.click();
            await page.waitForTimeout(800);
        }
        await page.screenshot({path: `${SHOT_DIR}/dialog-package-view.png`, fullPage: false});
    });

    test('datasource-batchtest-modal', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
        // 切到 datasource panel
        await page.locator('.activity-bar-icon[title="数据源"]').click();
        await page.waitForTimeout(1500);
        await page.screenshot({path: `${SHOT_DIR}/dialog-datasource.png`, fullPage: false});
    });

    test('quicktest-dialog', async ({page}) => {
        await login(page);
        // 打开 ruleset editor
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
        await page.waitForTimeout(2000);
        // 找 "测试" 或 quick test 按钮
        const testBtn = page.locator('button:has-text("测试"), button:has-text("快速测试"), a:has-text("测试")').first();
        if (await testBtn.isVisible({timeout: 2000}).catch(() => false)) {
            await testBtn.click();
            await page.waitForTimeout(1000);
        }
        await page.screenshot({path: `${SHOT_DIR}/dialog-quicktest.png`, fullPage: false});
    });

    test('condition-list-dialog', async ({page}) => {
        await login(page);
        // 打开 decision table editor
        await page.goto('/app/editor/decisiontable?file=/project/dt.xml');
        await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
        await page.waitForTimeout(2000);
        // dismiss error modal
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({timeout: 500}).catch(() => false)) {
            await okBtn.click();
            await page.waitForTimeout(300);
        }
        // 双击第一个 cell 触发 condition list
        const cell = page.locator('table td, .htCore td').first();
        if (await cell.isVisible({timeout: 2000}).catch(() => false)) {
            await cell.dblclick();
            await page.waitForTimeout(800);
        }
        await page.screenshot({path: `${SHOT_DIR}/dialog-condition-list.png`, fullPage: false});
    });
});
