/**
 * Redux store type definitions
 */

export interface UIState {
    activePanel: string;
    monitoringTab: string;
    simulationTab: string;
}

export interface TreeNodeState {
    data: import('./tree').TreeNodeData[];
    publicResource: import('./tree').TreeNodeData | null;
}

export interface DatasourceState {
    datasources: DatasourceItem[];
    loading: boolean;
}

export interface DatasourceItem {
    id: string;
    name: string;
    type: string;
    [key: string]: unknown;
}

export interface ReleaseState {
    packages: ReleasePackage[];
    loading: boolean;
}

export interface ReleasePackage {
    id: string;
    name: string;
    version: string;
    status: string;
    [key: string]: unknown;
}

export interface RootState {
    data: import('./tree').TreeNodeData[];
    publicResource: import('./tree').TreeNodeData | null;
    ui: UIState;
    datasource: DatasourceState;
    release: ReleaseState;
    [key: string]: unknown;
}

// Redux action types
export interface BaseAction {
    type: string;
    [key: string]: unknown;
}
