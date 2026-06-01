import * as ACTIONS from './action.js';
import type {ParameterItem} from './action.js';

interface ParameterState {
    data: ParameterItem[];
}

interface ParameterAction {
    type: string;
    data?: ParameterItem[] | Record<string, unknown>;
    rowIndex?: number;
    newVersion?: boolean;
    file?: string;
}

export default function (state: ParameterState = {data: []}, action: ParameterAction): ParameterState {
    switch (action.type) {
        case ACTIONS.DEL: {
            const rowIndex = action.rowIndex!;
            const newData = [...state.data];
            newData.splice(rowIndex, 1);
            return {...state, data: newData};
        }
        case ACTIONS.SAVE: {
            const data = state.data;
            ACTIONS.saveData(data, action.newVersion!, action.file!);
            return state;
        }
        case ACTIONS.LOAD_DATA_COMPLETED: {
            const data = action.data as ParameterItem[];
            return {...state, data};
        }
        case ACTIONS.ADD: {
            const newData = [...state.data];
            newData.push({name: '', label: '', type: 'String'});
            return {...state, data: newData};
        }
        default:
            return state;
    }
}
