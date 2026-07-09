import Styles from '../Styles.js';
import {formPost, jsonPost, apiBase} from '../api/client.js';
import * as event from './event.js';
import * as componentEvent from '../components/componentEvent.js';

import {alert, confirm} from '@/utils/modal';
// ---- Action type constants ----
export const ADD = 'add';
export const DEL = 'del';
export const UPDATE = 'upload';
export const LOAD_END = 'load_end';
export const FILE_RENAME = 'file_rename';
export const CREATE_NEW_PROJECT = 'create_new_project';
export const CREATE_NEW_FILE = 'create_new_file';
export const LOAD_CHILDREN_END = 'load_children_end';
export const SET_ACTIVE_PANEL = 'set_active_panel';
export const SET_MONITORING_TAB = 'set_monitoring_tab';
export const SET_SIMULATION_TAB = 'set_simulation_tab';
export const SET_GIT_STATUS_TAB = 'set_git_status_tab';
export const SET_PROJECT_NAME = 'set_project_name';
export const SET_CLASSIFY = 'set_classify';
export const SET_TYPES = 'set_types';
export const SET_SEARCH_FILE_NAME = 'set_search_file_name';
export const SET_CURRENT_GIT_TAG = 'set_current_git_tag';

// ---- Action creators ----

export function setActivePanel(panel: string) {
    return {type: SET_ACTIVE_PANEL, panel};
}

/** 设置当前选中的项目名(原 window._projectName 赋值)。传 null 清空(显示所有项目)。 */
export function setProjectName(projectName: string | null) {
    return {type: SET_PROJECT_NAME, projectName};
}

/** 设置树展示模式(原 window._classify 赋值)。 */
export function setClassify(classify: boolean) {
    return {type: SET_CLASSIFY, classify};
}

/** 设置文件类型过滤(原 window._types 赋值)。传 null 清空。 */
export function setTypes(types: string | null) {
    return {type: SET_TYPES, types};
}

/** 设置文件搜索关键字(原 window.searchFileName 赋值)。传 null 清空。 */
export function setSearchFileName(searchFileName: string | null) {
    return {type: SET_SEARCH_FILE_NAME, searchFileName};
}

/** V5.74.3:设置当前选中的知识包版本 gitTag(原 window._currentGitTag 赋值)。传 null 清空。 */
export function setCurrentGitTag(gitTag: string | null) {
    return {type: SET_CURRENT_GIT_TAG, gitTag};
}

export function setMonitoringTab(tab: string) {
    return {type: SET_MONITORING_TAB, tab};
}

export function setSimulationTab(tab: string) {
    return {type: SET_SIMULATION_TAB, tab};
}

export function setGitStatusTab(tab: string) {
    return {type: SET_GIT_STATUS_TAB, tab};
}

// ---- Thunk action creators ----

/**
 * 从 thunk 的 getState() 提取当前 UI 过滤参数(projectName / classify / types / searchFileName
 * / currentGitTag)。
 * 替代历史 window._projectName / window._classify / window._types / window.searchFileName /
 * window._currentGitTag。
 * getState 类型用 Function 兼容 redux-thunk 默认签名,内部 cast 后读字段。
 */
function readUiFilters(getState: Function): {
    projectName: string | null;
    classify: boolean;
    types: string | null;
    searchFileName: string | null;
    currentGitTag: string | null;
} {
    const st = getState() as {
        ui?: {
            projectName?: string | null;
            classify?: boolean;
            types?: string | null;
            searchFileName?: string | null;
            currentGitTag?: string | null;
        };
    };
    const ui = (st && st.ui) || {};
    return {
        projectName: ui.projectName ?? null,
        classify: ui.classify ?? true,
        types: ui.types ?? null,
        searchFileName: ui.searchFileName ?? null,
        currentGitTag: ui.currentGitTag ?? null,
    };
}

