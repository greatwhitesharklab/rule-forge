import {Dispatch} from 'redux';
import {formPost, jsonPost, jsonPut, httpGet, httpDelete} from '../api/client.js';

// Action type constants
const LOAD_ENVIRONMENTS = 'release_load_environments';
const LOAD_ENVIRONMENTS_COMPLETED = 'release_load_environments_completed';
const LOAD_APPROVALS = 'release_load_approvals';
const LOAD_APPROVALS_COMPLETED = 'release_load_approvals_completed';
const LOAD_DEPLOYMENT_HISTORY = 'release_load_deployment_history';
const LOAD_DEPLOYMENT_HISTORY_COMPLETED = 'release_load_deployment_history_completed';
const LOAD_NODES = 'LOAD_NODES';
const LOAD_NODES_COMPLETED = 'LOAD_NODES_COMPLETED';
const LOAD_GRAY_STRATEGIES = 'LOAD_GRAY_STRATEGIES';
const LOAD_GRAY_STRATEGIES_COMPLETED = 'LOAD_GRAY_STRATEGIES_COMPLETED';
const LOAD_SHADOW_CONFIGS = 'LOAD_SHADOW_CONFIGS';
const LOAD_SHADOW_CONFIGS_COMPLETED = 'LOAD_SHADOW_CONFIGS_COMPLETED';
const LOAD_SHADOW_COMPARISONS = 'LOAD_SHADOW_COMPARISONS';
const LOAD_SHADOW_COMPARISONS_COMPLETED = 'LOAD_SHADOW_COMPARISONS_COMPLETED';
const LOAD_SHADOW_STATS = 'LOAD_SHADOW_STATS';
const LOAD_SHADOW_STATS_COMPLETED = 'LOAD_SHADOW_STATS_COMPLETED';
const SET_TAB = 'release_set_tab';

// Action interfaces
export interface ReleaseAction {
    type: string;
    data?: any;
    tab?: string;
}

export interface EnvironmentInfo {
    execEnv: string;
    projectVersion: string;
    packageId: string;
    updateTime: string;
}

export interface ApprovalTask {
    id: string;
    title: string;
    projectVersion: string;
    execEnv: string;
    requester: string;
    createTime: string;
    status: string;
}

export interface DeploymentRecord {
    id: string;
    projectVersion: string;
    execEnv: string;
    deployStatus: string;
    deployUser: string;
    deployTime: string;
    packageId: string;
}

export interface ExecutorNode {
    id: string;
    nodeName: string;
    nodeUrl: string;
    execEnv: string;
    nodeGroup: string;
    status: string;
    lastHeartbeat: string;
}

export interface GrayStrategy {
    id: string;
    strategyName: string;
    strategyType: string;
    packageId: string;
    projectId: string;
    targetGitTag: string;
    baselineGitTag: string;
    enabled: boolean;
    grayPercent: number;
    whitelist: string | null;
}

export interface ShadowConfig {
    id: string;
    mainRulePackagePath: string;
    shadowRulePackagePath: string;
    shadowFlowId: string;
    sampleRate: number;
    enabled: boolean;
}

export interface ShadowComparison {
    id: string;
    userId: string;
    orderNo: string;
    mainTotalTimeMs: number;
    shadowTotalTimeMs: number;
    statusMatch: boolean;
    resultMatch: boolean;
    divergenceSeverity: string;
    createdAt: string;
}

export interface ShadowStats {
    totalComparisons: number;
    totalDivergent: number;
    divergenceRate: number;
}

interface ApiResponse {
    status?: boolean;
    message?: string;
    [key: string]: unknown;
}

interface AppDispatch extends Dispatch<ReleaseAction> {
    (action: any): any;
}

interface AppState {
    release?: { projectName?: string };
    [key: string]: unknown;
}

export function loadEnvironments(projectName: string) {
    return function (dispatch: AppDispatch) {
        dispatch({type: LOAD_ENVIRONMENTS});
        formPost('/deployment/environments', {projectName})
        .then(data => dispatch({type: LOAD_ENVIRONMENTS_COMPLETED, data}))
        .catch(err => {
            console.error('加载环境信息失败', err);
            dispatch({type: LOAD_ENVIRONMENTS_COMPLETED, data: []});
        });
    };
}

export function loadApprovals(projectName: string) {
    return function (dispatch: AppDispatch) {
        dispatch({type: LOAD_APPROVALS});
        formPost('/approval/listByProject', {projectName})
        .then(data => dispatch({type: LOAD_APPROVALS_COMPLETED, data}))
        .catch(err => {
            console.error('加载审批列表失败', err);
            dispatch({type: LOAD_APPROVALS_COMPLETED, data: []});
        });
    };
}

