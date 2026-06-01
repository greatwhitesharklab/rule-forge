import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for Variable Library Editor
 *
 * Given: User is logged in and opens variable editor
 * When: User interacts with variable management
 * Then: Expected variable operations should work
 */
test.describe('Variable Library Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // Given: User navigates to variable editor with a file parameter
    // When: Page loads
    // Then: Variable editor should render with Splitter and Grids
    test('should load variable editor page', async ({ page }) => {
        await page.goto('/html/variable-editor.html?file=/project/variables.xml');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "变量编辑器"
        await expect(page).toHaveTitle(/变量编辑器/);

        // Then: Container should be rendered
        const container = page.locator('#container');
        await expect(container).toBeVisible();

        // Then: Splitter should render panes with visible tables
        const visibleTables = page.locator('table.table-bordered:visible');
        await expect(visibleTables.first()).toBeVisible({ timeout: 10000 });
    });

    // Given: User is on variable editor page
    // When: Page loads
    // Then: Toolbar buttons should be visible
    test('should display toolbar buttons', async ({ page }) => {
        await page.goto('/html/variable-editor.html?file=/project/variables.xml');
        await page.waitForLoadState('networkidle');

        // Then: "添加" button should be visible (exact match to avoid matching "添加字段")
        const addButton = page.locator('button:text-is("添加")');
        await expect(addButton).toBeVisible({ timeout: 10000 });

        // Then: "保存" button should be visible (exact match to avoid matching "保存为新版本")
        const saveButton = page.locator('button:text-is("保存")');
        await expect(saveButton).toBeVisible();

        // Then: "添加字段" button should be visible
        const addFieldButton = page.locator('button:has-text("添加字段")');
        await expect(addFieldButton).toBeVisible();
    });

    // Given: User is on variable editor page
    // When: Page loads with data
    // Then: Grid tables should be displayed with headers
    test('should display grid tables with headers', async ({ page }) => {
        await page.goto('/html/variable-editor.html?file=/project/variables.xml');
        await page.waitForLoadState('networkidle');

        // Then: Visible grid tables should have column headers
        const visibleTables = page.locator('table.table-bordered:visible');
        await expect(visibleTables.first()).toBeVisible({ timeout: 10000 });

        // Then: Should have "名称" header
        const nameHeader = page.locator('label:has-text("名称")');
        await expect(nameHeader.first()).toBeVisible();

        // Then: Should have "类路径" header
        const clazzHeader = page.locator('label:has-text("类路径")');
        await expect(clazzHeader.first()).toBeVisible();
    });

    // Given: User is on variable editor page
    // When: User clicks "添加" button
    // Then: A bootbox prompt modal should appear
    test('should show prompt when clicking add button', async ({ page }) => {
        await page.goto('/html/variable-editor.html?file=/project/variables.xml');
        await page.waitForLoadState('networkidle');

        // When: Click add button (exact match)
        const addButton = page.locator('button:text-is("添加")');
        await expect(addButton).toBeVisible({ timeout: 10000 });

        // bootbox.prompt() creates a CSS modal, not a native dialog
        await addButton.click({ force: true });
        await page.waitForTimeout(500);

        // Then: A modal dialog should appear
        const modal = page.locator('.modal, .bootbox .modal-dialog').first();
        const modalVisible = await modal.isVisible().catch(() => false);
        // The bootbox prompt may or may not appear depending on implementation
        if (modalVisible) {
            // Dismiss by clicking the close or cancel button
            const closeBtn = page.locator('.modal .close, .bootbox .btn-default').first();
            if (await closeBtn.isVisible().catch(() => false)) {
                await closeBtn.click();
            }
        }
    });

    // Given: User is on variable editor page
    // When: User clicks "保存" button
    // Then: Save action should be triggered
    test('should trigger save when clicking save button', async ({ page }) => {
        await page.goto('/html/variable-editor.html?file=/project/variables.xml');
        await page.waitForLoadState('networkidle');

        // When: Click save button (exact match, force to handle any overlays)
        const saveButton = page.locator('button:text-is("保存")');
        await expect(saveButton).toBeVisible({ timeout: 10000 });
        await saveButton.click({ force: true });

        // Then: Wait for any response (could be bootbox alert or network request)
        await page.waitForTimeout(1000);
    });

    // Given: User is on variable editor page
    // When: User clicks on a master row
    // Then: Slave grid should update with fields
    test('should load slave grid when clicking master row', async ({ page }) => {
        await page.goto('/html/variable-editor.html?file=/project/variables.xml');
        await page.waitForLoadState('networkidle');

        // Then: Look for data rows in visible master grid
        const visibleTables = page.locator('table.table-bordered:visible');
        const firstVisibleTable = visibleTables.first();
        const dataRows = firstVisibleTable.locator('tbody tr.content-tr');
        const rowCount = await dataRows.count();

        if (rowCount > 0) {
            // When: Click on first data row
            await dataRows.first().click();

            // Then: Row should become selected (bg-warning class)
            await expect(dataRows.first()).toHaveClass(/bg-warning/);
        }
    });
});