export function createNewFile(newFileName: string, fileType: string, parentNodeData: TreeNodeData) {
    return function (dispatch: Function, getState: Function) {
        const fileName = newFileName + "." + fileType;
        const serverType = FILE_TYPE_MAP[fileType] || fileType;
        const {projectName} = readUiFilters(getState);

        formPost('/frame/createFile', {
            path: encodeURI(parentNodeData.fullPath + "/" + fileName),
            type: serverType
        }).then(function () {
            event.eventEmitter.emit(event.CLOSE_CREATE_FILE_DIALOG);
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            dispatch(loadData(true, projectName, null, null, [parentNodeData.fullPath]));
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}

export function rename(path: string, newPath: string) {
    return function (dispatch: Function, getState: Function) {
        const {classify, projectName, types} = readUiFilters(getState);
        formPost('/frame/fileRename', {
            path: path,
            newPath: newPath,
            classify: String(classify),
            projectName: projectName || '',
            types: types || ''
        }).then(function (data: { repo: { rootFile: TreeNodeData } }) {
            const rootFile = data.repo.rootFile;
            buildData(rootFile, 1);
            dispatch({data: rootFile, type: LOAD_END});
            event.eventEmitter.emit(event.HIDE_RENAME_DIALOG);
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function () {
        });
    };
}

export function createNewProject(newProjectName: string) {
    return function (dispatch: Function) {
        formPost('/frame/createProject', {newProjectName: newProjectName}).then(function () {
            dispatch(setProjectName(newProjectName));
            dispatch(loadData(true, newProjectName));
            event.eventEmitter.emit(event.PROJECT_SELECT, newProjectName);
            event.eventEmitter.emit(event.PROJECT_FILTER_CHANGE, newProjectName);
            event.eventEmitter.emit(event.CLOSE_NEW_PROJECT_DIALOG);
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}

export function createNewFolder(newFolderName: string, parentNodeData: TreeNodeData) {
    const fullFolderName = parentNodeData.fullPath + '/' + newFolderName;
    return function (dispatch: Function, getState: Function) {
        const {classify, projectName, types} = readUiFilters(getState);
        formPost('/frame/createFolder', {
            fullFolderName: fullFolderName,
            classify: String(classify),
            projectName: projectName || '',
            types: types || '',
        }).then(function () {
            event.eventEmitter.emit(event.CLOSE_CREATE_FOLDER_DIALOG);
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            dispatch(loadData(true, projectName, null));
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}

export function fileRename(itemData: TreeNodeData, newName: string) {
    return function (dispatch: Function, getState: Function) {
        const {classify, projectName, types} = readUiFilters(getState);
        var fullPath = itemData.fullPath;
        var namePos = fullPath.lastIndexOf(itemData.name);
        var basePath = fullPath.substring(0, namePos);
        var newFullPath = basePath + newName;
        formPost('/frame/fileRename', {
            path: fullPath,
            newPath: newFullPath,
            classify: String(classify),
            projectName: projectName || '',
            types: types || ''
        }).then(function (data: { repo: { rootFile: TreeNodeData } }) {
            const pos = newName.indexOf('.');
            if (pos !== -1) {
                itemData.fullPath = newFullPath;
                itemData.name = newName;
                dispatch({data: itemData, type: FILE_RENAME});
            } else {
                const rootFile = data.repo.rootFile;
                buildData(rootFile, 1);
                dispatch({data: rootFile, type: LOAD_END});
            }
            event.eventEmitter.emit(event.CLOSE_UPDATE_PROJECT_DIALOG);
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}

function moveFile(path: string, newPath: string, dispatch: Function) {
    const {classify, projectName, types} = readUiFilters(_getState);
    formPost('/frame/fileRename', {path, newPath, classify: String(classify), projectName: projectName || '', types: types || ''}).then(function (data: { repo: { rootFile: TreeNodeData } }) {
        const rootFile = data.repo.rootFile;
        buildData(rootFile, 1);
        dispatch({data: rootFile, type: LOAD_END});
        event.eventEmitter.emit(event.CLOSE_UPDATE_PROJECT_DIALOG);
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
    }).catch(function () {
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
    });
}

export function add(data: TreeNodeData) {
    return {data, type: ADD};
}

export function del(index: number) {
    return {index, type: DEL};
}

export function update(index: number, data: TreeNodeData) {
    return {index, data, type: UPDATE};
}

let _loadDataRequestId = 0;

let _pathsToExpand: string[] = [];

export function loadData(classify?: boolean | null, projectName?: string | null, types?: string | null, searchFileName?: string | null, pathsToExpand?: string[]) {
    if (classify === null || classify === undefined) {
        classify = true;
    }
    if (pathsToExpand) {
        _pathsToExpand = pathsToExpand;
    }
    const requestId = ++_loadDataRequestId;
    return function (dispatch: Function, getState: Function) {
        _dispatch = dispatch;
        _getState = getState;
        const params: Record<string, string> = {};
        if (classify !== undefined && classify !== null) params.classify = String(classify);
        if (projectName !== undefined && projectName !== null) params.projectName = projectName;
        if (types !== undefined && types !== null) params.types = types;
        if (searchFileName !== undefined && searchFileName !== null) params.searchFileName = searchFileName;
        params.projectDetail = 'true';

        formPost('/frame/loadProjects', params).then(function (data: {
            classify: boolean;
            repo: { rootFile: TreeNodeData; publicResource: TreeNodeData; projectNames: string[] };
            user: { import: boolean; export: boolean };
        }) {
            // Skip if a newer request has been made
            if (requestId !== _loadDataRequestId) return;

            const {classify, repo, user} = data;
            const {rootFile, publicResource, projectNames} = repo;
            event.eventEmitter.emit(event.CHANGE_CLASSIFY, classify);
            // Update project list whenever available (needed for project creation auto-select)
            if (projectNames && projectNames.length > 0) {
                event.eventEmitter.emit(event.PROJECT_LIST_CHANGE, projectNames);
            }

            // Determine which project to show in the tree
            const targetProject = projectName || (projectNames && projectNames[0]) || null;

            // Extract the target project's children to show directly (skip root + project layers)
            let treeData: TreeNodeData;
            if (targetProject && rootFile && Array.isArray(rootFile.children)) {
                const projectNode = rootFile.children.find(
                    child => child.name === targetProject || child.fullPath === '/' + targetProject
                );
                if (projectNode && projectNode.children) {
                    // Build a virtual root that contains the project's children directly
                    treeData = {
                        id: rootFile.id,
                        name: rootFile.name,
                        type: 'root',
                        fullPath: rootFile.fullPath,
                        children: projectNode.children
                    };
                } else {
                    treeData = rootFile;
                }
            } else {
                treeData = rootFile;
            }

            buildData(treeData, 1, user);
            buildData(publicResource, 1, user);

            // Mark nodes that should be force-expanded
            if (_pathsToExpand.length > 0) {
                function markForceExpand(node: TreeNodeData | null) {
                    if (node && _pathsToExpand.includes(node.fullPath)) {
                        node._forceExpand = true;
                    }
                    if (node && node.children) {
                        node.children.forEach(markForceExpand);
                    }
                }
                markForceExpand(treeData);
                _pathsToExpand = [];
            }

            dispatch({data: treeData, publicResource: publicResource, type: LOAD_END});
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);

            // V7.7.2:树节点 published 徽标 — 收集项目下所有 V1 节点 fullPath,
            // 单次 POST /v1/publish/status-batch 拿发布状态,回填 _publishedVersion /
            // _publishedStatus,FileTreeNode 据此渲染 "已发布 vX.X.X" 徽标。
            // 非 V1 节点忽略。
            const v1Types = new Set(['v1flow', 'v1library', 'v1ruleset', 'v1decisiontable', 'v1scorecard']);
            const v1Paths: string[] = [];
            function collectV1Paths(node: TreeNodeData | null) {
                if (!node) return;
                if (v1Types.has(node.type)) v1Paths.push(node.fullPath);
                if (node.children) node.children.forEach(collectV1Paths);
            }
            collectV1Paths(treeData);
            if (targetProject && v1Paths.length > 0) {
                jsonPost<Record<string, {status: string; currentVersion: string | null; publishTime: string | null}>>(
                    '/v1/publish/status-batch?project=' + encodeURIComponent(targetProject),
                    v1Paths
                ).then((statusMap) => {
                    if (requestId !== _loadDataRequestId) return;
                    if (!statusMap) return;
                    function enrich(node: TreeNodeData | null) {
                        if (!node) return;
                        if (v1Types.has(node.type)) {
                            const s = statusMap[node.fullPath];
                            if (s) {
                                node._publishedStatus = s.status;
                                node._publishedVersion = s.currentVersion;
                            }
                        }
                        if (node.children) node.children.forEach(enrich);
                    }
                    enrich(treeData);
                    dispatch({data: treeData, publicResource: publicResource, type: LOAD_END});
                }).catch(() => { /* ignore: published 徽标 best-effort,失败不阻塞树 */ });
            }

            // 控制所有节点显示
            const spanEl = document.getElementById('node-' + rootFile.id);
            if (spanEl) {
                const parentLi = spanEl.parentElement;
                if (searchFileName == null || searchFileName === '') {
                    var deepLiChildren = parentLi!.querySelectorAll('ul > li > ul > li');
                    deepLiChildren.forEach(function(child) { (child as HTMLElement).style.display = 'none'; });
                    parentLi!.querySelectorAll('ul > li').forEach(function(item) {
                        const firstI = item.querySelector('i:first-child');
                        if (firstI) { firstI.classList.add('rf-plus'); firstI.classList.remove('rf-minus'); }
                    });
                } else {
                    var allLiChildren = parentLi!.querySelectorAll('li');
                    allLiChildren.forEach(function(child) { (child as HTMLElement).style.display = ''; });
                    parentLi!.querySelectorAll('ul > li').forEach(function(item) {
                        const firstI = item.querySelector('i:first-child');
                        if (firstI) { firstI.classList.add('rf-minus'); firstI.classList.remove('rf-plus'); }
                    });
                }
            }
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}



// 加载子菜单的函数。classify/projectName/types 可省略,thunk 自动从 store 读
// (替代 TreeItem 历史上传 window._classify / window._types)。
export function loadChildren(parentNodeData: TreeNodeData, classify?: boolean | null, projectName?: string | null, types?: string | null) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    return function (dispatch: Function, getState: Function) {
        _dispatch = dispatch;
        _getState = getState;
        const uiFilters = readUiFilters(getState);
        const effectiveClassify = classify !== undefined && classify !== null ? classify : uiFilters.classify;
        const effectiveProjectName = projectName !== undefined ? projectName : uiFilters.projectName;
        const effectiveTypes = types !== undefined ? types : uiFilters.types;
        formPost('/frame/loadProjects', {
            classify: String(effectiveClassify),
            projectName: effectiveProjectName || '',
            types: effectiveTypes || '',
            parentPath: parentNodeData.fullPath,
            loadChildren: 'true'
        }).then(function (data: {
            repo: { rootFile: TreeNodeData; publicResource: TreeNodeData };
            user: { import: boolean; export: boolean };
        }) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            const {repo, user} = data;

            // 从 repo 中提取子菜单数据
            let childrenData: TreeNodeData[] | null = null;

            // 根据父节点的路径来确定从哪里提取子菜单数据
            if (parentNodeData.fullPath === '/') {
                // 如果是根节点，从 rootFile.children 获取
                childrenData = repo.rootFile ? repo.rootFile.children! : [];
            } else if (parentNodeData.type === 'publicResource') {
                // 如果是公共资源，从 publicResource.children 获取
                childrenData = repo.publicResource ? repo.publicResource.children! : [];
            } else {
                // 如果是项目节点，需要从 rootFile.children 中找到对应的项目
                if (repo.rootFile && repo.rootFile.children) {
                    const projectNode = repo.rootFile.children.find(child =>
                        child.name === parentNodeData.name ||
                        child.fullPath === parentNodeData.fullPath
                    );
                    childrenData = projectNode ? projectNode.children! : [];
                }
            }

            if (childrenData && childrenData.length > 0) {
                // 为每个子节点构建数据
                childrenData.forEach(child => {
                    buildData(child, (parentNodeData._level || 0) + 1, user);
                });

                dispatch({
                    parentNodeData,
                    childrenData,
                    type: LOAD_CHILDREN_END
                });
            }
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}

const FILE_TYPE_MAP: Record<string, string> = {
    'vl.xml': 'VariableLibrary', 'cl.xml': 'ConstantLibrary',
    'pl.xml': 'ParameterLibrary', 'al.xml': 'ActionLibrary',
    'rs.xml': 'Ruleset', 'rsl.xml': 'RulesetLib', 'ul': 'UL',
    'dt.xml': 'DecisionTable', 'ct.xml': 'Crosstab', 'dts.xml': 'ScriptDecisionTable',
    'dtree.xml': 'DecisionTree', 'sc': 'Scorecard', 'scc': 'ComplexScorecard',
    'drl': 'Drl',
    // V6.20.0 P3:DMN / PMML(只读/导入,FileTypeUtils 后端识别 .dmn/.pmml)
    'dmn': 'Dmn', 'pmml': 'Pmml',
    // V7.0.0→V7.5.1:V1 决策流(.v1flow.json 统一后缀;.json 兼容旧)
    'v1flow.json': 'V1Flow',
    'json': 'V1Flow',
};

export function buildType(fileType: string): string {
    let pos = fileType.indexOf(':');
    if (pos > -1) {
        fileType = fileType.substring(0, pos);
    }
    let type: string | undefined;
    switch (fileType) {
        case 'vl.xml':
            type = "变量库";
            break;
        case 'cl.xml':
            type = '常量库';
            break;
        case 'pl.xml':
            type = '参数库';
            break;
        case 'al.xml':
            type = '动作库';
            break;
        // V7.21:case 'rl.xml'(BPMN 决策流)已删除 — V1 决策流为唯一决策路径。
        // V6.20.0:DRL 规则
        case "drl":
            type = "DRL 规则";
            break;
        // V6.20.0 P3:DMN / PMML 标准决策模型
        case "dmn":
            type = "DMN 决策表(只读)";
            break;
        case "pmml":
            type = "PMML 模型(只读)";
            break;
        // V7.0.0:V1 决策流
        case "json":
            type = "V1 决策流";
            break;
        // V7.7.2:"rp" case 删除 — 老 .rp 知识包废弃
    }
    if (!type) {
        const info = "Unknow file type :" + fileType;
        alert(info);
        throw info;
    }
    return type;
}

// Global dispatch / getState used by context-menu callbacks (set during loadData thunk
// execution so the menu click closures — invoked later — can dispatch + read store).
let _dispatch: Function = () => {};
let _getState: Function = () => ({ui: {}});

// File clipboard state(剪切 / 复制)。原 window.___cutFileData / window.___copyFileData。
// 上下文菜单的 click 回调是模块级闭包(非 React 组件),所以用模块级 holder 而非 Context;
// 这避免了把临时剪贴板状态挂在 window 全局对象上。
let _cutFileData: TreeNodeData | null = null;
let _copyFileData: TreeNodeData | null = null;
function buildData(data: TreeNodeData, level: number): void;
function buildData(data: TreeNodeData, level: number, user: { import: boolean; export: boolean }): void;
function buildData(data: TreeNodeData, level: number, user?: { import: boolean; export: boolean }): void {
    data._level = level++;
    // NOTE: 历史上各文件类型分支会设置 data.editorPath("/html/editor.html?type=<type>"),
    // 但 SPA 化后 TreeItem 改走 window.open('/app/editor/<type>'),VersionListDialog 改用
    // data.type 直接映射(typeToSpaSegment),editorPath 赋值已全部删除(死代码)。
    switch (data.type) {
        case "root":
            data._icon = Styles.frameStyle.getRootIcon();
            data._style = Styles.frameStyle.getRootIconStyle();
            data.editorPath = function () {
                console.log(data);
            };
            data.contextMenu = [
                {
                    name: '创建新项目',
                    icon: 'rf rf-createpro',
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_NEW_PROJECT_DIALOG, data);
                    }
                }
            ];
            if (user && user.import) {
                data.contextMenu.push({
                        name: '导入项目',
                        icon: 'rf rf-import',
                        click: function () {
                            event.eventEmitter.emit(event.OPEN_IMPORT_PROJECT_DIALOG);
                        }
                    }
                );
            }
            break;
        case "rule":
            data._icon = Styles.frameStyle.getRuleIcon();
            data._style = Styles.frameStyle.getRuleIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        case "project":
            data._icon = Styles.frameStyle.getProjectIcon();
            data._style = Styles.frameStyle.getProjectIconStyle();
            data.contextMenu = [
                {
                    name: '删除项目',
                    icon: 'rf rf-remove',
                    click: function (data: TreeNodeData) {
                        confirm("此操作将删除" + data.name + "项目及其下所有文件，你确定要这样做吗？", function (result) {
                            if (!result) {
                                return;
                            }
                            projectDelete(data, _dispatch);
                        });
                    }
                }
            ];
            if (user && user.export) {
                data.contextMenu.push({
                        name: '导出项目备份',
                        icon: 'rf rf-export',
                        click: function (data: TreeNodeData) {
                            confirm("真的要导出项目" + data.name + "的备份文件吗？", function (result) {
                                if (!result) {
                                    return;
                                }
                                const url = apiBase() + '/frame/exportProjectBackupFile?path=' + encodeURI(encodeURI(data.fullPath));
                                window.open(url, '_blank');
                            });
                        }
                    },
                );
            }
            break;
        case "resource":
            data._icon = Styles.frameStyle.getResourceIcon();
            data._style = Styles.frameStyle.getResourceIconStyle();
            break;
        case "all":
            data._icon = Styles.frameStyle.getResourceIcon();
            data._style = Styles.frameStyle.getResourceIconStyle();
            data.contextMenu = buildFullContextMenu();
            break;
        case "folder":
            data._icon = Styles.frameStyle.getFolderIcon();
            data._style = Styles.frameStyle.getFolderIconStyle();
            data.contextMenu = buildFullContextMenu(true, data.folderType);
            break;
        // V7.7.2:"resourcePackage" case 删除 — 老 .rp 知识包节点废弃
        case "lib":
            data._icon = Styles.frameStyle.getLibIcon();
            data._style = Styles.frameStyle.getLibIconStyle();
            data.contextMenu = buildLibContextMenu();
            break;
        case "action":
            data._icon = Styles.frameStyle.getActionIcon();
            data._style = Styles.frameStyle.getActionIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        case "parameter":
            data._icon = Styles.frameStyle.getParameterIcon();
            data._style = Styles.frameStyle.getParameterIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        case "constant":
            data._icon = Styles.frameStyle.getConstantIcon();
            data._style = Styles.frameStyle.getConstantIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        case "variable":
            data._icon = Styles.frameStyle.getVariableIcon();
            data._style = Styles.frameStyle.getVariableIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        // V7.21:case "flowLib"(BPMN 决策流库)已删除 — V1 决策流为唯一决策路径。
        // V6.20.0:DRL 规则库(新分类,跟老 决策集/决策表 并列)
        case "drlLib":
            data._icon = Styles.frameStyle.getDrlLibIcon();
            data._style = Styles.frameStyle.getDrlLibIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加 DRL 规则',
                    icon: Styles.frameStyle.getDrlIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'drl', nodeData: data});
                    }
                }
            ];
            break;
        // V7.0.0:V1 决策流库(新分类,.json 画布资产)
        case "v1flowLib":
            data._icon = Styles.frameStyle.getFlowIcon();
            data._style = Styles.frameStyle.getFlowIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加 V1 决策流',
                    icon: Styles.frameStyle.getFlowIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'v1flow.json', nodeData: data});
                    }
                }
            ];
            break;
        // V7.21:case "flow"(老 BPMN 决策流文件)已删除 — V1 决策流为唯一决策路径。
        // V6.20.0:DRL 规则文件(.drl) → 走 DRL 编辑器
        case "drl":
            data._icon = Styles.frameStyle.getDrlIcon();
            data._style = Styles.frameStyle.getDrlIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        // V7.0.0:V1 决策流文件(.json) → 走 V1 画布(handleFileOpen → /app/v1-flow)
        case "v1flow":
            data._icon = Styles.frameStyle.getFlowIcon();
            data._style = Styles.frameStyle.getFlowIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        // V7.4:V1 库容器(.v1lib.json,vl/cl/pl 四库)
        case "v1libraryLib":
            data._icon = Styles.frameStyle.getFlowIcon();
            data._style = Styles.frameStyle.getFlowIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加 V1 库',
                    icon: Styles.frameStyle.getFlowIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'V1Library', nodeData: data});
                    }
                }
            ];
            break;
        // V7.4:V1 库文件(.v1lib.json) → 走库编辑器(handleFileOpen → /app/v1-library)
        case "v1library":
            data._icon = Styles.frameStyle.getFlowIcon();
            data._style = Styles.frameStyle.getFlowIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        // V7.5:V1 规则集容器 → 添加规则集文件
        case "v1rulesetLib":
            data._icon = Styles.frameStyle.getFlowIcon();
            data._style = Styles.frameStyle.getFlowIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加 V1 规则集',
                    icon: Styles.frameStyle.getFlowIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'V1RuleSet', nodeData: data});
                    }
                }
            ];
            break;
        // V7.5:V1 规则集文件(.v1rs.json) → 走规则集编辑器(handleFileOpen → /app/v1-ruleset)
        case "v1ruleset":
            data._icon = Styles.frameStyle.getRuleIcon();
            data._style = Styles.frameStyle.getRuleIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        // V7.5:V1 决策表容器 → 添加决策表文件
        case "v1decisiontableLib":
            data._icon = Styles.frameStyle.getDecisionTableIcon();
            data._style = Styles.frameStyle.getDecisionTableIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加 V1 决策表',
                    icon: Styles.frameStyle.getDecisionTableIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'V1DecisionTable', nodeData: data});
                    }
                }
            ];
            break;
        // V7.5:V1 决策表文件(.v1dt.json) → 走决策表编辑器(handleFileOpen → /app/v1-decisiontable)
        case "v1decisiontable":
            data._icon = Styles.frameStyle.getDecisionTableIcon();
            data._style = Styles.frameStyle.getDecisionTableIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        // V7.5:V1 评分卡容器 → 添加评分卡文件
        case "v1scorecardLib":
            data._icon = Styles.frameStyle.getScorecardIcon();
            data._style = Styles.frameStyle.getScorecardIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加 V1 评分卡',
                    icon: Styles.frameStyle.getScorecardIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'V1ScoreCard', nodeData: data});
                    }
                }
            ];
            break;
        // V7.5:V1 评分卡文件(.v1sc.json) → 走评分卡编辑器(handleFileOpen → /app/v1-scorecard)
        case "v1scorecard":
            data._icon = Styles.frameStyle.getScorecardIcon();
            data._style = Styles.frameStyle.getScorecardIconStyle();
            data.contextMenu = buildFileContextMenu();
            break;
        // V6.20.0 P3:DMN 标准决策模型文件(.dmn) → 只读查看器
        case "dmn":
            data._icon = 'rf rf-table'; // 复用 table 图标(无专属 DMN icon,先沿用)
            data._style = '';
            data.contextMenu = buildFileContextMenu();
            break;
        // V6.20.0 P3:PMML 标准模型文件(.pmml) → 只读查看器
        case "pmml":
            data._icon = 'rf rf-scorecard'; // 复用 scorecard 图标
            data._style = '';
            data.contextMenu = buildFileContextMenu();
            break;
    }
    // Ensure container types have a children array so they render as folders
    if (data.children === null || data.children === undefined) {
        var t = data.type;
        if (t === 'lib' || t === 'flowLib' || t === 'drlLib' || t === 'v1flowLib' || t === 'v1libraryLib' || t === 'v1rulesetLib' || t === 'v1decisiontableLib' || t === 'v1scorecardLib' || t === 'resource' || t === 'folder') {
            data.children = [];
        }
    }
    var children = data.children;
    if (children) {
        children.forEach((child) => {
            if (user) {
                buildData(child, level, user);
            } else {
                buildData(child, level);
            }
        });
    }
}

