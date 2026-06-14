import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
const { mocks, clearModalMockState, getLastAlertMessage, getLastConfirm, confirmLast } = vi.hoisted(() => {
    const alerts: { message: unknown; cb?: () => void }[] = [];
    const confirms: { message: string; callback: (ok: boolean) => void }[] = [];
    const alert = vi.fn((message: unknown, cb?: () => void) => {
        alerts.push({ message, cb });
        if (typeof cb === 'function') cb();
    });
    const confirm = vi.fn((message: string, callback: (ok: boolean) => void) => {
        confirms.push({ message, callback });
    });
    const prompt = vi.fn();
    const dialog = vi.fn();
    return {
        mocks: { alert, confirm, prompt, dialog },
        clearModalMockState: () => {
            alerts.length = 0;
            confirms.length = 0;
            alert.mockReset();
            confirm.mockReset();
            prompt.mockReset();
            dialog.mockReset();
        },
        getLastAlertMessage: () => {
            const last = alerts[alerts.length - 1];
            if (!last) return null;
            return typeof last.message === 'string' ? last.message : String(last.message);
        },
        getLastConfirm: () => confirms[confirms.length - 1] ?? null,
        confirmLast: (accept = true) => {
            const last = confirms[confirms.length - 1];
            if (last) last.callback(accept);
        },
    };
});

vi.mock('@/utils/modal', () => mocks);

import * as ACTIONS from '../action.js';
// Mock api/client.js to intercept the module import
// (saveData constructs url = apiBase() + '/common/saveFile')
vi.mock('../../api/client.js', () => ({
    save: vi.fn(),
    formPost: vi.fn(),
    apiBase: vi.fn(() => ''),
}));

// Helper to flush microtask queue for async thunks that don't return promises
async function flushAsync(mockFetch: any) {
    if (mockFetch.mock.results[0]) {
        await mockFetch.mock.results[0].value;
    }
    await new Promise(resolve => setTimeout(resolve, 0));
}


describe('Action Module - Thunks', () => {
    let dispatch: any;

    beforeEach(() => {
        clearModalMockState();
        dispatch = vi.fn();
    });

    afterEach(() => {
    });

    it('GIVEN valid files WHEN loadMasterData thunk is dispatched THEN it should fetch and dispatch LOAD_MASTER_COMPLETED', async () => {
        const masterData = {
            springBeans: [
                { id: 'bean1', name: 'Bean 1', methods: [] },
            ],
        };
        const { formPost } = await import('../../api/client.js') as any;
        (formPost as any).mockResolvedValue([masterData]);

        const thunk = ACTIONS.loadMasterData('test-files') as any;
        thunk(dispatch);

        await flushAsync(formPost);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData,
        });
    });

    it('GIVEN server error WHEN loadMasterData thunk is dispatched THEN it should handle error', async () => {
        const { formPost } = await import('../../api/client.js') as any;
        (formPost as any).mockRejectedValue(new Error('server error'));

        const thunk = ACTIONS.loadMasterData('test-files') as any;
        thunk(dispatch);

        await flushAsync(formPost);

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOAD_MASTER_COMPLETED })
        );
    });

    it('GIVEN valid beanId WHEN loadBeanMethods thunk is dispatched THEN it should fetch and dispatch LOADED_BEAN_METHODS', async () => {
        const result = [
            { name: 'method1', methodName: 'method1' },
            { name: 'method2', methodName: 'method2' },
        ];
        const { formPost } = await import('../../api/client.js') as any;
        (formPost as any).mockResolvedValue(result);

        const thunk = ACTIONS.loadBeanMethods('bean1') as any;
        thunk(dispatch);

        await flushAsync(formPost);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOADED_BEAN_METHODS,
            result,
        });
    });

    it('GIVEN server error WHEN loadBeanMethods thunk is dispatched THEN it should handle error', async () => {
        const { formPost } = await import('../../api/client.js') as any;
        (formPost as any).mockRejectedValue(new Error('unauthorized'));

        const thunk = ACTIONS.loadBeanMethods('bean1') as any;
        thunk(dispatch);

        await flushAsync(formPost);

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOADED_BEAN_METHODS })
        );
    });
});

describe('Action Module - saveData Function', () => {
    beforeEach(() => {
        clearModalMockState();
    });

    afterEach(() => {
    });

    it('GIVEN valid action data WHEN saveData is called THEN it should generate correct XML', async () => {
        const { save } = await import('../../api/client.js') as any;
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

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

        ACTIONS.saveData(data as any, false, 'actions.xml');

        expect(save).toHaveBeenCalled();
        const callArgs = (save as any).mock.calls[0];
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

        ACTIONS.saveData(data as any, false, 'actions.xml');

        expect(mocks.alert).toHaveBeenCalled();
        const alertCalls = mocks.alert.mock.calls;
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

        ACTIONS.saveData(data as any, false, 'actions.xml');

        expect(mocks.alert).toHaveBeenCalled();
        const alertCalls = mocks.alert.mock.calls;
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

        ACTIONS.saveData(data as any, false, 'actions.xml');

        expect(mocks.alert).toHaveBeenCalled();
        const alertCalls = mocks.alert.mock.calls;
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

        ACTIONS.saveData(data as any, false, 'actions.xml');

        expect(mocks.alert).toHaveBeenCalled();
        const alertCalls = mocks.alert.mock.calls;
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

        ACTIONS.saveData(data as any, false, 'actions.xml');

        expect(mocks.alert).toHaveBeenCalled();
        const alertCalls = mocks.alert.mock.calls;
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

        ACTIONS.saveData(data as any, false, 'actions.xml');

        expect(mocks.alert).toHaveBeenCalled();
        const alertCalls = mocks.alert.mock.calls;
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

        ACTIONS.saveData(data as any, false, 'actions.xml');

        expect(mocks.alert).toHaveBeenCalled();
        const alertCalls = mocks.alert.mock.calls;
        const lastAlert = alertCalls[alertCalls.length - 1][0];
        expect(lastAlert).toContain('参数类型不能为空');
    });

    it('GIVEN valid data and newVersion=true WHEN saveData is called THEN it should prompt for version comment', async () => {
        const { save } = await import('../../api/client.js') as any;
        (save as any).mockClear();
        (save as any).mockResolvedValue({ status: true });

        mocks.prompt.mockImplementation((_msg: any, callback: any) => {
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

        ACTIONS.saveData(data as any, true, 'actions.xml');

        expect(save).toHaveBeenCalled();
        const callArgs = (save as any).mock.calls[0];
        expect(callArgs[1].versionComment).toBe('Test version comment');
    });
});
