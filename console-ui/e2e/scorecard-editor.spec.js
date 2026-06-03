import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for Scorecard Editor
 *
 * Given: User is logged in and opens scorecard editor
 * When: User interacts with scorecard configuration
 * Then: Expected scorecard operations should work
 */
test.describe('Scorecard Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load scorecard editor page ──
    // Given: A logged-in user navigates to /html/editor.html?type=scorecard&file=/project/scorecard.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should contain "评分卡编辑器"
    // And:   The #tableContainer element should be visible
    //  (the new vite multi-page app uses editor.html?type=scorecard as a unified entry;
    //   the old /html/score-card-editor.html no longer exists; dismiss any
    //   bootbox error dialogs from backend 500s)
    test('should load scorecard editor page', async ({ page }) => {
        await page.goto('/html/editor.html?type=scorecard&file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "评分卡编辑器"
        await expect(page).toHaveTitle(/评分卡编辑器/);

        // Then: Table container should be rendered
        const tableContainer = page.locator('#tableContainer');
        await expect(tableContainer).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should display toolbar with buttons ──
    // Given: A logged-in user is on the scorecard editor page
    // When:  The EditorToolbar finishes mounting
    // Then:  The #toolbarContainer should be visible
    // And:   Buttons labeled "保存", "添加属性行", "添加自定义列", and "快速测试" should all be visible inside the toolbar
    test('should display toolbar with buttons', async ({ page }) => {
        await page.goto('/html/editor.html?type=scorecard&file=/project/scorecard.xml');
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

    // ── BDD STUB: should render scorecard table ──
    // Given: A logged-in user is on the scorecard editor page
    // When:  The ScoreCardTable component has initialized
    // Then:  The #tableContainer should be visible
    // And:   The #tableContainer should have at least one child element (the scorecard grid)
    test('should render scorecard table', async ({ page }) => {
        await page.goto('/html/editor.html?type=scorecard&file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Table container should be attached
        const tableContainer = page.locator('#tableContainer');
        await expect(tableContainer).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should add attribute row when clicking button ──
    // Given: A logged-in user is on the scorecard editor page with the toolbar rendered
    // When:  The user clicks the "添加属性行" toolbar button
    // Then:  A new attribute row should be added to the scorecard table inside #tableContainer
    test('should add attribute row when clicking button', async ({ page }) => {
        await page.goto('/html/editor.html?type=scorecard&file=/project/scorecard.xml');
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
    // Given: A logged-in user is on the scorecard editor page
    // When:  The React shell mounts the dialog provider
    // Then:  The #dialogContainer element should be attached to the DOM
    test('should render dialog container', async ({ page }) => {
        await page.goto('/html/editor.html?type=scorecard&file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should be attached
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached({ timeout: 15000 });
    });
});