function buildLibContextMenu(): ContextMenuItem[] {
    return [
        {
            name: '添加目录',
            icon: Styles.frameStyle.getFolderIcon(),
            click: function (data: TreeNodeData) {
                event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
            }
        },
        {
            name: '添加变量库',
            icon: Styles.frameStyle.getVariableIcon(),
            click: function (data: TreeNodeData) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'vl.xml', nodeData: data});
            }
        },
        {
            name: '添加常量库',
            icon: Styles.frameStyle.getConstantIcon(),
            click: function (data: TreeNodeData) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'cl.xml', nodeData: data});
            }
        },
        {
            name: '添加参数库',
            icon: Styles.frameStyle.getParameterIcon(),
            click: function (data: TreeNodeData) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'pl.xml', nodeData: data});
            }
        },
        {
            name: '添加动作库',
            icon: Styles.frameStyle.getActionIcon(),
            click: function (data: TreeNodeData) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'al.xml', nodeData: data});
            }
        }
    ];
}

function buildFullContextMenu(isFolder?: boolean, folderType?: string): ContextMenuItem[] {
    const menus: ContextMenuItem[] = [{
        name: isFolder ? '添加子目录' : '添加目录',
        icon: Styles.frameStyle.getFolderIcon(),
        click: function (data: TreeNodeData) {
            event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
        }
    }];
    let addPasteMenuItem = false;
    if (!folderType || folderType === 'all' || folderType === 'lib') {
        menus.push(
            {
                name: '添加变量库',
                icon: Styles.frameStyle.getVariableIcon(),
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'vl.xml', nodeData: data});
                }
            },
            {
                name: '添加常量库',
                icon: Styles.frameStyle.getConstantIcon(),
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'cl.xml', nodeData: data});
                }
            },
            {
                name: '添加参数库',
                icon: Styles.frameStyle.getParameterIcon(),
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'pl.xml', nodeData: data});
                }
            },
            {
                name: '添加动作库',
                icon: Styles.frameStyle.getActionIcon(),
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'al.xml', nodeData: data});
                }
            }
        );
        if (!addPasteMenuItem) {
            menus.push(buildPasteMenuItem());
            addPasteMenuItem = true;
        }
    }
    if (!folderType || folderType === 'all' || folderType === 'ruleLib') {
        menus.push(
            {
                name: '添加向导式决策集',
                icon: Styles.frameStyle.getRuleIcon(),
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rs.xml', nodeData: data});
                }
            },
            {
                name: '添加向导式决策库',
                icon: Styles.frameStyle.getRuleIcon(),
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rsl.xml', nodeData: data});
                }
            },
            {
                name: '添加脚本式决策集',
                icon: Styles.frameStyle.getUlIcon(),
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'ul', nodeData: data});
                }
            }
        );
        if (!addPasteMenuItem) {
            menus.push(buildPasteMenuItem());
            addPasteMenuItem = true;
        }
    }
    if (!folderType || folderType === 'all' || folderType === 'decisionTableLib') {
        menus.push(...[
            {
                name: '添加决策表',
                icon: Styles.frameStyle.getDecisionTableIcon(),
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'dt.xml', nodeData: data});
                }
            },
            {
                name: '添加交叉决策表',
                icon: Styles.frameStyle.getCrossDecisionTableIcon(),
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'ct.xml', nodeData: data});
                }
            }
        ]);
        if (!addPasteMenuItem) {
            menus.push(buildPasteMenuItem());
            addPasteMenuItem = true;
        }
    }
    if (!folderType || folderType === 'all' || folderType === 'decisionTreeLib') {
        menus.push({
            name: '添加决策树',
            icon: Styles.frameStyle.getDecisionTreeIcon(),
            click: function (data: TreeNodeData) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'dtree.xml', nodeData: data});
            }
        });
        if (!addPasteMenuItem) {
            menus.push(buildPasteMenuItem());
            addPasteMenuItem = true;
        }
    }
    if (!folderType || folderType === 'all' || folderType === 'scorecardLib') {
        menus.push({
            name: '添加评分卡',
            icon: Styles.frameStyle.getScorecardIcon(),
            click: function (data: TreeNodeData) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'sc', nodeData: data});
            }
        });
    }
    // V7.21:通用菜单的 BPMN 决策流(flowLib)分支已删除 — V1 决策流为唯一决策路径。
    if (isFolder) {
        menus.push(
            {
                name: '删除',
                icon: 'rf rf-remove',
                click: function (data: TreeNodeData) {
                    confirm("删除目录[" + data.name + "],将会同时删除其下所有子目录及文件，确认吗？", function (result) {
                        if (!result) {
                            return;
                        }
                        fileDelete(data, _dispatch, true);
                    });
                }
            },
            {
                name: '修改目录名',
                icon: 'rf rf-rename',
                click: function (data: TreeNodeData) {
                    event.eventEmitter.emit(event.SHOW_RENAME_DIALOG, data);
                }
            },
            {
                name: '锁定目录',
                icon: 'rf rf-lock',
                click: function (data: TreeNodeData) {
                    lockFile(data.fullPath, _dispatch);
                }
            },
            {
                name: '解锁目录',
                icon: 'rf rf-unlock',
                click: function (data: TreeNodeData) {
                    unlockFile(data.fullPath, _dispatch);
                }
            }
        );
        if (!addPasteMenuItem) {
            menus.push(buildPasteMenuItem());
            addPasteMenuItem = true;
        }
    }
    return menus;
}

