import {ajaxSave} from '../Utils.js';
import * as componentEvent from '../components/componentEvent.js';

export const LOAD_MASTER_COMPLETED = 'load_master_completed';
export const LOAD_SLAVE_COMPLETE = 'load_slave_completed';
export const GENERATED_FIELDS = 'generated_fields';
export const IMPORT_FIELDS = 'IMPORT_FIELDS';
export const SAVE_COMPLETED = 'save_completed';
export const SAVE = 'save';

export function loadMasterData(files) {
    return function (dispatch) {
        var url = window._server + "/xml";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({files}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            dispatch({type: LOAD_MASTER_COMPLETED, masterData: data[0]});
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function (response) {
            if (response && response.text) {
                response.text().then(function(text) {
                    window.bootbox.alert("<span style='color: red'>加载数据失败,服务端错误：" + text + "</span>");
                });
            } else {
                window.bootbox.alert("<span style='color: red'>加载数据失败,服务端出错</span>");
            }
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    }
}

export function loadSlaveData(masterData) {
    return {type: LOAD_SLAVE_COMPLETE, masterRowData: masterData};
}

export function reFresh(file) {
    return dispatch => {
        dispatch(generateVariableLibrary(file));
    };
}

export function addVariable(data, file) {
    console.log(data)
    return function (dispatch) {
        var url = window._server + "/common/addVariable";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams(data).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            dispatch(generateVariableLibrary(file));
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function (response) {
            if (response && response.text) {
                response.text().then(function(text) {
                    window.bootbox.alert("<span style='color: red'>保存数据失败,服务端错误：" + text + "</span>");
                });
            } else {
                window.bootbox.alert("<span style='color: red'>保存数据失败,服务端出错</span>");
            }
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    }
}

export function generateVariableLibrary(file) {
    return function (dispatch) {
        let url = window._server + '/variableeditor/generateVariableLibrary';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'}
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            dispatch({type: LOAD_MASTER_COMPLETED, masterData: data.variableCategories});
            if (file) {
                dispatch({type: SAVE, file, newVersion: false});
            }
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function (response) {
            if (response && response.text) {
                response.text().then(function(text) {
                    window.bootbox.alert("<span style='color: red'>生成字段失败,服务端错误：" + text + "</span>");
                });
            } else {
                window.bootbox.alert("<span style='color: red'>生成字段失败,服务端出错</span>");
            }
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    }
}
export function save(newVersion, file) {
    return {newVersion, file, type: SAVE};
}
export function saveData(data, newVersion, file) {
    let xml = '<?xml version="1.0" encoding="utf-8"?>';
    xml += '<variable-library>';
    let errorInfo = '';
    const escapeXml = (unsafe) => {
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
        let nameList = []
        let labelList = []
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
    let postData = {content: xml, file, newVersion};
    const url = window._server + '/common/saveFile';
    if (newVersion) {
        bootbox.prompt("请输入新版本描述.", function (versionComment) {
            if (!versionComment) {
                return;
            }
            postData.versionComment = versionComment;
            ajaxSave(url, postData, function () {
                // window.bootbox.alert('保存成功!');
            })
        });
    } else {
        ajaxSave(url, postData, function () {
            // window.bootbox.alert('保存成功!');
        })
    }
    return {type: SAVE_COMPLETED};
}
