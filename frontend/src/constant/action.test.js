import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as ACTIONS from './action.js';
import { setupMockServer, teardownMockServer } from '../__test_utils__/mockServer.js';
import { setupMockBootbox, teardownMockBootbox } from '../__test_utils__/mockBootbox.js';

// Mock ajaxSave and handleResponseError from Utils to intercept the module import
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

describe('Constant Module - Action Creators', () => {
    it('GIVEN newVersion and file WHEN save action is created THEN it should return SAVE action with correct payload', () => {
        const action = ACTIONS.save(true, 'test-file.xml');
        expect(action.type).toBe(ACTIONS.SAVE);
        expect(action.newVersion).toBe(true);
        expect(action.file).toBe('test-file.xml');
    });

    it('GIVEN no arguments WHEN addMaster is called THEN it should return ADD_MASTER action', () => {
        const action = ACTIONS.addMaster();
        expect(action.type).toBe(ACTIONS.ADD_MASTER);
    });

    it('GIVEN rowIndex WHEN deleteMaster is called THEN it should return DEL_MASTER action', () => {
        const action = ACTIONS.deleteMaster(2);
        expect(action.type).toBe(ACTIONS.DEL_MASTER);
        expect(action.rowIndex).toBe(2);
    });

    it('GIVEN rowIndex WHEN deleteSlave is called THEN it should return DEL_SLAVE action', () => {
        const action = ACTIONS.deleteSlave(0);
        expect(action.type).toBe(ACTIONS.DEL_SLAVE);
        expect(action.rowIndex).toBe(0);
    });

    it('GIVEN no arguments WHEN addSlave is called THEN it should return ADD_SLAVE action', () => {
        const action = ACTIONS.addSlave();
        expect(action.type).toBe(ACTIONS.ADD_SLAVE);
    });

    it('GIVEN masterRowData WHEN loadSlaveData is called THEN it should return LOAD_SLAVE_COMPLETE action', () => {
        const masterRowData = { name: 'Const1', label: 'L1', constants: [] };
        const action = ACTIONS.loadSlaveData(masterRowData);
        expect(action.type).toBe(ACTIONS.LOAD_SLAVE_COMPLETE);
        expect(action.masterRowData).toEqual(masterRowData);
    });
});

describe('Constant Module - Thunks', () => {
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
        const categories = [
            { name: 'Const1', label: 'Group 1', constants: [] },
            { name: 'Const2', label: 'Group 2', constants: [] },
        ];
        mockServer.mockResponse('/xml', [{ categories }]);

        const thunk = ACTIONS.loadMasterData('test-files');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData: categories,
        });
    });

    it('GIVEN server error WHEN loadMasterData thunk is dispatched THEN it should call handleResponseError', async () => {
        mockServer.mockError('/xml', 500);

        const thunk = ACTIONS.loadMasterData('test-files');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOAD_MASTER_COMPLETED })
        );
        // handleResponseError is called on error
        const { handleResponseError } = await import('../Utils.js');
        expect(handleResponseError).toHaveBeenCalled();
    });
});

