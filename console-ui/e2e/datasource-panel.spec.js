import {test, expect} from '@playwright/test';
import {login} from './helpers.js';

/**
 * BDD Tests for Datasource Panel (数据源管理)
 *
 * Feature: Upstream datasource management UI
 *   As a rule system administrator
 *   I want to manage external datasources and field mappings via the web console
 *   So that rules can fetch external data at runtime
 */

/** Helper: open the datasource panel via Activity Bar */
async function openDatasourcePanel(page) {
    await page.locator('.activity-bar-icon[title="数据源"]').click();
    // Wait for the panel's nav-tabs to render (Redux dispatch + fetch)
    await page.waitForSelector('.nav-tabs:has-text("数据源")');
}

/** Helper: get the datasource panel container (scoped to avoid collisions with other panels) */
function panel(page) {
    // The panel root is <div style="padding:15px..."> containing the .nav-tabs with "数据源"
    return page.locator('.nav-tabs:has-text("数据源")').locator('..');
}

/** Helper: create a datasource via UI and return its row */
async function createDatasourceViaUI(page, {name, type, configFields = {}}) {
    const p = panel(page);

    // Click add button
    await p.locator('button:has-text("新增数据源")').click();
    const form = p.locator('.panel');
    await expect(form.locator('.panel-heading')).toContainText('新增数据源');

    // Fill name — target the first visible input inside the form body
    await form.locator('.panel-body input.form-control').first().fill(name);

    // Select type if not default REST_API
    if (type && type !== 'REST_API') {
        await form.locator('.panel-body select').selectOption(type);
    }

    // Fill config fields (label → value)
    for (const [label, value] of Object.entries(configFields)) {
        const group = form.locator('.form-group').filter({hasText: new RegExp('^' + label)});
        await group.locator('input').fill(value);
    }

    // Save — scoped to the form panel
    await form.locator('button:has-text("保存")').click();
    // Wait for list to reload
    await page.waitForTimeout(500);

    // Return the row containing the new datasource name
    return p.locator('table tbody tr').filter({hasText: name}).first();
}

