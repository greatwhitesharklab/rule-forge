// V5.17 audit log 面板 — Vitest 单元测试
//
// Feature: V5.17 用户/权限操作审计日志
//
//   As an admin
//   I want to see a table of all user/permission operations
//   So that I can trace who did what and when
//
// Pattern 跟 src/frame/panels/GitStatusPanel.test.tsx 一致:
//   1) vi.hoisted 提供 mock 状态 + mock 函数
//   2) vi.mock('@/api/client.js') 替换整个 client 模块
//   3) Provider + createStore(reducer) 渲染
//   4) screen.getByTestId / fireEvent / waitFor 断言
//
// data-testid 约定(跟组件实现配套):
//   audit-log-table        — Antd Table 根
//   audit-log-row-{id}     — TableRow 用 rowKey={row.id}
//   audit-log-loading      — loading 中显示
//   audit-log-error        — 错误信息
//   audit-log-actor-input  — actor 过滤输入框
//   audit-log-action-select— action 过滤下拉
//   audit-log-refresh      — 刷新按钮
//   audit-log-drawer       — 详情 Drawer
//   audit-log-drawer-note  — Drawer 里的 note 字段
//   audit-log-drawer-close — Drawer 关闭按钮

import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {Provider} from 'react-redux';
import {createEditorStore} from '../../store/createEditorStore';
import reducer from '@/frame/reducer.js';
import {AuditLogPanel} from './AuditLogPanel.tsx';
import {getAuditLogs} from '@/api/client.js';

vi.mock('@/api/client.js', () => ({
    getAuditLogs: vi.fn(),
}));

const mockedGetAuditLogs = vi.mocked(getAuditLogs);

function renderPanel() {
    const store = createEditorStore(reducer);
    return render(
        <Provider store={store}>
            <AuditLogPanel/>
        </Provider>,
    );
}

const sampleRows = [
    {
        id: 101,
        occurredAt: '2026-06-07T12:00:00.000',
        actor: 'admin',
        action: 'CREATE_USER',
        targetUserId: 5,
        targetUsername: 'newuser',
        fieldName: null,
        oldValue: null,
        newValue: null,
        project: null,
        note: null,
    },
    {
        id: 102,
        occurredAt: '2026-06-07T12:05:00.000',
        actor: 'admin',
        action: 'TOGGLE_ENABLED',
        targetUserId: 5,
        targetUsername: 'newuser',
        fieldName: 'is_enabled',
        oldValue: 'true',
        newValue: 'false',
        project: null,
        note: null,
    },
];