function buildPasteMenuItem(): ContextMenuItem {
    return {
        name: '粘贴文件',
        icon: 'rf rf-paste',
        click: function (data: TreeNodeData) {
            let sourceFileData = _cutFileData, copy = false;
            if (!sourceFileData) {
                sourceFileData = _copyFileData;
                copy = true;
            }
            if (!sourceFileData) {
                alert("没有文件可供粘贴！");
                return;
            }
            const newDir = data.fullPath;
            const newFullPath = newDir + "/" + sourceFileData.name, oldFullPath = sourceFileData.fullPath;
            if (oldFullPath === newFullPath) {
                alert("目录未改变，不能进行此操作！");
                return;
            }
            let info = "真的要移动文件【" + sourceFileData.name + "】到【" + newDir + "】目录吗？";
            if (copy) {
                info = "真的要复制文件【" + sourceFileData.name + "】到【" + newDir + "】目录吗？";
            }
            confirm(info, function (result) {
                if (!result) {
                    return;
                }
                _cutFileData = null;
                _copyFileData = null;
                if (!copy) {
                    moveFile(oldFullPath, newFullPath, _dispatch);
                } else {
                    formPost('/frame/copyFile', {newFullPath, oldFullPath}).then(function (data: { repo: { rootFile: TreeNodeData } }) {
                        const rootFile = data.repo.rootFile;
                        buildData(rootFile, 1);
                        _dispatch({data: rootFile, type: LOAD_END});
                    }).catch(function () {
                    });
                }
            });
        }
    };
}

