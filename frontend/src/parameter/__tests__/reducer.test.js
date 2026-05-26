import { describe, it, expect, vi } from 'vitest';
import reducer from '../reducer.js';
import * as ACTIONS from '../action.js';

describe('Parameter Module - Reducer', () => {
    it('GIVEN an initial state WHEN LOAD_DATA_COMPLETED action is dispatched THEN it should set parameter data', () => {
        const initialState = {};
        const data = [
            { name: 'param1', label: 'Parameter 1', type: 'String' },
            { name: 'param2', label: 'Parameter 2', type: 'Integer' },
        ];
        const action = { type: ACTIONS.LOAD_DATA_COMPLETED, data };
        const newState = reducer(initialState, action);

        expect(newState.data).toEqual(data);
        expect(newState.data).toHaveLength(2);
    });

    it('GIVEN an existing state WHEN ADD action is dispatched THEN it should add a new empty parameter', () => {
        const existingState = {
            data: [{ name: 'param1', label: 'Parameter 1', type: 'String' }],
        };
        const action = { type: ACTIONS.ADD };
        const newState = reducer(existingState, action);

        expect(newState.data).toHaveLength(2);
        expect(newState.data[1]).toEqual({ name: '', label: '', type: 'String' });
    });

    it('GIVEN an existing state WHEN DEL action is dispatched THEN it should remove the parameter at specified index', () => {
        const existingState = {
            data: [
                { name: 'param1', label: 'Parameter 1', type: 'String' },
                { name: 'param2', label: 'Parameter 2', type: 'Integer' },
                { name: 'param3', label: 'Parameter 3', type: 'Boolean' },
            ],
        };
        const action = { type: ACTIONS.DEL, rowIndex: 1 };
        const newState = reducer(existingState, action);

        expect(newState.data).toHaveLength(2);
        expect(newState.data[0].name).toBe('param1');
        expect(newState.data[1].name).toBe('param3');
    });

    it('GIVEN an existing state WHEN SAVE action is dispatched THEN it should call saveData and return unchanged state', () => {
        const state = {
            data: [{ name: 'param1', label: 'Parameter 1', type: 'String' }],
        };
        const saveDataSpy = vi.spyOn(ACTIONS, 'saveData');
        const action = { type: ACTIONS.SAVE, newVersion: true, file: 'test.xml' };
        const newState = reducer(state, action);

        expect(saveDataSpy).toHaveBeenCalledWith(state.data, true, 'test.xml');
        expect(newState).toBe(state);
    });

    it('GIVEN a state WHEN unknown action is dispatched THEN it should return the same state', () => {
        const state = { data: [] };
        const action = { type: 'UNKNOWN_ACTION' };
        const newState = reducer(state, action);

        expect(newState).toBe(state);
    });
});
