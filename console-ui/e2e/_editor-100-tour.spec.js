import {test} from '@playwright/test';
import {login} from './helpers.js';

/**
 * V5.9.0 100% Editor/Panel/State tour
 *
 * 覆盖:
 *  - 19 editor type 全部打开截图 (test_proj 下的 test_*.xml)
 *  - 6 panel 的 tab 内部交互
 *  - 交互态: hover / focus / active
 *  - 状态: empty / loading
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

async function shot(page, name) {
    await page.screenshot({path: `${SHOT_DIR}/${name}.png`, fullPage: false});
}

async function openEditor(page, type, file) {
    await page.goto(`/html/editor.html?type=${type}&file=${encodeURIComponent(file)}&project=test_proj`);
    await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
    await page.waitForTimeout(2000);
    // dismiss bootbox error
    const okBtn = page.locator('.bootbox .btn-primary, button:has-text("确定")').first();
    if (await okBtn.isVisible({timeout: 500}).catch(() => false)) {
        await okBtn.click({force: true, timeout: 1000}).catch(() => {});
        await page.waitForTimeout(300);
    }
}

test.describe('100% Editor tour', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
    });

    // ========== 19 EDITOR TYPES ==========
    // 10 simple
    test('e01-editor-variable', async ({page}) => {
        await openEditor(page, 'variable', '/test_proj/test_vl.xml');
        await shot(page, 'editor-type-variable');
    });

    test('e02-editor-constant', async ({page}) => {
        await openEditor(page, 'constant', '/test_proj/test_cl.xml');
        await shot(page, 'editor-type-constant');
    });

    test('e03-editor-parameter', async ({page}) => {
        await openEditor(page, 'parameter', '/test_proj/test_pl.xml');
        await shot(page, 'editor-type-parameter');
    });

    test('e04-editor-action', async ({page}) => {
        await openEditor(page, 'action', '/test_proj/test_al.xml');
        await shot(page, 'editor-type-action');
    });

    test('e05-editor-package', async ({page}) => {
        // package editor 不是 file 路径模式,走 frame
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
        // 点 "选择项目" 选 test_proj
        const dropdown = page.locator('button:has-text("选择项目")').first();
        if (await dropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await dropdown.click();
            await page.waitForTimeout(400);
            const proj = page.locator('li:has-text("test_proj")').first();
            if (await proj.isVisible({timeout: 1000}).catch(() => false)) {
                await proj.click();
                await page.waitForTimeout(1500);
            }
        }
        // 切到 package view
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        // 点 知识包.rp
        const rp = page.locator('text=知识包.rp').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2500);
        }
        await shot(page, 'editor-type-package');
    });

    test('e06-editor-client', async ({page}) => {
        await openEditor(page, 'client', '/test_proj/test_cl.xml');
        await shot(page, 'editor-type-client');
    });

    test('e07-editor-permission', async ({page}) => {
        await openEditor(page, 'permission', '/test_proj/test_cl.xml');
        await shot(page, 'editor-type-permission');
    });

    test('e08-editor-resource', async ({page}) => {
        await openEditor(page, 'resource', '/test_proj/test_cl.xml');
        await shot(page, 'editor-type-resource');
    });

    test('e09-editor-monitoring', async ({page}) => {
        await openEditor(page, 'monitoring', '/test_proj/test_cl.xml');
        await shot(page, 'editor-type-monitoring');
    });

    test('e10-editor-analysis', async ({page}) => {
        await openEditor(page, 'analysis', '/test_proj/test_cl.xml');
        await shot(page, 'editor-type-analysis');
    });

    // 9 complex
    test('e11-editor-ruleset', async ({page}) => {
        await openEditor(page, 'ruleset', '/test_proj/test_rules.xml');
        await shot(page, 'editor-type-ruleset');
    });

    test('e12-editor-rulesetlib', async ({page}) => {
        await openEditor(page, 'rulesetlib', '/test_proj/test_rsl.xml');
        await shot(page, 'editor-type-rulesetlib');
    });

    test('e13-editor-decisiontable', async ({page}) => {
        await openEditor(page, 'decisiontable', '/test_proj/test_dt.xml');
        await shot(page, 'editor-type-decisiontable');
    });

    test('e14-editor-scriptdecisiontable', async ({page}) => {
        await openEditor(page, 'scriptdecisiontable', '/test_proj/test_dts.xml');
        await shot(page, 'editor-type-scriptdecisiontable');
    });

    test('e15-editor-decisiontree', async ({page}) => {
        await openEditor(page, 'decisiontree', '/test_proj/test_dtree.xml');
        await shot(page, 'editor-type-decisiontree');
    });

    test('e16-editor-crosstab', async ({page}) => {
        await openEditor(page, 'crosstab', '/test_proj/test_ct.xml');
        await shot(page, 'editor-type-crosstab');
    });

    test('e17-editor-complexscorecard', async ({page}) => {
        await openEditor(page, 'complexscorecard', '/test_proj/test_scc.xml');
        await shot(page, 'editor-type-complexscorecard');
    });

    test('e18-editor-scorecard', async ({page}) => {
        await openEditor(page, 'scorecard', '/test_proj/test_sc2.xml');
        await shot(page, 'editor-type-scorecard');
    });

    test('e19-editor-flowbpmn', async ({page}) => {
        await openEditor(page, 'flowbpmn', '/test_proj/test_rl.xml');
        await shot(page, 'editor-type-flowbpmn');
    });
});

test.describe('100% Panel tab tour', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
    });

    // ========== 6 PANELS ==========
    test('p01-panel-release', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="版本发布"]').click({force: true});
        await page.waitForTimeout(2000);
        // 切 tab
        const tabs = page.locator('.ant-tabs-tab, [role="tab"]');
        const n = Math.min(await tabs.count(), 4);
        for (let i = 0; i < n; i++) {
            if (await tabs.nth(i).isVisible({timeout: 500}).catch(() => false)) {
                await tabs.nth(i).click({force: true});
                await page.waitForTimeout(800);
            }
        }
        await shot(page, 'panel-release-tabs');
    });

    test('p02-panel-monitoring', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="监控告警"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'panel-monitoring');
    });

    test('p03-panel-simulation', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="规则仿真"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'panel-simulation');
    });

    test('p04-panel-agent', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="智能分析"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'panel-agent');
    });

    test('p05-panel-datasource', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="数据源"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'panel-datasource');
    });

    test('p06-panel-project-files', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="项目文件"]').click({force: true});
        await page.waitForTimeout(2000);
        // 选 test_proj
        const dropdown = page.locator('button:has-text("选择项目")').first();
        if (await dropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await dropdown.click();
            await page.waitForTimeout(400);
            const proj = page.locator('li:has-text("test_proj")').first();
            if (await proj.isVisible({timeout: 1000}).catch(() => false)) {
                await proj.click();
                await page.waitForTimeout(2000);
            }
        }
        // 展开树
        const treeNode = page.locator('.tree-text, .ant-tree-node-content-wrapper').first();
        if (await treeNode.isVisible({timeout: 1500}).catch(() => false)) {
            await treeNode.click({force: true});
            await page.waitForTimeout(1000);
        }
        await shot(page, 'panel-project-files');
    });
});

test.describe('100% State tour', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
    });

    // Empty states for each panel
    test('s01-empty-release', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="版本发布"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'state-empty-release');
    });

    test('s02-empty-monitoring', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="监控告警"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'state-empty-monitoring');
    });

    test('s03-empty-simulation', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="规则仿真"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'state-empty-simulation');
    });

    test('s04-empty-agent', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="智能分析"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'state-empty-agent');
    });

    test('s05-empty-datasource', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="数据源"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'state-empty-datasource');
    });

    // Loading states
    test('s06-loading-frame', async ({page}) => {
        await page.route('**/api/frame/loadProjects', async (route) => {
            await new Promise((r) => setTimeout(r, 2000));
            await route.continue();
        });
        await page.goto('/html/frame.html');
        await page.waitForTimeout(800);
        await shot(page, 'state-loading-frame');
    });

    test('s07-loading-editor', async ({page}) => {
        await page.route('**/api/common/loadXml*', async (route) => {
            await new Promise((r) => setTimeout(r, 2000));
            await route.continue();
        });
        await page.goto('/html/editor.html?type=ruleset&file=/test_proj/test_rules.xml&project=test_proj');
        await page.waitForTimeout(800);
        await shot(page, 'state-loading-editor');
    });

    // Error states
    test('s08-error-404-loadXml', async ({page}) => {
        await page.goto('/html/editor.html?type=ruleset&file=/nonexistent.xml&project=test_proj');
        await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
        await page.waitForSelector('.modal-dialog, .bootbox', {timeout: 8000}).catch(() => {});
        await page.waitForTimeout(500);
        await shot(page, 'state-error-404');
        // dismiss
        const okBtn = page.locator('.modal-dialog .btn-primary, .bootbox .btn-primary').first();
        if (await okBtn.isVisible({timeout: 1000}).catch(() => false)) {
            await okBtn.click({force: true});
            await page.waitForTimeout(300);
        }
    });

    test('s09-error-500-loadPackageConfig', async ({page}) => {
        // 这个之前会 500 返 JSON,现在返 200 + {} 空 config
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="项目文件"]').click({force: true});
        await page.waitForTimeout(800);
        // 选项目 → 切到 package view
        const dropdown = page.locator('button:has-text("选择项目")').first();
        if (await dropdown.isVisible({timeout: 2000}).catch(() => false)) {
            await dropdown.click();
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
            await page.waitForTimeout(2000);
        }
        await shot(page, 'state-package-loaded-no-500');
    });
});

