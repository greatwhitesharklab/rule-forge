import React from 'react';
import type {MenuProps} from 'antd';

/**
 * V6.13.5a:文件树纯函数集 —— 从老 {@link TreeItem} (class 组件) + {@link Menu} 抽出的可单测逻辑,
 * 供新 {@link FileTree} (antd `<Tree>`) 复用。零 UI 副作用,零 DOM 操作。
 *
 * <p>抽离来源:
 * <ul>
 *   <li>{@link isFileNode} / {@link isContainerType} ← TreeItem.isFile (121-129) / isContainer (143-144)</li>
 *   <li>{@link handleFileOpen} ← TreeItem.onClick (168-305) 的 13 type 分支 window.open + readOnly 回调</li>
 *   <li>{@link contextMenuToAntItems} ← Menu.tsx (22-27) 的 contextMenu → antd items 转换</li>
 *   <li>{@link matchesSearch} / {@link hasMatchInSubtree} / {@link collectMatchAncestorKeys} /
 *       {@link buildAntTreeData} / {@link toAntNode} / {@link highlight} ← 新增(搜索 + treeData 转换)</li>
 * </ul>
 */

/** 可展开的容器类型(文件夹/库根)。文件类型(rule/decisionTable/.rp 等)不在此列。 */
const CONTAINER_TYPES = new Set<string>([
    'root', 'project', 'folder', 'resource', 'all', 'lib',
    // V7.21:BPMN 决策流库(flowLib)已删除 — V1 决策流为唯一决策路径。只留 DRL 规则。
    'drlLib',
    // V7.4:V1 库容器
    'v1libraryLib',
    // V7.5:V1 规则独立文件容器
    'v1rulesetLib', 'v1decisiontableLib', 'v1scorecardLib',
    'publicResource',
]);

/**
 * V6.13.5f:antd Tree 要求兄弟 key 唯一,但 buildData 给 resource 容器和它所有 lib/ruleLib/...
 * 虚拟分类子节点赋同一个 fullPath(同一物理路径多虚拟分类是老 reducer 行为,有测试覆盖,不动)。
 * 这里用 (fullPath, type) 派生唯一 key:同 fullPath 不同 type 拼不同 key,不污染 store data。
 * type 缺失 fallback 到 fullPath(原行为兜底)。
 */
export function nodeKey(data: TreeNodeData): string {
    const base = data.fullPath || data.id || '';
    return data.type ? base + '#' + data.type : base;
}

/**
 * 兄弟节点按 nodeKey 去重(保留首个)。
 * 防御后端数据异常:test01 曾出现 5 个分类下各挂一个 fullPath+type 完全相同的
 * 同名文件夹,antd Tree 报 "Same 'key' exist" 警告且展开行为错乱。
 */
export function dedupeByNodeKey(children: TreeNodeData[]): TreeNodeData[] {
    const seen = new Set<string>();
    return children.filter(c => {
        const k = nodeKey(c);
        if (seen.has(k)) return false;
        seen.add(k);
        return true;
    });
}

/** antd `<Tree>` 的节点(转换后),rawData 挂回原始 TreeNodeData 供 titleRender/loadData 用。 */
export interface AntTreeNode {
    key: string;
    title: string;          // titleRender 接管,字段保留防 antd 报警
    icon?: React.ReactNode; // V6.13.5g:icon 走 titleRender (FileTreeNode 渲染),此处不设避免双层 icon 重复
    isLeaf: boolean;
    selectable: boolean;
    children?: AntTreeNode[];
    rawData: TreeNodeData;
}

export type AntMenuItem = NonNullable<MenuProps['items']>[number];

/**
 * 是否文件节点(可打开编辑器)。照搬 TreeItem.isFile:有扩展名(.),或特殊名 'ul'/'rp'。
 * 容器节点(文件夹/库)返回 false。
 */
export function isFileNode(data: TreeNodeData): boolean {
    const name = data.name;
    return name.indexOf('.') > -1 || name === 'ul' || name === 'rp';
}

/**
 * 是否容器节点(可展开)。照搬 TreeItem.isContainer:有 children(含空数组)/ 懒加载未加载 / type 属容器枚举。
 */
export function isContainerType(data: TreeNodeData): boolean {
    const children = data.children;
    const hasChildren = (!!children && children.length > 0) || Array.isArray(children);
    const lazyLoadable = !!(data._needLazyLoad && !data._childrenLoaded);
    return hasChildren || lazyLoadable || CONTAINER_TYPES.has(data.type);
}

/**
 * 文件点击行为(原 TreeItem.onClick 168-305)。按 type 分发 window.open SPA 编辑器路由,
 * readOnly 模式走 onFileReadOnlyClick 回调弹源码。纯逻辑,不含 DOM 选中高亮(新组件用 selectedKeys)。
 *
 * @param data              节点
 * @param treeType          'public' = 公共资源树(文件统一走 resource 编辑器)
 * @param readOnly          V6.13.1 只读模式(看 git 版本)
 * @param onFileReadOnlyClick readOnly 模式文件 click 回调(FileTreePanel dispatch seeFileSource)
 */
