import {describe, it, expect, vi, beforeEach, afterEach} from 'vitest';
import * as ACTIONS from '../action.js';
import {setupMockBootbox, teardownMockBootbox} from '../../__test_utils__/mockBootbox.js';

// Mock ajaxSave from Utils to intercept the module import
vi.mock('../../Utils.js', () => ({
    ajaxSave: vi.fn(),
    handleResponseError: vi.fn(),
}));

// Helper to flush async chains
async function flushAsync(mockFetch: { mock?: { results: Array<{ value: unknown }> } }) {
    if (mockFetch.mock && mockFetch.mock.results[0]) {
        await mockFetch.mock.results[0].value;
    }
    await new Promise(resolve => setTimeout(resolve, 0));
}

describe('Parameter Module - Action Creators', () => {
    it('GIVEN add WHEN called THEN it should return ADD action', () => {
        const action = ACTIONS.add();
        expect(action.type).toBe(ACTIONS.ADD);
    });

    it('GIVEN save with parameters WHEN called THEN it should return SAVE action with correct payload', () => {
        const action = ACTIONS.save(true, 'test-file.xml');
        expect(action.type).toBe(ACTIONS.SAVE);
        expect(action.newVersion).toBe(true);
        expect(action.file).toBe('test-file.xml');
    });

    it('GIVEN del with row index WHEN called THEN it should return DEL action with correct index', () => {
        const action = ACTIONS.del(2);
        expect(action.type).toBe(ACTIONS.DEL);
        expect(action.rowIndex).toBe(2);
    });
});

describe('Parameter Module - Thunks', () => {
    let mockBootbox: ReturnType<typeof setupMockBootbox>;
    let dispatch: (a: unknown) => void;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
        dispatch = vi.fn() as unknown as (a: unknown) => void;
    });

    afterEach(() => {
        teardownMockBootbox();
        delete global.fetch;
    });

    it('GIVEN valid files WHEN loadData thunk is dispatched THEN it should fetch and dispatch LOAD_DATA_COMPLETED', async () => {
        const mockResponse = [
            {name: 'param1', label: 'Parameter 1', type: 'String'},
            {name: 'param2', label: 'Parameter 2', type: 'Integer'},
        ];

        const mockFetch = vi.fn(() =>
            Promise.resolve({
                ok: true,
                json: () => Promise.resolve(mockResponse),
            })
        );
        global.fetch = mockFetch as unknown as typeof fetch;

        const thunk = ACTIONS.loadData('test-files');
        thunk(dispatch);

        await flushAsync(mockFetch);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_DATA_COMPLETED,
            data: mockResponse[0],
        });
    });

    it('GIVEN server error WHEN loadData thunk is dispatched THEN it should handle 401 error', async () => {
        const mockFetch = vi.fn(() =>
            Promise.resolve({
                ok: false,
                status: 401,
                text: () => Promise.resolve('Unauthorized'),
            })
        );
        global.fetch = mockFetch as unknown as typeof fetch;

        const thunk = ACTIONS.loadData('test-files');
        thunk(dispatch);

        await flushAsync(mockFetch);

        const lastMsg = mockBootbox.getLastAlertMessage();
        expect(lastMsg).toBeTruthy();
        expect(lastMsg).toContain('权限不足');
        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({type: ACTIONS.LOAD_DATA_COMPLETED})
        );
    });

    it('GIVEN server error WHEN loadData thunk is dispatched THEN it should handle generic error', async () => {
        const mockFetch = vi.fn(() =>
            Promise.resolve({
                ok: false,
                status: 500,
                text: () => Promise.resolve('Internal Server Error'),
            })
        );
        global.fetch = mockFetch as unknown as typeof fetch;

        const thunk = ACTIONS.loadData('test-files');
        thunk(dispatch);

        await flushAsync(mockFetch);

        const lastMsg = mockBootbox.getLastAlertMessage();
        expect(lastMsg).toBeTruthy();
        expect(lastMsg).toContain('加载数据失败');
        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({type: ACTIONS.LOAD_DATA_COMPLETED})
        );
    });
});

