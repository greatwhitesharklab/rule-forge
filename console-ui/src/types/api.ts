/**
 * API response type definitions
 */

export interface ApiResponse {
    status: boolean;
    msg?: string;
    [key: string]: unknown;
}

export interface ProjectListResponse {
    repo: {
        rootFile: import('./tree').TreeNodeData;
        publicResource: import('./tree').TreeNodeData;
        projectNames: string[];
    };
    classify: boolean;
    user?: {
        import: boolean;
        export: boolean;
    };
}

export interface FileSourceResponse {
    content: string;
}

export interface FileVersionsResponse {
    files: import('./tree').FileVersion[];
    count: number;
}

export interface SimulationProgress {
    runId: number;
    status: string;
    totalLogs: number;
    totalCompared: number;
    totalDivergent: number;
    divergenceRate: number;
    highSeverityCount: number;
    mediumSeverityCount: number;
    lowSeverityCount: number;
    errorMessage: string | null;
}

export interface SimulationResult {
    id: number;
    originalFlowLogId: number;
    originalExecutionStatus: string;
    originalRejectCode: string;
    simulatedExecutionStatus: string;
    simulatedRejectCode: string;
    statusMatch: boolean;
    resultMatch: boolean;
    hasDivergence: boolean;
    divergenceSeverity: string;
    outputDivergence: string | null;
    ruleDivergence: string | null;
    errorMessage: string | null;
}

export interface SimulationStats {
    totalRuns: number;
    totalLogs: number;
    totalCompared: number;
    totalDivergent: number;
    averageDivergenceRate: number;
}
