/**
 * UX-B3 — RuleEditorPanel 项目选择器下拉:导入项目入口。
 *
 * <p>背景:ImportProjectDialog 早已做好(挂在 ComponentContainer,事件驱动),
 * 但唯一入口在"项目列表"根节点右键菜单,而文件树渲染跳过根节点 → UI 不可达。
 * 现与"创建新项目"同位置(项目选择器下拉)露出。
 *
 * <p>锁 2 件事:
 * <ol>
 *   <li>下拉里有"导入项目"项(与"创建新项目"同组)</li>
 *   <li>点击 → emit OPEN_IMPORT_PROJECT_DIALOG + 收起下拉</li>
 * </ol>
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

// FileTreePanel 依赖重(文件树 / thunk 链),本测试只关心项目选择器下拉,stub 掉
vi.mock('@/frame/components/FileTreePanel.jsx', () => ({
    default: () => <div data-testid="mock-filetree"/>,
}));

// loadData 是 thunk(会打网络),stub 成空 thunk;setProjectName 保留 plain action 形状
vi.mock('@/frame/action.js', () => ({
    loadData: vi.fn(() => () => {}),
    setProjectName: (name: string) => ({type: 'set_project_name', name}),
}));

import RuleEditorPanel from './RuleEditorPanel';
import { OPEN_IMPORT_PROJECT_DIALOG } from '@/frame/event.js';

const {emitSpy} = vi.hoisted(() => ({emitSpy: vi.fn()}));

function renderPanel() {
    const store = {dispatch: vi.fn(), getState: () => ({}), subscribe: vi.fn()} as any;
    const eventObj = {
        eventEmitter: {on: vi.fn(), emit: emitSpy},
        PROJECT_LIST_CHANGE: 'project_list_change',
        PROJECT_FILTER_CHANGE: 'project_filter_change',
    } as any;
    return render(<RuleEditorPanel store={store} eventObj={eventObj}/>);
}

describe('RuleEditorPanel 项目选择器 (UX-B3)', () => {
    beforeEach(() => {
        emitSpy.mockClear();
    });

    it('GIVEN 项目选择器, WHEN 点开下拉, THEN 同时有 "创建新项目" 和 "导入项目"', () => {
        // Given
        renderPanel();

        // When — 点开项目选择器下拉
        fireEvent.click(document.querySelector('.panel-project-btn') as HTMLElement);

        // Then
        expect(screen.getByText('创建新项目')).toBeTruthy();
        expect(screen.getByText('导入项目')).toBeTruthy();
    });

    it('GIVEN 下拉已开, WHEN 点 "导入项目", THEN emit OPEN_IMPORT_PROJECT_DIALOG 且下拉收起', () => {
        // Given
        renderPanel();
        fireEvent.click(document.querySelector('.panel-project-btn') as HTMLElement);

        // When
        fireEvent.click(screen.getByText('导入项目'));

        // Then — 复用既有 ImportProjectDialog 的打开事件,且下拉关闭
        expect(emitSpy).toHaveBeenCalledWith(OPEN_IMPORT_PROJECT_DIALOG);
        expect(screen.queryByText('导入项目')).toBeNull();
    });
});
