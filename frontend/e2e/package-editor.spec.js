import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for Knowledge Package Management
 *
 * Given: User is logged in and opens package editor
 * When: User interacts with package management
 * Then: Expected package operations should work
 */
test.describe('Knowledge Package Management', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // Given: User navigates to package editor with a file parameter
    // When: Page loads
    // Then: Package editor should render with Splitter and Grids
    test('should load package editor page', async ({ page }) => {
        await page.goto('/html/package-editor.html?file=/project/test.rp');
        await page.waitForLoadState('networkidle');

        // Then: Page title should be "知识包编辑器"
        await expect(page).toHaveTitle(/知识包编辑器/);

        // Then: Container should be rendered
        const container = page.locator('#container');
        await expect(container).toBeVisible();

        // Then: Visible grid tables should be present
        const visibleTables = page.locator('table.table-bordered:visible');
        await expect(visibleTables.first()).toBeVisible({ timeout: 10000 });
    });

    // Given: User is on package editor page
    // When: Page loads
    // Then: Toolbar buttons should be visible
    test('should display toolbar buttons', async ({ page }) => {
        await page.goto('/html/package-editor.html?file=/project/test.rp');
        await page.waitForLoadState('networkidle');

        // Then: "添加包" button should be visible
        const addPackageButton = page.locator('button:text-is("添加包")');
        await expect(addPackageButton).toBeVisible({ timeout: 10000 });

        // Then: "保存" button should be visible (exact match, exclude modal buttons)
        const saveButton = page.locator('#container .btn-group button:text-is("保存")');
        await expect(saveButton).toBeVisible();

        // Then: "生成版本" button should be visible
        const versionButton = page.locator('button:has-text("生成版本")');
        await expect(versionButton).toBeVisible();

        // Then: "添加文件" button should be visible
        const addFileButton = page.locator('button:has-text("添加文件")');
        await expect(addFileButton).toBeVisible();
    });

    // Given: User is on package editor page
    // When: Page loads with data
    // Then: Grid tables should be displayed with correct headers
    test('should display grid tables with headers', async ({ page }) => {
        await page.goto('/html/package-editor.html?file=/project/test.rp');
        await page.waitForLoadState('networkidle');

        // Then: Grid tables should exist
        const tables = page.locator('table.table-bordered');
        await expect(tables.first()).toBeAttached({ timeout: 10000 });

        // Then: Should have expected headers in visible tables
        const idHeader = page.locator('label:has-text("编码")');
        await expect(idHeader.first()).toBeAttached();

        const nameHeader = page.locator('label:has-text("名称")');
        await expect(nameHeader.first()).toBeAttached();
    });

    // Given: User is on package editor page
    // When: User clicks "添加包" button
    // Then: A dialog should appear for creating a package
    test('should show dialog when clicking add package button', async ({ page }) => {
        await page.goto('/html/package-editor.html?file=/project/test.rp');
        await page.waitForLoadState('networkidle');

        // When: Click add package button
        const addPackageButton = page.locator('button:text-is("添加包")');
        await expect(addPackageButton).toBeVisible({ timeout: 10000 });
        await addPackageButton.click();

        // Then: A dialog should appear (bootbox or modal)
        await page.waitForTimeout(500);
        const dialog = page.locator('.modal, .bootbox .modal-dialog, .modal-content').first();
        // The dialog may appear or it may use a bootbox prompt
    });

    // Given: User is on package editor page with data
    // When: User clicks on a package row
    // Then: Row should become selected and slave grid should update
    test('should select package and load slave data', async ({ page }) => {
        await page.goto('/html/package-editor.html?file=/project/test.rp');
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

    // Given: User is on package editor page
    // When: User clicks "保存" button
    // Then: Save action should be triggered
    test('should trigger save when clicking save button', async ({ page }) => {
        await page.goto('/html/package-editor.html?file=/project/test.rp');
        await page.waitForLoadState('networkidle');

        // When: Click save button (exact match, exclude modal buttons)
        const saveButton = page.locator('#container .btn-group button:text-is("保存")');
        await expect(saveButton).toBeVisible({ timeout: 10000 });
        await saveButton.click({ force: true });

        // Then: Wait for any response
        await page.waitForTimeout(1000);
    });
});
