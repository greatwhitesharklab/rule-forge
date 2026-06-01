import type {Dispatch} from 'redux';
import type {TimeRange} from './helpers';

// ========== Action type constants ==========

export const LOAD_FLOW_TIMESERIES = 'analysis_load_flow_timeseries';
export const LOAD_FLOW_TIMESERIES_COMPLETED = 'analysis_load_flow_timeseries_completed';
export const LOAD_PACKAGE_SUMMARY = 'analysis_load_package_summary';
export const LOAD_PACKAGE_SUMMARY_COMPLETED = 'analysis_load_package_summary_completed';
export const LOAD_REJECT_DISTRIBUTION = 'analysis_load_reject_distribution';
export const LOAD_REJECT_DISTRIBUTION_COMPLETED = 'analysis_load_reject_distribution_completed';
export const LOAD_RULE_COVERAGE = 'analysis_load_rule_coverage';
export const LOAD_RULE_COVERAGE_COMPLETED = 'analysis_load_rule_coverage_completed';
export const LOAD_RULE_FIRE_FREQUENCY = 'analysis_load_rule_fire_frequency';
export const LOAD_RULE_FIRE_FREQUENCY_COMPLETED = 'analysis_load_rule_fire_frequency_completed';
export const LOAD_ANOMALIES = 'analysis_load_anomalies';
export const LOAD_ANOMALIES_COMPLETED = 'analysis_load_anomalies_completed';
export const LOAD_ANALYSIS_PACKAGES = 'analysis_load_packages';
export const LOAD_ANALYSIS_PACKAGES_COMPLETED = 'analysis_load_packages_completed';
export const SET_TIME_RANGE = 'analysis_set_time_range';
export const SET_ANALYSIS_PACKAGE = 'analysis_set_package';
export const SET_TAB = 'analysis_set_tab';

// ========== Data types ==========

export interface FlowTimeseriesData {
    timestamps: string[];
    volume: number[];
    successRate: number[];
    rejectRate: number[];
    avgLatency: number[];
}

export interface PackageSummaryRow {
    rulePackagePath: string;
    totalCount: number;
    successRate: number;
    rejectRate: number;
    avgTotalTimeMs: number | null;
}

export interface RejectDistributionRow {
    rejectCode: string;
    rejectReason: string;
    count: number;
}

export interface HotRule {
    ruleName: string;
    fireCount: number;
}

export interface RuleCoverageData {
    totalFiredInWindow?: number;
    totalRulesEverSeen?: number;
    hotRules?: HotRule[];
    coldRules?: string[];
    frequencyDistribution?: Record<string, number>;
}

export interface AnomalyRecord {
    label?: string;
    metric: string;
    severity: 'HIGH' | 'MEDIUM' | 'LOW';
    direction: 'SPIKE' | 'DROP';
    baseline: number;
    current: number;
    sigmaDelta: number;
}

// ========== Action interfaces ==========

export interface SetTimeRangeAction {
    type: typeof SET_TIME_RANGE;
    range: TimeRange;
}

export interface SetAnalysisPackageAction {
    type: typeof SET_ANALYSIS_PACKAGE;
    pkg: string | null;
}

export interface SetTabAction {
    type: typeof SET_TAB;
    tab: string;
}

export type AnalysisAction =
    | SetTimeRangeAction
    | SetAnalysisPackageAction
    | SetTabAction
    | { type: typeof LOAD_FLOW_TIMESERIES }
    | { type: typeof LOAD_FLOW_TIMESERIES_COMPLETED; data: FlowTimeseriesData }
    | { type: typeof LOAD_PACKAGE_SUMMARY }
    | { type: typeof LOAD_PACKAGE_SUMMARY_COMPLETED; data: PackageSummaryRow[] }
    | { type: typeof LOAD_REJECT_DISTRIBUTION }
    | { type: typeof LOAD_REJECT_DISTRIBUTION_COMPLETED; data: RejectDistributionRow[] }
    | { type: typeof LOAD_RULE_COVERAGE }
    | { type: typeof LOAD_RULE_COVERAGE_COMPLETED; data: RuleCoverageData }
    | { type: typeof LOAD_RULE_FIRE_FREQUENCY }
    | { type: typeof LOAD_RULE_FIRE_FREQUENCY_COMPLETED; data: HotRule[] }
    | { type: typeof LOAD_ANOMALIES }
    | { type: typeof LOAD_ANOMALIES_COMPLETED; data: AnomalyRecord[] }
    | { type: typeof LOAD_ANALYSIS_PACKAGES }
    | { type: typeof LOAD_ANALYSIS_PACKAGES_COMPLETED; data: string[] };

// ========== Helper ==========

function toISO(date: Date): string {
    return date.toISOString();
}

// ========== Thunk action creators ==========

export function loadFlowTimeseries(
    startTime: Date,
    endTime: Date,
    rulePackagePath?: string | null,
    flowId?: string | null,
    isGray?: boolean | null,
    granularity?: string
) {
    return function (dispatch: Dispatch<AnalysisAction>) {
        dispatch({type: LOAD_FLOW_TIMESERIES});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime),
            granularity: granularity || 'hourly'
        });
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);
        if (flowId) params.append('flowId', flowId);
        if (isGray !== null && isGray !== undefined) params.append('isGray', String(isGray));

        return fetch(window._server + '/analysis/flow/timeseries?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then((data: FlowTimeseriesData) => dispatch({type: LOAD_FLOW_TIMESERIES_COMPLETED, data}))
            .catch(err => {
                console.error('加载时间序列失败', err);
                dispatch({type: LOAD_FLOW_TIMESERIES_COMPLETED, data: {timestamps: [], volume: [], successRate: [], rejectRate: [], avgLatency: []}});
            });
    };
}

