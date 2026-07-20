// V7.24:从 frame/action.ts 拆分 — 文件/项目操作 thunk(创建/重命名/锁定/源码/版本)
import {formPost} from '../../api/client.js';
import * as event from '../event.js';
import * as componentEvent from '../../components/componentEvent.js';
import {alert} from '@/utils/modal';
import {LOAD_END, FILE_RENAME} from './constants.js';
import {readUiFilters} from './shared.js';
import {setProjectName} from './ui.js';
import {loadData} from './loadTree.js';
import {buildData} from './treeNode.js';

const FILE_TYPE_MAP: Record<string, string> = {
    // V7.23:vl.xml/cl.xml/pl.xml/al.xml 项删除 —— 老 4 库"新建"入口随编辑器下线移除
    'rs.xml': 'Ruleset', 'rsl.xml': 'RulesetLib', 'ul': 'UL',
    'dt.xml': 'DecisionTable', 'ct.xml': 'Crosstab', 'dts.xml': 'ScriptDecisionTable',
    'dtree.xml': 'DecisionTree', 'sc': 'Scorecard', 'scc': 'ComplexScorecard',
    'drl': 'Drl',
    // V6.20.0 P3:DMN / PMML(只读/导入,FileTypeUtils 后端识别 .dmn/.pmml)
    'dmn': 'Dmn', 'pmml': 'Pmml',
    // V7.0.0→V7.5.1:V1 决策流(.v1flow.json 统一后缀;.json 兼容旧)
    'v1flow.json': 'V1Flow',
    'json': 'V1Flow',
    // V7.4/V7.5:V1 库/规则独立文件(后缀权威见后端 FileTypeUtils.EXTENSION_MAP:
    //   .v1lib.json/.v1rs.json/.v1dt.json/.v1sc.json;枚举名即服务端 type)
    'v1lib.json': 'V1Library',
    'v1rs.json': 'V1RuleSet',
    'v1dt.json': 'V1DecisionTable',
    'v1sc.json': 'V1ScoreCard',
};

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
 * 拉文件源码并弹只读源码对话框(OPEN_SOURCE_DIALOG)。
 * seeFileSource thunk 与无 store 场景(VersionListDialog / ReferenceDialog 里
 * 无编辑器的老类型降级只读查看)共用本通道。
 */
export function openFileSourceDialog(fullPath: string, gitTag?: string | null) {
    const params: Record<string, string> = {path: fullPath};
    if (gitTag) {
        params.gitTag = gitTag;
    }
    formPost("/frame/fileSource", params).then(function (result: { content: string }) {
        event.eventEmitter.emit(event.OPEN_SOURCE_DIALOG, fullPath, result.content);
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
        openFileSourceDialog(data.fullPath, currentGitTag);
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
