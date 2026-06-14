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

/**
 * 构造规则编辑器 iframe URL(修复 B-0:dev 环境所有规则编辑器打开后空白)。
 *
 * 旧代码 `'.' + editorPath + "?file="` 有两个错误叠加:
 *  1. `'.' + '/html/editor.html'` = `'./html/editor.html'`,iframe 嵌在 `/html/frame.html` 内,
 *     相对路径解析成 `/html/html/editor.html`(双 html)→ vite 返回 404。
 *  2. editorPath 已含 `?type=`,再拼 `?file=` 产生 `?type=xxx?file=`(双 `?`),
 *     `editor.html` 里 `URLSearchParams.get('type')` 取到 `"xxx?file=..."`,switch-case 失配 → DOM 不注入 → 空白。
 *
 * 正确做法:用绝对 editorPath(无 `.` 前缀),用 `&` 拼接 file(editorPath 已含 `?`),无 `?` 时才用 `?`。
 */
export function buildEditorUrl(editorPath: string | (() => void), file: string): string {
    // editorPath 类型为联合:文件节点是 string(实际编辑器 URL),root 等节点是 debug 函数。
    // 构造 URL 只对 string 有意义;函数(不应在文件点击时出现)安全降级返回空串。
    if (typeof editorPath !== 'string') {
        return '';
    }
    const sep = editorPath.includes('?') ? '&' : '?';
    return editorPath + sep + 'file=' + file;
}

/**
 * editor.html `type=` 查询参数 → SPA 路由段映射表。
 *
 * 用 `editor.html?type=<type>` 构造的 URL(VersionListDialog 的 data.editorPath、
 * TreeItem 的 data.editorPath)在 SPA 化后要改走 `/app/editor/<segment>`。绝大多数
 * type 与路由段同名,只有 flowbpmn 是例外(路由段是 flow)。
 *
 * 权威路由表见 main.tsx 的 `<Route path="editor/...">`。
 *
 * key   = editorPath 里 `type=` 后的字符串(全小写,如 'ruleset'、'flowbpmn')。
 * value = SPA 路由段(`/app/editor/<value>`)。
 */
const EDITOR_TYPE_TO_SPA_SEGMENT: Record<string, string> = {
    ruleset: 'ruleset',
    decisiontable: 'decisiontable',
    scriptdecisiontable: 'scriptdecisiontable',
    decisiontree: 'decisiontree',
    scorecard: 'scorecard',
    complexscorecard: 'complexscorecard',
    crosstab: 'crosstab',
    variable: 'variable',
    constant: 'constant',
    parameter: 'parameter',
    action: 'action',
    resource: 'resource',
    package: 'package',
    // flowbpmn 的 SPA 路由段是 flow(不是 flowbpmn)
    flowbpmn: 'flow',
    drl: 'drl',
    permission: 'permission',
    client: 'client',
};

/**
 * 把 editorPath(含 `?type=<type>` 的字符串)解析出 SPA 路由段。
 *
 * 输入样例:`'/html/editor.html?type=flowbpmn'` → `'flow'`。
 *
 * @returns SPA 路由段(`/app/editor/<返回值>`),或 `null` 表示该 type 尚无 SPA 路由
 *          (如 'ul' 脚本决策集),调用方应回退到原 iframe 逻辑。
 */
export function editorPathToSpaSegment(editorPath: string | (() => void)): string | null {
    if (typeof editorPath !== 'string') {
        return null;
    }
    const match = editorPath.match(/[?&]type=([^&]+)/);
    if (!match) {
        return null;
    }
    const type = decodeURIComponent(match[1]).toLowerCase();
    return EDITOR_TYPE_TO_SPA_SEGMENT[type] ?? null;
}

/**
 * 文件路径 → SPA 路由段映射表(按文件扩展名)。
 *
 * 用于 ReferenceDialog:后端返回的 `file.editor` 是旧版单页 HTML 路径
 * (如 `/ruleset-editor.html`),不含 `type=`,无法用 {@link editorPathToSpaSegment} 解析。
 * 改按 `file.path` 的扩展名映射到 SPA 路由段(跟 TreeItem.tsx 的判定逻辑一致)。
 *
 * 注意:UL(`.ul.xml` 脚本决策集)目前无 SPA 路由,不在此表内 → 返回 null → 走 iframe fallback。
 */
const FILE_EXT_TO_SPA_SEGMENT: { ext: string; segment: string }[] = [
    { ext: '.rs.xml', segment: 'ruleset' },
    { ext: '.dt.xml', segment: 'decisiontable' },
    { ext: '.sdt.xml', segment: 'scriptdecisiontable' },
    { ext: '.dtree.xml', segment: 'decisiontree' },
    { ext: '.sc.xml', segment: 'scorecard' },
    { ext: '.sc', segment: 'scorecard' },
    { ext: '.complexscorecard', segment: 'complexscorecard' },
    { ext: '.ct.xml', segment: 'crosstab' },
    { ext: '.vl.xml', segment: 'variable' },
    { ext: '.cl.xml', segment: 'constant' },
    { ext: '.pl.xml', segment: 'parameter' },
    { ext: '.al.xml', segment: 'action' },
    { ext: '.rl.xml', segment: 'flow' },
];

/**
 * 把文件路径按扩展名映射到 SPA 路由段。
 *
 * 输入样例:`'/p/foo.dt.xml'` → `'decisiontable'`;`'/p/bar.rl.xml'` → `'flow'`。
 *
 * @returns SPA 路由段,或 `null`(如 `.ul.xml` 脚本决策集 / `.rp` 知识包,这些
 *          由各自的特殊入口处理,本函数不管)。
 */
export function filePathToSpaSegment(fullPath: string): string | null {
    if (!fullPath) {
        return null;
    }
    const lower = fullPath.toLowerCase();
    for (const entry of FILE_EXT_TO_SPA_SEGMENT) {
        if (lower.endsWith(entry.ext)) {
            return entry.segment;
        }
    }
    return null;
}

export function handleResponseError(response: Response | { status?: number; text?: () => Promise<string> }, prefix?: string): void | Promise<void> {
    if ((response as Response).status === 401) {
        alert("权限不足，不能进行此操作.");
    } else if ((response as Response).text) {
        return (response as Response).text().then(function (text: string) {
            var msg = text ? (prefix || "服务端错误：") + text : (prefix || "服务端出错");
            alert("<span style='color: red'>" + msg + "</span>");
        });
    } else {
        alert("<span style='color: red'>" + (prefix || "服务端出错") + "</span>");
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

import {alert} from '@/utils/modal';
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