export function loadPackageSummary(startTime: Date, endTime: Date) {
    return function (dispatch: Dispatch<AnalysisAction>) {
        dispatch({type: LOAD_PACKAGE_SUMMARY});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime)
        });
        return fetch(window._server + '/analysis/flow/packages-summary?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then((data: PackageSummaryRow[]) => dispatch({type: LOAD_PACKAGE_SUMMARY_COMPLETED, data}))
            .catch(err => {
                console.error('加载包汇总失败', err);
                dispatch({type: LOAD_PACKAGE_SUMMARY_COMPLETED, data: []});
            });
    };
}

export function loadRejectDistribution(
    startTime: Date,
    endTime: Date,
    rulePackagePath?: string | null,
    limit?: number
) {
    return function (dispatch: Dispatch<AnalysisAction>) {
        dispatch({type: LOAD_REJECT_DISTRIBUTION});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime),
            limit: String(limit || 20)
        });
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);

        return fetch(window._server + '/analysis/flow/reject-distribution?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then((data: RejectDistributionRow[]) => dispatch({type: LOAD_REJECT_DISTRIBUTION_COMPLETED, data}))
            .catch(err => {
                console.error('加载拒绝码分布失败', err);
                dispatch({type: LOAD_REJECT_DISTRIBUTION_COMPLETED, data: []});
            });
    };
}

export function loadRuleCoverage(
    rulePackagePath: string | null | undefined,
    startTime: Date,
    endTime: Date
) {
    return function (dispatch: Dispatch<AnalysisAction>) {
        dispatch({type: LOAD_RULE_COVERAGE});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime)
        });
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);

        return fetch(window._server + '/analysis/rule/coverage?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then((data: RuleCoverageData) => dispatch({type: LOAD_RULE_COVERAGE_COMPLETED, data}))
            .catch(err => {
                console.error('加载规则覆盖率失败', err);
                dispatch({type: LOAD_RULE_COVERAGE_COMPLETED, data: {}});
            });
    };
}

export function loadRuleFireFrequency(
    startTime: Date,
    endTime: Date,
    rulePackagePath?: string | null
) {
    return function (dispatch: Dispatch<AnalysisAction>) {
        dispatch({type: LOAD_RULE_FIRE_FREQUENCY});
        const params = new URLSearchParams({
            startTime: toISO(startTime),
            endTime: toISO(endTime)
        });
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);

        return fetch(window._server + '/analysis/rule/fire-frequency?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then((data: HotRule[]) => dispatch({type: LOAD_RULE_FIRE_FREQUENCY_COMPLETED, data}))
            .catch(err => {
                console.error('加载规则触发频率失败', err);
                dispatch({type: LOAD_RULE_FIRE_FREQUENCY_COMPLETED, data: []});
            });
    };
}

export function loadAnomalies(
    currentTime?: Date | null,
    baselineDays?: number,
    sigmaThreshold?: number,
    rulePackagePath?: string | null
) {
    return function (dispatch: Dispatch<AnalysisAction>) {
        dispatch({type: LOAD_ANOMALIES});
        const params = new URLSearchParams({
            baselineDays: String(baselineDays || 7),
            sigmaThreshold: String(sigmaThreshold || 2.0)
        });
        if (currentTime) params.append('currentTime', toISO(currentTime));
        if (rulePackagePath) params.append('rulePackagePath', rulePackagePath);

        return fetch(window._server + '/analysis/anomaly/detect?' + params.toString())
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then((data: AnomalyRecord[]) => dispatch({type: LOAD_ANOMALIES_COMPLETED, data}))
            .catch(err => {
                console.error('加载偏差检测失败', err);
                dispatch({type: LOAD_ANOMALIES_COMPLETED, data: []});
            });
    };
}

export function loadAnalysisPackages() {
    return function (dispatch: Dispatch<AnalysisAction>) {
        dispatch({type: LOAD_ANALYSIS_PACKAGES});
        return fetch(window._server + '/analysis/packages')
            .then(resp => { if (!resp.ok) throw resp; return resp.json(); })
            .then((data: string[]) => dispatch({type: LOAD_ANALYSIS_PACKAGES_COMPLETED, data}))
            .catch(err => {
                console.error('加载规则包列表失败', err);
                dispatch({type: LOAD_ANALYSIS_PACKAGES_COMPLETED, data: []});
            });
    };
}

// ========== Sync action creators ==========

export function setTimeRange(range: TimeRange): SetTimeRangeAction {
    return {type: SET_TIME_RANGE, range};
}

export function setAnalysisPackage(pkg: string | null): SetAnalysisPackageAction {
    return {type: SET_ANALYSIS_PACKAGE, pkg};
}

export function setTab(tab: string): SetTabAction {
    return {type: SET_TAB, tab};
}
