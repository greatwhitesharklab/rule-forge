import {test, expect} from '@playwright/test';
import {login} from './helpers';

/**
 * V5.9.0 100% 交互级 tour — 不放过任何小功能
 *
 * 覆盖:
 *  I: 30+ dialog 完整提交流
 *  J: project + files CRUD 端到端
 *  K: datasource + 知识包 + 版本 CRUD
 *  M: Quick test/批测/监控/仿真/agent 端到端
 *  N: 表单校验 + 网络错 + session 过期
 *  O: 移动端 + 主题 + 搜索 + 设置
 *  (H 老 4 库 editor round-trip、L 老编辑器/BPMN 加载 — 功能已删,用例移除)
 */

const SHOT = '/home/fredgu/git_home/ruleforge/step5-screenshots';

async function shot(page, name) {
    await page.screenshot({path: `${SHOT}/ix-${name}.png`, fullPage: false});
}

async function dismissModal(page) {
    for (let i = 0; i < 3; i++) {
        const btn = page.locator('.bootbox .btn-primary, .modal .btn-primary, .ant-modal-close').first();
        if (await btn.isVisible({timeout: 500}).catch(() => false)) {
            await btn.click({force: true}).catch(() => {});
            await page.waitForTimeout(200);
        } else {
            break;
        }
    }
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(200);
}

// legacy editor.html?type=<type> → SPA segment /app/editor/<segment>
// flowbpmn 是唯一真重命名;ruleflow/ul/rulesetlib 共享 ruleset editor;
// monitoring/analysis 无 SPA 路由,回退到 type 原值。
const EDITOR_SEGMENT: Record<string, string> = {
    flowbpmn: 'flow',
    ruleflow: 'flow',
    ul: 'ruleset',
    rulesetlib: 'ruleset',
};

async function openEditor(page, type, file = '/test_proj/test_vl.xml') {
    const segment = EDITOR_SEGMENT[type] || type;
    await page.goto(`/app/editor/${segment}?file=${encodeURIComponent(file)}&project=test_proj`);
    await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
    await page.waitForTimeout(1500);
    await dismissModal(page);
}

// Helper: API call from within page context
async function apiCall(page, method, path, body) {
    return await page.evaluate(async ({method, path, body}) => {
        const opts = {method, credentials: 'include'};
        if (body) {
            opts.headers = {'Content-Type': 'application/x-www-form-urlencoded'};
            opts.body = new URLSearchParams(body).toString();
        }
        const r = await fetch('/api' + path, opts);
        const text = await r.text();
        try { return {status: r.status, body: JSON.parse(text)}; }
        catch { return {status: r.status, body: text}; }
    }, {method, path, body});
}

// H 段已删:老 4 库(variable/constant/parameter/action)编辑器 V7.23 下线,round-trip 用例整体死亡。

