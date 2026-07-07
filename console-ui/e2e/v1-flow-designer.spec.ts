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
        // 改命中策略 FIRST_MATCH → ALL_MATCH(用 testid 精确定位 Drawer 内命中策略 Select;
        // 不能用 .ant-select.first() —— Header 的 Schema AutoComplete 也是 .ant-select)
        await page.locator('[data-testid="v1-hit-policy"]').click();
        // antd Select 下拉项用 title 属性定位(比 getByRole option 稳定:
        // antd 内部存在两套 option DOM,role=option 的 accessible name 计算不一致)
        await page.locator('.ant-select-item-option[title="ALL_MATCH(全命中)"]').click();
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

    test('Gateway 导入 → 画布渲染 + GatewayEditor 出边条件编辑 + 导出 round-trip', async ({page}) => {
        const asset = {
            version: '1.0', id: 'gw_e2e', name: '网关流',
            flow: {id: 'gf', name: 'GF', version: '1.0', flowElements: [
                {type: 'startEvent', id: 'start', name: '开始', implementation: 'Start:start', position: {x: 50, y: 200}},
                {type: 'exclusiveGateway', id: 'gw', name: '网关', defaultFlow: 'f_low', position: {x: 250, y: 200}},
                {type: 'serviceTask', id: 'approve', name: '通过', implementation: 'RuleSet:approve', position: {x: 450, y: 80}},
                {type: 'serviceTask', id: 'reject', name: '拒绝', implementation: 'RuleSet:reject', position: {x: 450, y: 320}},
                {type: 'endEvent', id: 'end', name: '决策', implementation: 'Decision:end', position: {x: 680, y: 200}},
                {type: 'sequenceFlow', id: 'f1', sourceRef: 'start', targetRef: 'gw'},
                {type: 'sequenceFlow', id: 'f_high', sourceRef: 'gw', targetRef: 'approve', conditionExpression: 'score >= 50'},
                {type: 'sequenceFlow', id: 'f_low', sourceRef: 'gw', targetRef: 'reject'},
                {type: 'sequenceFlow', id: 'f2', sourceRef: 'approve', targetRef: 'end'},
                {type: 'sequenceFlow', id: 'f3', sourceRef: 'reject', targetRef: 'end'},
            ]},
            nodes: {
                start: {id: 'start', type: 'Start', name: '开始', schema: 'LoanApplication'},
                approve: {id: 'approve', type: 'RuleSet', name: '通过', hitPolicy: 'FIRST_MATCH', rules: []},
                reject: {id: 'reject', type: 'RuleSet', name: '拒绝', hitPolicy: 'FIRST_MATCH', rules: []},
                end: {id: 'end', type: 'Decision', name: '决策', outputs: ['approve', 'reject']},
            },
        };
        await page.getByRole('button', {name: /导入/}).first().click();
        await page.locator('[data-testid="v1-import-text"]').fill(JSON.stringify(asset));
        await page.getByRole('button', {name: '导 入'}).click();

        // Gateway 渲染在画布(exclusiveGateway → nodeType=Gateway)
        await expect(page.locator('[data-testid="v1-node-Gateway"]')).toBeVisible();

        // 点 Gateway → GatewayEditor 开,出边条件显示
        await page.locator('[data-testid="v1-node-Gateway"]').click();
        await expect(page.getByText('出边分流条件')).toBeVisible();
        await expect(page.locator('[data-testid="gw-cond-f_high"]')).toHaveValue('score >= 50');

        // 改条件
        await page.locator('[data-testid="gw-cond-f_high"]').fill('score >= 60');
        await page.keyboard.press('Escape'); // 关 GatewayEditor

        // 导出 → condition 反映改动 + defaultFlow 保留(Gateway 不进 nodes{})
        const exported = await exportJson(page);
        const gw = exported.flow.flowElements.find((e: any) => e.type === 'exclusiveGateway');
        expect(gw.defaultFlow).toBe('f_low');
        const fHigh = exported.flow.flowElements.find((e: any) => e.id === 'f_high');
        expect(fHigh.conditionExpression).toBe('score >= 60');
        expect(exported.nodes['gw']).toBeUndefined(); // Gateway 是 flow element 不进 nodes
    });
});
