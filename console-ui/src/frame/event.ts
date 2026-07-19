import {EventEmitter} from 'events';

export const OPEN_DIALOG = 'open_dialog';
export const CLOSE_DIALOG = 'close_dialog';
export const DIALOG_CONTNET_CHANGE = 'dialog_content_change';
export const OPEN_NEW_PROJECT_DIALOG = 'open_new_project_dialog';
export const CLOSE_NEW_PROJECT_DIALOG = 'close_new_project_dialog';
export const OPEN_UPDATE_PROJECT_DIALOG = 'open_update_project_dialog';
export const CLOSE_UPDATE_PROJECT_DIALOG = 'close_update_project_dialog';
export const OPEN_CREATE_FILE_DIALOG = 'create_file_dialog';
export const CLOSE_CREATE_FILE_DIALOG = 'close_file_dialog';
export const OPEN_CREATE_FOLDER_DIALOG = 'create_folder_dialog';
export const CLOSE_CREATE_FOLDER_DIALOG = 'close_folder_dialog';
export const EXPAND_TREE_NODE = 'expand_tree_node';
export const SHOW_RENAME_DIALOG = 'SHOW_RENAME_DIALOG';
export const HIDE_RENAME_DIALOG = 'HIDE_RENAME_DIALOG';

export const PROJECT_LIST_CHANGE = 'project_list_change';
export const PROJECT_FILTER_CHANGE = 'project_filter_change';
export const PROJECT_SELECT = 'project_select';

export const OPEN_IMPORT_PROJECT_DIALOG = 'open_import_project_dialog';
export const CLOSE_IMPORT_PROJECT_DIALOG = 'close_import_project_dialog';

export const OPEN_SOURCE_DIALOG = 'open_source_dialog';
export const CLOSE_SOURCE_DIALOG = 'close_source_dialog';

export const OPEN_FILE_VERSION_DIALOG = 'open_file_version_dialog';
export const CLOSE_FILE_VERSION_DIALOG = 'close_file_version_dialog';

export const CHANGE_CLASSIFY = 'change_classify';

/**
 * 打开应用内编辑器标签(EditorTabs 宿主监听)。替代原新开浏览器标签('/app/editor/...')方案。
 */
export const OPEN_EDITOR_TAB = 'open_editor_tab';

/** {@link OPEN_EDITOR_TAB} 事件载荷。editorType 取值见 frame/editorTypeMap 的 EditorType。 */
export interface OpenEditorTabPayload {
    editorType: string;
    /** 文件完整路径(历史版本带 ':版本号' 后缀,原样透传给编辑器组件);permission 等全局单例可缺省 */
    file?: string;
    /** 标签显示名,缺省由宿主按 file 末段推导 */
    label?: string;
}

export const eventEmitter = new EventEmitter();

/**
 * 打开(或激活)主框架内容区的编辑器标签。文件树 / 对话框 / 顶栏等任意位置均可调用,
 * 无需在 React 树内 —— 统一走本事件总线,由 EditorTabs 宿主完成标签管理(同 key 重复打开只激活)。
 *
 * @returns 是否有宿主(EditorTabs)在监听;无宿主(如免登录 demo 路由 /v1-flow)返回 false,
 *          调用方可据此降级(提示用户),而不是静默 no-op
 */
export function openEditorTab(payload: OpenEditorTabPayload): boolean {
    return eventEmitter.emit(OPEN_EDITOR_TAB, payload);
}
