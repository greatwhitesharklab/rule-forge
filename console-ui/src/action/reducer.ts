import {combineReducers} from 'redux';
import * as ACTIONS from './action.js';
import {uniqueID} from '../components/componentAction.js';
import type {SpringBean, ActionMethod} from './action.js';

interface MasterState {
    data?: SpringBean[];
}

interface SlaveState {
    data?: SpringBean;
}

interface MethodState {
    data?: ActionMethod;
}

interface MethodListState {
    data?: ActionMethod[];
}

interface ActionModuleAction {
    type: string;
    masterData?: MasterDataResponse;
    masterRowData?: SpringBean;
    rowIndex?: number;
    newSlaveData?: ActionMethod;
    slaveData?: ActionMethod;
    result?: ActionMethod[];
    newVersion?: boolean;
    file?: string;
}

interface MasterDataResponse {
    springBeans: SpringBean[];
}

function master(state: MasterState = {}, action: ActionModuleAction): MasterState {
    switch (action.type) {
        case ACTIONS.LOAD_MASTER_COMPLETED: {
            var data = action.masterData!.springBeans;
            return Object.assign({}, {data});
        }
        case ACTIONS.ADD_MASTER: {
            var newData: SpringBean[] = [];
            if (state.data) {
                newData = [...state.data];
            }
            newData.push({id: '', name: '', methods: []});
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.DEL_MASTER: {
            var newData = [...(state.data || [])];
            var index = action.rowIndex!;
            newData.splice(index, 1);
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.SAVE: {
            var data = state.data!;
            var newVersion = action.newVersion!;
            var file = action.file!;
            ACTIONS.saveData(data, newVersion, file);
            return state;
        }
        default:
            return state;
    }
}

function slave(state: SlaveState = {}, action: ActionModuleAction): SlaveState {
    switch (action.type) {
        case ACTIONS.LOAD_SLAVE_COMPLETE: {
            var masterRowData = action.masterRowData!;
            if (masterRowData && masterRowData.methods) {
                masterRowData.methods.forEach((m, index) => {
                    m.id = uniqueID();
                });
            }
            return Object.assign({}, {data: action.masterRowData});
        }
        case ACTIONS.ADD_SLAVE: {
            if (!state.data || !state.data.methods) {
                window.bootbox.alert('请先指定方法所属的Bean');
                return state;
            }
            var newData = Object.assign({}, state.data);
            var newSlaveData = action.newSlaveData || {name: '', methodName: '', parameters: []};
            newData.methods.push(newSlaveData);
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.DEL_SLAVE: {
            var index = action.rowIndex!;
            var newData = Object.assign({}, state.data);
            newData.methods.splice(index, 1);
            return Object.assign({}, {data: newData});
        }
        default:
            return state;
    }
}

function method(state: MethodState = {}, action: ActionModuleAction): MethodState {
    switch (action.type) {
        case ACTIONS.LOAD_METHOD_COMPLETED:
            return Object.assign({}, {data: action.slaveData});
        case ACTIONS.LOAD_SLAVE_COMPLETE:
            return Object.assign({}, {data: {} as ActionMethod});
        case ACTIONS.ADD_PARAMETER: {
            if (!state.data || !state.data.parameters) {
                window.bootbox.alert('请先指定参数所属的方法');
                return state;
            }
            var newData = Object.assign({}, state.data);
            newData.parameters.push({name: '', type: 'String'});
            return Object.assign({}, {data: newData});
        }
        case ACTIONS.DEL_PARAMETER: {
            var index = action.rowIndex!;
            var newData = Object.assign({}, state.data);
            newData.parameters.splice(index, 1);
            return Object.assign({}, {data: newData});
        }
        default:
            return state;
    }
}

function methodList(state: MethodListState = {}, action: ActionModuleAction): MethodListState {
    switch (action.type) {
        case ACTIONS.LOADED_BEAN_METHODS:
            return Object.assign({}, {data: action.result});
        default:
            return state;
    }
}

export interface ActionRootState {
    master: MasterState;
    slave: SlaveState;
    method: MethodState;
    methodList: MethodListState;
}

export default combineReducers({
    master, slave, method, methodList
});
