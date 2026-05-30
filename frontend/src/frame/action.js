import Styles from '../Styles.js';
import {handleResponseError} from '../Utils.js';
import * as event from './event.js';
import * as componentEvent from '../components/componentEvent.js';

export const ADD = 'add';
export const DEL = 'del';
export const UPDATE = 'upload';
export const LOAD_END = 'load_end';
export const FILE_RENAME = 'file_rename';
export const CREATE_NEW_PROJECT = 'create_new_project';
export const CREATE_NEW_FILE = 'create_new_file';
export const LOAD_CHILDREN_END = 'load_children_end'; // 子菜单加载完成的action类型
export const SET_ACTIVE_PANEL = 'set_active_panel';
export const SET_MONITORING_TAB = 'set_monitoring_tab';

export function setActivePanel(panel) {
    return {type: SET_ACTIVE_PANEL, panel};
}

export function setMonitoringTab(tab) {
    return {type: SET_MONITORING_TAB, tab};
}

const FILE_TYPE_MAP = {
    'vl.xml': 'VariableLibrary', 'cl.xml': 'ConstantLibrary',
    'pl.xml': 'ParameterLibrary', 'al.xml': 'ActionLibrary',
    'rs.xml': 'Ruleset', 'rsl.xml': 'RulesetLib', 'ul': 'UL',
    'dt.xml': 'DecisionTable', 'ct.xml': 'Crosstab', 'dts.xml': 'ScriptDecisionTable',
    'dtree.xml': 'DecisionTree', 'sc': 'Scorecard', 'scc': 'ComplexScorecard',
    'rl.xml': 'RuleFlow'
};

export function createNewFile(newFileName, fileType, parentNodeData) {
    return function (dispatch) {
        const url = window._server + '/frame/createFile';
        const fileName = newFileName + "." + fileType;
        const path = parentNodeData.fullPath + "/" + fileName;
        const serverType = FILE_TYPE_MAP[fileType] || fileType;

        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({path: encodeURI(parentNodeData.fullPath + "/" + fileName), type: serverType}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (newFileInfo) {
            event.eventEmitter.emit(event.CLOSE_CREATE_FILE_DIALOG);
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            // Reload tree to show the new file, expanding the parent folder
            dispatch(loadData(true, window._projectName, null, null, [parentNodeData.fullPath]));
        }).catch(function (response) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            handleResponseError(response, '服务端错误：');
        });
    }
}

export function rename(path, newPath) {
    return function (dispatch) {
        const url = window._server + '/frame/fileRename';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                path: path,
                newPath: newPath,
                classify: window._classify,
                projectName: window._projectName,
                types: window._types
            }).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            const rootFile = data.repo.rootFile;
            buildData(rootFile, 1);
            dispatch({data: rootFile, type: LOAD_END});
            event.eventEmitter.emit(event.HIDE_RENAME_DIALOG);
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function (response) {
            handleResponseError(response, '服务端错误：');
        });
    }
}

export function createNewProject(newProjectName, parentNodeData) {
    return function (dispatch) {
        const url = window._server + '/frame/createProject';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({newProjectName: newProjectName}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function () {
            window._projectName = newProjectName;
            dispatch(loadData(true, newProjectName));
            event.eventEmitter.emit(event.PROJECT_SELECT, newProjectName);
            event.eventEmitter.emit(event.PROJECT_FILTER_CHANGE, newProjectName);
            event.eventEmitter.emit(event.CLOSE_NEW_PROJECT_DIALOG);
        }).catch(function (response) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            handleResponseError(response, '服务端错误：');
        });
    };
}

export function createNewFolder(newFolderName, parentNodeData) {
    const fullFolderName = parentNodeData.fullPath + '/' + newFolderName;
    return function (dispatch) {
        const url = window._server + '/frame/createFolder';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                fullFolderName: fullFolderName,
                classify: window._classify,
                projectName: window._projectName,
                types: window._types,
            }).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            event.eventEmitter.emit(event.CLOSE_CREATE_FOLDER_DIALOG);
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            dispatch(loadData(true, window._projectName, null));
        }).catch(function (response) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            handleResponseError(response, '服务端错误：');
        });
    };
}