function buildFileContextMenu(): ContextMenuItem[] {
    return [
        {
            name: '查看源码',
            icon: 'rf rf-code',
            click: function (data: TreeNodeData, dispatch?: (action: unknown) => void) {
                // V5.74.3:seeFileSource 是 thunk,需 dispatch 触发(getState 读 currentGitTag)
                dispatch?.(seeFileSource(data));
            }
        },
        {
            name: '查看版本信息',
            icon: 'rf rf-version',
            click: function (data: TreeNodeData) {
                data['page'] = 1;
                seeFileVersions(data);
            }
        },
        {
            name: '删除文件',
            icon: 'rf rf-remove',
            click: function (data: TreeNodeData) {
                confirm("真的要删除[" + data.name + "]文件吗？", function (result) {
                    if (!result) {
                        return;
                    }
                    fileDelete(data, _dispatch);
                });
            }
        },
        {
            name: '修改文件名',
            icon: 'rf rf-rename',
            click: function (data: TreeNodeData) {
                event.eventEmitter.emit(event.SHOW_RENAME_DIALOG, data);
            }
        },
        {
            name: '复制文件',
            icon: 'rf rf-copy',
            click: function (data: TreeNodeData) {
                _copyFileData = data;
                _cutFileData = null;
            }
        },
        {
            name: '剪切文件',
            icon: 'rf rf-cut',
            click: function (data: TreeNodeData) {
                _cutFileData = data;
                _copyFileData = null;
            }
        },
        {
            name: '锁定文件',
            icon: 'rf rf-lock',
            click: function (data: TreeNodeData) {
                lockFile(data.fullPath, _dispatch);
            }
        },
        {
            name: '解锁文件',
            icon: 'rf rf-unlock',
            click: function (data: TreeNodeData) {
                unlockFile(data.fullPath, _dispatch);
            }
        }
    ];
}

