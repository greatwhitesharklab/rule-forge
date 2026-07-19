import {test, expect} from '@playwright/test';
import {loginAndGotoFrame} from './helpers';

/**
 * BDD Tests for V5.15 User Management Panel (用户管理)
 *
 * Feature: User management UI
 *   As an admin
 *   I want to manage users and their permissions from the web console
 *   So that I can control access to projects and features
 */
test.describe('User Management Panel', () => {
    test.beforeEach(async ({page}) => {
        await loginAndGotoFrame(page);
        // Open user management panel via ActivityBar
        await page.locator('[title="用户管理"]').click();
        await page.waitForSelector('text=用户管理');
    });

    // ──────────────────────────────────────────────────
    // Scenario 1: Panel opens with user list
    // ──────────────────────────────────────────────────
    test('should open user management panel via ActivityBar', async ({page}) => {
        const header = page.locator('text=用户管理').first();
        await expect(header).toBeVisible();
        await expect(page.locator('.ant-table')).toBeVisible({timeout: 10000});
    });

    // ──────────────────────────────────────────────────
    // Scenario 2: Admin user is visible with role tag
    // ──────────────────────────────────────────────────
    test('should display admin user with role tag', async ({page}) => {
        await expect(page.locator('.ant-table')).toBeVisible({timeout: 10000});
        await expect(page.locator('.ant-table').locator('text=admin')).toBeVisible({timeout: 10000});
        await expect(page.locator('.ant-table').locator('text=管理员').first()).toBeVisible({timeout: 10000});
    });

    // ──────────────────────────────────────────────────
    // Scenario 3: Create user button opens modal
    // ──────────────────────────────────────────────────
    test('should open create user modal', async ({page}) => {
        await expect(page.locator('.ant-table')).toBeVisible({timeout: 10000});
        await page.locator('button:has-text("新增用户")').click();
        await expect(page.locator('.ant-modal')).toBeVisible({timeout: 5000});
        // Verify form fields exist
        await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
        await expect(page.getByPlaceholder('请输入密码')).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 4: Create user via API, verify it appears in table
    // ──────────────────────────────────────────────────
    test('should display newly created user in table', async ({page}) => {
        await expect(page.locator('.ant-table')).toBeVisible({timeout: 10000});

        // 用户名带时间戳 — 固定名 "e2e_api_user" 跑第二次会撞"已存在"失败(用例不幂等)
        const username = 'e2e_api_user_' + Date.now();

        // Create user via API (bypasses Antd Form complexity)
        await page.evaluate(async (username) => {
            const resp = await fetch('/api/permission/users', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: new URLSearchParams({
                    username,
                    password: 'test123',
                    isAdmin: 'false',
                    canExport: 'false',
                }).toString(),
            });
            const result = await resp.json();
            if (!result.status) throw new Error('Create user failed: ' + JSON.stringify(result));
        }, username);

        // Reload the panel by clicking away and back
        await page.locator('[title="规则编辑"]').click();
        await page.locator('[title="用户管理"]').click();
        await page.waitForSelector('text=用户管理');
        await expect(page.locator('.ant-table')).toBeVisible({timeout: 10000});

        // New user should appear in the table
        await expect(page.locator('.ant-table').locator(`text=${username}`)).toBeVisible({timeout: 10000});

        // Non-admin user should have "权限" button
        await expect(page.locator('.ant-table').locator('button:has-text("权限")').first()).toBeVisible({timeout: 5000});

        // Non-admin user should have "禁用" button
        await expect(page.locator('.ant-table').locator('button:has-text("禁用")').first()).toBeVisible({timeout: 5000});
    });

    // ──────────────────────────────────────────────────
    // Scenario 5: Permission drawer opens for non-admin user
    // ──────────────────────────────────────────────────
    test('should open permission drawer for non-admin user', async ({page}) => {
        await expect(page.locator('.ant-table')).toBeVisible({timeout: 10000});

        // Ensure a non-admin user exists via API
        const hasPermButton = await page.locator('.ant-table').locator('button:has-text("权限")').first().isVisible({timeout: 3000}).catch(() => false);
        if (!hasPermButton) {
            await page.evaluate(async () => {
                await fetch('/api/permission/users', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: new URLSearchParams({
                        username: 'e2e_perm_user',
                        password: 'test123',
                        isAdmin: 'false',
                        canExport: 'false',
                    }).toString(),
                });
            });
            // Reload panel
            await page.locator('[title="规则编辑"]').click();
            await page.locator('[title="用户管理"]').click();
            await page.waitForSelector('text=用户管理');
            await expect(page.locator('.ant-table')).toBeVisible({timeout: 10000});
        }

        // Click the first "权限" button
        await page.locator('.ant-table').locator('button:has-text("权限")').first().click();

        // Drawer should open with permission table
        await expect(page.locator('.ant-drawer')).toBeVisible({timeout: 5000});
        await expect(page.locator('.ant-drawer').locator('text=权限配置')).toBeVisible({timeout: 5000});
    });
});