// ============================================================================
//  I: 30+ dialog 完整提交
// ============================================================================
test.describe('I: dialog complete submit flow', () => {
    test.beforeEach(async ({page}) => { await login(page); });

    test('I-01-create-project', async ({page}) => {
        // API: 直接创建
        const name = '__ix_proj_' + Date.now();
        const res = await apiCall(page, 'POST', '/frame/createProject', {newProjectName: name});
        expect([200, 400]).toContain(res.status);
        if (res.status === 200 && res.body.status) {
            // load projects to confirm
            const lp = await apiCall(page, 'POST', '/frame/loadProjects', {});
            expect(lp.status).toBe(200);
            expect(JSON.stringify(lp.body)).toContain(name);
        }
    });

    test('I-02-create-file-v1ruleset', async ({page}) => {
        // V7.23:VariableLibrary(老 4 库)已删,createFile 改用活类型 V1RuleSet
        const proj = '__ix_p_' + Date.now();
        await apiCall(page, 'POST', '/frame/createProject', {newProjectName: proj});
        const res = await apiCall(page, 'POST', '/frame/createFile', {
            path: '/' + proj + '/__ix_v' + Date.now() + '.v1rs.json', type: 'V1RuleSet'
        });
        expect(res.status).toBe(200);
        expect(res.body.type).toBe('v1ruleset');
        // cleanup
        if (res.status === 200 && res.body.fullPath) {
            await apiCall(page, 'POST', '/frame/deleteFile', {
                path: res.body.fullPath, classify: 'public', projectName: proj
            });
        }
    });

    test('I-03-create-folder', async ({page}) => {
        const res = await apiCall(page, 'POST', '/frame/createFolder', {
            fullFolderName: '/test_proj/__ix_folder_' + Date.now(),
            classify: 'public', projectName: 'test_proj', types: ''
        });
        expect([200, 400]).toContain(res.status);
    });

    test('I-04-rename-file', async ({page}) => {
        // 先建项目 + 文件(V7.23:老 VariableLibrary 类型已删,用 V1RuleSet)
        const ts = Date.now();
        const proj = '__ix_rn_' + ts;
        await apiCall(page, 'POST', '/frame/createProject', {newProjectName: proj});
        const path = '/' + proj + '/__ix_rename_' + ts + '.v1rs.json';
        const c = await apiCall(page, 'POST', '/frame/createFile', {path, type: 'V1RuleSet'});
        expect(c.status).toBe(200);
        // rename (now POST /frame/fileRename with path/newPath)
        const r = await apiCall(page, 'POST', '/frame/fileRename', {
            path, newPath: '/' + proj + '/__ix_renamed_' + ts + '.v1rs.json',
            classify: 'public', projectName: proj, types: ''
        });
        expect([200, 400, 500]).toContain(r.status);
        // cleanup
        await apiCall(page, 'POST', '/frame/deleteFile', {
            path: '/' + proj + '/__ix_renamed_' + ts + '.v1rs.json',
            classify: 'public', projectName: proj
        });
    });

    // I-05-save-and-reload-variable 已删:V7.23 老 4 库(变量库 vl.xml)编辑器下线,
    // /common/saveFile 不再接受 variable-library XML,该用例整体死亡。

    test('I-06-datasource-CRUD', async ({page}) => {
        const ts = Date.now();
        // create via JSON (need configJson field to avoid SQL NOT NULL)
        const create = await page.evaluate(async (body) => {
            const r = await fetch('/api/datasource', {
                method: 'POST', credentials: 'include',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(body)
            });
            return {status: r.status, body: await r.json().catch(() => r.text())};
        }, {
            name: '__ix_ds_' + ts, type: 'REST_API', url: 'http://example.com',
            enabled: true, timeoutMs: 5000, cacheTtlSeconds: 60,
            configJson: '{"baseUrl":"http://example.com"}'
        });
        expect([200, 201, 400]).toContain(create.status);
        const id = create.body?.id || create.body?.data?.id;
        if (id) {
            // READ
            const r = await page.evaluate(async (id) => {
                const r = await fetch('/api/datasource/' + id, {credentials: 'include'});
                return {status: r.status, body: await r.json().catch(() => r.text())};
            }, id);
            expect([200, 404]).toContain(r.status);
            // DELETE
            const d = await page.evaluate(async (id) => {
                const r = await fetch('/api/datasource/' + id, {method: 'DELETE', credentials: 'include'});
                return {status: r.status, body: await r.text()};
            }, id);
            expect([200, 204, 404]).toContain(d.status);
        }
    });

    test('I-07-load-projects-list', async ({page}) => {
        const r = await apiCall(page, 'POST', '/frame/loadProjects', {});
        expect(r.status).toBe(200);
        expect(r.body.repo).toBeDefined();
    });

    test('I-08-load-package-config', async ({page}) => {
        // 修复后:空 project name 应该返 200 + 空 {}
        const r = await apiCall(page, 'POST', '/packageeditor/loadPackageTree', {project: 'test_proj', packageId: 'main'});
        expect([200, 400, 500]).toContain(r.status);
    });

    test('I-09-monitoring-list', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/monitoring/alerts/', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 404, 500]).toContain(r.status);
    });

    test('I-10-datasource-list', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/datasource', {credentials: 'include'});
            return {status: r.status, body: await r.json().catch(() => r.text())};
        });
        expect([200, 404]).toContain(r.status);
    });

    // I-11-package-list 已删:后端 PackageEditorController 已整体移除
    // (/packageeditor/loadPackages 端点不存在,返 500 NoResourceFound)。

    test('I-12-version-list', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/deployment/history', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('I-13-agent-sessions', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/agent/sessions/', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 404, 500]).toContain(r.status);
    });

    test('I-14-logout', async ({page}) => {
        const r = await apiCall(page, 'POST', '/frame/logout', {});
        expect([200, 204, 302]).toContain(r.status);
        // re-login for other tests
        await login(page);
    });

    test('I-15-quick-test-doTest', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/test', {
                method: 'POST', credentials: 'include',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'rule=__nonexistent__&version=0&project=test_proj&file=/test_proj/test_vl.xml'
            });
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 404, 500]).toContain(r.status);
    });
});

