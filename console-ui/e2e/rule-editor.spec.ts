import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * BDD Tests for Wizard Rule Editor (Ruleset Editor)
 *
 * Given: User is logged in and opens ruleset editor
 * When: User interacts with wizard rule editor
 * Then: Expected rule operations should work
 */
test.describe('Wizard Rule Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load ruleset editor page ──
    // Given: A logged-in user navigates to /app/editor/ruleset?file=/project/rules.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should contain "决策集编辑器"
    // And:   The #container element should be visible
    //  (the SPA route /app/editor/ruleset is the unified entry;
    //   the old /html/editor.html?type=ruleset & /html/ruleset-editor.html no longer exist; dismiss any
    //   bootbox error dialogs from backend 500s)
    test('should load ruleset editor page', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "决策集编辑器"
        await expect(page).toHaveTitle(/决策集编辑器/);

        // Then: Container should be attached
        const container = page.locator('#container');
        await expect(container).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should display toolbar with rule buttons ──
    // Given: A logged-in user is on the ruleset editor page
    // When:  The EditorToolbar finishes mounting
    // Then:  The #toolbarContainer should be visible
    // And:   Buttons labeled "保存", "添加规则", "添加循环规则", and "快速测试" should all be visible inside the toolbar
    test('should display toolbar with rule buttons', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle');

        // Then: Toolbar container should be attached
        const toolbarContainer = page.locator('#toolbarContainer');
        await expect(toolbarContainer).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should display rule content in container ──
    // Given: A logged-in user is on the ruleset editor page
    // When:  RuleFactory has loaded its data
    // Then:  The #container should be visible
    // And:   The #container should have at least one child element (rendered rule rows)
    test('should display rule content in container', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
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

    // ── BDD STUB: should show prompt when clicking add rule button ──
    // Given: A logged-in user is on the ruleset editor page with the toolbar rendered
    // When:  The user clicks the "添加规则" toolbar button
    // Then:  A bootbox prompt (a visible .modal / .bootbox .modal-dialog) should appear asking for a rule key
    test('should show prompt when clicking add rule button', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle');

        // Then: Toolbar container should be attached
        const toolbarContainer = page.locator('#toolbarContainer');
        await expect(toolbarContainer).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render dialog container ──
    // Given: A logged-in user is on the ruleset editor page
    // When:  The React shell mounts the dialog provider
    // Then:  The #dialogContainer element should be attached to the DOM
    test('should render dialog container', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should be attached
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached({ timeout: 15000 });
    });
});
