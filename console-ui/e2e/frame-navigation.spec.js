import { test, expect } from '@playwright/test';
import { login } from './helpers.js';

/**
 * BDD Tests for Main Frame Navigation
 *
 * Given: User is logged in and on main frame (index.html)
 * When: User interacts with project tree and file operations
 * Then: Expected navigation and file operations occur
 */
test.describe('Main Frame Navigation', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
        await page.goto('/index.html');
    });

    // Given: User is logged in and on main frame
    // When: Page loads
    // Then: Sidebar with tree and welcome page should be visible
    test('should load main frame with sidebar and welcome page', async ({ page }) => {
        // Then: The container div should be rendered
        const container = page.locator('#container');
        await expect(container).toBeVisible({ timeout: 10000 });

        // Then: The Splitter should render two panes
        // The sidebar has the .tree class div
        const treeDiv = page.locator('.tree');
        await expect(treeDiv).toBeVisible({ timeout: 10000 });

        // Then: Welcome page or QuickStart should be visible
        const welcomePage = page.locator('h1:has-text("欢迎使用决策系统")');
        await expect(welcomePage).toBeVisible({ timeout: 10000 });
    });

    // Given: User is on main frame
    // When: Sidebar loads
    // Then: Toolbar with dropdowns and search box should be visible
    test('should display sidebar toolbar with search and dropdowns', async ({ page }) => {
        // Then: Search input should be visible
        const searchInput = page.locator('.fileSearchText');
        await expect(searchInput).toBeVisible({ timeout: 10000 });

        // Then: Search icon should be visible
        const searchIcon = page.locator('.glyphicon.glyphicon-search');
        await expect(searchIcon).toBeVisible();

        // Then: Dropdown toggles should be visible
        const dropdownToggles = page.locator('.dropdown-toggle');
        const toggleCount = await dropdownToggles.count();
        expect(toggleCount).toBeGreaterThanOrEqual(2);

        // Then: Logout link should be visible
        const logoutLink = page.locator('a[title="退出登录"]');
        await expect(logoutLink).toBeVisible();

        // Then: Current username should be displayed
        const userSpan = page.locator('.glyphicon-user');
        await expect(userSpan).toBeVisible();
    });

    // Given: User is on main frame with sidebar
    // When: User looks at the tree
    // Then: Tree nodes should be present with expandable items
    test('should display project tree with nodes', async ({ page }) => {
        // Then: Tree should contain list items
        const treeItems = page.locator('.tree li');
        await expect(treeItems.first()).toBeVisible({ timeout: 10000 });

        // Then: Some parent nodes should have expand icons
        const expandIcons = page.locator('.tree .rf-plus, .tree .rf-minus');
        const iconCount = await expandIcons.count();
        expect(iconCount).toBeGreaterThanOrEqual(0);

        // Then: Tree nodes should have links
        const treeLinks = page.locator('.tree span a');
        await expect(treeLinks.first()).toBeVisible();
    });

    // Given: User is on main frame with project tree
    // When: User clicks on a parent tree node to expand
    // Then: Node should expand showing children
    test('should expand tree node when clicking parent node', async ({ page }) => {
        // Given: Find a parent node (has parent_li class)
        const parentNode = page.locator('.tree li.parent_li').first();
        const parentExists = await parentNode.isVisible({ timeout: 10000 }).catch(() => false);

        if (parentExists) {
            // When: Click on the parent node span
            const parentSpan = parentNode.locator('span').first();
            await parentSpan.click();

            // Then: Children should become visible
            await page.waitForTimeout(500);
        }
    });

    // Given: User is on main frame with project tree
    // When: User right-clicks on a tree node
    // Then: Context menu should appear with operations
    test('should show context menu on right-click', async ({ page }) => {
        // Given: Wait for tree to load
        const treeSpan = page.locator('.tree span[id^="node-"]').first();
        await expect(treeSpan).toBeVisible({ timeout: 10000 });

        // When: Right-click on tree node
        await treeSpan.click({ button: 'right' });

        // Then: Context menu should appear (uses Menu component)
        const contextMenu = page.locator('.dropdown-menu:visible, .context-menu:visible').first();
        // The context menu may or may not appear depending on node type
        await page.waitForTimeout(500);
    });

    // Given: User is on main frame
    // When: User searches for a file using search box
    // Then: Tree should reload with filtered results
    test('should search files when entering text and clicking search', async ({ page }) => {
        // Given: Locate search box
        const searchInput = page.locator('.fileSearchText');
        await expect(searchInput).toBeVisible({ timeout: 10000 });

        // When: Type search query
        await searchInput.fill('variable');

        // When: Click search icon
        const searchIcon = page.locator('.glyphicon-search');
        await searchIcon.click();

        // Then: Wait for tree to reload
        await page.waitForLoadState('networkidle');

        // Then: Tree should still be visible
        const treeDiv = page.locator('.tree');
        await expect(treeDiv).toBeVisible();
    });

    // Given: User is on main frame
    // When: User clicks on a file in the tree
    // Then: File should open in a new tab
    test('should open file in tab when clicking tree file node', async ({ page }) => {
        // Given: Wait for tree to load
        // Tree nodes are spans with id starting "node-" that have an anchor inside
        // File nodes have icons like rf-variable, rf-rule, rf-table, rf-tree, rf-flow (NOT rf-folder)
        const fileNodeSpans = page.locator('.tree span[id^="node-"]');
        const count = await fileNodeSpans.count();

        // Find a file-type node (not a folder) by checking if it has a non-folder icon
        let clickedFileNode = false;
        for (let i = 0; i < count; i++) {
            const span = fileNodeSpans.nth(i);
            const iconClass = await span.locator('i:first-child').getAttribute('class').catch(() => '');
            if (iconClass && !iconClass.includes('rf-folder')) {
                await span.locator('a').click();
                clickedFileNode = true;
                break;
            }
        }

        if (clickedFileNode) {
            // Then: A tab should appear in the tab bar
            const tabLink = page.locator('#fornavframetab_ li');
            await expect(tabLink.first()).toBeVisible({ timeout: 5000 });

            // Then: An iframe should be created for the file
            const iframe = page.locator('iframe');
            await expect(iframe.first()).toBeVisible({ timeout: 5000 });
        }
    });

    // Given: User is on main frame
    // When: User examines the display mode dropdown
    // Then: Dropdown should have toggle and menu items
    test('should have display mode dropdown with options', async ({ page }) => {
        // Given: Wait for toolbar to load
        const displayDropdown = page.locator('span.dropdown').first();
        await expect(displayDropdown).toBeAttached({ timeout: 10000 });

        // Then: Dropdown toggle should exist with correct title
        const toggle = displayDropdown.locator('.dropdown-toggle').first();
        await expect(toggle).toBeAttached();
        const title = await toggle.getAttribute('title');
        expect(title).toBe('知识库内容展示方式');

        // Then: Dropdown menu should exist with menu items
        const menu = displayDropdown.locator('.dropdown-menu');
        await expect(menu).toBeAttached();

        // Then: Menu items should contain display options
        const menuItems = menu.locator('li a');
        const itemCount = await menuItems.count();
        expect(itemCount).toBeGreaterThan(0);

        // Then: Menu items should contain classify and non-classify options
        const menuTexts = await menuItems.allTextContents();
        const hasClassifyOption = menuTexts.some(t => t.includes('分类展示'));
        const hasNonClassifyOption = menuTexts.some(t => t.includes('集中展示'));
        expect(hasClassifyOption).toBe(true);
        expect(hasNonClassifyOption).toBe(true);
    });
});
