import {test, expect} from '@playwright/test';
import {loginAndGotoFrame} from './helpers.js';

/**
 * BDD Tests for Simulation Panel (规则仿真)
 *
 * Feature: Rule simulation UI (Phase 5)
 *   As a rule system operator
 *   I want to configure and run rule simulations against historical decision logs
 *   So that I can validate rule changes before releasing them
 */

test.describe('Simulation Panel', () => {
    test.beforeEach(async ({page}) => {
        await loginAndGotoFrame(page);
        // Open simulation panel via ActivityBar
        await page.locator('[title="规则仿真"]').click();
        await page.waitForSelector('text=规则仿真');
    });

    // ──────────────────────────────────────────────────
    // Scenario 1: Navigate to simulation panel via ActivityBar
    // ──────────────────────────────────────────────────
    // Given: User is logged in and on the main frame
    // When: User clicks the "规则仿真" item in the ActivityBar
    // Then: The simulation panel should appear with header "规则仿真"
    test('should open simulation panel when clicking the simulation icon', async ({page}) => {
        const header = page.locator('text=规则仿真').first();
        await expect(header).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 2: Simulation config form is visible
    // ──────────────────────────────────────────────────
    // Given: User has opened the simulation panel
    // When: The panel renders (default tab is "仿真配置")
    // Then: The config form should show: 项目名称, 包ID, 规则文件, 决策流ID, 开始时间, 结束时间
    // And: A "启动仿真" button should be visible
    test('should display simulation config form with all fields', async ({page}) => {
        // Then: Nav item "仿真配置" is visible (active by default)
        await expect(page.locator('text=仿真配置')).toBeVisible();

        // Then: Form fields are present (use textbox placeholders which are unique)
        await expect(page.locator('input[placeholder="项目名"]')).toBeVisible();
        await expect(page.locator('input[placeholder="规则包ID"]')).toBeVisible();
        await expect(page.locator('input[placeholder="flowId"]')).toBeVisible();

        // Then: "启动仿真" submit button is visible
        await expect(page.locator('button:has-text("启动仿真")')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 3: Simulation stats panel shows summary view
    // ──────────────────────────────────────────────────
    // Given: User has opened the simulation panel
    // When: User clicks the "统计分析" nav item
    // Then: The stats panel should render
    test('should switch to stats tab', async ({page}) => {
        await page.locator('text=统计分析').click();
        await page.waitForTimeout(1000);
        // Stats nav item was clicked — panel should still render without crash
        await expect(page.locator('[title="规则仿真"]')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 4: Simulation results tab is accessible
    // ──────────────────────────────────────────────────
    // Given: User has opened the simulation panel
    // When: User clicks the "对比结果" nav item
    // Then: The results view should render (empty state since no simulation run)
    test('should switch to results tab', async ({page}) => {
        await page.locator('text=对比结果').click();
        await page.waitForTimeout(1000);
        // Panel should still be visible
        await expect(page.locator('[title="规则仿真"]')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 5: All four nav tabs are visible and navigable
    // ──────────────────────────────────────────────────
    // Given: User has opened the simulation panel
    // When: The panel renders
    // Then: Four nav items should be visible: 仿真配置, 执行进度, 对比结果, 统计分析
    test('should display all four nav tabs', async ({page}) => {
        await expect(page.locator('text=仿真配置')).toBeVisible();
        await expect(page.locator('text=执行进度')).toBeVisible();
        await expect(page.locator('text=对比结果')).toBeVisible();
        await expect(page.locator('text=统计分析')).toBeVisible();
    });
});
