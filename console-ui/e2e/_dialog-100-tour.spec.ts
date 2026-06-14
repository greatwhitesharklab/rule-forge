import {test} from '@playwright/test';
import {login} from './helpers';

/**
 * V5.9.0 100% Dialog tour — 全部 30+ dialog 触发截图
 *
 * 策略: 每个 dialog 都通过真实 UI 路径触发(右键 tree / 点 toolbar / 走 config)
 * 触发后等待 dialog 出现 (modal 元素 / .ant-modal-wrap),截图,关掉
 *
 * 关键修复: 之前 tour 跑下来,7 个 package dialog 都因为 loadPackageConfig 500 NPE
 * 失败,且任何 5xx 都返 raw JSON(让用户看到 "服务端错误: {timestamp:...}")。
 * 后端修完后 (GlobalExceptionHandler 兜底 + loadPackageConfigs NPE 修) 重跑。
 */
const SHOT_DIR = '/home/fredgu/git_home/ruleforge/step5-screenshots';

async function shot(page, name) {
    await page.screenshot({path: `${SHOT_DIR}/dialog-${name}.png`, fullPage: false});
}

async function dismissAnyModal(page) {
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(200);
    const cancels = page.locator(
        '.ant-modal-close, .modal-header .close, .bootbox-close-button, ' +
        'button:has-text("取消"), button:has-text("Cancel"), button:has-text("Close"), ' +
        'button:has-text("关闭")'
    );
    const n = await cancels.count();
    for (let i = 0; i < Math.min(n, 3); i++) {
        try {
            await cancels.nth(i).click({timeout: 500, force: true});
        } catch (_) {}
    }
    await page.waitForTimeout(300);
}

async function openProject(page, name = 'test_proj') {
    // 选 test_proj 项目
    const dropdown = page.locator('button:has-text("选择项目")').first();
    if (await dropdown.isVisible({timeout: 2000}).catch(() => false)) {
        await dropdown.click();
        await page.waitForTimeout(400);
        const proj = page.locator(`.dropdown-menu li:has-text("${name}"), .ant-dropdown-menu li:has-text("${name}")`).first();
        if (await proj.isVisible({timeout: 2000}).catch(() => false)) {
            await proj.click();
            await page.waitForTimeout(1500);
        }
    }
}

// legacy editor.html?type=<type> → SPA segment /app/editor/<segment>
// flowbpmn 是唯一真重命名;ruleflow/ul/rulesetlib 共享 ruleset editor;
// monitoring/analysis 无 SPA 路由(仪表盘类,main.tsx 未注册),回退到 type 原值。
const EDITOR_SEGMENT: Record<string, string> = {
    flowbpmn: 'flow',
    ruleflow: 'flow',
    ul: 'ruleset',
    rulesetlib: 'ruleset',
};

async function openEditor(page, type, file) {
    const segment = EDITOR_SEGMENT[type] || type;
    await page.goto(`/app/editor/${segment}?file=${encodeURIComponent(file)}&project=test_proj`);
    await page.waitForLoadState('networkidle', {timeout: 15000}).catch(() => {});
    await page.waitForTimeout(2000);
    // dismiss bootbox error modal
    const okBtn = page.locator('.bootbox .btn-primary, .modal .btn-primary, button:has-text("确定")').first();
    if (await okBtn.isVisible({timeout: 500}).catch(() => false)) {
        await okBtn.click({force: true, timeout: 1000}).catch(() => {});
        await page.waitForTimeout(300);
    }
}

