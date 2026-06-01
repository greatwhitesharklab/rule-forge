import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as ACTIONS from './action.js';
import { setupMockServer, teardownMockServer } from '../__test_utils__/mockServer.js';
import { setupMockBootbox, teardownMockBootbox } from '../__test_utils__/mockBootbox.js';

// Mock componentEvent module used by action.js
vi.mock('../components/componentEvent.js', () => ({
    eventEmitter: { emit: vi.fn() },
    SHOW_LOADING: 'SHOW_LOADING',
    HIDE_LOADING: 'HIDE_LOADING',
}));

// Mock ajaxSave from Utils to intercept the module import
vi.mock('../Utils.js', () => ({
    ajaxSave: vi.fn(),
    handleResponseError: vi.fn(),
}));

// Helper to flush async chains
async function flushAsync(mockFetch: ReturnType<typeof vi.fn>) {
    if (mockFetch.mock && mockFetch.mock.results[0]) {
        await mockFetch.mock.results[0].value;
    }
    await new Promise(resolve => setTimeout(resolve, 0));
}

describe('Resource Module - Action Creators', () => {
    it('GIVEN newVersion and file WHEN save action is created THEN it should return SAVE action with correct payload', () => {
        const act = ACTIONS.save(true, 'test-file.xml');
        expect(act.type).toBe(ACTIONS.SAVE);
        expect(act.newVersion).toBe(true);
        expect(act.file).toBe('test-file.xml');
    });

    it('GIVEN masterRowData WHEN loadSlaveData is called THEN it should return LOAD_SLAVE_COMPLETE action', () => {
        const masterRowData = { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] };
        const act = ACTIONS.loadSlaveData(masterRowData);
        expect(act.type).toBe(ACTIONS.LOAD_SLAVE_COMPLETE);
        expect(act.masterRowData).toEqual(masterRowData);
    });

    it('GIVEN file WHEN reFresh is called THEN it should return a thunk that generates variable library', () => {
        const thunk = ACTIONS.reFresh('test-file.xml');
        expect(typeof thunk).toBe('function');
        expect(thunk.length).toBe(1);
    });
});

