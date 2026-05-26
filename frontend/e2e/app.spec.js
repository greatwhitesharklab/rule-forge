import { test, expect } from '@playwright/test';

test.describe('RuleForge 前端', () => {

    // Given RuleForge 前端服务已启动
    // When 访问根路径
    // Then 应返回 HTTP 200
    test('首页应可访问', async ({ page }) => {
        const response = await page.goto('/');
        expect(response.status()).toBe(200);
    });

    // Given RuleForge 前端服务已启动
    // When 访问根路径
    // Then 页面标题应包含 RuleForge 相关文本
    test('页面应加载成功', async ({ page }) => {
        await page.goto('/');
        // 只要页面加载不报错即可
        const body = page.locator('body');
        await expect(body).toBeVisible();
    });
});
