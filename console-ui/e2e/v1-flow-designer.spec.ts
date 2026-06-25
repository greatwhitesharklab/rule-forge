import {test, expect, type Page} from '@playwright/test';

/**
 * V7.0.0 W3-6 — V1 决策流设计器 Playwright 套件(正式 e2e)。
 *
 * <p>纯客户端画布(/v1-flow demo 路由,不调后端),覆盖核心契约:
 * <ul>
 *   <li>palette 加 5 节点 → 画布渲染</li>
 *   <li>点节点 → antd Drawer 开 → 按类型编辑器</li>
 *   <li>RuleSet 加规则 → 可视化/CEL 双模式</li>
 *   <li>导出 → RuleAsset JSON(version/flowElements/nodes,后端 V1FlowRunner 可执行)</li>
 * </ul>
 *
 * <p>跑:`PLAYWRIGHT_BASE_URL=http://localhost:5173 npx playwright test e2e/v1-flow-designer.spec.ts --project=chromium`
 */

async function addNode(page: Page, name: string) {
    await page.getByRole('menuitem', {name, exact: true}).click();
}

async function exportJson(page: Page): Promise<any> {
    await page.evaluate(() => {
        const b = Array.from(document.querySelectorAll('button')).find((x) => x.textContent?.includes('导出'));
        (b as HTMLButtonElement)?.click();
    });
    await expect(page.locator('[data-testid="v1-export"]')).toBeVisible();
    const text = await page.locator('[data-testid="v1-export"]').textContent();
    return JSON.parse(text || '{}');
}

test.describe('V1 决策流设计器', () => {
    test.beforeEach(async ({page}) => {
        await page.goto('/v1-flow');
        await expect(page.getByText('RuleForge · V1 决策流设计器')).toBeVisible();
    });

    test('palette 加 5 节点 → 画布渲染 + 导出 RuleAsset JSON', async ({page}) => {
        // Given palette When 加 Start/RuleSet/DecisionTable/ScoreCard/Decision
        await addNode(page, '+ Start');
        await addNode(page, '+ RuleSet');
        await addNode(page, '+ DecisionTable');
        await addNode(page, '+ ScoreCard');
        await addNode(page, '+ Decision');

        // Then 5 节点在画布
        await expect(page.locator('[data-testid="v1-node-Start"]')).toBeVisible();
        await expect(page.locator('[data-testid="v1-node-RuleSet"]')).toBeVisible();
        await expect(page.locator('[data-testid="v1-node-Decision"]')).toBeVisible();

        // And 导出 RuleAsset JSON 结构正确(后端可执行)
        const asset = await exportJson(page);
        expect(asset.version).toBe('1.0');
        const events = asset.flow.flowElements.filter((e: any) => e.type !== 'sequenceFlow');
        expect(events).toHaveLength(5);
        // BPMN 元素类型映射
        const types = events.map((e: any) => e.type);
        expect(types).toContain('startEvent');
        expect(types).toContain('endEvent');
        expect(types.filter((t: string) => t === 'serviceTask')).toHaveLength(3);
        // nodes Map 5 个,按 id 引用
        expect(Object.keys(asset.nodes)).toHaveLength(5);
        expect(asset.nodes['start_1']?.type).toBe('Start');
        expect(asset.nodes['decision_5']?.type).toBe('Decision');
    });

    test('点 RuleSet 节点 → Drawer 开 + 加规则 + 可视化/CEL 模式', async ({page}) => {
        await addNode(page, '+ RuleSet');
        await page.locator('[data-testid="v1-node-RuleSet"]').click();

        // Drawer 开,RuleSet 编辑器渲染
        await expect(page.getByText('命中策略')).toBeVisible();
        await expect(page.getByRole('button', {name: '添加规则'})).toBeVisible();

        // 加规则 → condition 编辑器(可视化/CEL 双模式 Segmented)+ actions
        await page.getByRole('button', {name: '添加规则'}).click();
        // Segmented(可视化/CEL)+ QueryBuilder 渲染
        await expect(page.locator('.ant-segmented').first()).toBeVisible();
        await expect(page.locator('.queryBuilder, .v1-rqb').first()).toBeVisible();

        // 导出 JSON 含规则结构
        const asset = await exportJson(page);
        const rs = asset.nodes['ruleset_1'];
        expect(rs.type).toBe('RuleSet');
        expect(rs.hitPolicy).toBe('FIRST_MATCH');
        expect(rs.rules).toHaveLength(1);
        expect(rs.rules[0].condition).toBe(''); // RQB 默认空
        expect(rs.rules[0].actions).toEqual([]);
    });

    test('RuleSet 改命中策略 → 导出反映', async ({page}) => {
        await addNode(page, '+ RuleSet');
        await page.locator('[data-testid="v1-node-RuleSet"]').click();
        // 改命中策略 FIRST_MATCH → ALL_MATCH
        await page.locator('.ant-select').first().click();
        await page.getByText('ALL_MATCH(全命中)').click();
        const asset = await exportJson(page);
        expect(asset.nodes['ruleset_1'].hitPolicy).toBe('ALL_MATCH');
    });

    test('导入 RuleAsset JSON → 画布渲染节点(client-side round-trip)', async ({page}) => {
        const asset = {
            version: '1.0', id: 'loan', name: 'Loan',
            flow: {id: 'f', name: 'F', version: '1.0', flowElements: [
                {type: 'startEvent', id: 'start', name: '开始', implementation: 'Start:start', position: {x: 50, y: 50}},
                {type: 'serviceTask', id: 'pre', name: '准入', implementation: 'RuleSet:pre', position: {x: 50, y: 180}},
                {type: 'endEvent', id: 'end', name: '决策', implementation: 'Decision:end', position: {x: 50, y: 310}},
                {type: 'sequenceFlow', id: 'e1', sourceRef: 'start', targetRef: 'pre'},
                {type: 'sequenceFlow', id: 'e2', sourceRef: 'pre', targetRef: 'end'},
            ]},
            nodes: {
                start: {id: 'start', type: 'Start', name: '开始', schema: 'LoanApplication'},
                pre: {id: 'pre', type: 'RuleSet', name: '准入', hitPolicy: 'FIRST_MATCH', rules: []},
                end: {id: 'end', type: 'Decision', name: '决策', outputs: ['approve', 'reject']},
            },
        };
        await page.getByRole('button', {name: /导入/}).first().click();
        await page.locator('[data-testid="v1-import-text"]').fill(JSON.stringify(asset));
        await page.getByRole('button', {name: '导 入'}).click();
        // 3 节点出现在画布(implementation 权威路由:startEvent→Start, serviceTask RuleSet:→RuleSet, endEvent→Decision)
        await expect(page.locator('[data-testid="v1-node-Start"]')).toBeVisible();
        await expect(page.locator('[data-testid="v1-node-RuleSet"]')).toBeVisible();
        await expect(page.locator('[data-testid="v1-node-Decision"]')).toBeVisible();
    });
});