describe('Resource Module - Thunks', () => {
    let mockServer: ReturnType<typeof setupMockServer>, dispatch: ReturnType<typeof vi.fn>, mockBootbox: ReturnType<typeof setupMockBootbox>;

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
            { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] },
            { name: 'Category2', type: 'type2', clazz: 'clazz2', variables: [] },
        ];
        mockServer.mockResponse('/xml', [masterData]);

        const thunk = ACTIONS.loadMasterData('test-files');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData,
        });
        const { eventEmitter } = await import('../components/componentEvent.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith('HIDE_LOADING');
    });

    it('GIVEN server error WHEN loadMasterData thunk is dispatched THEN it should handle error', async () => {
        mockServer.mockError('/xml', 500);

        const thunk = ACTIONS.loadMasterData('test-files');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOAD_MASTER_COMPLETED })
        );
        const { eventEmitter } = await import('../components/componentEvent.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith('HIDE_LOADING');
    });

    it('GIVEN valid file WHEN generateVariableLibrary thunk is dispatched THEN it should fetch and dispatch LOAD_MASTER_COMPLETED', async () => {
        const variableCategories = [
            { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] }
        ];
        mockServer.mockResponse('/variableeditor/generateVariableLibrary', { variableCategories });

        const thunk = ACTIONS.generateVariableLibrary('test-file.xml');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData: variableCategories,
        });
        const { eventEmitter } = await import('../components/componentEvent.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith('HIDE_LOADING');
    });

    it('GIVEN file and newVersion WHEN generateVariableLibrary is dispatched with file THEN it should dispatch SAVE action', async () => {
        const variableCategories = [
            { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] }
        ];
        mockServer.mockResponse('/variableeditor/generateVariableLibrary', { variableCategories });

        const thunk = ACTIONS.generateVariableLibrary('test-file.xml');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.SAVE,
            file: 'test-file.xml',
            newVersion: false,
        });
    });

    it('GIVEN server error WHEN generateVariableLibrary thunk is dispatched THEN it should handle error', async () => {
        mockServer.mockError('/variableeditor/generateVariableLibrary', 500);

        const thunk = ACTIONS.generateVariableLibrary('test-file.xml');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOAD_MASTER_COMPLETED })
        );
        const { eventEmitter } = await import('../components/componentEvent.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith('HIDE_LOADING');
    });

    it('GIVEN valid data and file WHEN addVariable thunk is dispatched THEN it should add variable and regenerate library', async () => {
        mockServer.mockResponse('/common/addVariable', {});
        mockServer.mockResponse('/variableeditor/generateVariableLibrary', {
            variableCategories: [
                { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] }
            ]
        });

        // Create a dispatch that handles thunks (like redux-thunk middleware)
        const dispatch = vi.fn(async (act: unknown) => {
            if (typeof act === 'function') {
                await (act as Function)(dispatch);
            }
        });

        const data = { name: 'newVar', label: 'New Variable', type: 'String' };
        const thunk = ACTIONS.addVariable(data, 'test-file.xml');
        thunk(dispatch);

        // Wait for first fetch (addVariable)
        if (mockServer.fetchMock.mock.results[0]) {
            await mockServer.fetchMock.mock.results[0].value;
        }
        await new Promise(resolve => setTimeout(resolve, 0));

        // Wait for second fetch (generateVariableLibrary)
        if (mockServer.fetchMock.mock.results[1]) {
            await mockServer.fetchMock.mock.results[1].value;
        }
        await new Promise(resolve => setTimeout(resolve, 0));

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData: [
                { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] }
            ],
        });
        const { eventEmitter } = await import('../components/componentEvent.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith('HIDE_LOADING');
    });

    it('GIVEN server error WHEN addVariable thunk is dispatched THEN it should handle error', async () => {
        mockServer.mockError('/common/addVariable', 500);

        const data = { name: 'newVar', label: 'New Variable', type: 'String' };
        const thunk = ACTIONS.addVariable(data, 'test-file.xml');
        thunk(dispatch);

        await flushAsync(mockServer.fetchMock);

        const { eventEmitter } = await import('../components/componentEvent.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith('HIDE_LOADING');
    });
});

