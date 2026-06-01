import Styles from '../Styles.js';
import { formPost, jsonPost } from '../api/client.js';

let __ui_id = 1;

export function uniqueID(): string {
    return '_ui_' + (__ui_id++);
}

// todo unused function
export function refactorContent(file: Record<string, string>, callback: () => void): void {
    formPost('/common/refactorContent', file, { silent: true }).then(function () {
        callback();
    });
}

export function loadFileVersions(file: string, callback: (files: TreeNodeData[]) => void): void {
    formPost<{ files?: TreeNodeData[] }>('/frame/fileVersions', { path: file }, { silent: true }).then(function (data) {
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

export function loadResourceTreeData(data: Record<string, string | boolean | undefined>, callback: (data: TreeNodeData) => void): void {
    const filtered: Record<string, string> = {};
    Object.keys(data).forEach(function (key) {
        if (data[key] !== undefined && data[key] !== null) {
            filtered[key] = String(data[key]);
        }
    });
    formPost<TreeNodeData>('/common/loadResourceTreeData', filtered, { silent: true }).then(function (data) {
        buildData(data);
        callback(data);
    }).catch(function () {
        alert('加载资源失败.');
    });
}

// 获取测试规则集
export function loadTestRuleSets(file: string, version: string, callback: (rules: unknown[]) => void): void {
    formPost<Array<{ rules?: unknown[] }>>('/common/loadXml', { files: `${file}:${version}` }, { silent: true }).then(function (data) {
        const ruleset = data[0] || {};
        const rules = ruleset["rules"] || [];
        console.log('获取规则集', rules);
        callback(rules);
    }).catch(function () {
        window.bootbox.alert('加载资源失败.');
        callback([]);
    });
}

// 选择版本和规则测试集后获取数据源
export function loadVariableCategories(params: Record<string, unknown>, callback: (data: unknown[]) => void): void {
    jsonPost<unknown[]>('/test/variableCategories/load', params, { silent: true }).then(function (data) {
        callback(data);
    }).catch(function () {
        window.bootbox.alert('加载资源失败.');
        callback([]);
    });
}

// 根据订单号获取数据源
export function searchForAppId(appId: string, projectId: string, callback: (data: unknown[]) => void): void {
    formPost<{ status?: boolean; data?: unknown[]; msg?: string }>('/test/data/appId', { appId, projectId }, { silent: true }).then(function (res) {
        if (res.status) {
            callback(res.data || []);
        } else {
            window.bootbox.alert(res.msg || '加载资源失败.');
            callback([]);
        }
    }).catch(function () {
        window.bootbox.alert('加载资源失败.');
        callback([]);
    });
}

// 开始测试
export function beginTest(postData: Record<string, unknown>, type: string, callback: (data: unknown[], success: boolean) => void): void {
    jsonPost<{ status?: boolean; data?: unknown[]; msg?: string }>('/test/fast', postData, { silent: true }).then(function (res) {
        if (res.status) {
            callback(res.data || [], true);
        } else {
            if (type === 'json') {
                window.bootbox.alert('JSON格式错误，请检查输入内容.');
            } else {
                window.bootbox.alert(res.msg || '测试失败');
            }
            callback([], false);
        }
    }).catch(function () {
        if (type === 'json') {
            window.bootbox.alert('JSON格式错误，请检查输入内容.');
        } else {
            window.bootbox.alert('接口调用失败.');
        }
        callback([], false);
    });
}

export function buildData(data: TreeNodeData | TreeNodeData[]): void {
    const items = Array.isArray(data) ? data : [data];
    items.forEach(function (item) {
        switch (item.type) {
            case "root":
                item._icon = Styles.frameStyle.getRootIcon();
                item._style = Styles.frameStyle.getRootIconStyle();
                break;
            case "folder":
                item._icon = Styles.frameStyle.getFolderIcon();
                item._style = Styles.frameStyle.getFolderIconStyle();
                break;
            case "rule":
                item._icon = Styles.frameStyle.getRuleIcon();
                item._style = Styles.frameStyle.getRuleIconStyle();
                item.editorPath = "/ruleeditor";
                break;
            case "project":
                item._icon = Styles.frameStyle.getProjectIcon();
                item._style = Styles.frameStyle.getProjectIconStyle();
                break;
            case "resource":
                item._icon = Styles.frameStyle.getResourceIcon();
                item._style = Styles.frameStyle.getResourceIconStyle();
                break;
            case "resourcePackage":
                item._icon = Styles.frameStyle.getResourcePackageIcon();
                item._style = Styles.frameStyle.getResourcePackageIconStyle();
                item.editorPath = "/packageeditor";
                break;
            case "lib":
                item._icon = Styles.frameStyle.getLibIcon();
                item._style = Styles.frameStyle.getLibIconStyle();
                break;
            case "action":
                item._icon = Styles.frameStyle.getActionIcon();
                item._style = Styles.frameStyle.getActionIconStyle();
                item.editorPath = "/actioneditor";
                break;
            case "parameter":
                item._icon = Styles.frameStyle.getParameterIcon();
                item._style = Styles.frameStyle.getParameterIconStyle();
                item.editorPath = "/parametereditor";
                break;
            case "constant":
                item._icon = Styles.frameStyle.getConstantIcon();
                item._style = Styles.frameStyle.getConstantIconStyle();
                item.editorPath = "/constanteditor";
                break;
            case "variable":
                item._icon = Styles.frameStyle.getVariableIcon();
                item._style = Styles.frameStyle.getVariableIconStyle();
                item.editorPath = "/variableeditor";
                break;
            case "ruleLib":
                item._icon = Styles.frameStyle.getRuleLibIcon();
                item._style = Styles.frameStyle.getRuleLibIconStyle();
                break;
            case "decisionTableLib":
                item._icon = Styles.frameStyle.getDecisionTableLibIcon();
                item._style = Styles.frameStyle.getDecisionTableLibIconStyle();
                break;
            case "decisionTreeLib":
                item._icon = Styles.frameStyle.getDecisionTreeLibIcon();
                item._style = Styles.frameStyle.getDecisionTreeLibIconStyle();
                break;
            case "flowLib":
                item._icon = Styles.frameStyle.getFlowLibIcon();
                item._style = Styles.frameStyle.getFlowLibIconStyle();
                break;
            case "ul":
                item._icon = Styles.frameStyle.getUlIcon();
                item._style = Styles.frameStyle.getUlIconStyle();
                item.editorPath = "/uleditor";
                break;
            case "decisionTable":
                item._icon = Styles.frameStyle.getDecisionTableIcon();
                item._style = Styles.frameStyle.getDecisionTableIconStyle();
                item.editorPath = "/decisiontableeditor";
                break;
            case "scriptDecisionTable":
                item._icon = Styles.frameStyle.getScriptDecisionTableIcon();
                item._style = Styles.frameStyle.getScriptDecisionTableIconStyle();
                item.editorPath = "/scriptdecisiontableeditor";
                break;
            case "decisionTree":
                item._icon = Styles.frameStyle.getDecisionTreeIcon();
                item._style = Styles.frameStyle.getDecisionTreeIconStyle();
                item.editorPath = "/decisiontreeditor";
                break;
            case "flow":
                item._icon = Styles.frameStyle.getFlowIcon();
                item._style = Styles.frameStyle.getFlowIconStyle();
                item.editorPath = "/floweditor";
                break;
            case "scorecard":
                item._icon = Styles.frameStyle.getScorecardIcon();
                item._style = Styles.frameStyle.getScorecardIconStyle();
                item.editorPath = "/scorecardeditor";
                break;
            case "complexscorecard":
                item._icon = Styles.frameStyle.getComplexScorecardIcon();
                item._style = Styles.frameStyle.getComplexScorecardIconStyle();
                item.editorPath = "/complexscorecardeditor";
                break;
        }
        const children = item.children;
        if (children) {
            buildData(children);
        }
    });
}