export function handleFileOpen(
    data: TreeNodeData,
    treeType: string | undefined,
    readOnly: boolean,
    onFileReadOnlyClick?: (data: TreeNodeData) => void,
): void {
    if (!isFileNode(data)) return;
    // V6.13.1 readOnly 模式:走回调弹源码,不开编辑器
    if (readOnly && onFileReadOnlyClick) {
        onFileReadOnlyClick(data);
        return;
    }
    if (readOnly) return; // readOnly 且无回调:不开编辑器(看 git 版本,开编辑器无意义)

    const fullPath = typeof data.fullPath === 'string' ? data.fullPath : '';
    const open = (url: string) => window.open(url, '_blank');

    // V6.20.0 P2:删老 urule 规则(.rs.xml/.dt.xml/.dtree.xml/.sdt.xml/.sc.xml/.complexscorecard/.ct.xml)
// UI 入口,只留 DRL + 4 库 + 决策流 + 知识包 + 公共资源。
// V6.20.0:DRL (.drl) — 走 DRL 编辑器
    if (data.type === 'drl' || fullPath.endsWith('.drl')) {
        open('/app/editor/drl?file=' + encodeURIComponent(fullPath));
        return;
    }
    // V7.0.0→V7.5.1:V1 决策流 (.v1flow.json 统一后缀;.json 兼容旧文件)
    if (data.type === 'v1flow' || fullPath.endsWith('.v1flow.json') || fullPath.endsWith('.json')) {
        open('/app/v1-flow?file=' + encodeURIComponent(fullPath));
        return;
    }
    // V7.4:V1 库 (.v1lib.json) — 走库编辑器
    if (data.type === 'v1library' || fullPath.endsWith('.v1lib.json')) {
        open('/app/v1-library?file=' + encodeURIComponent(fullPath));
        return;
    }
    // V7.5:V1 规则集 (.v1rs.json) — 走规则集编辑器
    if (data.type === 'v1ruleset' || fullPath.endsWith('.v1rs.json')) {
        open('/app/v1-ruleset?file=' + encodeURIComponent(fullPath));
        return;
    }
    // V7.5:V1 决策表 (.v1dt.json) — 走决策表编辑器
    if (data.type === 'v1decisiontable' || fullPath.endsWith('.v1dt.json')) {
        open('/app/v1-decisiontable?file=' + encodeURIComponent(fullPath));
        return;
    }
    // V7.5:V1 评分卡 (.v1sc.json) — 走评分卡编辑器
    if (data.type === 'v1scorecard' || fullPath.endsWith('.v1sc.json')) {
        open('/app/v1-scorecard?file=' + encodeURIComponent(fullPath));
        return;
    }
    // V6.20.0 P3:DMN / PMML 标准决策模型 — 走只读查看器(无 UI 编辑器,纯源文本展示)
    if (data.type === 'dmn' || fullPath.endsWith('.dmn')) {
        open('/app/editor/dmn?file=' + encodeURIComponent(fullPath));
        return;
    }
    if (data.type === 'pmml' || fullPath.endsWith('.pmml')) {
        open('/app/editor/pmml?file=' + encodeURIComponent(fullPath));
        return;
    }
    // 变量/常量/参数/动作库 (双扩展名 .vl.xml/.cl.xml/.pl.xml/.al.xml),按 type 或后缀
    const libType =
        data.type === 'variable' ? 'variable'
        : data.type === 'constant' ? 'constant'
        : data.type === 'parameter' ? 'parameter'
        : data.type === 'action' ? 'action'
        : fullPath.endsWith('.vl.xml') ? 'variable'
        : fullPath.endsWith('.cl.xml') ? 'constant'
        : fullPath.endsWith('.pl.xml') ? 'parameter'
        : fullPath.endsWith('.al.xml') ? 'action'
        : null;
    if (libType) {
        open('/app/editor/' + libType + '?file=' + encodeURIComponent(fullPath));
        return;
    }
    // 公共资源树 (treeType==='public') → 统一 resource 编辑器
    if (treeType === 'public') {
        open('/app/editor/resource?file=' + encodeURIComponent(fullPath));
        return;
    }
    // V7.22:知识包(.rp / resourcePackage)入口已删除 — V1 发布替代,编辑器路由 V7.7.2 已删。
    // V7.21:BPMN 决策流(.rl.xml)入口已删除 — V1 决策流为唯一决策路径。
    // 未匹配类型(如老 .rs.xml/.dt.xml/.ul.xml 等):no-op,不动 window
}

/**
 * contextMenu (ContextMenuItem[]) → antd Menu items。照搬 Menu.tsx:22-27 转换。
 * key=name(或 index),label=name,icon=字符串→`<i>`,onClick=click(data, dispatch)。
 */
export function contextMenuToAntItems(
    items: ContextMenuItem[] | undefined,
    data: TreeNodeData,
    dispatch?: (action: unknown) => void,
): AntMenuItem[] {
    if (!items || items.length === 0) return [];
    return items.map((item, index) => ({
        key: item.name || String(index),
        label: item.name,
        icon: typeof item.icon === 'string' ? <i className={item.icon}/> : null,
        onClick: () => item.click && item.click(data, dispatch),
    }));
}

