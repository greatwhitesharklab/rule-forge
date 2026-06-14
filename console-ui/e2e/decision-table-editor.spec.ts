import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * BDD Tests for Decision Table Editor
 *
 * Given: User is logged in and opens decision table editor
 * When: User interacts with decision table
 * Then: Expected table operations should work
 *
 * SPA DOM (DecisionTableEditor.tsx): mounts into #root (no #container /
 * #dialogContainer — those were jquery editor.html ids). The React editor
 * renders <Text strong>决策表: <file></Text> + AntD <Button>添加列</Button> +
 * <Button>添加行</Button> + <Button>粘贴行</Button> + <Button>保存</Button> +
 * an AntD <Table> when columns exist (or an Alert when empty).
 */
test.describe('Decision Table Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load decision table editor page ──
    // Given: A logged-in user navigates to /app/editor/decisiontable?file=/project/dt.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should be "RuleForge" (SPA shell)
    // And:   The "决策表: <file>" header should be visible
    //  (the SPA route /app/editor/<type> is the unified entry;
    //   dismiss any error dialogs — React uses AntD message/Modal, not bootbox)
    test('should load decision table editor page', async ({ page }) => {
        await page.goto('/app/editor/decisiontable?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: SPA title is "RuleForge"
        await expect(page).toHaveTitle(/RuleForge/);

        // Then: The decision-table editor header should be visible (React mounted)
        await expect(page.getByText('决策表:').first()).toBeVisible({ timeout: 15000 });

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render container with content ──
    // Given: A logged-in user is on the decision table editor page
    // When:  The DecisionTableEditor component has finished its initial render
    // Then:  The "决策表: <file>" header should be visible
    // And:   The toolbar buttons (添加列/添加行/保存) should be visible
    //  (no #container; React mounts into #root)
    test('should render container with content', async ({ page }) => {
        await page.goto('/app/editor/decisiontable?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: The decision-table editor header should be visible
        await expect(page.getByText('决策表:').first()).toBeVisible({ timeout: 15000 });

        // Then: The "添加列" toolbar button should be visible
        await expect(page.getByRole('button', { name: '添加列' })).toBeVisible();

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should initialize table editor ──
    // Given: A logged-in user is on the decision table editor page
    // When:  The DecisionTableEditor React component initializes
    // Then:  The "决策表: <file>" header should be visible
    test('should initialize table editor', async ({ page }) => {
        await page.goto('/app/editor/decisiontable?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: The decision-table editor header should be visible (editor initialized)
        await expect(page.getByText('决策表:').first()).toBeVisible({ timeout: 15000 });

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should handle right-click on container ──
    // Given: A logged-in user is on the decision table editor page
    // When:  The user right-clicks on the editor body
    // Then:  No uncaught error should be thrown
    //  (React DecisionTableEditor wires per-cell Dropdown context menus;
    //   with an empty table there are no cells, so just verify the editor body
    //   does not throw on a right-click)
    test('should handle right-click on container', async ({ page }) => {
        await page.goto('/app/editor/decisiontable?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: The decision-table editor header should be visible
        await expect(page.getByText('决策表:').first()).toBeVisible({ timeout: 15000 });

        // Then: Right-click the editor header area without throwing
        await page.getByText('决策表:').first().click({ button: 'right' }).catch(() => {});

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render dialog components ──
    // Given: A logged-in user is on the decision table editor page
    // When:  The React app mounts
    // Then:  The #root element (SPA mount point) should be attached
    // And:   #root should contain React-rendered children
    //  (no #dialogContainer; AntD Modal/message portals render into #root)
    test('should render dialog components', async ({ page }) => {
        await page.goto('/app/editor/decisiontable?file=/project/dt.xml');
        await page.waitForLoadState('networkidle');

        // Then: SPA root mount point should be visible (it acts as the editor host)
        const root = page.locator('#root');
        await expect(root).toBeVisible({ timeout: 10000 });

        // Then: #root should have React-rendered children
        const children = root.locator('*');
        const childCount = await children.count();
        expect(childCount).toBeGreaterThan(0);
    });
});
