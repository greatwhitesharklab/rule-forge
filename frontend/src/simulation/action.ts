/**
 * 仿真 API 调用
 */

export interface SimulationParams {
    project: string;
    packageId: string;
    files: string;
    flowId: string | null;
    startTime: string;
    endTime: string;
    createdBy: string;
}

export interface SimulationStartResult {
    runId?: string;
    error?: boolean;
    message?: string;
}

export interface SimulationProgressData {
    status: string;
    totalLogs?: number;
    totalCompared?: number;
    totalDivergent?: number;
    divergenceRate?: number;
    highSeverityCount?: number;
    mediumSeverityCount?: number;
    lowSeverityCount?: number;
    errorMessage?: string;
}

export interface SimulationResultItem {
    id: string;
    originalFlowLogId: string;
    statusMatch: boolean | number;
    resultMatch: boolean | number;
    divergenceSeverity: string;
    hasDivergence: boolean;
    errorMessage?: string;
}

export interface SimulationResultsData {
    results: SimulationResultItem[];
    page: number;
    size: number;
}

export interface SimulationRun {
    id: string;
    [key: string]: unknown;
}

export interface SimulationRunsData {
    runs: SimulationRun[];
    page: number;
    size: number;
}

export interface SimulationStatsData {
    totalRuns?: number;
    totalLogs?: number;
    totalCompared?: number;
    totalDivergent?: number;
    averageDivergenceRate?: number;
}

export function startSimulation(params: SimulationParams, callback?: (data: SimulationStartResult) => void) {
    fetch(window._server + '/simulation/startSimulation', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(params)
    }).then(function (response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data: SimulationStartResult) {
        if (callback) callback(data);
    }).catch(function (err) {
        console.error('启动仿真失败', err);
        if (callback) callback({error: true, message: '启动仿真失败'});
    });
}

export function getSimulationProgress(runId: string, callback?: (data: SimulationProgressData) => void) {
    fetch(window._server + '/simulation/simulationProgress?runId=' + runId)
        .then(function (response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data: SimulationProgressData) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('查询仿真进度失败', err);
            if (callback) callback({status: 'ERROR'});
        });
}

export function loadSimulationResults(runId: string, page: number, size: number, callback?: (data: SimulationResultsData) => void) {
    const url = window._server + '/simulation/simulationResults?runId=' + runId
        + '&page=' + page + '&size=' + size;
    fetch(url)
        .then(function (response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data: SimulationResultsData) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('加载仿真结果失败', err);
            if (callback) callback({results: [], page: page, size: size});
        });
}

export function loadSimulationRuns(rulePackagePath: string, page: number, size: number, callback?: (data: SimulationRunsData) => void) {
    const url = window._server + '/simulation/simulationRuns?rulePackagePath='
        + encodeURIComponent(rulePackagePath) + '&page=' + page + '&size=' + size;
    fetch(url)
        .then(function (response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data: SimulationRunsData) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('加载仿真历史失败', err);
            if (callback) callback({runs: [], page: page, size: size});
        });
}

export function loadSimulationStats(rulePackagePath: string, startTime: string | null, endTime: string | null, callback?: (data: SimulationStatsData) => void) {
    let params = 'rulePackagePath=' + encodeURIComponent(rulePackagePath);
    if (startTime) params += '&startTime=' + encodeURIComponent(startTime);
    if (endTime) params += '&endTime=' + encodeURIComponent(endTime);
    fetch(window._server + '/simulation/simulationStats?' + params)
        .then(function (response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data: SimulationStatsData) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('加载仿真统计失败', err);
            if (callback) callback({});
        });
}