export function fileRename(itemData, newName) {
    return function (dispatch) {
        var fullPath = itemData.fullPath;
        var namePos = fullPath.lastIndexOf(itemData.name);
        var basePath = fullPath.substring(0, namePos);
        var newFullPath = basePath + newName;
        var url = window._server + "/frame/fileRename";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                path: fullPath,
                newPath: newFullPath,
                classify: window._classify,
                projectName: window._projectName,
                types: window._types
            }).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
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
        }).catch(function (response) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            handleResponseError(response, '服务端错误：');
        });
    }
}

function moveFile(path, newPath, dispatch) {
    var url = window._server + "/frame/fileRename";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({path, newPath, classify: window._classify, projectName: window._projectName, types: window._types}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        const rootFile = data.repo.rootFile;
        buildData(rootFile, 1);
        dispatch({data: rootFile, type: LOAD_END});
        event.eventEmitter.emit(event.CLOSE_UPDATE_PROJECT_DIALOG);
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
    }).catch(function (response) {
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        handleResponseError(response, '服务端错误：');
    });
}

export function add(data) {
    return {data, type: ADD};
}

export function del(index) {
    return {index, type: DEL};
}

export function update(index, data) {
    return {index, data, type: UPDATE};
}

let _loadDataRequestId = 0;

let _pathsToExpand = [];

export function loadData(classify, projectName, types, searchFileName, pathsToExpand) {
    if (classify === null || classify === undefined) {
        classify = true;
    }
    if (pathsToExpand) {
        _pathsToExpand = pathsToExpand;
    }
    const requestId = ++_loadDataRequestId;
    return function (dispatch) {
        const url = window._server + '/frame/loadProjects';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams(Object.fromEntries(Object.entries({classify, projectName, types, searchFileName, projectDetail: true}).filter(([_, v]) => v !== undefined && v !== null))).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
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
            let treeData;
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
                }
            }
            if (!treeData) {
                treeData = rootFile;
            }

            buildData(treeData, 1, user);
            buildData(publicResource, 1, user);

            // Mark nodes that should be force-expanded
            if (_pathsToExpand.length > 0) {
                function markForceExpand(node) {
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
                    var liChildren = parentLi.querySelectorAll('ul > li > ul > li');
                    liChildren.forEach(function(child) { child.style.display = 'none'; });
                    parentLi.querySelectorAll('ul > li').forEach(function(item) {
                        const firstI = item.querySelector('i:first-child');
                        if (firstI) { firstI.classList.add('rf-plus'); firstI.classList.remove('rf-minus'); }
                    });
                } else {
                    var liChildren = parentLi.querySelectorAll('li');
                    liChildren.forEach(function(child) { child.style.display = ''; });
                    parentLi.querySelectorAll('ul > li').forEach(function(item) {
                        const firstI = item.querySelector('i:first-child');
                        if (firstI) { firstI.classList.add('rf-minus'); firstI.classList.remove('rf-plus'); }
                    });
                }
            }
        }).catch(function (response) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            handleResponseError(response, '加载数据失败,');
        });
    }
}



// 加载子菜单的函数
export function loadChildren(parentNodeData, classify, projectName, types) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    return function (dispatch) {
        const url = window._server + '/frame/loadProjects';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                classify,
                projectName,
                types,
                parentPath: parentNodeData.fullPath,
                loadChildren: true
            }).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            const {repo, user} = data;

            // 从 repo 中提取子菜单数据
            let childrenData = null;

            // 根据父节点的路径来确定从哪里提取子菜单数据
            if (parentNodeData.fullPath === '/') {
                // 如果是根节点，从 rootFile.children 获取
                childrenData = repo.rootFile ? repo.rootFile.children : [];
            } else if (parentNodeData.type === 'publicResource') {
                // 如果是公共资源，从 publicResource.children 获取
                childrenData = repo.publicResource ? repo.publicResource.children : [];
            } else {
                // 如果是项目节点，需要从 rootFile.children 中找到对应的项目
                if (repo.rootFile && repo.rootFile.children) {
                    const projectNode = repo.rootFile.children.find(child =>
                        child.name === parentNodeData.name ||
                        child.fullPath === parentNodeData.fullPath
                    );
                    childrenData = projectNode ? projectNode.children : [];
                }
            }

            if (childrenData && childrenData.length > 0) {
                // 为每个子节点构建数据
                childrenData.forEach(child => {
                    buildData(child, parentNodeData._level + 1, user);
                });

                dispatch({
                    parentNodeData,
                    childrenData,
                    type: LOAD_CHILDREN_END
                });
            }
        }).catch(function (response) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            handleResponseError(response, '加载子菜单失败,');
        });
    }
}

