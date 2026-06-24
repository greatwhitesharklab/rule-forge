import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, act} from '@testing-library/react';
import {Provider} from 'react-redux';
import {createStore, applyMiddleware} from 'redux';
import thunk from 'redux-thunk';

// V6.13.5i:展开闪回 bug 回归测试。jsdom 下 antd <Tree> 的折叠 DOM 行为不一致(节点移除/复用时机不可靠),
// 改 mock antd <Tree> 直接捕获受控 expandedKeys prop —— 断言"传给 antd 的展开 key 集合",最贴近 root cause
// (effect 重置 userExpandedKeys → 合并后的 expandedKeys prop 变 → antd 折叠 → 视觉闪回)。

let lastExpandedKeys: React.Key[] = [];
let lastOnExpand: ((keys: React.Key[]) => void) | null = null;
vi.mock('antd', async () => {
    const actual = await vi.importActual<typeof import('antd')>('antd');
    const TreeStub = (props: {expandedKeys?: React.Key[]; onExpand?: (k: React.Key[]) => void}) => {
        lastExpandedKeys = props.expandedKeys ?? [];
        lastOnExpand = props.onExpand ?? null;
        return null;
    };
    return {...actual, Tree: TreeStub};
});

import FileTree from './FileTree';

/** 可变 store:SET_DATA 替换 data 引用(模拟 reducer LOAD_CHILDREN_END 的 Object.assign 新引用)。 */
function makeStore(data: TreeNodeData) {
    let current = data;
    return createStore((state: {data: TreeNodeData; publicResource: null} | undefined, action: {type: string; payload?: TreeNodeData}) => {
        if (action.type === 'SET_DATA' && action.payload) {
            current = action.payload;
            return {data: current, publicResource: null};
        }
        return state || {data: current, publicResource: null};
    }, applyMiddleware(thunk));
}

describe('FileTree expandedKeys V6.13.5i (展开闪回)', () => {
    beforeEach(() => {
        lastExpandedKeys = [];
        lastOnExpand = null;
    });

    // 库 _level=3 >= expandLevel 3 → collectInitialExpandedKeys 不收集 → 初始折叠
    const makeTree = (topPath: string) => ({
        id: 'root', name: 'root', type: 'root', fullPath: '/r',
        children: [{
            id: 'lib', name: '库', type: 'lib', fullPath: topPath + '/lib', _level: 3,
            children: [{id: 'f', name: 'item.rs.xml', type: 'rule', fullPath: topPath + '/lib/f.rs.xml', _level: 4}],
        }],
    } as unknown as TreeNodeData);
    // nodeKey(data) = fullPath + '#' + type → 库 key = topPath/lib#lib
    const libKey = (topPath: string) => topPath + '/lib#lib';

    // Given 用户展开库节点后,loadChildren 增量 dispatch(同 project,data 新引用)
    // When effect 触发
    // Then expandedKeys 保留库 —— 老 bug:effect([data]) 重算 collectInitialExpandedKeys(不含 _level>=expandLevel
    //   的库)→ userExpandedKeys=[] → 合并 expandedKeys 移除库 → antd 折叠 → "点一下闪一下"
    it('V6.13.5i:loadChildren 增量(data 新引用,同 project)不重置用户展开', () => {
        const store = makeStore(makeTree('/r'));
        render(<Provider store={store}><FileTree/></Provider>);
        // 初始:库 _level>=expandLevel 不展开
        expect(lastExpandedKeys).not.toContain(libKey('/r'));

        // 模拟点库 caret 展开 → onExpand → setUserExpandedKeys
        act(() => { lastOnExpand?.([libKey('/r')]); });
        expect(lastExpandedKeys).toContain(libKey('/r'));

        // 模拟 loadChildren 增量:dispatch SET_DATA(新引用,同 project 顶层 /r)
        act(() => { store.dispatch({type: 'SET_DATA', payload: makeTree('/r')}); });

        // 库应保持展开(修复后:effect 依赖 projectId 不变 → 不重算)
        expect(lastExpandedKeys).toContain(libKey('/r'));
    });

    // Given project 切换(顶层 fullPath 变)—— 必须重置初始展开,不残留旧 project 的展开
    it('V6.13.5i:project 切换(顶层 children fullPath 变)重置初始展开', () => {
        const store = makeStore(makeTree('/projA'));
        render(<Provider store={store}><FileTree/></Provider>);

        // 点库展开(projA)
        act(() => { lastOnExpand?.([libKey('/projA')]); });
        expect(lastExpandedKeys).toContain(libKey('/projA'));

        // project 切换:SET_DATA(treeB,顶层 /projA→/projB)
        act(() => { store.dispatch({type: 'SET_DATA', payload: makeTree('/projB')}); });

        // 库折叠回初始(_level>=expandLevel)—— 不残留 projA 展开,projB 也不自动展开
        expect(lastExpandedKeys).not.toContain(libKey('/projA'));
        expect(lastExpandedKeys).not.toContain(libKey('/projB'));
    });
});
