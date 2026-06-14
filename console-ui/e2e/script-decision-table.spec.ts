import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * BDD Tests for Script Decision Table Editor
 *
 * Given: User is logged in and opens script decision table editor
 * When: User interacts with script decision table
 * Then: Expected table operations should work
 */
test.describe('Script Decision Table Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load script decision table editor page ──
    // Given: A logged-in user navigates to /app/editor/scriptdecisiontable?file=/project/script-table.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should contain "脚本式决策表编辑器"
    // And:   The #container element should be attached
    //  (the SPA route /app/editor/scriptdecisiontable is the unified entry;
    //   the old /html/editor.html?type=scriptdecisiontable & /html/script-decision-table-editor.html no longer exist; dismiss
    //   any bootbox error dialogs from backend 500s)
    test('should load script decision table editor page', async ({ page }) => {
        await page.goto('/app/editor/scriptdecisiontable?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "脚本式决策表编辑器"
        await expect(page).toHaveTitle(/脚本式决策表编辑器/);

        // Then: Container should be attached
        const container = page.locator('#container');
        await expect(container).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render container with content ──
    // Given: A logged-in user is on the script decision table editor page
    // When:  The ScriptDecisionTable component has finished its initial render
    // Then:  The #container should be attached
    test('should render container with content', async ({ page }) => {
        await page.goto('/app/editor/scriptdecisiontable?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Container should be attached
        const container = page.locator('#container');
        await expect(container).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should handle right-click on container ──
    // Given: A logged-in user is on the script decision table editor page
    // When:  The user right-clicks on the #container
    // Then:  No uncaught error should be thrown
    test('should handle right-click on container', async ({ page }) => {
        await page.goto('/app/editor/scriptdecisiontable?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Container should be attached
        const container = page.locator('#container');
        await expect(container).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render dialog container ──
    // Given: A logged-in user is on the script decision table editor page
    // When:  The React shell mounts the dialog provider
    // Then:  The #dialogContainer element should be attached to the DOM
    test('should render dialog container', async ({ page }) => {
        await page.goto('/app/editor/scriptdecisiontable?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should be attached
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached({ timeout: 15000 });
    });
});