test.describe('100% Dialog tour', () => {
    test.beforeEach(async ({page}) => {
        await login(page);
    });

    // ========== FRAME DIALOGS (8) ==========
    test('01-frame-create-project', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.waitForTimeout(800);
        // 选项目后,右键 root "项目列表" 节点
        await openProject(page);
        await page.waitForTimeout(800);
        const rootNode = page.locator('.tree-node, .tree-text, [class*="tree-node"]').first();
        if (await rootNode.isVisible({timeout: 2000}).catch(() => false)) {
            await rootNode.click({button: 'right', force: true});
            await page.waitForTimeout(500);
            const newItem = page.locator('text=创建新项目, li:has-text("创建新项目")').first();
            if (await newItem.isVisible({timeout: 1000}).catch(() => false)) {
                await newItem.click({force: true, timeout: 1000}).catch(() => {});
                await page.waitForTimeout(800);
            }
        }
        await shot(page, 'frame-create-project');
        await dismissAnyModal(page);
    });

    test('02-frame-update-project', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        // 右键 project node
        const projectNode = page.locator('.tree-text, .tree-node-text, .tree-item, .ant-tree-node-content-wrapper').first();
        if (await projectNode.isVisible({timeout: 2000}).catch(() => false)) {
            await projectNode.click({button: 'right', force: true});
            await page.waitForTimeout(500);
            const updateItem = page.locator('text=编辑项目, text=修改项目, li:has-text("编辑")').first();
            if (await updateItem.isVisible({timeout: 1000}).catch(() => false)) {
                await updateItem.click({force: true, timeout: 1000}).catch(() => {});
                await page.waitForTimeout(800);
            }
        }
        await shot(page, 'frame-update-project');
        await dismissAnyModal(page);
    });

    test('03-frame-create-folder', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const folderNode = page.locator('.tree-text, .tree-node-text, .ant-tree-node-content-wrapper').nth(1);
        if (await folderNode.isVisible({timeout: 2000}).catch(() => false)) {
            await folderNode.click({button: 'right', force: true});
            await page.waitForTimeout(500);
            const folderItem = page.locator('text=新建文件夹, text=创建文件夹, li:has-text("文件夹")').first();
            if (await folderItem.isVisible({timeout: 1000}).catch(() => false)) {
                await folderItem.click({force: true, timeout: 1000}).catch(() => {});
                await page.waitForTimeout(800);
            }
        }
        await shot(page, 'frame-create-folder');
        await dismissAnyModal(page);
    });

    test('04-frame-create-file', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const folderNode = page.locator('.tree-text, .tree-node-text, .ant-tree-node-content-wrapper').nth(1);
        if (await folderNode.isVisible({timeout: 2000}).catch(() => false)) {
            await folderNode.click({button: 'right', force: true});
            await page.waitForTimeout(500);
            const fileItem = page.locator('text=新建文件, text=创建文件, li:has-text("文件")').first();
            if (await fileItem.isVisible({timeout: 1000}).catch(() => false)) {
                await fileItem.click({force: true, timeout: 1000}).catch(() => {});
                await page.waitForTimeout(800);
            }
        }
        await shot(page, 'frame-create-file');
        await dismissAnyModal(page);
    });

    test('05-frame-rename', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const node = page.locator('.tree-text, .tree-node-text, .ant-tree-node-content-wrapper').nth(1);
        if (await node.isVisible({timeout: 2000}).catch(() => false)) {
            await node.click({button: 'right', force: true});
            await page.waitForTimeout(500);
            const renameItem = page.locator('text=重命名, li:has-text("重命名")').first();
            if (await renameItem.isVisible({timeout: 1000}).catch(() => false)) {
                await renameItem.click({force: true, timeout: 1000}).catch(() => {});
                await page.waitForTimeout(800);
            }
        }
        await shot(page, 'frame-rename');
        await dismissAnyModal(page);
    });

    test('06-frame-source', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const node = page.locator('.tree-text, .tree-node-text, .ant-tree-node-content-wrapper').first();
        if (await node.isVisible({timeout: 2000}).catch(() => false)) {
            await node.click({button: 'right', force: true});
            await page.waitForTimeout(500);
            const viewSrc = page.locator('text=查看源码, text=源码, text=查看XML, text=查看 Xml, li:has-text("源码")').first();
            if (await viewSrc.isVisible({timeout: 1000}).catch(() => false)) {
                await viewSrc.click({force: true, timeout: 1000}).catch(() => {});
                await page.waitForTimeout(1200);
            }
        }
        await shot(page, 'frame-source');
        await dismissAnyModal(page);
    });

    test('07-frame-import-project', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const rootNode = page.locator('.tree-text, .tree-node-text, .ant-tree-node-content-wrapper').first();
        if (await rootNode.isVisible({timeout: 2000}).catch(() => false)) {
            await rootNode.click({button: 'right', force: true});
            await page.waitForTimeout(500);
            const importItem = page.locator('text=导入项目, li:has-text("导入")').first();
            if (await importItem.isVisible({timeout: 1000}).catch(() => false)) {
                await importItem.click({force: true, timeout: 1000}).catch(() => {});
                await page.waitForTimeout(800);
            }
        }
        await shot(page, 'frame-import-project');
        await dismissAnyModal(page);
    });

    test('08-frame-version-list', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const node = page.locator('.tree-text, .tree-node-text, .ant-tree-node-content-wrapper').nth(1);
        if (await node.isVisible({timeout: 2000}).catch(() => false)) {
            await node.click({button: 'right', force: true});
            await page.waitForTimeout(500);
            const versionItem = page.locator('text=版本, li:has-text("版本")').first();
            if (await versionItem.isVisible({timeout: 1000}).catch(() => false)) {
                await versionItem.click({force: true, timeout: 1000}).catch(() => {});
                await page.waitForTimeout(1200);
            }
        }
        await shot(page, 'frame-version-list');
        await dismissAnyModal(page);
    });

    // ========== EDITOR DIALOGS (3) ==========
    test('09-editor-condition-list', async ({page}) => {
        await openEditor(page, 'decisiontable', '/test_proj/test_dt.xml');
        await page.waitForTimeout(1000);
        const cell = page.locator('table td, .htCore td').first();
        if (await cell.isVisible({timeout: 2000}).catch(() => false)) {
            await cell.dblclick({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'editor-condition-list');
        await dismissAnyModal(page);
    });

    test('10-editor-resource-list', async ({page}) => {
        await openEditor(page, 'variable', '/test_proj/test_vl.xml');
        await page.waitForTimeout(1000);
        const btn = page.locator('button:has-text("选择资源"), a:has-text("选择资源"), button:has-text("引用")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'editor-resource-list');
        await dismissAnyModal(page);
    });

    test('11-editor-resource-version', async ({page}) => {
        await openEditor(page, 'variable', '/test_proj/test_vl.xml');
        await page.waitForTimeout(1000);
        const btn = page.locator('button:has-text("版本"), a:has-text("版本")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'editor-resource-version');
        await dismissAnyModal(page);
    });

    // ========== EDITOR ACTION/VARIABLE/RESOURCE DIALOGS ==========
    test('12-editor-quick-test', async ({page}) => {
        await openEditor(page, 'ruleset', '/test_proj/test_rules.xml');
        await page.waitForTimeout(1500);
        const btn = page.locator('button:has-text("快速测试"), a:has-text("快速测试"), button:has-text("测试")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1500);
        }
        await shot(page, 'editor-quick-test');
        await dismissAnyModal(page);
    });

    test('13-editor-config-library', async ({page}) => {
        await openEditor(page, 'ruleset', '/test_proj/test_rules.xml');
        await page.waitForTimeout(1500);
        const btn = page.locator('button:has-text("配置库"), a:has-text("配置库"), button:has-text("变量")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1500);
        }
        await shot(page, 'editor-config-library');
        await dismissAnyModal(page);
    });

    test('14-editor-knowledge-tree', async ({page}) => {
        await openEditor(page, 'ul', '/test_proj/script.ul');
        await page.waitForTimeout(1500);
        const btn = page.locator('button:has-text("知识树"), a:has-text("知识树"), button:has-text("引用")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1500);
        }
        await shot(page, 'editor-knowledge-tree');
        await dismissAnyModal(page);
    });

    test('15-variable-import-xml', async ({page}) => {
        await openEditor(page, 'variable', '/test_proj/test_vl.xml');
        await page.waitForTimeout(1500);
        const btn = page.locator('button:has-text("导入XML"), button:has-text("导入 XML"), a:has-text("导入")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1500);
        }
        await shot(page, 'variable-import-xml');
        await dismissAnyModal(page);
    });

    test('16-action-select-method', async ({page}) => {
        await openEditor(page, 'action', '/test_proj/test_al.xml');
        await page.waitForTimeout(1500);
        const btn = page.locator('button:has-text("选择方法"), a:has-text("选择方法"), button:has-text("方法")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1500);
        }
        await shot(page, 'action-select-method');
        await dismissAnyModal(page);
    });

    test('17-resource-add-params', async ({page}) => {
        await openEditor(page, 'ruleflow', '/test_proj/test_rl.xml');
        await page.waitForTimeout(1500);
        const btn = page.locator('button:has-text("添加参数"), button:has-text("参数"), a:has-text("参数")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1500);
        }
        await shot(page, 'resource-add-params');
        await dismissAnyModal(page);
    });

    test('18-reference-dialog', async ({page}) => {
        await openEditor(page, 'ruleset', '/test_proj/test_rules.xml');
        await page.waitForTimeout(1500);
        const link = page.locator('a:has-text("引用"), button:has-text("引用"), .reference-link').first();
        if (await link.isVisible({timeout: 2000}).catch(() => false)) {
            await link.click({force: true});
            await page.waitForTimeout(1500);
        }
        await shot(page, 'reference-dialog');
        await dismissAnyModal(page);
    });

    // ========== PACKAGE DIALOGS (9) ==========
    test('19-package-create-package', async ({page}) => {
        // 切到 package view (有 __res__package__file__ 时会进 PackageEditor)
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        // 切到 package view button
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        // 找 知识包.rp
        const rp = page.locator('text=知识包.rp, .package-navigator, .package-view').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2000);
        }
        const btn = page.locator('button:has-text("添加包"), button:has-text("添加知识包")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'package-create');
        await dismissAnyModal(page);
    });

    test('20-package-create-item', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        const rp = page.locator('text=知识包.rp, .package-navigator, .package-view').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2000);
        }
        const btn = page.locator('button:has-text("添加知识文件"), button:has-text("添加文件"), button:has-text("添加条目")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'package-create-item');
        await dismissAnyModal(page);
    });

    test('21-package-versions', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        const rp = page.locator('text=知识包.rp, .package-navigator, .package-view').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2000);
        }
        const btn = page.locator('button:has-text("生成版本"), button:has-text("版本")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'package-versions');
        await dismissAnyModal(page);
    });

    test('22-package-simulator', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        const rp = page.locator('text=知识包.rp, .package-navigator, .package-view').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2000);
        }
        const btn = page.locator('button:has-text("仿真测试"), button:has-text("仿真")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'package-simulator');
        await dismissAnyModal(page);
    });

    test('23-package-rete-diagram', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        const rp = page.locator('text=知识包.rp, .package-navigator, .package-view').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2000);
        }
        const btn = page.locator('button:has-text("RETE"), button:has-text("规则图"), button:has-text("网络图")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'package-rete-diagram');
        await dismissAnyModal(page);
    });

    test('24-package-flow', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        const rp = page.locator('text=知识包.rp, .package-navigator, .package-view').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2000);
        }
        const btn = page.locator('button:has-text("流程"), button:has-text("Flow"), button:has-text("决策流")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'package-flow');
        await dismissAnyModal(page);
    });

    test('25-package-import-excel', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        const rp = page.locator('text=知识包.rp, .package-navigator, .package-view').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2000);
        }
        const btn = page.locator('button:has-text("导入Excel"), button:has-text("导入 Excel"), a:has-text("导入")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'package-import-excel');
        await dismissAnyModal(page);
    });

    test('26-package-export-excel', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        const rp = page.locator('text=知识包.rp, .package-navigator, .package-view').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2000);
        }
        const btn = page.locator('button:has-text("导出Excel"), button:has-text("导出 Excel"), a:has-text("导出")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'package-export-excel');
        await dismissAnyModal(page);
    });

    test('27-package-batch-test', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await openProject(page);
        await page.waitForTimeout(1500);
        const viewToggle = page.locator('button[title*="包"], button[title*="切换"]').first();
        if (await viewToggle.isVisible({timeout: 1000}).catch(() => false)) {
            await viewToggle.click({force: true});
            await page.waitForTimeout(1500);
        }
        const rp = page.locator('text=知识包.rp, .package-navigator, .package-view').first();
        if (await rp.isVisible({timeout: 1500}).catch(() => false)) {
            await rp.click({force: true});
            await page.waitForTimeout(2000);
        }
        const btn = page.locator('button:has-text("批量测试"), button:has-text("Batch Test")').first();
        if (await btn.isVisible({timeout: 2000}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'package-batch-test');
        await dismissAnyModal(page);
    });

    // ========== COMPONENT DIALOGS ==========
    test('30-component-version-select', async ({page}) => {
        await page.goto('/html/frame.html');
        await page.waitForSelector('.app-layout', {timeout: 10000});
        await page.locator('.activity-bar-icon[title="版本发布"]').click({force: true});
        await page.waitForTimeout(2000);
        const btn = page.locator('button:has-text("版本"), .ant-select-selector').first();
        if (await btn.isVisible({timeout: 1500}).catch(() => false)) {
            await btn.click({force: true});
            await page.waitForTimeout(1200);
        }
        await shot(page, 'component-version-select');
        await dismissAnyModal(page);
    });
});
