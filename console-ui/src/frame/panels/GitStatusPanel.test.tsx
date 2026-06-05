import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';
import reducer from '@/frame/reducer.js';
import GitStatusPanel from './GitStatusPanel.tsx';

// Mock the API client so we don't touch the network.
const { getSummaryMock, getRecentMock, setSummary, setRecent, setHttpError } = vi.hoisted(() => {
    const state: { summary: any; recent: any[]; httpError?: Error } = {
        summary: { totalFailures: 0, last1h: 0, last24h: 0, counters: {} },
        recent: [],
    };
    return {
        getSummaryMock: vi.fn(() => {
            if (state.httpError) return Promise.reject(state.httpError);
            return Promise.resolve(state.summary);
        }),
        getRecentMock: vi.fn(() => {
            if (state.httpError) return Promise.reject(state.httpError);
            return Promise.resolve(state.recent);
        }),
        setSummary: (s: any) => { state.summary = s; },
        setRecent: (r: any[]) => { state.recent = r; },
        setHttpError: (e: Error | undefined) => { state.httpError = e; },
    };
});

vi.mock('@/api/client.js', () => ({
    getGitStatusSummary: getSummaryMock,
    getGitStatusRecent: getRecentMock,
}));

function renderPanel() {
    const store = createStore(reducer);
    return render(
        <Provider store={store}>
            <GitStatusPanel/>
        </Provider>,
    );
}

describe('GitStatusPanel (5.10-D)', () => {
    beforeEach(() => {
        getSummaryMock.mockClear();
        getRecentMock.mockClear();
        setHttpError(undefined);
        setSummary({ totalFailures: 0, last1h: 0, last24h: 0, counters: {} });
        setRecent([]);
    });

    // -----------------------------------------------------------------------
    // Scenario 1: summary tab — 显示 total / last1h / last24h
    // -----------------------------------------------------------------------
    describe('Scenario 1: summary tab', () => {
        it('Given summary={total=123, last1h=7, last24h=42}, '
            + 'When mount + 等待加载, '
            + 'Then 看到 "总失败 123"、"近 1h 7"、"近 24h 42"', async () => {
            // Given
            setSummary({ totalFailures: 123, last1h: 7, last24h: 42, counters: {} });

            // When
            renderPanel();
            await waitFor(() => {
                const total = screen.getByTestId('git-status-total');
                expect(total.textContent).toBe('123');
            });

            // Then
            expect(screen.getByTestId('git-status-total').textContent).toBe('123');
            expect(screen.getByTestId('git-status-last1h').textContent).toBe('7');
            expect(screen.getByTestId('git-status-last24h').textContent).toBe('42');
        });
    });

    // -----------------------------------------------------------------------
    // Scenario 2: recent tab — 列出最近 N 条失败
    // -----------------------------------------------------------------------
    describe('Scenario 2: recent tab', () => {
        it('Given recent 有 3 条, '
            + 'When 切到 "最近失败" tab, '
            + 'Then 3 行都渲染,errorType 都对', async () => {
            // Given
            setRecent([
                {
                    id: 1, filePath: '/p/a.xml', projectId: 1, fileId: 10,
                    errorType: 'GitOperationException', errorMessage: 'boom',
                    branch: 'user/alice', occurredAt: '2026-06-05T10:00:00Z',
                },
                {
                    id: 2, filePath: '/p/b.xml', projectId: 2, fileId: 20,
                    errorType: 'IOException', errorMessage: 'io',
                    branch: 'main', occurredAt: '2026-06-05T10:01:00Z',
                },
                {
                    id: 3, filePath: '/p/c.xml', projectId: 1, fileId: 30,
                    errorType: 'GitOperationException', errorMessage: 'boom2',
                    branch: 'main', occurredAt: '2026-06-05T10:02:00Z',
                },
            ]);

            // When
            renderPanel();
            await waitFor(() => {
                expect(screen.getByTestId('git-status-total').textContent).toBe('0');
            });
            const recentTab = screen.getByText(/最近失败/);
            fireEvent.click(recentTab);

            // Then
            await waitFor(() => screen.getByText(/p\/a\.xml/));
            expect(screen.getByText(/p\/a\.xml/)).toBeTruthy();
            expect(screen.getByText(/p\/b\.xml/)).toBeTruthy();
            expect(screen.getByText(/p\/c\.xml/)).toBeTruthy();
            expect(screen.getAllByText(/GitOperationException/).length).toBeGreaterThanOrEqual(2);
            expect(screen.getByText(/IOException/)).toBeTruthy();
        });
    });

    // -----------------------------------------------------------------------
    // Scenario 3: empty — 没失败时显示 "无失败"
    // -----------------------------------------------------------------------
    describe('Scenario 3: empty', () => {
        it('Given summary.totalFailures=0 + recent=[], '
            + 'When mount + 切到 recent, '
            + 'Then 显示 "无失败" 或类似空态文案', async () => {
            // Given
            setSummary({ totalFailures: 0, last1h: 0, last24h: 0, counters: {} });
            setRecent([]);

            // When
            renderPanel();
            await waitFor(() => screen.getByText(/0/));
            const recentTab = screen.getByText(/最近失败/);
            fireEvent.click(recentTab);

            // Then
            await waitFor(() => {
                const emptyTexts = ['无失败', '暂无', '没有', 'empty', 'No failures'];
                const found = emptyTexts.some(t => screen.queryByText(new RegExp(t, 'i')));
                expect(found).toBe(true);
            });
        });
    });

    // -----------------------------------------------------------------------
    // Scenario 4: 401 — 非 admin 走 httpGet → 显示错误状态
    // -----------------------------------------------------------------------
    describe('Scenario 4: 401 / non-admin', () => {
        it('Given httpGet 抛 401, When mount, '
            + 'Then 不崩,显示 "无权限" 或 "加载失败" 之类错误提示', async () => {
            // Given
            setHttpError(new Error('HTTP 401 Unauthorized'));

            // When
            renderPanel();

            // Then — 不崩,最终落到错误展示 (data-testid 锁定)
            await waitFor(() => {
                const errEl = screen.getByTestId('git-status-error');
                expect(errEl.textContent).toMatch(/401|加载失败/);
            });
        });
    });
});
