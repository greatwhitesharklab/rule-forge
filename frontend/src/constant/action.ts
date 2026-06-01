import {save as apiSave, formPost} from '../api/client.js';

// eslint-disable-next-line @typescript-eslint/no-unused-vars
import type {Dispatch} from 'redux';

/* Thunk action type — redux-thunk middleware allows dispatching functions. */
type ThunkAction = (dispatch: (a: unknown) => void) => void;

export const LOAD_MASTER_COMPLETED = 'load_master_completed';
export const LOAD_SLAVE_COMPLETE = 'load_slave_completed';
export const ADD_MASTER = 'add_master';
export const DEL_MASTER = 'del_master';
export const ADD_SLAVE = 'add_slave';
export const DEL_SLAVE = 'del_slave';
export const SAVE = 'save';

export interface ConstantCategory {
    name: string;
    label?: string;
    type?: string;
    constants: ConstantItem[];
}

export interface ConstantItem {
    name: string;
    label: string;
    type: string;
}

export function save(newVersion: boolean, file: string) {
    return {newVersion, file, type: SAVE};
}

export function saveData(data: ConstantCategory[], newVersion: boolean, file: string): void {
    let xml = '<?xml version="1.0" encoding="utf-8"?>';
    xml += '<constant-library>';
    let errorInfo = '';
    data.forEach((item) => {
        if (!item.name || item.name.length < 1) {
            errorInfo = '常量分类名称不能为空.';
            return false;
        }
        if (!item.label || item.label.length < 1) {
            errorInfo = '常量分类标题不能为空.';
            return false;
        }
        xml += "<category name='" + item.name + "' label='" + item.label + "'>";
        const constants = item.constants;
        if (!constants || constants.length === 0) {
            errorInfo = "常量分类[" + item.label + "]下未定义具体的常量信息";
            return false;
        }
        constants.forEach((constant) => {
            if (!constant.name || constant.name.length < 1) {
                errorInfo = '常量名不能为空.';
                return false;
            }
            if (!constant.label || constant.label.length < 1) {
                errorInfo = '常量标题不能为空.';
                return false;
            }
            if (!constant.type || constant.type.length < 1) {
                errorInfo = '常量数据类型不能为空.';
                return false;
            }
            xml += "<constant name='" + constant.name + "' label='" + constant.label + "' type='" + constant.type + "'/>";
        });
        if (errorInfo.length > 1) {
            return false;
        }
        xml += '</category>';
    });
    if (errorInfo.length > 1) {
        window.bootbox.alert(errorInfo + ',不能保存！');
        return;
    }
    xml += '</constant-library>';
    xml = encodeURIComponent(xml);
    const postData: Record<string, string> = {content: xml, file, newVersion: String(newVersion)};
    const url = window._server + '/common/saveFile';
    if (newVersion) {
        window.bootbox.prompt("请输入新版本描述.", function (versionComment) {
            if (!versionComment) {
                return;
            }
            postData.versionComment = versionComment;
            apiSave(url, postData).then(function () {
                window.bootbox.alert('保存成功!');
            });
        });
    } else {
        apiSave(url, postData).then(function () {
            window.bootbox.alert('保存成功!');
        });
    }
}

export function addMaster() {
    return {type: ADD_MASTER};
}

export function deleteMaster(rowIndex: number) {
    return {rowIndex, type: DEL_MASTER};
}

export function deleteSlave(rowIndex: number) {
    return {rowIndex, type: DEL_SLAVE};
}

export function addSlave() {
    return {type: ADD_SLAVE};
}

export function loadMasterData(files: string): ThunkAction {
    return function (dispatch) {
        formPost<Array<{ categories: ConstantCategory[] }>>("/xml", {files}).then(function (data) {
            dispatch({type: LOAD_MASTER_COMPLETED, masterData: data[0].categories});
        }).catch(function () {
            // Error handled by api/client.js (shows bootbox alert)
        });
    };
}

export function loadSlaveData(masterData: ConstantCategory | {}) {
    return {type: LOAD_SLAVE_COMPLETE, masterRowData: masterData};
}
