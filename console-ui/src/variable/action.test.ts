import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as ACTIONS from './action.js';
import { setupMockBootbox, teardownMockBootbox } from '../__test_utils__/mockBootbox.js';

// Mock api/client.js — production code imports save and formPost
vi.mock('../api/client.js', () => ({
    save: vi.fn(),
    formPost: vi.fn(),
}));

// Helper to flush async chains
async function flushAsync() {
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
        const action = ACTIONS.importFields(2, jsonResult as any);
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
        const action = ACTIONS.loadSlaveData(masterRowData as any);
        expect(action.type).toBe(ACTIONS.LOAD_SLAVE_COMPLETE);
        expect(action.masterRowData).toEqual(masterRowData);
    });
});

describe('Variable Module - Thunks', () => {
    let dispatch: any, mockBootbox: any;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
        dispatch = vi.fn();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    it('GIVEN valid files WHEN loadMasterData thunk is dispatched THEN it should fetch and dispatch LOAD_MASTER_COMPLETED', async () => {
        const masterData = [
            { name: 'Cat1', type: 'Custom', clazz: 'c1', variables: [] },
            { name: 'Cat2', type: 'Custom', clazz: 'c2', variables: [] },
        ];
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockResolvedValue([masterData]);

        const thunk = ACTIONS.loadMasterData('test-files') as any;
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData,
        });
    });

    it('GIVEN server error WHEN loadMasterData thunk is dispatched THEN it should handle error', async () => {
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockRejectedValue(new Error('Server error'));

        const thunk = ACTIONS.loadMasterData('test-files') as any;
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOAD_MASTER_COMPLETED })
        );
    });

    it('GIVEN valid clazz WHEN generateFields thunk is dispatched THEN it should fetch and dispatch GENERATED_FIELDS', async () => {
        const fields = [
            { name: 'field1', label: 'Field 1', type: 'String' },
            { name: 'field2', label: 'Field 2', type: 'Integer' },
        ];
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockResolvedValue(fields);

        const thunk = ACTIONS.generateFields(0, 'com.example.TestClazz') as any;
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.GENERATED_FIELDS,
            rowIndex: 0,
            variables: fields,
        });
    });

    it('GIVEN server error WHEN generateFields thunk is dispatched THEN it should handle error', async () => {
        const { formPost } = await import('../api/client.js');
        (formPost as any).mockRejectedValue(new Error('Server error'));

        const thunk = ACTIONS.generateFields(0, 'com.example.BadClazz') as any;
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.GENERATED_FIELDS })
        );
    });
});

describe('Variable Module - saveData Function', () => {
    let mockBootbox: any;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    it('GIVEN valid variable data WHEN saveData is called THEN it should generate correct XML', async () => {
        const { save } = await import('../api/client.js') as any;
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

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

        ACTIONS.saveData(data as any, false, 'variables.xml');

        expect(save).toHaveBeenCalled();
        const callArgs = (save as any).mock.calls[0];
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

        const result = ACTIONS.saveData(data as any, false, 'test.xml');

        expect((window as any).bootbox.alert).toHaveBeenCalled();
        const alertMsg = (window as any).bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('变量分类名称不能为空');
        // saveData returns undefined when validation fails (no explicit return before bootbox.alert)
        expect(result).toBeUndefined();
    });

    it('GIVEN data with empty clazz WHEN saveData is called THEN it should alert error', () => {
        const data = [
            { name: 'Cat1', clazz: '', type: 'Custom', variables: [{ name: 'v1', label: 'V1', type: 'String' }] },
        ];

        ACTIONS.saveData(data as any, false, 'test.xml');

        expect((window as any).bootbox.alert).toHaveBeenCalled();
        const alertMsg = (window as any).bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('变量类路径不能为空');
    });

    it('GIVEN data with empty variables WHEN saveData is called THEN it should alert error', () => {
        const data = [
            { name: 'Cat1', clazz: 'com.example.Cls', type: 'Custom', variables: [] },
        ];

        ACTIONS.saveData(data as any, false, 'test.xml');

        expect((window as any).bootbox.alert).toHaveBeenCalled();
        const alertMsg = (window as any).bootbox.alert.mock.calls[0][0];
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

        ACTIONS.saveData(data as any, false, 'test.xml');

        expect((window as any).bootbox.alert).toHaveBeenCalled();
        const alertMsg = (window as any).bootbox.alert.mock.calls[0][0];
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

        ACTIONS.saveData(data as any, false, 'test.xml');

        expect((window as any).bootbox.alert).toHaveBeenCalled();
        const alertMsg = (window as any).bootbox.alert.mock.calls[0][0];
        expect(alertMsg).toContain('变量标题[SameLabel]重复');
    });

    it('GIVEN data with newVersion=true WHEN saveData is called THEN it should prompt for version comment', async () => {
        const { save } = await import('../api/client.js') as any;
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

        (window as any).bootbox.prompt = vi.fn((msg: any, callback: any) => {
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

        ACTIONS.saveData(data as any, true, 'variables.xml');

        expect((window as any).bootbox.prompt).toHaveBeenCalled();
        expect(save).toHaveBeenCalled();
        const callArgs = (save as any).mock.calls[0];
        expect(callArgs[1].versionComment).toBe('Test version comment');
    });

    it('GIVEN data with newVersion=true and cancelled prompt WHEN saveData is called THEN it should not save', async () => {
        const { save } = await import('../api/client.js') as any;
        (save as any).mockClear();

        (window as any).bootbox.prompt = vi.fn((msg: any, callback: any) => {
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

        ACTIONS.saveData(data as any, true, 'variables.xml');

        expect((window as any).bootbox.prompt).toHaveBeenCalled();
        expect(save).not.toHaveBeenCalled();
    });

    it('GIVEN valid data with newVersion=false WHEN saveData is called THEN it should save without prompting', async () => {
        const { save } = await import('../api/client.js') as any;
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

        const data = [
            {
                name: 'Cat1',
                type: 'Custom',
                clazz: 'com.example.Cls',
                variables: [{ name: 'v1', label: 'V1', type: 'String' }],
            },
        ];

        ACTIONS.saveData(data as any, false, 'variables.xml');

        expect((window as any).bootbox.prompt).not.toHaveBeenCalled();
        expect(save).toHaveBeenCalled();
    });

    it('GIVEN valid data WHEN saveData is called THEN it should return SAVE_COMPLETED action', async () => {
        const { save } = await import('../api/client.js') as any;
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

        const data = [
            {
                name: 'Cat1',
                type: 'Custom',
                clazz: 'com.example.Cls',
                variables: [{ name: 'v1', label: 'V1', type: 'String' }],
            },
        ];

        const result = ACTIONS.saveData(data as any, false, 'variables.xml');
        expect(result).toEqual({ type: ACTIONS.SAVE_COMPLETED });
    });
});
