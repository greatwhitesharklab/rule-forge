import {combineReducers} from 'redux';
import * as ACTIONS from './action.js';
import type {ConstantCategory, ConstantItem} from './action.js';

interface MasterState {
    data: ConstantCategory[];
}

interface SlaveState {
    data?: ConstantCategory | {};
}

interface MasterAction {
    type: string;
    masterData?: ConstantCategory[];
    rowIndex?: number;
    newVersion?: boolean;
    file?: string;
}

interface SlaveAction {
    type: string;
    masterRowData?: ConstantCategory | {};
    rowIndex?: number;
}

function master(state: MasterState = {data: []}, action: MasterAction): MasterState {
    switch (action.type) {
        case ACTIONS.LOAD_MASTER_COMPLETED:
            return {...state, data: action.masterData!};
        case ACTIONS.DEL_MASTER: {
            const rowIndex = action.rowIndex!;
            const newData = [...state.data];
            newData.splice(rowIndex, 1);
            return {...state, data: newData};
        }
        case ACTIONS.ADD_MASTER: {
            const newData = [...state.data];
            newData.push({name: '', type: 'Custom', constants: [] as ConstantCategory['constants']});
            return {...state, data: newData};
        }
        case ACTIONS.SAVE: {
            const data = state.data;
            ACTIONS.saveData(data, action.newVersion!, action.file!);
            return state;
        }
        default:
            return state;
    }
}

function slave(state: SlaveState = {}, action: SlaveAction): SlaveState {
    switch (action.type) {
        case ACTIONS.LOAD_SLAVE_COMPLETE:
            return {...state, data: action.masterRowData};
        case ACTIONS.DEL_SLAVE: {
            const rowIndex = action.rowIndex!;
            const newData = Object.assign({}, state.data as ConstantCategory);
            newData.constants.splice(rowIndex, 1);
            return {...state, data: newData};
        }
        case ACTIONS.ADD_SLAVE: {
            const newData = Object.assign({}, state.data as ConstantCategory);
            newData.constants.push({name: '', label: '', type: 'String'});
            return {...state, data: newData};
        }
        default:
            return state;
    }
}

export default combineReducers({
    master, slave
});
