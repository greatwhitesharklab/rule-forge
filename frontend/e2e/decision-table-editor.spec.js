import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for Decision Table Editor
 *
 * Given: User is logged in and opens decision table editor
 * When: User interacts with decision table
 * Then: Expected table operations should work
 */
test.describe('Decision Table Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // Given: User navigates to decision table editor with a file parameter
    // When: Page loads
    // Then: Decision table editor should render
    test('should load decision table editor page', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "决策表编辑器"
        await expect(page).toHaveTitle(/决策表编辑器/);

        // Then: Container should be rendered
        const container = page.locator('#container');
        await expect(container).toBeVisible();
    });

    // Given: User is on decision table editor page
    // When: Page loads
    // Then: Container should have content
    test('should render container with content', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: Container should have child elements
        const container = page.locator('#container');
        await expect(container).toBeVisible({ timeout: 10000 });

        const children = container.locator('*');
        const childCount = await children.count();
        expect(childCount).toBeGreaterThan(0);
    });

    // Given: User is on decision table editor page
    // When: Page loads
    // Then: Table editor should initialize in container
    test('should initialize table editor', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: Container should have content from DecisionTable initialization
        const container = page.locator('#container');
        await expect(container).toBeVisible({ timeout: 10000 });

        // Then: Container should have some rendered content
        const innerHTML = await container.evaluate(el => el.innerHTML);
        expect(innerHTML.length).toBeGreaterThan(0);
    });

    // Given: User is on decision table editor page
    // When: User right-clicks on container
    // Then: No error should occur
    test('should handle right-click on container', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // When: Right-click on container
        const container = page.locator('#container');
        await container.click({ button: 'right' });

        // Then: Wait briefly for any context menu
        await page.waitForTimeout(500);
    });

    // Given: User is on decision table editor page
    // When: Page loads
    // Then: Dialog container should be present for modal dialogs
    test('should render dialog components', async ({ page }) => {
        await page.goto('/html/decision-table-editor.html?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should exist
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached();

        // Then: Dialog container should have React-rendered components
        const dialogChildren = dialogContainer.locator('*');
        const childCount = await dialogChildren.count();
        expect(childCount).toBeGreaterThan(0);
    });
});
