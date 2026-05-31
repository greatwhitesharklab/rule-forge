const LOAD_FLOW_TIMESERIES = 'analysis_load_flow_timeseries';
const LOAD_FLOW_TIMESERIES_COMPLETED = 'analysis_load_flow_timeseries_completed';
const LOAD_PACKAGE_SUMMARY = 'analysis_load_package_summary';
const LOAD_PACKAGE_SUMMARY_COMPLETED = 'analysis_load_package_summary_completed';
const LOAD_REJECT_DISTRIBUTION = 'analysis_load_reject_distribution';
const LOAD_REJECT_DISTRIBUTION_COMPLETED = 'analysis_load_reject_distribution_completed';
const LOAD_RULE_COVERAGE = 'analysis_load_rule_coverage';
const LOAD_RULE_COVERAGE_COMPLETED = 'analysis_load_rule_coverage_completed';
const LOAD_RULE_FIRE_FREQUENCY = 'analysis_load_rule_fire_frequency';
const LOAD_RULE_FIRE_FREQUENCY_COMPLETED = 'analysis_load_rule_fire_frequency_completed';
const LOAD_ANOMALIES = 'analysis_load_anomalies';
const LOAD_ANOMALIES_COMPLETED = 'analysis_load_anomalies_completed';
const LOAD_ANALYSIS_PACKAGES = 'analysis_load_packages';
const LOAD_ANALYSIS_PACKAGES_COMPLETED = 'analysis_load_packages_completed';
const SET_TIME_RANGE = 'analysis_set_time_range';
const SET_ANALYSIS_PACKAGE = 'analysis_set_package';
const SET_TAB = 'analysis_set_tab';

function toISO(date) {
    return date.toISOString();
}

export function loadFlowTimeseries(startTime, endTime, rulePackagePath, flowId, isGray, granularity) {
    return function (dispatch) {
        dispatch({type: LOAD_FLOW_TIMESERIES});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime),
            granularity: granularity || 'hourly'
        });
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);
        if (flowId) params.append('flowId', flowId);
        if (isGray !== null && isGray !== undefined) params.append('isGray', isGray);

        return fetch(window._server + '/analysis/flow/timeseries?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then(data => dispatch({type: LOAD_FLOW_TIMESERIES_COMPLETED, data}))
            .catch(err => {
                console.error('加载时间序列失败', err);
                dispatch({type: LOAD_FLOW_TIMESERIES_COMPLETED, data: {timestamps: [], volume: [], successRate: [], rejectRate: [], avgLatency: []}});
            });
    };
}

export function loadPackageSummary(startTime, endTime) {
    return function (dispatch) {
        dispatch({type: LOAD_PACKAGE_SUMMARY});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime)
        });
        return fetch(window._server + '/analysis/flow/packages-summary?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then(data => dispatch({type: LOAD_PACKAGE_SUMMARY_COMPLETED, data}))
            .catch(err => {
                console.error('加载包汇总失败', err);
                dispatch({type: LOAD_PACKAGE_SUMMARY_COMPLETED, data: []});
            });
    };
}

export function loadRejectDistribution(startTime, endTime, rulePackagePath, limit) {
    return function (dispatch) {
        dispatch({type: LOAD_REJECT_DISTRIBUTION});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime),
            limit: limit || 20
        });
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);

        return fetch(window._server + '/analysis/flow/reject-distribution?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then(data => dispatch({type: LOAD_REJECT_DISTRIBUTION_COMPLETED, data}))
            .catch(err => {
                console.error('加载拒绝码分布失败', err);
                dispatch({type: LOAD_REJECT_DISTRIBUTION_COMPLETED, data: []});
            });
    };
}

export function loadRuleCoverage(rulePackagePath, startTime, endTime) {
    return function (dispatch) {
        dispatch({type: LOAD_RULE_COVERAGE});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime)
        });
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);

        return fetch(window._server + '/analysis/rule/coverage?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then(data => dispatch({type: LOAD_RULE_COVERAGE_COMPLETED, data}))
            .catch(err => {
                console.error('加载规则覆盖率失败', err);
                dispatch({type: LOAD_RULE_COVERAGE_COMPLETED, data: {}});
            });
    };
}

export function loadRuleFireFrequency(startTime, endTime, rulePackagePath) {
    return function (dispatch) {
        dispatch({type: LOAD_RULE_FIRE_FREQUENCY});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime)
        });
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);

        return fetch(window._server + '/analysis/rule/fire-frequency?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then(data => dispatch({type: LOAD_RULE_FIRE_FREQUENCY_COMPLETED, data}))
            .catch(err => {
                console.error('加载规则触发频率失败', err);
                dispatch({type: LOAD_RULE_FIRE_FREQUENCY_COMPLETED, data: []});
            });
    };
}

export function loadAnomalies(currentTime, baselineDays, sigmaThreshold, rulePackagePath) {
    return function (dispatch) {
        dispatch({type: LOAD_ANOMALIES});
        const params = new URLSearchParams({
            baselineDays: baselineDays || 7,
            sigmaThreshold: sigmaThreshold || 2.0
        });
        if (currentTime) params.append('currentTime', toISO(currentTime));
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);

        return fetch(window._server + '/analysis/anomaly/detect?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then(data => dispatch({type: LOAD_ANOMALIES_COMPLETED, data}))
            .catch(err => {
                console.error('加载偏差检测失败', err);
                dispatch({type: LOAD_ANOMALIES_COMPLETED, data: []});
            });
    };
}

export function loadAnalysisPackages() {
    return function (dispatch) {
        dispatch({type: LOAD_ANALYSIS_PACKAGES});
        return fetch(window._server + '/analysis/packages')
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then(data => dispatch({type: LOAD_ANALYSIS_PACKAGES_COMPLETED, data}))
            .catch(err => {
                console.error('加载规则包列表失败', err);
                dispatch({type: LOAD_ANALYSIS_PACKAGES_COMPLETED, data: []});
            });
    };
}

export function setTimeRange(range) {
    return {type: SET_TIME_RANGE, range};
}

export function setAnalysisPackage(pkg) {
    return {type: SET_ANALYSIS_PACKAGE, pkg};
}

export function setTab(tab) {
    return {type: SET_TAB, tab};
}

export {
    LOAD_FLOW_TIMESERIES, LOAD_FLOW_TIMESERIES_COMPLETED,
    LOAD_PACKAGE_SUMMARY, LOAD_PACKAGE_SUMMARY_COMPLETED,
    LOAD_REJECT_DISTRIBUTION, LOAD_REJECT_DISTRIBUTION_COMPLETED,
    LOAD_RULE_COVERAGE, LOAD_RULE_COVERAGE_COMPLETED,
    LOAD_RULE_FIRE_FREQUENCY, LOAD_RULE_FIRE_FREQUENCY_COMPLETED,
    LOAD_ANOMALIES, LOAD_ANOMALIES_COMPLETED,
    LOAD_ANALYSIS_PACKAGES, LOAD_ANALYSIS_PACKAGES_COMPLETED,
    SET_TIME_RANGE, SET_ANALYSIS_PACKAGE, SET_TAB
};
