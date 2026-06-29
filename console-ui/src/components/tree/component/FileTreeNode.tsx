import React from 'react';
import {Dropdown, Tag} from 'antd';
import {EllipsisOutlined} from '@ant-design/icons';
import {contextMenuToAntItems, highlight} from './treeDataUtils';

interface FileTreeNodeProps {
    data: TreeNodeData;
    dispatch?: (action: unknown) => void;
    treeType?: string;
    readOnly?: boolean;
    searchTerm: string;
}

/**
 * V6.13.5b:antd {@code <Tree>} 的 titleRender 节点。渲染 icon(保留 rf iconfont + 业务色)+
 * 高亮名(搜索命中 `<mark>`)+ lock 图标 + hover `⋯` 操作按钮。
 *
 * <p>右键 + `⋯` 两套菜单入口,共享 {@link TreeNodeData#contextMenu}(经
 * {@link contextMenuToAntItems} 转 antd items)。readOnly 模式(V6.13.1 看版本)两套都禁 ——
 * 节点只展示,文件 click 走 {@code onFileReadOnlyClick} 弹源码,无编辑入口。
 *
 * <p>结构:外层 {@code Dropdown trigger=['contextMenu']} 包根(右键弹菜单),内层
 * {@code Dropdown trigger=['click']} 包 `⋯` 按钮(点击弹同一菜单)。两个 overlay 都是 portal,
 * 不互相嵌套。`⋯` 按钮 stopPropagation 防触发 antd onSelect / 双重菜单。
 */
export default function FileTreeNode({data, dispatch, readOnly, searchTerm}: FileTreeNodeProps) {
    const hasMenu = !readOnly && !!data.contextMenu && data.contextMenu.length > 0;
    const menuProps = hasMenu
        ? {items: contextMenuToAntItems(data.contextMenu, data, dispatch)}
        : {items: [] as never[]};

    const inner = (
        <div className="rf-tree-node" title={data.lock ? data.lockInfo : undefined}>
            <i className={data._icon as string} style={data._style as React.CSSProperties}/>
            <span className="rf-tree-label">{highlight(data.name, searchTerm)}</span>
            {/* V7.7.2:已发布 V1 节点绿色徽标(单 SQL 批量查,V1 publish 后状态回填) */}
            {data._publishedStatus === 'published' && data._publishedVersion && (
                <Tag color="green" className="rf-tree-published-badge">
                    {`已发布 v${String(data._publishedVersion)}`}
                </Tag>
            )}
            {data.lock && <i className="rf rf-lock rf-tree-lock"/>}
            {hasMenu && (
                <Dropdown trigger={['click']} menu={menuProps}>
                    <button
                        type="button"
                        className="rf-tree-actions"
                        aria-label="节点操作"
                        onClick={(e) => e.stopPropagation()}
                        onContextMenu={(e) => e.stopPropagation()}
                    >
                        <EllipsisOutlined/>
                    </button>
                </Dropdown>
            )}
        </div>
    );

    if (!hasMenu) return inner;
    return (
        <Dropdown trigger={['contextMenu']} menu={menuProps}>
            {inner}
        </Dropdown>
    );
}
