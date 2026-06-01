import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as ACTIONS from './action.js';
import { setupMockBootbox, teardownMockBootbox } from '../__test_utils__/mockBootbox.js';

// Mock componentEvent module used by action.js
vi.mock('../components/componentEvent.js', () => ({
    eventEmitter: { emit: vi.fn() },
    SHOW_LOADING: 'SHOW_LOADING',
    HIDE_LOADING: 'HIDE_LOADING',
}));

// Mock api/client.js — production code imports save and formPost
vi.mock('../api/client.js', () => ({
    save: vi.fn(),
    formPost: vi.fn(),
}));

// Helper to flush async chains
async function flushAsync() {
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
    let dispatch: ReturnType<typeof vi.fn>, mockBootbox: ReturnType<typeof setupMockBootbox>;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
        dispatch = vi.fn();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    it('GIVEN valid files WHEN loadMasterData thunk is dispatched THEN it should fetch and dispatch LOAD_MASTER_COMPLETED', async () => {
        const masterData = [
            { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] },
            { name: 'Category2', type: 'type2', clazz: 'clazz2', variables: [] },
        ];
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockResolvedValue([masterData]);

        const thunk = ACTIONS.loadMasterData('test-files');
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData,
        });
        const { eventEmitter } = await import('../components/componentEvent.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith('HIDE_LOADING');
    });

    it('GIVEN server error WHEN loadMasterData thunk is dispatched THEN it should handle error', async () => {
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockRejectedValue(new Error('Server error'));

        const thunk = ACTIONS.loadMasterData('test-files');
        thunk(dispatch);

        await flushAsync();

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
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockResolvedValue({ variableCategories });

        const thunk = ACTIONS.generateVariableLibrary('test-file.xml');
        thunk(dispatch);

        await flushAsync();

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
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockResolvedValue({ variableCategories });

        const thunk = ACTIONS.generateVariableLibrary('test-file.xml');
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.SAVE,
            file: 'test-file.xml',
            newVersion: false,
        });
    });

    it('GIVEN server error WHEN generateVariableLibrary thunk is dispatched THEN it should handle error', async () => {
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockRejectedValue(new Error('Server error'));

        const thunk = ACTIONS.generateVariableLibrary('test-file.xml');
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOAD_MASTER_COMPLETED })
        );
        const { eventEmitter } = await import('../components/componentEvent.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith('HIDE_LOADING');
    });

    it('GIVEN valid data and file WHEN addVariable thunk is dispatched THEN it should add variable and regenerate library', async () => {
        const variableCategories = [
            { name: 'Category1', type: 'type1', clazz: 'clazz1', variables: [] }
        ];
        const { formPost } = await import('../api/client.js');
        // First call: addVariable resolves with anything
        // Second call: generateVariableLibrary resolves with variableCategories
        (formPost as any)
            .mockResolvedValueOnce({})                     // addVariable
            .mockResolvedValueOnce({ variableCategories }); // generateVariableLibrary

        // Create a dispatch that handles thunks (like redux-thunk middleware)
        const dispatch = vi.fn(async (act: unknown) => {
            if (typeof act === 'function') {
                await (act as Function)(dispatch);
            }
        });

        const data = { name: 'newVar', label: 'New Variable', type: 'String' };
        const thunk = ACTIONS.addVariable(data, 'test-file.xml');
        thunk(dispatch);

        await flushAsync();

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
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockRejectedValue(new Error('Server error'));

        const data = { name: 'newVar', label: 'New Variable', type: 'String' };
        const thunk = ACTIONS.addVariable(data, 'test-file.xml');
        thunk(dispatch);

        await flushAsync();

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
        const { save } = await import('../api/client.js');
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

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

        expect(save).toHaveBeenCalled();
        const callArgs = (save as any).mock.calls[0];
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
        const { save } = await import('../api/client.js');
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

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

        const callArgs = (save as any).mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain('&lt;Category&gt; &amp; &quot;Category&quot;');
        expect(xmlContent).toContain('var&lt;1&gt;');
        expect(xmlContent).toContain('Label &amp; Label');
    });

    it('GIVEN data with newVersion=true WHEN saveData is called THEN it should prompt for version comment', async () => {
        const { save } = await import('../api/client.js');
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

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
        expect(save).toHaveBeenCalled();
        const callArgs = (save as any).mock.calls[0];
        expect(callArgs[1].versionComment).toBe('Test version comment');
    });

    it('GIVEN data with newVersion=true and no comment WHEN saveData is called THEN it should not save', async () => {
        const { save } = await import('../api/client.js');
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

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
        expect(save).not.toHaveBeenCalled();
    });

    it('GIVEN data with newVersion=false WHEN saveData is called THEN it should save without prompting', async () => {
        const { save } = await import('../api/client.js');
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

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
        expect(save).toHaveBeenCalled();
    });

    it('GIVEN multiple categories WHEN saveData is called THEN it should generate XML for all categories', async () => {
        const { save } = await import('../api/client.js');
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

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

        const callArgs = (save as any).mock.calls[0];
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
        const { save } = await import('../api/client.js');
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

        const data = [
            {
                name: 'Category1',
                type: 'type1',
                clazz: 'com.example.Clazz',
                variables: [],
            },
        ];

        ACTIONS.saveData(data, false, 'variables.xml');

        const callArgs = (save as any).mock.calls[0];
        const xmlContent = decodeURIComponent(callArgs[1].content);

        expect(xmlContent).toContain("<category name='Category1' type='type1' clazz='com.example.Clazz'>");
        expect(xmlContent).toContain('</category>');
    });
});
