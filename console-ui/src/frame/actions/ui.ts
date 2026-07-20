// V7.24:从 frame/action.ts 拆分 — 纯 UI/树数据 action creators(非 thunk)
import {
    ADD,
    DEL,
    UPDATE,
    SET_ACTIVE_PANEL,
    SET_MONITORING_TAB,
    SET_SIMULATION_TAB,
    SET_GIT_STATUS_TAB,
    SET_PROJECT_NAME,
    SET_CLASSIFY,
    SET_TYPES,
    SET_SEARCH_FILE_NAME,
    SET_CURRENT_GIT_TAG,
} from './constants.js';

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

export function add(data: TreeNodeData) {
    return {data, type: ADD};
}

export function del(index: number) {
    return {index, type: DEL};
}

export function update(index: number, data: TreeNodeData) {
    return {index, data, type: UPDATE};
}
