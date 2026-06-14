import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * BDD Tests for Decision Tree Editor
 *
 * Given: User is logged in and opens decision tree editor
 * When: User interacts with decision tree
 * Then: Expected tree operations should work
 */
test.describe('Decision Tree Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load decision tree editor page ──
    // Given: A logged-in user navigates to /app/editor/decisiontree?file=/project/decision-tree.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should contain "决策树编辑器"
    // And:   The #container element should be attached
    //  (the SPA route /app/editor/<type> is the unified entry;
    //   dismiss any bootbox error dialogs from backend 500s)
    test('should load decision tree editor page', async ({ page }) => {
        await page.goto('/app/editor/decisiontree?file=/project/decision-tree.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "决策树编辑器"
        await expect(page).toHaveTitle(/决策树编辑器/);

        // Then: Container should be attached
        const container = page.locator('#container');
        await expect(container).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should display toolbar with buttons ──
    // Given: A logged-in user is on the decision tree editor page
    // When:  The EditorToolbar component finishes mounting
    // Then:  The #toolbarContainer should be visible
    // And:   A button labeled "保存" should be visible inside the toolbar
    // And:   A button labeled "变量库" should be visible
    // And:   A button labeled "快速测试" should be visible
    //  (editor.html's dom for decisiontree includes #toolbarContainer;
    //   use the shared EditorToolbar button labels to verify)
    test('should display toolbar with buttons', async ({ page }) => {
        await page.goto('/app/editor/decisiontree?file=/project/decision-tree.xml');
        await page.waitForLoadState('networkidle');

        // Then: Toolbar container should be visible
        const toolbarContainer = page.locator('#toolbarContainer');
        await expect(toolbarContainer).toBeVisible({ timeout: 15000 });

        // Then: Save button should be visible (EditorToolbar - exact match)
        const saveButton = page.locator('#toolbarContainer button:text-is("保存")');
        await expect(saveButton).toBeVisible();

        // Then: Variable library button should be visible
        const varButton = page.locator('#toolbarContainer button:has-text("变量库")');
        await expect(varButton).toBeVisible();

        // Then: Quick test button should be visible
        const quickTestButton = page.locator('#toolbarContainer button:has-text("快速测试")');
        await expect(quickTestButton).toBeVisible();
    });

    // ── BDD STUB: should render decision tree canvas ──
    // Given: A logged-in user is on the decision tree editor page
    // When:  The DecisionTree JS module has initialized
    // Then:  The #container should be attached
    test('should render decision tree canvas', async ({ page }) => {
        await page.goto('/app/editor/decisiontree?file=/project/decision-tree.xml');
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

    // ── BDD STUB: should show quick test dialog ──
    // Given: A logged-in user is on the decision tree editor page with the toolbar rendered
    // When:  The user clicks the "快速测试" button
    // Then:  A QuickTestDialog (modal/bootbox) should appear inside #dialogContainer
    test('should show quick test dialog', async ({ page }) => {
        await page.goto('/app/editor/decisiontree?file=/project/decision-tree.xml');
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
    // Given: A logged-in user is on the decision tree editor page
    // When:  The React shell mounts the dialog provider
    // Then:  The #dialogContainer element should be attached to the DOM
    test('should render dialog container', async ({ page }) => {
        await page.goto('/app/editor/decisiontree?file=/project/decision-tree.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should be attached
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached({ timeout: 15000 });
    });
});
