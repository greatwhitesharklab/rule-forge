import {describe, it, expect, beforeEach, afterEach, vi} from 'vitest';
import {render} from '@testing-library/react';
import {eventEmitter, OPEN_EDITOR_TAB} from '@/frame/event';
import {
    isFileNode,
    isContainerType,
    handleFileOpen,
    contextMenuToAntItems,
    matchesSearch,
    hasMatchInSubtree,
    collectMatchAncestorKeys,
    nodeKey,
    dedupeByNodeKey,
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
        // V7:window.open 新窗口 → openEditorTab 应用内标签。断言改为监听 OPEN_EDITOR_TAB 事件。
        let opened: Array<{editorType: string; file?: string}>;
        let openSpy: ReturnType<typeof vi.spyOn>;
        const handler = (p: {editorType: string; file?: string}) => opened.push(p);
        beforeEach(() => {
            opened = [];
            eventEmitter.on(OPEN_EDITOR_TAB, handler);
            // window.open 不应再被调用(全链路已改应用内标签)
            openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
        });
        afterEach(() => {
            eventEmitter.removeListener(OPEN_EDITOR_TAB, handler);
            openSpy.mockRestore();
        });

        // V6.20.0 P2:删老 urule 规则 UI 入口(.rs.xml/.dt.xml/.dtree.xml/.sdt.xml/.sc.xml/.complexscorecard/.ct.xml)。
        // 只留 DRL + 决策流 + 知识包。
        // V7.23:老 4 库(.vl/.cl/.pl/.al.xml)window.open 用例删除 —— 编辑器下线,改走
        //   onFileSourceClick 回调(见下方专项用例)。
        // (name 必须含扩展名,isFileNode 才判 true;真实节点 name 就是文件名带后缀)
        const cases: Array<{name: string; node: Partial<TreeNodeData>; expectedType: string}> = [
            // V7.21:flow/.rl.xml 用例已删除(BPMN 决策流入口移除)。
            // V6.20.0:DRL 规则(.drl) → DRL 编辑器
            {name: 'V6.20.0:DRL/.drl → drl', node: {name: 'r.drl', type: 'drl', fullPath: '/p/r.drl'}, expectedType: 'drl'},
            // V7 系:V1 决策流/库/规则集/决策表/评分卡
            {name: 'V1 决策流/.v1flow.json → v1flow', node: {name: 'f.v1flow.json', type: 'v1flow', fullPath: '/p/f.v1flow.json'}, expectedType: 'v1flow'},
            {name: 'V1 库/.v1lib.json(type 直配)→ v1library', node: {name: 'l.v1lib.json', type: 'v1library', fullPath: '/p/l.v1lib.json'}, expectedType: 'v1library'},
            {name: 'V1 规则集(type 直配)→ v1ruleset', node: {name: 'r.v1rs.json', type: 'v1ruleset', fullPath: '/p/r.v1rs.json'}, expectedType: 'v1ruleset'},
            {name: 'V1 决策表(type 直配)→ v1decisiontable', node: {name: 'd.v1dt.json', type: 'v1decisiontable', fullPath: '/p/d.v1dt.json'}, expectedType: 'v1decisiontable'},
            {name: 'V1 评分卡(type 直配)→ v1scorecard', node: {name: 's.v1sc.json', type: 'v1scorecard', fullPath: '/p/s.v1sc.json'}, expectedType: 'v1scorecard'},
            // V6.20.0 P3:DMN/PMML 标准决策模型 → 只读源查看器
            {name: 'V6.20.0 P3:DMN/.dmn → dmn', node: {name: 'd.dmn', type: 'dmn', fullPath: '/p/d.dmn'}, expectedType: 'dmn'},
            {name: 'V6.20.0 P3:PMML/.pmml → pmml', node: {name: 'm.pmml', type: 'pmml', fullPath: '/p/m.pmml'}, expectedType: 'pmml'},
        ];
        it.each(cases)('$name', ({node, expectedType}) => {
            handleFileOpen(makeNode(node), undefined, false);
            expect(opened).toEqual([{editorType: expectedType, file: node.fullPath}]);
            expect(openSpy).not.toHaveBeenCalled();
        });

        // V7.22:resourcePackage(知识包)测试用例已删除 — 入口已移除,V1 发布替代。

        it('treeType=public → resource 编辑器', () => {
            handleFileOpen(makeNode({name: 'res.xml', fullPath: '/pub/r.xml'}), 'public', false);
            expect(opened).toEqual([{editorType: 'resource', file: '/pub/r.xml'}]);
        });

        it('readOnly + onFileReadOnlyClick → 调回调,不开编辑器', () => {
            const cb = vi.fn();
            handleFileOpen(makeNode({name: 'v.vl.xml', type: 'variable', fullPath: '/p/v.vl.xml'}), undefined, true, cb);
            expect(cb).toHaveBeenCalledWith(expect.objectContaining({fullPath: '/p/v.vl.xml'}));
            expect(opened).toEqual([]);
            expect(openSpy).not.toHaveBeenCalled();
        });

        // V7.23:老 4 库编辑器已删除,点击走 onFileSourceClick 回调(只读源码查看),不开编辑器
        it.each([
            {name: 'variable/.vl.xml', node: {name: 'v.vl.xml', type: 'variable', fullPath: '/p/v.vl.xml'}},
            {name: 'constant/.cl.xml', node: {name: 'c.cl.xml', type: 'constant', fullPath: '/p/c.cl.xml'}},
            {name: 'parameter/.pl.xml', node: {name: 'p.pl.xml', type: 'parameter', fullPath: '/p/p.pl.xml'}},
            {name: 'action/.al.xml', node: {name: 'a.al.xml', type: 'action', fullPath: '/p/a.al.xml'}},
            {name: '仅后缀命中(.vl.xml 无 type)', node: {name: 'v.vl.xml', fullPath: '/p/v.vl.xml'}},
        ])('V7.23:老 4 库 $name → onFileSourceClick 回调,不开编辑器', ({node}) => {
            const cb = vi.fn();
            handleFileOpen(makeNode(node), undefined, false, undefined, cb);
            expect(cb).toHaveBeenCalledWith(expect.objectContaining({fullPath: node.fullPath}));
            expect(opened).toEqual([]);
        });

        it('V7.23:老 4 库无 onFileSourceClick 回调 → no-op,不开编辑器', () => {
            handleFileOpen(makeNode({name: 'v.vl.xml', type: 'variable', fullPath: '/p/v.vl.xml'}), undefined, false);
            expect(opened).toEqual([]);
            expect(openSpy).not.toHaveBeenCalled();
        });

        it('V6.20.0 P2:无编辑器老 type(.rs.xml)→ no-op,不发 OPEN_EDITOR_TAB', () => {
            // 老 urule 规则类型 UI 入口已删,handleFileOpen 命中后 no-op
            handleFileOpen(makeNode({name: 'x.rs.xml', type: 'rule', fullPath: '/p/x.rs.xml'}), undefined, false);
            expect(opened).toEqual([]);
            expect(openSpy).not.toHaveBeenCalled();
        });

        it('容器节点 → no-op', () => {
            handleFileOpen(makeNode({name: '决策表库', type: 'decisionTableLib'}), undefined, false);
            expect(opened).toEqual([]);
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

    describe('dedupeByNodeKey', () => {
        it('fullPath+type 完全相同的兄弟节点只保留首个(test01 重复 V1决策流 文件夹防御)', () => {
            const dup1 = makeNode({name: 'V1决策流', type: 'v1flowLib', fullPath: '/t/flows'});
            const dup2 = makeNode({name: 'V1决策流', type: 'v1flowLib', fullPath: '/t/flows'});
            const other = makeNode({name: 'V1库', type: 'v1libraryLib', fullPath: '/t/libs'});
            expect(dedupeByNodeKey([dup1, dup2, other])).toEqual([dup1, other]);
        });
        it('buildAntTreeData 对 root.children 去重后不产生重复 key', () => {
            const child = makeNode({name: 'V1决策流', type: 'v1flowLib', fullPath: '/t/flows'});
            const root = makeNode({name: 'root', children: [child, child]});
            const tree = buildAntTreeData(root, '');
            expect(tree.length).toBe(1);
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
