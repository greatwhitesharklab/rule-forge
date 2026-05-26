import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as ACTIONS from '../action.js';
import { setupMockServer, teardownMockServer } from '../../__test_utils__/mockServer.js';
import { setupMockBootbox, teardownMockBootbox } from '../../__test_utils__/mockBootbox.js';

// Mock ajaxSave from Utils to intercept the module import
vi.mock('../../Utils.js', () => ({
    ajaxSave: vi.fn(),
    handleResponseError: vi.fn(),
}));

// Helper to flush microtask queue for async thunks that don't return promises
async function flushAsync(mockFetch) {
    if (mockFetch.mock.results[0]) {
        await mockFetch.mock.results[0].value;
    }
    await new Promise(resolve => setTimeout(resolve, 0));
}

describe('Action Module - Action Creators', () => {
    it('GIVEN save parameters WHEN save action is created THEN it should return SAVE action with correct payload', () => {
        const action = ACTIONS.save(true, 'test-file.xml');

        expect(action.type).toBe(ACTIONS.SAVE);
        expect(action.newVersion).toBe(true);
        expect(action.file).toBe('test-file.xml');
    });

    it('GIVEN addMaster WHEN called THEN it should return ADD_MASTER action', () => {
        const action = ACTIONS.addMaster();

        expect(action.type).toBe(ACTIONS.ADD_MASTER);
    });

    it('GIVEN deleteMaster with row index WHEN called THEN it should return DEL_MASTER action with correct index', () => {
        const action = ACTIONS.deleteMaster(2);

        expect(action.type).toBe(ACTIONS.DEL_MASTER);
        expect(action.rowIndex).toBe(2);
    });

    it('GIVEN deleteSlave with row index WHEN called THEN it should return DEL_SLAVE action with correct index', () => {
        const action = ACTIONS.deleteSlave(1);

        expect(action.type).toBe(ACTIONS.DEL_SLAVE);
        expect(action.rowIndex).toBe(1);
    });

    it('GIVEN deleteParameter with row index WHEN called THEN it should return DEL_PARAMETER action with correct index', () => {
        const action = ACTIONS.deleteParameter(0);

        expect(action.type).toBe(ACTIONS.DEL_PARAMETER);
        expect(action.rowIndex).toBe(0);
    });

    it('GIVEN addSlave with custom data WHEN called THEN it should return ADD_SLAVE action with custom data', () => {
        const customData = { name: 'customMethod', methodName: 'customMethod', parameters: [] };
        const action = ACTIONS.addSlave(customData);

        expect(action.type).toBe(ACTIONS.ADD_SLAVE);
        expect(action.newSlaveData).toEqual(customData);
    });

    it('GIVEN addSlave without custom data WHEN called THEN it should return ADD_SLAVE action without newSlaveData', () => {
        const action = ACTIONS.addSlave();

        expect(action.type).toBe(ACTIONS.ADD_SLAVE);
        expect(action.newSlaveData).toBeUndefined();
    });

    it('GIVEN addParameter WHEN called THEN it should return ADD_PARAMETER action', () => {
        const action = ACTIONS.addParameter();

        expect(action.type).toBe(ACTIONS.ADD_PARAMETER);
    });

    it('GIVEN loadMethodData with slave data WHEN called THEN it should return LOAD_METHOD_COMPLETED action with correct payload', () => {
        const slaveData = { name: 'method1', methodName: 'method1', parameters: [] };
        const action = ACTIONS.loadMethodData(slaveData);

        expect(action.type).toBe(ACTIONS.LOAD_METHOD_COMPLETED);
        expect(action.slaveData).toEqual(slaveData);
    });

    it('GIVEN loadSlaveData with master data and row index WHEN called THEN it should return LOAD_SLAVE_COMPLETE action', () => {
        const masterData = { id: 'bean1', name: 'Bean 1', methods: [] };
        const action = ACTIONS.loadSlaveData(masterData, 0);

        expect(action.type).toBe(ACTIONS.LOAD_SLAVE_COMPLETE);
        expect(action.masterRowData).toEqual(masterData);
    });

    it('GIVEN same row index WHEN loadSlaveData is called twice THEN it should return DO_NOTHING action', () => {
        const masterData = { id: 'bean1', name: 'Bean 1', methods: [] };

        ACTIONS.loadSlaveData(masterData, 0);
        const secondAction = ACTIONS.loadSlaveData(masterData, 0);

        expect(secondAction.type).toBe(ACTIONS.DO_NOTHING);
    });
});