export function buildType(fileType) {
    let pos = fileType.indexOf(':');
    if (pos > -1) {
        fileType = fileType.substring(0, pos);
    }
    let type;
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
        case"scc":
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

function buildData() {
    const data = arguments[0] || {};
    let level = arguments[1] || '';

    data._level = level++;
    switch (data.type) {
        case "root":
            data._icon = Styles.frameStyle.getRootIcon();
            data._style = Styles.frameStyle.getRootIconStyle();
            data.editorPath = function () {
                console.log(data)
            };
            data.contextMenu = [
                {
                    name: '创建新项目',
                    icon: 'rf rf-createpro',
                    click: function (data) {
                        event.eventEmitter.emit(event.OPEN_NEW_PROJECT_DIALOG, data)
                    }
                }
            ];
            if (arguments.length === 3 && arguments[2].import) {
                data.contextMenu.push({
                        name: '导入项目',
                        icon: 'rf rf-import',
                        click: function (e) {
                            event.eventEmitter.emit(event.OPEN_IMPORT_PROJECT_DIALOG);
                        }
                    }
                )
            }
            break;
        case "rule":
            data._icon = Styles.frameStyle.getRuleIcon();
            data._style = Styles.frameStyle.getRuleIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/ruleset-editor.html";
            break;
        case "project":
            data._icon = Styles.frameStyle.getProjectIcon();
            data._style = Styles.frameStyle.getProjectIconStyle();
            data.contextMenu = [
                {
                    name: '删除项目',
                    icon: 'rf rf-remove',
                    click: function (data, dispatch) {
                        bootbox.confirm("此操作将删除" + data.name + "项目及其下所有文件，你确定要这样做吗？", function (result) {
                            if (!result) {
                                return;
                            }
                            projectDelete(data, dispatch);
                        });
                    }
                }
            ];
            if (arguments.length === 3 && arguments[2].export) {
                data.contextMenu.push({
                        name: '导出项目备份',
                        icon: 'rf rf-export',
                        click: function (data, dispatch) {
                            bootbox.confirm("真的要导出项目" + data.name + "的备份文件吗？", function (result) {
                                if (!result) {
                                    return;
                                }
                                const url = window._server + '/frame/exportProjectBackupFile?path=' + encodeURI(encodeURI(data.fullPath));
                                window.open(url, '_blank');
                            });
                        }
                    },
                )
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
                    click: function (data, dispatch) {
                        seeFileSource(data);
                    }
                },
                {
                    name: '查看版本信息',
                    icon: 'rf rf-version',
                    click: function (data, dispatch) {
                        data['rpp'] = data['fullPath'].split('/')[1];
                        data['page'] = 1;
                        seeFileVersions(data);
                    }
                }
            ];
            data.editorPath = "/html/package-editor.html";
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
            data.editorPath = "/html/action-editor.html";
            break;
        case "parameter":
            data._icon = Styles.frameStyle.getParameterIcon();
            data._style = Styles.frameStyle.getParameterIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/parameter-editor.html";
            break;
        case "constant":
            data._icon = Styles.frameStyle.getConstantIcon();
            data._style = Styles.frameStyle.getConstantIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/constant-editor.html";
            break;
        case "variable":
            data._icon = Styles.frameStyle.getVariableIcon();
            data._style = Styles.frameStyle.getVariableIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/variable-editor.html";
            break;
        case "ruleLib":
            data._icon = Styles.frameStyle.getRuleLibIcon();
            data._style = Styles.frameStyle.getRuleLibIconStyle();
            data.contextMenu = [
                {
                    name: '添加目录',
                    icon: Styles.frameStyle.getFolderIcon(),
                    click: function (data, dispatch) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data})
                    }
                },
                {
                    name: '添加向导式决策集',
                    icon: Styles.frameStyle.getRuleIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rs.xml', nodeData: data})
                    }
                },
                {
                    name: '添加脚本式决策集',
                    icon: Styles.frameStyle.getUlIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'ul', nodeData: data})
                    }
                },
                {
                    name: '添加向导式决策库',
                    icon: Styles.frameStyle.getRuleIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rsl.xml', nodeData: data})
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
                    click: function (data, dispatch) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data})
                    }
                },
                {
                    name: '添加决策表',
                    icon: Styles.frameStyle.getDecisionTableIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'dt.xml', nodeData: data})
                    }
                },
                {
                    name: '添加交叉决策表',
                    icon: Styles.frameStyle.getCrossDecisionTableIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'ct.xml', nodeData: data})
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
                    click: function (data, dispatch) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data})
                    }
                },
                {
                    name: '添加决策树',
                    icon: Styles.frameStyle.getDecisionTreeIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'dtree.xml', nodeData: data})
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
                    click: function (data, dispatch) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data})
                    }
                },
                {
                    name: '添加决策流',
                    icon: Styles.frameStyle.getFlowIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rl.xml', nodeData: data})
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
                    click: function (data, dispatch) {
                        event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data})
                    }
                },
                {
                    name: '添加评分卡',
                    icon: Styles.frameStyle.getScorecardIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'sc', nodeData: data})
                    }
                },
                {
                    name: "添加复杂评分卡",
                    icon: Styles.frameStyle.getComplexScorecardIcon(),
                    click: function () {
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: "scc", nodeData: data})
                    }
                }
            ];
            break;
        case "ul":
            data._icon = Styles.frameStyle.getUlIcon();
            data._style = Styles.frameStyle.getUlIconStyle();
            let menus = buildFileContextMenu();
            menus.splice(0, 1);
            data.contextMenu = menus;
            data.editorPath = "/html/ul-editor.html";
            break;
        case "decisionTable":
            data._icon = Styles.frameStyle.getDecisionTableIcon();
            data._style = Styles.frameStyle.getDecisionTableIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/decision-table-editor.html";
            break;
        case "scriptDecisionTable":
            data._icon = Styles.frameStyle.getScriptDecisionTableIcon();
            data._style = Styles.frameStyle.getScriptDecisionTableIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/script-decision-table-editor.html";
            break;
        case "decisionTree":
            data._icon = Styles.frameStyle.getDecisionTreeIcon();
            data._style = Styles.frameStyle.getDecisionTreeIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/decision-tree-editor.html";
            break;
        case "flow":
            data._icon = Styles.frameStyle.getFlowIcon();
            data._style = Styles.frameStyle.getFlowIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/flow-bpmn-editor.html";
            break;
        case "scorecard":
            data._icon = Styles.frameStyle.getScorecardIcon();
            data._style = Styles.frameStyle.getScorecardIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/score-card-editor.html";
            break;
        case"complexscorecard":
            data._icon = Styles.frameStyle.getComplexScorecardIcon();
            data._style = Styles.frameStyle.getComplexScorecardIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/complexscorecard-editor.html";
            break;
        case"crosstab":
            data._icon = Styles.frameStyle.getCrossDecisionTableIcon();
            data._style = Styles.frameStyle.getCrossDecisionTableIconStyle();
            data.contextMenu = buildFileContextMenu();
            data.editorPath = "/html/crosstab-editor.html";
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
        children.forEach((child, index) => {
            if (arguments.length === 3) {
                buildData(child, level, arguments[2]);
            } else {
                buildData(child, level);
            }
        });
    }
}

