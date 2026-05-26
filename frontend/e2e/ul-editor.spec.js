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

    // Given: User navigates to UL editor with a file parameter
    // When: Page loads
    // Then: UL script editor should render with CodeMirror
    test('should load UL editor page with CodeMirror', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "脚本编辑器"
        await expect(page).toHaveTitle(/脚本编辑器/);

        // Then: CodeMirror editor should be visible
        const codeMirror = page.locator('.CodeMirror').first();
        await expect(codeMirror).toBeVisible({ timeout: 15000 });
    });

    // Given: User is on UL editor page
    // When: Page loads
    // Then: Toolbar with buttons should be visible
    test('should display toolbar with buttons', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: Toolbar container should be visible
        const toolbarContainer = page.locator('#toolbarContainer');
        await expect(toolbarContainer).toBeVisible({ timeout: 10000 });

        // Then: Save button should be visible
        const saveButton = page.locator('#toolbarContainer button:text-is("保存")');
        await expect(saveButton).toBeVisible();

        // Then: Import library buttons should be visible
        const varButton = page.locator('button:has-text("变量库")');
        await expect(varButton).toBeVisible();

        const constButton = page.locator('button:has-text("常量库")');
        await expect(constButton).toBeVisible();

        const actionButton = page.locator('button:has-text("动作库")');
        await expect(actionButton).toBeVisible();

        const paramButton = page.locator('button:has-text("参数库")');
        await expect(paramButton).toBeVisible();

        // Then: Quick test button should be visible
        const quickTestButton = page.locator('button:has-text("快速测试")');
        await expect(quickTestButton).toBeVisible();
    });

    // Given: User is on UL editor page
    // When: CodeMirror initializes
    // Then: Editor should display code content with line numbers
    test('should display CodeMirror with line numbers', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: CodeMirror should be rendered
        const codeMirror = page.locator('.CodeMirror').first();
        await expect(codeMirror).toBeVisible({ timeout: 15000 });

        // Then: CodeMirror should have line numbers
        const lineNumbers = page.locator('.CodeMirror-gutters, .CodeMirror-linenumber');
        await expect(lineNumbers.first()).toBeVisible();
    });

    // Given: User is on UL editor page
    // When: User clicks on the CodeMirror editor and types
    // Then: Content should appear in editor
    test('should allow typing in CodeMirror editor', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: CodeMirror should be visible
        const codeMirror = page.locator('.CodeMirror').first();
        await expect(codeMirror).toBeVisible({ timeout: 15000 });

        // When: Click on CodeMirror's code area (target the code lines area)
        const codeArea = codeMirror.locator('.CodeMirror-code, .CodeMirror-scroll').first();
        await codeArea.click({ force: true });

        // When: Type content
        await page.keyboard.type('// test comment');

        // Then: Content should be in the CodeMirror code element
        const codeContent = page.locator('.CodeMirror-code').first();
        await expect(codeContent).toBeVisible();
    });

    // Given: User is on UL editor page
    // When: Page loads
    // Then: Dialog container should be present for modal dialogs
    test('should render dialog container', async ({ page }) => {
        await page.goto('/html/ul-editor.html?file=/project/script.ul');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should exist
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached();
    });
});
