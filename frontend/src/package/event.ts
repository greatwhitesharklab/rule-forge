import EventEmitter from 'events';

export const OPEN_CREATE_PACKAGE_DIALOG = 'open_create_package_dialog';
export const HIDE_CREATE_PACKAGE_DIALOG = 'hide_create_package_dialog';
export const OPEN_CREATE_PACKAGE_ITEM_DIALOG = 'open_create_package_item_dialog';
export const HIDE_CREATE_PACKAGE_ITEM_DIALOG = 'hide_create_package_item_dialog';

export const OPEN_SIMULATOR_DIALOG = 'open_simulator_dialog';
export const HIDE_SIMULATOR_DIALOG = 'hide_simulator_dialog';

export const OPEN_RETE_DIAGRAM_DIALOG = 'open_rete_diagram_dialog';
export const HIDE_RETE_DIAGRAM_DIALOG = 'hide_rete_diagram_dialog';

export const OPEN_FLOW_DIALOG = 'open_flow_dialog';
export const HIDE_FLOW_DIALOG = 'hide_flow_dialog';

export const OPEN_EXPORT_EXCEL_DIALOG = 'open_export_excel_dialog';
export const HIDE_EXPORT_EXCEL_DIALOG = 'hide_export_excel_dialog';

export const OPEN_IMPORT_EXCEL_DIALOG = 'open_import_excel_dialog';
export const HIDE_IMPORT_EXCEL_DIALOG = 'hide_import_excel_dialog';

export const OPEN_BATCH_TEST_DIALOG = 'open_batch_test_dialog';
export const HIDE_BATCH_TEST_DIALOG = 'hide_batch_test_dialog';

export const REFRESH_SIMULATOR_DATA = 'refresh_simulator_data';

export const OPEN_VERSION_DIALOG = 'open_versions_Dialog';
export const HIDE_VERSION_DIALOG = 'hide_versions_Dialog';

export const OPEN_IMPORT_EXCEL_ERROR_DIALOG = 'open_import_excel_error_dialog';
export const HIDE_IMPORT_EXCEL_ERROR_DIALOG = 'hide_import_excel_error_dialog';

export const SHOW_LOADING = 'show_loading';
export const HIDE_LOADING = 'hide_loading';

export const eventEmitter = new EventEmitter();
