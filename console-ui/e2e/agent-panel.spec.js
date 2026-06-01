import {test, expect} from '@playwright/test';
import {loginAndGotoFrame} from './helpers.js';

/**
 * BDD Tests for Agent AI Assistant Panel (Phase 7 — 智能分析)
 *
 * Feature: AI assistant panel embedded in the main frame sidebar
 *   As a rule system user
 *   I want to interact with an AI assistant for rule analysis
 *   So that I can get intelligent help with rule authoring and debugging
 */

test.describe('Agent AI Assistant Panel', () => {
    test.beforeEach(async ({page}) => {
        await loginAndGotoFrame(page);
        // Open agent panel via ActivityBar
        await page.locator('[title="智能分析"]').click();
        await page.waitForSelector('text=AI 助手');
    });

    // ──────────────────────────────────────────────────
    // Scenario 1: Navigate to agent panel via ActivityBar
    // ──────────────────────────────────────────────────
    // Given: User is logged in and on the main frame
    // When: User clicks the "智能分析" item in the ActivityBar
    // Then: The agent panel loads and shows "AI 助手" in the header
    test('should navigate to agent panel via ActivityBar', async ({page}) => {
        await expect(page.locator('text=AI 助手')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 2: Agent panel header shows "AI 助手"
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open
    // When: The panel renders
    // Then: Header text contains "AI 助手"
    test('should display AI 助手 in the panel header', async ({page}) => {
        const header = page.locator('text=AI 助手');
        await expect(header).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 3: Config panel opens when clicking gear icon
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open
    // When: User clicks the gear (⚙) button
    // Then: ConfigPanel should appear with vendor selector label "LLM 厂商"
    test('should open config panel when clicking gear icon', async ({page}) => {
        await page.locator('button[title="配置"]').click();
        await expect(page.locator('text=LLM 厂商')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 4: Config panel shows form fields
    // ──────────────────────────────────────────────────
    // Given: Config panel is open
    // When: The panel renders
    // Then: Input fields for API Key, Base URL, Model, Temperature should be visible
    test('should show form fields in config panel', async ({page}) => {
        await page.locator('button[title="配置"]').click();
        await expect(page.locator('text=LLM 厂商')).toBeVisible();

        // API Key label
        await expect(page.locator('text=API Key')).toBeVisible();

        // Password input is visible
        await expect(page.locator('input[type="password"]')).toBeVisible();

        // Base URL label
        await expect(page.locator('text=Base URL')).toBeVisible();

        // Model label (exact match to avoid "量化评估模型" match from welcome cards)
        await expect(page.locator('label:text-is("模型")')).toBeVisible();

        // Temperature label
        await expect(page.locator('text=Temperature')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 5: Config panel has save and test buttons
    // ──────────────────────────────────────────────────
    // Given: Config panel is open
    // When: The panel renders
    // Then: "保存" and "测试连接" buttons should be visible
    test('should show save and test buttons in config panel', async ({page}) => {
        await page.locator('button[title="配置"]').click();
        await expect(page.locator('text=LLM 厂商')).toBeVisible();

        // Use .first() to avoid matching other "保存" buttons in hidden dialogs
        await expect(page.locator('button.btn-primary:has-text("保存")').first()).toBeVisible();
        await expect(page.locator('button:has-text("测试连接")')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 6: Status bar shows configuration state
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open
    // When: The panel loads and fetches status
    // Then: A status bar should be visible showing either "已连接" or "未配置"
    test('should show status bar with configuration state', async ({page}) => {
        const statusText = page.locator('text=已连接').or(page.locator('text=未配置'));
        await expect(statusText).toBeVisible({timeout: 10000});
    });

    // ──────────────────────────────────────────────────
    // Scenario 7: New chat button is visible
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open
    // When: The panel renders
    // Then: A "+" (new chat) button should be visible
    test('should display new chat button', async ({page}) => {
        await expect(page.locator('button[title="新对话"]')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 8: Empty state prompt is visible
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open with no active session
    // When: The panel renders
    // Then: The empty state "点击 + 开始与 AI 对话" should be visible
    test('should display empty state prompt', async ({page}) => {
        await expect(page.locator('text=点击 + 开始与 AI 对话')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 9: New chat button is clickable without crash
    // ──────────────────────────────────────────────────
    // Given: Agent panel is open
    // When: User clicks new chat button
    // Then: Panel should not crash (even if backend is unavailable)
    test('should handle new chat click gracefully', async ({page}) => {
        await page.locator('button[title="新对话"]').click();
        await page.waitForTimeout(2000);
        // Panel should still be visible
        await expect(page.locator('text=AI 助手')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 10: Config panel can be toggled closed
    // ──────────────────────────────────────────────────
    // Given: Config panel is open
    // When: User clicks the gear button again
    // Then: Config panel should hide
    test('should toggle config panel closed', async ({page}) => {
        // Open config
        await page.locator('button[title="配置"]').click();
        await expect(page.locator('text=LLM 厂商')).toBeVisible();

        // Close config
        await page.locator('button[title="配置"]').click();
        await expect(page.locator('text=LLM 厂商')).not.toBeVisible();
    });
});
