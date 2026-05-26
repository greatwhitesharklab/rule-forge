import { describe, it, expect, vi } from 'vitest';
import reducer from './reducer.js';
import * as ACTIONS from './action.js';

describe('Variable Module - Master Reducer', () => {
    const initialState = { master: { data: [] } };

    it('GIVEN an initial state WHEN LOAD_MASTER_COMPLETED action is dispatched THEN it should set master data', () => {
        const masterData = [
            { name: 'Category1', type: 'Custom', clazz: 'com.example.Cat1', variables: [] },
            { name: 'Category2', type: 'Custom', clazz: 'com.example.Cat2', variables: [] },
        ];
        const action = { type: ACTIONS.LOAD_MASTER_COMPLETED, masterData };
        const newState = reducer(initialState, action);

        expect(newState.master.data).toEqual(masterData);
        expect(newState.master.data).toHaveLength(2);
    });

    it('GIVEN an existing state WHEN DEL_MASTER action is dispatched THEN it should remove the item at specified index', () => {
        const existingState = {
            master: {
                data: [
                    { name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] },
                    { name: 'Cat2', type: 'Custom', clazz: 'c2', variables: [] },
                    { name: 'Cat3', type: 'Custom', clazz: 'c3', variables: [] },
                ],
            },
        };
        const action = { type: ACTIONS.DEL_MASTER, rowIndex: 1 };
        const newState = reducer(existingState, action);

        expect(newState.master.data).toHaveLength(2);
        expect(newState.master.data[0].name).toBe('Cat1');
        expect(newState.master.data[1].name).toBe('Cat3');
    });

    it('GIVEN an existing state WHEN ADD_MASTER action is dispatched with a new name THEN it should add a new category', () => {
        const existingState = {
            master: {
                data: [
                    { name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] },
                ],
            },
        };
        const action = { type: ACTIONS.ADD_MASTER, masterName: 'Cat2' };
        const newState = reducer(existingState, action);

        expect(newState.master.data).toHaveLength(2);
        expect(newState.master.data[1]).toEqual({ name: 'Cat2', clazz: '', type: 'Custom', variables: [] });
    });

    it('GIVEN an existing state WHEN ADD_MASTER action is dispatched with duplicate name THEN it should alert and return unchanged state', () => {
        const bootboxAlert = vi.fn();
        window.bootbox = { alert: bootboxAlert };

        const existingState = {
            master: {
                data: [
                    { name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] },
                ],
            },
        };
        const action = { type: ACTIONS.ADD_MASTER, masterName: 'Cat1' };
        const newState = reducer(existingState, action);

        expect(newState.master.data).toHaveLength(1);
        expect(bootboxAlert).toHaveBeenCalledWith('[Cat1]已经存在，添加失败');

        delete window.bootbox;
    });

    it('GIVEN an existing state WHEN GENERATED_FIELDS action is dispatched THEN it should set variables for the category at specified index', () => {
        const existingState = {
            master: {
                data: [
                    { name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] },
                    { name: 'Cat2', type: 'Custom', clazz: 'c2', variables: [] },
                ],
            },
        };
        const newVariables = [
            { name: 'var1', label: 'Variable 1', type: 'String' },
            { name: 'var2', label: 'Variable 2', type: 'Integer' },
        ];
        const action = { type: ACTIONS.GENERATED_FIELDS, variables: newVariables, rowIndex: 0 };
        const newState = reducer(existingState, action);

        expect(newState.master.data[0].variables).toEqual(newVariables);
        expect(newState.master.data[1].variables).toEqual([]);
    });

    it('GIVEN an existing state WHEN IMPORT_FIELDS action is dispatched THEN it should import variables and set clazz', () => {
        const existingState = {
            master: {
                data: [
                    {
                        name: 'Cat1',
                        type: 'Custom',
                        clazz: '',
                        variables: [{ name: 'var1', label: 'V1', type: 'String' }],
                    },
                ],
            },
        };
        const jsonResult = {
            variables: [
                { name: 'var2', label: 'V2', type: 'Integer' },
                { name: 'var3', label: 'V3', type: 'Boolean' },
            ],
            clazz: 'com.example.NewClazz',
        };
        const action = { type: ACTIONS.IMPORT_FIELDS, jsonResult, rowIndex: 0 };
        const newState = reducer(existingState, action);

        expect(newState.master.data[0].variables).toHaveLength(3);
        expect(newState.master.data[0].variables[0].name).toBe('var1');
        expect(newState.master.data[0].variables[1].name).toBe('var2');
        expect(newState.master.data[0].variables[2].name).toBe('var3');
        expect(newState.master.data[0].clazz).toBe('com.example.NewClazz');
    });

    it('GIVEN an existing state WHEN IMPORT_FIELDS action without clazz is dispatched THEN it should only add variables', () => {
        const existingState = {
            master: {
                data: [
                    {
                        name: 'Cat1',
                        type: 'Custom',
                        clazz: 'original.Clazz',
                        variables: [{ name: 'var1', label: 'V1', type: 'String' }],
                    },
                ],
            },
        };
        const jsonResult = {
            variables: [{ name: 'var2', label: 'V2', type: 'Integer' }],
        };
        const action = { type: ACTIONS.IMPORT_FIELDS, jsonResult, rowIndex: 0 };
        const newState = reducer(existingState, action);

        expect(newState.master.data[0].variables).toHaveLength(2);
        expect(newState.master.data[0].clazz).toBe('original.Clazz');
    });

    it('GIVEN a state WHEN SAVE action is dispatched THEN it should call saveData and return unchanged state', () => {
        const saveDataSpy = vi.spyOn(ACTIONS, 'saveData').mockImplementation(() => ({ type: ACTIONS.SAVE_COMPLETED }));

        const state = {
            master: {
                data: [{ name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] }],
            },
        };
        const action = { type: ACTIONS.SAVE, newVersion: true, file: 'test.xml' };
        const newState = reducer(state, action);

        expect(saveDataSpy).toHaveBeenCalledWith(state.master.data, true, 'test.xml');
        expect(newState.master).toBe(state.master);

        saveDataSpy.mockRestore();
    });

    it('GIVEN an initial state WHEN unknown action is dispatched THEN it should return the same state', () => {
        const state = { master: { data: [] } };
        const action = { type: 'UNKNOWN_ACTION' };
        const newState = reducer(state, action);

        expect(newState.master).toBe(state.master);
    });
});

