import {test, expect} from '@playwright/test';
import {loginAndGotoFrame} from './helpers';

/**
 * BDD Tests for Release Management Panel (版本发布)
 *
 * Feature: Release management UI (Phase 3)
 *   As a rule system operator
 *   I want to manage rule version releases across environments
 *   So that rule packages can be promoted through approval and deployment workflows
 *
 * V7.x:面板已 antd 化(PageShell + antd Tabs),老 bootstrap h5/button 标签断言作废。
 */

test.describe('Release Panel', () => {
    test.beforeEach(async ({page}) => {
        await loginAndGotoFrame(page);
        // Open release panel via ActivityBar
        await page.locator('[title="版本发布"]').click();
        // PageShell 的 h1.page-title = "版本发布"
        await page.waitForSelector('h1.page-title:has-text("版本发布")');
    });

    // ──────────────────────────────────────────────────
    // Scenario 1: Navigate to release panel via ActivityBar
    // ──────────────────────────────────────────────────
    // Given: User is on the main frame
    // When: User clicks the "版本发布" item in the ActivityBar
    // Then: The release panel should open and display its header
    test('should open release panel via ActivityBar icon', async ({page}) => {
        const header = page.locator('h1.page-title:has-text("版本发布")');
        await expect(header).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 2: Release panel loads with tab buttons
    // ──────────────────────────────────────────────────
    // Given: User has opened the release panel
    // When: The panel renders
    // Then: Six antd tabs should be visible — 环境管理, 审批流程, 部署历史, 节点管理, 灰度策略, 陪跑配置
    test('should load release panel with tab buttons', async ({page}) => {
        const shell = page.locator('.page-shell').filter({has: page.locator('h1.page-title', {hasText: '版本发布'})});
        await expect(shell.locator('.ant-tabs-tab:has-text("环境管理")')).toBeVisible();
        await expect(shell.locator('.ant-tabs-tab:has-text("审批流程")')).toBeVisible();
        await expect(shell.locator('.ant-tabs-tab:has-text("部署历史")')).toBeVisible();
        await expect(shell.locator('.ant-tabs-tab:has-text("节点管理")')).toBeVisible();
        await expect(shell.locator('.ant-tabs-tab:has-text("灰度策略")')).toBeVisible();
        await expect(shell.locator('.ant-tabs-tab:has-text("陪跑配置")')).toBeVisible();
    });

    // ── BDD STUB: should display environment content on default tab ──
    // Given: A logged-in user has opened the release panel via the "版本发布" ActivityBar item
    // When:  The default "环境管理" tab is active
    // Then:  The environment content area is present in DOM
    // And:   Any of the following valid renderings is acceptable:
    //          - empty-state message "暂无环境配置" (no environments configured)
    //          - "加载中" loading indicator (API request still in flight)
    //          - "请先选择一个项目" (no project selected in the file tree yet)
    //          - an environment <table> (rendered even if empty)
    test('should display environment content on default tab', async ({page}) => {
        const emptyOrLoading = page.locator('text=暂无环境配置')
            .or(page.locator('text=加载中'))
            .or(page.locator('text=请先选择一个项目'));
        const envTable = page.locator('table').first();
        // OR-check: at least one of these is present in the DOM
        const emptyShown = await emptyOrLoading.first().isVisible({timeout: 2000}).catch(() => false);
        const tableAttached = await envTable.isVisible({timeout: 2000}).catch(() => false);
        const tableInDom = await envTable.count() > 0;
        expect(emptyShown || tableAttached || tableInDom).toBe(true);
    });

    // ──────────────────────────────────────────────────
    // Scenario 4: Can switch between tab buttons
    // ──────────────────────────────────────────────────
    // Given: User is on the release panel with "环境管理" tab active
    // When: User clicks "审批流程" tab
    // Then: Tab switches to approval view (.ant-tabs-tab-active moves)
    // When: User clicks "部署历史" tab
    // Then: Tab switches to deployment history view
    test('should switch between tabs', async ({page}) => {
        const shell = page.locator('.page-shell').filter({has: page.locator('h1.page-title', {hasText: '版本发布'})});

        // Click 审批流程
        await shell.locator('.ant-tabs-tab:has-text("审批流程")').click();
        await expect(shell.locator('.ant-tabs-tab-active')).toContainText('审批流程');

        // Click 部署历史
        await shell.locator('.ant-tabs-tab:has-text("部署历史")').click();
        await expect(shell.locator('.ant-tabs-tab-active')).toContainText('部署历史');

        // Click back to 环境管理
        await shell.locator('.ant-tabs-tab:has-text("环境管理")').click();
        await expect(shell.locator('.ant-tabs-tab-active')).toContainText('环境管理');

        // Panel should still be visible (no crash)
        await expect(page.locator('h1.page-title:has-text("版本发布")')).toBeVisible();
    });
});
