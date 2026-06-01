/**
 * Global type declarations for RuleForge frontend
 *
 * Window extensions, bootbox, and module-level globals.
 */

// ---- Window extensions ----

interface BootboxStatic {
    alert(message: string, callback?: () => void): unknown;
    alert(options: { title?: string; message: string; callback?: () => void }): unknown;
    confirm(message: string, callback: (result: boolean) => void): unknown;
    prompt(message: string, callback: (result: string | null) => void): unknown;
    dialog(options: Record<string, unknown>): unknown;
    setDefaults(): void;
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

// ---- CodeMirror declaration ----

interface CodeMirrorEditor {
    setSize(width: string, height: string | number): void;
    setValue(content: string): void;
    getValue(): string;
    refresh(): void;
    toTextArea(): void;
    on(event: string, handler: (...args: any[]) => void): void;
    setOption(option: string, value: unknown): void;
    getOption(option: string): unknown;
    getCursor(start?: string): CodeMirror.Position;
    getTokenAt(pos: CodeMirror.Position, precise?: boolean): CodeMirror.Token;
    getModeAt(pos: CodeMirror.Position): any;
    getMode(): any;
    startState(): any;
    getStateAfter(line?: number): any;
    showHint(options?: Record<string, unknown>): void;
    replaceSelection(replacement: string): void;
    _library?: any;
}

declare namespace CodeMirror {
    interface Position {
        line: number;
        ch: number;
    }

    interface Token {
        start: number;
        end: number;
        string: string;
        type: string | null;
        state: any;
    }

    function fromTextArea(textarea: HTMLTextAreaElement, options?: Record<string, unknown>): CodeMirrorEditor;
    type Editor = CodeMirrorEditor;
    var Pos: { (line: number, ch: number): Position };
    var Pass: {};
    var commands: Record<string, (cm: CodeMirrorEditor) => void>;
    var hint: Record<string, any>;
    function innerMode(mode: any, state: any): { state: any; mode: any };
    function getMode(config: any, mode: string | any): any;
    function copyState(mode: any, state: any): any;
    function defineSimpleMode(name: string, spec: Record<string, any>): void;
    function defineMode(name: string, factory: (config: any, parserConfig: any) => any, ...deps: string[]): void;
    function registerHelper(type: string, name: string, value: any): void;
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

    // Server URL override (used by ResourceListDialogComponent)
    ruleforgeServer?: string;

    // Library caches (set by editor entry points)
    _ruleforgeEditorActionLibraries: unknown[];
    _ruleforgeEditorConstantLibraries: unknown[];
    _ruleforgeEditorParameterLibraries: unknown[];
    _ruleforgeEditorVariableLibraries: unknown[];
    _ruleforgeEditorFunctionLibraries?: unknown[];

    // Clipboard state (cut/copy file operations)
    ___cutFileData: TreeNodeData | null;
    ___copyFileData: TreeNodeData | null;

    // Dirty flag callbacks
    _setDirty: (() => void) | undefined;
    setDirty: ((dirty: boolean) => void) | undefined;
    cancelDirty: (() => void) | undefined;

    // Dirty state
    _dirty: boolean;

    // Widget registration arrays (used by scorecard editors)
    _VariableValueArray: Array<{ initMenu?(data: unknown[]): void }>;
    _ParameterValueArray: Array<{ initMenu?(data: unknown[]): void }>;

    // Current selection state for complex scorecard
    _currentConditionCell: any;

    // Simulator category data (used in Cell editor)
    simulatorCategoryData: SimulatorCategoryItem[] | undefined;

    // Frame styles
    _frameStyles: Record<string, unknown> | undefined;

    // File search term (used by SidebarToolbar, FileTreePanel)
    searchFileName: string;

    // Welcome page
    _welcomePage: string;
}
