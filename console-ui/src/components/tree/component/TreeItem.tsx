import React, {Component} from 'react';
import TreeParentItem from './TreeParentItem';
import Menu from '../../menu/component/Menu';
import * as ACTIONS from '../../../frame/action.js';

interface TreeItemProps {
    data: TreeNodeData;
    dispatch?: (action: unknown) => void;
    selectDir?: (data: TreeNodeData) => void;
    expandLevel?: number;
    treeType?: string;
    draggable?: boolean;
    /**
     * V6.13.1:只读模式。
     * <ul>
     *   <li>右键 context menu 不弹 (无新建/重命名/删除/锁定 等编辑入口)</li>
     *   <li>文件 click 走 {@link onFileReadOnlyClick} 而非 window.open 编辑器</li>
     *   <li>父组件 (FileTreePanel) 选 git 版本时传 true,选 working tree 时传 false</li>
     * </ul>
     */
    readOnly?: boolean;
    /**
     * V6.13.1:readOnly 模式下文件 click 触发的回调,代替默认 window.open 打开编辑器。
     * FileTreePanel 接到后 dispatch seeFileSource thunk,弹源码对话框 (走 git snapshot)。
     */
    onFileReadOnlyClick?: (data: TreeNodeData) => void;
}

interface TreeItemState {
    expanded: boolean;
    contextMenuVisible: boolean;
    contextMenuX: number;
    contextMenuY: number;
}

class TreeItem extends Component<TreeItemProps, TreeItemState> {
    liRef: React.RefObject<HTMLLIElement>;

    constructor(props: TreeItemProps) {
        super(props);
        const expandLevel = props.expandLevel || 3;
        const data = props.data;
        const initiallyExpanded = data._forceExpand || !((data._level || 1) >= expandLevel);
        this.state = {
            expanded: initiallyExpanded,
            contextMenuVisible: false,
            contextMenuX: 0,
            contextMenuY: 0
        };
        this.liRef = React.createRef();
        this._handleSpanClick = this._handleSpanClick.bind(this);
        this._handleContextMenu = this._handleContextMenu.bind(this);
        this._handleClickOutside = this._handleClickOutside.bind(this);
    }

    componentDidUpdate(prevProps: TreeItemProps) {
        if (this.props.data._forceExpand && !prevProps.data._forceExpand) {
            this.setState({expanded: true});
        }
    }

    componentDidMount() {
        document.addEventListener('click', this._handleClickOutside);
    }

    componentWillUnmount() {
        document.removeEventListener('click', this._handleClickOutside);
    }

    _handleClickOutside() {
        if (this.state.contextMenuVisible) {
            this.setState({contextMenuVisible: false});
        }
    }

    _handleContextMenu(e: React.MouseEvent<HTMLSpanElement>) {
        // V6.13.1:readOnly 模式禁右键 context menu (新建/重命名/删除等编辑入口全无)
        if (this.props.readOnly) {
            e.preventDefault();
            return;
        }
        const contextMenu = this.props.data.contextMenu;
        if (!contextMenu || contextMenu.length === 0) {
            return;
        }
        e.preventDefault();
        e.stopPropagation();
        this.setState({
            contextMenuVisible: true,
            contextMenuX: e.clientX,
            contextMenuY: e.clientY
        });
    }

    _handleSpanClick(e: React.MouseEvent<HTMLSpanElement>) {
        const li = this.liRef.current;
        if (!li || !li.classList.contains("parent_li")) {
            return;
        }
        const {data, dispatch} = this.props;
        const isExpanded = this.state.expanded;

        if (isExpanded) {
            this.setState({expanded: false});
        } else {
            this.setState({expanded: true});
            if (data._needLazyLoad && !data._childrenLoaded) {
                // loadChildren thunk 从 frame store 读 classify/types,projectName 传节点名
                // (与历史行为一致:子菜单加载按节点名查 project,不按过滤的当前项目)。
                dispatch?.(ACTIONS.loadChildren(
                    data,
                    undefined,
                    data.name,
                    undefined
                ) as unknown);
            }
        }
        e.stopPropagation();
    }

    isFile(): boolean {
        const data = this.props.data;
        const name = data.name;
        let isFile = false;
        if (name.indexOf(".") > -1 || name === "ul" || name === 'rp') {
            isFile = true;
        }
        return isFile;
    }