export function lockFile(file: string, dispatch: Function) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    formPost("/frame/lockFile", {file}).then(function (data: { repo: { rootFile: TreeNodeData } }) {
        const rootFile = data.repo.rootFile;
        buildData(rootFile, 1);
        dispatch({data: rootFile, type: LOAD_END});
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        alert('锁定成功!');
    }).catch(function () {
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
    });
}

export function unlockFile(file: string, dispatch: Function) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    formPost("/frame/unlockFile", {file}).then(function (data: { repo: { rootFile: TreeNodeData } }) {
        const rootFile = data.repo.rootFile;
        buildData(rootFile, 1);
        dispatch({data: rootFile, type: LOAD_END});
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        alert('解锁成功!');
    }).catch(function () {
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
    });
}

export function saveFileSource(file: string, content: string) {
    const encodedContent = encodeURIComponent(content);
    formPost("/common/saveFile", {file, content: encodedContent}).then(function () {
        alert('保存成功!');
    }).catch(function () {
    });
}

/**
 * V5.74.3:thunk 化以通过 getState() 读 currentGitTag(原直接读 window._currentGitTag)。
 * 知识包视图选版本 → setCurrentGitTag 写 store,这里读出来作为 fileSource 请求的 gitTag 参数,
 * 实现"按版本看源码"。
 */
