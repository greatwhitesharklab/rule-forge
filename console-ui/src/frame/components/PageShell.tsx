import {ReactNode} from 'react';

interface PageShellProps {
    /** 页面标题(首要信息:"我在哪") */
    title?: ReactNode;
    /** 标题下一句说明(可选) */
    description?: ReactNode;
    /** 标题右侧操作区(按钮等,右对齐) */
    actions?: ReactNode;
    /** 标题下方、body 上方的额外区(如 Tabs / FilterBar) */
    toolbar?: ReactNode;
    /** body 是否默认 padding(表格全宽面板可关) */
    bodyPad?: boolean;
    /** body 是否改为 flex 列填充、无 padding、不滚动 —— 给自管布局的面板用(如 chat / dashboard) */
    fill?: boolean;
    children: ReactNode;
}

/**
 * V5.101 统一页面骨架(PageShell)— 布局优先。
 *
 * <p>根治"每个面板各搞各的":所有 dedicated 面板共用同一结构 ——
 * <pre>
 *   PageHeader(标题左 / 操作右,固定高)
 *     └ 可选 toolbar(Tabs / FilterBar)
 *   PageBody(统一 padding,滚动区 / 或 fill 模式 flex 列填充)
 * </pre>
 * 稳定骨架 → 每页"标题/操作/内容"位置固定,扫视路径统一。
 *
 * <p>纯布局(DOM + flex);样式只用 token,服务于层级区分。
 */
export default function PageShell({title, description, actions, toolbar, bodyPad = true, fill = false, children}: PageShellProps) {
    const hasHeader = title || actions;
    const bodyClass = 'page-body' + (fill ? ' fill' : (bodyPad ? '' : ' no-pad'));
    return (
        <div className="page-shell">
            {hasHeader && (
                <header className="page-header">
                    <div className="page-header-text">
                        {title && <h1 className="page-title">{title}</h1>}
                        {description && <p className="page-description">{description}</p>}
                    </div>
                    {actions && <div className="page-header-actions">{actions}</div>}
                </header>
            )}
            {toolbar && <div className="page-toolbar">{toolbar}</div>}
            <div className={bodyClass}>{children}</div>
        </div>
    );
}
