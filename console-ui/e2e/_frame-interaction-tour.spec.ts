import {test} from '@playwright/test';
import {login} from './helpers';

/**
 * V5.9.0 Frame 交互视觉 tour
 *  覆盖 splitter hover / tree node hover / tree active / context menu / dropdown
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

test.describe('Frame interaction tour', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
    });

    test('frame-splitter-hover', async ({page}) => {
        // 找 splitter 把手 (orientation vertical 时是水平 bar)
        const splitter = page.locator('.splitter, [class*="splitter"]').first();
        if (await splitter.isVisible({timeout: 2000}).catch(() => false)) {
            await splitter.hover();
            await page.waitForTimeout(300);
        }
        await page.screenshot({path: `${SHOT_DIR}/frame-splitter-hover.png`, fullPage: false});
    });

    test('frame-tree-hover', async ({page}) => {
        // 选第一个项目 (QuickStart 页有 "选择项目" 下拉)
        const projectDropdown = page.locator('button:has-text("选择项目")').first();
        if (await projectDropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await projectDropdown.click();
            await page.waitForTimeout(500);
            const firstProject = page.locator('.dropdown-menu li a, .ant-dropdown-menu li').first();
            if (await firstProject.isVisible({timeout: 2000}).catch(() => false)) {
                await firstProject.click();
                await page.waitForTimeout(1500);
            }
        }
        // hover 第一个 tree node
        const treeNode = page.locator('.tree li span, .tree li a').first();
        if (await treeNode.isVisible({timeout: 2000}).catch(() => false)) {
            await treeNode.hover();
            await page.waitForTimeout(300);
        }
        await page.screenshot({path: `${SHOT_DIR}/frame-tree-hover.png`, fullPage: false});
    });

    test('frame-tree-active', async ({page}) => {
        // 选项目
        const projectDropdown = page.locator('button:has-text("选择项目")').first();
        if (await projectDropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await projectDropdown.click();
            await page.waitForTimeout(500);
            const firstProject = page.locator('.dropdown-menu li a, .ant-dropdown-menu li').first();
            if (await firstProject.isVisible({timeout: 2000}).catch(() => false)) {
                await firstProject.click();
                await page.waitForTimeout(1500);
            }
        }
        // click 第一个 tree node (not a parent, just a leaf)
        const leafNode = page.locator('.tree li a').first();
        if (await leafNode.isVisible({timeout: 2000}).catch(() => false)) {
            await leafNode.click();
            await page.waitForTimeout(800);
        }
        await page.screenshot({path: `${SHOT_DIR}/frame-tree-active.png`, fullPage: false});
    });

    test('frame-tree-contextmenu', async ({page}) => {
        // 选项目
        const projectDropdown = page.locator('button:has-text("选择项目")').first();
        if (await projectDropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await projectDropdown.click();
            await page.waitForTimeout(500);
            const firstProject = page.locator('.dropdown-menu li a, .ant-dropdown-menu li').first();
            if (await firstProject.isVisible({timeout: 2000}).catch(() => false)) {
                await firstProject.click();
                await page.waitForTimeout(1500);
            }
        }
        // right-click 第一个 tree node
        const treeNode = page.locator('.tree li a, .tree li span').first();
        if (await treeNode.isVisible({timeout: 2000}).catch(() => false)) {
            await treeNode.click({button: 'right'});
            await page.waitForTimeout(500);
        }
        await page.screenshot({path: `${SHOT_DIR}/frame-tree-contextmenu.png`, fullPage: false});
        // 关闭 menu
        await page.keyboard.press('Escape');
    });

    test('frame-topbar-dropdown', async ({page}) => {
        // topbar 一般有 project 下拉 / 用户菜单
        const topbarDropdown = page.locator('.top-bar .dropdown-toggle, .topbar .dropdown-toggle, [class*="topbar"] .dropdown-toggle').first();
        if (await topbarDropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await topbarDropdown.click();
            await page.waitForTimeout(500);
        }
        await page.screenshot({path: `${SHOT_DIR}/frame-topbar-dropdown.png`, fullPage: false});
    });
});
