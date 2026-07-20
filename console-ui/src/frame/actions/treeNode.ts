// V7.24:从 frame/action.ts 拆分 — 树节点构建(buildData)与右键上下文菜单、文件剪贴板
// (内部模块:buildData 等不经 frame/action.ts 对外导出)
import Styles from '../../Styles.js';
import {formPost, apiBase} from '../../api/client.js';
import * as event from '../event.js';
import * as componentEvent from '../../components/componentEvent.js';

import {alert, confirm} from '@/utils/modal';
import {LOAD_END, DEL} from './constants.js';
import {readUiFilters, _dispatch, _getState} from './shared.js';
import {lockFile, unlockFile, seeFileSource, seeFileVersions} from './fileOps.js';

// File clipboard state(剪切 / 复制)。原 window.___cutFileData / window.___copyFileData。
// 上下文菜单的 click 回调是模块级闭包(非 React 组件),所以用模块级 holder 而非 Context;
// 这避免了把临时剪贴板状态挂在 window 全局对象上。
let _cutFileData: TreeNodeData | null = null;
let _copyFileData: TreeNodeData | null = null;

export function buildData(data: TreeNodeData, level: number): void;
export function buildData(data: TreeNodeData, level: number, user: { import: boolean; export: boolean }): void;
export function buildData(data: TreeNodeData, level: number, user?: { import: boolean; export: boolean }): void {
    data._level = level++;
    // NOTE: 历史上各文件类型分支会设置 data.editorPath("/html/editor.html?type=<type>"),
    // 但 TreeItem/treeDataUtils 改走 openEditorTab 应用内标签后 editorPath 已无读取方,
    // 赋值已全部删除(死代码)。
    switch (data.type) {
        case "root":
            data._icon = Styles.frameStyle.getRootIcon();
            data._style = Styles.frameStyle.getRootIconStyle();
            data.editorPath = function () {
                // 历史遗留:root 节点的 editorPath 仅为占位,无实际操作
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
                                // 导出是文件下载(非编辑器),用隐藏 a 标签触发,避免 window.open 弹窗拦截
                                const a = document.createElement('a');
                                a.href = url;
                                a.download = '';
                                document.body.appendChild(a);
                                a.click();
                                a.remove();
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
        // V7.23:老 4 库(action/parameter/constant/variable)编辑器已删除,但后端
        //   (FrameController loadProjects / RuleForgeRepositoryServiceImpl)仍会给老项目
        //   下发这 4 类节点,故渲染 case 保留;文件点击走只读源码查看(TreeItem.tsx),
        //   "新建"菜单项已从 buildLibContextMenu / buildFullContextMenu 移除。
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
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'v1lib.json', nodeData: data});
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
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'v1rs.json', nodeData: data});
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
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'v1dt.json', nodeData: data});
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
                        event.eventEmitter.emit(event.OPEN_CREATE_FILE_DIALOG, {fileType: 'v1sc.json', nodeData: data});
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
        }
        // V7.23:"添加变量库/常量库/参数库/动作库"菜单项删除 —— 老 4 库编辑器已下线
        //   (后端加载端点 POST /xml V5.43 移除),新建入口不再提供;存量文件只读查看源码。
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
        // V7.23:"添加变量库/常量库/参数库/动作库"菜单项删除 —— 老 4 库编辑器已下线,
        //   此分支只保留粘贴项
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
