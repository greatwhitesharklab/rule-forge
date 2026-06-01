import {save as apiSave, formPost} from '../api/client.js';
import * as componentEvent from '../components/componentEvent.js';

export const LOAD_MASTER_COMPLETED = 'load_master_completed';
export const LOAD_SLAVE_COMPLETE = 'load_slave_completed';
export const GENERATED_FIELDS = 'generated_fields';
export const IMPORT_FIELDS = 'IMPORT_FIELDS';
export const SAVE_COMPLETED = 'save_completed';
export const SAVE = 'save';
export const DEL_MASTER = 'del_master';
export const ADD_SLAVE = 'add_slave';
export const DEL_SLAVE = 'del_slave';

export interface VariableItem {
    name: string;
    label: string;
    type: string;
    defaultVal?: string;
    logicComment?: string;
    categoryLabel?: string;
    dsStatus?: number;
}

export interface ResourceCategory {
    name: string;
    type: string;
    clazz: string;
    variables: VariableItem[];
}

export function loadMasterData(files: string) {
    return function (dispatch: Function) {
        formPost<ResourceCategory[][]>("/xml", {files}).then(function (data) {
            dispatch({type: LOAD_MASTER_COMPLETED, masterData: data[0]});
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    }
}

export function loadSlaveData(masterData: ResourceCategory) {
    return {type: LOAD_SLAVE_COMPLETE, masterRowData: masterData};
}

export function reFresh(file: string) {
    return (dispatch: Function) => {
        dispatch(generateVariableLibrary(file));
    };
}

export function addVariable(data: Record<string, string>, file: string) {
    return function (dispatch: Function) {
        formPost("/common/addVariable", data).then(function () {
            dispatch(generateVariableLibrary(file));
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    }
}

export function generateVariableLibrary(file?: string) {
    return function (dispatch: Function) {
        formPost<{ variableCategories: ResourceCategory[] }>('/variableeditor/generateVariableLibrary', {}).then(function (data) {
            dispatch({type: LOAD_MASTER_COMPLETED, masterData: data.variableCategories});
            if (file) {
                dispatch({type: SAVE, file, newVersion: false});
            }
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    }
}

export function save(newVersion: boolean, file: string) {
    return {newVersion, file, type: SAVE};
}

export function saveData(data: ResourceCategory[], newVersion: boolean, file: string): { type: string } | undefined {
    let xml = '<?xml version="1.0" encoding="utf-8"?>';
    xml += '<variable-library>';
    let errorInfo = '';
    const escapeXml = (unsafe: string | undefined): string => {
        if (!unsafe) return '';
        return unsafe.replace(/[<>&'"]/g, (c) => {
            switch (c) {
                case '<': return '&lt;';
                case '>': return '&gt;';
                case '&': return '&amp;';
                case '\'': return '&apos;';
                case '"': return '&quot;';
                default: return c;
            }
        });
    };
    data.forEach((item, index) => {
        xml += "<category name='" + escapeXml(item.name) + "' type='" + escapeXml(item.type) + "' clazz='" + escapeXml(item.clazz) + "'>";
        const variables = item.variables;
        let nameList: string[] = []
        let labelList: string[] = []
        variables.forEach((variable, i) => {
            nameList.push(variable.name)
            labelList.push(variable.label)
            xml += "<var act='InOut' name='" + escapeXml(variable.name) + "' label='" + escapeXml(variable.label) + "' type='" + escapeXml(variable.type) + "'/>";
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
    xml += '</variable-library>';
    xml = encodeURIComponent(xml);
    let postData: Record<string, string> = {content: xml, file, newVersion: String(newVersion)};
    const url = window._server + '/common/saveFile';
    if (newVersion) {
        window.bootbox.prompt("请输入新版本描述.", function (versionComment) {
            if (!versionComment) {
                return;
            }
            postData.versionComment = versionComment;
            apiSave(url, postData).then(function () {
                // window.bootbox.alert('保存成功!');
            })
        });
    } else {
        apiSave(url, postData).then(function () {
            // window.bootbox.alert('保存成功!');
        })
    }
    return {type: SAVE_COMPLETED};
}

export function deleteSlave(rowIndex: number) {
    return {rowIndex, type: DEL_SLAVE};
}
