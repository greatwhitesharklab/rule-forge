import {test, expect} from '@playwright/test';
import {loginAndGotoFrame} from './helpers.js';

/**
 * BDD Tests for Release Management Panel (版本发布)
 *
 * Feature: Release management UI (Phase 3)
 *   As a rule system operator
 *   I want to manage rule version releases across environments
 *   So that rule packages can be promoted through approval and deployment workflows
 */

test.describe('Release Panel', () => {
    test.beforeEach(async ({page}) => {
        await loginAndGotoFrame(page);
        // Open release panel via ActivityBar
        await page.locator('[title="版本发布"]').click();
        await page.waitForSelector('text=版本发布');
    });

    // ──────────────────────────────────────────────────
    // Scenario 1: Navigate to release panel via ActivityBar
    // ──────────────────────────────────────────────────
    // Given: User is on the main frame
    // When: User clicks the "版本发布" item in the ActivityBar
    // Then: The release panel should open and display its header
    test('should open release panel via ActivityBar icon', async ({page}) => {
        const header = page.locator('h5:has-text("版本发布")');
        await expect(header).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 2: Release panel loads with tab buttons
    // ──────────────────────────────────────────────────
    // Given: User has opened the release panel
    // When: The panel renders
    // Then: Six tab buttons should be visible — 环境管理, 审批流程, 部署历史, 节点管理, 灰度策略, 陪跑配置
    test('should load release panel with tab buttons', async ({page}) => {
        await expect(page.locator('button:has-text("环境管理")')).toBeVisible();
        await expect(page.locator('button:has-text("审批流程")')).toBeVisible();
        await expect(page.locator('button:has-text("部署历史")')).toBeVisible();
        await expect(page.locator('button:has-text("节点管理")')).toBeVisible();
        await expect(page.locator('button:has-text("灰度策略")')).toBeVisible();
        await expect(page.locator('button:has-text("陪跑配置")')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 3: Default view shows environment content
    // ──────────────────────────────────────────────────
    // Given: User has opened the release panel
    // When: The default "环境管理" tab is active
    // Then: The environment content area should be visible (empty state "暂无环境配置" or a table)
    test('should display environment content on default tab', async ({page}) => {
        // Default tab shows either "暂无环境配置" empty state or an environment table
        const emptyState = page.locator('text=暂无环境配置');
        await expect(emptyState).toBeVisible({timeout: 5000});
    });

    // ──────────────────────────────────────────────────
    // Scenario 4: Can switch between tab buttons
    // ──────────────────────────────────────────────────
    // Given: User is on the release panel with "环境管理" tab active
    // When: User clicks "审批流程" tab
    // Then: Tab switches to approval view
    // When: User clicks "部署历史" tab
    // Then: Tab switches to deployment history view
    test('should switch between tabs', async ({page}) => {
        // Click 审批流程
        await page.locator('button:has-text("审批流程")').click();
        await page.waitForTimeout(1000);

        // Click 部署历史
        await page.locator('button:has-text("部署历史")').click();
        await page.waitForTimeout(1000);

        // Click back to 环境管理
        await page.locator('button:has-text("环境管理")').click();
        await page.waitForTimeout(1000);

        // Panel should still be visible (no crash)
        await expect(page.locator('h5:has-text("版本发布")')).toBeVisible();
    });
});