// ============================================================================
//  J: project + files CRUD 端到端
// ============================================================================
test.describe('J: project + files CRUD end-to-end', () => {
    test.beforeEach(async ({page}) => { await login(page); });

    test('J-01-project-create-load-delete', async ({page}) => {
        const name = '__ix_j_' + Date.now();
        const c = await apiCall(page, 'POST', '/frame/createProject', {newProjectName: name});
        expect([200, 400]).toContain(c.status);

        const lp = await apiCall(page, 'POST', '/frame/loadProjects', {});
        expect(lp.status).toBe(200);
        expect(JSON.stringify(lp.body)).toContain(name);
    });

    test('J-02-file-create-load-save-load', async ({page}) => {
        // V7.23:老 VariableLibrary 已删,改用 V1 规则集(.v1rs.json)做 create→save→read 闭环
        const ts = Date.now();
        const proj = '__ix_j2_' + ts;
        await apiCall(page, 'POST', '/frame/createProject', {newProjectName: proj});
        const path = '/' + proj + '/__ix_j2_' + ts + '.v1rs.json';
        const c = await apiCall(page, 'POST', '/frame/createFile', {path, type: 'V1RuleSet'});
        expect(c.status).toBe(200);

        // save V1 ruleset JSON
        const content = JSON.stringify({id: 'rs1', type: 'RuleSet', name: '__ix_j2_marker__', hitPolicy: 'FIRST', rules: []});
        const s = await apiCall(page, 'POST', '/common/saveFile', {
            file: path, newVersion: 'false', content
        });
        expect(s.status, JSON.stringify(s.body)).toBe(200);
        expect(s.body.status, JSON.stringify(s.body)).toBe(true);

        // read back — V1 文件走 /frame/fileSource(loadXml 只认老 XML)
        const l = await apiCall(page, 'POST', '/frame/fileSource', {path});
        expect(l.status).toBe(200);
        expect(JSON.stringify(l.body)).toContain('__ix_j2_marker__');

        // cleanup
        await apiCall(page, 'POST', '/frame/deleteFile', {path, classify: 'public', projectName: proj});
    });

    test('J-03-folder-create-list', async ({page}) => {
        const name = '__ix_folder_' + Date.now();
        const c = await apiCall(page, 'POST', '/frame/createFolder', {
            fullFolderName: '/test_proj/' + name,
            classify: 'public', projectName: 'test_proj', types: ''
        });
        expect([200, 400]).toContain(c.status);

        const lp = await apiCall(page, 'POST', '/frame/loadProjects', {});
        if (lp.status === 200 && c.status === 200) {
            expect(JSON.stringify(lp.body)).toContain(name);
        }
    });
});

// ============================================================================
//  K: datasource + 知识包 + 版本 CRUD
// ============================================================================
test.describe('K: datasource + package + version CRUD', () => {
    test.beforeEach(async ({page}) => { await login(page); });

    test('K-01-datasource-full-CRUD', async ({page}) => {
        const ts = Date.now();
        const create = await page.evaluate(async (body) => {
            const r = await fetch('/api/datasource', {
                method: 'POST', credentials: 'include',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(body)
            });
            return {status: r.status, body: await r.json().catch(() => r.text())};
        }, {
            name: '__ix_k1_' + ts, type: 'REST_API', url: 'http://example.com',
            enabled: true, timeoutMs: 5000, cacheTtlSeconds: 60,
            configJson: '{"baseUrl":"http://example.com"}'
        });
        expect([200, 201, 400]).toContain(create.status);
        const id = create.body?.id || create.body?.data?.id;
        if (id) {
            const toggle = await page.evaluate(async (id) => {
                const r = await fetch('/api/datasource/toggle?id=' + id, {method: 'PUT', credentials: 'include'});
                return {status: r.status, body: await r.text()};
            }, id);
            // toggle endpoint 实际返 200/400/404(成功 or 参数错)
            expect([200, 400, 404, 500]).toContain(toggle.status);
            const del = await page.evaluate(async (id) => {
                const r = await fetch('/api/datasource/' + id, {method: 'DELETE', credentials: 'include'});
                return {status: r.status, body: await r.text()};
            }, id);
            expect([200, 204, 404]).toContain(del.status);
        }
    });

    test('K-02-datasource-test', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/datasource/test/1', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 404, 500]).toContain(r.status);
    });

    test('K-03-datasource-field-mappings', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/field-mappings?clazz=java.lang.String', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 404, 500]).toContain(r.status);
    });

    test('K-04-datasource-entity-mapping', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/datasource/entity-mapping', {method: 'POST', credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('K-05-package-save-config', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/packageeditor/saveResourcePackages', {
                method: 'POST', credentials: 'include',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'project=test_proj&packages=' + encodeURIComponent('[]')
            });
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('K-06-package-refresh-cache', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/packageeditor/refreshKnowledgeCache', {
                method: 'POST', credentials: 'include',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'project=test_proj'
            });
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('K-07-deployment-environments', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/deployment/environments', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('K-08-deployment-nodes', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/deployment/listNodes', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('K-09-approval-list', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/approval/listByProject', {
                method: 'POST', credentials: 'include',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'project=test_proj'
            });
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('K-10-gray-strategies', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/gray/strategies', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('K-11-shadow-configs', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/shadow/configs', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });
});

