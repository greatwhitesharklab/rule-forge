import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as ACTIONS from './action.js';
import { setupMockServer, teardownMockServer } from '../__test_utils__/mockServer.js';
import { setupMockBootbox, teardownMockBootbox } from '../__test_utils__/mockBootbox.js';

// Mock ajaxSave from Utils to intercept the module import
vi.mock('../Utils.js', () => ({
    ajaxSave: vi.fn(),
    handleResponseError: vi.fn(),
}));

// Helper to flush async chains
async function flushAsync(mockFetch) {
    if (mockFetch.mock && mockFetch.mock.results[0]) {
        await mockFetch.mock.results[0].value;
    }
    await new Promise(resolve => setTimeout(resolve, 0));
}

describe('Variable Module - Action Creators', () => {
    it('GIVEN newVersion and file WHEN save action is created THEN it should return SAVE action with correct payload', () => {
        const action = ACTIONS.save(true, 'test-file.xml');
        expect(action.type).toBe(ACTIONS.SAVE);
        expect(action.newVersion).toBe(true);
        expect(action.file).toBe('test-file.xml');
    });

    it('GIVEN rowIndex and jsonResult WHEN importFields is called THEN it should return IMPORT_FIELDS action', () => {
        const jsonResult = { variables: [{ name: 'v1', label: 'V1', type: 'String' }], clazz: 'com.test.Cls' };
        const action = ACTIONS.importFields(2, jsonResult);
        expect(action.type).toBe(ACTIONS.IMPORT_FIELDS);
        expect(action.rowIndex).toBe(2);
        expect(action.jsonResult).toEqual(jsonResult);
    });

    it('GIVEN masterName WHEN addMaster is called THEN it should return ADD_MASTER action', () => {
        const action = ACTIONS.addMaster('TestCategory');
        expect(action.type).toBe(ACTIONS.ADD_MASTER);
        expect(action.masterName).toBe('TestCategory');
    });

    it('GIVEN rowIndex WHEN deleteMaster is called THEN it should return DEL_MASTER action', () => {
        const action = ACTIONS.deleteMaster(3);
        expect(action.type).toBe(ACTIONS.DEL_MASTER);
        expect(action.rowIndex).toBe(3);
    });

    it('GIVEN rowIndex WHEN deleteSlave is called THEN it should return DEL_SLAVE action', () => {
        const action = ACTIONS.deleteSlave(1);
        expect(action.type).toBe(ACTIONS.DEL_SLAVE);
        expect(action.rowIndex).toBe(1);
    });

    it('GIVEN no arguments WHEN addSlave is called THEN it should return ADD_SLAVE action', () => {
        const action = ACTIONS.addSlave();
        expect(action.type).toBe(ACTIONS.ADD_SLAVE);
    });

    it('GIVEN masterRowData WHEN loadSlaveData is called THEN it should return LOAD_SLAVE_COMPLETE action', () => {
        const masterRowData = { name: 'Cat1', variables: [] };
        const action = ACTIONS.loadSlaveData(masterRowData);
        expect(action.type).toBe(ACTIONS.LOAD_SLAVE_COMPLETE);
        expect(action.masterRowData).toEqual(masterRowData);
    });
});

describe('Variable Module - Thunks', () => {
    let mockServer, dispatch, mockBootbox;

    beforeEach(() => {
        mockServer = setupMockServer();
        mockBootbox = setupMockBootbox();
        dispatch = vi.fn();
    });

    afterEach(() => {
        teardownMockServer();
        teardownMockBootbox();
    });

    it('GIVEN valid files WHEN loadMasterData thunk is dispatched THEN it should fetch and dispatch LOAD_MASTER_COMPLETED', async () => {
        const masterData = [
            { name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] },
            { name: 'Cat2', type: 'Custom', clazz: 'c2', variables: [] },
        ];
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
        // Error path shows bootbox alert
        expect(window.bootbox.alert).toHaveBeenCalled();
    });

    it('GIVEN valid clazz WHEN generateFields thunk is dispatched THEN it should fetch and dispatch GENERATED_FIELDS', async () => {
        const fields = [
            { name: 'field1', label: 'Field 1', type: 'String' },
            { name: 'field2', label: 'Field 2', type: 'Integer' },
        ];
        mockServer.mockResponse('/variableeditor/generateFields', fields);

        const thunk = ACTIONS.generateFields(0, 'com.example.TestClazz');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.GENERATED_FIELDS,
            rowIndex: 0,
            variables: fields,
        });
    });

    it('GIVEN server error WHEN generateFields thunk is dispatched THEN it should handle error', async () => {
        mockServer.mockError('/variableeditor/generateFields', 500);

        const thunk = ACTIONS.generateFields(0, 'com.example.BadClazz');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.GENERATED_FIELDS })
        );
        expect(window.bootbox.alert).toHaveBeenCalled();
    });
});