export function approveTask(taskId: string, approveRemark: string) {
    return function (dispatch: AppDispatch, getState: () => AppState) {
        formPost('/approval/approve', {taskId, approveRemark: approveRemark || ''})
        .then(() => {
            const state = getState();
            dispatch(loadApprovals(state.release && state.release.projectName || ''));
        })
        .catch(err => console.error('审批操作失败', err));
    };
}

export function rejectTask(taskId: string, approveRemark: string) {
    return function () {
        formPost('/approval/reject', {taskId, approveRemark: approveRemark || ''})
        .then(() => {
            window.bootbox.alert('已驳回');
        })
        .catch(err => console.error('驳回操作失败', err));
    };
}

export function loadDeploymentHistory(projectName: string, packageId?: string) {
    return function (dispatch: AppDispatch) {
        dispatch({type: LOAD_DEPLOYMENT_HISTORY});
        const params: Record<string, string> = {projectName};
        if (packageId) params.packageId = packageId;
        formPost('/deployment/history', params)
        .then(data => dispatch({type: LOAD_DEPLOYMENT_HISTORY_COMPLETED, data}))
        .catch(err => {
            console.error('加载部署历史失败', err);
            dispatch({type: LOAD_DEPLOYMENT_HISTORY_COMPLETED, data: []});
        });
    };
}

export function promoteVersion(projectName: string, packageId: string, version: string) {
    return function (dispatch: AppDispatch) {
        formPost<ApiResponse>('/deployment/promote', {projectName, packageId, version})
        .then((result: ApiResponse) => {
            if (result.status) {
                window.bootbox.alert('发布成功');
                dispatch(loadEnvironments(projectName));
                dispatch(loadDeploymentHistory(projectName, packageId));
            } else {
                window.bootbox.alert(result.message || '发布失败');
            }
        })
        .catch(err => {
            console.error('发布失败', err);
        });
    };
}

export function rollbackVersion(projectName: string, packageId: string, targetVersion: string, execEnv?: string) {
    return function (dispatch: AppDispatch) {
        formPost<ApiResponse>('/deployment/rollback', {projectName, packageId, targetVersion, execEnv: execEnv || 'prod'})
        .then((result: ApiResponse) => {
            if (result.status) {
                window.bootbox.alert('回滚成功');
                dispatch(loadEnvironments(projectName));
                dispatch(loadDeploymentHistory(projectName, packageId));
            } else {
                window.bootbox.alert(result.message || '回滚失败');
            }
        })
        .catch(err => {
            console.error('回滚失败', err);
        });
    };
}

export function loadNodes(execEnv?: string) {
    return function (dispatch: AppDispatch) {
        dispatch({type: LOAD_NODES});
        const params: Record<string, string> = {};
        if (execEnv) params.execEnv = execEnv;
        formPost('/deployment/listNodes', params)
        .then(data => dispatch({type: LOAD_NODES_COMPLETED, data}))
        .catch(err => {
            console.error('加载节点列表失败', err);
            dispatch({type: LOAD_NODES_COMPLETED, data: []});
        });
    };
}

export function updateNodeGroup(nodeId: string, nodeGroup: string) {
    return function (dispatch: AppDispatch) {
        formPost('/deployment/updateNodeGroup', {nodeId, nodeGroup})
        .then(() => {
            dispatch(loadNodes());
        })
        .catch(err => console.error('更新节点分组失败', err));
    };
}

export function deployToGroup(projectName: string, packageId: string, version: string, execEnv: string, nodeGroup: string) {
    return function (dispatch: AppDispatch) {
        formPost<ApiResponse>('/deployment/deployToGroup', {projectName, packageId, version, execEnv: execEnv || 'prod', nodeGroup})
        .then((result: ApiResponse) => {
            if (result.status) {
                window.bootbox.alert('灰度部署成功');
                dispatch(loadNodes());
            } else {
                window.bootbox.alert(result.message || '灰度部署失败');
            }
        })
        .catch(err => {
            console.error('灰度部署失败', err);
        });
    };
}

export function setTab(tab: string): ReleaseAction {
    return {type: SET_TAB, tab};
}

// ===== Shadow (陪跑) actions =====

export function loadShadowConfigs() {
    return function (dispatch: AppDispatch) {
        dispatch({type: LOAD_SHADOW_CONFIGS});
        httpGet('/shadow/configs')
        .then(data => dispatch({type: LOAD_SHADOW_CONFIGS_COMPLETED, data}))
        .catch(err => {
            console.error('加载陪跑配置失败', err);
            dispatch({type: LOAD_SHADOW_CONFIGS_COMPLETED, data: []});
        });
    };
}

export function createShadowConfig(config: Partial<ShadowConfig>) {
    return function (dispatch: AppDispatch) {
        jsonPost('/shadow/configs', config)
        .then(() => {
            window.bootbox.alert('陪跑配置创建成功');
            dispatch(loadShadowConfigs());
        })
        .catch(err => {
            console.error('创建陪跑配置失败', err);
        });
    };
}