describe('Constant Module - saveData Function', () => {
    let mockBootbox;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    it('GIVEN valid constant data WHEN saveData is called THEN it should generate correct XML', async () => {
        const { ajaxSave } = await import('../Utils.js');
        ajaxSave.mockClear();
        ajaxSave.mockImplementation((_url, _postData, callback) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Constants1',
                label: 'Group 1',
                constants: [
                    { name: 'MAX_SIZE', label: 'Max Size', type: 'Integer' },
                    { name: 'GREETING', label: 'Greeting', type: 'String' },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'constants.xml');

        expect(ajaxSave).toHaveBeenCalled();
        const callArgs = ajaxSave.mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain('<?xml version="1.0" encoding="utf-8"?>');
        expect(xmlContent).toContain('<constant-library>');
        expect(xmlContent).toContain("<category name='Constants1' label='Group 1'>");
        expect(xmlContent).toContain("<constant name='MAX_SIZE' label='Max Size' type='Integer'/>");
        expect(xmlContent).toContain("<constant name='GREETING' label='Greeting' type='String'/>");
        expect(xmlContent).toContain('</category>');
        expect(xmlContent).toContain('</constant-library>');
    });

    it('GIVEN data with empty category name WHEN saveData is called THEN it should alert error', () => {
        const data = [
            { name: '', label: 'Group 1', constants: [{ name: 'C1', label: 'Const 1', type: 'String' }] },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('常量分类名称不能为空');
    });

    it('GIVEN data with empty category label WHEN saveData is called THEN it should alert error', () => {
        const data = [
            { name: 'Constants1', label: '', constants: [{ name: 'C1', label: 'Const 1', type: 'String' }] },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('常量分类标题不能为空');
    });

    it('GIVEN data with empty constants WHEN saveData is called THEN it should alert error', () => {
        const data = [
            { name: 'Constants1', label: 'Group 1', constants: [] },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('常量分类[Group 1]下未定义具体的常量信息');
    });

    it('GIVEN data with empty constant name WHEN saveData is called THEN it should alert error', () => {
        const data = [
            {
                name: 'Constants1',
                label: 'Group 1',
                constants: [{ name: '', label: 'Const 1', type: 'String' }],
            },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('常量名不能为空');
    });

    it('GIVEN data with empty constant label WHEN saveData is called THEN it should alert error', () => {
        const data = [
            {
                name: 'Constants1',
                label: 'Group 1',
                constants: [{ name: 'C1', label: '', type: 'String' }],
            },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('常量标题不能为空');
    });

    it('GIVEN data with empty constant type WHEN saveData is called THEN it should alert error', () => {
        const data = [
            {
                name: 'Constants1',
                label: 'Group 1',
                constants: [{ name: 'C1', label: 'Const 1', type: '' }],
            },
        ];

        ACTIONS.saveData(data, false, 'test.xml');

        expect(window.bootbox.alert).toHaveBeenCalled();
        const alertMsg = window.bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('常量数据类型不能为空');
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
                name: 'Constants1',
                label: 'Group 1',
                constants: [{ name: 'C1', label: 'Const 1', type: 'String' }],
            },
        ];

        ACTIONS.saveData(data, true, 'constants.xml');

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
                name: 'Constants1',
                label: 'Group 1',
                constants: [{ name: 'C1', label: 'Const 1', type: 'String' }],
            },
        ];

        ACTIONS.saveData(data, true, 'constants.xml');

        expect(window.bootbox.prompt).toHaveBeenCalled();
        expect(ajaxSave).not.toHaveBeenCalled();
    });

    it('GIVEN data with newVersion=false WHEN saveData is called THEN it should save without prompting', async () => {
        const { ajaxSave } = await import('../Utils.js');
        ajaxSave.mockClear();
        ajaxSave.mockImplementation((_url, _postData, callback) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Constants1',
                label: 'Group 1',
                constants: [{ name: 'C1', label: 'Const 1', type: 'String' }],
            },
        ];

        ACTIONS.saveData(data, false, 'constants.xml');

        expect(window.bootbox.prompt).not.toHaveBeenCalled();
        expect(ajaxSave).toHaveBeenCalled();
    });

    it('GIVEN multiple categories WHEN saveData is called THEN it should generate XML for all categories', async () => {
        const { ajaxSave } = await import('../Utils.js');
        ajaxSave.mockClear();
        ajaxSave.mockImplementation((_url, _postData, callback) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Constants1',
                label: 'Group 1',
                constants: [{ name: 'C1', label: 'Const 1', type: 'String' }],
            },
            {
                name: 'Constants2',
                label: 'Group 2',
                constants: [{ name: 'C2', label: 'Const 2', type: 'Integer' }],
            },
        ];

        ACTIONS.saveData(data, false, 'constants.xml');

        const callArgs = ajaxSave.mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain("name='Constants1'");
        expect(xmlContent).toContain("name='Constants2'");
        expect(xmlContent).toContain("label='Group 1'");
        expect(xmlContent).toContain("label='Group 2'");
    });
});
