import * as ACTIONS from './action.ts';

interface ClientConfigState {
    data: Array<{ name?: string; client?: string }>;
}

export default function clientConfig(state: ClientConfigState = {data: []}, action: { type: string; data?: unknown; index?: number }): ClientConfigState {
    // NOTE: Original code used `state.prototype` which evaluates to undefined for plain objects,
    // so Object.assign({}, undefined, {data}) effectively returns {data}.
    // We replicate this behavior explicitly.
    switch (action.type) {
        case ACTIONS.LOADED_DATA:
            return {data: action.data as Array<{ name?: string; client?: string }>} as ClientConfigState;
        case ACTIONS.DEL: {
            const index = action.index!;
            const newData = [...state.data];
            newData.splice(index, 1);
            return {data: newData} as ClientConfigState;
        }
        case ACTIONS.ADD: {
            const data = [...state.data];
            data.push({});
            return {data} as ClientConfigState;
        }
        default:
            return state;
    }
}
