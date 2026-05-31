const LOAD_ENVIRONMENTS = 'release_load_environments';
const LOAD_ENVIRONMENTS_COMPLETED = 'release_load_environments_completed';
const LOAD_APPROVALS = 'release_load_approvals';
const LOAD_APPROVALS_COMPLETED = 'release_load_approvals_completed';
const LOAD_DEPLOYMENT_HISTORY = 'release_load_deployment_history';
const LOAD_DEPLOYMENT_HISTORY_COMPLETED = 'release_load_deployment_history_completed';
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

export function setTab(tab) {
    return {type: SET_TAB, tab};
}

export {
    LOAD_ENVIRONMENTS, LOAD_ENVIRONMENTS_COMPLETED,
    LOAD_APPROVALS, LOAD_APPROVALS_COMPLETED,
    LOAD_DEPLOYMENT_HISTORY, LOAD_DEPLOYMENT_HISTORY_COMPLETED,
    SET_TAB
};
