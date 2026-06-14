import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * BDD Tests for Scorecard Editor
 *
 * Given: User is logged in and opens scorecard editor
 * When: User interacts with scorecard configuration
 * Then: Expected scorecard operations should work
 *
 * SPA DOM (ScoreCardEditor.tsx): mounts into #root (no #tableContainer /
 * #toolbarContainer / #dialogContainer — those were jquery editor.html ids).
 * The React editor renders <Text strong>评分卡: <file></Text> + AntD
 * <Button>添加属性行</Button> + <Button>添加自定义列</Button> +
 * <Button>粘贴属性组</Button> + <Button>保存</Button> + an AntD <Table> when
 * attribute rows exist (or an Alert when empty).
 */
test.describe('Scorecard Editor', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    // ── BDD STUB: should load scorecard editor page ──
    // Given: A logged-in user navigates to /app/editor/scorecard?file=/project/scorecard.xml
    // When:  The page finishes loading and the network is idle
    // Then:  The browser title should be "RuleForge" (SPA shell)
    // And:   The "评分卡: <file>" header should be visible
    //  (the SPA route /app/editor/scorecard is the unified entry;
    //   the old /html/editor.html?type=scorecard & /html/score-card-editor.html no longer exist; dismiss any
    //   error dialogs — React uses AntD message/Modal, not bootbox)
    test('should load scorecard editor page', async ({ page }) => {
        await page.goto('/app/editor/scorecard?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: SPA title is "RuleForge"
        await expect(page).toHaveTitle(/RuleForge/);

        // Then: The scorecard editor header should be visible (React mounted)
        await expect(page.getByText('评分卡:').first()).toBeVisible({ timeout: 15000 });

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should display toolbar with buttons ──
    // Given: A logged-in user is on the scorecard editor page
    // When:  The ScoreCardEditor component finishes mounting
    // Then:  A button labeled "添加属性行" should be visible
    // And:   A button labeled "添加自定义列" should be visible
    // And:   A button labeled "保存" should be visible
    //  (React editor uses an inline AntD Space toolbar — no #toolbarContainer.
    //   The old jquery "快速测试" button is TODO for the React port.)
    test('should display toolbar with buttons', async ({ page }) => {
        await page.goto('/app/editor/scorecard?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: "添加属性行" button should be visible (React toolbar)
        await expect(page.getByRole('button', { name: '添加属性行' })).toBeVisible({ timeout: 15000 });

        // Then: "添加自定义列" button should be visible
        await expect(page.getByRole('button', { name: '添加自定义列' })).toBeVisible();

        // Then: "保存" button should be visible
        await expect(page.getByRole('button', { name: '保存' })).toBeVisible();

        // TODO(React port): the jquery editor had a "快速测试" toolbar button;
        // the React ScoreCardEditor omits it. Restore that assertion when the
        // React port adds the quick-test control.

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render scorecard table ──
    // Given: A logged-in user is on the scorecard editor page
    // When:  The ScoreCardEditor component has initialized
    // Then:  The "评分卡: <file>" header should be visible
    // And:   An AntD Table (or empty-state Alert) should be present
    //  (no #tableContainer; React renders an AntD <Table> into #root)
    test('should render scorecard table', async ({ page }) => {
        await page.goto('/app/editor/scorecard?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: The scorecard editor header should be visible
        await expect(page.getByText('评分卡:').first()).toBeVisible({ timeout: 15000 });

        // Then: The scorecard content should be present (AntD Table when rows
        // exist, or an empty-state Alert — verify the editor body rendered)
        await expect(page.locator('.ant-table, .ant-alert').first()).toBeAttached({ timeout: 15000 });

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should add attribute row when clicking button ──
    // Given: A logged-in user is on the scorecard editor page with the toolbar rendered
    // When:  The user clicks the "添加属性行" button
    // Then:  A new attribute row should appear in the scorecard table
    //  (React ScoreCardEditor.addAttributeRow appends a row to state; with no
    //   rows initially the editor shows an Alert, after add it shows a Table)
    test('should add attribute row when clicking button', async ({ page }) => {
        await page.goto('/app/editor/scorecard?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: "添加属性行" button should be visible (React toolbar)
        const addAttrBtn = page.getByRole('button', { name: '添加属性行' });
        await expect(addAttrBtn).toBeVisible({ timeout: 15000 });

        // Then: Click the button to add an attribute row
        await addAttrBtn.click();

        // Then: An AntD Table should now be present (was Alert before)
        await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: 5000 });

        // Then: Dismiss any error dialogs
        const okBtn = page.locator('.ant-modal .ant-btn-primary, .bootbox .btn-primary, .modal .btn-primary').first();
        if (await okBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await okBtn.click();
        }
    });

    // ── BDD STUB: should render dialog host ──
    // Given: A logged-in user is on the scorecard editor page
    // When:  The React app mounts
    // Then:  The #root element (SPA mount point) should be attached
    //  (no #dialogContainer; AntD message/Modal render into #root's portal)
    test('should render dialog host', async ({ page }) => {
        await page.goto('/app/editor/scorecard?file=/project/scorecard.xml');
        await page.waitForLoadState('networkidle');

        // Then: SPA root mount point should be attached
        await expect(page.locator('#root')).toBeAttached({ timeout: 15000 });
    });
});
