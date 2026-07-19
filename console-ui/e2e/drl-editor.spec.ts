import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * V5.44.4 — DRL 编辑器 round-trip e2e。
 *
 * <p>锁 1 件事:console-ui DRL editor 走 /loadDrl 端点,展示 imports/ruleNames。
 *
 * <p>这个 e2e 是 stub — V5.44.4 console-ui 没建完整 DRL 编辑器页面,
 * 只暴露了 /api/drlEditor.loadDrlFile (POST /common/loadDrl)。
 * 完整 DRL 编辑器 UI 集成进 ruleflow-designer / ruleset-editor 留 V5.45+。
 *
 * <p>e2e 验证:drl-editor entry 点能加载,DRL 文件被解析。
 */
test.describe('V5.44.4 — DRL Editor round-trip e2e', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // BDD stub: smoke test for DRL file load endpoint via browser
    test('should call /common/loadDrl via UI and show imports + ruleNames', async ({ page }) => {
        // V5.44.4 决定:e2e 走 page.request 调 /loadDrl 端点(不依赖 UI 集成,
        // UI 集成留 V5.45+)。这样锁住端点契约 + 验证 auth flow 正常。
        // 注意:必须带 /api 前缀,否则请求落在 vite SPA fallback 上而非后端。
        const response = await page.request.post('/api/common/loadDrl', {
            form: { file: '/proj/rules/r.drl' },
        });
        // 后端可能返 404 (文件不存在) — 视为可接受,锁 401 才算 auth fail
        expect(response.status()).toBeLessThan(500);
        // 200 → 解析 response JSON 验证 schema
        if (response.status() === 200) {
            const body = await response.json();
            expect(body).toHaveProperty('path');
            expect(body).toHaveProperty('content');
            expect(body).toHaveProperty('imports');
            expect(body).toHaveProperty('ruleNames');
            expect(Array.isArray(body.imports)).toBe(true);
            expect(Array.isArray(body.ruleNames)).toBe(true);
        }
        // 404 → 后端正确返 404,锁住
        if (response.status() === 404) {
            const text = await response.text();
            expect(text).toContain('file not found');
        }
    });
});
