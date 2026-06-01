import {
    LOAD_FLOW_TIMESERIES, LOAD_FLOW_TIMESERIES_COMPLETED,
    LOAD_PACKAGE_SUMMARY, LOAD_PACKAGE_SUMMARY_COMPLETED,
    LOAD_REJECT_DISTRIBUTION, LOAD_REJECT_DISTRIBUTION_COMPLETED,
    LOAD_RULE_COVERAGE, LOAD_RULE_COVERAGE_COMPLETED,
    LOAD_RULE_FIRE_FREQUENCY, LOAD_RULE_FIRE_FREQUENCY_COMPLETED,
    LOAD_ANOMALIES, LOAD_ANOMALIES_COMPLETED,
    LOAD_ANALYSIS_PACKAGES, LOAD_ANALYSIS_PACKAGES_COMPLETED,
    SET_TIME_RANGE, SET_ANALYSIS_PACKAGE, SET_TAB
} from './action.js';
import type {
    AnalysisAction,
    FlowTimeseriesData,
    PackageSummaryRow,
    RejectDistributionRow,
    RuleCoverageData,
    AnomalyRecord
} from './action.js';
import type {TimeRange} from './helpers.js';

// ========== State interface ==========

export interface AnalysisState {
    activeTab: string;
    timeRange: TimeRange;
    selectedPackage: string | null;
    packages: string[];
    packagesLoading: boolean;
    timeSeriesData: FlowTimeseriesData;
    timeSeriesLoading: boolean;
    packageSummary: PackageSummaryRow[];
    packageSummaryLoading: boolean;
    rejectDistribution: RejectDistributionRow[];
    rejectDistributionLoading: boolean;
    ruleCoverage: RuleCoverageData;
    ruleCoverageLoading: boolean;
    ruleFireFrequency: { ruleName: string; fireCount: number }[];
    ruleFireFrequencyLoading: boolean;
    anomalies: AnomalyRecord[];
    anomaliesLoading: boolean;
}

const EMPTY_TIMESERIES: FlowTimeseriesData = {timestamps: [], volume: [], successRate: [], rejectRate: [], avgLatency: []};

const initialState: AnalysisState = {
    activeTab: 'trend',
    timeRange: '24h',
    selectedPackage: null,
    packages: [],
    packagesLoading: false,
    timeSeriesData: {...EMPTY_TIMESERIES},
    timeSeriesLoading: false,
    packageSummary: [],
    packageSummaryLoading: false,
    rejectDistribution: [],
    rejectDistributionLoading: false,
    ruleCoverage: {},
    ruleCoverageLoading: false,
    ruleFireFrequency: [],
    ruleFireFrequencyLoading: false,
    anomalies: [],
    anomaliesLoading: false,
};

export default function (state: AnalysisState = initialState, action: AnalysisAction): AnalysisState {
    switch (action.type) {
        case SET_TAB:
            return {...state, activeTab: action.tab};
        case SET_TIME_RANGE:
            return {...state, timeRange: action.range};
        case SET_ANALYSIS_PACKAGE:
            return {...state, selectedPackage: action.pkg};
        case LOAD_FLOW_TIMESERIES:
            return {...state, timeSeriesLoading: true};
        case LOAD_FLOW_TIMESERIES_COMPLETED:
            return {...state, timeSeriesLoading: false, timeSeriesData: action.data || initialState.timeSeriesData};
        case LOAD_PACKAGE_SUMMARY:
            return {...state, packageSummaryLoading: true};
        case LOAD_PACKAGE_SUMMARY_COMPLETED:
            return {...state, packageSummaryLoading: false, packageSummary: action.data || []};
        case LOAD_REJECT_DISTRIBUTION:
            return {...state, rejectDistributionLoading: true};
        case LOAD_REJECT_DISTRIBUTION_COMPLETED:
            return {...state, rejectDistributionLoading: false, rejectDistribution: action.data || []};
        case LOAD_RULE_COVERAGE:
            return {...state, ruleCoverageLoading: true};
        case LOAD_RULE_COVERAGE_COMPLETED:
            return {...state, ruleCoverageLoading: false, ruleCoverage: action.data || {}};
        case LOAD_RULE_FIRE_FREQUENCY:
            return {...state, ruleFireFrequencyLoading: true};
        case LOAD_RULE_FIRE_FREQUENCY_COMPLETED:
            return {...state, ruleFireFrequencyLoading: false, ruleFireFrequency: action.data || []};
        case LOAD_ANOMALIES:
            return {...state, anomaliesLoading: true};
        case LOAD_ANOMALIES_COMPLETED:
            return {...state, anomaliesLoading: false, anomalies: action.data || []};
        case LOAD_ANALYSIS_PACKAGES:
            return {...state, packagesLoading: true};
        case LOAD_ANALYSIS_PACKAGES_COMPLETED:
            return {...state, packagesLoading: false, packages: action.data || []};
        default:
            return state;
    }
}