/**
 * 节点自身是否匹配搜索(name 或 fullPath 包含 term,大小写不敏感)。term 空 → 全匹配。
 */
export function matchesSearch(data: TreeNodeData, term: string): boolean {
    if (!term) return true;
    const lower = term.toLowerCase();
    return data.name.toLowerCase().includes(lower)
        || (typeof data.fullPath === 'string' && data.fullPath.toLowerCase().includes(lower));
}

/**
 * 节点自身或任一后代是否匹配搜索(递归)。用于搜索时保留命中节点的祖先链(否则树会断)。
 */
export function hasMatchInSubtree(data: TreeNodeData, term: string): boolean {
    if (!term) return true;
    if (matchesSearch(data, term)) return true;
    if (data.children) {
        for (const child of data.children) {
            if (hasMatchInSubtree(child, term)) return true;
        }
    }
    return false;
}

/**
 * 收集命中节点的所有祖先 key(用于搜索时自动展开祖先)。递归遍历,ancestors 栈不含当前节点。
 * 返回的 Set 包含所有"有命中后代"的祖先 key(展开它们才能看到命中节点)。
 * V6.13.5f:key 用 nodeKey(派生自 fullPath+type),跟 antd 同步
 */
export function collectMatchAncestorKeys(root: TreeNodeData, term: string): Set<string> {
    const keys = new Set<string>();
    if (!term) return keys;
    const walk = (node: TreeNodeData, ancestors: string[]) => {
        if (matchesSearch(node, term)) {
            ancestors.forEach(k => keys.add(k));
        }
        if (node.children) {
            const childAncestors = [...ancestors, nodeKey(node)];
            for (const child of node.children) {
                walk(child, childAncestors);
            }
        }
    };
    walk(root, []);
    return keys;
}

/**
 * TreeNodeData → antd Tree 节点(递归)。key=fullPath(稳定唯一),isLeaf=!容器,selectable=文件。
 * 懒加载未加载容器 children=undefined(触发 antd loadData);空容器 children=[]。
 * 搜索时过滤 children(保留命中 + 祖先链)。
 */
export function toAntNode(data: TreeNodeData, term: string): AntTreeNode {
    const isContainer = isContainerType(data);
    const hasChildren = !!data.children && data.children.length > 0;
    const lazyLoadable = !!(data._needLazyLoad && !data._childrenLoaded);

    let children: AntTreeNode[] | undefined;
    if (hasChildren) {
        const filtered = dedupeByNodeKey(data.children!).filter(c => hasMatchInSubtree(c, term));
        children = filtered.map(c => toAntNode(c, term));
    } else if (lazyLoadable) {
        children = undefined; // 触发 antd loadData
    } else {
        children = isContainer ? [] : undefined;
    }

    return {
        key: nodeKey(data),
        title: data.name,
        // V6.13.5g:icon 走 titleRender (FileTreeNode 内 <i className={_icon}/>),不在此处设避免双层 icon
        isLeaf: !isContainer,
        selectable: isFileNode(data),
        children,
        rawData: data,
    };
}

/**
 * 构建整棵 antd treeData。跳过 root(跟老 Tree.tsx:26 一致),渲染 root.children。
 * @param root    Redux 的 state.data 或 state.publicResource
 * @param term    搜索词(空 = 全树)
 */
export function buildAntTreeData(root: TreeNodeData | null | undefined, term: string): AntTreeNode[] {
    if (!root || !root.children) return [];
    return dedupeByNodeKey(root.children)
        .filter(c => hasMatchInSubtree(c, term))
        .map(c => toAntNode(c, term));
}

/**
 * 收集初始展开 key:_forceExpand 标记 或 _level <= expandLevel 的节点(照搬 TreeItem:43 的初始展开判定)。
 * V6.13.5f:key 用 nodeKey(派生自 fullPath+type),跟 antd 同步
 */
export function collectInitialExpandedKeys(root: TreeNodeData | null | undefined, expandLevel: number): string[] {
    const keys: string[] = [];
    if (!root) return keys;
    const walk = (node: TreeNodeData) => {
        const force = !!node._forceExpand;
        const level = node._level || 1;
        const initiallyExpanded = force || level < expandLevel;
        if (initiallyExpanded) {
            keys.push(nodeKey(node));
        }
        if (node.children) node.children.forEach(walk);
    };
    if (root.children) root.children.forEach(walk);
    return keys;
}

/**
 * 高亮搜索命中:<mark> 包裹匹配段。term 空 → 原文本。
 */
export function highlight(text: string, term: string): React.ReactNode {
    if (!term) return text;
    const lower = text.toLowerCase();
    const idx = lower.indexOf(term.toLowerCase());
    if (idx === -1) return text;
    return (
        <>
            {text.slice(0, idx)}
            <mark className="rf-tree-match">{text.slice(idx, idx + term.length)}</mark>
            {text.slice(idx + term.length)}
        </>
    );
}
