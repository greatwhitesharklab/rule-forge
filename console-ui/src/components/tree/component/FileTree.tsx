import React, {useState, useEffect, useMemo, useCallback} from 'react';
import {Tree as AntTree, Input} from 'antd';
import type {TreeProps, TreeDataNode} from 'antd';
import '../css/fileTree.css';
import {connect} from 'react-redux';
import * as ACTIONS from '@/frame/action.js';
import {
    buildAntTreeData,
    collectInitialExpandedKeys,
    collectMatchAncestorKeys,
    handleFileOpen,
} from './treeDataUtils';
import FileTreeNode from './FileTreeNode';

interface FileTreeOwnProps {
    /** 'public' = 公共资源树(取 state.publicResource,文件统一走 resource 编辑器);默认主树 state.data */
    treeType?: string;
    /** V6.13.1 只读模式(看 git 版本):禁 ⋯/右键,文件 click 走 onFileReadOnlyClick */
    readOnly?: boolean;
    /** readOnly 模式文件 click 回调(FileTreePanel dispatch seeFileSource 弹源码) */
    onFileReadOnlyClick?: (data: TreeNodeData) => void;
    /** 初始展开层级(默认 3,照搬老 TreeItem:41 expandLevel) */
    expandLevel?: number;
    /** 接收但忽略(本次不做拖拽,跟老 Tree 的 dead draggable 一致) */
    draggable?: boolean;
}

interface FileTreeProps extends FileTreeOwnProps {
    /** connect 注入:treeType 选 state.data 或 state.publicResource */
    data: TreeNodeData | null;
    /** connect 注入 */
    dispatch: (action: unknown) => void;
}

/**
 * V6.13.5b:文件树 antd {@code <Tree>} 受控容器,替换老 Tree.tsx(手写 ul/li 递归)。
 *
 * <p>treeData 由纯函数 {@link buildAntTreeData} 从 Redux data 转换(key=fullPath,懒加载容器
 * children=undefined 触发 loadData)。expandedKeys/selectedKeys 受控。搜索框过滤 + 高亮 +
 * 自动展开命中祖先。节点走 {@link FileTreeNode}(titleRender)。
 *
 * <p>交互映射:onSelect→{@link handleFileOpen}(13 type window.open / readOnly 回调);
 * loadData→{@link ACTIONS.loadChildren} 懒加载;右键/hover ⋯→FileTreeNode 内 Dropdown。
 *
 * <p>样式走 fileTree.css(定制 .ant-tree + --rf-* token,跟 PackageNavigator 同代)。
 */
