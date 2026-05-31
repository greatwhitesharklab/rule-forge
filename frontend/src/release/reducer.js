import {
    LOAD_ENVIRONMENTS, LOAD_ENVIRONMENTS_COMPLETED,
    LOAD_APPROVALS, LOAD_APPROVALS_COMPLETED,
    LOAD_DEPLOYMENT_HISTORY, LOAD_DEPLOYMENT_HISTORY_COMPLETED,
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
        default:
            return state;
    }
}
