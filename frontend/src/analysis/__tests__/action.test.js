import {
    loadFlowTimeseries,
    loadPackageSummary,
    loadRejectDistribution,
    loadRuleCoverage,
    loadRuleFireFrequency,
    loadAnomalies,
    loadAnalysisPackages,
    setTimeRange,
    setAnalysisPackage,
    setTab,
    LOAD_FLOW_TIMESERIES, LOAD_FLOW_TIMESERIES_COMPLETED,
    LOAD_PACKAGE_SUMMARY, LOAD_PACKAGE_SUMMARY_COMPLETED,
    LOAD_REJECT_DISTRIBUTION, LOAD_REJECT_DISTRIBUTION_COMPLETED,
    LOAD_RULE_COVERAGE, LOAD_RULE_COVERAGE_COMPLETED,
    LOAD_RULE_FIRE_FREQUENCY, LOAD_RULE_FIRE_FREQUENCY_COMPLETED,
    LOAD_ANOMALIES, LOAD_ANOMALIES_COMPLETED,
    LOAD_ANALYSIS_PACKAGES, LOAD_ANALYSIS_PACKAGES_COMPLETED,
    SET_TIME_RANGE, SET_ANALYSIS_PACKAGE, SET_TAB
} from '../action.js';
import { vi, describe, it, expect, beforeEach } from 'vitest';

/**
 * Action creators 测试
 *
 * 测试 thunk action 的 dispatch 调用序列和 API 参数构造
 */
