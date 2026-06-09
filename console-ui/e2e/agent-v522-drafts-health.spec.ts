import {test, expect} from '@playwright/test';
import {loginAndGotoFrame} from './helpers';

/**
 * BDD Tests for V5.22 Drafts + Health tabs in Agent AI Assistant Panel
 *
 * Feature: AI assistant exposes two new tabs alongside the chat tab
 *   - 草稿 (Drafts): list / view / submit / approve / reject AI-generated drafts
 *   - 健康 (Health): coverage cards + stale drafts + anomalies + hot rules
 *
 * As a rule system user (BA)
 * I want to see AI-generated drafts and rule health in the agent panel
 * So that I can iterate on rules and monitor deployed behavior without leaving the editor.
 */
test.describe('V5.22 — Agent panel Drafts + Health tabs', () => {
    test.beforeEach(async ({page}) => {
        await loginAndGotoFrame(page);
        // Open agent panel via ActivityBar
        await page.locator('[title="智能分析"]').click();
        await page.waitForSelector('text=AI 助手');
    });

    // ──────────────────────────────────────────────────
    // Scenario 1: All three tabs (chat / drafts / health) are visible
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open
    // When: The panel renders
    // Then: 对话 / 草稿 / 健康 tab labels are all present
    test('should show all three tabs in agent panel', async ({page}) => {
        await expect(page.getByText('对话', {exact: true})).toBeVisible();
        await expect(page.getByText('草稿', {exact: true})).toBeVisible();
        await expect(page.getByText('健康', {exact: true})).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 2: Click 草稿 tab → DraftsView loads
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open
    // When: User clicks the 草稿 tab
    // Then: DraftsView renders (project filter dropdown is visible)
    test('should load DraftsView when 草稿 tab is clicked', async ({page}) => {
        await page.getByText('草稿', {exact: true}).click();
        // DraftsView has a project filter dropdown labelled "项目"
        await expect(page.getByText('项目', {exact: true}).first()).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 3: Click 健康 tab → RuleHealthView loads
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open
    // When: User clicks the 健康 tab
    // Then: RuleHealthView renders (time window 7/30/90 toggle is visible)
    test('should load RuleHealthView when 健康 tab is clicked', async ({page}) => {
        await page.getByText('健康', {exact: true}).click();
        // RuleHealthView has a 7/30/90 day toggle (rendered as buttons or radios)
        // Look for any of the day labels to confirm the view rendered
        const day7 = page.getByText('7', {exact: true}).first();
        const day30 = page.getByText('30', {exact: true}).first();
        const day90 = page.getByText('90', {exact: true}).first();
        // At least one day label should be visible
        await expect(day7.or(day30).or(day90)).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 4: Click back to 对话 tab → chat view returns
    // ──────────────────────────────────────────────────
    // Given: User has switched to 健康 tab
    // When: User clicks the 对话 tab
    // Then: Tab becomes active (we can't see the "config" panel state but at minimum
    //       clicking the tab doesn't throw / the tab is still clickable)
    test('should switch back to 对话 tab without error', async ({page}) => {
        // First switch to 健康, then back to 对话
        await page.getByText('健康', {exact: true}).click();
        await page.getByText('对话', {exact: true}).click();
        // Both tabs should still be visible
        await expect(page.getByText('对话', {exact: true})).toBeVisible();
        await expect(page.getByText('草稿', {exact: true})).toBeVisible();
        await expect(page.getByText('健康', {exact: true})).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 5: Health view does not crash on empty / missing data
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open
    // When: User clicks 健康 tab (in test env, backend may return empty health)
    // Then: View renders without throwing — no React error overlay
    test('should render health view gracefully on empty data', async ({page}) => {
        await page.getByText('健康', {exact: true}).click();
        // Wait briefly for the fetch to settle
        await page.waitForTimeout(500);
        // The page should still have the agent panel + tabs visible
        await expect(page.getByText('AI 助手')).toBeVisible();
        await expect(page.getByText('草稿', {exact: true})).toBeVisible();
        // No console error overlay (Playwright surfaces these as "nextjs-portal" or
        // similar error nodes; we just check the agent panel didn't unmount).
    });
});
