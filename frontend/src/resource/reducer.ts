import * as ACTIONS from './action.js';
import {combineReducers} from 'redux';
import type {ResourceCategory, VariableItem} from './action.js';

interface MasterState {
    data?: ResourceCategory[];
}

interface SlaveState {
    data?: ResourceCategory;
}

interface ResourceAction {
    type: string;
    rowIndex?: number;
    masterData?: ResourceCategory[];
    masterRowData?: ResourceCategory;
    masterName?: string;
    variables?: VariableItem[];
    jsonResult?: { variables: VariableItem[]; clazz?: string };
    newVersion?: boolean;
    file?: string;
}

function master(state: MasterState = {}, action: ResourceAction): MasterState {
    switch (action.type) {
        case ACTIONS.LOAD_MASTER_COMPLETED:
            return Object.assign({}, {data: action.masterData});
        case ACTIONS.DEL_MASTER: {
            var rowIndex = action.rowIndex!;
            var newData = [...(state.data || [])];
            newData.splice(rowIndex, 1);
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.GENERATED_FIELDS: {
            var newData = [...(state.data || [])];
            var variables = action.variables!;
            var rowIndex = action.rowIndex!;
            var targetData = newData[rowIndex];
            targetData.variables = variables;
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.IMPORT_FIELDS: {
            var newData = [...(state.data || [])];
            var jsonResult = action.jsonResult!;
            var variables = jsonResult.variables;
            var clazz = jsonResult.clazz;
            var rowIndex = action.rowIndex!;
            var targetData = newData[rowIndex];
            var fields: VariableItem[] = targetData.variables || [];
            if (clazz) {
                targetData.clazz = clazz;
            }
            fields.push(...variables);
            targetData.variables = fields;
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.SAVE:
            ACTIONS.saveData(state.data!, action.newVersion!, action.file!);
            return state;
        default:
            return state;
    }
}

function slave(state: SlaveState = {}, action: ResourceAction): SlaveState {
    switch (action.type) {
        case ACTIONS.LOAD_SLAVE_COMPLETE:
            return Object.assign({}, {data: action.masterRowData});
        case ACTIONS.DEL_SLAVE: {
            var rowIndex = action.rowIndex!;
            var newData = Object.assign({}, state.data);
            newData.variables.splice(rowIndex, 1);
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.ADD_SLAVE:
            ACTIONS.saveData(state.data as any, action.newVersion!, action.file!);
            return state;
        default:
            return state;
    }
}

export interface ResourceRootState {
    master: MasterState;
    slave: SlaveState;
}

export default combineReducers({
    master,
    slave
});
