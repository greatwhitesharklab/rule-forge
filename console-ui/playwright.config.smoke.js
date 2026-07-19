// V5.50.5 — PR smoke subset config (JavaScript, 跟现有 playwright.config.js 一致)。
//
// 5 spec 子集(覆盖 nav / auth / datasource / package):
// - app
// - login
// - frame-navigation
// - datasource-panel
// - package-editor
// (decision-table-editor / rule-editor 随 V7.0 老编辑器删除下线)
//
// 单 browser(chromium),60s timeout,5 spec 期望 2-3min 跑完。
// 失败 info-only,不挡 merge — V5.50.5 设计。

import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: './e2e',
    timeout: 60_000,
    retries: 0,
    use: {
        baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
        headless: true,
        screenshot: 'only-on-failure',
        storageState: undefined,
    },
    testMatch: [
        '**/app.spec.ts',
        '**/login.spec.ts',
        '**/frame-navigation.spec.ts',
        '**/datasource-panel.spec.ts',
        '**/package-editor.spec.ts',
    ],
    projects: [
        {
            name: 'chromium',
            use: { browserName: 'chromium' },
        },
    ],
});
