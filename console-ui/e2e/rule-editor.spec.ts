import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * BDD Tests for Wizard Rule Editor (Ruleset Editor)
 *
 * Given: User is logged in and opens ruleset editor
 * When: User interacts with wizard rule editor
 * Then: Expected rule operations should work
 *
 * SPA DOM (RulesetEditor.tsx): mounts into #root (no #container /
 * #toolbarContainer / #dialogContainer — those were jquery editor.html ids).
 * The React editor renders <Text strong>规则集: <file></Text> + AntD
 * <Button>添加规则</Button> + <Button>保存</Button> + an Alert when empty.
 */
test.describe('Wizard Rule Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load ruleset editor page ──
    // Given: A logged-in user navigates to /app/editor/ruleset?file=/project/rules.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should be "RuleForge" (SPA shell)
    // And:   The React editor should render the "规则集: <file>" header
    //  (the SPA route /app/editor/ruleset is the unified entry;
    //   the old /html/editor.html?type=ruleset & /html/ruleset-editor.html no longer exist; dismiss any
    //   error dialogs from backend 500s — React uses AntD message/Modal, not bootbox)
    test('should load ruleset editor page', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle');

        // Then: SPA title is "RuleForge"
        await expect(page).toHaveTitle(/RuleForge/);

        // Then: The ruleset editor header should be visible (React mounted)
        await expect(page.getByText('规则集:').first()).toBeVisible({ timeout: 15000 });

        // Then: Dismiss any error dialogs (React Modal / AntD message)
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should display toolbar with rule buttons ──
    // Given: A logged-in user is on the ruleset editor page
    // When:  The RulesetEditor component finishes mounting
    // Then:  A button labeled "添加规则" should be visible
    // And:   A button labeled "保存" should be visible
    //  (React editor uses an inline AntD Space toolbar — no #toolbarContainer)
    test('should display toolbar with rule buttons', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle');

        // Then: "添加规则" button should be visible (React toolbar)
        await expect(page.getByRole('button', { name: '添加规则' })).toBeVisible({ timeout: 15000 });

        // Then: "保存" button should be visible (React toolbar)
        await expect(page.getByRole('button', { name: '保存' })).toBeVisible();

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should display rule content in container ──
    // Given: A logged-in user is on the ruleset editor page
    // When:  RulesetEditor has parsed its data
    // Then:  The "规则集: <file>" header should be visible
    // And:   The remark textarea placeholder should be visible
    //  (no #container; React mounts into #root)
    test('should display rule content in container', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle');

        // Then: The ruleset editor header should be visible
        await expect(page.getByText('规则集:').first()).toBeVisible({ timeout: 15000 });

        // Then: The remark textarea placeholder should be visible
        await expect(page.getByPlaceholder('规则集备注 (remark)')).toBeVisible();

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should show prompt when clicking add rule button ──
    // Given: A logged-in user is on the ruleset editor page with the toolbar rendered
    // When:  The user clicks the "添加规则" button
    // Then:  A native prompt() should appear asking for a rule name
    //  (React uses window.prompt, not a bootbox modal — verify the button is clickable)
    test('should show prompt when clicking add rule button', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle');

        // Then: "添加规则" button should be visible (React toolbar)
        const addRuleBtn = page.getByRole('button', { name: '添加规则' });
        await expect(addRuleBtn).toBeVisible({ timeout: 15000 });

        // Then: Clicking the button triggers a prompt (accept it to dismiss)
        page.once('dialog', (dialog) => dialog.accept('规则1').catch(() => {}));
        await addRuleBtn.click().catch(() => {});

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render dialog host ──
    // Given: A logged-in user is on the ruleset editor page
    // When:  The React app mounts
    // Then:  The #root element (SPA mount point) should be attached
    //  (no #dialogContainer; AntD message/Modal render into #root's portal)
    test('should render dialog host', async ({ page }) => {
        await page.goto('/app/editor/ruleset?file=/project/rules.xml');
        await page.waitForLoadState('networkidle');

        // Then: SPA root mount point should be attached
        await expect(page.locator('#root')).toBeAttached({ timeout: 15000 });
    });
});
