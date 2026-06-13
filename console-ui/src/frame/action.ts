import Styles from '../Styles.js';
import {formPost} from '../api/client.js';
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

// ---- Action creators ----

export function setActivePanel(panel: string) {
    return {type: SET_ACTIVE_PANEL, panel};
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

export function createNewFile(newFileName: string, fileType: string, parentNodeData: TreeNodeData) {
    return function (dispatch: Function) {
        const fileName = newFileName + "." + fileType;
        const serverType = FILE_TYPE_MAP[fileType] || fileType;

        formPost('/frame/createFile', {
            path: encodeURI(parentNodeData.fullPath + "/" + fileName),
            type: serverType
        }).then(function () {
            event.eventEmitter.emit(event.CLOSE_CREATE_FILE_DIALOG);
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            dispatch(loadData(true, window._projectName, null, null, [parentNodeData.fullPath]));
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}

export function rename(path: string, newPath: string) {
    return function (dispatch: Function) {
        formPost('/frame/fileRename', {
            path: path,
            newPath: newPath,
            classify: String(window._classify),
            projectName: window._projectName || '',
            types: window._types || ''
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
            window._projectName = newProjectName;
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
    return function (dispatch: Function) {
        formPost('/frame/createFolder', {
            fullFolderName: fullFolderName,
            classify: String(window._classify),
            projectName: window._projectName || '',
            types: window._types || '',
        }).then(function () {
            event.eventEmitter.emit(event.CLOSE_CREATE_FOLDER_DIALOG);
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            dispatch(loadData(true, window._projectName, null));
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}

export function fileRename(itemData: TreeNodeData, newName: string) {
    return function (dispatch: Function) {
        var fullPath = itemData.fullPath;
        var namePos = fullPath.lastIndexOf(itemData.name);
        var basePath = fullPath.substring(0, namePos);
        var newFullPath = basePath + newName;
        formPost('/frame/fileRename', {
            path: fullPath,
            newPath: newFullPath,
            classify: String(window._classify),
            projectName: window._projectName || '',
            types: window._types || ''
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
    formPost('/frame/fileRename', {path, newPath, classify: String(window._classify), projectName: window._projectName || '', types: window._types || ''}).then(function (data: { repo: { rootFile: TreeNodeData } }) {
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
    return function (dispatch: Function) {
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



// 加载子菜单的函数
export function loadChildren(parentNodeData: TreeNodeData, classify: boolean, projectName: string | null, types: string | null) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    return function (dispatch: Function) {
        formPost('/frame/loadProjects', {
            classify: String(classify),
            projectName: projectName || '',
            types: types || '',
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
    'rl.xml': 'RuleFlow',
    'drl': 'Drl',
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
        case 'rs.xml':
            type = '向导式决策集';
            break;
        case 'rsl.xml':
            type = '向导式决策库';
            break;
        case 'ul':
            type = '脚本式决策集';
            break;
        case 'dt.xml':
            type = '决策表';
            break;
        case 'dts.xml':
            type = '脚本式决策表';
            break;
        case 'rl.xml':
            type = '决策流';
            break;
        case 'dtree.xml':
            type = '决策树';
            break;
        case "sc":
            type = "评分卡";
            break;
        case "drl":
            type = "DRL 规则";
            break;
        case "scc":
            type = "复杂评分卡";
            break;
        case "ct.xml":
            type = "交叉决策表";
            break;
        case "rp":
            type = 'package';
            break;
    }
    if (!type) {
        const info = "Unknow file type :" + fileType;
        alert(info);
        throw info;
    }
    return type;
}

// Global dispatch used by context-menu callbacks (set during buildData call chain)
let _dispatch: Function = () => {};
// Track whether the current buildData invocation has a user argument
let _hasUser: boolean = false;

function buildData(data: TreeNodeData, level: number): void;
function buildData(data: TreeNodeData, level: number, user: { import: boolean; export: boolean }): void;
function buildData(data: TreeNodeData, level: number, user?: { import: boolean; export: boolean }): void {
    data._level = level++;
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
            data.editorPath = "/html/editor.html?type=ruleset";
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
                                const url = window._server + '/frame/exportProjectBackupFile?path=' + encodeURI(encodeURI(data.fullPath));
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
        case "resourcePackage":
            data._icon = Styles.frameStyle.getResourcePackageIcon();
            data._style = Styles.frameStyle.getResourcePackageIconStyle();
            data.contextMenu = [
                {
                    name: '查看源码',
                    icon: 'rf rf-code',
                    click: function (data: TreeNodeData) {
                        seeFileSource(data);
                    }
                },
                {
                    name: '查看版本信息',
                    icon: 'rf rf-version',
                    click: function (data: TreeNodeData) {
                        data['rpp'] = data['fullPath'].split('/')[1];
                        data['page'] = 1;
                        seeFileVersions(data);
                    }
                }
            ];
            data.editorPath = "/html/editor.html?type=package";
            break;
        case "lib":
            data._icon = Styles.frameStyle.getLibIcon();
            data._style = Styles.frameStyle.getLibIconStyle();
            data.contextMenu = buildLibContextMenu();
            break;
        case "action":
            data._icon = Styles.frameStyle.getActionIcon();
            data._style = Styles.frameStyle.getActionIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=action";
            break;
        case "parameter":
            data._icon = Styles.frameStyle.getParameterIcon();
            data._style = Styles.frameStyle.getParameterIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=parameter";
            break;
        case "constant":
            data._icon = Styles.frameStyle.getConstantIcon();
            data._style = Styles.frameStyle.getConstantIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=constant";
            break;
        case "variable":
            data._icon = Styles.frameStyle.getVariableIcon();
            data._style = Styles.frameStyle.getVariableIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=variable";
            break;
        case "ruleLib":
            data._icon = Styles.frameStyle.getRuleLibIcon();
            data._style = Styles.frameStyle.getRuleLibIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加向导式决策集',
                    icon: Styles.frameStyle.getRuleIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rs.xml', nodeData: data});
                    }
                },
                {
                    name: '添加脚本式决策集',
                    icon: Styles.frameStyle.getUlIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'ul', nodeData: data});
                    }
                },
                {
                    name: '添加向导式决策库',
                    icon: Styles.frameStyle.getRuleIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rsl.xml', nodeData: data});
                    }
                }
            ];
            break;
        case "decisionTableLib":
            data._icon = Styles.frameStyle.getDecisionTableLibIcon();
            data._style = Styles.frameStyle.getDecisionTableLibIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加决策表',
                    icon: Styles.frameStyle.getDecisionTableIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'dt.xml', nodeData: data});
                    }
                },
                {
                    name: '添加交叉决策表',
                    icon: Styles.frameStyle.getCrossDecisionTableIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'ct.xml', nodeData: data});
                    }
                }
            ];
            break;
        case "decisionTreeLib":
            data._icon = Styles.frameStyle.getDecisionTreeLibIcon();
            data._style = Styles.frameStyle.getDecisionTreeLibIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加决策树',
                    icon: Styles.frameStyle.getDecisionTreeIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'dtree.xml', nodeData: data});
                    }
                }
            ];
            break;
        case "flowLib":
            data._icon = Styles.frameStyle.getFlowLibIcon();
            data._style = Styles.frameStyle.getFlowLibIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加决策流',
                    icon: Styles.frameStyle.getFlowIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rl.xml', nodeData: data});
                    }
                }
            ];
            break;
        case "scorecardLib":
            data._icon = Styles.frameStyle.getScorecardLibIcon();
            data._style = Styles.frameStyle.getScorecardLibIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data: TreeNodeData) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data});
                    }
                },
                {
                    name: '添加评分卡',
                    icon: Styles.frameStyle.getScorecardIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'sc', nodeData: data});
                    }
                },
                {
                    name: "添加复杂评分卡",
                    icon: Styles.frameStyle.getComplexScorecardIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: "scc", nodeData: data});
                    }
                }
            ];
            break;
        case "ul":
            data._icon = Styles.frameStyle.getUlIcon();
            data._style = Styles.frameStyle.getUlIconStyle();
            let menus: ContextMenuItem[] = buildFileContextMenu();
            menus.splice(0, 1);
            data.contextMenu = menus;
            data.editorPath = "/html/editor.html?type=ul";
            break;
        case "decisionTable":
            data._icon = Styles.frameStyle.getDecisionTableIcon();
            data._style = Styles.frameStyle.getDecisionTableIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=decisiontable";
            break;
        case "scriptDecisionTable":
            data._icon = Styles.frameStyle.getScriptDecisionTableIcon();
            data._style = Styles.frameStyle.getScriptDecisionTableIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=scriptdecisiontable";
            break;
        case "decisionTree":
            data._icon = Styles.frameStyle.getDecisionTreeIcon();
            data._style = Styles.frameStyle.getDecisionTreeIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=decisiontree";
            break;
        case "flow":
            data._icon = Styles.frameStyle.getFlowIcon();
            data._style = Styles.frameStyle.getFlowIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=flowbpmn";
            break;
        case "scorecard":
            data._icon = Styles.frameStyle.getScorecardIcon();
            data._style = Styles.frameStyle.getScorecardIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=scorecard";
            break;
        case "complexscorecard":
            data._icon = Styles.frameStyle.getComplexScorecardIcon();
            data._style = Styles.frameStyle.getComplexScorecardIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=complexscorecard";
            break;
        case "crosstab":
            data._icon = Styles.frameStyle.getCrossDecisionTableIcon();
            data._style = Styles.frameStyle.getCrossDecisionTableIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/editor.html?type=crosstab";
            break;
    }
    // Ensure container types have a children array so they render as folders
    if (data.children === null || data.children === undefined) {
        var t = data.type;
        if (t === 'lib' || t === 'ruleLib' || t === 'decisionTableLib' || t === 'decisionTreeLib'
            || t === 'scorecardLib' || t === 'flowLib' || t === 'resource' || t === 'folder') {
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
    if (!folderType || folderType === 'all' || folderType === 'flowLib') {
        menus.push({
            name: '添加决策流',
            icon: Styles.frameStyle.getFlowIcon(),
            click: function (data: TreeNodeData) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rl.xml', nodeData: data});
            }
        });
        if (!addPasteMenuItem) {
            menus.push(buildPasteMenuItem());
            addPasteMenuItem = true;
        }
    }
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
            let sourceFileData = window.___cutFileData, copy = false;
            if (!sourceFileData) {
                sourceFileData = window.___copyFileData;
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
                window.___cutFileData = null;
                window.___copyFileData = null;
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
            click: function (data: TreeNodeData) {
                seeFileSource(data);
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
                window.___copyFileData = data;
                window.___cutFileData = null;
            }
        },
        {
            name: '剪切文件',
            icon: 'rf rf-cut',
            click: function (data: TreeNodeData) {
                window.___cutFileData = data;
                window.___copyFileData = null;
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

export function seeFileSource(data: TreeNodeData) {
    const params: Record<string, string> = {path: data.fullPath};
    // Include gitTag for version-aware reading
    if (window._currentGitTag) {
        params.gitTag = window._currentGitTag;
    }
    formPost("/frame/fileSource", params).then(function (result: { content: string }) {
        event.eventEmitter.emit(event.OPEN_SOURCE_DIALOG, data.fullPath, result.content);
    }).catch(function () {
    });
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
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    setTimeout(function () {
        formPost("/frame/deleteProject", {
            isFolder: String(!!isFolder),
            path: item.fullPath,
            classify: String(window._classify),
            projectName: window._projectName || '',
            types: window._types || ''
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
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    setTimeout(function () {
        formPost("/frame/deleteFile", {
            isFolder: String(!!isFolder),
            path: item.fullPath,
            classify: String(window._classify),
            projectName: window._projectName || '',
            types: window._types || ''
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
