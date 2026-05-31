import {
    LOAD_ENVIRONMENTS, LOAD_ENVIRONMENTS_COMPLETED,
    LOAD_APPROVALS, LOAD_APPROVALS_COMPLETED,
    LOAD_DEPLOYMENT_HISTORY, LOAD_DEPLOYMENT_HISTORY_COMPLETED,
    LOAD_NODES, LOAD_NODES_COMPLETED,
    LOAD_GRAY_STRATEGIES, LOAD_GRAY_STRATEGIES_COMPLETED,
    LOAD_SHADOW_CONFIGS, LOAD_SHADOW_CONFIGS_COMPLETED,
    LOAD_SHADOW_COMPARISONS, LOAD_SHADOW_COMPARISONS_COMPLETED,
    LOAD_SHADOW_STATS, LOAD_SHADOW_STATS_COMPLETED,
    SET_TAB
} from './action';

const initialState = {
    activeTab: 'environments',
    environments: [],
    environmentsLoading: false,
    approvals: [],
    approvalsLoading: false,
    deploymentHistory: [],
    historyLoading: false,
    nodes: [],
    nodesLoading: false,
    grayStrategies: [],
    grayStrategiesLoading: false,
    shadowConfigs: [],
    shadowConfigsLoading: false,
    shadowComparisons: [],
    shadowComparisonsLoading: false,
    shadowStats: null,
    shadowStatsLoading: false,
};

export default function (state = initialState, action) {
    switch (action.type) {
        case SET_TAB:
            return {...state, activeTab: action.tab};
        case LOAD_ENVIRONMENTS:
            return {...state, environmentsLoading: true};
        case LOAD_ENVIRONMENTS_COMPLETED:
            return {...state, environmentsLoading: false, environments: action.data || []};
        case LOAD_APPROVALS:
            return {...state, approvalsLoading: true};
        case LOAD_APPROVALS_COMPLETED:
            return {...state, approvalsLoading: false, approvals: action.data || []};
        case LOAD_DEPLOYMENT_HISTORY:
            return {...state, historyLoading: true};
        case LOAD_DEPLOYMENT_HISTORY_COMPLETED:
            return {...state, historyLoading: false, deploymentHistory: action.data || []};
        case LOAD_NODES:
            return {...state, nodesLoading: true};
        case LOAD_NODES_COMPLETED:
            return {...state, nodesLoading: false, nodes: action.data || []};
        case LOAD_GRAY_STRATEGIES:
            return {...state, grayStrategiesLoading: true};
        case LOAD_GRAY_STRATEGIES_COMPLETED:
            return {...state, grayStrategiesLoading: false, grayStrategies: action.data || []};
        case LOAD_SHADOW_CONFIGS:
            return {...state, shadowConfigsLoading: true};
        case LOAD_SHADOW_CONFIGS_COMPLETED:
            return {...state, shadowConfigsLoading: false, shadowConfigs: action.data || []};
        case LOAD_SHADOW_COMPARISONS:
            return {...state, shadowComparisonsLoading: true};
        case LOAD_SHADOW_COMPARISONS_COMPLETED:
            return {...state, shadowComparisonsLoading: false, shadowComparisons: action.data || []};
        case LOAD_SHADOW_STATS:
            return {...state, shadowStatsLoading: true};
        case LOAD_SHADOW_STATS_COMPLETED:
            return {...state, shadowStatsLoading: false, shadowStats: action.data || null};
        default:
            return state;
    }
}
