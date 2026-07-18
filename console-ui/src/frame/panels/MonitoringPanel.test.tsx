/**
 * UX-B3 — MonitoringPanel vitest BDD。
 *
 * <p>锁 2 件事:
 * <ol>
 *   <li>默认总览 tab:三个指标以 Statistic 卡片呈现(P95 延迟 / 成功率 / 活跃告警),
 *       不再是沉到左下角的纯文本</li>
 *   <li>切 tab:dispatch setMonitoringTab,内容区切换到对应占位空态</li>
 * </ol>
 */
import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';
import reducer from '@/frame/reducer.js';
import MonitoringPanel from './MonitoringPanel.tsx';

function renderPanel() {
    const store = createStore(reducer);
    return render(
        <Provider store={store}>
            <MonitoringPanel/>
        </Provider>,
    );
}

describe('MonitoringPanel (UX-B3)', () => {
    // -----------------------------------------------------------------------
    // Scenario 1: 默认总览 tab — 三个 Statistic 指标卡
    // -----------------------------------------------------------------------
    it('Given 打开监控面板, When 默认总览 tab, Then 三张指标卡(P95 延迟/成功率/活跃告警)', () => {
        // When
        renderPanel();

        // Then — 指标卡在 overview 容器里,不再是纯文本堆叠
        const overview = screen.getByTestId('monitoring-overview');
        expect(overview.textContent).toContain('P95 延迟');
        expect(overview.textContent).toContain('成功率');
        expect(overview.textContent).toContain('活跃告警');
        // 三个 tab 都在
        expect(screen.getByText('总览仪表盘')).toBeTruthy();
        expect(screen.getByText('指标浏览')).toBeTruthy();
        expect(screen.getByText('告警规则')).toBeTruthy();
    });

    // -----------------------------------------------------------------------
    // Scenario 2: 切 tab — dispatch + 内容区切换
    // -----------------------------------------------------------------------
    it('Given 总览 tab, When 点 "指标浏览", Then 内容区显示指标占位空态', () => {
        // Given
        renderPanel();

        // When
        fireEvent.click(screen.getByText('指标浏览'));

        // Then
        expect(screen.getByText('指标数据接入中')).toBeTruthy();
    });
});