describe('Action Module - Thunks', () => {
    let mockServer, dispatch;

    beforeEach(() => {
        mockServer = setupMockServer();
        setupMockBootbox();
        dispatch = vi.fn();
    });

    afterEach(() => {
        teardownMockServer();
        teardownMockBootbox();
    });

    it('GIVEN valid files WHEN loadMasterData thunk is dispatched THEN it should fetch and dispatch LOAD_MASTER_COMPLETED', async () => {
        const masterData = {
            springBeans: [
                { id: 'bean1', name: 'Bean 1', methods: [] },
            ],
        };
        mockServer.mockResponse('/xml', [masterData]);

        const thunk = ACTIONS.loadMasterData('test-files');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData,
        });
    });

    it('GIVEN server error WHEN loadMasterData thunk is dispatched THEN it should handle error', async () => {
        mockServer.mockError('/xml', 500);

        const thunk = ACTIONS.loadMasterData('test-files');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOAD_MASTER_COMPLETED })
        );
    });

    it('GIVEN valid beanId WHEN loadBeanMethods thunk is dispatched THEN it should fetch and dispatch LOADED_BEAN_METHODS', async () => {
        const result = [
            { name: 'method1', methodName: 'method1' },
            { name: 'method2', methodName: 'method2' },
        ];
        mockServer.mockResponse('/loadMethods', result);

        const thunk = ACTIONS.loadBeanMethods('bean1');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOADED_BEAN_METHODS,
            result,
        });
    });

    it('GIVEN server error WHEN loadBeanMethods thunk is dispatched THEN it should handle error', async () => {
        mockServer.mockError('/loadMethods', 401);

        const thunk = ACTIONS.loadBeanMethods('bean1');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOADED_BEAN_METHODS })
        );
    });
});