test.describe('100% Interaction state tour', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
    });

    // 按钮 hover 态
    test('i01-button-hover', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
        // hover 一个 activity bar
        const icon = page.locator('.activity-bar-icon[title="项目文件"]');
        await icon.hover({force: true});
        await page.waitForTimeout(400);
        await shot(page, 'interact-button-hover');
    });

    test('i02-button-focus', async ({page}) => {
        await page.goto('/html/login.html');
        await page.waitForSelector('.login-container', {timeout: 5000});
        const input = page.locator('input[type="text"]').first();
        await input.focus();
        await page.waitForTimeout(400);
        await shot(page, 'interact-input-focus');
    });

    test('i03-button-active', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
        // click 监控告警 to show active state
        await page.locator('.activity-bar-icon[title="监控告警"]').click({force: true});
        await page.waitForTimeout(1000);
        await shot(page, 'interact-button-active');
    });

    test('i04-dropdown-open', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
        const dropdown = page.locator('button:has-text("选择项目")').first();
        await dropdown.click({force: true});
        await page.waitForTimeout(800);
        await shot(page, 'interact-dropdown-open');
    });

    test('i05-tab-switch', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="版本发布"]').click({force: true});
        await page.waitForTimeout(1500);
        const tab = page.locator('.ant-tabs-tab, [role="tab"]').nth(2);
        if (await tab.isVisible({timeout: 1000}).catch(() => false)) {
            await tab.click({force: true});
            await page.waitForTimeout(800);
        }
        await shot(page, 'interact-tab-switched');
    });
});