describe('Resource Module - saveData Function', () => {
    let mockBootbox: ReturnType<typeof setupMockBootbox>;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    it('GIVEN valid category data WHEN saveData is called THEN it should generate correct XML', async () => {
        const { ajaxSave } = await import('../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: Record<string, string>, callback?: () => void) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Category1',
                type: 'type1',
                clazz: 'com.example.Category1',
                variables: [
                    { name: 'var1', label: 'Variable 1', type: 'String' },
                    { name: 'var2', label: 'Variable 2', type: 'Integer' },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'variables.xml');

        expect(ajaxSave).toHaveBeenCalled();
        const callArgs = (ajaxSave as ReturnType<typeof vi.fn>).mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain('<?xml version="1.0" encoding="utf-8"?>');
        expect(xmlContent).toContain('<variable-library>');
        expect(xmlContent).toContain("<category name='Category1' type='type1' clazz='com.example.Category1'>");
        expect(xmlContent).toContain("<var act='InOut' name='var1' label='Variable 1' type='String'/>");
        expect(xmlContent).toContain("<var act='InOut' name='var2' label='Variable 2' type='Integer'/>");
        expect(xmlContent).toContain('</category>');
        expect(xmlContent).toContain('</variable-library>');
    });

    it('GIVEN data with special characters WHEN saveData is called THEN it should escape XML properly', async () => {
        const { ajaxSave } = await import('../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: Record<string, string>, callback?: () => void) => {
            if (callback) callback();
        });

        const data = [
            {
                name: '<Category> & "Category"',
                type: 'type1',
                clazz: 'com.example.Clazz',
                variables: [
                    { name: 'var<1>', label: 'Label & Label', type: 'String' },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'variables.xml');

        const callArgs = (ajaxSave as ReturnType<typeof vi.fn>).mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain('&lt;Category&gt; &amp; &quot;Category&quot;');
        expect(xmlContent).toContain('var&lt;1&gt;');
        expect(xmlContent).toContain('Label &amp; Label');
    });

    it('GIVEN data with newVersion=true WHEN saveData is called THEN it should prompt for version comment', async () => {
        const { ajaxSave } = await import('../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: Record<string, string>, callback?: () => void) => {
            if (callback) callback();
        });

        window.bootbox.prompt = vi.fn((msg: string, callback: (result: string | null) => void) => {
            callback('Test version comment');
        });

        const data = [
            {
                name: 'Category1',
                type: 'type1',
                clazz: 'com.example.Clazz',
                variables: [],
            },
        ];

        ACTIONS.saveData(data, true, 'variables.xml');

        expect(window.bootbox.prompt).toHaveBeenCalled();
        expect(ajaxSave).toHaveBeenCalled();
        const callArgs = (ajaxSave as ReturnType<typeof vi.fn>).mock.calls[0];
        expect(callArgs[1].versionComment).toBe('Test version comment');
    });

    it('GIVEN data with newVersion=true and no comment WHEN saveData is called THEN it should not save', async () => {
        const { ajaxSave } = await import('../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: Record<string, string>, callback?: () => void) => {
            if (callback) callback();
        });

        window.bootbox.prompt = vi.fn((msg: string, callback: (result: string | null) => void) => {
            callback(null); // User cancels
        });

        const data = [
            {
                name: 'Category1',
                type: 'type1',
                clazz: 'com.example.Clazz',
                variables: [],
            },
        ];

        ACTIONS.saveData(data, true, 'variables.xml');

        expect(window.bootbox.prompt).toHaveBeenCalled();
        expect(ajaxSave).not.toHaveBeenCalled();
    });

    it('GIVEN data with newVersion=false WHEN saveData is called THEN it should save without prompting', async () => {
        const { ajaxSave } = await import('../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: Record<string, string>, callback?: () => void) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Category1',
                type: 'type1',
                clazz: 'com.example.Clazz',
                variables: [],
            },
        ];

        ACTIONS.saveData(data, false, 'variables.xml');

        expect(window.bootbox.prompt).not.toHaveBeenCalled();
        expect(ajaxSave).toHaveBeenCalled();
    });

    it('GIVEN multiple categories WHEN saveData is called THEN it should generate XML for all categories', async () => {
        const { ajaxSave } = await import('../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: Record<string, string>, callback?: () => void) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Category1',
                type: 'type1',
                clazz: 'com.example.Clazz1',
                variables: [
                    { name: 'var1', label: 'Variable 1', type: 'String' },
                ],
            },
            {
                name: 'Category2',
                type: 'type2',
                clazz: 'com.example.Clazz2',
                variables: [
                    { name: 'var2', label: 'Variable 2', type: 'Integer' },
                ],
            },
        ];

        ACTIONS.saveData(data, false, 'variables.xml');

        const callArgs = (ajaxSave as ReturnType<typeof vi.fn>).mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain("name='Category1'");
        expect(xmlContent).toContain("name='Category2'");
        expect(xmlContent).toContain("clazz='com.example.Clazz1'");
        expect(xmlContent).toContain("clazz='com.example.Clazz2'");
    });

    it('GIVEN action type is SAVE_COMPLETED WHEN saveData is called THEN it should return correct action', () => {
        const data = [
            {
                name: 'Category1',
                type: 'type1',
                clazz: 'com.example.Clazz',
                variables: [],
            },
        ];

        const result = ACTIONS.saveData(data, false, 'variables.xml');
        expect(result).toEqual({ type: ACTIONS.SAVE_COMPLETED });
    });

    it('GIVEN category with no variables WHEN saveData is called THEN it should generate empty category', async () => {
        const { ajaxSave } = await import('../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: Record<string, string>, callback?: () => void) => {
            if (callback) callback();
        });

        const data = [
            {
                name: 'Category1',
                type: 'type1',
                clazz: 'com.example.Clazz',
                variables: [],
            },
        ];

        ACTIONS.saveData(data, false, 'variables.xml');

        const callArgs = (ajaxSave as ReturnType<typeof vi.fn>).mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain("<category name='Category1' type='type1' clazz='com.example.Clazz'>");
        expect(xmlContent).toContain('</category>');
    });
});
