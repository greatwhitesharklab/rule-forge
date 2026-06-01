import {ThunkDispatch} from 'redux-thunk';

import {MonitoringState} from './reducer.js';

export const LOAD_METRICS = 'monitoring_load_metrics';
export const LOAD_METRICS_COMPLETED = 'monitoring_load_metrics_completed';
export const LOAD_PACKAGES = 'monitoring_load_packages';
export const LOAD_PACKAGES_COMPLETED = 'monitoring_load_packages_completed';
export const LOAD_ALERT_RULES = 'monitoring_load_alert_rules';
export const LOAD_ALERT_RULES_COMPLETED = 'monitoring_load_alert_rules_completed';
export const LOAD_ALERT_HISTORY = 'monitoring_load_alert_history';
export const LOAD_ALERT_HISTORY_COMPLETED = 'monitoring_load_alert_history_completed';
export const SET_TIME_RANGE = 'monitoring_set_time_range';
export const SET_SELECTED_PACKAGE = 'monitoring_set_selected_package';

export interface MetricsData {
    timestamps: string[];
    series: Record<string, number[]>;
}

export interface AlertRule {
    id?: number;
    name: string;
    metricName: string;
    condition: string;
    threshold: number;
    durationMin: number;
    webhookUrl: string;
    cooldownMin: number;
    enabled: boolean;
}

type MonitoringDispatch = ThunkDispatch<MonitoringState, unknown, MonitoringAction>;

interface MonitoringActionBase {
    type: string;
    [key: string]: unknown;
}

export interface LoadMetricsCompletedAction extends MonitoringActionBase {
    type: typeof LOAD_METRICS_COMPLETED;
    data: MetricsData;
}

export interface LoadPackagesCompletedAction extends MonitoringActionBase {
    type: typeof LOAD_PACKAGES_COMPLETED;
    data: string[];
}

export interface LoadAlertRulesCompletedAction extends MonitoringActionBase {
    type: typeof LOAD_ALERT_RULES_COMPLETED;
    data: AlertRule[];
}

export interface LoadAlertHistoryCompletedAction extends MonitoringActionBase {
    type: typeof LOAD_ALERT_HISTORY_COMPLETED;
    data: AlertHistoryEntry[];
}

export interface SetTimeRangeAction extends MonitoringActionBase {
    type: typeof SET_TIME_RANGE;
    range: string;
}

export interface SetSelectedPackageAction extends MonitoringActionBase {
    type: typeof SET_SELECTED_PACKAGE;
    pkg: string | null;
}

export type MonitoringAction =
    | MonitoringActionBase
    | LoadMetricsCompletedAction
    | LoadPackagesCompletedAction
    | LoadAlertRulesCompletedAction
    | LoadAlertHistoryCompletedAction
    | SetTimeRangeAction
    | SetSelectedPackageAction;

export interface AlertHistoryEntry {
    id: number;
    alertRuleId: number;
    triggeredAt: string;
    resolvedAt: string | null;
    [key: string]: unknown;
}

export function loadMetrics(metricName: string, startTime: Date, endTime: Date, packageName: string | null) {
    return function (dispatch: MonitoringDispatch) {
        dispatch({type: LOAD_METRICS});
        const params = new URLSearchParams({metricName});
        if (startTime) params.append('startTime', startTime.toISOString());
        if (endTime) params.append('endTime', endTime.toISOString());
        if (packageName) params.append('tags', JSON.stringify({package: packageName}));

        fetch(window._server + '/monitoring/metrics?' + params.toString())
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then((data: MetricsData) => dispatch({type: LOAD_METRICS_COMPLETED, data}))
            .catch(err => {
                console.error('加载指标失败', err);
                dispatch({type: LOAD_METRICS_COMPLETED, data: {timestamps: [], series: {}}});
            });
    };
}

export function loadPackages() {
    return function (dispatch: MonitoringDispatch) {
        dispatch({type: LOAD_PACKAGES});
        fetch(window._server + '/monitoring/metrics/packages')
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then((data: string[]) => dispatch({type: LOAD_PACKAGES_COMPLETED, data}))
            .catch(err => {
                console.error('加载规则包列表失败', err);
                dispatch({type: LOAD_PACKAGES_COMPLETED, data: []});
            });
    };
}

export function loadAlertRules() {
    return function (dispatch: MonitoringDispatch) {
        dispatch({type: LOAD_ALERT_RULES});
        fetch(window._server + '/monitoring/alerts')
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then((data: AlertRule[]) => dispatch({type: LOAD_ALERT_RULES_COMPLETED, data}))
            .catch(err => {
                console.error('加载告警规则失败', err);
                dispatch({type: LOAD_ALERT_RULES_COMPLETED, data: []});
            });
    };
}

export function saveAlertRule(rule: AlertRule, callback?: () => void) {
    return function (dispatch: MonitoringDispatch) {
        const url = rule.id
            ? window._server + '/monitoring/alerts/' + rule.id
            : window._server + '/monitoring/alerts';
        const method = rule.id ? 'PUT' : 'POST';

        fetch(url, {
            method,
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(rule)
        })
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then(() => {
                dispatch(loadAlertRules());
                if (callback) callback();
            })
            .catch(err => console.error('保存告警规则失败', err));
    };
}

export function deleteAlertRule(id: number) {
    return function (dispatch: MonitoringDispatch) {
        fetch(window._server + '/monitoring/alerts/' + id, {method: 'DELETE'})
            .then(resp => {
                if (!resp.ok) throw resp;
                dispatch(loadAlertRules());
            })
            .catch(err => console.error('删除告警规则失败', err));
    };
}

export function loadAlertHistory(alertRuleId?: number, startTime?: Date, endTime?: Date) {
    return function (dispatch: MonitoringDispatch) {
        dispatch({type: LOAD_ALERT_HISTORY});
        const params = new URLSearchParams();
        if (alertRuleId) params.append('alertRuleId', String(alertRuleId));
        if (startTime) params.append('startTime', startTime.toISOString());
        if (endTime) params.append('endTime', endTime.toISOString());

        fetch(window._server + '/monitoring/alerts/history?' + params.toString())
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then((data: AlertHistoryEntry[]) => dispatch({type: LOAD_ALERT_HISTORY_COMPLETED, data}))
            .catch(err => {
                console.error('加载告警历史失败', err);
                dispatch({type: LOAD_ALERT_HISTORY_COMPLETED, data: []});
            });
    };
}

export function setTimeRange(range: string): SetTimeRangeAction {
    return {type: SET_TIME_RANGE, range};
}

export function setSelectedPackage(pkg: string | null): SetSelectedPackageAction {
    return {type: SET_SELECTED_PACKAGE, pkg};
}
