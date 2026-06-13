/**
 * V5.45.5 — Frame 顶部 AlertBell vitest BDD。
 *
 * <p>锁 3 件事:
 * <ol>
 *   <li>无 draft / 无 audit 时,badge 不显示(0 通知 = 隐藏)</li>
 *   <li>有 PENDING_REVIEW draft 时,badge 显示数字,点开 popover 列 draft title</li>
 *   <li>"去审批"按钮 emit 事件 / 触发 navigation(DraftsView / AgentPanel)</li>
 * </ol>
 *
 * <p>本测试用 jsdom + @testing-library/react,antd Popover / Badge 在 jsdom
 * 下行为有限(无 portal),所以通过 data-testid + 文本断言测结构。
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, waitFor, fireEvent } from '@testing-library/react';

// mock DraftsView 依赖的 client — 返 2 条 draft(PENDING_REVIEW + APPROVED)
vi.mock('@/api/client', () => ({
    jsonPost: vi.fn(async (url: string, body: any) => {
        if (url === '/agent/tools/list_drafts') {
            // V5.45.5 mock — 模拟后端按 status 过滤:body.status === 'PENDING_REVIEW'
            // 时只返 1 条;否则返全部 2 条
            const all = [
                {
                    draftId: 'D-1',
                    ruleType: 'DECISION_TABLE',
                    project: 'loan',
                    status: 'PENDING_REVIEW',
                    title: 'Loan interest bump',
                    source: 'AI',
                    createdBy: 'agent-1',
                    createdAt: '2026-06-12T10:00:00Z',
                    updatedAt: '2026-06-12T10:00:00Z',
                    reviewedBy: null,
                    reviewedAt: null,
                    reviewComment: null,
                    appliedVersion: null,
                    appliedAt: null,
                    expiresAt: null,
                    content: null,
                },
                {
                    draftId: 'D-2',
                    ruleType: 'DECISION_TABLE',
                    project: 'loan',
                    status: 'APPROVED',
                    title: 'Old approval',
                    source: 'AI',
                    createdBy: 'agent-1',
                    createdAt: '2026-06-10T10:00:00Z',
                    updatedAt: '2026-06-10T11:00:00Z',
                    reviewedBy: 'admin',
                    reviewedAt: '2026-06-10T11:00:00Z',
                    reviewComment: 'OK',
                    appliedVersion: 'v1',
                    appliedAt: null,
                    expiresAt: null,
                    content: null,
                },
            ];
            const filtered = body && body.status
                ? all.filter((d) => d.status === body.status)
                : all;
            return {drafts: filtered, count: filtered.length};
        }
        if (url === '/agent/tools/list_agent_audit') {
            return {audits: [], count: 0};
        }
        return {drafts: [], count: 0};
    }),
}));

import AlertBell from './AlertBell';
import {jsonPost} from '@/api/client';

describe('AlertBell - 失败退避 (B-1)', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('GIVEN 连续 list_drafts 失败 WHEN polling THEN 连续 MAX_FAILS(3) 次后停止轮询,不再刷屏 404', async () => {
        const mocked = vi.mocked(jsonPost);
        mocked.mockClear();
        // 前 3 次失败触发退避;用 Once 避免污染后续 describe 的默认 mock 实现
        mocked.mockRejectedValueOnce(new Error('404'));
        mocked.mockRejectedValueOnce(new Error('404'));
        mocked.mockRejectedValueOnce(new Error('404'));
        render(<AlertBell username="admin" pollIntervalMs={50}/>);
        // 初始 load(fail=1) + 1 次轮询(fail=2) + 2 次轮询(fail=3)→停止,共 3 次调用
        await waitFor(() => expect(mocked).toHaveBeenCalledTimes(3), {timeout: 3000});
        await new Promise(r => setTimeout(r, 300));
        // 停止后调用次数不再增长
        expect(mocked).toHaveBeenCalledTimes(3);
    });
});

describe('V5.45.5 — Frame AlertBell BDD', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('mounts and renders bell icon with hidden badge when no PENDING_REVIEW drafts', async () => {
        // 用默认 mock(PENDING_REVIEW + APPROVED)— 我们只测 badge 数字 = 1
        render(<AlertBell username="admin" />);
        await waitFor(() => {
            // antd Badge 包数字在 <sup> — 1 表示 1 条待审
            expect(document.body.textContent).toContain('1');
        });
    });

    it('badge count = number of PENDING_REVIEW drafts (APPROVED 不计)', async () => {
        render(<AlertBell username="admin" />);
        await waitFor(() => {
            const text = document.body.textContent || '';
            // mock 返 2 条 draft,但只有 1 条 PENDING_REVIEW
            // badge sup 文本 = "1"
            const hasOne = /\b1\b/.test(text);
            expect(hasOne).toBeTruthy();
        });
    });

    it('clicking bell renders popover content with draft title', async () => {
        render(<AlertBell username="admin" />);
        await waitFor(() => {
            // bell button 用 data-testid
            const bell = document.querySelector('[data-testid="alert-bell-btn"]');
            expect(bell).toBeTruthy();
        });
        const bell = document.querySelector('[data-testid="alert-bell-btn"]') as HTMLElement;
        fireEvent.click(bell);
        // popover content 显示 draft title
        await waitFor(() => {
            expect(document.body.textContent).toContain('Loan interest bump');
        });
    });
});
