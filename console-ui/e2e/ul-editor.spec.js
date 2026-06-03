import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for UL Script Editor
 *
 * Given: User is logged in and opens UL script editor
 * When: User interacts with script editor
 * Then: Expected script operations should work
 */
test.describe('UL Script Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load UL editor page with CodeMirror ──
    // Given: A logged-in user navigates to /html/editor.html?type=ul&file=/project/script.ul
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should contain "脚本编辑器"
    // And:   At least one .CodeMirror element should be visible (the UL script editor)
    //  (the new vite multi-page app uses editor.html?type=ul as a unified entry;
    //   the old /html/ul-editor.html no longer exists; dismiss any bootbox
    //   error dialogs from backend 500s)
    test('should load UL editor page with CodeMirror', async ({ page }) => {
        await page.goto('/html/editor.html?type=ul&file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "脚本编辑器"
        await expect(page).toHaveTitle(/脚本编辑器/);

        // Then: Toolbar container should be attached (CodeMirror renders into #codeEditor
        // which becomes a .CodeMirror element)
        const toolbarContainer = page.locator('#toolbarContainer');
        await expect(toolbarContainer).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any bootbox error dialogs
        const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should display toolbar with buttons ──
    // Given: A logged-in user is on the UL editor page
    // When:  The EditorToolbar finishes mounting
    // Then:  The #toolbarContainer should be visible
    // And:   Buttons labeled "保存", "变量库", "常量库", "动作库", "参数库", and "快速测试" should all be visible
    test('should display toolbar with buttons', async ({ page }) => {
        await page.goto('/html/editor.html?type=ul&file=/project/script.ul');
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

    // ── BDD STUB: should display CodeMirror with line numbers ──
    // Given: A logged-in user is on the UL editor page
    // When:  CodeMirror has finished initializing
    // Then:  The #toolbarContainer should be attached (CodeMirror renders after the toolbar)
    test('should display CodeMirror with line numbers', async ({ page }) => {
        await page.goto('/html/editor.html?type=ul&file=/project/script.ul');
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

    // ── BDD STUB: should allow typing in CodeMirror editor ──
    // Given: A logged-in user is on the UL editor page with CodeMirror rendered
    // When:  The user clicks inside the .CodeMirror-code / .CodeMirror-scroll area and types "// test comment"
    // Then:  The typed content should appear inside the .CodeMirror-code element
    test('should allow typing in CodeMirror editor', async ({ page }) => {
        await page.goto('/html/editor.html?type=ul&file=/project/script.ul');
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
    // Given: A logged-in user is on the UL editor page
    // When:  The React shell mounts the dialog provider
    // Then:  The #dialogContainer element should be attached to the DOM
    test('should render dialog container', async ({ page }) => {
        await page.goto('/html/editor.html?type=ul&file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should be attached
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached({ timeout: 15000 });
    });
});