function FileTreeImpl({data, dispatch, treeType, readOnly, onFileReadOnlyClick, expandLevel = 3}: FileTreeProps) {
    // V6.13.5f:拆 user 手动 caret(userExpandedKeys)与搜索自动展开祖先(searchExpandedKeys),
    // 合并后给 antd expandedKeys。避免清空搜索重置用户手点 caret 的副作用(老 V6.13.5e 行为)。
    const [userExpandedKeys, setUserExpandedKeys] = useState<React.Key[]>([]);
    const [searchExpandedKeys, setSearchExpandedKeys] = useState<React.Key[]>([]);
    const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
    const [searchTerm, setSearchTerm] = useState('');

    // V6.13.5i:projectId = 顶层 children fullPath join。只在 project 切换(LOAD_END 整体替换,顶层 fullPath
    // 变)时重算 user 初始展开,不在 loadChildren 增量时重算。
    // 老 bug:effect 依赖 [data] 整个对象引用。loadChildren 增量 dispatch LOAD_CHILDREN_END,reducer 用
    // Object.assign({}, state.data) 浅拷贝 root → state.data 新引用 → effect 跑 → 重算
    // collectInitialExpandedKeys(只含 _forceExpand/_level<expandLevel)→ 用户手动展开的 _level>=expandLevel
    // 懒加载节点 key 被移除 → expandedKeys 丢该节点 → antd 折叠回去 → "点一下闪一下"。
    // 改依赖 projectId:loadChildren 只填某深层节点 children,顶层 children fullPath 不变 → projectId 不变 →
    // effect 不跑 → 用户展开保留。project 切换顶层 fullPath 变 → projectId 变 → effect 重算(重置)。
    const projectId = (data?.children || []).map(c => c.fullPath).join('|');
    useEffect(() => {
        setUserExpandedKeys(collectInitialExpandedKeys(data, expandLevel));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [projectId, expandLevel]);

    // 搜索词变化 → 重算 search 自动展开祖先(term 空 → [])
    useEffect(() => {
        if (!data) {
            setSearchExpandedKeys([]);
            return;
        }
        if (!searchTerm) {
            setSearchExpandedKeys([]);
            return;
        }
        setSearchExpandedKeys(Array.from(collectMatchAncestorKeys(data, searchTerm)));
    }, [searchTerm, data]);

    // 合并 user + search 给 antd
    const expandedKeys = useMemo<React.Key[]>(
        () => Array.from(new Set([...userExpandedKeys, ...searchExpandedKeys])),
        [userExpandedKeys, searchExpandedKeys],
    );

    const treeData = useMemo(() => buildAntTreeData(data, searchTerm), [data, searchTerm]);

    // antd onExpand 给的是合并后 keys → 反推 user 部分(去掉 search 自动展开的祖先,其余视为用户手动)
    const onExpand: TreeProps['onExpand'] = (keys) => {
        const searchSet = new Set(searchExpandedKeys);
        // 用户手动 caret = 合并后 keys 减去 search 自动展开部分
        setUserExpandedKeys(keys.filter(k => !searchSet.has(k)));
    };

    // antd loadData:懒加载未加载容器(toAntNode children=undefined)触发。node.rawData 挂原始 TreeNodeData。
    const loadData = useCallback((node: {rawData?: TreeNodeData}) => {
        const rawData = node?.rawData;
        if (!rawData) return Promise.resolve();
        // loadChildren 是 thunk,dispatch 返回 thunk 的 Promise;resolve 后 reducer 已更新 data → re-render → antd 停 loading
        const result = dispatch(ACTIONS.loadChildren(rawData, undefined, rawData.name, undefined) as unknown);
        return Promise.resolve(result as unknown).then(() => undefined);
    }, [dispatch]);

    const onSelect: TreeProps['onSelect'] = (keys, info) => {
        setSelectedKeys(keys);
        const node = info.node as {rawData?: TreeNodeData} | undefined;
        if (node?.rawData) {
            // V7.23:老 4 库(变量/常量/参数/动作库)编辑器已删除,点击走 seeFileSource
            // 只读源码查看(第 5 参回调);其余类型仍 window.open 各自 SPA 编辑器。
            handleFileOpen(
                node.rawData,
                treeType,
                !!readOnly,
                onFileReadOnlyClick,
                (d: TreeNodeData) => dispatch(ACTIONS.seeFileSource(d) as unknown),
            );
        }
    };

    return (
        <div className="rf-file-tree-wrap">
            <Input.Search
                className="rf-tree-search"
                placeholder="搜索文件/项目"
                allowClear
                size="small"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
            />
            <AntTree
                className="rf-file-tree"
                treeData={treeData}
                expandedKeys={expandedKeys}
                onExpand={onExpand}
                selectedKeys={selectedKeys}
                onSelect={onSelect}
                loadData={loadData as TreeProps['loadData']}
                showLine={{showLeafIcon: false}}
                showIcon
                blockNode
                virtual={false}
                titleRender={(node: TreeDataNode) => {
                    const raw = (node as {rawData?: TreeNodeData}).rawData;
                    if (!raw) return null;
                    return (
                        <FileTreeNode
                            data={raw}
                            dispatch={dispatch}
                            treeType={treeType}
                            readOnly={readOnly}
                            searchTerm={searchTerm}
                        />
                    );
                }}
            />
        </div>
    );
}

/** connect:treeType 选数据源(跟老 Tree.tsx selector 一致)。dispatch 由 connect 注入。 */
function mapStateToProps(state: {data: TreeNodeData | null; publicResource: TreeNodeData | null}, ownProps: FileTreeOwnProps) {
    return {
        data: ownProps.treeType === 'public' ? state.publicResource : state.data,
    };
}

export default connect(mapStateToProps)(FileTreeImpl);