describe('AuditLogPanel - V5.17 audit log 列表 + 过滤 + 详情', () => {
    beforeEach(() => {
        mockedGetAuditLogs.mockReset();
        mockedGetAuditLogs.mockResolvedValue(sampleRows);
    });

    // -----------------------------------------------------------------------
    // Scenario 1: 列表渲染
    // -----------------------------------------------------------------------
    describe('Scenario 1: 列表渲染', () => {
        it('Given API 返 2 行 / When mount / Then 表格显示 2 行 + actor/action/target 列', async () => {
            // Given
            mockedGetAuditLogs.mockResolvedValue(sampleRows);

            // When
            renderPanel();

            // Then
            await waitFor(() => {
                expect(screen.getByTestId('audit-log-table')).toBeInTheDocument();
            });
            // 行用 rowKey=row.id,testId={audit-log-row-${id}}
            expect(screen.getByTestId('audit-log-row-101')).toBeInTheDocument();
            expect(screen.getByTestId('audit-log-row-102')).toBeInTheDocument();
            // 列内容
            expect(screen.getByText('CREATE_USER')).toBeInTheDocument();
            expect(screen.getByText('TOGGLE_ENABLED')).toBeInTheDocument();
            expect(screen.getAllByText('admin').length).toBeGreaterThan(0);
            expect(screen.getAllByText('newuser').length).toBeGreaterThan(0);
        });
    });

    // -----------------------------------------------------------------------
    // Scenario 2: 加载中
    // -----------------------------------------------------------------------
    describe('Scenario 2: 加载中', () => {
        it('Given API 不 resolve / When mount / Then 显示 loading', async () => {
            // Given — 一个永挂起的 Promise
            mockedGetAuditLogs.mockReturnValue(new Promise(() => {}));

            // When
            renderPanel();

            // Then
            expect(await screen.findByTestId('audit-log-loading')).toBeInTheDocument();
        });
    });

    // -----------------------------------------------------------------------
    // Scenario 3: actor 过滤
    // -----------------------------------------------------------------------
    describe('Scenario 3: actor 过滤', () => {
        it('Given 输入框 / When 改值 + click 刷新 / Then API 收到新 actor', async () => {
            // Given
            renderPanel();
            await waitFor(() => screen.getByTestId('audit-log-table'));

            // When
            const input = screen.getByTestId('audit-log-actor-input');
            fireEvent.change(input, {target: {value: 'alice'}});
            fireEvent.click(screen.getByTestId('audit-log-refresh'));

            // Then — 最后一次调用应带 actor='alice'(非 null)
            await waitFor(() => {
                const calls = mockedGetAuditLogs.mock.calls;
                const last = calls[calls.length - 1];
                expect(last[0]).toBe('alice');
            });
        });
    });

    // -----------------------------------------------------------------------
    // Scenario 4: action 过滤
    // -----------------------------------------------------------------------
    describe('Scenario 4: action 过滤', () => {
        it('Given 下拉 / When 选 CREATE_USER / Then API 收到 action=CREATE_USER', async () => {
            // Given
            renderPanel();
            await waitFor(() => screen.getByTestId('audit-log-table'));

            // When — Antd Select 的 data-testid 在 wrapper div 上,内部 .ant-select
            // 是真实交互元素。mouseDown wrapper 触发 dropdown 打开。
            const selectWrapper = screen.getByTestId('audit-log-action-select');
            fireEvent.mouseDown(selectWrapper);
            await waitFor(() => {
                expect(document.querySelector('.ant-select-item-option'))
                    .toBeInTheDocument();
            });
            // 找到含 "创建用户" 的 option 点击(value=CREATE_USER)
            const option = Array.from(document.querySelectorAll('.ant-select-item-option'))
                .find((el) => el.textContent === '创建用户') as HTMLElement;
            expect(option).toBeTruthy();
            fireEvent.click(option);
            fireEvent.click(screen.getByTestId('audit-log-refresh'));

            // Then
            await waitFor(() => {
                const calls = mockedGetAuditLogs.mock.calls;
                const last = calls[calls.length - 1];
                expect(last[1]).toBe('CREATE_USER');
            });
        });
    });

    // -----------------------------------------------------------------------
    // Scenario 5: 点行 → 详情 Drawer
    // -----------------------------------------------------------------------
    describe('Scenario 5: 点行 → 详情 Drawer', () => {
        it('Given 1 行 / When click 行 / Then Drawer 显示完整 row (含 note)', async () => {
            // Given — 1 行带 note 的 row
            mockedGetAuditLogs.mockResolvedValue([
                {
                    ...sampleRows[0],
                    id: 200,
                    action: 'RESET_PASSWORD',
                    fieldName: 'password',
                    note: 'admin reset password (hash not stored)',
                },
            ]);
            renderPanel();
            await waitFor(() => screen.getByTestId('audit-log-row-200'));

            // When
            fireEvent.click(screen.getByTestId('audit-log-row-200'));

            // Then — Drawer 打开,note 出现
            await waitFor(() => {
                const drawer = screen.getByTestId('audit-log-drawer');
                expect(drawer).toBeInTheDocument();
                expect(drawer.textContent).toContain('admin reset password');
                expect(drawer.textContent).toContain('RESET_PASSWORD');
            });
        });
    });

    // -----------------------------------------------------------------------
    // Scenario 6: Drawer 关闭
    // -----------------------------------------------------------------------
    describe('Scenario 6: Drawer 关闭', () => {
        it('Given Drawer 打开 / When click 关闭 / Then Drawer 消失', async () => {
            // Given
            mockedGetAuditLogs.mockResolvedValue([sampleRows[0]]);
            renderPanel();
            await waitFor(() => screen.getByTestId('audit-log-row-101'));
            fireEvent.click(screen.getByTestId('audit-log-row-101'));
            // 确认 Drawer 打开(看到 note 字段)
            await waitFor(() => screen.getByTestId('audit-log-drawer-note'));

            // When — 用 Antd Drawer 自带的 X 关闭按钮(aria-label="Close")
            // 比 extra slot 里的 button 更稳。两者都调 onClose。
            const closeBtn = document.querySelector(
                'button.ant-drawer-close',
            ) as HTMLElement;
            fireEvent.click(closeBtn);

            // Then — 内部 note 字段消失(Drawer 容器本身因动画可能残留)
            await waitFor(() => {
                expect(screen.queryByTestId('audit-log-drawer-note'))
                    .not.toBeInTheDocument();
            });
        });
    });

    // -----------------------------------------------------------------------
    // Scenario 7: 错误处理
    // -----------------------------------------------------------------------
    describe('Scenario 7: 错误处理', () => {
        it('Given API 抛 / When mount / Then 显示错误信息', async () => {
            // Given
            mockedGetAuditLogs.mockRejectedValue(new Error('network down'));

            // When
            renderPanel();

            // Then
            await waitFor(() => {
                const err = screen.getByTestId('audit-log-error');
                expect(err).toBeInTheDocument();
                expect(err.textContent).toContain('network down');
            });
        });
    });
});
