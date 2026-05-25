import { version } from 'react';
import Styles from '../Styles.js';
import {handleResponseError} from '../Utils.js';

let __ui_id = 1;

export function uniqueID() {
    return '_ui_' + (__ui_id++);
}

// todo unused function
export function refactorContent(file, callback) {
    var url = window._server + "/common/refactorContent";
    fetch(url, {
        method: "POST",
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams(file).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function () {
        callback.call(this)
    }).catch(function (response) {
        handleResponseError(response, '服务端错误：');
    });
}

export function loadFileVersions(file, callback) {
    var url = window._server + '/frame/fileVersions';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({path: file}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        if (data && data.files && data.files.length) {
            buildData(data.files);
            callback(data.files);
        } else {
            alert("未获取到文件[" + file + "]的版本信息.");
        }
    }).catch(function () {
        alert("加载文件[" + file + "]的版本信息失败.");
    });
}

export function loadResourceTreeData(data, callback) {
    var url = window._server + '/common/loadResourceTreeData';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams(data).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        buildData(data);
        callback(data);
    }).catch(function () {
        alert('加载资源失败.');
    });
}

// 获取测试规则集
export function loadTestRuleSets(file, version, callback) {
    var url = window._server + '/common/loadXml';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files: `${file}:${version}`}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        var ruleset = data[0] || {};
        var rules = ruleset["rules"] || [];
        console.log('获取规则集', rules)
        callback(rules)
    }).catch(function () {
        bootbox.alert('加载资源失败.');
        callback([])
    });
}

// 选择版本和规则测试集后获取数据源
export function loadVariableCategories(params, callback) {
    var url = window._server + '/test/variableCategories/load';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(params)
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        callback(data)
    }).catch(function () {
        bootbox.alert('加载资源失败.');
        callback([])
    });
}

// 根据订单号获取数据源
export function searchForAppId(appId, projectId, callback) {
    var url = window._server + '/test/data/appId';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({appId, projectId}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res) {
        if(res.status) {
            callback(res.data)
        } else {
            bootbox.alert(res.msg || '加载资源失败.');
            callback([]);
        }
    }).catch(function () {
        bootbox.alert('加载资源失败.');
        callback([]);
    });
}

// 开始测试
export function beginTest(postData, type, callback) {
    var url = window._server + '/test/fast';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(postData)
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res) {
        if (res.status) {
            callback(res.data, true);
        } else {
            if(type === 'json') {
                bootbox.alert('JSON格式错误，请检查输入内容.');
            } else {
                bootbox.alert(res.msg || '测试失败');
            }
            callback([], false);
        }
    }).catch(function () {
        if(type === 'json') {
            bootbox.alert('JSON格式错误，请检查输入内容.');
        } else {
            bootbox.alert('接口调用失败.');
        }
        callback([], false);
    });
}

export function buildData(data) {
    switch (data.type) {
        case "root":
            data._icon = Styles.frameStyle.getRootIcon();
            data._style = Styles.frameStyle.getRootIconStyle();
            break;
        case "folder":
            data._icon = Styles.frameStyle.getFolderIcon();
            data._style = Styles.frameStyle.getFolderIconStyle();
            break;
        case "rule":
            data._icon = Styles.frameStyle.getRuleIcon();
            data._style = Styles.frameStyle.getRuleIconStyle();
            data.editorPath = "/ruleeditor";
            break;
        case "project":
            data._icon = Styles.frameStyle.getProjectIcon();
            data._style = Styles.frameStyle.getProjectIconStyle();
            break;
        case "resource":
            data._icon = Styles.frameStyle.getResourceIcon();
            data._style = Styles.frameStyle.getResourceIconStyle();
            break;
        case "resourcePackage":
            data._icon = Styles.frameStyle.getResourcePackageIcon();
            data._style = Styles.frameStyle.getResourcePackageIconStyle();
            data.editorPath = "/packageeditor";
            break;
        case "lib":
            data._icon = Styles.frameStyle.getLibIcon();
            data._style = Styles.frameStyle.getLibIconStyle();
            break;
        case "action":
            data._icon = Styles.frameStyle.getActionIcon();
            data._style = Styles.frameStyle.getActionIconStyle();
            data.editorPath = "/actioneditor";
            break;
        case "parameter":
            data._icon = Styles.frameStyle.getParameterIcon();
            data._style = Styles.frameStyle.getParameterIconStyle();
            data.editorPath = "/parametereditor";
            break;
        case "constant":
            data._icon = Styles.frameStyle.getConstantIcon();
            data._style = Styles.frameStyle.getConstantIconStyle();
            data.editorPath = "/constanteditor";
            break;
        case "variable":
            data._icon = Styles.frameStyle.getVariableIcon();
            data._style = Styles.frameStyle.getVariableIconStyle();
            data.editorPath = "/variableeditor";
            break;
        case "ruleLib":
            data._icon = Styles.frameStyle.getRuleLibIcon();
            data._style = Styles.frameStyle.getRuleLibIconStyle();
            break;
        case "decisionTableLib":
            data._icon = Styles.frameStyle.getDecisionTableLibIcon();
            data._style = Styles.frameStyle.getDecisionTableLibIconStyle();
            break;
        case "decisionTreeLib":
            data._icon = Styles.frameStyle.getDecisionTreeLibIcon();
            data._style = Styles.frameStyle.getDecisionTreeLibIconStyle();
            break;
        case "flowLib":
            data._icon = Styles.frameStyle.getFlowLibIcon();
            data._style = Styles.frameStyle.getFlowLibIconStyle();
            break;
        case "ul":
            data._icon = Styles.frameStyle.getUlIcon();
            data._style = Styles.frameStyle.getUlIconStyle();
            data.editorPath = "/uleditor";
            break;
        case "decisionTable":
            data._icon = Styles.frameStyle.getDecisionTableIcon();
            data._style = Styles.frameStyle.getDecisionTableIconStyle();
            data.editorPath = "/decisiontableeditor";
            break;
        case "scriptDecisionTable":
            data._icon = Styles.frameStyle.getScriptDecisionTableIcon();
            data._style = Styles.frameStyle.getScriptDecisionTableIconStyle();
            data.editorPath = "/scriptdecisiontableeditor";
            break;
        case "decisionTree":
            data._icon = Styles.frameStyle.getDecisionTreeIcon();
            data._style = Styles.frameStyle.getDecisionTreeIconStyle();
            data.editorPath = "/decisiontreeditor";
            break;
        case "flow":
            data._icon = Styles.frameStyle.getFlowIcon();
            data._style = Styles.frameStyle.getFlowIconStyle();
            data.editorPath = "/floweditor";
            break;
        case "scorecard":
            data._icon = Styles.frameStyle.getScorecardIcon();
            data._style = Styles.frameStyle.getScorecardIconStyle();
            data.editorPath = "/scorecardeditor";
            break;
        case "complexscorecard":
            data._icon = Styles.frameStyle.getComplexScorecardIcon();
            data._style = Styles.frameStyle.getComplexScorecardIconStyle();
            data.editorPath = "/complexscorecardeditor";
            break;
    }
    var children = data.children;
    if (children) {
        children.forEach((child, index) => {
            buildData(child);
        });
    }
}