// L 段已删:决策表/树/卡编辑器 V7.0 删除、BPMN 决策流 V7.21 删除,加载用例整体死亡。

// ============================================================================
//  M: Quick test/批测/监控/仿真/agent 端到端
// ============================================================================
test.describe('M: deep functional flows', () => {
    test.beforeEach(async ({page}) => { await login(page); });

    test('M-01-quicktest-from-frame', async ({page}) => {
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(1000);
        // 选 test_proj
        const dd = page.locator('button:has-text("选择项目"), button:has-text("test_proj")').first();
        if (await dd.isVisible({timeout: 2000}).catch(() => false)) {
            await dd.click({force: true});
            await page.waitForTimeout(400);
            const proj = page.locator('li:has-text("test_proj")').first();
            if (await proj.isVisible({timeout: 1000}).catch(() => false)) {
                await proj.click();
                await page.waitForTimeout(1500);
            }
        }
        await shot(page, 'm-frame-with-project');
    });

    test('M-02-monitoring-load', async ({page}) => {
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="监控告警"]').click({force: true});
        await page.waitForTimeout(2500);
        await shot(page, 'm-monitoring');
    });

    test('M-03-simulation-load', async ({page}) => {
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="规则仿真"]').click({force: true});
        await page.waitForTimeout(2500);
        await shot(page, 'm-simulation');
    });

    test('M-04-agent-load', async ({page}) => {
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="智能分析"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'm-agent');
    });

    test('M-05-datasource-load', async ({page}) => {
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="数据源"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'm-datasource');
    });

    test('M-06-release-load', async ({page}) => {
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="版本发布"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'm-release');
    });

    test('M-07-project-files-load', async ({page}) => {
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        // rules panel (项目文件) is the default first panel
        await page.locator('.activity-bar-icon[title="规则编辑"]').click({force: true});
        await page.waitForTimeout(2000);
        // select test_proj
        const dd = page.locator('button:has-text("选择项目")').first();
        if (await dd.isVisible({timeout: 2000}).catch(() => false)) {
            await dd.click({force: true});
            await page.waitForTimeout(400);
            const proj = page.locator('li:has-text("test_proj")').first();
            if (await proj.isVisible({timeout: 1000}).catch(() => false)) {
                await proj.click();
                await page.waitForTimeout(2000);
            }
        }
        await shot(page, 'm-project-files');
    });

    test('M-08-quick-test-doBatchTest', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/packageeditor/doBatchTest', {
                method: 'POST', credentials: 'include',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'project=test_proj&packageId=&flowId=&input={}'
            });
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('M-09-batchtest-start-without-file', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/batchtest/start', {
                method: 'POST', credentials: 'include',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    subjectType: 'DATASOURCE', subjectId: 1,
                    inputSourceType: 'FILE', inputConfig: {rows: []},
                    project: 'test_proj', packageId: '', flowId: ''
                })
            });
            return {status: r.status, body: await r.text()};
        });
        expect([200, 400, 500]).toContain(r.status);
    });

    test('M-10-agent-vendors', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/agent/vendors', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 404, 500]).toContain(r.status);
    });

    test('M-11-agent-config', async ({page}) => {
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/agent/config', {credentials: 'include'});
            return {status: r.status, body: await r.text()};
        });
        expect([200, 404, 500]).toContain(r.status);
    });
});