function buildLibContextMenu() {
    return [
        {
            name: '添加目录',
            icon: Styles.frameStyle.getFolderIcon(),
            click: function (data, dispatch) {
                event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data})
            }
        },
        {
            name: '添加变量库',
            icon: Styles.frameStyle.getVariableIcon(),
            click: function (data, dispatch) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'vl.xml', nodeData: data})
            }
        },
        {
            name: '添加常量库',
            icon: Styles.frameStyle.getConstantIcon(),
            click: function (data, dispatch) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'cl.xml', nodeData: data})
            }
        },
        {
            name: '添加参数库',
            icon: Styles.frameStyle.getParameterIcon(),
            click: function (data, dispatch) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'pl.xml', nodeData: data})
            }
        },
        {
            name: '添加动作库',
            icon: Styles.frameStyle.getActionIcon(),
            click: function (data, dispatch) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'al.xml', nodeData: data})
            }
        }
    ];
}

function buildFullContextMenu(isFolder, folderType) {
    const menus = [{
        name: isFolder ? '添加子目录' : '添加目录',
        icon: Styles.frameStyle.getFolderIcon(),
        click: function (data, dispatch) {
            event.eventEmitter.emit(event.OPEN_CREATE_FOLDER_DIALOG, {nodeData: data})
        }
    }];
    let addPasteMenuItem = false;
    if (!folderType || folderType === 'all' || folderType === 'lib') {
        menus.push(
            {
                name: '添加变量库',
                icon: Styles.frameStyle.getVariableIcon(),
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'vl.xml', nodeData: data})
                }
            },
            {
                name: '添加常量库',
                icon: Styles.frameStyle.getConstantIcon(),
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'cl.xml', nodeData: data})
                }
            },
            {
                name: '添加参数库',
                icon: Styles.frameStyle.getParameterIcon(),
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'pl.xml', nodeData: data})
                }
            },
            {
                name: '添加动作库',
                icon: Styles.frameStyle.getActionIcon(),
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'al.xml', nodeData: data})
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
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rs.xml', nodeData: data})
                }
            },
            {
                name: '添加向导式决策库',
                icon: Styles.frameStyle.getRuleIcon(),
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rsl.xml', nodeData: data})
                }
            },
            {
                name: '添加脚本式决策集',
                icon: Styles.frameStyle.getUlIcon(),
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'ul', nodeData: data})
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
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'dt.xml', nodeData: data})
                }
            },
            {
                name: '添加交叉决策表',
                icon: Styles.frameStyle.getCrossDecisionTableIcon(),
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'ct.xml', nodeData: data})
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
            click: function (data, dispatch) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'dtree.xml', nodeData: data})
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
            click: function (data, dispatch) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'sc', nodeData: data})
            }
        });
    }
    if (!folderType || folderType === 'all' || folderType === 'flowLib') {
        menus.push({
            name: '添加决策流',
            icon: Styles.frameStyle.getFlowIcon(),
            click: function (data, dispatch) {
                event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'rl.xml', nodeData: data})
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
                click: function (data, dispatch) {
                    bootbox.confirm("删除目录[" + data.name + "],将会同时删除其下所有子目录及文件，确认吗？", function (result) {
                        if (!result) {
                            return;
                        }
                        fileDelete(data, dispatch, true);
                    });
                }
            },
            {
                name: '修改目录名',
                icon: 'rf rf-rename',
                click: function (data, dispatch) {
                    event.eventEmitter.emit(event.SHOW_RENAME_DIALOG, data);
                }
            },
            {
                name: '锁定目录',
                icon: 'rf rf-lock',
                click: function (data, dispatch) {
                    lockFile(data.fullPath, dispatch);
                }
            },
            {
                name: '解锁目录',
                icon: 'rf rf-unlock',
                click: function (data, dispatch) {
                    unlockFile(data.fullPath, dispatch);
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

function buildPasteMenuItem() {
    return {
        name: '粘贴文件',
        icon: 'rf rf-paste',
        click: function (data, dispatch) {
            let sourceFileData = window.___cutFileData, copy = false;
            if (!sourceFileData) {
                sourceFileData = window.___copyFileData;
                copy = true;
            }
            if (!sourceFileData) {
                window.bootbox.alert("没有文件可供粘贴！");
                return;
            }
            const newDir = data.fullPath;
            const newFullPath = newDir + "/" + sourceFileData.name, oldFullPath = sourceFileData.fullPath;
            if (oldFullPath === newFullPath) {
                window.bootbox.alert("目录未改变，不能进行此操作！");
                return;
            }
            let info = "真的要移动文件【" + sourceFileData.name + "】到【" + newDir + "】目录吗？";
            if (copy) {
                info = "真的要复制文件【" + sourceFileData.name + "】到【" + newDir + "】目录吗？";
            }
            bootbox.confirm(info, function (result) {
                if (!result) {
                    return;
                }
                window.___cutFileData = null;
                window.___copyFileData = null;
                if (!copy) {
                    moveFile(oldFullPath, newFullPath, dispatch);
                } else {
                    fetch(window._server + '/frame/copyFile', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                        body: new URLSearchParams({newFullPath, oldFullPath}).toString()
                    }).then(function(response) {
                        if (!response.ok) throw response;
                        return response.json();
                    }).then(function (data) {
                        const rootFile = data.repo.rootFile;
                        buildData(rootFile, 1);
                        dispatch({data: rootFile, type: LOAD_END});
                    }).catch(function (response) {
                        handleResponseError(response, '复制文件操作失败,');
                    });
                }
            });
        }
    };
}

function buildFileContextMenu() {
    return [
        {
            name: '查看源码',
            icon: 'rf rf-code',
            click: function (data, dispatch) {
                seeFileSource(data);
            }
        },
        {
            name: '查看版本信息',
            icon: 'rf rf-version',
            click: function (data, dispatch) {
                data['page'] = 1;
                seeFileVersions(data);
            }
        },
        {
            name: '删除文件',
            icon: 'rf rf-remove',
            click: function (data, dispatch) {
                bootbox.confirm("真的要删除[" + data.name + "]文件吗？", function (result) {
                    if (!result) {
                        return;
                    }
                    fileDelete(data, dispatch);
                });
            }
        },
        {
            name: '修改文件名',
            icon: 'rf rf-rename',
            click: function (data, dispatch) {
                event.eventEmitter.emit(event.SHOW_RENAME_DIALOG, data);
            }
        },
        {
            name: '复制文件',
            icon: 'rf rf-copy',
            click: function (data, dispatch) {
                window.___copyFileData = data;
                window.___cutFileData = null;
            }
        },
        {
            name: '剪切文件',
            icon: 'rf rf-cut',
            click: function (data, dispatch) {
                window.___cutFileData = data;
                window.___copyFileData = null;
            }
        },
        {
            name: '锁定文件',
            icon: 'rf rf-lock',
            click: function (data, dispatch) {
                lockFile(data.fullPath, dispatch);
            }
        },
        {
            name: '解锁文件',
            icon: 'rf rf-unlock',
            click: function (data, dispatch) {
                unlockFile(data.fullPath, dispatch);
            }
        }
    ];
}

export function lockFile(file, dispatch) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    var url = window._server + "/frame/lockFile";
    fetch(url, {
        method: "POST",
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({file}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        const rootFile = data.repo.rootFile;
        buildData(rootFile, 1);
        dispatch({data: rootFile, type: LOAD_END});
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        window.bootbox.alert('锁定成功!');
    }).catch(function (response) {
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        handleResponseError(response, '服务端错误：');
    });
}

export function unlockFile(file, dispatch) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    var url = window._server + "/frame/unlockFile";
    fetch(url, {
        method: "POST",
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({file}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        const rootFile = data.repo.rootFile;
        buildData(rootFile, 1);
        dispatch({data: rootFile, type: LOAD_END});
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        window.bootbox.alert('解锁成功!');
    }).catch(function (response) {
        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        handleResponseError(response, '服务端错误：');
    });
}

export function saveFileSource(file, content) {
    content = encodeURIComponent(content);
    var url = window._server + "/common/saveFile";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({file, content}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function () {
        window.bootbox.alert('保存成功!');
    }).catch(function (response) {
        handleResponseError(response, '服务端错误：');
    });
}

export function seeFileSource(data) {
    var url = window._server + "/frame/fileSource";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({path: data.fullPath}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (result) {
        event.eventEmitter.emit(event.OPEN_SOURCE_DIALOG, data.fullPath, result.content);
    }).catch(function (response) {
        handleResponseError(response, '服务端错误：');
    });
}

export function seeFileVersions(data) {
    var url = window._server + "/frame/fileVersions";
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({path: data.fullPath, project: data['rpp'], page: data.page}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (res) {
        const files = res.files
        const num = res.count
        event.eventEmitter.emit(event.OPEN_FILE_VERSION_DIALOG, {files, data, num});
    }).catch(function (response) {
        handleResponseError(response, '服务端错误：');
    });
}

function projectDelete(item, dispatch, isFolder) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    setTimeout(function () {
        var url = window._server + "/frame/deleteProject";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                isFolder,
                path: item.fullPath,
                classify: window._classify,
                projectName: window._projectName,
                types: window._types
            }).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            if (!isFolder) {
                dispatch({data: item, type: DEL});
            } else {
                // 对于文件夹，也使用 DEL action 来避免数据污染
                dispatch({data: item, type: DEL});
            }
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function (response) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            handleResponseError(response, '服务端错误：');
        });
    }, 150);
}

function fileDelete(item, dispatch, isFolder) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    setTimeout(function () {
        var url = window._server + "/frame/deleteFile";
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                isFolder,
                path: item.fullPath,
                classify: window._classify,
                projectName: window._projectName,
                types: window._types
            }).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (data) {
            if (!isFolder) {
                dispatch({data: item, type: DEL});
            } else {
                // 对于文件夹，也使用 DEL action 来避免数据污染
                dispatch({data: item, type: DEL});
            }
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        }).catch(function (response) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            handleResponseError(response, '服务端错误：');
        });
    }, 150);
}
