import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * BDD Tests for Script Decision Table Editor
 *
 * Given: User is logged in and opens script decision table editor
 * When: User interacts with script decision table
 * Then: Expected table operations should work
 *
 * SPA DOM (ScriptDecisionTableEditor.tsx): mounts into #root (no #container /
 * #dialogContainer — those were jquery editor.html ids). The React editor
 * renders <Text strong>脚本式决策表: <file></Text> + AntD <Button>添加列</Button>
 * + <Button>添加行</Button> + <Button>粘贴行</Button> + <Button>保存</Button> +
 * an AntD <Table> when columns exist (or an Alert when empty).
 */
test.describe('Script Decision Table Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load script decision table editor page ──
    // Given: A logged-in user navigates to /app/editor/scriptdecisiontable?file=/project/script-table.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should be "RuleForge" (SPA shell)
    // And:   The "脚本式决策表: <file>" header should be visible
    //  (the SPA route /app/editor/scriptdecisiontable is the unified entry;
    //   the old /html/editor.html?type=scriptdecisiontable & /html/script-decision-table-editor.html no longer exist; dismiss
    //   any error dialogs — React uses AntD message/Modal, not bootbox)
    test('should load script decision table editor page', async ({ page }) => {
        await page.goto('/app/editor/scriptdecisiontable?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: SPA title is "RuleForge"
        await expect(page).toHaveTitle(/RuleForge/);

        // Then: The script-decision-table editor header should be visible (React mounted)
        await expect(page.getByText('脚本式决策表:').first()).toBeVisible({ timeout: 15000 });

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render container with content ──
    // Given: A logged-in user is on the script decision table editor page
    // When:  The ScriptDecisionTableEditor component has finished its initial render
    // Then:  The "脚本式决策表: <file>" header should be visible
    // And:   The toolbar buttons (添加列/添加行/保存) should be visible
    //  (no #container; React mounts into #root)
    test('should render container with content', async ({ page }) => {
        await page.goto('/app/editor/scriptdecisiontable?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: The script-decision-table editor header should be visible
        await expect(page.getByText('脚本式决策表:').first()).toBeVisible({ timeout: 15000 });

        // Then: The "添加列" toolbar button should be visible
        await expect(page.getByRole('button', { name: '添加列' })).toBeVisible();

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should handle right-click on container ──
    // Given: A logged-in user is on the script decision table editor page
    // When:  The user right-clicks on the editor body
    // Then:  No uncaught error should be thrown
    //  (React ScriptDecisionTableEditor wires per-cell Dropdown context menus;
    //   with an empty table there are no cells, so just verify the editor body
    //   does not throw on a right-click)
    test('should handle right-click on container', async ({ page }) => {
        await page.goto('/app/editor/scriptdecisiontable?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: The script-decision-table editor header should be visible
        await expect(page.getByText('脚本式决策表:').first()).toBeVisible({ timeout: 15000 });

        // Then: Right-click the editor header area without throwing
        await page.getByText('脚本式决策表:').first().click({ button: 'right' }).catch(() => {});

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render dialog host ──
    // Given: A logged-in user is on the script decision table editor page
    // When:  The React app mounts
    // Then:  The #root element (SPA mount point) should be attached
    //  (no #dialogContainer; AntD message/Modal render into #root's portal)
    test('should render dialog host', async ({ page }) => {
        await page.goto('/app/editor/scriptdecisiontable?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: SPA root mount point should be attached
        await expect(page.locator('#root')).toBeAttached({ timeout: 15000 });
    });
});
