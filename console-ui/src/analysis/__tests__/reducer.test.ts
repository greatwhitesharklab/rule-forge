import reducer from '../reducer.js';
import type {AnalysisState} from '../reducer.js';
import {
    LOAD_FLOW_TIMESERIES, LOAD_FLOW_TIMESERIES_COMPLETED,
    LOAD_PACKAGE_SUMMARY_COMPLETED,
    LOAD_RULE_COVERAGE_COMPLETED,
    LOAD_ANOMALIES_COMPLETED,
    SET_TIME_RANGE, SET_ANALYSIS_PACKAGE, SET_TAB
} from '../action.js';
import type {AnalysisAction} from '../action.js';
import {describe, it, expect} from 'vitest';

describe('analysis reducer', () => {
    const initialState: AnalysisState = {
        activeTab: 'trend',
        timeRange: '24h',
        selectedPackage: null,
        packages: [],
        packagesLoading: false,
        timeSeriesData: {timestamps: [], volume: [], successRate: [], rejectRate: [], avgLatency: []},
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

    it('should return initial state', () => {
        const state = reducer(undefined as unknown as AnalysisState, {type: 'UNKNOWN'} as unknown as AnalysisAction);
        expect(state.activeTab).toBe('trend');
        expect(state.timeRange).toBe('24h');
        expect(state.selectedPackage).toBeNull();
    });

    it('should handle SET_TAB', () => {
        const state = reducer(initialState, {type: SET_TAB, tab: 'coverage'});
        expect(state.activeTab).toBe('coverage');
    });

    it('should handle SET_TIME_RANGE', () => {
        const state = reducer(initialState, {type: SET_TIME_RANGE, range: '7d'});
        expect(state.timeRange).toBe('7d');
    });

    it('should handle SET_ANALYSIS_PACKAGE', () => {
        const state = reducer(initialState, {type: SET_ANALYSIS_PACKAGE, pkg: 'loan-rules'});
        expect(state.selectedPackage).toBe('loan-rules');
    });

    it('should handle LOAD_FLOW_TIMESERIES (loading)', () => {
        const state = reducer(initialState, {type: LOAD_FLOW_TIMESERIES});
        expect(state.timeSeriesLoading).toBe(true);
    });

    it('should handle LOAD_FLOW_TIMESERIES_COMPLETED', () => {
        const data = {timestamps: ['t1', 't2'], volume: [10, 20], successRate: [90, 80], rejectRate: [5, 10], avgLatency: [50, 60]};
        const state = reducer({...initialState, timeSeriesLoading: true}, {type: LOAD_FLOW_TIMESERIES_COMPLETED, data});
        expect(state.timeSeriesLoading).toBe(false);
        expect(state.timeSeriesData.timestamps).toEqual(['t1', 't2']);
        expect(state.timeSeriesData.volume).toEqual([10, 20]);
    });

    it('should handle LOAD_FLOW_TIMESERIES_COMPLETED with null data', () => {
        const state = reducer({...initialState, timeSeriesLoading: true}, {type: LOAD_FLOW_TIMESERIES_COMPLETED, data: null as any});
        expect(state.timeSeriesLoading).toBe(false);
        expect(state.timeSeriesData.timestamps).toEqual([]);
    });

    it('should handle LOAD_PACKAGE_SUMMARY_COMPLETED', () => {
        const data = [{rulePackagePath: 'loan-rules', totalCount: 100, successRate: 0, rejectRate: 0, avgTotalTimeMs: null}];
        const state = reducer(initialState, {type: LOAD_PACKAGE_SUMMARY_COMPLETED, data});
        expect(state.packageSummary).toEqual(data);
        expect(state.packageSummaryLoading).toBe(false);
    });

    it('should handle LOAD_RULE_COVERAGE_COMPLETED', () => {
        const data = {totalFiredInWindow: 10, totalRulesEverSeen: 15, hotRules: [], coldRules: ['R005']};
        const state = reducer(initialState, {type: LOAD_RULE_COVERAGE_COMPLETED, data});
        expect(state.ruleCoverage.totalFiredInWindow).toBe(10);
        expect(state.ruleCoverage.coldRules).toEqual(['R005']);
    });

    it('should handle LOAD_ANOMALIES_COMPLETED', () => {
        const data = [{metric: 'reject_rate', severity: 'HIGH' as const, direction: 'SPIKE' as const, baseline: 0, current: 0, sigmaDelta: 0}];
        const state = reducer(initialState, {type: LOAD_ANOMALIES_COMPLETED, data});
        expect(state.anomalies).toEqual(data);
        expect(state.anomaliesLoading).toBe(false);
    });

    it('should handle unknown action type', () => {
        const state = reducer(initialState, {type: 'UNKNOWN_ACTION'} as unknown as AnalysisAction);
        expect(state).toEqual(initialState);
    });
});
