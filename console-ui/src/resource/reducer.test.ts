import { describe, it, expect, vi } from 'vitest';
import reducer from './reducer.js';
import * as ACTIONS from './action.js';
import type {ResourceRootState} from './reducer.js';

// Cast reducer to accept partial state for testing convenience
const testReducer = reducer as (state: Partial<ResourceRootState> | undefined, action: any) => ResourceRootState;

describe('Resource Module - Master Reducer', () => {
    const initialState = { master: { data: [] } };

    it('GIVEN an initial state WHEN LOAD_MASTER_COMPLETED action is dispatched THEN it should set master data', () => {
        const masterData = [
            { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] },
            { name: 'Category2', type: 'type2', clazz: 'clazz2', variables: [] },
        ];
        const act = { type: ACTIONS.LOAD_MASTER_COMPLETED, masterData };
        const newState = testReducer(initialState, act);

        expect(newState.master.data).toEqual(masterData);
        expect(newState.master.data).toHaveLength(2);
    });

    it('GIVEN an existing state WHEN DEL_MASTER action is dispatched THEN it should remove the category at specified index', () => {
        const existingState = {
            master: {
                data: [
                    { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] },
                    { name: 'Category2', type: 'type2', clazz: 'clazz2', variables: [] },
                    { name: 'Category3', type: 'type3', clazz: 'clazz3', variables: [] },
                ],
            },
        };
        const act = { type: ACTIONS.DEL_MASTER, rowIndex: 1 };
        const newState = testReducer(existingState, act);

        expect(newState.master.data).toHaveLength(2);
        expect(newState.master.data![0].name).toBe('Category1');
        expect(newState.master.data![1].name).toBe('Category3');
    });

    it('GIVEN an existing state WHEN GENERATED_FIELDS action is dispatched THEN it should set variables for category at specified index', () => {
        const existingState = {
            master: {
                data: [
                    { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] },
                    { name: 'Category2', type: 'type2', clazz: 'clazz2', variables: [] },
                ],
            },
        };
        const newVariables = [
            { name: 'var1', label: 'Variable 1', type: 'String' },
            { name: 'var2', label: 'Variable 2', type: 'Integer' },
        ];
        const act = { type: ACTIONS.GENERATED_FIELDS, variables: newVariables, rowIndex: 0 };
        const newState = testReducer(existingState, act);

        expect(newState.master.data![0].variables).toEqual(newVariables);
        expect(newState.master.data![0].variables).toHaveLength(2);
        expect(newState.master.data![1].variables).toEqual([]);
    });

    it('GIVEN an existing state WHEN IMPORT_FIELDS action is dispatched THEN it should import variables to category at specified index', () => {
        const existingState = {
            master: {
                data: [
                    {
                        name: 'Category1',
                        type: 'type1',
                        clazz: '',
                        variables: [
                            { name: 'var1', label: 'Variable 1', type: 'String' }
                        ]
                    },
                ],
            },
        };
        const jsonResult = {
            variables: [
                { name: 'var2', label: 'Variable 2', type: 'Integer' },
                { name: 'var3', label: 'Variable 3', type: 'Boolean' },
            ],
            clazz: 'com.example.Clazz'
        };
        const act = { type: ACTIONS.IMPORT_FIELDS, jsonResult, rowIndex: 0 };
        const newState = testReducer(existingState, act);

        expect(newState.master.data![0].variables).toHaveLength(3);
        expect(newState.master.data![0].variables[0].name).toBe('var1');
        expect(newState.master.data![0].variables[1].name).toBe('var2');
        expect(newState.master.data![0].variables[2].name).toBe('var3');
        expect(newState.master.data![0].clazz).toBe('com.example.Clazz');
    });

    it('GIVEN a state WHEN SAVE action is dispatched THEN it should call saveData and return unchanged state', () => {
        const state = {
            master: {
                data: [{ name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] }],
            },
        };

        // Mock saveData to avoid errors
        const saveDataSpy = vi.spyOn(ACTIONS, 'saveData').mockImplementation(() => ({ type: ACTIONS.SAVE_COMPLETED }));

        const act = { type: ACTIONS.SAVE, newVersion: true, file: 'test.xml' };
        const newState = testReducer(state, act);

        expect(saveDataSpy).toHaveBeenCalledWith(state.master.data, true, 'test.xml');
        expect(newState.master).toBe(state.master);

        // Restore original
        saveDataSpy.mockRestore();
    });

    it('GIVEN a state WHEN unknown action is dispatched THEN it should return the same state', () => {
        const state = { master: { data: [] } };
        const act = { type: 'UNKNOWN_ACTION' };
        const newState = testReducer(state, act);

        expect(newState.master).toBe(state.master);
    });
});

