import { createEditorStore } from '../store/createEditorStore';

export function createTestStore(rootReducer: any, initialState = {}) {
    return createEditorStore(rootReducer, initialState);
}
