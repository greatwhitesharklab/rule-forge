import React, {Component} from 'react';
import TreeParentItem from './TreeParentItem';
import Menu from '../../menu/component/Menu';
import * as event from '../../componentEvent.js';
import * as ACTIONS from '../../../frame/action.js';
import {buildEditorUrl} from '../../../Utils.ts';

interface TreeItemProps {
    data: TreeNodeData;
    dispatch?: (action: unknown) => void;
    selectDir?: (data: TreeNodeData) => void;
    expandLevel?: number;
    treeType?: string;
    draggable?: boolean;
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
                dispatch?.(ACTIONS.loadChildren(
                    data,
                    window._classify,
                    data.name,
                    window._types
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
        const {data, dispatch} = this.props;
        const children = data.children;
        const spanId = "node-" + data.id, menuId = 'treenodemenu' + data.id;
        const {contextMenuVisible, contextMenuX, contextMenuY} = this.state;
        let menu: React.ReactElement | null = null;
        if (data.contextMenu) {
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
                        if (isFile) {
                            // 已完成 SPA 化的 React 编辑器集合:新标签打开 /app/editor/<type>,
                            // 不走原 iframe (editor.html?type=...)。判断优先级:data.type 直配 >
                            // 文件扩展名 > public 资源树(treeType==='public')。
                            // - ruleset (rule / .rs.xml) → /app/editor/ruleset
                            // - variable (.vl.xml) / constant (.cl.xml) / parameter (.pl.xml)
                            //   / action (.al.xml) → 对应 /app/editor/<type>
                            // - public 资源 (treeType==='public',原 /html/editor.html?type=resource)
                            //   → /app/editor/resource
                            const isRuleset = data.type === 'rule'
                                || (typeof data.fullPath === 'string' && data.fullPath.endsWith('.rs.xml'));
                            if (isRuleset) {
                                window.open('/app/editor/ruleset?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // 决策树 (decisionTree / .dtree.xml) → /app/editor/decisiontree
                            // React 重写,替代原 iframe + Raphael 画布 editor.html?type=decisiontree。
                            // 注意:.dtree.xml 是决策树,.dt.xml 是决策表(不同扩展名)。
                            const isDecisionTree = data.type === 'decisionTree'
                                || (typeof data.fullPath === 'string' && data.fullPath.endsWith('.dtree.xml'));
                            if (isDecisionTree) {
                                window.open('/app/editor/decisiontree?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // 决策表 (decisionTable / .dt.xml) → /app/editor/decisiontable
                            // React 重写,替代原 iframe editor.html?type=decisionTable。
                            const isDecisionTable = data.type === 'decisionTable'
                                || (typeof data.fullPath === 'string' && data.fullPath.endsWith('.dt.xml'));
                            if (isDecisionTable) {
                                window.open('/app/editor/decisiontable?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // 脚本式决策表 (scriptDecisionTable / .sdt.xml) → /app/editor/scriptdecisiontable
                            // React 重写,替代原 iframe editor.html?type=scriptdecisiontable。
                            // 与决策表共享列/行/库结构,单元格内容是 UL 脚本(CDATA)。
                            const isScriptDecisionTable = data.type === 'scriptDecisionTable'
                                || (typeof data.fullPath === 'string' && data.fullPath.endsWith('.sdt.xml'));
                            if (isScriptDecisionTable) {
                                window.open('/app/editor/scriptdecisiontable?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // 评分卡 (scorecard / .sc.xml) → /app/editor/scorecard
                            // React 重写,替代原 iframe editor.html?type=scorecard。
                            const isScoreCard = data.type === 'scorecard'
                                || (typeof data.fullPath === 'string' && data.fullPath.endsWith('.sc.xml'));
                            if (isScoreCard) {
                                window.open('/app/editor/scorecard?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // 复杂评分卡 (complexscorecard / .complexscorecard) → /app/editor/complexscorecard
                            // React 重写,替代原 iframe editor.html?type=complexscorecard。
                            // 文件后缀 = FileType.ComplexScorecard.toString().toLowerCase() = "complexscorecard"
                            // (后端 ComplexScorecardFileRefactor.support 用 path.toLowerCase().endsWith("complexscorecard") 判定)。
                            const isComplexScoreCard = data.type === 'complexscorecard'
                                || (typeof data.fullPath === 'string' && data.fullPath.toLowerCase().endsWith('.complexscorecard'));
                            if (isComplexScoreCard) {
                                window.open('/app/editor/complexscorecard?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // 交叉决策表 (crosstab / .ct.xml) → /app/editor/crosstab
                            // React 重写,替代原 iframe editor.html?type=crosstab。
                            const isCrosstab = data.type === 'crosstab'
                                || (typeof data.fullPath === 'string' && data.fullPath.endsWith('.ct.xml'));
                            if (isCrosstab) {
                                window.open('/app/editor/crosstab?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // 变量库 / 常量库 / 参数库 / 动作库:按 data.type + 完整文件后缀双判定
                            // (注意是 .vl.xml / .cl.xml / .pl.xml / .al.xml 这种双扩展名)
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
                                window.open('/app/editor/' + libType + '?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // 公共资源树(treeType==='public'):原走 /html/editor.html?type=resource,
                            // 现 SPA 化为 /app/editor/resource
                            if (this.props.treeType === 'public') {
                                window.open('/app/editor/resource?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            // 知识包(resourcePackage, .rp):原走 iframe
                            // editor.html?type=package,现 SPA 化为 /app/editor/package。
                            // file 参数沿用原 buildEditorUrl 的格式 <packageName>.rp(无路径前缀),
                            // EditorRoute 内部 .replace('.rp','') 得到 project 名。
                            if (data.type === 'resourcePackage') {
                                const packageName = data.fullPath.split('/')[1];
                                window.open('/app/editor/package?file=' + encodeURIComponent(packageName + '.rp'), '_blank');
                                return;
                            }

                            // 决策流 (flow / bpmn-js / .rl.xml) → /app/editor/flow
                            // SPA 化,替代原 iframe editor.html?type=flowbpmn。
                            // FlowEditor.tsx (bpmn-js React 包装) + EditorRoute 复现 index.tsx 挂载。
                            const isFlow = data.type === 'flow'
                                || (typeof data.fullPath === 'string' && data.fullPath.endsWith('.rl.xml'));
                            if (isFlow) {
                                window.open('/app/editor/flow?file=' + encodeURIComponent(data.fullPath), '_blank');
                                return;
                            }

                            const editorBasePath = this.props.treeType === 'public' ? '/html/editor.html?type=resource' : data.editorPath;

                            let url = buildEditorUrl(editorBasePath, data.fullPath);
                            let fullPath = data.fullPath;
                            if (data.type === 'resourcePackage') {
                                const packageName = data.fullPath.split("/")[1];
                                url = buildEditorUrl(data.editorPath, packageName + '.rp');
                                fullPath = '/' + packageName;
                            }

                            event.eventEmitter.emit(event.TREE_NODE_CLICK, {
                                id: data.id,
                                name: data.name,
                                fullPath: fullPath,
                                path: url,
                                active: true
                            });
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
