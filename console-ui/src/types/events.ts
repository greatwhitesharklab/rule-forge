/**
 * Event bus type definitions
 *
 * Three event buses: frame/event, components/componentEvent, components/grid/componentEvent
 */

export interface FrameEventMap {
    TREE_NODE_CLICK: import('./tree').TreeNodeData;
    OPEN_CREATE_FILE_DIALOG: { fileType: string; nodeData: import('./tree').TreeNodeData };
    OPEN_CREATE_FOLDER_DIALOG: { nodeData: import('./tree').TreeNodeData };
    OPEN_NEW_PROJECT_DIALOG: import('./tree').TreeNodeData;
    OPEN_IMPORT_PROJECT_DIALOG: void;
    CLOSE_CREATE_FILE_DIALOG: void;
    CLOSE_CREATE_FOLDER_DIALOG: void;
    CLOSE_NEW_PROJECT_DIALOG: void;
    CLOSE_UPDATE_PROJECT_DIALOG: void;
    OPEN_SOURCE_DIALOG: [string, string]; // [fullPath, content]
    OPEN_FILE_VERSION_DIALOG: { files: unknown[]; data: import('./tree').TreeNodeData; num: number };
    SHOW_RENAME_DIALOG: import('./tree').TreeNodeData;
    HIDE_RENAME_DIALOG: void;
    EXPAND_TREE_NODE: { id: string };
    CHANGE_CLASSIFY: boolean;
    PROJECT_SELECT: string;
    PROJECT_FILTER_CHANGE: string;
    PROJECT_LIST_CHANGE: string[];
    OPEN_FILE: import('./tree').TreeNodeData;
}

export interface ComponentEventMap {
    SHOW_LOADING: void;
    HIDE_LOADING: void;
}

export type EventMap = FrameEventMap | ComponentEventMap;

/**
 * Typed event emitter wrapper
 */
export interface TypedEventEmitter<T extends Record<string, unknown>> {
    on<K extends keyof T & string>(event: K, listener: T[K] extends void ? () => void : (data: T[K]) => void): void;
    emit<K extends keyof T & string>(event: K, ...args: T[K] extends void ? [] : [T[K]]): void;
    removeAllListeners(event?: string): void;
}
