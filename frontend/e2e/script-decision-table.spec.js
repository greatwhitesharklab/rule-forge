import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

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

    // Given: User navigates to script decision table editor with a file parameter
    // When: Page loads
    // Then: Script decision table editor should render
    test('should load script decision table editor page', async ({ page }) => {
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "脚本式决策表编辑器"
        await expect(page).toHaveTitle(/脚本式决策表编辑器/);

        // Then: Container should be rendered
        const container = page.locator('#container');
        await expect(container).toBeVisible();
    });

    // Given: User is on script decision table editor page
    // When: Page loads
    // Then: Container should have content
    test('should render container with content', async ({ page }) => {
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Container should have child elements
        const container = page.locator('#container');
        await expect(container).toBeVisible({ timeout: 10000 });

        const children = container.locator('*');
        const childCount = await children.count();
        expect(childCount).toBeGreaterThan(0);
    });

    // Given: User is on script decision table editor page
    // When: User right-clicks on container
    // Then: No error should occur
    test('should handle right-click on container', async ({ page }) => {
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // When: Right-click on container
        const container = page.locator('#container');
        await container.click({ button: 'right' });

        // Then: Wait briefly for any context menu
        await page.waitForTimeout(500);
    });

    // Given: User is on script decision table editor page
    // When: Page loads
    // Then: Dialog container should be present
    test('should render dialog container', async ({ page }) => {
        await page.goto('/html/script-decision-table-editor.html?file=/project/script-table.xml');
        await page.waitForLoadState('networkidle');

        // Then: Dialog container should exist
        const dialogContainer = page.locator('#dialogContainer');
        await expect(dialogContainer).toBeAttached();
    });
});
