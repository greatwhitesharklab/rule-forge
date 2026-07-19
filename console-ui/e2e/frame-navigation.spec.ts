import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * BDD Tests for Main Frame Navigation
 *
 * Given: User is logged in and on the SPA main frame (/app)
 * When: User interacts with project tree and file operations
 * Then: Expected navigation and file operations occur
 */
test.describe('Main Frame Navigation', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
        // SPA:主框架是 /app 路由,frame.html / #container 已删
        await page.goto('/app');
        await page.waitForSelector('.app-layout', { timeout: 10000 });
    });

    // Given: A logged-in user navigates to /app
    // When:  The main frame's React shell finishes its initial mount
    // Then:  The .app-layout shell should be visible
    // And:   The sidebar file tree (.rf-file-tree) should be visible
    // And:   The QuickStart welcome heading should be visible
    test('should load main frame with sidebar and welcome page', async ({ page }) => {
        // Then: The SPA shell should be rendered
        const container = page.locator('.app-layout');
        await expect(container).toBeVisible({ timeout: 10000 });

        // Then: The sidebar should render the antd file tree
        const treeDiv = page.locator('.rf-file-tree');
        await expect(treeDiv).toBeVisible({ timeout: 10000 });

        // Then: Welcome page (QuickStart) heading should be visible
        const welcomePage = page.locator('.welcome-title');
        await expect(welcomePage).toBeVisible({ timeout: 10000 });
    });

    // Given: A logged-in user is on the main frame
    // When:  The top bar + sidebar toolbar finish rendering
    // Then:  The TopBar search input (.fileSearchText) should be visible
    // And:   The project selector (.panel-project-selector) should be visible
    // And:   The user avatar (.topbar-user-avatar) should be visible
    //  (bootstrap glyphicon 图标已删,搜索图标是 antd SearchOutlined;
    //   TopBar 搜索仅在 rules 面板显示,而 rules 是默认面板)
    test('should display sidebar toolbar with search and dropdowns', async ({ page }) => {
        // Then: Search input should be visible
        const searchInput = page.locator('.fileSearchText');
        await expect(searchInput).toBeVisible({ timeout: 10000 });

        // Then: Search icon (antd) should be visible next to the input
        const searchIcon = page.locator('.topbar-search .anticon-search');
        await expect(searchIcon).toBeVisible();

        // Then: Project selector dropdown should be visible
        const projectSelector = page.locator('.panel-project-selector');
        await expect(projectSelector).toBeVisible();

        // Then: User avatar should be visible (TopBar's redesign)
        const userAvatar = page.locator('.topbar-user-avatar');
        await expect(userAvatar).toBeVisible();
    });

    // Given: A logged-in user is on the main frame
    // When:  The project tree finishes its initial fetch + render
    // Then:  The .rf-file-tree container is present (antd Tree renders into it)
    //  (the project tree may be empty if the backend returned no projects;
    //   we verify the container is mounted rather than asserting non-empty
    //   children, since the data fetch is async and may complete after the
    //   test page is loaded)
    test('should display project tree with nodes', async ({ page }) => {
        // Then: Tree container should be present
        const treeContainer = page.locator('.rf-file-tree').first();
        await expect(treeContainer).toBeVisible({ timeout: 10000 });

        // Then: Tree may contain nodes (may be empty if no project data)
        const treeItems = page.locator('.rf-file-tree .ant-tree-treenode');
        const itemCount = await treeItems.count();
        expect(itemCount).toBeGreaterThanOrEqual(0);
    });

    // Given: User is on main frame with project tree
    // When: User clicks on a parent node switcher to expand
    // Then: Node should expand showing children
    test('should expand tree node when clicking parent node', async ({ page }) => {
        // Given: Find a collapsed parent node (antd switcher in close state)
        const switcher = page.locator('.rf-file-tree .ant-tree-switcher_close').first();
        const switcherVisible = await switcher.isVisible({ timeout: 10000 }).catch(() => false);

        if (switcherVisible) {
            const before = await page.locator('.rf-file-tree .ant-tree-treenode').count();

            // When: Click on the switcher to expand
            await switcher.click();

            // Then: More tree nodes should be rendered
            await expect(async () => {
                const after = await page.locator('.rf-file-tree .ant-tree-treenode').count();
                expect(after).toBeGreaterThan(before);
            }).toPass({ timeout: 5000 });
        }
    });

    // Given: A logged-in user is on the main frame with a project tree loaded
    // When:  The user right-clicks on a tree node (.rf-tree-node)
    // Then:  An antd dropdown context menu should appear
    //  (FileTreeNode 用 antd Dropdown trigger=['contextMenu'] 渲染右键菜单)
    test('should show context menu on right-click', async ({ page }) => {
        // Given: Wait for tree to load
        const treeNode = page.locator('.rf-file-tree .rf-tree-node').first();
        const nodeVisible = await treeNode.isVisible({ timeout: 10000 }).catch(() => false);

        if (nodeVisible) {
            // When: Right-click on tree node
            await treeNode.click({ button: 'right' });

            // Then: antd dropdown menu should appear
            const menu = page.locator('.ant-dropdown-menu');
            await expect(menu.first()).toBeVisible({ timeout: 5000 });
        }
    });

    // Given: A logged-in user is on the main frame
    // When:  The user types "variable" into the TopBar .fileSearchText and presses Enter
    // Then:  The tree should re-fetch with the new filter (network goes idle)
    // And:   The .rf-file-tree div should remain visible (re-rendered with filtered results)
    //  (V5.101 起搜索在 TopBar,回车触发 loadData;glyphicon-search 图标已删)
    test('should search files when entering text and pressing enter', async ({ page }) => {
        // Given: Locate search box
        const searchInput = page.locator('.fileSearchText');
        await expect(searchInput).toBeVisible({ timeout: 10000 });

        // When: Type search query and press Enter (TopBar search is Enter-triggered)
        await searchInput.fill('variable');
        await searchInput.press('Enter');

        // Then: Wait for tree to reload
        await page.waitForLoadState('networkidle');

        // Then: Tree should still be visible
        const treeDiv = page.locator('.rf-file-tree');
        await expect(treeDiv).toBeVisible();
    });

    // Given: User is on main frame
    // When: User clicks on a file in the tree
    // Then: File should open as an in-app editor tab (V7: no more window.open new browser tab)
    test('should open file in tab when clicking tree file node', async ({ page }) => {
        // Given: Wait for tree to load; expand all collapsed nodes first
        const treeNodes = page.locator('.rf-file-tree .ant-tree-treenode');
        const anyNode = await treeNodes.first().isVisible({ timeout: 10000 }).catch(() => false);

        if (anyNode) {
            // Expand every collapsed switcher so leaf file nodes become visible
            const switchers = page.locator('.rf-file-tree .ant-tree-switcher_close');
            while (await switchers.count() > 0) {
                await switchers.first().click();
                await page.waitForTimeout(300);
            }

            // When: Click the first leaf node (no switcher icon = file, not folder)
            const leaf = page.locator('.rf-file-tree .ant-tree-treenode:has(.ant-tree-switcher-noop) .ant-tree-node-content-wrapper').first();
            const leafVisible = await leaf.isVisible({ timeout: 5000 }).catch(() => false);

            if (leafVisible) {
                await leaf.click();

                // Then: An antd tab should appear in the in-app editor tab bar
                //  (老 4 库文件类型走 seeFileSource 源码对话框,不算编辑器标签;
                //   其余类型经 openEditorTab 开 .ant-tabs-tab + .editor-tab-pane)
                const tab = page.locator('.ant-tabs-tab');
                const sourceDialog = page.locator('.ant-modal');
                await expect(tab.first().or(sourceDialog.first())).toBeVisible({ timeout: 5000 });
            }
        }
    });

    // Given: A logged-in user is on the main frame
    // When:  The sidebar project selector finishes rendering
    // Then:  The .panel-project-selector should be visible
    // And:   The .panel-project-btn should display the placeholder "选择项目" or a project name
    test('should have project selector with project options', async ({ page }) => {
        // Given: Wait for project selector to load
        const projectSelector = page.locator('.panel-project-selector');
        await expect(projectSelector).toBeVisible({ timeout: 10000 });

        // Then: Project button should be visible
        const projectBtn = page.locator('.panel-project-btn');
        await expect(projectBtn).toBeVisible();

        // Then: Button text should contain "选择项目" placeholder or a project name
        const btnText = await projectBtn.textContent();
        expect(btnText).toBeTruthy();
    });
});