export function deleteShadowConfig(id: string) {
    return function (dispatch: AppDispatch) {
        window.bootbox.confirm('确认删除该陪跑配置？', (ok) => {
            if (!ok) return;
            httpDelete('/shadow/configs/' + id)
            .then(() => dispatch(loadShadowConfigs()))
            .catch(err => console.error('删除陪跑配置失败', err));
        });
    };
}

export function toggleShadowConfig(id: string, enabled: boolean) {
    return function (dispatch: AppDispatch) {
        jsonPut('/shadow/configs/' + id + '/toggle', {enabled})
        .then(() => dispatch(loadShadowConfigs()))
        .catch(err => console.error('切换陪跑配置状态失败', err));
    };
}

export function loadShadowComparisons(rulePackagePath: string, startTime?: string, endTime?: string, page?: number, size?: number) {
    return function (dispatch: AppDispatch) {
        dispatch({type: LOAD_SHADOW_COMPARISONS});
        const params = new URLSearchParams({rulePackagePath, page: String(page || 1), size: String(size || 20)});
        if (startTime) params.append('startTime', startTime);
        if (endTime) params.append('endTime', endTime);
        httpGet('/shadow/comparisons?' + params.toString())
        .then(data => dispatch({type: LOAD_SHADOW_COMPARISONS_COMPLETED, data}))
        .catch(err => {
            console.error('加载陪跑对比失败', err);
            dispatch({type: LOAD_SHADOW_COMPARISONS_COMPLETED, data: []});
        });
    };
}

export function loadShadowStats(rulePackagePath: string, startTime?: string, endTime?: string) {
    return function (dispatch: AppDispatch) {
        dispatch({type: LOAD_SHADOW_STATS});
        const params = new URLSearchParams({rulePackagePath});
        if (startTime) params.append('startTime', startTime);
        if (endTime) params.append('endTime', endTime);
        httpGet('/shadow/stats?' + params.toString())
        .then(data => dispatch({type: LOAD_SHADOW_STATS_COMPLETED, data}))
        .catch(err => {
            console.error('加载陪跑统计失败', err);
            dispatch({type: LOAD_SHADOW_STATS_COMPLETED, data: null});
        });
    };
}

export function loadGrayStrategies(projectId?: string, packageId?: string) {
    return function (dispatch: AppDispatch) {
        dispatch({type: LOAD_GRAY_STRATEGIES});
        const params = new URLSearchParams();
        if (projectId) params.append('projectId', projectId);
        if (packageId) params.append('packageId', packageId);
        httpGet('/gray/strategies?' + params.toString())
        .then(data => dispatch({type: LOAD_GRAY_STRATEGIES_COMPLETED, data}))
        .catch(err => {
            console.error('加载灰度策略失败', err);
            dispatch({type: LOAD_GRAY_STRATEGIES_COMPLETED, data: []});
        });
    };
}

export function createGrayStrategy(strategy: Partial<GrayStrategy>) {
    return function (dispatch: AppDispatch) {
        jsonPost('/gray/strategies', strategy)
        .then(() => {
            window.bootbox.alert('策略创建成功');
            dispatch(loadGrayStrategies(strategy.projectId, strategy.packageId));
        })
        .catch(err => {
            console.error('创建灰度策略失败', err);
        });
    };
}

export function deleteGrayStrategy(id: string, projectId: string, packageId: string) {
    return function (dispatch: AppDispatch) {
        window.bootbox.confirm('确认删除该灰度策略？', (ok) => {
            if (!ok) return;
            httpDelete('/gray/strategies/' + id)
            .then(() => dispatch(loadGrayStrategies(projectId, packageId)))
            .catch(err => console.error('删除灰度策略失败', err));
        });
    };
}

export function toggleGrayStrategy(id: string, enabled: boolean, projectId: string, packageId: string) {
    return function (dispatch: AppDispatch) {
        jsonPut('/gray/strategies/' + id + '/toggle', {enabled})
        .then(() => dispatch(loadGrayStrategies(projectId, packageId)))
        .catch(err => console.error('切换灰度策略状态失败', err));
    };
}

export {
    LOAD_ENVIRONMENTS, LOAD_ENVIRONMENTS_COMPLETED,
    LOAD_APPROVALS, LOAD_APPROVALS_COMPLETED,
    LOAD_DEPLOYMENT_HISTORY, LOAD_DEPLOYMENT_HISTORY_COMPLETED,
    LOAD_NODES, LOAD_NODES_COMPLETED,
    LOAD_GRAY_STRATEGIES, LOAD_GRAY_STRATEGIES_COMPLETED,
    LOAD_SHADOW_CONFIGS, LOAD_SHADOW_CONFIGS_COMPLETED,
    LOAD_SHADOW_COMPARISONS, LOAD_SHADOW_COMPARISONS_COMPLETED,
    LOAD_SHADOW_STATS, LOAD_SHADOW_STATS_COMPLETED,
    SET_TAB
};
