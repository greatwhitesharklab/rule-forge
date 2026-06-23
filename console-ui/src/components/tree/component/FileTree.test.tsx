import {describe, it, expect, beforeEach, afterEach, vi} from 'vitest';
import {render, fireEvent, screen} from '@testing-library/react';
import {Provider} from 'react-redux';
import {createStore} from 'redux';
import FileTree from './FileTree';

/** 构造测试 store:reducer 直接返回固定 state(connect 的 mapStateToProps 读 state.data)。 */
function makeStore(data: TreeNodeData | null) {
    return createStore(() => ({data, publicResource: null}) as unknown);
}

/** 含项目 + 文件 + contextMenu 的样本树(projA _level=1 < expandLevel 3 → 初始展开,loan 可见)。 */
const sampleData = {
    id: 'root', name: 'root', type: 'root', fullPath: '/r',
    children: [
        {
            id: 'p', name: 'projA', type: 'project', fullPath: '/r/p', _level: 1, _icon: 'rf rf-project',
            contextMenu: [{name: '删除项目', icon: 'rf rf-remove'}],
            children: [
                {id: 'f', name: 'loan.rs.xml', type: 'rule', fullPath: '/r/p/loan.rs.xml', _level: 2, _icon: 'rf rf-rule'},
            ],
        },
    ],
} as unknown as TreeNodeData;

describe('FileTree', () => {
    let openSpy: ReturnType<typeof vi.spyOn>;
    beforeEach(() => {
        openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    });
    afterEach(() => openSpy.mockRestore());

    it('渲染节点(projA _level<expandLevel 初始展开 → loan 可见)', () => {
        render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        expect(screen.getByText('projA')).toBeTruthy();
        expect(screen.getByText('loan.rs.xml')).toBeTruthy();
    });

    it('data=null → 不渲染节点(不崩)', () => {
        render(<Provider store={makeStore(null)}><FileTree/></Provider>);
        expect(screen.queryByText('projA')).toBeNull();
    });

    it('搜索过滤:匹配 loan → loan 仍渲染', () => {
        const {container} = render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: 'loan'}});
        // highlight 把命中拆 <mark>,用 textContent 而非 getByText 精确匹配
        expect(container.textContent).toContain('loan.rs.xml');
    });

    it('搜索命中高亮 <mark>', () => {
        const {container} = render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: 'loan'}});
        const mark = container.querySelector('mark.rf-tree-match');
        expect(mark).not.toBeNull();
        expect(mark?.textContent).toBe('loan');
    });

    it('点文件 → window.open ruleset 编辑器(onSelect→handleFileOpen)', () => {
        render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        fireEvent.click(screen.getByText('loan.rs.xml'));
        expect(openSpy).toHaveBeenCalledWith(
            expect.stringContaining('/app/editor/ruleset'),
            '_blank',
        );
    });

    it('正常模式:contextMenu 节点有 ⋯ 操作按钮(FileTreeNode)', () => {
        render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        // projA 有 contextMenu → FileTreeNode 渲染 ⋯ 按钮(aria-label="节点操作")
        expect(screen.getByRole('button', {name: '节点操作'})).toBeTruthy();
    });

    it('readOnly 模式:无 ⋯ 按钮(禁编辑入口)', () => {
        render(<Provider store={makeStore(sampleData)}><FileTree readOnly/></Provider>);
        expect(screen.queryByRole('button', {name: '节点操作'})).toBeNull();
    });

    it('readOnly 模式:点文件 → 走 onFileReadOnlyClick 回调,不开窗', () => {
        const onReadOnly = vi.fn();
        render(<Provider store={makeStore(sampleData)}><FileTree readOnly onFileReadOnlyClick={onReadOnly}/></Provider>);
        fireEvent.click(screen.getByText('loan.rs.xml'));
        expect(onReadOnly).toHaveBeenCalled();
        expect(openSpy).not.toHaveBeenCalled();
    });

    it('搜索展开祖先:匹配深层文件 → projA 仍可见', () => {
        render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: 'loan.rs.xml'}});
        // projA 是 loan 的祖先,搜索展开祖先后 projA 仍渲染
        expect(screen.getByText('projA')).toBeTruthy();
    });
});
