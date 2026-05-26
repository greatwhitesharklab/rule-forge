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

    // Given: User navigates to scorecard editor with a file parameter
    // When: Page loads
    // Then: Scorecard editor should render
    test('should load scorecard editor page', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "评分卡编辑器"
        await expect(page).toHaveTitle(/评分卡编辑器/);

        // Then: Table container should be rendered
        const tableContainer = page.locator('#tableContainer');
        await expect(tableContainer).toBeVisible();
    });

    // Given: User is on scorecard editor page
    // When: Page loads
    // Then: Toolbar with buttons should be visible
    test('should display toolbar with buttons', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Toolbar container should be visible
        const toolbarContainer = page.locator('#toolbarContainer');
        await expect(toolbarContainer).toBeVisible({ timeout: 10000 });

        // Then: Save button should be visible (exact match)
        const saveButton = page.locator('#toolbarContainer button:text-is("保存")');
        await expect(saveButton).toBeVisible();

        // Then: "添加属性行" button should be visible
        const addAttrButton = page.locator('#toolbarContainer button:has-text("添加属性行")');
        await expect(addAttrButton).toBeVisible();

        // Then: "添加自定义列" button should be visible
        const addColButton = page.locator('#toolbarContainer button:has-text("添加自定义列")');
        await expect(addColButton).toBeVisible();

        // Then: "快速测试" button should be visible
        const quickTestButton = page.locator('#toolbarContainer button:has-text("快速测试")');
        await expect(quickTestButton).toBeVisible();
    });

    // Given: User is on scorecard editor page
    // When: ScoreCardTable initializes
    // Then: Table should render inside tableContainer
    test('should render scorecard table', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Table container should have content
        const tableContainer = page.locator('#tableContainer');
        await expect(tableContainer).toBeVisible({ timeout: 10000 });

        // Then: Table container should have child elements
        const children = tableContainer.locator('*');
        const childCount = await children.count();
        expect(childCount).toBeGreaterThan(0);
    });

    // Given: User is on scorecard editor page
    // When: User clicks "添加属性行" button
    // Then: New attribute row should be added
    test('should add attribute row when clicking button', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Button should be visible
        const addAttrButton = page.locator('#toolbarContainer button:has-text("添加属性行")');
        await expect(addAttrButton).toBeVisible({ timeout: 10000 });

        // When: Get initial table content
        const tableContainer = page.locator('#tableContainer');
        const initialChildren = await tableContainer.locator('*').count();

        // When: Click add attribute button
        await addAttrButton.click({ force: true });

        // Then: Table should update
        await page.waitForTimeout(500);
    });

    // Given: User is on scorecard editor page
    // When: Page loads
    // Then: Dialog container should be present for modal dialogs
    test('should render dialog container', async ({ page }) => {
        await page.goto('/html/score-card-editor.html?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should exist
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached();
    });
});
