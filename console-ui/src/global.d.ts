/**
 * Global type declarations for RuleForge frontend
 *
 * Window extensions and module-level globals.
 */

// ---- Window extensions ----

interface TreeNodeData {
    id: string;
    name: string;
    type: string;
    fullPath: string;
    children?: TreeNodeData[];
    _level?: number;
    _icon?: string | Record<string, string>;
    _style?: string | Record<string, string>;
    contextMenu?: ContextMenuItem[];
    editorPath?: string | (() => void);
    folderType?: string;
    project?: string;
    /** Force-expand flag for tree navigation */
    _forceExpand?: boolean;
    /** Lazy-load flag: node supports lazy-loading children */
    _needLazyLoad?: boolean;
    /** Whether children have been loaded for a lazy-load node */
    _childrenLoaded?: boolean;
    /** Lock state of the node */
    lock?: boolean;
    /** Lock information text */
    lockInfo?: string;
    [key: string]: unknown;
}

interface ContextMenuItem {
    name: string;
    icon?: string | Record<string, string>;
    // V5.74.3:click 第二参接收 dispatch(thunk 化菜单项用,如 seeFileSource),
    // 旧菜单项忽略即可,MenuItem.tsx 一直传 dispatch 进来。
    click?: (data: TreeNodeData, dispatch?: (action: unknown) => void) => void;
}

interface UserInfo {
    username: string;
    [key: string]: unknown;
}

// ---- RuleForge menu namespace (loaded from public/js/menu.js) ----

interface MenuItemConfig {
    name?: string;
    label: string;
    icon?: string;
    datatype?: string;
    act?: string;
    defaultValue?: string | boolean;
    editorType?: number;
    parent?: MenuItemConfig;
    subMenu?: { menuItems: MenuItemConfig[] };
    variables?: unknown[];
    onClick?: (menuItem: MenuItemConfig) => void;
}

interface MenuConfig {
    menuItems: MenuItemConfig[];
    onHide?: () => void;
}

interface MenuInstance {
    show: (e: React.MouseEvent | MouseEvent) => void;
    hide: () => void;
    setConfig: (config: MenuConfig) => void;
}

interface MenuConstructor {
    new (config: MenuConfig): MenuInstance;
}

// MsgBox - Global confirm dialog utility (loaded from bundled legacy code)
declare const MsgBox: {
    confirm(message: string, callback: () => void): void;
    alert(message: string, callback?: () => void): void;
};

// ---- ruleforge backward-compat global namespace ----
// Legacy prototype-based classes register themselves here during migration.
// As files are converted to ES modules, they still assign to this object
// so that unconverted consumers (ruleforge.XXX) keep working.
declare const ruleforge: Record<string, any>;

declare namespace RuleForge {
    namespace menu {
        const Menu: MenuConstructor;
    }
    function alert(message: string): void;
    function setDomContent(container: HTMLElement, text: string): void;
}

// ---- Component event module type ----

interface ComponentEventModule {
    SHOW_LOADING: string;
    HIDE_LOADING: string;
    TREE_NODE_CLICK: string;
    TREE_DIR_NODE_CLICK: string;
    OPEN_KNOWLEDGE_TREE_DIALOG: string;
    HIDE_KNOWLEDGE_TREE_DIALOG: string;
    OPEN_VERSION_SELECT_DIALOG: string;
    HIDE_VERSION_SELECT_DIALOG: string;
    OPEN_QUICK_TEST_DIALOG: string;
    HIDE_QUICK_TEST_DIALOG: string;
    OPEN_RESOURCE_VERSION_DIALOG: string;
    CLOSE_RESOURCE_VERSION_DIALOG: string;
    OPEN_CONDITION_LIST_DIALOG: string;
    CLOSE_CONDITION_LIST_DIALOG: string;
    REFRESH_CONDITION_LIST_DIALOG: string;
    OPEN_RESOURCE_LIST_DIALOG: string;
    CLOSE_RESOURCE_LIST_DIALOG: string;
    eventEmitter: import('events').EventEmitter;
}

// ---- CSS module declarations ----

declare module '*.css' {
    const content: string;
    export default content;
}

// ---- Node.js polyfill declarations ----

declare module 'events' {
    class EventEmitter {
        on(event: string, listener: (...args: any[]) => void): this;
        emit(event: string, ...args: any[]): boolean;
        removeListener(event: string, listener: (...args: any[]) => void): this;
        removeAllListeners(event?: string): this;
    }
    export { EventEmitter };
    export default EventEmitter;
}

// Vitest global
declare const global: typeof globalThis;

// ---- Window extensions ----

interface SimulatorCategoryItem {
    clazz: string;
    name: string;
    variables: SimulatorVariable[];
}

interface SimulatorVariable {
    name: string;
    label: string;
}

// V5.74.4:知识包仿真器分类数据 — 替代 window.simulatorCategoryData 全局变量。
// Provider 由 SimulatorPage 挂载(只有仿真器打开时才在 React 树内),
// Cell 通过 contextType 读。null 表示无 Provider(非仿真器场景下 Cells 看到空数组)。
// 实际 createContext 实例在 components/grid/SimulatorCategoryContext.ts。
declare const SimulatorCategoryContext: typeof import('./components/grid/SimulatorCategoryContext').SimulatorCategoryContext;

interface Window {
    // Server config (V5.72: _server 已移除,apiBase 改纯 Vite env)
    _projectName: string | null;
    _types: string | null;
    _classify: boolean;
    _project: string | null;
    // V5.74.3:已移除 _currentGitTag 全局变量,改 Redux ui.currentGitTag(FileTreePanel
    // setCurrentGitTag dispatch,seeFileSource thunk 通过 getState() 读)。

    // Current user
    // V5.74.2:已移除 __currentUser 全局变量,改纯 React CurrentUserContext(由 RequireAuth 注入)。

    // Component event bus
    componentEvent: import('@/components/componentEvent.js').ComponentEventModule;
    refEvent: unknown;

    // Server URL override (used by ResourceListDialogComponent)
    ruleforgeServer?: string;

    // V7.23:老编辑器 window 全局缓存已随编辑器下线删除 ——
    // _ruleforgeEditor{Action,Constant,Parameter,Variable,Function}Libraries、
    // _VariableValueArray / _ParameterValueArray / _currentConditionCell 均无写入方。

    // Clipboard state (cut/copy file operations)
    ___cutFileData: TreeNodeData | null;
    ___copyFileData: TreeNodeData | null;

    // Dirty flag callbacks
    _setDirty: (() => void) | undefined;
    setDirty: ((dirty: boolean) => void) | undefined;
    cancelDirty: (() => void) | undefined;

    // Dirty state
    _dirty: boolean;

    // File search term (used by FileTreePanel)
    searchFileName: string;
}