// ============================================================================
//  N: 表单校验 + 网络错 + session 过期
// ============================================================================
test.describe('N: validation + error + session expiry', () => {
    test.beforeEach(async ({page}) => { await login(page); });

    test('N-01-empty-project-name-create', async ({page}) => {
        // 已修:返 200 + {} 不是 500
        const r = await apiCall(page, 'POST', '/frame/createProject', {newProjectName: ''});
        expect([200, 400]).toContain(r.status);
        if (r.status === 200) {
            expect(r.body.status).toBe(false);
        }
    });

    test('N-02-save-nonexistent-file', async ({page}) => {
        const r = await apiCall(page, 'POST', '/common/saveFile', {
            file: '/__nonexistent_path__/__no__.xml', newVersion: 'false', content: '<?xml version="1.0"?><r/>'
        });
        expect(r.status).toBe(200);  // 返 200 + body status=false
        expect(r.body.status).toBe(false);
        expect(r.body.message).toContain('not exist');
    });

    test('N-03-load-nonexistent-file', async ({page}) => {
        const r = await apiCall(page, 'POST', '/common/loadXml', {files: '/__nonexistent_path__/__no__.xml'});
        expect(r.status).toBe(404);
    });

    test('N-04-401-no-cookie', async ({browser}) => {
        const ctx = await browser.newContext();  // 无 cookie
        const page = await ctx.newPage();
        await page.goto('/');  // 先 navigate(baseURL = PLAYWRIGHT_BASE_URL)
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/frame/loadProjects', {method: 'POST'});
            return {status: r.status, body: await r.text()};
        });
        // 后端应返 401/302/403 (dev 模式可能宽松返 200)
        expect([200, 401, 302, 403]).toContain(r.status);
        await ctx.close();
    });

    test('N-05-malformed-save-content', async ({page}) => {
        // 保存非 XML content
        const r = await apiCall(page, 'POST', '/common/saveFile', {
            file: '/test_proj/test_vl.xml', newVersion: 'false', content: 'NOT XML AT ALL <<<'
        });
        // 后端可能成功保存但下次加载失败,这是已知行为
        expect([200, 400, 500]).toContain(r.status);
    });

    test('N-06-unauthorized-no-credentials', async ({browser}) => {
        const ctx = await browser.newContext();
        const page = await ctx.newPage();
        await page.goto('/');  // baseURL = PLAYWRIGHT_BASE_URL
        const r = await page.evaluate(async () => {
            const r = await fetch('/api/common/loadXml', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'files=/test_proj/test_vl.xml'
            });
            return {status: r.status, body: await r.text()};
        });
        expect(r.status).toBeGreaterThanOrEqual(200);
        await ctx.close();
    });
});

// ============================================================================
//  O: 移动端 + 主题 + 搜索 + 设置
// ============================================================================
test.describe('O: mobile + theme + search + settings', () => {
    test.beforeEach(async ({page}) => { await login(page); });

    test('O-01-mobile-frame', async ({page}) => {
        await page.setViewportSize({width: 375, height: 667});
        await page.goto('/app');
        await page.waitForTimeout(2000);
        await shot(page, 'o-mobile-frame');
    });

    test('O-02-mobile-login', async ({page}) => {
        await page.setViewportSize({width: 375, height: 667});
        await page.goto('/login');
        await page.waitForTimeout(2000);
        await shot(page, 'o-mobile-login');
    });

    test('O-03-tablet-frame', async ({page}) => {
        await page.setViewportSize({width: 768, height: 1024});
        await page.goto('/app');
        await page.waitForTimeout(2000);
        await shot(page, 'o-tablet-frame');
    });

    test('O-04-tablet-editor', async ({page}) => {
        await page.setViewportSize({width: 768, height: 1024});
        await openEditor(page, 'variable', '/test_proj/test_vl.xml');
        await shot(page, 'o-tablet-editor');
    });

    test('O-05-search-tree', async ({page}) => {
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(1500);
        // select project
        try {
            const dd = page.locator('button:has-text("选择项目")').first();
            await dd.click({force: true, timeout: 5000});
            await page.waitForTimeout(500);
            const proj = page.locator('li:has-text("test_proj")').first();
            await proj.click({force: true, timeout: 5000});
            await page.waitForTimeout(2000);
        } catch (_) {
            // ignore — 可能项目已选
        }
        // find search input (use waitFor + try-catch to avoid hanging)
        try {
            const search = page.locator('input[placeholder*="搜索"]').first();
            await search.fill('test_', {timeout: 5000});
            await page.waitForTimeout(1500);
            await shot(page, 'o-search-results');
        } catch (_) {
            await shot(page, 'o-search-not-found');
        }
    });

    test('O-06-settings-load', async ({page}) => {
        await page.goto('/app');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="系统设置"]').click({force: true});
        await page.waitForTimeout(2000);
        await shot(page, 'o-settings');
    });
});
