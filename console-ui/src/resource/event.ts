import events from 'events';

export const MASTER_ROW_CHANGE = 'row_change';
export const OPEN_CREATE_PARAMS_DIALOG = 'open_create_params_dialog';
export const HIDE_CREATE_PARAMS_DIALOG = 'hide_create_params_dialog';
export const SHOW_LOADING = 'show_loading';

export const eventEmitter = new events();
