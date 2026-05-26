import { describe, it, expect, vi } from 'vitest';
import reducer from './reducer.js';
import * as ACTIONS from './action.js';

describe('Constant Module - Master Reducer', () => {
    const initialState = { master: { data: [] } };

    it('GIVEN an initial state WHEN LOAD_MASTER_COMPLETED action is dispatched THEN it should set master data', () => {
        const masterData = [
            { name: 'Constants1', label: 'Group 1', constants: [] },
            { name: 'Constants2', label: 'Group 2', constants: [] },
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
                    { name: 'Const1', label: 'L1', constants: [] },
                    { name: 'Const2', label: 'L2', constants: [] },
                    { name: 'Const3', label: 'L3', constants: [] },
                ],
            },
        };
        const action = { type: ACTIONS.DEL_MASTER, rowIndex: 1 };
        const newState = reducer(existingState, action);

        expect(newState.master.data).toHaveLength(2);
        expect(newState.master.data[0].name).toBe('Const1');
        expect(newState.master.data[1].name).toBe('Const3');
    });

    it('GIVEN an existing state WHEN ADD_MASTER action is dispatched THEN it should add a new empty constant category', () => {
        const existingState = {
            master: {
                data: [
                    { name: 'Const1', label: 'L1', constants: [] },
                ],
            },
        };
        const action = { type: ACTIONS.ADD_MASTER };
        const newState = reducer(existingState, action);

        expect(newState.master.data).toHaveLength(2);
        expect(newState.master.data[1]).toEqual({ name: '', type: 'Custom', constants: [] });
    });

    it('GIVEN a state WHEN SAVE action is dispatched THEN it should call saveData and return unchanged state', () => {
        const saveDataSpy = vi.spyOn(ACTIONS, 'saveData').mockImplementation(() => {});

        const state = {
            master: {
                data: [{ name: 'Const1', label: 'L1', constants: [] }],
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

describe('Constant Module - Slave Reducer', () => {
    it('GIVEN an initial state WHEN LOAD_SLAVE_COMPLETE action is dispatched THEN it should set slave data', () => {
        const initialState = { slave: {} };
        const masterRowData = {
            name: 'Const1',
            label: 'Group 1',
            constants: [
                { name: 'VAL_A', label: 'Value A', type: 'String' },
                { name: 'VAL_B', label: 'Value B', type: 'Integer' },
            ],
        };
        const action = { type: ACTIONS.LOAD_SLAVE_COMPLETE, masterRowData };
        const newState = reducer(initialState, action);

        expect(newState.slave.data).toEqual(masterRowData);
    });

    it('GIVEN a state with constants WHEN DEL_SLAVE action is dispatched THEN it should remove the constant at specified index', () => {
        const existingState = {
            slave: {
                data: {
                    name: 'Const1',
                    label: 'Group 1',
                    constants: [
                        { name: 'VAL_A', label: 'Value A', type: 'String' },
                        { name: 'VAL_B', label: 'Value B', type: 'Integer' },
                        { name: 'VAL_C', label: 'Value C', type: 'Boolean' },
                    ],
                },
            },
        };
        const action = { type: ACTIONS.DEL_SLAVE, rowIndex: 1 };
        const newState = reducer(existingState, action);

        expect(newState.slave.data.constants).toHaveLength(2);
        expect(newState.slave.data.constants[0].name).toBe('VAL_A');
        expect(newState.slave.data.constants[1].name).toBe('VAL_C');
    });

    it('GIVEN a state WHEN ADD_SLAVE action is dispatched THEN it should add a new empty constant', () => {
        const existingState = {
            slave: {
                data: {
                    name: 'Const1',
                    label: 'Group 1',
                    constants: [
                        { name: 'VAL_A', label: 'Value A', type: 'String' },
                    ],
                },
            },
        };
        const action = { type: ACTIONS.ADD_SLAVE };
        const newState = reducer(existingState, action);

        expect(newState.slave.data.constants).toHaveLength(2);
        expect(newState.slave.data.constants[1]).toEqual({ name: '', label: '', type: 'String' });
    });

    it('GIVEN an initial state WHEN unknown action is dispatched THEN it should return the same state', () => {
        const state = { slave: { data: {} } };
        const action = { type: 'UNKNOWN_ACTION' };
        const newState = reducer(state, action);

        expect(newState.slave).toBe(state.slave);
    });
});

describe('Constant Module - Combined Reducer', () => {
    it('GIVEN an initial state WHEN multiple actions are dispatched THEN it should maintain separate state slices', () => {
        const initialState = { master: {}, slave: {} };

        let state = reducer(initialState, {
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData: [{ name: 'Const1', label: 'L1', constants: [] }],
        });

        state = reducer(state, {
            type: ACTIONS.LOAD_SLAVE_COMPLETE,
            masterRowData: { name: 'Const1', label: 'L1', constants: [] },
        });

        expect(state.master.data).toEqual([
            { name: 'Const1', label: 'L1', constants: [] },
        ]);
        expect(state.slave.data).toEqual({ name: 'Const1', label: 'L1', constants: [] });
    });
});
