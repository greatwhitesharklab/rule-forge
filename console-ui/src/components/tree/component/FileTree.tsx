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
    const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
    const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
    const [searchTerm, setSearchTerm] = useState('');

    // data 变化(loadData 增量 / project 切换)→ 重算初始展开(_forceExpand / _level < expandLevel)
    useEffect(() => {
        setExpandedKeys(collectInitialExpandedKeys(data, expandLevel));
    }, [data, expandLevel]);

    // 搜索 → 自动展开命中节点的所有祖先(否则命中深层节点看不到)
    useEffect(() => {
        if (!searchTerm || !data) return;
        setExpandedKeys((prev) => {
            const ancestors = collectMatchAncestorKeys(data, searchTerm);
            return Array.from(new Set([...prev, ...ancestors]));
        });
    }, [searchTerm, data]);

    const treeData = useMemo(() => buildAntTreeData(data, searchTerm), [data, searchTerm]);

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
            handleFileOpen(node.rawData, treeType, !!readOnly, onFileReadOnlyClick);
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
                onExpand={(keys) => setExpandedKeys(keys)}
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
