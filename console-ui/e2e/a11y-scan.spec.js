import {test} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {login} from './helpers.js';

/**
 * V5.9.0 Accessibility Scan
 *
 * 跑 axe-core on 关键页,按 WCAG 2.1 AA 标准扫描:
 * - login.html (auth entry)
 * - frame.html (主框架)
 * - editor.html (编辑器)
 * - datasource panel (settings)
 *
 * 报告按严重度分组: critical / serious / moderate / minor
 * 不阻止 ship,只报告。
 */
test.describe('A11y scan', () => {
    test('login page a11y', async ({page}) => {
        await page.goto('/html/login.html');
        await page.waitForSelector('.login-container', {timeout: 5000});
        const results = await new AxeBuilder({page})
            .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
            .analyze();
        logViolations('login', results);
    });

    test('frame a11y', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(1500);
        const results = await new AxeBuilder({page})
            .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
            .analyze();
        logViolations('frame', results);
    });

    test('editor (ruleset) a11y', async ({page}) => {
        await login(page);
        await page.goto('/html/editor.html?type=ruleset&file=/test_proj/test_rules.xml&project=test_proj');
        await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
        await page.waitForTimeout(1500);
        const results = await new AxeBuilder({page})
            .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
            .analyze();
        logViolations('editor-ruleset', results);
    });

    test('datasource panel a11y', async ({page}) => {
        await login(page);
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="数据源"]').click({force: true});
        await page.waitForTimeout(2000);
        const results = await new AxeBuilder({page})
            .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
            .analyze();
        logViolations('datasource-panel', results);
    });
});

function logViolations(pageName, results) {
    const violations = results.violations || [];
    if (violations.length === 0) {
        console.log(`  ✓ ${pageName}: 0 violations`);
        return;
    }
    const byImpact = {};
    for (const v of violations) {
        byImpact[v.impact] = (byImpact[v.impact] || 0) + 1;
        console.log(`  [${v.impact}] ${v.id}: ${v.description}`);
        console.log(`     nodes: ${v.nodes.length}, help: ${v.helpUrl}`);
        // print first 3 nodes' selector + summary
        for (const node of v.nodes.slice(0, 3)) {
            const sel = node.target && node.target[0] ? node.target[0] : '?';
            const msg = node.failureSummary ? node.failureSummary.replace(/\n/g, ' | ') : '';
            console.log(`     - ${sel} :: ${msg}`);
        }
        if (v.nodes.length > 3) {
            console.log(`     ... and ${v.nodes.length - 3} more`);
        }
    }
    console.log(`  ${pageName}: ${violations.length} violations total,`,
        JSON.stringify(byImpact));
}
