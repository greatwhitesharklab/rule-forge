/**
 * 仿真 API 调用
 */

import {jsonPost, httpGet} from '../api/client.js';

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
    jsonPost<SimulationStartResult>('/simulation/startSimulation', params, {silent: true})
        .then(function (data: SimulationStartResult) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('启动仿真失败', err);
            if (callback) callback({error: true, message: '启动仿真失败'});
        });
}

export function getSimulationProgress(runId: string, callback?: (data: SimulationProgressData) => void) {
    httpGet<SimulationProgressData>('/simulation/simulationProgress?runId=' + runId, {silent: true})
        .then(function (data: SimulationProgressData) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('查询仿真进度失败', err);
            if (callback) callback({status: 'ERROR'});
        });
}

export function loadSimulationResults(runId: string, page: number, size: number, callback?: (data: SimulationResultsData) => void) {
    httpGet<SimulationResultsData>('/simulation/simulationResults?runId=' + runId + '&page=' + page + '&size=' + size, {silent: true})
        .then(function (data: SimulationResultsData) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('加载仿真结果失败', err);
            if (callback) callback({results: [], page: page, size: size});
        });
}

export function loadSimulationRuns(rulePackagePath: string, page: number, size: number, callback?: (data: SimulationRunsData) => void) {
    const path = '/simulation/simulationRuns?rulePackagePath=' + encodeURIComponent(rulePackagePath) + '&page=' + page + '&size=' + size;
    httpGet<SimulationRunsData>(path, {silent: true})
        .then(function (data: SimulationRunsData) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('加载仿真历史失败', err);
            if (callback) callback({runs: [], page: page, size: size});
        });
}

export function loadSimulationStats(rulePackagePath: string, startTime: string | null, endTime: string | null, callback?: (data: SimulationStatsData) => void) {
    let path = '/simulation/simulationStats?rulePackagePath=' + encodeURIComponent(rulePackagePath);
    if (startTime) path += '&startTime=' + encodeURIComponent(startTime);
    if (endTime) path += '&endTime=' + encodeURIComponent(endTime);
    httpGet<SimulationStatsData>(path, {silent: true})
        .then(function (data: SimulationStatsData) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('加载仿真统计失败', err);
            if (callback) callback({});
        });
}