describe('Variable Module - Slave Reducer', () => {
    it('GIVEN an initial state WHEN LOAD_SLAVE_COMPLETE action is dispatched THEN it should set slave data', () => {
        const initialState = { slave: {} };
        const masterRowData = {
            name: 'Cat1',
            type: 'Custom',
            clazz: 'c1',
            variables: [
                { name: 'var1', label: 'V1', type: 'String' },
                { name: 'var2', label: 'V2', type: 'Integer' },
            ],
        };
        const action = { type: ACTIONS.LOAD_SLAVE_COMPLETE, masterRowData };
        const newState = reducer(initialState, action);

        expect(newState.slave.data).toEqual(masterRowData);
    });

    it('GIVEN a state with variables WHEN DEL_SLAVE action is dispatched THEN it should remove the variable at specified index', () => {
        const existingState = {
            slave: {
                data: {
                    name: 'Cat1',
                    type: 'Custom',
                    clazz: 'c1',
                    variables: [
                        { name: 'var1', label: 'V1', type: 'String' },
                        { name: 'var2', label: 'V2', type: 'Integer' },
                        { name: 'var3', label: 'V3', type: 'Boolean' },
                    ],
                },
            },
        };
        const action = { type: ACTIONS.DEL_SLAVE, rowIndex: 1 };
        const newState = reducer(existingState, action);

        expect(newState.slave.data.variables).toHaveLength(2);
        expect(newState.slave.data.variables[0].name).toBe('var1');
        expect(newState.slave.data.variables[1].name).toBe('var3');
    });

    it('GIVEN a state WHEN ADD_SLAVE action is dispatched THEN it should add a new empty variable', () => {
        const existingState = {
            slave: {
                data: {
                    name: 'Cat1',
                    type: 'Custom',
                    clazz: 'c1',
                    variables: [
                        { name: 'var1', label: 'V1', type: 'String' },
                    ],
                },
            },
        };
        const action = { type: ACTIONS.ADD_SLAVE };
        const newState = reducer(existingState, action);

        expect(newState.slave.data.variables).toHaveLength(2);
        expect(newState.slave.data.variables[1]).toEqual({ name: '', label: '', type: 'String' });
    });

    it('GIVEN an initial state WHEN unknown action is dispatched THEN it should return the same state', () => {
        const state = { slave: { data: {} } };
        const action = { type: 'UNKNOWN_ACTION' };
        const newState = reducer(state, action);

        expect(newState.slave).toBe(state.slave);
    });
});

describe('Variable Module - Combined Reducer', () => {
    it('GIVEN an initial state WHEN multiple actions are dispatched THEN it should maintain separate state slices', () => {
        const initialState = { master: {}, slave: {} };

        let state = reducer(initialState, {
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData: [{ name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] }],
        });

        state = reducer(state, {
            type: ACTIONS.LOAD_SLAVE_COMPLETE,
            masterRowData: { name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] },
        });

        expect(state.master.data).toEqual([
            { name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] },
        ]);
        expect(state.slave.data).toEqual({ name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] });
    });
});
