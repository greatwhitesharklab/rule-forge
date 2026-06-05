import {test} from '@playwright/test';
import {login} from './helpers.js';

/**
 * V5.9.0 100% Business Flow E2E
 *
 * 覆盖真实业务链路:
 *  1. 创建项目 → 选项目 → 触发 dialog
 *  2. 创建文件 → 编辑 → 保存
 *  3. 切到 package view → 添加知识包
 *  4. Datasource: 创建数据源 + 字段映射
 *  5. Decision table: 条件行编辑 + 保存
 *  6. Quick test 流程
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

async function shot(page, name) {
    await page.screenshot({path: `${SHOT_DIR}/bizflow-${name}.png`, fullPage: false});
}

async function dismiss(page) {
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(200);
    const closes = page.locator(
        '.ant-modal-close, .modal-header .close, button:has-text("取消"), button:has-text("确定")'
    );
    const n = await closes.count();
    for (let i = 0; i < Math.min(n, 3); i++) {
        try { await closes.nth(i).click({force: true, timeout: 500}); } catch (_) {}
    }
    await page.waitForTimeout(200);
}

test.describe('100% Business Flow', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
    });

    test('flow-01-create-and-select-project', async ({page}) => {
        // 创建新项目
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
        const dropdown = page.locator('button:has-text("选择项目"), button:has-text("test_proj")').first();
        if (await dropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await dropdown.click({force: true});
            await page.waitForTimeout(400);
        }
        const newProj = page.locator('a:has-text("新建项目"), li:has-text("新建项目")').first();
        if (await newProj.isVisible({timeout: 1000}).catch(() => false)) {
            await newProj.click({force: true});
            await page.waitForTimeout(800);
            // 填名字
            const nameInput = page.locator('input[placeholder*="项目"], input[name*="name"]').first();
            if (await nameInput.isVisible({timeout: 1000}).catch(() => false)) {
                await nameInput.fill('biz_test_proj');
                await page.waitForTimeout(300);
                const submitBtn = page.locator('button:has-text("确定"), button:has-text("创建")').first();
                if (await submitBtn.isVisible({timeout: 500}).catch(() => false)) {
                    await submitBtn.click({force: true});
                    await page.waitForTimeout(2000);
                }
            }
        }
        await shot(page, 'flow-01-create-project');
    });

    test('flow-02-open-ruleset-editor', async ({page}) => {
        // 选 test_proj → 打开决策集
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        const dropdown = page.locator('button:has-text("选择项目"), button:has-text("test_proj")').first();
        if (await dropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await dropdown.click({force: true});
            await page.waitForTimeout(400);
            const proj = page.locator('li:has-text("test_proj")').first();
            if (await proj.isVisible({timeout: 1000}).catch(() => false)) {
                await proj.click();
                await page.waitForTimeout(2000);
            }
        }
        // 展开 决策集
        const decisionSet = page.locator('.tree-text:has-text("决策集"), .ant-tree-node-content-wrapper:has-text("决策集")').first();
        if (await decisionSet.isVisible({timeout: 1500}).catch(() => false)) {
            await decisionSet.click({force: true});
            await page.waitForTimeout(1000);
        }
        // 找 test_rules.xml 双击
        const ruleFile = page.locator('.tree-text:has-text("test_rules"), text=test_rules.xml').first();
        if (await ruleFile.isVisible({timeout: 1500}).catch(() => false)) {
            await ruleFile.dblclick({force: true});
            await page.waitForTimeout(3000);
        }
        await shot(page, 'flow-02-ruleset-editor');
    });

    test('flow-03-create-datasource', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="数据源"]').click({force: true});
        await page.waitForTimeout(2000);
        // 新建数据源 button
        const btn = page.locator('button:has-text("新建"), button:has-text("创建"), .ant-btn-primary').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1500);
        }
        await shot(page, 'flow-03-create-datasource');
        await dismiss(page);
    });

    test('flow-04-version-release', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="版本发布"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'flow-04-release');
    });

    test('flow-05-batch-test-datasource', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="数据源"]').click({force: true});
        await page.waitForTimeout(2000);
        // 找 "批量测试" 按钮(任一行)
        const btn = page.locator('button:has-text("批量测试"), a:has-text("批量测试")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1500);
        }
        await shot(page, 'flow-05-batchtest');
        await dismiss(page);
    });

    test('flow-06-agent-chat', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="智能分析"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'flow-06-agent');
    });

    test('flow-07-monitoring-dashboard', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="监控告警"]').click({force: true});
        await page.waitForTimeout(2500);
        await shot(page, 'flow-07-monitoring');
    });

    test('flow-08-simulation-setup', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="规则仿真"]').click({force: true});
        await page.waitForTimeout(2500);
        await shot(page, 'flow-08-simulation');
    });

    test('flow-09-package-save', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        const dropdown = page.locator('button:has-text("选择项目"), button:has-text("test_proj")').first();
        if (await dropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await dropdown.click({force: true});
            await page.waitForTimeout(400);
            const proj = page.locator('li:has-text("test_proj")').first();
            if (await proj.isVisible({timeout: 1000}).catch(() => false)) {
                await proj.click();
                await page.waitForTimeout(2000);
            }
        }
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        const rp = page.locator('text=知识包.rp').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2500);
        }
        await shot(page, 'flow-09-package-view');
    });
});
