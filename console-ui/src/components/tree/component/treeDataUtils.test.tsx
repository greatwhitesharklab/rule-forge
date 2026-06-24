import {describe, it, expect, beforeEach, afterEach, vi} from 'vitest';
import {render} from '@testing-library/react';
import {
    isFileNode,
    isContainerType,
    handleFileOpen,
    contextMenuToAntItems,
    matchesSearch,
    hasMatchInSubtree,
    collectMatchAncestorKeys,
    nodeKey,
    toAntNode,
    buildAntTreeData,
    collectInitialExpandedKeys,
    highlight,
} from './treeDataUtils';

/** 构造测试节点,填默认值避免 TS 报缺字段。 */
function makeNode(partial: Partial<TreeNodeData>): TreeNodeData {
    return {id: 'id', name: '', type: '', fullPath: '', ...partial} as TreeNodeData;
}

describe('treeDataUtils', () => {
    describe('isFileNode', () => {
        // Given 节点名含扩展名 / 'ul' / 'rp' When isFileNode Then true;容器名 false
        it('名含 . 视为文件', () => {
            expect(isFileNode(makeNode({name: 'loan.rs.xml'}))).toBe(true);
            expect(isFileNode(makeNode({name: 'credit.dt.xml'}))).toBe(true);
        });
        it('特殊名 ul / rp 视为文件', () => {
            expect(isFileNode(makeNode({name: 'ul'}))).toBe(true);
            expect(isFileNode(makeNode({name: 'rp'}))).toBe(true);
        });
        it('容器名(无扩展名非 ul/rp)非文件', () => {
            expect(isFileNode(makeNode({name: '项目A', type: 'project'}))).toBe(false);
            expect(isFileNode(makeNode({name: '决策表库', type: 'decisionTableLib'}))).toBe(false);
        });
    });

    describe('isContainerType', () => {
        it('有 children 视为容器', () => {
            expect(isContainerType(makeNode({children: [makeNode({name: 'a'})]}))).toBe(true);
            expect(isContainerType(makeNode({children: []}))).toBe(true); // 空数组也算(isArray)
        });
        it('_needLazyLoad 未加载视为容器', () => {
            expect(isContainerType(makeNode({_needLazyLoad: true, _childrenLoaded: false}))).toBe(true);
        });
        it('容器 type 枚举视为容器(无 children)', () => {
            expect(isContainerType(makeNode({type: 'lib'}))).toBe(true);
            // V6.20.0 P2:ruleLib 不再是前端容器类型(老 urule 规则库已删)
            expect(isContainerType(makeNode({type: 'folder'}))).toBe(true);
        });
        it('文件节点非容器', () => {
            expect(isContainerType(makeNode({name: 'x.rs.xml', type: 'rule'}))).toBe(false);
        });
        // V6.20.0:drlLib 容器类型走 CONTAINER_TYPES(可展开 + 不是文件)
        it('V6.20.0:drlLib 视为容器', () => {
            expect(isContainerType(makeNode({type: 'drlLib'}))).toBe(true);
        });
    });

    describe('handleFileOpen', () => {
        let openSpy: ReturnType<typeof vi.spyOn>;
        beforeEach(() => {
            openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
        });
        afterEach(() => openSpy.mockRestore());

        // V6.20.0 P2:删老 urule 规则 UI 入口(.rs.xml/.dt.xml/.dtree.xml/.sdt.xml/.sc.xml/.complexscorecard/.ct.xml)。
        // 只留 DRL + 4 库 + 决策流 + 知识包。
        // (name 必须含扩展名,isFileNode 才判 true;真实节点 name 就是文件名带后缀)
        const cases: Array<{name: string; node: Partial<TreeNodeData>; expectedPath: string}> = [
            {name: 'variable/.vl.xml → variable', node: {name: 'v.vl.xml', type: 'variable', fullPath: '/p/v.vl.xml'}, expectedPath: '/app/editor/variable'},
            {name: 'constant/.cl.xml → constant', node: {name: 'c.cl.xml', type: 'constant', fullPath: '/p/c.cl.xml'}, expectedPath: '/app/editor/constant'},
            {name: 'parameter/.pl.xml → parameter', node: {name: 'p.pl.xml', type: 'parameter', fullPath: '/p/p.pl.xml'}, expectedPath: '/app/editor/parameter'},
            {name: 'action/.al.xml → action', node: {name: 'a.al.xml', type: 'action', fullPath: '/p/a.al.xml'}, expectedPath: '/app/editor/action'},
            {name: 'flow/.rl.xml → flow', node: {name: 'f.rl.xml', type: 'flow', fullPath: '/p/f.rl.xml'}, expectedPath: '/app/editor/flow'},
            // V6.20.0:DRL 规则(.drl) → DRL 编辑器
            {name: 'V6.20.0:DRL/.drl → drl', node: {name: 'r.drl', type: 'drl', fullPath: '/p/r.drl'}, expectedPath: '/app/editor/drl'},
        ];
        it.each(cases)('$name', ({node, expectedPath}) => {
            handleFileOpen(makeNode(node), undefined, false);
            expect(openSpy).toHaveBeenCalledWith(
                expectedPath + '?file=' + encodeURIComponent(node.fullPath as string),
                '_blank',
            );
        });

        it('resourcePackage → package?file={packageName}.rp', () => {
            // fullPath='/projA' → split('/')[1]='projA' → file=projA.rp
            handleFileOpen(makeNode({name: 'pkg.rp', type: 'resourcePackage', fullPath: '/projA'}), undefined, false);
            expect(openSpy).toHaveBeenCalledWith('/app/editor/package?file=projA.rp', '_blank');
        });

        it('treeType=public → resource 编辑器', () => {
            handleFileOpen(makeNode({name: 'res.xml', fullPath: '/pub/r.xml'}), 'public', false);
            expect(openSpy).toHaveBeenCalledWith(
                '/app/editor/resource?file=' + encodeURIComponent('/pub/r.xml'),
                '_blank',
            );
        });

        it('readOnly + onFileReadOnlyClick → 调回调,不开窗', () => {
            const cb = vi.fn();
            handleFileOpen(makeNode({name: 'v.vl.xml', type: 'variable', fullPath: '/p/v.vl.xml'}), undefined, true, cb);
            expect(cb).toHaveBeenCalledWith(expect.objectContaining({fullPath: '/p/v.vl.xml'}));
            expect(openSpy).not.toHaveBeenCalled();
        });

        it('V6.20.0 P2:无 SPA 路由老 type(.rs.xml)→ no-op,window.open 不调', () => {
            // 老 urule 规则类型 UI 入口已删,handleFileOpen 命中后 no-op
            handleFileOpen(makeNode({name: 'x.rs.xml', type: 'rule', fullPath: '/p/x.rs.xml'}), undefined, false);
            expect(openSpy).not.toHaveBeenCalled();
        });

        it('容器节点 → no-op', () => {
            handleFileOpen(makeNode({name: '决策表库', type: 'decisionTableLib'}), undefined, false);
            expect(openSpy).not.toHaveBeenCalled();
        });
    });

    describe('contextMenuToAntItems', () => {
        it('ContextMenuItem[] → antd items(name/icon/click)', () => {
            const clickFn = vi.fn();
            const items: ContextMenuItem[] = [
                {name: '删除', icon: 'rf rf-remove', click: clickFn},
                {name: '重命名', icon: 'rf rf-rename'},
            ];
            const data = makeNode({name: 'x'});
            const dispatch = vi.fn();
            const result = contextMenuToAntItems(items, data, dispatch);
            expect(result).toHaveLength(2);
            const first = result[0] as {key: string; label: string; onClick?: (...a: never[]) => void};
            expect(first.key).toBe('删除');
            expect(first.label).toBe('删除');
            // onClick 触发原 click(data, dispatch)
            first.onClick?.({} as never, {} as never);
            expect(clickFn).toHaveBeenCalledWith(data, dispatch);
        });
        it('空 items → []', () => {
            expect(contextMenuToAntItems(undefined, makeNode({}))).toEqual([]);
            expect(contextMenuToAntItems([], makeNode({}))).toEqual([]);
        });
    });

    describe('nodeKey V6.13.5f', () => {
        it('有 type → fullPath + # + type', () => {
            expect(nodeKey(makeNode({name: 'p', type: 'project', fullPath: '/p'}))).toBe('/p#project');
        });
        it('无 type → 退化为 fullPath(原行为兜底)', () => {
            expect(nodeKey(makeNode({name: 'p', fullPath: '/p'}))).toBe('/p');
        });
        it('同 fullPath 不同 type → 派生不同 key(resource 容器 vs lib 子节点共享 /test003 不撞)', () => {
            const resource = nodeKey(makeNode({name: '资源', type: 'resource', fullPath: '/test003'}));
            const lib = nodeKey(makeNode({name: '库', type: 'lib', fullPath: '/test003'}));
            const ruleLib = nodeKey(makeNode({name: '决策集', type: 'ruleLib', fullPath: '/test003'}));
            expect(resource).not.toBe(lib);
            expect(lib).not.toBe(ruleLib);
            expect(new Set([resource, lib, ruleLib]).size).toBe(3);
        });
    });

    describe('matchesSearch', () => {
        it('term 空 → 全匹配', () => {
            expect(matchesSearch(makeNode({name: '任意'}), '')).toBe(true);
        });
        it('name 包含 term(大小写不敏感)', () => {
            expect(matchesSearch(makeNode({name: 'CreditRule'}), 'credit')).toBe(true);
            expect(matchesSearch(makeNode({name: 'credit'}), 'CREDIT')).toBe(true);
        });
        it('fullPath 包含 term', () => {
            expect(matchesSearch(makeNode({name: 'x', fullPath: '/proj/folder/file.xml'}), 'folder')).toBe(true);
        });
        it('无命中 → false', () => {
            expect(matchesSearch(makeNode({name: 'abc', fullPath: '/a/b'}), 'xyz')).toBe(false);
        });
    });

    describe('hasMatchInSubtree', () => {
        const tree = makeNode({
            name: 'root', fullPath: '/r',
            children: [makeNode({name: 'loan', fullPath: '/r/loan', children: [makeNode({name: 'credit.dt.xml', fullPath: '/r/loan/credit.dt.xml'})]})],
        });
        it('自身命中 → true', () => {
            expect(hasMatchInSubtree(makeNode({name: 'credit'}), 'credit')).toBe(true);
        });
        it('后代命中 → true(保留祖先链)', () => {
            expect(hasMatchInSubtree(tree, 'credit.dt.xml')).toBe(true);
        });
        it('子树无命中 → false', () => {
            expect(hasMatchInSubtree(tree, '不存在')).toBe(false);
        });
        it('term 空 → true', () => {
            expect(hasMatchInSubtree(tree, '')).toBe(true);
        });
    });

    describe('collectMatchAncestorKeys', () => {
        it('深层命中 → 收集所有祖先 key(不含命中节点自身)', () => {
            const root = makeNode({
                name: 'root', fullPath: '/r',
                children: [makeNode({
                    name: 'projA', fullPath: '/r/p',
                    children: [makeNode({name: 'credit.dt.xml', fullPath: '/r/p/credit.dt.xml'})],
                })],
            });
            const keys = collectMatchAncestorKeys(root, 'credit');
            expect(keys.has('/r')).toBe(true);     // root
            expect(keys.has('/r/p')).toBe(true);    // projA
            expect(keys.has('/r/p/credit.dt.xml')).toBe(false); // 命中节点自身不含(它不是祖先)
        });
        it('term 空 → 空 Set', () => {
            expect(collectMatchAncestorKeys(makeNode({name: 'r', fullPath: '/r'}), '').size).toBe(0);
        });
    });

    describe('toAntNode', () => {
        it('文件节点:key=fullPath#type (V6.13.5f), isLeaf=true, selectable=true, children=undefined', () => {
            const node = toAntNode(makeNode({name: 'r.drl', type: 'drl', fullPath: '/p/r.drl', _icon: 'rf rf-rule'}), '');
            expect(node.key).toBe('/p/r.drl#drl');
            expect(node.isLeaf).toBe(true);
            expect(node.selectable).toBe(true);
            expect(node.children).toBeUndefined();
        });
        it('容器有 children:isLeaf=false, selectable=false, children 递归', () => {
            const node = toAntNode(makeNode({
                name: 'proj', type: 'project', fullPath: '/p', _icon: 'rf rf-project',
                children: [makeNode({name: 'r.drl', type: 'drl', fullPath: '/p/r.drl'})],
            }), '');
            expect(node.isLeaf).toBe(false);
            expect(node.selectable).toBe(false);
            expect(node.children).toHaveLength(1);
            expect(node.key).toBe('/p#project');
            expect(node.children![0].key).toBe('/p/r.drl#drl');
        });
        // V6.13.5g:icon 走 titleRender (FileTreeNode 渲染) — toAntNode.icon 必须 undefined,
        // 否则 antd 内部用 .ant-tree-iconEle 渲染 + titleRender 也渲染 → 每个节点 icon 重复 2 次
        it('V6.13.5g:icon=undefined(走 titleRender,避免双层 icon 重复渲染)', () => {
            const node = toAntNode(makeNode({name: 'x.rs.xml', type: 'rule', fullPath: '/p/x.rs.xml', _icon: 'rf rf-rule'}), '');
            expect(node.icon).toBeUndefined();
        });
        it('懒加载未加载容器:children=undefined(触发 antd loadData)', () => {
            const node = toAntNode(makeNode({name: 'lib', type: 'lib', fullPath: '/p/lib', _needLazyLoad: true, _childrenLoaded: false}), '');
            expect(node.isLeaf).toBe(false);
            expect(node.children).toBeUndefined();
        });
        it('搜索过滤 children(保留命中 + 祖先链)', () => {
            const node = toAntNode(makeNode({
                name: 'proj', type: 'project', fullPath: '/p',
                children: [
                    makeNode({name: 'credit.dt.xml', fullPath: '/p/credit.dt.xml'}),
                    makeNode({name: 'other.rs.xml', fullPath: '/p/other.rs.xml'}),
                ],
            }), 'credit');
            expect(node.children).toHaveLength(1);
            expect(node.children![0].key).toBe('/p/credit.dt.xml');
        });
        // V6.20.0:drlLib 容器转换后 isLeaf=false(走 loadData 懒加载)
        it('V6.20.0:drlLib → isLeaf=false', () => {
            const node = toAntNode(makeNode({name: 'DRL规则', type: 'drlLib', fullPath: '/p/drl', _needLazyLoad: true, _childrenLoaded: false}), '');
            expect(node.isLeaf).toBe(false);
            expect(node.selectable).toBe(false);
            expect(node.children).toBeUndefined();
        });
    });

    describe('buildAntTreeData', () => {
        it('跳 root,渲染 root.children', () => {
            const root = makeNode({name: 'root', fullPath: '/r', children: [
                makeNode({name: 'projA', type: 'project', fullPath: '/r/a'}),
                makeNode({name: 'projB', type: 'project', fullPath: '/r/b'}),
            ]});
            const data = buildAntTreeData(root, '');
            expect(data).toHaveLength(2);
            expect(data[0].key).toBe('/r/a#project');
        });
        it('root=null → []', () => {
            expect(buildAntTreeData(null, '')).toEqual([]);
        });
        it('搜索过滤顶层', () => {
            const root = makeNode({name: 'root', fullPath: '/r', children: [
                makeNode({name: 'loan', type: 'project', fullPath: '/r/loan'}),
                makeNode({name: 'credit', type: 'project', fullPath: '/r/credit'}),
            ]});
            expect(buildAntTreeData(root, 'credit')).toHaveLength(1);
        });
    });

    describe('collectInitialExpandedKeys', () => {
        it('_forceExpand 标记 → 收集', () => {
            const root = makeNode({name: 'root', fullPath: '/r', children: [
                makeNode({name: 'a', fullPath: '/r/a', _forceExpand: true, _level: 5}),
            ]});
            expect(collectInitialExpandedKeys(root, 3)).toContain('/r/a');
        });
        it('_level < expandLevel → 收集', () => {
            const root = makeNode({name: 'root', fullPath: '/r', children: [
                makeNode({name: 'a', fullPath: '/r/a', _level: 1, children: [
                    makeNode({name: 'deep', fullPath: '/r/a/d', _level: 2}),
                ]}),
            ]});
            const keys = collectInitialExpandedKeys(root, 3);
            expect(keys).toContain('/r/a');      // level 1 < 3
            expect(keys).toContain('/r/a/d');    // level 2 < 3
        });
        it('root=null → []', () => {
            expect(collectInitialExpandedKeys(null, 3)).toEqual([]);
        });
    });

    describe('highlight', () => {
        it('term 空 → 原文本(无 mark)', () => {
            const {container, queryByText} = render(<>{highlight('credit rule', '')}</>);
            expect(queryByText('credit rule')).toBeTruthy();
            expect(container.querySelector('mark')).toBeNull();
        });
        it('命中 → <mark> 包裹', () => {
            const {container} = render(<>{highlight('credit rule', 'credit')}</>);
            const mark = container.querySelector('mark.rf-tree-match');
            expect(mark).not.toBeNull();
            expect(mark?.textContent).toBe('credit');
        });
        it('无命中 → 原文本(无 mark)', () => {
            const {container} = render(<>{highlight('abc', 'xyz')}</>);
            expect(container.querySelector('mark')).toBeNull();
        });
    });
});
