import React, {Component} from 'react';
import TreeParentItem from './TreeParentItem';
import Menu from '../../menu/component/Menu';
import * as event from '../../componentEvent.js';
import * as ACTIONS from '../../../frame/action.js';

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
                            const editorBasePath = this.props.treeType === 'public' ? '/html/editor.html?type=resource' : data.editorPath;

                            let url = '.' + editorBasePath + "?file=" + data.fullPath;
                            let fullPath = data.fullPath;
                            if (data.type === 'resourcePackage') {
                                const packageName = data.fullPath.split("/")[1];
                                url = '.' + data.editorPath + "?file=" + packageName + '.rp';
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
