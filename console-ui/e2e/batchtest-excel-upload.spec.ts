import {test, expect} from '@playwright/test';
import {login} from './helpers';

/**
 * BDD walkthrough — v5.8.4 BatchTest Excel upload (DatasourcePanel)
 *
 * Background:
 *   - console-ui 在 docker 里 nginx 跑在 80
 *   - 后端 console-app 在 docker network 里 8180,通过 /api/* 代理
 *   - 已有 datasource "E2E测试REST数据源" (id=2, REST_API 类型)
 *   - 已准备好 fixtures/batchtest-datasource-file.xlsx
 *     (3 行:entityId/fieldName/clazz,schema=DATASOURCE+FILE)
 *
 * Feature: V5.8.4 BatchTest Excel upload (DatasourcePanel)
 *   As a rule system administrator
 *   I want to start a batch test by uploading an .xlsx from the DatasourcePanel
 *   So that I can drive 1000+ row test runs without hand-typing entityIds
 */

// 截图保存到 e2e/screenshots/<step>.png(用 process.cwd() 拿到 e2e 目录)
const SCREENSHOT_DIR = `${process.cwd()}/e2e/screenshots`;
const FIXTURE = `${process.cwd()}/e2e/fixtures/batchtest-datasource-file.xlsx`;

async function shot(page, name) {
    await page.screenshot({path: `${SCREENSHOT_DIR}/${name}.png`, fullPage: true});
}

