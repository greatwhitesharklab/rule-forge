/**
 * 仿真 API 调用
 */

export function startSimulation(params, callback) {
    fetch(window._server + '/simulation/startSimulation', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(params)
    }).then(function (response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        if (callback) callback(data);
    }).catch(function (err) {
        console.error('启动仿真失败', err);
        if (callback) callback({error: true, message: '启动仿真失败'});
    });
}

export function getSimulationProgress(runId, callback) {
    fetch(window._server + '/simulation/simulationProgress?runId=' + runId)
        .then(function (response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('查询仿真进度失败', err);
            if (callback) callback({status: 'ERROR'});
        });
}

export function loadSimulationResults(runId, page, size, callback) {
    var url = window._server + '/simulation/simulationResults?runId=' + runId
        + '&page=' + page + '&size=' + size;
    fetch(url)
        .then(function (response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('加载仿真结果失败', err);
            if (callback) callback({results: [], page: page, size: size});
        });
}

export function loadSimulationRuns(rulePackagePath, page, size, callback) {
    var url = window._server + '/simulation/simulationRuns?rulePackagePath='
        + encodeURIComponent(rulePackagePath) + '&page=' + page + '&size=' + size;
    fetch(url)
        .then(function (response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('加载仿真历史失败', err);
            if (callback) callback({runs: [], page: page, size: size});
        });
}

export function loadSimulationStats(rulePackagePath, startTime, endTime, callback) {
    var params = 'rulePackagePath=' + encodeURIComponent(rulePackagePath);
    if (startTime) params += '&startTime=' + encodeURIComponent(startTime);
    if (endTime) params += '&endTime=' + encodeURIComponent(endTime);
    fetch(window._server + '/simulation/simulationStats?' + params)
        .then(function (response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            if (callback) callback(data);
        }).catch(function (err) {
            console.error('加载仿真统计失败', err);
            if (callback) callback({});
        });
}
