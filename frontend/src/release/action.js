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

export function loadEnvironments(projectName) {
    return function (dispatch) {
        dispatch({type: LOAD_ENVIRONMENTS});
        fetch(window._server + '/deployment/environments', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({projectName}).toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(data => dispatch({type: LOAD_ENVIRONMENTS_COMPLETED, data}))
        .catch(err => {
            console.error('加载环境信息失败', err);
            dispatch({type: LOAD_ENVIRONMENTS_COMPLETED, data: []});
        });
    };
}

export function loadApprovals(projectName) {
    return function (dispatch) {
        dispatch({type: LOAD_APPROVALS});
        fetch(window._server + '/approval/listByProject', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({projectName}).toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(data => dispatch({type: LOAD_APPROVALS_COMPLETED, data}))
        .catch(err => {
            console.error('加载审批列表失败', err);
            dispatch({type: LOAD_APPROVALS_COMPLETED, data: []});
        });
    };
}

export function approveTask(taskId, approveRemark) {
    return function (dispatch, getState) {
        fetch(window._server + '/approval/approve', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({taskId, approveRemark: approveRemark || ''}).toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(() => {
            const state = getState();
            dispatch(loadApprovals(state.release && state.release.projectName || ''));
        })
        .catch(err => console.error('审批操作失败', err));
    };
}

export function rejectTask(taskId, approveRemark) {
    return function () {
        fetch(window._server + '/approval/reject', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({taskId, approveRemark: approveRemark || ''}).toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(() => {
            window.bootbox.alert('已驳回');
        })
        .catch(err => console.error('驳回操作失败', err));
    };
}

export function loadDeploymentHistory(projectName, packageId) {
    return function (dispatch) {
        dispatch({type: LOAD_DEPLOYMENT_HISTORY});
        const params = new URLSearchParams({projectName});
        if (packageId) params.append('packageId', packageId);
        fetch(window._server + '/deployment/history', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: params.toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(data => dispatch({type: LOAD_DEPLOYMENT_HISTORY_COMPLETED, data}))
        .catch(err => {
            console.error('加载部署历史失败', err);
            dispatch({type: LOAD_DEPLOYMENT_HISTORY_COMPLETED, data: []});
        });
    };
}

export function promoteVersion(projectName, packageId, version) {
    return function (dispatch) {
        fetch(window._server + '/deployment/promote', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({projectName, packageId, version}).toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(result => {
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
            window.bootbox.alert('发布失败');
        });
    };
}

export function rollbackVersion(projectName, packageId, targetVersion, execEnv) {
    return function (dispatch) {
        fetch(window._server + '/deployment/rollback', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({projectName, packageId, targetVersion, execEnv: execEnv || 'prod'}).toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(result => {
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
            window.bootbox.alert('回滚失败');
        });
    };
}

export function loadNodes(execEnv) {
    return function (dispatch) {
        dispatch({type: LOAD_NODES});
        const params = new URLSearchParams();
        if (execEnv) params.append('execEnv', execEnv);
        fetch(window._server + '/deployment/listNodes', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: params.toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(data => dispatch({type: LOAD_NODES_COMPLETED, data}))
        .catch(err => {
            console.error('加载节点列表失败', err);
            dispatch({type: LOAD_NODES_COMPLETED, data: []});
        });
    };
}

export function updateNodeGroup(nodeId, nodeGroup) {
    return function (dispatch) {
        fetch(window._server + '/deployment/updateNodeGroup', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({nodeId, nodeGroup}).toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(() => {
            dispatch(loadNodes());
        })
        .catch(err => console.error('更新节点分组失败', err));
    };
}

export function deployToGroup(projectName, packageId, version, execEnv, nodeGroup) {
    return function (dispatch) {
        fetch(window._server + '/deployment/deployToGroup', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({projectName, packageId, version, execEnv: execEnv || 'prod', nodeGroup}).toString()
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(result => {
            if (result.status) {
                window.bootbox.alert('灰度部署成功');
                dispatch(loadNodes());
            } else {
                window.bootbox.alert(result.message || '灰度部署失败');
            }
        })
        .catch(err => {
            console.error('灰度部署失败', err);
            window.bootbox.alert('灰度部署失败');
        });
    };
}

export function setTab(tab) {
    return {type: SET_TAB, tab};
}

// ===== Shadow (陪跑) actions =====

export function loadShadowConfigs() {
    return function (dispatch) {
        dispatch({type: LOAD_SHADOW_CONFIGS});
        fetch(window._server + '/shadow/configs')
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(data => dispatch({type: LOAD_SHADOW_CONFIGS_COMPLETED, data}))
        .catch(err => {
            console.error('加载陪跑配置失败', err);
            dispatch({type: LOAD_SHADOW_CONFIGS_COMPLETED, data: []});
        });
    };
}

export function createShadowConfig(config) {
    return function (dispatch) {
        fetch(window._server + '/shadow/configs', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(config)
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(() => {
            window.bootbox.alert('陪跑配置创建成功');
            dispatch(loadShadowConfigs());
        })
        .catch(err => {
            console.error('创建陪跑配置失败', err);
            window.bootbox.alert('创建失败');
        });
    };
}

export function deleteShadowConfig(id) {
    return function (dispatch) {
        window.bootbox.confirm('确认删除该陪跑配置？', (ok) => {
            if (!ok) return;
            fetch(window._server + '/shadow/configs/' + id, {method: 'DELETE'})
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then(() => dispatch(loadShadowConfigs()))
            .catch(err => console.error('删除陪跑配置失败', err));
        });
    };
}

export function toggleShadowConfig(id, enabled) {
    return function (dispatch) {
        fetch(window._server + '/shadow/configs/' + id + '/toggle', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({enabled})
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(() => dispatch(loadShadowConfigs()))
        .catch(err => console.error('切换陪跑配置状态失败', err));
    };
}

export function loadShadowComparisons(rulePackagePath, startTime, endTime, page, size) {
    return function (dispatch) {
        dispatch({type: LOAD_SHADOW_COMPARISONS});
        const params = new URLSearchParams({rulePackagePath, page: page || 1, size: size || 20});
        if (startTime) params.append('startTime', startTime);
        if (endTime) params.append('endTime', endTime);
        fetch(window._server + '/shadow/comparisons?' + params.toString())
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(data => dispatch({type: LOAD_SHADOW_COMPARISONS_COMPLETED, data}))
        .catch(err => {
            console.error('加载陪跑对比失败', err);
            dispatch({type: LOAD_SHADOW_COMPARISONS_COMPLETED, data: []});
        });
    };
}

export function loadShadowStats(rulePackagePath, startTime, endTime) {
    return function (dispatch) {
        dispatch({type: LOAD_SHADOW_STATS});
        const params = new URLSearchParams({rulePackagePath});
        if (startTime) params.append('startTime', startTime);
        if (endTime) params.append('endTime', endTime);
        fetch(window._server + '/shadow/stats?' + params.toString())
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(data => dispatch({type: LOAD_SHADOW_STATS_COMPLETED, data}))
        .catch(err => {
            console.error('加载陪跑统计失败', err);
            dispatch({type: LOAD_SHADOW_STATS_COMPLETED, data: null});
        });
    };
}

export function loadGrayStrategies(projectId, packageId) {
    return function (dispatch) {
        dispatch({type: LOAD_GRAY_STRATEGIES});
        const params = new URLSearchParams();
        if (projectId) params.append('projectId', projectId);
        if (packageId) params.append('packageId', packageId);
        fetch(window._server + '/gray/strategies?' + params.toString())
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(data => dispatch({type: LOAD_GRAY_STRATEGIES_COMPLETED, data}))
        .catch(err => {
            console.error('加载灰度策略失败', err);
            dispatch({type: LOAD_GRAY_STRATEGIES_COMPLETED, data: []});
        });
    };
}

export function createGrayStrategy(strategy) {
    return function (dispatch, getState) {
        fetch(window._server + '/gray/strategies', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(strategy)
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
        .then(() => {
            window.bootbox.alert('策略创建成功');
            const state = getState();
            dispatch(loadGrayStrategies(strategy.projectId, strategy.packageId));
        })
        .catch(err => {
            console.error('创建灰度策略失败', err);
            window.bootbox.alert('创建失败');
        });
    };
}

export function deleteGrayStrategy(id, projectId, packageId) {
    return function (dispatch) {
        window.bootbox.confirm('确认删除该灰度策略？', (ok) => {
            if (!ok) return;
            fetch(window._server + '/gray/strategies/' + id, {method: 'DELETE'})
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then(() => dispatch(loadGrayStrategies(projectId, packageId)))
            .catch(err => console.error('删除灰度策略失败', err));
        });
    };
}

export function toggleGrayStrategy(id, enabled, projectId, packageId) {
    return function (dispatch) {
        fetch(window._server + '/gray/strategies/' + id + '/toggle', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({enabled})
        })
        .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
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