describe('Action Module - saveData Function', () => {
    let mockBootbox;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    it('GIVEN valid action data WHEN saveData is called THEN it should generate correct XML', async () => {
        const { ajaxSave } = await import('../../Utils.js');
        ajaxSave.mockClear();
        ajaxSave.mockImplementation((_url, _postData, callback) => {
            if (callback) callback();
        });

        const data = [
            {
                id: 'bean1',
                name: 'Action Bean',
                methods: [
                    {
                        name: 'doAction',
                        methodName: 'doAction',
                        parameters: [
                            { name: 'param1', type: 'String' },
                            { name: 'param2', type: 'Integer' },
                        ],
                    },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'actions.xml');

        expect(ajaxSave).toHaveBeenCalled();
        const callArgs = ajaxSave.mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain('<?xml version="1.0" encoding="utf-8"?>');
        expect(xmlContent).toContain('<action-library>');
        expect(xmlContent).toContain("<spring-bean id='bean1' name='Action Bean'>");
        expect(xmlContent).toContain("<method name='doAction' method-name='doAction'>");
        expect(xmlContent).toContain("<parameter name='param1' type='String'/>");
        expect(xmlContent).toContain("<parameter name='param2' type='Integer'/>");
        expect(xmlContent).toContain('</method>');
        expect(xmlContent).toContain('</spring-bean>');
        expect(xmlContent).toContain('</action-library>');
    });

    it('GIVEN data with empty name WHEN saveData is called THEN it should show alert for missing name', () => {
        const data = [
            {
                id: 'bean1',
                name: '',
                methods: [
                    { name: 'doAction', methodName: 'doAction', parameters: [] },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'actions.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertCalls = window.bootbox.alert.mock.calls;
        const lastAlert = alertCalls[alertCalls.length - 1][0];
        expect(lastAlert).toContain('动作名称不能为空');
    });

    it('GIVEN data with empty bean id WHEN saveData is called THEN it should show alert for missing bean id', () => {
        const data = [
            {
                id: '',
                name: 'Action Bean',
                methods: [
                    { name: 'doAction', methodName: 'doAction', parameters: [] },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'actions.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertCalls = window.bootbox.alert.mock.calls;
        const lastAlert = alertCalls[alertCalls.length - 1][0];
        expect(lastAlert).toContain('Bean Id不能为空');
    });

    it('GIVEN data with no methods WHEN saveData is called THEN it should show alert for missing methods', () => {
        const data = [
            {
                id: 'bean1',
                name: 'Action Bean',
                methods: [],
            },
        ];

        ACTIONS.saveData(data, false, 'actions.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertCalls = window.bootbox.alert.mock.calls;
        const lastAlert = alertCalls[alertCalls.length - 1][0];
        expect(lastAlert).toContain('动作分类[Action Bean]下未定义具体的动作方法');
    });

    it('GIVEN data with method missing name WHEN saveData is called THEN it should show alert for missing method name', () => {
        const data = [
            {
                id: 'bean1',
                name: 'Action Bean',
                methods: [
                    { name: '', methodName: 'doAction', parameters: [] },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'actions.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertCalls = window.bootbox.alert.mock.calls;
        const lastAlert = alertCalls[alertCalls.length - 1][0];
        expect(lastAlert).toContain('名称不能为空');
    });

    it('GIVEN data with method missing methodName WHEN saveData is called THEN it should show alert for missing method name', () => {
        const data = [
            {
                id: 'bean1',
                name: 'Action Bean',
                methods: [
                    { name: 'doAction', methodName: '', parameters: [] },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'actions.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertCalls = window.bootbox.alert.mock.calls;
        const lastAlert = alertCalls[alertCalls.length - 1][0];
        expect(lastAlert).toContain('方法名不能为空');
    });

    it('GIVEN data with parameter missing name WHEN saveData is called THEN it should show alert for missing parameter name', () => {
        const data = [
            {
                id: 'bean1',
                name: 'Action Bean',
                methods: [
                    {
                        name: 'doAction',
                        methodName: 'doAction',
                        parameters: [{ name: '', type: 'String' }],
                    },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'actions.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertCalls = window.bootbox.alert.mock.calls;
        const lastAlert = alertCalls[alertCalls.length - 1][0];
        expect(lastAlert).toContain('参数名不能为空');
    });

    it('GIVEN data with parameter missing type WHEN saveData is called THEN it should show alert for missing parameter type', () => {
        const data = [
            {
                id: 'bean1',
                name: 'Action Bean',
                methods: [
                    {
                        name: 'doAction',
                        methodName: 'doAction',
                        parameters: [{ name: 'param1', type: '' }],
                    },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'actions.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertCalls = window.bootbox.alert.mock.calls;
        const lastAlert = alertCalls[alertCalls.length - 1][0];
        expect(lastAlert).toContain('参数类型不能为空');
    });

    it('GIVEN valid data and newVersion=true WHEN saveData is called THEN it should prompt for version comment', async () => {
        const { ajaxSave } = await import('../../Utils.js');
        ajaxSave.mockClear();
        ajaxSave.mockImplementation((_url, _postData, callback) => {
            if (callback) callback();
        });

        window.bootbox.prompt = vi.fn((_msg, callback) => {
            callback('Test version comment');
        });

        const data = [
            {
                id: 'bean1',
                name: 'Action Bean',
                methods: [
                    { name: 'doAction', methodName: 'doAction', parameters: [] },
                ],
            },
        ];

        ACTIONS.saveData(data, true, 'actions.xml');

        expect(ajaxSave).toHaveBeenCalled();
        const callArgs = ajaxSave.mock.calls[0];
        expect(callArgs[1].versionComment).toBe('Test version comment');
    });
});
