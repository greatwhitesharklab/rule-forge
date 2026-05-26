import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import reducer from '../reducer.js';
import * as ACTIONS from '../action.js';
import { setupMockBootbox, teardownMockBootbox } from '../../__test_utils__/mockBootbox.js';

describe('Action Module - Combined Reducer', () => {
    beforeEach(() => {
        setupMockBootbox();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    describe('Master Reducer', () => {
        it('GIVEN an initial state WHEN LOAD_MASTER_COMPLETED action is dispatched THEN it should set master data from springBeans', () => {
            const initialState = {
                master: {},
                slave: {},
                method: {},
                methodList: {},
            };
            const masterData = {
                springBeans: [
                    { id: 'bean1', name: 'Bean 1', methods: [] },
                    { id: 'bean2', name: 'Bean 2', methods: [] },
                ],
            };
            const action = { type: ACTIONS.LOAD_MASTER_COMPLETED, masterData };
            const newState = reducer(initialState, action);

            expect(newState.master.data).toEqual(masterData.springBeans);
            expect(newState.master.data).toHaveLength(2);
        });

        it('GIVEN an existing state WHEN ADD_MASTER action is dispatched THEN it should add a new empty bean', () => {
            const existingState = {
                master: {
                    data: [{ id: 'bean1', name: 'Bean 1', methods: [] }],
                },
                slave: {},
                method: {},
                methodList: {},
            };
            const action = { type: ACTIONS.ADD_MASTER };
            const newState = reducer(existingState, action);

            expect(newState.master.data).toHaveLength(2);
            expect(newState.master.data[1]).toEqual({ id: '', name: '', methods: [] });
        });

        it('GIVEN an empty state WHEN ADD_MASTER action is dispatched THEN it should initialize data array and add new bean', () => {
            const initialState = {
                master: {},
                slave: {},
                method: {},
                methodList: {},
            };
            const action = { type: ACTIONS.ADD_MASTER };
            const newState = reducer(initialState, action);

            expect(newState.master.data).toHaveLength(1);
            expect(newState.master.data[0]).toEqual({ id: '', name: '', methods: [] });
        });

        it('GIVEN an existing state WHEN DEL_MASTER action is dispatched THEN it should remove the bean at specified index', () => {
            const existingState = {
                master: {
                    data: [
                        { id: 'bean1', name: 'Bean 1', methods: [] },
                        { id: 'bean2', name: 'Bean 2', methods: [] },
                        { id: 'bean3', name: 'Bean 3', methods: [] },
                    ],
                },
                slave: {},
                method: {},
                methodList: {},
            };
            const action = { type: ACTIONS.DEL_MASTER, rowIndex: 1 };
            const newState = reducer(existingState, action);

            expect(newState.master.data).toHaveLength(2);
            expect(newState.master.data[0].id).toBe('bean1');
            expect(newState.master.data[1].id).toBe('bean3');
        });

        it('GIVEN an existing state WHEN SAVE action is dispatched THEN it should call saveData and return unchanged state', () => {
            const state = {
                master: {
                    data: [{ id: 'bean1', name: 'Bean 1', methods: [] }],
                },
                slave: {},
                method: {},
                methodList: {},
            };
            const saveDataSpy = vi.spyOn(ACTIONS, 'saveData');
            const action = { type: ACTIONS.SAVE, newVersion: true, file: 'test.xml' };
            const newState = reducer(state, action);

            expect(saveDataSpy).toHaveBeenCalledWith(state.master.data, true, 'test.xml');
            expect(newState.master).toEqual(state.master);
            saveDataSpy.mockRestore();
        });

        it('GIVEN a state WHEN unknown action is dispatched THEN it should return the same state', () => {
            const state = {
                master: {},
                slave: {},
                method: {},
                methodList: {},
            };
            const action = { type: 'UNKNOWN_ACTION' };
            const newState = reducer(state, action);

            expect(newState).toEqual(state);
        });
    });

    describe('Slave Reducer', () => {
        it('GIVEN an initial state WHEN LOAD_SLAVE_COMPLETE action is dispatched THEN it should set slave data and assign unique IDs to methods', () => {
            const initialState = {
                master: {},
                slave: {},
                method: {},
                methodList: {},
            };
            const masterRowData = {
                id: 'bean1',
                name: 'Bean 1',
                methods: [
                    { name: 'method1', methodName: 'method1', parameters: [] },
                    { name: 'method2', methodName: 'method2', parameters: [] },
                ],
            };
            const action = { type: ACTIONS.LOAD_SLAVE_COMPLETE, masterRowData };
            const newState = reducer(initialState, action);

            expect(newState.slave.data).toEqual(masterRowData);
            expect(newState.slave.data.methods[0].id).toBeDefined();
            expect(newState.slave.data.methods[1].id).toBeDefined();
            expect(newState.slave.data.methods[0].id).not.toBe(newState.slave.data.methods[1].id);
        });

        it('GIVEN a state with methods WHEN ADD_SLAVE action is dispatched THEN it should add a new empty method', () => {
            const existingState = {
                master: {},
                slave: {
                    data: {
                        id: 'bean1',
                        name: 'Bean 1',
                        methods: [{ name: 'method1', methodName: 'method1', parameters: [] }],
                    },
                },
                method: {},
                methodList: {},
            };
            const action = { type: ACTIONS.ADD_SLAVE };
            const newState = reducer(existingState, action);

            expect(newState.slave.data.methods).toHaveLength(2);
            expect(newState.slave.data.methods[1]).toEqual({ name: '', methodName: '', parameters: [] });
        });

        it('GIVEN a state with methods WHEN ADD_SLAVE action is dispatched with custom data THEN it should add the custom method', () => {
            const existingState = {
                master: {},
                slave: {
                    data: {
                        id: 'bean1',
                        name: 'Bean 1',
                        methods: [],
                    },
                },
                method: {},
                methodList: {},
            };
            const customMethod = { name: 'customMethod', methodName: 'customMethod', parameters: [] };
            const action = { type: ACTIONS.ADD_SLAVE, newSlaveData: customMethod };
            const newState = reducer(existingState, action);

            expect(newState.slave.data.methods).toHaveLength(1);
            expect(newState.slave.data.methods[0]).toEqual(customMethod);
        });

        it('GIVEN a state without methods WHEN ADD_SLAVE action is dispatched THEN it should show alert and return unchanged state', () => {
            const existingState = {
                master: {},
                slave: { data: {} },
                method: {},
                methodList: {},
            };
            const action = { type: ACTIONS.ADD_SLAVE };
            const newState = reducer(existingState, action);

            expect(window.bootbox.alert).toHaveBeenCalledWith('请先指定方法所属的Bean');
            expect(newState.slave).toEqual(existingState.slave);
        });

        it('GIVEN a state with methods WHEN DEL_SLAVE action is dispatched THEN it should remove the method at specified index', () => {
            const existingState = {
                master: {},
                slave: {
                    data: {
                        id: 'bean1',
                        name: 'Bean 1',
                        methods: [
                            { name: 'method1', methodName: 'method1', parameters: [] },
                            { name: 'method2', methodName: 'method2', parameters: [] },
                            { name: 'method3', methodName: 'method3', parameters: [] },
                        ],
                    },
                },
                method: {},
                methodList: {},
            };
            const action = { type: ACTIONS.DEL_SLAVE, rowIndex: 1 };
            const newState = reducer(existingState, action);

            expect(newState.slave.data.methods).toHaveLength(2);
            expect(newState.slave.data.methods[0].name).toBe('method1');
            expect(newState.slave.data.methods[1].name).toBe('method3');
        });
    });

    describe('Method Reducer', () => {
        it('GIVEN an initial state WHEN LOAD_METHOD_COMPLETED action is dispatched THEN it should set method data', () => {
            const initialState = {
                master: {},
                slave: {},
                method: {},
                methodList: {},
            };
            const slaveData = {
                name: 'method1',
                methodName: 'method1',
                parameters: [
                    { name: 'param1', type: 'String' },
                    { name: 'param2', type: 'Integer' },
                ],
            };
            const action = { type: ACTIONS.LOAD_METHOD_COMPLETED, slaveData };
            const newState = reducer(initialState, action);

            expect(newState.method.data).toEqual(slaveData);
            expect(newState.method.data.parameters).toHaveLength(2);
        });

        it('GIVEN a state WHEN LOAD_SLAVE_COMPLETE action is dispatched THEN it should reset to empty object', () => {
            const existingState = {
                master: {},
                slave: {},
                method: {
                    data: {
                        name: 'method1',
                        methodName: 'method1',
                        parameters: [{ name: 'param1', type: 'String' }],
                    },
                },
                methodList: {},
            };
            const action = { type: ACTIONS.LOAD_SLAVE_COMPLETE };
            const newState = reducer(existingState, action);

            expect(newState.method.data).toEqual({});
        });

        it('GIVEN a state with parameters WHEN ADD_PARAMETER action is dispatched THEN it should add a new parameter', () => {
            const existingState = {
                master: {},
                slave: {},
                method: {
                    data: {
                        name: 'method1',
                        methodName: 'method1',
                        parameters: [{ name: 'param1', type: 'String' }],
                    },
                },
                methodList: {},
            };
            const action = { type: ACTIONS.ADD_PARAMETER };
            const newState = reducer(existingState, action);

            expect(newState.method.data.parameters).toHaveLength(2);
            expect(newState.method.data.parameters[1]).toEqual({ name: '', type: 'String' });
        });

        it('GIVEN a state without parameters WHEN ADD_PARAMETER action is dispatched THEN it should show alert and return unchanged state', () => {
            const existingState = {
                master: {},
                slave: {},
                method: { data: {} },
                methodList: {},
            };
            const action = { type: ACTIONS.ADD_PARAMETER };
            const newState = reducer(existingState, action);

            expect(window.bootbox.alert).toHaveBeenCalledWith('请先指定参数所属的方法');
            expect(newState.method).toEqual(existingState.method);
        });

        it('GIVEN a state with parameters WHEN DEL_PARAMETER action is dispatched THEN it should remove the parameter at specified index', () => {
            const existingState = {
                master: {},
                slave: {},
                method: {
                    data: {
                        name: 'method1',
                        methodName: 'method1',
                        parameters: [
                            { name: 'param1', type: 'String' },
                            { name: 'param2', type: 'Integer' },
                            { name: 'param3', type: 'Boolean' },
                        ],
                    },
                },
                methodList: {},
            };
            const action = { type: ACTIONS.DEL_PARAMETER, rowIndex: 1 };
            const newState = reducer(existingState, action);

            expect(newState.method.data.parameters).toHaveLength(2);
            expect(newState.method.data.parameters[0].name).toBe('param1');
            expect(newState.method.data.parameters[1].name).toBe('param3');
        });
    });

    describe('MethodList Reducer', () => {
        it('GIVEN an initial state WHEN LOADED_BEAN_METHODS action is dispatched THEN it should set method list data', () => {
            const initialState = {
                master: {},
                slave: {},
                method: {},
                methodList: {},
            };
            const result = [
                { name: 'method1', methodName: 'method1' },
                { name: 'method2', methodName: 'method2' },
            ];
            const action = { type: ACTIONS.LOADED_BEAN_METHODS, result };
            const newState = reducer(initialState, action);

            expect(newState.methodList.data).toEqual(result);
            expect(newState.methodList.data).toHaveLength(2);
        });
    });

    describe('Combined Reducer Integration', () => {
        it('GIVEN an initial state WHEN multiple actions are dispatched THEN it should maintain separate state slices', () => {
            const initialState = {
                master: {},
                slave: {},
                method: {},
                methodList: {},
            };
            let state = reducer(initialState, {
                type: ACTIONS.LOAD_MASTER_COMPLETED,
                masterData: { springBeans: [{ id: 'bean1', name: 'Bean 1', methods: [] }] },
            });

            state = reducer(state, {
                type: ACTIONS.LOAD_SLAVE_COMPLETE,
                masterRowData: { id: 'bean1', name: 'Bean 1', methods: [] },
            });

            expect(state.master.data).toEqual([{ id: 'bean1', name: 'Bean 1', methods: [] }]);
            expect(state.slave.data).toEqual({ id: 'bean1', name: 'Bean 1', methods: [] });
            expect(state.method).toEqual({ data: {} });
            expect(state.methodList).toEqual({});
        });
    });
});
