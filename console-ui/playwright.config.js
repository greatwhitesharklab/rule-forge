import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: './e2e',
    // 30s default 跑 16 个重型集成测试(datasource 创建、flow 流程、agent panel)会超时,
    // 这些测试要做 login + 复杂 UI 操作 + 等后端响应。提到 60s 给它们空间。
    timeout: 60_000,
    retries: 0,
    use: {
        // 默认 vite dev server (3000);走 docker stack 测试时用
        //   PLAYWRIGHT_BASE_URL=http://localhost npx playwright test ...
        // 覆盖到 80(console-ui nginx)。
        baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
        headless: true,
        screenshot: 'only-on-failure',
        storageState: undefined,
    },
    projects: [
        {
            name: 'chromium',
            use: { browserName: 'chromium' },
        },
        {
            name: 'firefox',
            use: { browserName: 'firefox' },
        },
        {
            name: 'webkit',
            use: { browserName: 'webkit' },
        },
    ],
});
