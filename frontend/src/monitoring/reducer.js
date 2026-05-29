import {
    LOAD_METRICS, LOAD_METRICS_COMPLETED,
    LOAD_PACKAGES, LOAD_PACKAGES_COMPLETED,
    LOAD_ALERT_RULES, LOAD_ALERT_RULES_COMPLETED,
    LOAD_ALERT_HISTORY, LOAD_ALERT_HISTORY_COMPLETED,
    SET_TIME_RANGE, SET_SELECTED_PACKAGE
} from './action.js';

const initialState = {
    loading: false,
    metricsData: {timestamps: [], series: {}},
    packages: [],
    alertRules: [],
    alertHistory: [],
    timeRange: '1h',
    selectedPackage: null
};

export default function (state = initialState, action) {
    switch (action.type) {
        case LOAD_METRICS:
            return {...state, loading: true};
        case LOAD_METRICS_COMPLETED:
            return {...state, loading: false, metricsData: action.data};
        case LOAD_PACKAGES:
            return {...state};
        case LOAD_PACKAGES_COMPLETED:
            return {...state, packages: action.data || []};
        case LOAD_ALERT_RULES:
            return {...state};
        case LOAD_ALERT_RULES_COMPLETED:
            return {...state, alertRules: action.data || []};
        case LOAD_ALERT_HISTORY:
            return {...state};
        case LOAD_ALERT_HISTORY_COMPLETED:
            return {...state, alertHistory: action.data || []};
        case SET_TIME_RANGE:
            return {...state, timeRange: action.range};
        case SET_SELECTED_PACKAGE:
            return {...state, selectedPackage: action.pkg};
        default:
            return state;
    }
}
