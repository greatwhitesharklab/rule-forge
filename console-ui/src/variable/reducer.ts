import * as ACTIONS from './action.js';
import {combineReducers} from 'redux';
import type {VariableCategory, VariableItem} from './action.js';

interface MasterState {
    data?: VariableCategory[];
}

interface SlaveState {
    data?: VariableCategory;
}

interface VariableAction {
    type: string;
    rowIndex?: number;
    masterData?: VariableCategory[];
    masterRowData?: VariableCategory;
    masterName?: string;
    variables?: VariableItem[];
    jsonResult?: { variables: VariableItem[]; clazz?: string };
    newVersion?: boolean;
    file?: string;
}

function master(state: MasterState = {}, action: VariableAction): MasterState {
    switch (action.type) {
        case ACTIONS.LOAD_MASTER_COMPLETED:
            return Object.assign({}, {data: action.masterData});
        case ACTIONS.DEL_MASTER: {
            var rowIndex = action.rowIndex!;
            var newData = [...(state.data || [])];
            newData.splice(rowIndex, 1);
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.ADD_MASTER: {
            var masterName = action.masterName!;
            var newData = [...(state.data || [])];
            for (let masterItem of newData) {
                if (masterName === masterItem['name']) {
                    window.bootbox.alert("[" + masterName + "]已经存在，添加失败")
                    return state;
                }
            }
            newData.push({name: masterName, clazz: '', type: 'Custom', variables: []});
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

function slave(state: SlaveState = {}, action: VariableAction): SlaveState {
    switch (action.type) {
        case ACTIONS.LOAD_SLAVE_COMPLETE:
            return Object.assign({}, {data: action.masterRowData});
        case ACTIONS.DEL_SLAVE: {
            var rowIndex = action.rowIndex!;
            var newData = Object.assign({}, state.data);
            newData.variables.splice(rowIndex, 1);
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.ADD_SLAVE: {
            var newData = Object.assign({}, state.data);
            newData.variables.push({name: '', label: '', type: 'String'});
            return Object.assign({}, {data: newData});
        }
        default:
            return state;
    }
}

export interface VariableRootState {
    master: MasterState;
    slave: SlaveState;
}

export default combineReducers({
    master,
    slave
});
