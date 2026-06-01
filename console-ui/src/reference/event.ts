import events from 'events';

export const OPEN_REFERENCE_DIALOG = 'open_reference_dialog';
export const CLOSE_REFERENCE_DIALOG = 'close_reference_dialog';
export const PROJECT_LIST_CHANGE = 'project_list_change';
export const OPEN_QUICK_TEST_DIALOG = 'open_quick_test_dialog';

export const eventEmitter = new events();