    render() {
        const {data, dispatch, readOnly} = this.props;
        const children = data.children;
        const spanId = "node-" + data.id, menuId = 'treenodemenu' + data.id;
        const {contextMenuVisible, contextMenuX, contextMenuY} = this.state;
        let menu: React.ReactElement | null = null;
        // V6.13.1:readOnly 模式不挂 Menu (删编辑入口,跟右键禁菜单双保险)
        if (data.contextMenu && !readOnly) {
            menu = <Menu items={data.contextMenu} data={data} dispatch={dispatch} menuId={menuId}
                            visible={contextMenuVisible} x={contextMenuX} y={contextMenuY}/>;
        }
        // Container types: has children (even empty array) or lazy-loadable
        const isContainer = (children && children.length > 0) || Array.isArray(children)
            || (data._needLazyLoad && !data._childrenLoaded);
        if (isContainer) {
            let expandIcon = this.state.expanded ? 'rf rf-minus' : 'rf rf-plus';
            if (data._needLazyLoad && !data._childrenLoaded) {
                expandIcon = 'rf rf-plus';
            }
            return (
                <li className='parent_li' ref={this.liRef}>
                    <span id={spanId} onClick={this._handleSpanClick} onContextMenu={this._handleContextMenu}>
                        <i className={expandIcon} style={{fontSize: 10, opacity: 0.5}}/>
                        <i className={data._icon as string} style={data._style as React.CSSProperties}/>
                        <a href='#' style={data._style as React.CSSProperties}> {data.name}</a>
                        <sup><i title={data.lock ? data.lockInfo : ''} className={data.lock ? 'rf rf-lock' : ''}/></sup>
                    </span>
                    {menu}
                    <ul style={{display: this.state.expanded ? '' : 'none'}}>
                        {children && children.length > 0 && <TreeParentItem dispatch={dispatch} children={children} expandLevel={this.props.expandLevel} treeType={this.props.treeType}/>}
                    </ul>
                </li>
            );
        } else {
            const isFile = this.isFile();
            return (
                <li>
                    <span id={spanId} onContextMenu={this._handleContextMenu} onClick={(e) => {
                        // V6.13.1:readOnly 模式 (看 git 历史版本) 文件 click 不开编辑器,
                        // 走 onFileReadOnlyClick 回调 → 父组件 dispatch seeFileSource → 弹源码对话框 (走 git snapshot)
                        if (readOnly && this.props.onFileReadOnlyClick && isFile) {
                            this.props.onFileReadOnlyClick(data);
                            e.stopPropagation();
                            return;
                        }
                        if (isFile) {
                            // 已完成 SPA 化的 React 编辑器集合:新标签打开 /app/editor/<type>,
                            // 不走原 iframe (editor.html?type=...)。判断优先级:data.type 直配 >
                            // 文件扩展名 > public 资源树(treeType==='public')。
                            // - ruleset (rule / .rs.xml) → /app/editor/ruleset
                            // - public 资源 (treeType==='public',原 /html/editor.html?type=resource)
                            //   → /app/editor/resource
                            // V7.0.0 item ④:老 7 编辑器(ruleset/decisiontree/decisiontable/
                            // scriptdecisiontable/scorecard/complexscorecard/crosstab)已物理删除,
                            // 其 window.open 路由分支随之移除。V1 画布接管 RuleSet/DecisionTable/ScoreCard;
                            // 4 弃类决策(树/脚本表/复杂评分卡/交叉表)已下线。详见 main.tsx。

                            // 变量库 / 常量库 / 参数库 / 动作库:按 data.type + 完整文件后缀双判定
                            // (注意是 .vl.xml / .cl.xml / .pl.xml / .al.xml 这种双扩展名)。
                            // V7.23:老 4 库编辑器已删除(后端加载端点 POST /xml V5.43 已移除,打开白屏),
                            // 老项目里残留的这类文件点击改走只读源码查看 —— dispatch seeFileSource thunk,
                            // 弹源码对话框(与 FileTreePanel 的 readOnly 查看同一通道)。
                            const libTypeByData: string | null =
                                data.type === 'variable' ? 'variable'
                                : data.type === 'constant' ? 'constant'
                                : data.type === 'parameter' ? 'parameter'
                                : data.type === 'action' ? 'action'
                                : null;
                            const libTypeByExt: string | null = typeof data.fullPath === 'string'
                                ? (data.fullPath.endsWith('.vl.xml') ? 'variable'
                                    : data.fullPath.endsWith('.cl.xml') ? 'constant'
                                    : data.fullPath.endsWith('.pl.xml') ? 'parameter'
                                    : data.fullPath.endsWith('.al.xml') ? 'action'
                                    : null)
                                : null;
                            const libType = libTypeByData || libTypeByExt;
                            if (libType) {
                                dispatch?.(ACTIONS.seeFileSource(data) as unknown);
                                return;
                            }

                            // 公共资源树(treeType==='public'):原走 /html/editor.html?type=resource,
                            // 现 SPA 化为 /app/editor/resource
                            if (this.props.treeType === 'public') {
                                window.open('/app/editor/resource?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // V7.22:知识包(.rp / resourcePackage)入口已删除 — V1 发布(V1PublishService)
                            //   替代老 .rp 管线,知识包编辑器路由 V7.7.2 已删,点击是空白页。
                            // V7.21:BPMN 决策流(.rl.xml / flow)入口已删除 — V1 决策流为唯一决策路径。

                            // 所有 SPA 化编辑器类型(rule/decisionTable/.../libs/public)
                            // 已在上方各自分支 window.open 后 return。到达此处的类型(如 .ul.xml 脚本决策集)
                            // 尚无 SPA 路由,原走 iframe + TREE_NODE_CLICK 的通道在 SPA 化后已废弃
                            // (FrameTab 不再托管 iframe)。保留节点选中视觉反馈,不再尝试打开编辑器。
                            document.querySelectorAll('.tree .tree-active').forEach(el => el.classList.remove('tree-active'));
                            const spanEl = document.getElementById(spanId);
                            if (spanEl) spanEl.classList.add('tree-active');
                        }
                    }}>
                        <i className={data._icon as string} style={data._style as React.CSSProperties}/> <a href='#' style={data._style as React.CSSProperties}> {data.name}</a>
                        <sup><i title={data.lock ? data.lockInfo : ''} className={data.lock ? 'rf rf-lock' : ''}/></sup>
                    </span>
                    {menu}
                </li>
            );
        }
    }
}

export default TreeItem;
