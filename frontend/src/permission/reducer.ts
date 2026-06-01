import * as ACTION from './action.js';
import {combineReducers} from 'redux';
import type {ProjectConfig} from './action.js';

interface MasterState {
    masterData?: any[];
}

interface SlaveState {
    slaveData?: ProjectConfig[];
}

interface PermissionAction {
    type: string;
    data?: any;
    prop?: string;
    permission?: boolean;
}

function master(state: MasterState = {}, action: PermissionAction): MasterState {
    switch (action.type) {
        case ACTION.MASTER_LOADED:
            return Object.assign({}, {masterData: action.data});
        default:
            return state;
    }
}

function slave(state: SlaveState = {}, action: PermissionAction): SlaveState {
    switch (action.type) {
        case ACTION.SLAVE_LOADED:
            return Object.assign({}, {slaveData: action.data});
        case ACTION.PERMISSION_CHANGE: {
            let data = action.data;
            switch (action.prop) {
                case "readProject":
                    data.readProject = action.permission;
                    break;
                default:
            }
            return Object.assign({}, {slaveData: state.slaveData});
        }
        default:
            return state;
    }
}

export interface PermissionRootState {
    master: MasterState;
    slave: SlaveState;
}

export default combineReducers({
    master, slave
});