export function seeFileSource(data: TreeNodeData) {
    return function (_dispatch: Function, getState: Function) {
        const {currentGitTag} = readUiFilters(getState);
        const params: Record<string, string> = {path: data.fullPath};
        if (currentGitTag) {
            params.gitTag = currentGitTag;
        }
        formPost("/frame/fileSource", params).then(function (result: { content: string }) {
            event.eventEmitter.emit(event.OPEN_SOURCE_DIALOG, data.fullPath, result.content);
        }).catch(function () {
        });
    };
}

export function seeFileVersions(data: TreeNodeData & { rpp?: string; page?: number }) {
    formPost("/frame/fileVersions", {path: data.fullPath, project: data['rpp'] || '', page: String(data.page || 1)}).then(function (res: { files: TreeNodeData[]; count: number }) {
        const files = res.files;
        const num = res.count;
        event.eventEmitter.emit(event.OPEN_FILE_VERSION_DIALOG, {files, data, num});
    }).catch(function () {
    });
}

function projectDelete(item: TreeNodeData, dispatch: Function, isFolder?: boolean) {
    const {classify, projectName, types} = readUiFilters(_getState);
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    setTimeout(function () {
        formPost("/frame/deleteProject", {
            isFolder: String(!!isFolder),
            path: item.fullPath,
            classify: String(classify),
            projectName: projectName || '',
            types: types || ''
        }).then(function () {
            if (!isFolder) {
                dispatch({data: item, type: DEL});
            } else {
                // 对于文件夹，也使用 DEL action 来避免数据污染
                dispatch({data: item, type: DEL});
            }
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    }, 150);
}

function fileDelete(item: TreeNodeData, dispatch: Function, isFolder?: boolean) {
    const {classify, projectName, types} = readUiFilters(_getState);
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    setTimeout(function () {
        formPost("/frame/deleteFile", {
            isFolder: String(!!isFolder),
            path: item.fullPath,
            classify: String(classify),
            projectName: projectName || '',
            types: types || ''
        }).then(function () {
            if (!isFolder) {
                dispatch({data: item, type: DEL});
            } else {
                // 对于文件夹，也使用 DEL action 来避免数据污染
                dispatch({data: item, type: DEL});
            }
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    }, 150);
}
