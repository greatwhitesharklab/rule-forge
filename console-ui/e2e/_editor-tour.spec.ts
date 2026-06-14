import {test, expect} from '@playwright/test';
import {login} from './helpers';

/**
 * V5.9.0 Editor tour — 截图 8 种 editor 类型
 *  路径:/app/editor/<segment>?file=/project/<file>
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';
// legacy editor.html?type=<type> → SPA segment /app/editor/<segment>
// flowbpmn 是唯一真重命名;ruleflow/ul/rulesetlib 共享 ruleset editor;
// monitoring/analysis 无 SPA 路由,回退到 type 原值。
const EDITOR_SEGMENT: Record<string, string> = {
    flowbpmn: 'flow',
    ruleflow: 'flow',
    ul: 'ruleset',
    rulesetlib: 'ruleset',
};
const EDITORS = [
    {type: 'ruleset', file: 'rules.xml', name: 'editor-ruleset'},
    {type: 'decisiontable', file: 'dt.xml', name: 'editor-decisiontable'},
    {type: 'decisiontree', file: 'decision-tree.xml', name: 'editor-decisiontree'},
    {type: 'scorecard', file: 'scorecard.xml', name: 'editor-scorecard'},
    {type: 'scriptdecisiontable', file: 'script-table.xml', name: 'editor-scriptdecisiontable'},
    {type: 'ul', file: 'ul.xml', name: 'editor-ul'},
    {type: 'variable', file: 'variable.xml', name: 'editor-variable'},
    {type: 'package', file: 'package.xml', name: 'editor-package'},
];

test.describe('Editor tour', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
    });

    for (const ed of EDITORS) {
        test(`editor-${ed.type}`, async ({page}) => {
            await page.goto(`/app/editor/${EDITOR_SEGMENT[ed.type] || ed.type}?file=/project/${ed.file}`);
            await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
            await page.waitForTimeout(2000);
            // dismiss any bootbox error
            const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
            if (await okBtn.isVisible({timeout: 500}).catch(() => false)) {
                await okBtn.click();
            }
            await page.screenshot({path: `${SHOT_DIR}/${ed.name}.png`, fullPage: false});
        });
    }

    // 独立入口:permission(从 SidebarToolbar 打开)
    test('editor-permission', async ({page}) => {
        await page.goto('/app/editor/permission');
        await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
        await page.waitForTimeout(2000);
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({timeout: 500}).catch(() => false)) {
            await okBtn.click();
        }
        await page.screenshot({path: `${SHOT_DIR}/editor-permission.png`, fullPage: false});
    });
});
