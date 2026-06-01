declare const constantLibraries: string[];
declare const actionLibraries: string[];
declare const variableLibraries: string[];
declare const parameterLibraries: string[];
declare function refreshActionLibraries(): void;
declare function refreshConstantLibraries(): void;
declare function refreshVariableLibraries(): void;
declare function refreshParameterLibraries(): void;
declare function refreshFunctionLibraries(): void;

window.iframe_id_ = 1;

export function nextIFrameId(): string {
    window.iframe_id_++;
    return '_iframe' + window.iframe_id_;
}

export function getParameter(name: string): string | null {
    var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)");
    var r = window.location.search.substr(1).match(reg);
    if (r != null) return r[2];
    return null;
}

export function buildProjectNameFromFile(file: string): string | undefined {
    if (file.startsWith('/')) {
        file = file.substring(1);
        const pos = file.indexOf("/");
        return file.substring(0, pos);
    }
}

export function handleResponseError(response: Response | { status?: number; text?: () => Promise<string> }, prefix?: string): void | Promise<void> {
    if ((response as Response).status === 401) {
        window.bootbox.alert("权限不足，不能进行此操作.");
    } else if ((response as Response).text) {
        return (response as Response).text().then(function (text: string) {
            var msg = text ? (prefix || "服务端错误：") + text : (prefix || "服务端出错");
            window.bootbox.alert("<span style='color: red'>" + msg + "</span>");
        });
    } else {
        window.bootbox.alert("<span style='color: red'>" + (prefix || "服务端出错") + "</span>");
    }
}

export function formatDate(date: Date | number | string, format: string): string {
    if (typeof date === 'number') {
        date = new Date(date);
    }
    if (typeof date === 'string') {
        return date;
    }
    var o: Record<string, number> = {
        "M+": date.getMonth() + 1,
        "d+": date.getDate(),
        "H+": date.getHours(),
        "m+": date.getMinutes(),
        "s+": date.getSeconds()
    };
    if (/(y+)/.test(format))
        format = format.replace(RegExp.$1, (date.getFullYear() + "").substring(4 - RegExp.$1.length));
    for (var k in o)
        if (new RegExp("(" + k + ")").test(format))
            format = format.replace(RegExp.$1, (RegExp.$1.length === 1) ? ("" + o[k]) : (("00" + o[k]).substring(("" + o[k]).length)));
    return format;
}

interface Library {
    type: string;
    path: string;
}

export function loadLibraries(libraries: Library[]): void {
    if (!libraries) return;
    for (var i = 0; i < libraries.length; i++) {
        var lib = libraries[i];
        switch (lib.type) {
            case 'Constant': constantLibraries.push(lib.path); break;
            case 'Action': actionLibraries.push(lib.path); break;
            case 'Variable': variableLibraries.push(lib.path); break;
            case 'Parameter': parameterLibraries.push(lib.path); break;
        }
    }
    refreshActionLibraries();
    refreshConstantLibraries();
    refreshVariableLibraries();
    refreshParameterLibraries();
    refreshFunctionLibraries();
}

interface EditorData {
    libraries?: Library[];
    [key: string]: unknown;
}

import { formPost } from './api/client.js';

export function loadEditorData(file: string, extraParams?: Record<string, string>): Promise<EditorData> {
    var params: Record<string, string> = {files: file};
    if (extraParams) {
        Object.assign(params, extraParams);
    }
    return formPost<EditorData[]>('/common/loadXml', params).then(function (data) {
        var editorData = data[0];
        if (editorData.libraries) {
            loadLibraries(editorData.libraries);
        }
        return editorData;
    });
}