describe('Variable Module - saveData Function', () => {
    let mockBootbox;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    it('GIVEN valid variable data WHEN saveData is called THEN it should generate correct XML', async () => {
        const { ajaxSave } = await import('../Utils.js');
        ajaxSave.mockClear();
        ajaxSave.mockImplementation((_url, _postData, callback) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Category1',
                type: 'Custom',
                clazz: 'com.example.Category1',
                variables: [
                    { name: 'var1', label: 'Variable 1', type: 'String' },
                    { name: 'var2', label: 'Variable 2', type: 'Integer' },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'variables.xml');

        expect(ajaxSave).toHaveBeenCalled();
        const callArgs = ajaxSave.mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain('<?xml version="1.0" encoding="utf-8"?>');
        expect(xmlContent).toContain('<variable-library>');
        expect(xmlContent).toContain("<category name='Category1' type='Custom' clazz='com.example.Category1'>");
        expect(xmlContent).toContain("<var act='InOut' name='var1' label='Variable 1' type='String'/>");
        expect(xmlContent).toContain("<var act='InOut' name='var2' label='Variable 2' type='Integer'/>");
        expect(xmlContent).toContain('</category>');
        expect(xmlContent).toContain('</variable-library>');
    });

    it('GIVEN data with empty category name WHEN saveData is called THEN it should alert error', () => {
        const data = [
            { name: '', clazz: 'com.example.Clazz', type: 'Custom', variables: [{ name: 'v1', label: 'V1', type: 'String' }] },
        ];

        const result = ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('变量分类名称不能为空');
        // saveData returns undefined when validation fails (no explicit return before bootbox.alert)
        expect(result).toBeUndefined();
    });

    it('GIVEN data with empty clazz WHEN saveData is called THEN it should alert error', () => {
        const data = [
            { name: 'Cat1', clazz: '', type: 'Custom', variables: [{ name: 'v1', label: 'V1', type: 'String' }] },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('变量类路径不能为空');
    });

    it('GIVEN data with empty variables WHEN saveData is called THEN it should alert error', () => {
        const data = [
            { name: 'Cat1', clazz: 'com.example.Cls', type: 'Custom', variables: [] },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('变量分类[Cat1]下未定义具体变量信息');
    });

    it('GIVEN data with duplicate variable names WHEN saveData is called THEN it should alert error', () => {
        const data = [
            {
                name: 'Cat1',
                clazz: 'com.example.Cls',
                type: 'Custom',
                variables: [
                    { name: 'var1', label: 'Label 1', type: 'String' },
                    { name: 'var1', label: 'Label 2', type: 'Integer' },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('变量名[var1]重复');
    });

    it('GIVEN data with duplicate variable labels WHEN saveData is called THEN it should alert error', () => {
        const data = [
            {
                name: 'Cat1',
                clazz: 'com.example.Cls',
                type: 'Custom',
                variables: [
                    { name: 'var1', label: 'SameLabel', type: 'String' },
                    { name: 'var2', label: 'SameLabel', type: 'Integer' },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('变量标题[SameLabel]重复');
    });

    it('GIVEN data with newVersion=true WHEN saveData is called THEN it should prompt for version comment', async () => {
        const { ajaxSave } = await import('../Utils.js');
        ajaxSave.mockClear();
        ajaxSave.mockImplementation((_url, _postData, callback) => {
            if (callback) callback();
        });

        window.bootbox.prompt = vi.fn((msg, callback) => {
            callback('Test version comment');
        });

        const data = [
            {
                name: 'Cat1',
                type: 'Custom',
                clazz: 'com.example.Cls',
                variables: [{ name: 'v1', label: 'V1', type: 'String' }],
            },
        ];

        ACTIONS.saveData(data, true, 'variables.xml');

        expect(window.bootbox.prompt).toHaveBeenCalled();
        expect(ajaxSave).toHaveBeenCalled();
        const callArgs = ajaxSave.mock.calls[0];
        expect(callArgs[1].versionComment).toBe('Test version comment');
    });

    it('GIVEN data with newVersion=true and cancelled prompt WHEN saveData is called THEN it should not save', async () => {
        const { ajaxSave } = await import('../Utils.js');
        ajaxSave.mockClear();

        window.bootbox.prompt = vi.fn((msg, callback) => {
            callback(null);
        });

        const data = [
            {
                name: 'Cat1',
                type: 'Custom',
                clazz: 'com.example.Cls',
                variables: [{ name: 'v1', label: 'V1', type: 'String' }],
            },
        ];

        ACTIONS.saveData(data, true, 'variables.xml');

        expect(window.bootbox.prompt).toHaveBeenCalled();
        expect(ajaxSave).not.toHaveBeenCalled();
    });

    it('GIVEN valid data with newVersion=false WHEN saveData is called THEN it should save without prompting', async () => {
        const { ajaxSave } = await import('../Utils.js');
        ajaxSave.mockClear();
        ajaxSave.mockImplementation((_url, _postData, callback) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Cat1',
                type: 'Custom',
                clazz: 'com.example.Cls',
                variables: [{ name: 'v1', label: 'V1', type: 'String' }],
            },
        ];

        ACTIONS.saveData(data, false, 'variables.xml');

        expect(window.bootbox.prompt).not.toHaveBeenCalled();
        expect(ajaxSave).toHaveBeenCalled();
    });

    it('GIVEN valid data WHEN saveData is called THEN it should return SAVE_COMPLETED action', async () => {
        const { ajaxSave } = await import('../Utils.js');
        ajaxSave.mockClear();
        ajaxSave.mockImplementation((_url, _postData, callback) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Cat1',
                type: 'Custom',
                clazz: 'com.example.Cls',
                variables: [{ name: 'v1', label: 'V1', type: 'String' }],
            },
        ];

        const result = ACTIONS.saveData(data, false, 'variables.xml');
        expect(result).toEqual({ type: ACTIONS.SAVE_COMPLETED });
    });
});
