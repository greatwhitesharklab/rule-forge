import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, screen, waitFor, fireEvent} from '@testing-library/react';
import DraftsView from './DraftsView';

// Mock the API client
vi.mock('@/api/client', () => ({
    jsonPost: vi.fn(),
}));

import {jsonPost} from '@/api/client';
const mockJsonPost = jsonPost as unknown as ReturnType<typeof vi.fn>;

// V5.72: apiBase 改纯 Vite env,改用 vi.stubEnv mock VITE_API_BASE(替代 window._server)
beforeEach(() => {
    vi.stubEnv('VITE_API_BASE', 'http://test');
    (global as any).window.confirm = vi.fn(() => true);
    (global as any).window.prompt = vi.fn(() => 'test-package');
    (global as any).window.alert = vi.fn();
    mockJsonPost.mockReset();
});

afterEach(() => {
    vi.unstubAllEnvs();
});

describe('DraftsView (V5.22)', () => {
    it('should call list_drafts on mount and show results', async () => {
        // Given
        const mockDrafts = {
            count: 2,
            drafts: [
                {
                    draftId: 'drf_a', ruleType: 'decision_table', project: 'demo',
                    status: 'DRAFT', title: '年龄拒贷', source: 'LLM',
                    createdBy: 'user1', createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                    content: {type: 'decision_table', rows: []},
                },
                {
                    draftId: 'drf_b', ruleType: 'ul', project: 'demo',
                    status: 'PENDING_REVIEW', title: '收入门槛', source: 'LLM',
                    createdBy: 'user1', createdAt: '2026-06-09T11:00:00', updatedAt: '2026-06-09T11:00:00',
                    content: {type: 'ul', rules: []},
                },
            ]
        };
        mockJsonPost.mockResolvedValueOnce(mockDrafts);

        // When
        render(<DraftsView project="demo" username="BA1" />);

        // Then
        await waitFor(() => {
            expect(screen.getByText('年龄拒贷')).toBeDefined();
            expect(screen.getByText('收入门槛')).toBeDefined();
        });
        // Status badges — 出现 2 次(下拉 option + 列表 badge)
        expect(screen.getAllByText('草稿').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('待审批').length).toBeGreaterThanOrEqual(1);
        // API call
        expect(mockJsonPost).toHaveBeenCalledWith(
            '/agent/tools/list_drafts',
            expect.objectContaining({project: 'demo', limit: 50}),
            expect.objectContaining({silent: true})
        );
    });

    it('should show empty state when no drafts', async () => {
        mockJsonPost.mockResolvedValueOnce({count: 0, drafts: []});
        render(<DraftsView project="empty" />);
        await waitFor(() => {
            expect(screen.getByText(/暂无草稿/)).toBeDefined();
        });
    });

    it('should show error message on API failure', async () => {
        mockJsonPost.mockRejectedValueOnce(new Error('Network error'));
        render(<DraftsView project="demo" />);
        await waitFor(() => {
            expect(screen.getByText(/加载草稿失败/)).toBeDefined();
        });
    });

    it('should call submit_draft when clicking 提交审批 button on DRAFT', async () => {
        // Given — list returns one DRAFT with unique title
        mockJsonPost
            .mockResolvedValueOnce({
                count: 1,
                drafts: [{
                    draftId: 'drf_a', ruleType: 'decision_table', project: 'demo',
                    status: 'DRAFT', title: '年龄拒贷测试', source: 'LLM', createdBy: 'u',
                    createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                    content: {},
                }]
            })
            // click detail
            .mockResolvedValueOnce({
                draftId: 'drf_a', ruleType: 'decision_table', project: 'demo',
                status: 'DRAFT', title: '年龄拒贷测试', source: 'LLM', createdBy: 'u',
                createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                content: {type: 'decision_table', rows: []},
            })
            // submit
            .mockResolvedValueOnce({status: 'PENDING_REVIEW'});

        // When
        render(<DraftsView project="demo" username="BA1" />);
        await waitFor(() => screen.getByText('年龄拒贷测试'));
        // Click on draft to open detail
        fireEvent.click(screen.getByText('年龄拒贷测试'));
        await waitFor(() => screen.getByText(/提交审批/));
        fireEvent.click(screen.getByText(/提交审批/));

        // Then
        await waitFor(() => {
            const submitCall = mockJsonPost.mock.calls.find(
                c => c[0] === '/agent/tools/submit_draft'
            );
            expect(submitCall).toBeDefined();
            expect(submitCall[1]).toMatchObject({draftId: 'drf_a', submittedBy: 'BA1'});
        });
    });

    it('should switch to test cases tab and load them', async () => {
        // Given — list returns one DRAFT
        mockJsonPost
            .mockResolvedValueOnce({
                count: 1,
                drafts: [{
                    draftId: 'drf_t', ruleType: 'decision_table', project: 'demo',
                    status: 'DRAFT', title: '年龄测试', source: 'LLM', createdBy: 'u',
                    createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                    content: {},
                }]
            })
            // click detail
            .mockResolvedValueOnce({
                draftId: 'drf_t', ruleType: 'decision_table', project: 'demo',
                status: 'DRAFT', title: '年龄测试', source: 'LLM', createdBy: 'u',
                createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                content: {type: 'decision_table', rows: []},
            })
            // list_test_cases
            .mockResolvedValueOnce({
                count: 1,
                testCases: [{
                    testCaseId: 'tc_1', draftId: 'drf_t', name: 'under18',
                    description: '测试未成年', expectedRowId: 'r1',
                    createdBy: 'BA1', source: 'MANUAL',
                    createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                    inputs: {'customer.age': 17},
                }]
            });

        // When
        render(<DraftsView project="demo" username="BA1" />);
        await waitFor(() => screen.getByText('年龄测试'));
        fireEvent.click(screen.getByText('年龄测试'));
        // 切到"测试用例" tab
        await waitFor(() => screen.getByText(/测试用例/));
        fireEvent.click(screen.getByText(/测试用例/));

        // Then
        await waitFor(() => {
            expect(screen.getByText('under18')).toBeDefined();
            // 期望行 ID badge
            expect(screen.getByText(/期望 → r1/)).toBeDefined();
        });
        // API call
        const listTestsCall = mockJsonPost.mock.calls.find(
            c => c[0] === '/agent/tools/list_test_cases'
        );
        expect(listTestsCall).toBeDefined();
        expect(listTestsCall[1]).toMatchObject({draftId: 'drf_t'});
    });

    it('should call run_saved_tests when clicking 跑全部 saved tests', async () => {
        // Given — 1 draft, 1 test case
        mockJsonPost
            .mockResolvedValueOnce({
                count: 1,
                drafts: [{
                    draftId: 'drf_r', ruleType: 'decision_table', project: 'demo',
                    status: 'DRAFT', title: '跑测试', source: 'LLM', createdBy: 'u',
                    createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                    content: {},
                }]
            })
            .mockResolvedValueOnce({
                draftId: 'drf_r', ruleType: 'decision_table', project: 'demo',
                status: 'DRAFT', title: '跑测试', source: 'LLM', createdBy: 'u',
                createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                content: {type: 'decision_table', rows: []},
            })
            .mockResolvedValueOnce({
                count: 1,
                testCases: [{
                    testCaseId: 'tc_1', draftId: 'drf_r', name: 'tc1',
                    description: null, expectedRowId: 'r1',
                    createdBy: 'BA', source: 'MANUAL',
                    createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                    inputs: {x: 1},
                }]
            })
            // run_saved_tests
            .mockResolvedValueOnce({
                draftId: 'drf_r', passed: 1, failed: 0, total: 1,
                results: [{testCaseId: 'tc_1', name: 'tc1', expectedRowId: 'r1', matchedRowId: 'r1', status: 'PASS'}]
            });

        render(<DraftsView project="demo" username="BA1" />);
        await waitFor(() => screen.getByText('跑测试'));
        fireEvent.click(screen.getByText('跑测试'));
        await waitFor(() => screen.getByText(/测试用例/));
        fireEvent.click(screen.getByText(/测试用例/));
        await waitFor(() => screen.getByText('tc1'));
        fireEvent.click(screen.getByText(/跑全部 saved tests/));

        // Then
        await waitFor(() => {
            const runCall = mockJsonPost.mock.calls.find(
                c => c[0] === '/agent/tools/run_saved_tests'
            );
            expect(runCall).toBeDefined();
            expect(runCall[1]).toMatchObject({draftId: 'drf_r'});
        });
    });
});