describe('analysis actions', () => {

    // ========== 同步 action creators ==========

    describe('sync action creators', () => {
        it('setTimeRange should create SET_TIME_RANGE action', () => {
            const action = setTimeRange('7d');
            expect(action).toEqual({type: SET_TIME_RANGE, range: '7d'});
        });

        it('setAnalysisPackage should create SET_ANALYSIS_PACKAGE action', () => {
            const action = setAnalysisPackage('loan-rules');
            expect(action).toEqual({type: SET_ANALYSIS_PACKAGE, pkg: 'loan-rules'});
        });

        it('setTab should create SET_TAB action', () => {
            const action = setTab('coverage');
            expect(action).toEqual({type: SET_TAB, tab: 'coverage'});
        });
    });

    // ========== Action type constants ==========

    describe('action type constants', () => {
        it('should export all required action types', () => {
            expect(LOAD_FLOW_TIMESERIES).toBe('analysis_load_flow_timeseries');
            expect(LOAD_FLOW_TIMESERIES_COMPLETED).toBe('analysis_load_flow_timeseries_completed');
            expect(LOAD_PACKAGE_SUMMARY).toBe('analysis_load_package_summary');
            expect(LOAD_PACKAGE_SUMMARY_COMPLETED).toBe('analysis_load_package_summary_completed');
            expect(LOAD_REJECT_DISTRIBUTION).toBe('analysis_load_reject_distribution');
            expect(LOAD_REJECT_DISTRIBUTION_COMPLETED).toBe('analysis_load_reject_distribution_completed');
            expect(LOAD_RULE_COVERAGE).toBe('analysis_load_rule_coverage');
            expect(LOAD_RULE_COVERAGE_COMPLETED).toBe('analysis_load_rule_coverage_completed');
            expect(LOAD_RULE_FIRE_FREQUENCY).toBe('analysis_load_rule_fire_frequency');
            expect(LOAD_RULE_FIRE_FREQUENCY_COMPLETED).toBe('analysis_load_rule_fire_frequency_completed');
            expect(LOAD_ANOMALIES).toBe('analysis_load_anomalies');
            expect(LOAD_ANOMALIES_COMPLETED).toBe('analysis_load_anomalies_completed');
            expect(LOAD_ANALYSIS_PACKAGES).toBe('analysis_load_packages');
            expect(LOAD_ANALYSIS_PACKAGES_COMPLETED).toBe('analysis_load_packages_completed');
            expect(SET_TIME_RANGE).toBe('analysis_set_time_range');
            expect(SET_ANALYSIS_PACKAGE).toBe('analysis_set_package');
            expect(SET_TAB).toBe('analysis_set_tab');
        });
    });

    // ========== Thunk actions (mock fetch + dispatch) ==========

    let dispatch;
    let fetchMock;

    beforeEach(() => {
        dispatch = vi.fn();
        fetchMock = vi.fn();
        global.fetch = fetchMock;
    });

    describe('loadFlowTimeseries', () => {
        it('should dispatch LOAD then COMPLETED with data on success', async () => {
            const data = {timestamps: ['t1'], volume: [10], successRate: [90], rejectRate: [5], avgLatency: [50]};
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve(data)});

            const start = new Date('2026-05-01');
            const end = new Date('2026-05-31');
            const thunk = loadFlowTimeseries(start, end, null, null, null, 'daily');
            await thunk(dispatch);

            expect(dispatch).toHaveBeenCalledTimes(2);
            expect(dispatch).toHaveBeenNthCalledWith(1, {type: LOAD_FLOW_TIMESERIES});
            expect(dispatch).toHaveBeenNthCalledWith(2, {type: LOAD_FLOW_TIMESERIES_COMPLETED, data});

            // Verify URL contains correct params
            const fetchUrl = fetchMock.mock.calls[0][0];
            expect(fetchUrl).toContain('/analysis/flow/timeseries?');
            expect(fetchUrl).toContain('granularity=daily');
        });

        it('should append rulePackagePath when provided', async () => {
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve({timestamps: [], volume: [], successRate: [], rejectRate: [], avgLatency: []})});

            const thunk = loadFlowTimeseries(new Date(), new Date(), 'luzcred/withdrawal');
            await thunk(dispatch);

            const fetchUrl = fetchMock.mock.calls[0][0];
            expect(fetchUrl).toContain('rulePackagePath=luzcred%2Fwithdrawal');
        });

        it('should dispatch empty data on fetch error', async () => {
            fetchMock.mockRejectedValue(new Error('Network error'));

            const thunk = loadFlowTimeseries(new Date(), new Date());
            await thunk(dispatch);

            expect(dispatch).toHaveBeenNthCalledWith(2, {
                type: LOAD_FLOW_TIMESERIES_COMPLETED,
                data: {timestamps: [], volume: [], successRate: [], rejectRate: [], avgLatency: []}
            });
        });

        it('should dispatch empty data on HTTP error', async () => {
            fetchMock.mockResolvedValue({ok: false, status: 500, json: () => Promise.resolve({})});

            const thunk = loadFlowTimeseries(new Date(), new Date());
            await thunk(dispatch);

            expect(dispatch).toHaveBeenNthCalledWith(2, {
                type: LOAD_FLOW_TIMESERIES_COMPLETED,
                data: {timestamps: [], volume: [], successRate: [], rejectRate: [], avgLatency: []}
            });
        });
    });

    describe('loadPackageSummary', () => {
        it('should dispatch LOAD then COMPLETED with data', async () => {
            const data = [{rulePackagePath: 'luzcred', totalCount: 100, successRate: 90}];
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve(data)});

            const thunk = loadPackageSummary(new Date(), new Date());
            await thunk(dispatch);

            expect(dispatch).toHaveBeenNthCalledWith(1, {type: LOAD_PACKAGE_SUMMARY});
            expect(dispatch).toHaveBeenNthCalledWith(2, {type: LOAD_PACKAGE_SUMMARY_COMPLETED, data});
            expect(fetchMock.mock.calls[0][0]).toContain('/analysis/flow/packages-summary?');
        });

        it('should dispatch empty array on error', async () => {
            fetchMock.mockRejectedValue(new Error('fail'));

            const thunk = loadPackageSummary(new Date(), new Date());
            await thunk(dispatch);

            expect(dispatch).toHaveBeenNthCalledWith(2, {type: LOAD_PACKAGE_SUMMARY_COMPLETED, data: []});
        });
    });

    describe('loadRejectDistribution', () => {
        it('should pass limit param', async () => {
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve([])});

            const thunk = loadRejectDistribution(new Date(), new Date(), null, 10);
            await thunk(dispatch);

            const fetchUrl = fetchMock.mock.calls[0][0];
            expect(fetchUrl).toContain('limit=10');
            expect(fetchUrl).toContain('/analysis/flow/reject-distribution?');
        });

        it('should append rulePackagePath when provided', async () => {
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve([])});

            const thunk = loadRejectDistribution(new Date(), new Date(), 'luzcred/T4', 20);
            await thunk(dispatch);

            const fetchUrl = fetchMock.mock.calls[0][0];
            expect(fetchUrl).toContain('rulePackagePath=luzcred%2FT4');
        });
    });

    describe('loadRuleCoverage', () => {
        it('should dispatch correct actions', async () => {
            const data = {totalFiredInWindow: 10, totalRulesEverSeen: 17, hotRules: [], coldRules: []};
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve(data)});

            const thunk = loadRuleCoverage('luzcred/withdrawal', new Date(), new Date());
            await thunk(dispatch);

            expect(dispatch).toHaveBeenNthCalledWith(1, {type: LOAD_RULE_COVERAGE});
            expect(dispatch).toHaveBeenNthCalledWith(2, {type: LOAD_RULE_COVERAGE_COMPLETED, data});
            expect(fetchMock.mock.calls[0][0]).toContain('/analysis/rule/coverage?');
        });

        it('should dispatch empty object on error', async () => {
            fetchMock.mockRejectedValue(new Error('fail'));

            const thunk = loadRuleCoverage(null, new Date(), new Date());
            await thunk(dispatch);

            expect(dispatch).toHaveBeenNthCalledWith(2, {type: LOAD_RULE_COVERAGE_COMPLETED, data: {}});
        });
    });

    describe('loadRuleFireFrequency', () => {
        it('should dispatch correct actions', async () => {
            const data = [{ruleName: 'R001', fireCount: 100, avgDurationMs: 5}];
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve(data)});

            const thunk = loadRuleFireFrequency(new Date(), new Date(), 'luzcred');
            await thunk(dispatch);

            expect(dispatch).toHaveBeenNthCalledWith(1, {type: LOAD_RULE_FIRE_FREQUENCY});
            expect(dispatch).toHaveBeenNthCalledWith(2, {type: LOAD_RULE_FIRE_FREQUENCY_COMPLETED, data});
            expect(fetchMock.mock.calls[0][0]).toContain('/analysis/rule/fire-frequency?');
        });
    });

    describe('loadAnomalies', () => {
        it('should pass baselineDays and sigmaThreshold', async () => {
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve([])});

            const thunk = loadAnomalies(null, 14, 3.0, 'luzcred');
            await thunk(dispatch);

            const fetchUrl = fetchMock.mock.calls[0][0];
            expect(fetchUrl).toContain('baselineDays=14');
            expect(fetchUrl).toContain('sigmaThreshold=3');
            expect(fetchUrl).toContain('rulePackagePath=luzcred');
        });

        it('should use defaults when no params given', async () => {
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve([])});

            const thunk = loadAnomalies();
            await thunk(dispatch);

            const fetchUrl = fetchMock.mock.calls[0][0];
            expect(fetchUrl).toContain('baselineDays=7');
            expect(fetchUrl).toContain('sigmaThreshold=2');
        });
    });

    describe('loadAnalysisPackages', () => {
        it('should dispatch correct actions', async () => {
            const data = ['luzcred/withdrawal', 'luzcred/T4'];
            fetchMock.mockResolvedValue({ok: true, json: () => Promise.resolve(data)});

            const thunk = loadAnalysisPackages();
            await thunk(dispatch);

            expect(dispatch).toHaveBeenNthCalledWith(1, {type: LOAD_ANALYSIS_PACKAGES});
            expect(dispatch).toHaveBeenNthCalledWith(2, {type: LOAD_ANALYSIS_PACKAGES_COMPLETED, data});
            expect(fetchMock.mock.calls[0][0]).toContain('/analysis/packages');
        });

        it('should dispatch empty array on error', async () => {
            fetchMock.mockRejectedValue(new Error('fail'));

            const thunk = loadAnalysisPackages();
            await thunk(dispatch);

            expect(dispatch).toHaveBeenNthCalledWith(2, {type: LOAD_ANALYSIS_PACKAGES_COMPLETED, data: []});
        });
    });
});
