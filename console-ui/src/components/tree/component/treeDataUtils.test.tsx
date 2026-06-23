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
            expect(isContainerType(makeNode({type: 'ruleLib'}))).toBe(true);
            expect(isContainerType(makeNode({type: 'folder'}))).toBe(true);
        });
        it('文件节点非容器', () => {
            expect(isContainerType(makeNode({name: 'x.rs.xml', type: 'rule'}))).toBe(false);
        });
    });

    describe('handleFileOpen', () => {
        let openSpy: ReturnType<typeof vi.spyOn>;
        beforeEach(() => {
            openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
        });
        afterEach(() => openSpy.mockRestore());

        // Given 文件 type/后缀 When handleFileOpen Then window.open 对应 SPA 路由
        // (name 必须含扩展名,isFileNode 才判 true;真实节点 name 就是文件名带后缀)
        const cases: Array<{name: string; node: Partial<TreeNodeData>; expectedPath: string}> = [
            {name: 'rule/.rs.xml → ruleset', node: {name: 'x.rs.xml', type: 'rule', fullPath: '/p/x.rs.xml'}, expectedPath: '/app/editor/ruleset'},
            {name: 'decisionTree/.dtree.xml → decisiontree', node: {name: 't.dtree.xml', type: 'decisionTree', fullPath: '/p/t.dtree.xml'}, expectedPath: '/app/editor/decisiontree'},
            {name: 'decisionTable/.dt.xml → decisiontable', node: {name: 'd.dt.xml', type: 'decisionTable', fullPath: '/p/d.dt.xml'}, expectedPath: '/app/editor/decisiontable'},
            {name: 'scriptDecisionTable/.sdt.xml → scriptdecisiontable', node: {name: 's.sdt.xml', type: 'scriptDecisionTable', fullPath: '/p/s.sdt.xml'}, expectedPath: '/app/editor/scriptdecisiontable'},
            {name: 'scorecard/.sc.xml → scorecard', node: {name: 's.sc.xml', type: 'scorecard', fullPath: '/p/s.sc.xml'}, expectedPath: '/app/editor/scorecard'},
            {name: 'complexscorecard → complexscorecard', node: {name: 'c.complexscorecard', type: 'complexscorecard', fullPath: '/p/c.complexscorecard'}, expectedPath: '/app/editor/complexscorecard'},
            {name: 'crosstab/.ct.xml → crosstab', node: {name: 'c.ct.xml', type: 'crosstab', fullPath: '/p/c.ct.xml'}, expectedPath: '/app/editor/crosstab'},
            {name: 'variable/.vl.xml → variable', node: {name: 'v.vl.xml', type: 'variable', fullPath: '/p/v.vl.xml'}, expectedPath: '/app/editor/variable'},
            {name: 'constant/.cl.xml → constant', node: {name: 'c.cl.xml', type: 'constant', fullPath: '/p/c.cl.xml'}, expectedPath: '/app/editor/constant'},
            {name: 'parameter/.pl.xml → parameter', node: {name: 'p.pl.xml', type: 'parameter', fullPath: '/p/p.pl.xml'}, expectedPath: '/app/editor/parameter'},
            {name: 'action/.al.xml → action', node: {name: 'a.al.xml', type: 'action', fullPath: '/p/a.al.xml'}, expectedPath: '/app/editor/action'},
            {name: 'flow/.rl.xml → flow', node: {name: 'f.rl.xml', type: 'flow', fullPath: '/p/f.rl.xml'}, expectedPath: '/app/editor/flow'},
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
            handleFileOpen(makeNode({name: 'x.rs.xml', type: 'rule', fullPath: '/p/x.rs.xml'}), undefined, true, cb);
            expect(cb).toHaveBeenCalledWith(expect.objectContaining({fullPath: '/p/x.rs.xml'}));
            expect(openSpy).not.toHaveBeenCalled();
        });

        it('无 SPA 路由类型(.ul.xml)→ no-op,window.open 不调', () => {
            handleFileOpen(makeNode({name: 's.ul.xml', fullPath: '/p/s.ul.xml'}), undefined, false);
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
        it('文件节点:key=fullPath, isLeaf=true, selectable=true, children=undefined', () => {
            const node = toAntNode(makeNode({name: 'x.rs.xml', type: 'rule', fullPath: '/p/x.rs.xml', _icon: 'rf rf-rule'}), '');
            expect(node.key).toBe('/p/x.rs.xml');
            expect(node.isLeaf).toBe(true);
            expect(node.selectable).toBe(true);
            expect(node.children).toBeUndefined();
        });
        it('容器有 children:isLeaf=false, selectable=false, children 递归', () => {
            const node = toAntNode(makeNode({
                name: 'proj', type: 'project', fullPath: '/p', _icon: 'rf rf-project',
                children: [makeNode({name: 'x.rs.xml', type: 'rule', fullPath: '/p/x.rs.xml'})],
            }), '');
            expect(node.isLeaf).toBe(false);
            expect(node.selectable).toBe(false);
            expect(node.children).toHaveLength(1);
            expect(node.children![0].key).toBe('/p/x.rs.xml');
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
    });

    describe('buildAntTreeData', () => {
        it('跳 root,渲染 root.children', () => {
            const root = makeNode({name: 'root', fullPath: '/r', children: [
                makeNode({name: 'projA', type: 'project', fullPath: '/r/a'}),
                makeNode({name: 'projB', type: 'project', fullPath: '/r/b'}),
            ]});
            const data = buildAntTreeData(root, '');
            expect(data).toHaveLength(2);
            expect(data[0].key).toBe('/r/a');
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
