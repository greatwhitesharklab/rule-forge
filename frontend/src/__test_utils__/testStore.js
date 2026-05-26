import { createStore, applyMiddleware } from 'redux';
import thunk from 'redux-thunk';

export function createTestStore(rootReducer, initialState = {}) {
    return createStore(rootReducer, initialState, applyMiddleware(thunk));
}
