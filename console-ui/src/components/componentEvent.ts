import events from 'events';

export const SHOW_LOADING = 'show_loading';
export const HIDE_LOADING = 'hide_loading';
export const TREE_NODE_CLICK = 'tree_node_click';
export const TREE_DIR_NODE_CLICK = 'tree_dir_node_click';
export const OPEN_KNOWLEDGE_TREE_DIALOG = 'open_knowledge_tree_dialog';
export const HIDE_KNOWLEDGE_TREE_DIALOG = 'hide_knowledge_tree_dialog';
export const OPEN_VERSION_SELECT_DIALOG = 'open_version_select_dialog';
export const HIDE_VERSION_SELECT_DIALOG = 'hide_version_select_dialog';
export const OPEN_QUICK_TEST_DIALOG = 'open_quick_test_dialog';
export const HIDE_QUICK_TEST_DIALOG = 'hide_quick_test_dialog';
export const OPEN_RESOURCE_VERSION_DIALOG = 'open_resource_version_dialog';
export const CLOSE_RESOURCE_VERSION_DIALOG = 'close_resource_version_dialog';
export const OPEN_CONDITION_LIST_DIALOG = 'open_condition_list_dialog';
export const CLOSE_CONDITION_LIST_DIALOG = 'close_condition_list_dialog';
export const REFRESH_CONDITION_LIST_DIALOG = 'refresh_condition_list_dialog';
export const OPEN_RESOURCE_LIST_DIALOG = 'open_resource_list_dialog';
export const CLOSE_RESOURCE_LIST_DIALOG = 'close_resource_list_dialog';

export const eventEmitter = new events();