test.describe('V5.8.4 BatchTest Excel upload (DatasourcePanel)', () => {

    // 截图输出目录
    test.beforeAll(async () => {
        const fs = await import('node:fs/promises');
        await fs.mkdir(SCREENSHOT_DIR, {recursive: true});
    });

    test.beforeEach(async ({page}) => {
        await login(page);
        await page.goto('/app');
        await page.locator('.activity-bar-icon[title="数据源"]').click();
        // antd 化:面板是 PageShell + antd Tabs,不再是 bootstrap .nav-tabs
        await page.waitForSelector('.page-shell .ant-tabs-tab:has-text("数据源")');
        // 等 datasource 列表加载
        await page.waitForSelector('table tbody tr', {timeout: 10000});
    });

    // ── BDD STUB: DatasourcePanel 入口走真实 UI 流程 ─────────────────────
    // Given: 用户登录后已打开 DatasourcePanel
    //   And:  列表里至少有一行 datasource(用 REST_API 类型的 "E2E测试REST数据源")
    // When:  用户点击该行的 "批量测试" 按钮
    // Then:  弹出 mode-picker Modal,含 3 个 Radio:
    //          FLOW + DATASOURCE / DATASOURCE + FILE / 上传 Excel (v5.8.4)
    //   And:  默认选中 FLOW
    // When:  用户点 EXCEL 单选 → "下一步"
    // Then:  切换到 Excel upload Modal:
    //          模式 Radio (DATASOURCE+FILE 默认 / FLOW+DATASOURCE)
    //          Upload.Dragger 拖拽区
    //          主键字段名输入框(默认 entityId)
    //          决策流 ID 输入框(仅 FLOW 模式)
    //          "启动测试" 按钮
    test('should open the v5.8.4 mode picker and Excel upload modal', async ({page}) => {
        const targetRow = page.locator('table tbody tr').filter({hasText: 'E2E测试REST数据源'}).first();
        await expect(targetRow).toBeVisible();
        await shot(page, '01-datasource-panel');

        // When: click 批量测试 on the row
        await targetRow.locator('button.ant-btn:has-text("批量测试")').first().click();

        // Then: mode-picker Modal appears
        const modeModal = page.locator('.ant-modal-confirm');
        await expect(modeModal).toBeVisible({timeout: 10000});
        await expect(modeModal.locator('.ant-modal-title')).toContainText('批量测试');
        await page.waitForTimeout(400);  // 让 modal 入场动画完成
        await shot(page, '02-mode-picker-modal');

        // Then: 3 radios
        await expect(modeModal.locator('input[type="radio"][value="FLOW"]')).toBeAttached();
        await expect(modeModal.locator('input[type="radio"][value="DATASOURCE"]')).toBeAttached();
        await expect(modeModal.locator('input[type="radio"][value="EXCEL"]')).toBeAttached();

        // When: select EXCEL radio and click 下一步
        await modeModal.locator('input[type="radio"][value="EXCEL"]').click();
        // Antd 5 渲染中文带空格 → "下 一 步",用 regex 兼容
        await modeModal.getByRole('button', {name: /下\s*一\s*步/}).click();

        // Then: Excel upload Modal appears — wait for the Upload.Dragger (unique to the
        // new modal — the mode-picker has radios only, no .ant-upload-drag)
        const excelModal = page.locator('.ant-modal-confirm').filter({has: page.locator('.ant-upload-drag')});
        await expect(excelModal).toBeVisible({timeout: 10000});
        await expect(excelModal.locator('.ant-modal-title')).toContainText('上传 Excel');
        // Upload.Dragger should be visible
        await expect(excelModal.locator('.ant-upload-drag')).toBeVisible();
        // idField input default value
        await expect(excelModal.locator('input').first()).toBeVisible();
        // Give the modal-confirm animation time to settle before screenshot
        await page.waitForTimeout(500);
        await shot(page, '03-excel-upload-modal-empty');


        // Cancel out — this scenario only verifies the modal shell renders
        // (note: Antd 5 renders Chinese button text with a space "取 消" — match flexibly)
        await excelModal.getByRole('button', {name: /取\s*消/}).click();
    });

    // ── BDD STUB: 激活 DatasourcePanel 时右侧 welcome 页应隐藏 ──
    // Given: 用户登录后打开 frame 主页
    //   And:  默认 activePanel = 'rules',右侧 EditorTabs 在无打开标签时渲染欢迎页
    // When:  用户点击 ActivityBar 的"数据源"图标
    // Then:  右侧 EditorTabs 的"欢迎使用 RuleForge 决策平台"应不再可见
    //   And:  DatasourcePanel 应占满 content 区(1232px @ 1280 viewport)
    //   And:  DatasourcePanel 自身的 table/tabs 仍可见
    test('should hide the welcome page when a side panel is active', async ({page}) => {
        // Given: 默认页是 welcome(QuickStart.tsx 渲染 h2.welcome-title)
        // (EditorTabs 只在无打开标签时渲染 welcome,大多数情况都满足)
        const welcomeCount = await page.locator('h2.welcome-title').count();
        if (welcomeCount === 0) {
            // 极少数情况已有打开的编辑器标签,这种情况下已经没 welcome 可隐藏
            // — 跳过这个断言,只验证切换后 layout
            console.log('note: welcome not initially visible (editor tabs open)');
        } else {
            await expect(page.locator('h2.welcome-title')).toBeVisible({timeout: 5000});
        }

        // When: 切到 DatasourcePanel
        await page.locator('.activity-bar-icon[title="数据源"]').click();
        await page.waitForSelector('.page-shell .ant-tabs-tab:has-text("数据源")', {timeout: 10000});
        await page.waitForTimeout(500);  // 等 layout 切换

        // Then: welcome 页隐藏(无论之前是否可见)
        await expect(page.locator('h2.welcome-title')).not.toBeVisible();
        // And: DatasourcePanel 自身的 table + tabs 仍可见
        await expect(page.locator('table tbody tr').first()).toBeVisible();
        // And: DatasourcePanel 容器占满 content 区(>800,显著宽于旧 240 左侧)
        const panelWidth = await page.evaluate(() => {
            const shell = document.querySelector('.page-shell');
            return shell ? shell.getBoundingClientRect().width : null;
        });
        expect(panelWidth).toBeGreaterThan(800);  // 全宽,不是 240
    });



    // ── BDD STUB: 完整流程 — 上传 Excel,启动测试,BatchTestDialog 弹出 ────
    // Given: 用户已打开 DatasourcePanel 并点开 Excel upload Modal
    //   And:  选 DATASOURCE+FILE(默认)
    //   And:  fixtures/batchtest-datasource-file.xlsx(3 行,entityId/fieldName/clazz)
    // When:  用户拖拽 .xlsx 到 Upload.Dragger
    //   And:  点 "启动测试"
    // Then:  POST /api/batchtest/start-with-file 命中,返回 sessionId
    //   And:  BatchTestDialog 弹窗出现,显示 sessionId 和 RUNNING 状态
    //   And:  1-3 秒后 progress 从 0 增长(行执行)
    test('should upload .xlsx and start a batch test session', async ({page}) => {
        const targetRow = page.locator('table tbody tr').filter({hasText: 'E2E测试REST数据源'}).first();
        await targetRow.locator('button.ant-btn:has-text("批量测试")').first().click();

        // mode picker → EXCEL → 下一步
        const modeModal = page.locator('.ant-modal-confirm');
        await expect(modeModal).toBeVisible({timeout: 10000});
        await modeModal.locator('input[type="radio"][value="EXCEL"]').click();
        // Antd 5 渲染中文带空格 → "下 一 步",用 regex 兼容
        await modeModal.getByRole('button', {name: /下\s*一\s*步/}).click();

        // Excel upload modal — filter by unique Upload.Dragger
        const excelModal = page.locator('.ant-modal-confirm').filter({has: page.locator('.ant-upload-drag')});
        await expect(excelModal).toBeVisible({timeout: 10000});

        // Default mode is DATASOURCE+FILE — no flowId needed
        // setInputFiles fires the change event for Upload.Dragger
        const draggerInput = excelModal.locator('input[type="file"]');
        await draggerInput.setInputFiles(FIXTURE);
        await page.waitForTimeout(300);
        await shot(page, '04-excel-uploaded');

        // Capture the start-with-file response to assert sessionId shape
        const startWithFileResponse = page.waitForResponse(
            (resp) => resp.url().includes('/api/batchtest/start-with-file'),
            {timeout: 15000}
        );

        // When: click 启动测试
        await excelModal.getByRole('button', {name: /启\s*动\s*测\s*试/}).click();

        // Then: start-with-file returns 200 with sessionId
        const resp = await startWithFileResponse;
        expect(resp.status()).toBe(200);
        const body = await resp.json();
        expect(body.sessionId).toBeTruthy();
        expect(body.subjectType).toBe('DATASOURCE');
        expect(body.inputSourceType).toBe('FILE');

        // V7.7.2:老 BatchTestDialog 已删(OPEN_BATCH_TEST_DIALOG 事件无监听方),
        // 启动后不再有进度弹窗 — 本用例锁到 start-with-file API 层(sessionId 返回即成功)。
        // session 执行进度不再断言(REST_API 数据源在 docker network 里不可达)
    });
});
