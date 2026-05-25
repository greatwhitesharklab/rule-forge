import {handleResponseError} from '../Utils.js';

export const MASTER_LOADED='master_loaded';
export const SLAVE_LOADED='slave_loaded';
export const PERMISSION_CHANGE="permission_change";

export function loadMasterData() {
    return function (dispatch) {
        const url=window._server+"/permission/loadResourceSecurityConfigs";
        fetch(url).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            dispatch({type:MASTER_LOADED,data});
        }).catch(function (response) {
            handleResponseError(response, '加载权限信息失败,');
        });
    }
};
export function loadSlave(masterRowData) {
    return function (dispatch) {
        return dispatch({type:SLAVE_LOADED,data:masterRowData.projectConfigs});
    }
};
export function save(data) {
    let xml="<?xml version=\"1.0\" encoding=\"utf-8\"?><user-permission>";
    for(let item of data){
        xml+=`<user-permission username="${item.username}">`;
        let projectConfigs=item.projectConfigs || [];
        for(let config of projectConfigs){
            if(!config.project || !config.readProject){
                continue;
            }
            xml+=`<project-config project="${config.project}" read-project="${config.readProject}"
                read-package="${config.readPackage}" write-package="${config.writePackage}"
                read-variable-file="${config.readVariableFile}" write-variable-file="${config.writeVariableFile}"
                read-parameter-file="${config.readParameterFile}" write-parameter-file="${config.writeParameterFile}"
                read-constant-file="${config.readConstantFile}" write-constant-file="${config.writeConstantFile}"
                read-action-file="${config.readActionFile}" write-action-file="${config.writeActionFile}"
                read-rule-file="${config.readRuleFile}" write-rule-file="${config.writeRuleFile}"
                read-scorecard-file="${config.readScorecardFile}" write-scorecard-file="${config.writeScorecardFile}"
                read-decision-table-file="${config.readDecisionTableFile}" write-decision-table-file="${config.writeDecisionTableFile}"
                read-decision-tree-file="${config.readDecisionTreeFile}" write-decision-tree-file="${config.writeDecisionTreeFile}"
                read-flow-file="${config.readFlowFile}" write-flow-file="${config.writeFlowFile}"
                />`;
        }
        xml+="</user-permission>";
    }
    xml+="</user-permission>";
    xml=encodeURIComponent(xml);
    const url=window._server+"/permission/saveResourceSecurityConfigs";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({content: xml}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function () {
        window.bootbox.alert('保存成功');
    }).catch(function (response) {
        handleResponseError(response, '服务端错误：');
    });
};
