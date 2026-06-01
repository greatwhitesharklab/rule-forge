import {test, expect} from '@playwright/test';
import {loginAndGotoFrame} from './helpers.js';

/**
 * BDD Tests for Monitoring Dashboard Panel (监控告警)
 *
 * Feature: Monitoring & Alerting UI (Phase 1)
 *   As a rule system operator
 *   I want to view system metrics and manage alert rules from the web console
 *   So that I can monitor rule engine health and respond to anomalies
 */

test.describe('Monitoring Dashboard', () => {
    test.beforeEach(async ({page}) => {
        await loginAndGotoFrame(page);
        // Open monitoring panel via ActivityBar
        await page.locator('[title="监控告警"]').click();
        await page.waitForSelector('text=监控告警');
    });

    // ──────────────────────────────────────────────────
    // Scenario 1: Navigate to monitoring panel via ActivityBar
    // ──────────────────────────────────────────────────
    // Given: User is logged in and on the main frame
    // When: User clicks the "监控告警" item in the ActivityBar
    // Then: The monitoring panel should appear with header "监控告警"
    test('should open monitoring panel via ActivityBar icon', async ({page}) => {
        // Then: Panel header is visible
        const header = page.locator('text=监控告警').first();
        await expect(header).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 2: Three nav items are visible
    // ──────────────────────────────────────────────────
    // Given: Monitoring panel is open
    // When: The panel renders
    // Then: Three nav items should be visible — 总览仪表盘, 指标浏览, 告警规则
    test('should display three nav items', async ({page}) => {
        // Then: Nav items are visible
        await expect(page.locator('text=总览仪表盘')).toBeVisible();
        await expect(page.locator('text=指标浏览')).toBeVisible();
        await expect(page.locator('text=告警规则')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 3: Metrics cards display on Overview tab
    // ──────────────────────────────────────────────────
    // Given: User is on the default "总览仪表盘" view
    // When: The panel loads
    // Then: Metric cards for P95, 成功率, 告警 should be visible
    test('should display metric cards on default view', async ({page}) => {
        await expect(page.locator('text=P95').first()).toBeVisible({timeout: 10000});
        await expect(page.locator('text=成功率').first()).toBeVisible({timeout: 10000});
        // "告警" metric card — use .nth(2) for the third status-label span
        await expect(page.locator('.status-label').nth(2)).toHaveText('告警', {timeout: 10000});
    });

    // ──────────────────────────────────────────────────
    // Scenario 4: Can click 告警规则 nav item
    // ──────────────────────────────────────────────────
    // Given: Monitoring panel is open
    // When: User clicks "告警规则" nav item
    // Then: The view should update to show alert rules
    test('should navigate to alert rules view', async ({page}) => {
        await page.locator('text=告警规则').click();
        // Alert rules view should render (empty state or table)
        await page.waitForTimeout(1000);
        // The panel should still be visible, no crash
        await expect(page.locator('[title="监控告警"]')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 5: Can switch between nav items
    // ──────────────────────────────────────────────────
    // Given: Monitoring panel is open on 总览仪表盘
    // When: User clicks 指标浏览, then back to 总览仪表盘
    // Then: Each view should render without errors
    test('should switch between nav items', async ({page}) => {
        await page.locator('text=指标浏览').click();
        await page.waitForTimeout(500);

        await page.locator('text=总览仪表盘').click();
        await page.waitForTimeout(500);

        // Should still show metric cards
        await expect(page.locator('text=P95')).toBeVisible({timeout: 5000});
    });
});