describe('Parameter Module - saveData Function', () => {
    let mockBootbox: ReturnType<typeof setupMockBootbox>;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    it('GIVEN valid parameter data WHEN saveData is called THEN it should generate correct XML', async () => {
        const {ajaxSave} = await import('../../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: unknown, callback?: () => void) => {
            if (callback) callback();
        });

        const data = [
            {name: 'param1', label: 'Parameter 1', type: 'String'},
            {name: 'param2', label: 'Parameter 2', type: 'Integer'},
        ];

        ACTIONS.saveData(data, false, 'parameters.xml');

        expect(ajaxSave).toHaveBeenCalled();
        const callArgs = (ajaxSave as ReturnType<typeof vi.fn>).mock.calls[0];
        const postData = callArgs[1] as Record<string, unknown>;
        const xmlContent = decodeURIComponent(postData.content as string);

        expect(xmlContent).toContain('<?xml version="1.0" encoding="utf-8"?>');
        expect(xmlContent).toContain('<parameter-library>');
        expect(xmlContent).toContain("<parameter name='param1' label='Parameter 1' type='String' act='InOut'/>");
        expect(xmlContent).toContain("<parameter name='param2' label='Parameter 2' type='Integer' act='InOut'/>");
        expect(xmlContent).toContain('</parameter-library>');
    });

    it('GIVEN data with empty name WHEN saveData is called THEN it should show alert for missing name', () => {
        const data = [
            {name: '', label: 'Parameter 1', type: 'String'},
        ];

        ACTIONS.saveData(data, false, 'parameters.xml');

        const lastMsg = mockBootbox.getLastAlertMessage();
        expect(lastMsg).toBeTruthy();
        expect(lastMsg).toContain('参数名称不能为空');
    });

    it('GIVEN data with empty label WHEN saveData is called THEN it should show alert for missing label', () => {
        const data = [
            {name: 'param1', label: '', type: 'String'},
        ];

        ACTIONS.saveData(data, false, 'parameters.xml');

        const lastMsg = mockBootbox.getLastAlertMessage();
        expect(lastMsg).toBeTruthy();
        expect(lastMsg).toContain('参数标题不能为空');
    });

    it('GIVEN valid data and newVersion=true WHEN saveData is called THEN it should prompt for version comment', async () => {
        const {ajaxSave} = await import('../../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: unknown, callback?: () => void) => {
            if (callback) callback();
        });

        window.bootbox.prompt = vi.fn((_msg: string, callback: (result: string) => void) => {
            callback('Test version comment');
        });

        const data = [
            {name: 'param1', label: 'Parameter 1', type: 'String'},
        ];

        ACTIONS.saveData(data, true, 'parameters.xml');

        expect(ajaxSave).toHaveBeenCalled();
        const callArgs = (ajaxSave as ReturnType<typeof vi.fn>).mock.calls[0];
        expect(callArgs[1].versionComment).toBe('Test version comment');
    });

    it('GIVEN valid data and newVersion=true WHEN saveData is called and prompt is cancelled THEN it should not call ajaxSave', async () => {
        const {ajaxSave} = await import('../../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: unknown, callback?: () => void) => {
            if (callback) callback();
        });

        window.bootbox.prompt = vi.fn((_msg: string, callback: (result: string | null) => void) => {
            callback(null);
        });

        const data = [
            {name: 'param1', label: 'Parameter 1', type: 'String'},
        ];

        ACTIONS.saveData(data, true, 'parameters.xml');

        expect(ajaxSave).not.toHaveBeenCalled();
    });

    it('GIVEN valid data and newVersion=false WHEN saveData is called THEN it should call ajaxSave directly', async () => {
        const {ajaxSave} = await import('../../Utils.js');
        (ajaxSave as ReturnType<typeof vi.fn>).mockClear();
        (ajaxSave as ReturnType<typeof vi.fn>).mockImplementation((_url: string, _postData: unknown, callback?: () => void) => {
            if (callback) callback();
        });

        const data = [
            {name: 'param1', label: 'Parameter 1', type: 'String'},
        ];

        ACTIONS.saveData(data, false, 'parameters.xml');

        expect(ajaxSave).toHaveBeenCalled();
        expect(window.bootbox.prompt).not.toHaveBeenCalled();
    });
});