describe('Resource Module - Slave Reducer', () => {
    it('GIVEN an initial state WHEN LOAD_SLAVE_COMPLETE action is dispatched THEN it should set slave data', () => {
        const initialState = { slave: {} };
        const masterRowData = {
            name: 'Category1',
            type: 'type1',
            clazz: 'clazz1',
            variables: [
                { name: 'var1', label: 'Variable 1', type: 'String' },
                { name: 'var2', label: 'Variable 2', type: 'Integer' },
            ],
        };
        const act = { type: ACTIONS.LOAD_SLAVE_COMPLETE, masterRowData };
        const newState = testReducer(initialState, act);

        expect(newState.slave.data).toEqual(masterRowData);
        expect(newState.slave.data!.variables).toHaveLength(2);
    });

    it('GIVEN a state with variables WHEN DEL_SLAVE action is dispatched THEN it should remove the variable at specified index', () => {
        const existingState = {
            slave: {
                data: {
                    name: 'Category1',
                    type: 'type1',
                    clazz: 'clazz1',
                    variables: [
                        { name: 'var1', label: 'Variable 1', type: 'String' },
                        { name: 'var2', label: 'Variable 2', type: 'Integer' },
                        { name: 'var3', label: 'Variable 3', type: 'Boolean' },
                    ],
                },
            },
        };
        const act = { type: ACTIONS.DEL_SLAVE, rowIndex: 1 };
        const newState = testReducer(existingState, act);

        expect(newState.slave.data!.variables).toHaveLength(2);
        expect(newState.slave.data!.variables[0].name).toBe('var1');
        expect(newState.slave.data!.variables[1].name).toBe('var3');
    });

    it('GIVEN a state WHEN ADD_SLAVE action is dispatched THEN it should call saveData and return unchanged state', () => {
        const state = {
            slave: {
                data: {
                    name: 'Category1',
                    type: 'type1',
                    clazz: 'clazz1',
                    variables: [{ name: 'var1', label: 'Variable 1', type: 'String' }],
                },
            },
        };

        // Mock saveData to avoid the forEach error
        const saveDataSpy = vi.spyOn(ACTIONS, 'saveData').mockImplementation(() => ({ type: ACTIONS.SAVE_COMPLETED }));

        const act = { type: ACTIONS.ADD_SLAVE, newVersion: false, file: 'test.xml' };
        const newState = testReducer(state, act);

        expect(saveDataSpy).toHaveBeenCalledWith(state.slave.data, false, 'test.xml');
        expect(newState.slave).toBe(state.slave);

        // Restore original
        saveDataSpy.mockRestore();
    });

    it('GIVEN a state WHEN unknown action is dispatched THEN it should return the same state', () => {
        const state = { slave: { data: {} as ACTIONS.ResourceCategory } };
        const act = { type: 'UNKNOWN_ACTION' };
        const newState = testReducer(state, act);

        expect(newState.slave).toBe(state.slave);
    });
});

describe('Resource Module - Combined Reducer', () => {
    it('GIVEN an initial state WHEN multiple actions are dispatched THEN it should maintain separate state slices', () => {
        const initialState = { master: {}, slave: {} };

        let state = testReducer(initialState, {
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData: [
                { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] }
            ],
        });

        state = testReducer(state, {
            type: ACTIONS.LOAD_SLAVE_COMPLETE,
            masterRowData: { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] },
        });

        expect(state.master.data).toEqual([
            { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] }
        ]);
        expect(state.slave.data).toEqual({ name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] });
    });
});
