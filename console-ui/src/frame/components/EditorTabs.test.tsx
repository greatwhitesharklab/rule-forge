import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, screen, fireEvent, act} from '@testing-library/react';
import EditorTabs from './EditorTabs';
import {openEditorTab, eventEmitter, OPEN_EDITOR_TAB} from '@/frame/event';

/**
 * EditorTabs 宿主单测:openEditorTab → 标签出现 / 重复打开激活 / 关闭卸载 / 无标签 QuickStart。
 *
 * editorRegistry 导入 11 个真实编辑器(重依赖:xyflow/redux/后端请求),这里 mock 成
 * 轻量桩组件 —— 测的是宿主的标签管理逻辑,不是编辑器本体。
 */
vi.mock('@/frame/editorRegistry', () => ({
    EDITOR_REGISTRY: {
        drl: {render: (file?: string) => <div data-testid={'editor-drl-' + file}>DRL:{file}</div>},
        v1ruleset: {render: (file?: string) => <div data-testid={'editor-rs-' + file}>RS:{file}</div>},
        permission: {render: () => <div data-testid="editor-permission">PERMISSION</div>},
    },
}));

/** 在 act 中发 openEditorTab(事件总线 → setState,需包 act 防警告)。 */
function open(payload: {editorType: string; file?: string; label?: string}) {
    act(() => openEditorTab(payload));
}

describe('EditorTabs', () => {
    beforeEach(() => {
        // 防御:用例间无残留监听(组件 unmount 会 removeListener,这里双保险)
        eventEmitter.removeAllListeners(OPEN_EDITOR_TAB);
    });

    // Given 未打开任何标签 When 渲染宿主 Then 显示 QuickStart 欢迎页
    it('无标签 → 渲染 QuickStart 欢迎页', () => {
        render(<EditorTabs/>);
        expect(screen.getByText('欢迎使用 RuleForge 决策平台')).toBeTruthy();
    });

    // Given 宿主已渲染 When openEditorTab(drl 文件) Then 出现对应标签且编辑器内容可见
    it('openEditorTab → 标签出现,编辑器内容可见(label 取 file 末段)', () => {
        render(<EditorTabs/>);
        open({editorType: 'drl', file: '/proj/loan.drl'});
        expect(screen.getByText('loan.drl')).toBeTruthy();
        expect(screen.getByTestId('editor-drl-/proj/loan.drl')).toBeTruthy();
        // QuickStart 让位
        expect(screen.queryByText('欢迎使用 RuleForge 决策平台')).toBeNull();
    });

    // Given 已打开某文件 When 再次 openEditorTab 同 key Then 不新增标签,只激活
    it('重复打开同 key → 不新增标签,保持激活', () => {
        const {container} = render(<EditorTabs/>);
        open({editorType: 'drl', file: '/proj/a.drl'});
        open({editorType: 'drl', file: '/proj/a.drl'});
        expect(container.querySelectorAll('.ant-tabs-tab').length).toBe(1);
    });

    // Given 打开两个文件 Then 全部保持挂载(保活),非激活的 display:none
    it('保活:多个编辑器全部挂载,非激活 pane display:none', () => {
        const {container} = render(<EditorTabs/>);
        open({editorType: 'drl', file: '/proj/a.drl'});
        open({editorType: 'drl', file: '/proj/b.drl'});
        // 两个编辑器都在 DOM 中(未卸载)
        expect(screen.getByTestId('editor-drl-/proj/a.drl')).toBeTruthy();
        expect(screen.getByTestId('editor-drl-/proj/b.drl')).toBeTruthy();
        // 后开的 b 激活,a 的 pane 隐藏但保留
        const panes = container.querySelectorAll('.editor-tab-pane');
        expect(panes.length).toBe(2);
        expect((panes[0] as HTMLElement).style.display).toBe('none');
        expect((panes[1] as HTMLElement).style.display).toBe('');
        // 再激活 a → a 显示 b 隐藏,仍都挂载
        open({editorType: 'drl', file: '/proj/a.drl'});
        expect((panes[0] as HTMLElement).style.display).toBe('');
        expect((panes[1] as HTMLElement).style.display).toBe('none');
        expect(screen.getByTestId('editor-drl-/proj/b.drl')).toBeTruthy();
    });

    // Given 已打开标签 When 点关闭按钮 Then 编辑器卸载;关完全部回到 QuickStart
    it('关闭标签 → 编辑器卸载;全部关闭回到 QuickStart', () => {
        const {container} = render(<EditorTabs/>);
        open({editorType: 'drl', file: '/proj/a.drl'});
        open({editorType: 'drl', file: '/proj/b.drl'});
        // 关激活的 b → 回退激活相邻的 a
        const closeBtns = () => container.querySelectorAll('.ant-tabs-tab-remove');
        fireEvent.click(closeBtns()[1]);
        expect(screen.queryByTestId('editor-drl-/proj/b.drl')).toBeNull();
        expect(screen.getByTestId('editor-drl-/proj/a.drl')).toBeTruthy();
        const pane = container.querySelector('.editor-tab-pane') as HTMLElement;
        expect(pane.style.display).toBe('');
        // 关掉最后一个 → QuickStart 回归
        fireEvent.click(container.querySelector('.ant-tabs-tab-remove')!);
        expect(screen.queryByTestId('editor-drl-/proj/a.drl')).toBeNull();
        expect(screen.getByText('欢迎使用 RuleForge 决策平台')).toBeTruthy();
    });

    // Given permission 全局单例 When openEditorTab 无 file Then label 用固定名
    it('permission 无 file → 固定标签名「权限配置」', () => {
        render(<EditorTabs/>);
        open({editorType: 'permission'});
        expect(screen.getByText('权限配置')).toBeTruthy();
        expect(screen.getByTestId('editor-permission')).toBeTruthy();
    });

    // Given 历史版本 file 带 ':版本号' When openEditorTab Then 原样透传给编辑器组件
    it("历史版本 file 带 ':版本号' 后缀 → 原样透传,label 带版本号", () => {
        render(<EditorTabs/>);
        open({editorType: 'drl', file: '/proj/loan.drl:LATEST'});
        expect(screen.getByText('loan.drl:LATEST')).toBeTruthy();
        expect(screen.getByTestId('editor-drl-/proj/loan.drl:LATEST')).toBeTruthy();
    });

    // Given 未知 editorType When openEditorTab Then 忽略(不开标签)
    it('未知 editorType → 忽略,不开标签', () => {
        render(<EditorTabs/>);
        open({editorType: 'not-exist', file: '/p/x'});
        expect(screen.getByText('欢迎使用 RuleForge 决策平台')).toBeTruthy();
    });
});
