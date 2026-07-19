import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: './e2e',
    // 30s default 跑 16 个重型集成测试(datasource 创建、flow 流程、agent panel)会超时,
    // 这些测试要做 login + 复杂 UI 操作 + 等后端响应。提到 60s 给它们空间。
    timeout: 60_000,
    // 全量并行时跨 spec 共享后端状态(项目列表/数据源),个别用例(V-02 建项目下拉)
    // 会随机撞上状态污染而超时 — 单跑必过。retries=1 吸收这类并行 flake,
    // 真 bug 仍会红(重试也过不了)。
    retries: 1,
    use: {
        // 默认打 vite preview(构建产物,见下方 webServer;2026-07 起由 dev server 改过来:
        // dev server 按需编译在 10 workers 并发下偶发 >10s,导致 .app-layout 加载类用例随机超时;
        // preview 静态产物快 ~7 倍且跟 docker nginx 生产形态一致)。
        // 显式覆盖:PLAYWRIGHT_BASE_URL=http://localhost:5173 npx playwright test ...(打 dev server)
        // 或 PLAYWRIGHT_BASE_URL=http://localhost ...(打 docker 全栈 nginx)。
        baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:4173',
        headless: true,
        screenshot: 'only-on-failure',
        storageState: undefined,
    },
    // 未显式指定 PLAYWRIGHT_BASE_URL 时自动起 vite preview(需先 npm run build;
    // 已在跑的 preview 直接复用)。webkit 项目需要 Ubuntu 系 install-deps,
    // 非 Debian 系(如 Manjaro)本地可 --project=chromium --project=firefox。
    // 注意:全量必须按浏览器串行跑(npm run test:e2e 已固化)——两个浏览器的用例
    // 打同一个后端 + 同一个 DB,并行交叉跑会互相污染状态(项目/数据源/用户),
    // 随机挂 2~11 条(实测);单浏览器全量两轮均全绿。
    webServer: process.env.PLAYWRIGHT_BASE_URL ? undefined : {
        command: 'npx vite preview --port 4173',
        url: 'http://localhost:4173',
        reuseExistingServer: true,
        timeout: 30_000,
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
