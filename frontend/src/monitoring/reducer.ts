import {
    LOAD_METRICS, LOAD_METRICS_COMPLETED,
    LOAD_PACKAGES, LOAD_PACKAGES_COMPLETED,
    LOAD_ALERT_RULES, LOAD_ALERT_RULES_COMPLETED,
    LOAD_ALERT_HISTORY, LOAD_ALERT_HISTORY_COMPLETED,
    SET_TIME_RANGE, SET_SELECTED_PACKAGE,
    MonitoringAction, MetricsData, AlertRule, AlertHistoryEntry
} from './action.js';

export interface MonitoringState {
    loading: boolean;
    metricsData: MetricsData;
    packages: string[];
    alertRules: AlertRule[];
    alertHistory: AlertHistoryEntry[];
    timeRange: string;
    selectedPackage: string | null;
}

const initialState: MonitoringState = {
    loading: false,
    metricsData: {timestamps: [], series: {}},
    packages: [],
    alertRules: [],
    alertHistory: [],
    timeRange: '1h',
    selectedPackage: null
};

export default function (state: MonitoringState = initialState, action: MonitoringAction): MonitoringState {
    switch (action.type) {
        case LOAD_METRICS:
            return {...state, loading: true};
        case LOAD_METRICS_COMPLETED:
            return {...state, loading: false, metricsData: (action as MonitoringAction & { data: MetricsData }).data};
        case LOAD_PACKAGES:
            return {...state};
        case LOAD_PACKAGES_COMPLETED:
            return {...state, packages: (action as MonitoringAction & { data: string[] }).data || []};
        case LOAD_ALERT_RULES:
            return {...state};
        case LOAD_ALERT_RULES_COMPLETED:
            return {...state, alertRules: (action as MonitoringAction & { data: AlertRule[] }).data || []};
        case LOAD_ALERT_HISTORY:
            return {...state};
        case LOAD_ALERT_HISTORY_COMPLETED:
            return {...state, alertHistory: (action as MonitoringAction & { data: AlertHistoryEntry[] }).data || []};
        case SET_TIME_RANGE:
            return {...state, timeRange: (action as MonitoringAction & { range: string }).range};
        case SET_SELECTED_PACKAGE:
            return {...state, selectedPackage: (action as MonitoringAction & { pkg: string | null }).pkg};
        default:
            return state;
    }
}
