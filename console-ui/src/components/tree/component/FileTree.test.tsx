import {describe, it, expect, beforeEach, afterEach, vi} from 'vitest';
import {render, fireEvent, screen, waitFor} from '@testing-library/react';
import {Provider} from 'react-redux';
import {createStore, applyMiddleware} from 'redux';
import thunk from 'redux-thunk';
import FileTree from './FileTree';

/** 构造测试 store:reducer 直接返回固定 state(connect 的 mapStateToProps 读 state.data)。
 *  生产 FileTree 在 antd Tree mount 时会调 loadData 触发 dispatch(thunk),所以测试 store 加 thunk middleware。 */
function makeStore(data: TreeNodeData | null) {
    return createStore(() => ({data, publicResource: null}) as unknown, applyMiddleware(thunk));
}

/** 含项目 + 文件 + contextMenu 的样本树(projA _level=1 < expandLevel 3 → 初始展开,loan 可见)。
 *  V6.20.0 P2:老 .rs.xml (rule) → DRL .drl (drl)。 */
const sampleData = {
    id: 'root', name: 'root', type: 'root', fullPath: '/r',
    children: [
        {
            id: 'p', name: 'projA', type: 'project', fullPath: '/r/p', _level: 1, _icon: 'rf rf-project',
            contextMenu: [{name: '删除项目', icon: 'rf rf-remove'}],
            children: [
                {id: 'f', name: 'loan.drl', type: 'drl', fullPath: '/r/p/loan.drl', _level: 2, _icon: 'rf rf-rule'},
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
        expect(screen.getByText('loan.drl')).toBeTruthy();
    });

    it('data=null → 不渲染节点(不崩)', () => {
        render(<Provider store={makeStore(null)}><FileTree/></Provider>);
        expect(screen.queryByText('projA')).toBeNull();
    });

    it('搜索过滤:匹配 loan → loan 仍渲染', () => {
        const {container} = render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: 'loan'}});
        // highlight 把命中拆 <mark>,用 textContent 而非 getByText 精确匹配
        expect(container.textContent).toContain('loan.drl');
    });

    it('搜索命中高亮 <mark>', () => {
        const {container} = render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: 'loan'}});
        const mark = container.querySelector('mark.rf-tree-match');
        expect(mark).not.toBeNull();
        expect(mark?.textContent).toBe('loan');
    });

    // V6.20.0 P2:点 .drl 文件 → /app/editor/drl(老 /app/editor/ruleset 已删)
    it('点文件 → window.open DRL 编辑器(onSelect→handleFileOpen)', () => {
        render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        fireEvent.click(screen.getByText('loan.drl'));
        expect(openSpy).toHaveBeenCalledWith(
            expect.stringContaining('/app/editor/drl'),
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
        fireEvent.click(screen.getByText('loan.drl'));
        expect(onReadOnly).toHaveBeenCalled();
        expect(openSpy).not.toHaveBeenCalled();
    });

    it('搜索展开祖先:匹配深层文件 → projA 仍可见', () => {
        render(<Provider store={makeStore(sampleData)}><FileTree/></Provider>);
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: 'loan.drl'}});
        // projA 是 loan 的祖先,搜索展开祖先后 projA 仍渲染
        expect(screen.getByText('projA')).toBeTruthy();
    });

    // BUG FIX V6.13.5f:buildData 给 resource 容器和它所有 lib/ruleLib/... 子节点赋同一个 fullPath(同物理路径多虚拟分类)
    // 兄弟节点共享 key → antd 强制 key 唯一,第 2 个起被丢弃 → caret 折叠节点消失 / 状态错位
    // 修复:toAntNode 用 nodeKey(fullPath, type) 派生唯一 key
    // V6.20.0 P2:只 4 类库容器 (lib/flowLib/drlLib/resource),用 5 个不同 type 兄弟测契约
    it('V6.13.5f:同 fullPath 不同 type 的兄弟节点 antd key 唯一(resource + 4 个 lib 不撞 key)', () => {
        const siblingData = {
            id: 'root', name: 'root', type: 'root', fullPath: '/',
            children: [
                {id: 'r', name: '资源', type: 'resource', fullPath: '/test003', _level: 2},
                {id: 'l1', name: '库', type: 'lib', fullPath: '/test003', _level: 3},
                {id: 'l2', name: 'DRL规则', type: 'drlLib', fullPath: '/test003', _level: 3},
                {id: 'l3', name: '决策流', type: 'flowLib', fullPath: '/test003', _level: 3},
                {id: 'l4', name: '公共', type: 'publicResource', fullPath: '/test003', _level: 3},
            ],
        } as unknown as TreeNodeData;
        const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
        const {container} = render(<Provider store={makeStore(siblingData)}><FileTree/></Provider>);
        // 5 个期望 name 全出现(无 antd 兄弟合并丢节点)
        const titles = Array.from(container.querySelectorAll('.ant-tree-treenode')).map(n => n.textContent || '');
        ['资源', '库', 'DRL规则', '决策流', '公共'].forEach(name => {
            expect(titles.some(t => t.includes(name))).toBe(true);
        });
        // antd 不应该报 "Same 'key' exist in the Tree" 警告
        const keyWarn = errSpy.mock.calls.find(args =>
            typeof args[0] === 'string' && /same.*key/i.test(args[0]),
        );
        expect(keyWarn).toBeUndefined();
        errSpy.mockRestore();
    });

    it('清空搜索:expandedKeys 重置为初始(_level<expandLevel 的节点展开),深层 loan 不可见', async () => {
        // 深层数据:所有 _level >= expandLevel=3,初始都不展开
        const deepData = {
            id: 'root', name: 'root', type: 'root', fullPath: '/r',
            children: [
                {
                    id: 'p', name: 'projA', type: 'project', fullPath: '/r/p', _level: 3,
                    children: [
                        {id: 'f', name: 'loan.drl', type: 'drl', fullPath: '/r/p/loan.drl', _level: 4},
                    ],
                },
            ],
        } as unknown as TreeNodeData;
        const {container} = render(<Provider store={makeStore(deepData)}><FileTree/></Provider>);

        // antd Tree 折叠的子节点不 render DOM。nodeExists = DOM 中能查到该文本的 treenode。
        const nodeExists = (text: string) => {
            return Array.from(container.querySelectorAll('.ant-tree-treenode')).some(
                n => n.textContent === text || n.textContent?.includes(text),
            );
        };

        // 初始:loan 不可见(projA 不展开,loan 不 render)
        expect(nodeExists('loan.drl')).toBe(false);

        // 搜索 loan → projA 展开祖先链,loan 渲染
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: 'loan'}});
        await waitFor(() => {
            expect(nodeExists('loan.drl')).toBe(true);
        });

        // 清空搜索 → expandedKeys 重置回 _level<3(初始空),projA 收起 → loan 不再渲染
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: ''}});
        await waitFor(() => {
            expect(nodeExists('loan.drl')).toBe(false);
        });
    });

    // V6.13.5f:清空搜索不应重置用户手动 caret 折叠(老 V6.13.5e 行为把"还原初始"当作"清空搜索",
    // 但用户手点折叠的 caret 也会被还原,违背用户意图)
    it('V6.13.5f:清空搜索保留用户手动折叠的 caret(projA _level<expandLevel 默认展开,手点折叠后清空搜索应保持折叠)', async () => {
        // 浅数据:projA _level=1 < 3 → 初始展开;loan _level=2 < 3 → 也展开
        const shallowData = {
            id: 'root', name: 'root', type: 'root', fullPath: '/r',
            children: [
                {
                    id: 'p', name: 'projA', type: 'project', fullPath: '/r/p', _level: 1,
                    children: [{id: 'f', name: 'loan.drl', type: 'drl', fullPath: '/r/p/loan.drl', _level: 2}],
                },
            ],
        } as unknown as TreeNodeData;
        const {container} = render(<Provider store={makeStore(shallowData)}><FileTree/></Provider>);

        // 初始:loan 可见(projA 展开,loan 跟着渲染)
        expect(container.textContent).toContain('loan.drl');

        // 模拟用户点 projA caret 折叠 — 找 projA treenode,click 它的 .ant-tree-switcher
        const projATreenode = Array.from(container.querySelectorAll('.ant-tree-treenode'))
            .find(n => (n.textContent || '').includes('projA'));
        expect(projATreenode).toBeTruthy();
        const projACaret = projATreenode!.querySelector('.ant-tree-switcher') as HTMLElement;
        fireEvent.click(projACaret);

        // 折叠后 loan 不可见
        await waitFor(() => {
            expect(container.textContent).not.toContain('loan.drl');
        });

        // 搜索 loan → 自动展开 projA 祖先链,loan 再次可见
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: 'loan'}});
        await waitFor(() => {
            expect(container.textContent).toContain('loan.drl');
        });

        // 清空搜索 → expandedKeys 只去 search 部分,user 手动折叠保留 → loan 不可见
        fireEvent.change(screen.getByPlaceholderText('搜索文件/项目'), {target: {value: ''}});
        await waitFor(() => {
            expect(container.textContent).not.toContain('loan.drl');
        });
    });
});
