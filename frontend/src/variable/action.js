import {ajaxSave} from '../Utils.js';

export const LOAD_MASTER_COMPLETED = 'load_master_completed';
export const LOAD_SLAVE_COMPLETE = 'load_slave_completed';
export const ADD_MASTER = 'add_master';
export const DEL_MASTER = 'del_master';
export const ADD_SLAVE = 'add_slave';
export const DEL_SLAVE = 'del_slave';
export const GENERATED_FIELDS = 'generated_fields';
export const IMPORT_FIELDS = 'IMPORT_FIELDS';
export const SAVE = 'save';
export const SAVE_COMPLETED = 'save_completed';

export function save(newVersion, file) {
    return {newVersion, file, type: SAVE};
}

export function saveData(data, newVersion, file) {
    let xml = '<?xml version="1.0" encoding="utf-8"?>';
    xml += '<variable-library>';
    let errorInfo = '';
    data.forEach((item, index) => {
        if (!item.name || item.name.length < 1) {
            errorInfo = '变量分类名称不能为空.';
            return false;
        }
        if (!item.clazz || item.clazz.length < 1) {
            errorInfo = '变量类路径不能为空.';
            return false;
        }
        xml += "<category name='" + item.name + "' type='" + item.type + "' clazz='" + item.clazz + "'>";
        const variables = item.variables;
        if (!variables || variables.length === 0) {
            errorInfo = "变量分类[" + item.name + "]下未定义具体变量信息.";
            return false;
        }
        let nameList = []
        let labelList = []
        variables.forEach((variable, i) => {
            if (!variable.name || variable.name.length < 1) {
                errorInfo = '变量名不能为空';
                return false;
            }
            if (!variable.label || variable.label.length < 1) {
                errorInfo = '变量标题不能为空';
                return false;
            }
            if (!variable.type || variable.type.length < 1) {
                errorInfo = '变量数据类型不能为空';
                return false;
            }
            if (nameList.indexOf(variable.name) > -1) {
                errorInfo = '[' + item.name + ']变量名[' + variable.name + ']重复';
                return false;
            }
            if (labelList.indexOf(variable.label) > -1) {
                errorInfo = '[' + item.name + ']变量标题[' + variable.label + ']重复';
                return false;
            }
            nameList.push(variable.name)
            labelList.push(variable.label)
            xml += "<var act='InOut' name='" + variable.name + "' label='" + variable.label + "' type='" + variable.type + "'/>";
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
                window.bootbox.alert('保存成功!');
            })
        });
    } else {
        ajaxSave(url, postData, function () {
            window.bootbox.alert('保存成功!');
        })
    }
    return {type: SAVE_COMPLETED};
}

export function importFields(rowIndex, jsonResult) {
    return {rowIndex, jsonResult, type: IMPORT_FIELDS};
}

export function addMaster(masterName) {
    return {masterName, type: ADD_MASTER};
}

export function deleteMaster(rowIndex) {
    return {rowIndex, type: DEL_MASTER};
}

export function deleteSlave(rowIndex) {
    return {rowIndex, type: DEL_SLAVE};
}

export function addSlave() {
    return {type: ADD_SLAVE};
}

export function generateFields(rowIndex, clazz) {
    return function (dispatch) {
        let url = window._server + '/variableeditor/generateFields';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({clazz}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (result) {
            dispatch({rowIndex, variables: result, type: GENERATED_FIELDS});
        }).catch(function (response) {
            if (response && response.text) {
                response.text().then(function(text) {
                    window.bootbox.alert("<span style='color: red'>生成字段失败,服务端错误：" + text + "</span>");
                });
            } else {
                window.bootbox.alert("<span style='color: red'>生成字段失败,服务端出错</span>");
            }
        });
    }
}

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
        }).catch(function (response) {
            if (response && response.text) {
                response.text().then(function(text) {
                    window.bootbox.alert("<span style='color: red'>加载数据失败,服务端错误：" + text + "</span>");
                });
            } else {
                window.bootbox.alert("<span style='color: red'>加载数据失败,服务端出错</span>");
            }
        });
    }
}

export function loadSlaveData(masterData) {
    return {type: LOAD_SLAVE_COMPLETE, masterRowData: masterData};
}
