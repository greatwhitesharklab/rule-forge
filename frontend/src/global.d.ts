/**
 * Global type declarations for RuleForge frontend
 *
 * Window extensions, bootbox, and module-level globals.
 */

// ---- Window extensions ----

interface BootboxStatic {
    alert(message: string, callback?: () => void): void;
    alert(options: { title?: string; message: string }): void;
    confirm(message: string, callback: (result: boolean) => void): void;
    prompt(message: string, callback: (result: string) => void): void;
    dialog(options: Record<string, unknown>): void;
}

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
    click?: (data: TreeNodeData) => void;
}

interface UserInfo {
    username: string;
    [key: string]: unknown;
}

// ---- RuleForge menu namespace (loaded from public/js/menu.js) ----

interface MenuItemConfig {
    name: string;
    label: string;
    icon?: string;
    datatype?: string;
    act?: string;
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

declare namespace RuleForge {
    namespace menu {
        const Menu: MenuConstructor;
    }
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

// ---- CodeMirror declaration ----

interface CodeMirrorEditor {
    setSize(width: string, height: string): void;
    setValue(content: string): void;
    getValue(): string;
    refresh(): void;
    toTextArea(): void;
    on(event: string, handler: (...args: any[]) => void): void;
    setOption(option: string, value: unknown): void;
    getOption(option: string): unknown;
}

declare namespace CodeMirror {
    function fromTextArea(textarea: HTMLTextAreaElement, options?: Record<string, unknown>): CodeMirrorEditor;
    type Editor = CodeMirrorEditor;
}

declare module 'codemirror' {
    export = CodeMirror;
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

interface Window {
    // Iframe counter
    iframe_id_: number;

    // Server config
    _server: string;
    _projectName: string | null;
    _types: string | null;
    _classify: boolean;
    _project: string | null;
    _welcomePage: string;
    _currentGitTag: string | null;

    // Current user
    __currentUser: UserInfo | undefined;

    // Bootbox dialog library (custom implementation)
    bootbox: BootboxStatic;

    // Component event bus
    componentEvent: import('@/components/componentEvent.js').ComponentEventModule;
    refEvent: unknown;

    // Library caches (set by editor entry points)
    _ruleforgeEditorActionLibraries: unknown[];
    _ruleforgeEditorConstantLibraries: unknown[];
    _ruleforgeEditorParameterLibraries: unknown[];
    _ruleforgeEditorVariableLibraries: unknown[];

    // Clipboard state (cut/copy file operations)
    ___cutFileData: TreeNodeData | null;
    ___copyFileData: TreeNodeData | null;

    // Dirty flag callbacks
    _setDirty: (() => void) | undefined;
    setDirty: ((dirty: boolean) => void) | undefined;

    // Dirty state
    _dirty: boolean;

    // Simulator category data (used in Cell editor)
    simulatorCategoryData: SimulatorCategoryItem[] | undefined;

    // Frame styles
    _frameStyles: Record<string, unknown> | undefined;

    // File search term (used by SidebarToolbar, FileTreePanel)
    searchFileName: string;

    // Welcome page
    _welcomePage: string;
}