test.describe('Datasource Panel', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
        await page.goto('/index.html');
        await openDatasourcePanel(page);
    });

    // ──────────────────────────────────────────────────
    // Scenario 1: Panel loads with default "数据源" tab
    // ──────────────────────────────────────────────────
    // Given: User has opened the datasource panel
    // When: The panel renders
    // Then: Two tabs should be visible — "数据源" (active) and "映射配置"
    // And: A "新增数据源" button should be visible
    // And: A table with headers should exist
    test('should load datasource panel with tabs and table headers', async ({page}) => {
        const p = panel(page);

        // Then: Two tabs are visible
        const tabs = p.locator('.nav-tabs li');
        await expect(tabs).toHaveCount(2);

        // Then: First tab "数据源" is active
        await expect(tabs.nth(0)).toHaveClass(/active/);
        await expect(tabs.nth(0)).toContainText('数据源');
        await expect(tabs.nth(1)).toContainText('映射配置');

        // Then: "新增数据源" button is visible
        await expect(p.locator('button:has-text("新增数据源")')).toBeVisible();

        // Then: Table headers are present
        const headers = p.locator('table thead th');
        const headerTexts = await headers.allTextContents();
        expect(headerTexts).toEqual(
            expect.arrayContaining(['名称', '类型', '状态', '描述', '超时(ms)', '缓存', '操作'])
        );
    });

    // ──────────────────────────────────────────────────
    // Scenario 2: Create a REST API datasource
    // ──────────────────────────────────────────────────
    // Given: User is on the "数据源" tab
    // When: User clicks "新增数据源" button
    // Then: A form panel should appear with fields: 名称, 类型, 描述
    // When: User fills in name, selects type "REST API", fills base URL + endpoint
    // And: Clicks "保存"
    // Then: Form should close and the new datasource should appear in the table
    test('should create a REST API datasource and show it in the list', async ({page}) => {
        const row = await createDatasourceViaUI(page, {
            name: 'E2E测试REST数据源',
            type: 'REST_API',
            configFields: {'Base URL': 'https://api.example.com', 'Endpoint': '/v1/data'}
        });

        // Then: Row should be visible in the table
        await expect(row).toBeVisible();
        await expect(row.locator('td').nth(0)).toContainText('E2E测试REST数据源');
        await expect(row.locator('td').nth(1)).toContainText('REST_API');
    });

    // ──────────────────────────────────────────────────
    // Scenario 3: Create an Advance AI datasource
    // ──────────────────────────────────────────────────
    // Given: User is on the "数据源" tab and clicked "新增数据源"
    // When: User selects type "Advance AI"
    // Then: Form shows Advance AI-specific fields: Base URL, Access Key, Secret Key
    // When: User fills fields and saves
    // Then: New datasource with type "ADVANCE_AI" appears in the table
    test('should create an Advance AI datasource with type-specific config fields', async ({page}) => {
        const row = await createDatasourceViaUI(page, {
            name: 'E2E测试AdvanceAI数据源',
            type: 'ADVANCE_AI',
            configFields: {
                'Base URL': 'https://api.advance-ai.com',
                'Access Key': 'test-access-key',
                'Secret Key': 'test-secret-key'
            }
        });

        // Then: Row appears with ADVANCE_AI type
        await expect(row).toBeVisible();
        await expect(row.locator('td').nth(1)).toContainText('ADVANCE_AI');
    });

    // ──────────────────────────────────────────────────
    // Scenario 4: Create a JDBC datasource
    // ──────────────────────────────────────────────────
    // Given: User is on the "数据源" tab and clicked "新增数据源"
    // When: User selects type "JDBC"
    // Then: Form shows JDBC-specific fields: JDBC URL, 用户名, 密码, 查询模板
    // When: User fills fields and saves
    // Then: New datasource with type "JDBC" appears in the table
    test('should create a JDBC datasource with connection config fields', async ({page}) => {
        const row = await createDatasourceViaUI(page, {
            name: 'E2E测试JDBC数据源',
            type: 'JDBC',
            configFields: {
                'JDBC URL': 'jdbc:mysql://localhost:3306/test',
                '用户名': 'root',
                '密码': 'password',
                '查询模板': 'SELECT * FROM users WHERE id = ${entityId}'
            }
        });

        // Then: Row appears with JDBC type
        await expect(row).toBeVisible();
        await expect(row.locator('td').nth(1)).toContainText('JDBC');
    });

    // ──────────────────────────────────────────────────
    // Scenario 5: Edit an existing datasource
    // ──────────────────────────────────────────────────
    // Given: A datasource exists in the table
    // When: User clicks the edit (pencil) button on that row
    // Then: The form panel should open pre-filled with the datasource's data
    // When: User changes the name and clicks "保存"
    // Then: Form should close and the updated name should appear in the table
    test('should edit an existing datasource and reflect changes', async ({page}) => {
        const p = panel(page);

        // Given: Create a datasource first
        await createDatasourceViaUI(page, {name: 'E2E待编辑数据源'});

        // When: Click edit button (pencil icon) on that row
        const row = p.locator('table tbody tr').filter({hasText: 'E2E待编辑数据源'}).first();
        await row.locator('.glyphicon-edit').first().click();

        // Then: Form opens with "编辑数据源" heading
        const form = p.locator('.panel');
        await expect(form.locator('.panel-heading')).toContainText('编辑数据源');

        // Then: Name field is pre-filled
        const nameInput = form.locator('.panel-body input.form-control').first();
        await expect(nameInput).toHaveValue('E2E待编辑数据源');

        // When: Change name
        await nameInput.clear();
        await nameInput.fill('E2E已编辑数据源');
        await form.locator('button:has-text("保存")').click();
        await page.waitForTimeout(500);

        // Then: Updated name appears in the table
        const updatedRow = p.locator('table tbody tr').filter({hasText: 'E2E已编辑数据源'}).first();
        await expect(updatedRow).toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 6: Delete a datasource
    // ──────────────────────────────────────────────────
    // Given: A datasource exists in the table
    // When: User clicks the delete (trash) button on that row
    // Then: A confirmation dialog should appear with text "确定删除此数据源？"
    // When: User accepts the dialog
    // Then: The datasource should be removed from the table
    test('should delete a datasource after confirmation', async ({page}) => {
        const p = panel(page);

        // Given: Create a datasource first
        await createDatasourceViaUI(page, {name: 'E2E待删除数据源'});

        // When: Click delete button and accept confirmation
        const row = p.locator('table tbody tr').filter({hasText: 'E2E待删除数据源'}).first();
        page.on('dialog', async dialog => {
            expect(dialog.message()).toContain('确定删除此数据源');
            await dialog.accept();
        });
        await row.locator('.glyphicon-trash').first().click();

        // Then: Row should disappear
        await page.waitForTimeout(500);
        await expect(p.locator('table tbody tr').filter({hasText: 'E2E待删除数据源'})).toHaveCount(0);
    });

    // ──────────────────────────────────────────────────
    // Scenario 7: Test connection
    // ──────────────────────────────────────────────────
    // Given: A datasource exists in the table
    // When: User clicks the "测试" button on that row
    // Then: An alert banner should appear showing test result
    // And: The alert should auto-dismiss after ~3 seconds
    test('should test connection and show result banner', async ({page}) => {
        const p = panel(page);

        // Given: Create a datasource
        await createDatasourceViaUI(page, {
            name: 'E2E测试连接数据源',
            type: 'REST_API',
            configFields: {'Base URL': 'https://httpbin.org', 'Endpoint': '/get'}
        });

        // When: Click test button
        const row = p.locator('table tbody tr').filter({hasText: 'E2E测试连接数据源'}).first();
        await row.locator('button:has-text("测试")').click();

        // Then: Alert banner appears with result
        const alert = p.locator('.alert');
        await expect(alert).toBeVisible({timeout: 10000});
        // Wait for "测试中..." to be replaced by actual result (may take time for external API)
        await expect(alert).toContainText(/连接成功|连接失败/, {timeout: 20000});

        // And: Alert auto-dismisses after ~3 seconds
        await expect(alert).not.toBeVisible({timeout: 5000});
    });

    // ──────────────────────────────────────────────────
    // Scenario 8: Switch to "映射配置" tab
    // ──────────────────────────────────────────────────
    // Given: User is on the datasource panel
    // When: User clicks the "映射配置" tab
    // Then: Entity mapping section should be visible with input, dropdown, button, and table
    test('should switch to mapping tab and show entity mapping UI', async ({page}) => {
        const p = panel(page);

        // When: Click "映射配置" tab
        await p.locator('.nav-tabs li').nth(1).locator('a').click();

        // Then: Second tab becomes active
        await expect(p.locator('.nav-tabs li').nth(1)).toHaveClass(/active/);

        // Then: Heading visible
        await expect(p.locator('h5')).toContainText('实体类');

        // Then: Input for clazz
        await expect(p.locator('input[placeholder="实体类名 (clazz)"]')).toBeVisible();

        // Then: "保存映射" button
        await expect(p.locator('button:has-text("保存映射")')).toBeVisible();

        // Then: Table headers for mapping
        const mappingHeaders = p.locator('table').last().locator('thead th');
        const mappingHeaderTexts = await mappingHeaders.allTextContents();
        expect(mappingHeaderTexts).toEqual(
            expect.arrayContaining(['实体类 (clazz)', '数据源', '操作'])
        );
    });

    // ──────────────────────────────────────────────────
    // Scenario 9: Save entity mapping
    // ──────────────────────────────────────────────────
    // Given: User is on "映射配置" tab and at least one datasource exists
    // When: User types a clazz name and selects a datasource from dropdown
    // And: Clicks "保存映射"
    // Then: The new mapping should appear in the entity mapping table
    test('should save an entity mapping and display it in the table', async ({page}) => {
        const p = panel(page);

        // Given: Create a datasource first
        await createDatasourceViaUI(page, {name: 'E2E映射数据源'});
        const dsRow = p.locator('table tbody tr').filter({hasText: 'E2E映射数据源'}).first();
        await expect(dsRow).toBeVisible();

        // When: Switch to mapping tab
        await p.locator('.nav-tabs li').nth(1).locator('a').click();

        // When: Fill clazz name
        await p.locator('input[placeholder="实体类名 (clazz)"]').fill('com.example.LoanEntity');

        // When: Select datasource from dropdown (the select inside mapping section)
        await p.locator('select:has(option:has-text("E2E映射数据源"))').selectOption({label: 'E2E映射数据源'});

        // When: Click save
        await p.locator('button:has-text("保存映射")').click();
        await page.waitForTimeout(500);

        // Then: New mapping row appears
        const mappingTable = p.locator('table').last();
        const mappingRow = mappingTable.locator('tbody tr').filter({hasText: 'com.example.LoanEntity'}).first();
        await expect(mappingRow).toBeVisible();
        await expect(mappingRow).toContainText('E2E映射数据源');
    });

    // ──────────────────────────────────────────────────
    // Scenario 10: View field mappings for an entity
    // ──────────────────────────────────────────────────
    // Given: An entity mapping exists in the table
    // When: User clicks the "字段映射" button on that row
    // Then: Field mapping table should appear with headers (规则变量名, 外部字段名)
    // And: Title should show the clazz name
    test('should load and display field mappings for an entity', async ({page}) => {
        const p = panel(page);

        // Given: Create datasource + entity mapping
        await createDatasourceViaUI(page, {name: 'E2E字段映射数据源'});
        await p.locator('.nav-tabs li').nth(1).locator('a').click();

        // Wait for mapping tab to render and datasource options to load
        await page.waitForTimeout(500);
        await p.locator('input[placeholder="实体类名 (clazz)"]').fill('com.example.FieldTestEntity');

        // Select the datasource option — wait for it to appear in the dropdown
        const dsSelect = p.locator('select').last();
        await expect(dsSelect.locator('option:has-text("E2E字段映射数据源")')).toBeAttached({timeout: 5000});
        await dsSelect.selectOption({label: 'E2E字段映射数据源'});
        await p.locator('button:has-text("保存映射")').click();

        // Wait for entity mapping to appear
        const mappingTable = p.locator('table').last();
        await expect(mappingTable.locator('tbody')).not.toBeEmpty({timeout: 5000});

        // Given: Insert field mappings via API so the component renders the section
        const dsId = await page.evaluate(async () => {
            const resp = await fetch(window._server + '/datasource');
            const list = await resp.json();
            const ds = list.find(d => d.name === 'E2E字段映射数据源');
            return ds ? ds.id : null;
        });
        await page.evaluate(async (dsId) => {
            await fetch(window._server + '/datasource/' + dsId + '/field-mappings', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    clazz: 'com.example.FieldTestEntity',
                    mappings: [
                        {variableName: 'creditScore', remoteField: 'score'},
                        {variableName: 'riskLevel', remoteField: 'risk_level'}
                    ]
                })
            });
        }, dsId);

        // When: Click "字段映射" button
        const mappingRow = mappingTable.locator('tbody tr').filter({hasText: 'com.example.FieldTestEntity'}).first();
        await expect(mappingRow).toBeVisible({timeout: 5000});
        await mappingRow.locator('button:has-text("字段映射")').click();
        await page.waitForTimeout(500);

        // Then: Field mapping section appears with clazz in title
        await expect(p.locator('h5:has-text("字段映射")')).toContainText('com.example.FieldTestEntity');

        // Then: Field mapping table headers exist
        const fieldHeaders = p.locator('table').last().locator('thead th');
        const fieldHeaderTexts = await fieldHeaders.allTextContents();
        expect(fieldHeaderTexts).toEqual(
            expect.arrayContaining(['规则变量名', '外部字段名'])
        );
    });

    // ──────────────────────────────────────────────────
    // Scenario 11: Form type switch changes config fields
    // ──────────────────────────────────────────────────
    // Given: User has opened the "新增数据源" form
    // When: User selects type "REST API" → Base URL + Endpoint
    // When: User switches to "Advance AI" → Base URL + Access Key + Secret Key
    // When: User switches to "JDBC" → JDBC URL + 用户名 + 密码 + 查询模板
    test('should dynamically switch config fields when type changes', async ({page}) => {
        const p = panel(page);

        // Given: Open the form
        await p.locator('button:has-text("新增数据源")').click();
        const form = p.locator('.panel');
        await expect(form.locator('.panel-heading')).toContainText('新增数据源');

        const formBody = form.locator('.panel-body');

        // When: Default is REST_API — Base URL + Endpoint visible
        await expect(formBody.locator('.form-group').filter({hasText: /^Endpoint/})).toBeVisible();

        // When: Switch to Advance AI
        await formBody.locator('select').first().selectOption('ADVANCE_AI');
        // Then: Access Key and Secret Key appear, Endpoint disappears
        await expect(formBody.locator('.form-group').filter({hasText: /^Access Key/})).toBeVisible();
        await expect(formBody.locator('.form-group').filter({hasText: /^Secret Key/})).toBeVisible();
        await expect(formBody.locator('.form-group').filter({hasText: /^Endpoint/})).not.toBeVisible();

        // When: Switch to JDBC
        await formBody.locator('select').first().selectOption('JDBC');
        // Then: JDBC-specific fields appear
        await expect(formBody.locator('.form-group').filter({hasText: /^JDBC URL/})).toBeVisible();
        await expect(formBody.locator('.form-group').filter({hasText: /^用户名/})).toBeVisible();
        await expect(formBody.locator('.form-group').filter({hasText: /^密码/})).toBeVisible();
        await expect(formBody.locator('.form-group').filter({hasText: /^查询模板/})).toBeVisible();
        // And: REST/AdvanceAI fields disappear
        await expect(formBody.locator('.form-group').filter({hasText: /^Endpoint/})).not.toBeVisible();
        await expect(formBody.locator('.form-group').filter({hasText: /^Access Key/})).not.toBeVisible();
    });

    // ──────────────────────────────────────────────────
    // Scenario 12: Cancel form dismisses it
    // ──────────────────────────────────────────────────
    // Given: User has opened the "新增数据源" form
    // When: User clicks "取消"
    // Then: Form panel should close
    // And: Table should still be visible without new entries
    test('should dismiss form on cancel', async ({page}) => {
        const p = panel(page);

        // Given: Open the form
        await p.locator('button:has-text("新增数据源")').click();
        const form = p.locator('.panel');
        await expect(form.locator('.panel-heading')).toContainText('新增数据源');

        // When: Click cancel
        await form.locator('button:has-text("取消")').click();

        // Then: Form panel is gone
        await expect(form).not.toBeVisible();

        // Then: Datasource table is still visible (first table in the panel)
        await expect(p.locator('table').first()).toBeVisible();
    });
});
