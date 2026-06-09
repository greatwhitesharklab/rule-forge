import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, screen, waitFor} from '@testing-library/react';
import AgentAuditView from './AgentAuditView';

vi.mock('@/api/client', () => ({
    jsonPost: vi.fn(),
}));

import {jsonPost} from '@/api/client';
const mockJsonPost = jsonPost as unknown as ReturnType<typeof vi.fn>;

beforeEach(() => {
    (global as any).window._server = 'http://test';
    mockJsonPost.mockReset();
});

describe('AgentAuditView (V5.22.3)', () => {
    it('should load audit records and display them', async () => {
        mockJsonPost.mockResolvedValueOnce({
            audits: [
                {
                    id: 1, sessionId: 'sess_1', messageId: 'msg_1',
                    userId: 'BA1', toolName: 'draft_rule',
                    argsSummary: '{"ruleType":"decision_table"}',
                    resultSize: 16, status: 'OK',
                    errorCode: null, errorMessage: null,
                    durationMs: 42,
                    at: '2026-06-09T10:00:00Z',
                },
                {
                    id: 2, sessionId: 'sess_1', messageId: 'msg_2',
                    userId: 'BA1', toolName: 'list_drafts',
                    argsSummary: '{"project":"demo"}',
                    resultSize: 100, status: 'ERROR',
                    errorCode: 'tool_execution_failed', errorMessage: 'boom',
                    durationMs: 5,
                    at: '2026-06-09T10:01:00Z',
                },
            ],
            count: 2,
        });

        render(<AgentAuditView username="BA1" />);

        await waitFor(() => {
            // 工具名
            expect(screen.getByText('draft_rule')).toBeDefined();
            expect(screen.getByText('list_drafts')).toBeDefined();
            // 用户
            expect(screen.getAllByText(/by BA1/).length).toBeGreaterThan(0);
            // 状态 tag
            expect(screen.getAllByText('OK').length).toBeGreaterThan(0);
            expect(screen.getAllByText('ERROR').length).toBeGreaterThan(0);
            // 错误信息
            expect(screen.getByText(/tool_execution_failed/)).toBeDefined();
        });

        // 记录数
        expect(screen.getByText(/2 条记录/)).toBeDefined();
    });

    it('should show empty state when no audits', async () => {
        mockJsonPost.mockResolvedValueOnce({audits: [], count: 0});
        render(<AgentAuditView username="nobody" />);

        await waitFor(() => {
            expect(screen.getByText(/暂无调用记录/)).toBeDefined();
        });
    });

    it('should pass userId to filter when scope is me', async () => {
        mockJsonPost.mockResolvedValueOnce({audits: [], count: 0});
        render(<AgentAuditView username="BA1" />);

        await waitFor(() => {
            expect(mockJsonPost).toHaveBeenCalledWith(
                '/agent/tools/list_agent_audit',
                expect.objectContaining({userId: 'BA1', limit: 50}),
                expect.objectContaining({silent: true})
            );
        });
    });
});
