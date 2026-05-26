import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as ACTIONS from '../action.js';
import { setupMockBootbox, teardownMockBootbox } from '../../__test_utils__/mockBootbox.js';

// Helper to flush async chains
async function flushAsync(mockFetch) {
    if (mockFetch.mock && mockFetch.mock.results[0]) {
        await mockFetch.mock.results[0].value;
    }
    await new Promise(resolve => setTimeout(resolve, 0));
}

describe('Package Module - Action Creators', () => {
    it('GIVEN addMaster with data WHEN called THEN it should return ADD_MASTER action with correct payload', () => {
        const packageData = { id: 'pkg1', name: 'Package 1' };
        const action = ACTIONS.addMaster(packageData);

        expect(action.type).toBe(ACTIONS.ADD_MASTER);
        expect(action.data).toEqual(packageData);
    });

    it('GIVEN updateMaster with data WHEN called THEN it should return UPDATE_MASTER action with correct payload', () => {
        const updateData = { rowIndex: 0, packageName: 'Updated Package' };
        const action = ACTIONS.updateMaster(updateData);

        expect(action.type).toBe(ACTIONS.UPDATE_MASTER);
        expect(action.data).toEqual(updateData);
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

    it('GIVEN addSlave with data WHEN called THEN it should return ADD_SLAVE action with correct payload', () => {
        const resourceData = { name: 'file.xml', path: '/path/to/file.xml', version: '1.0' };
        const action = ACTIONS.addSlave(resourceData);
        expect(action.type).toBe(ACTIONS.ADD_SLAVE);
        expect(action.data).toEqual(resourceData);
    });

    it('GIVEN updateSlave with data WHEN called THEN it should return UPDATE_SLAVE action with correct payload', () => {
        const updateData = { rowIndex: 0, name: 'updated.xml', path: '/new/path', version: '2.0' };
        const action = ACTIONS.updateSlave(updateData);
        expect(action.type).toBe(ACTIONS.UPDATE_SLAVE);
        expect(action.data).toEqual(updateData);
    });

    it('GIVEN loadSlaveData with master data WHEN called THEN it should return LOAD_SLAVE_COMPLETE action', () => {
        const masterData = { id: 'pkg1', name: 'Package 1', resourceItems: [] };
        const action = ACTIONS.loadSlaveData(masterData);
        expect(action.type).toBe(ACTIONS.LOAD_SLAVE_COMPLETE);
        expect(action.masterRowData).toEqual(masterData);
    });

    it('GIVEN apply with parameters WHEN called THEN it should return APPLY action with correct payload', () => {
        const action = ACTIONS.apply('test-project', {}, { id: 'pkg1' });
        expect(action.type).toBe(ACTIONS.APPLY);
        expect(action.project).toBe('test-project');
        expect(action.packageConfig).toEqual({});
        expect(action.currentPackage).toEqual({ id: 'pkg1' });
    });

    it('GIVEN save with parameters WHEN called THEN it should return SAVE action with correct payload', () => {
        const callback = vi.fn();
        const action = ACTIONS.save(true, 'test-project', [], 'comment', 'pkg1', callback);
        expect(action.type).toBe(ACTIONS.SAVE);
        expect(action.newVersion).toBe(true);
        expect(action.project).toBe('test-project');
        expect(action.associatedFiles).toEqual([]);
        expect(action.versionComment).toBe('comment');
        expect(action.packageId).toBe('pkg1');
        expect(action.callback).toBe(callback);
    });
});

describe('Package Module - Thunks', () => {
    let dispatch;

    beforeEach(() => {
        setupMockBootbox();
        dispatch = vi.fn();
    });

    afterEach(() => {
        teardownMockBootbox();
        delete global.fetch;
        delete window.parent;
    });

    it('GIVEN valid project WHEN loadMasterData thunk is dispatched THEN it should fetch and dispatch LOAD_MASTER_COMPLETED', async () => {
        const masterData = [
            { id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() },
        ];

        const mockFetch = vi.fn(() =>
            Promise.resolve({
                ok: true,
                json: () => Promise.resolve(masterData),
            })
        );
        global.fetch = mockFetch;

        const thunk = ACTIONS.loadMasterData('test-project');
        thunk(dispatch);

        await flushAsync(mockFetch);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData,
        });
    });

    it('GIVEN valid project WHEN loadPackageConfig thunk is dispatched THEN it should fetch and dispatch LOAD_PACKAGE_CONFIG_COMPLETE', async () => {
        const config = { project: 'test-project', version: '1.0' };

        const mockFetch = vi.fn(() =>
            Promise.resolve({
                ok: true,
                json: () => Promise.resolve(config),
            })
        );
        global.fetch = mockFetch;

        const thunk = ACTIONS.loadPackageConfig('test-project');
        thunk(dispatch);

        await flushAsync(mockFetch);

        expect(dispatch).toHaveBeenCalledWith({
            type: ACTIONS.LOAD_PACKAGE_CONFIG_COMPLETE,
            config,
        });
    });

    it('GIVEN valid data WHEN doTest thunk is called THEN it should fetch and call callback', async () => {
        const callback = vi.fn();
        const testData = { packageId: 'pkg1', testData: {} };
        const result = { status: true, data: 'Test result' };

        const mockFetch = vi.fn(() =>
            Promise.resolve({
                ok: true,
                json: () => Promise.resolve(result),
            })
        );
        global.fetch = mockFetch;

        // Set up window.parent.componentEvent for doTest
        window.parent = window;
        window.parent.componentEvent = {
            eventEmitter: { emit: vi.fn() },
            HIDE_LOADING: 'HIDE_LOADING',
        };

        ACTIONS.doTest(testData, callback);
        await flushAsync(mockFetch);

        expect(callback).toHaveBeenCalledWith(result);
    });

    it('GIVEN valid data WHEN doBatchTest thunk is called THEN it should fetch and call callback', async () => {
        const callback = vi.fn();
        const batchData = { packageId: 'pkg1', tests: [] };
        const result = { status: true, results: [] };

        const mockFetch = vi.fn(() =>
            Promise.resolve({
                ok: true,
                json: () => Promise.resolve(result),
            })
        );
        global.fetch = mockFetch;

        window.parent = window;
        window.parent.componentEvent = {
            eventEmitter: { emit: vi.fn() },
            HIDE_LOADING: 'HIDE_LOADING',
        };

        ACTIONS.doBatchTest(batchData, callback);
        await flushAsync(mockFetch);

        expect(callback).toHaveBeenCalledWith(result);
    });

    it('GIVEN valid data WHEN testFlow thunk is called THEN it should fetch and call callback', async () => {
        const callback = vi.fn();
        const flowData = { flowId: 'flow1', testData: {} };
        const result = { status: true, executionResult: {} };

        const mockFetch = vi.fn(() =>
            Promise.resolve({
                ok: true,
                json: () => Promise.resolve(result),
            })
        );
        global.fetch = mockFetch;

        window.parent = window;
        window.parent.componentEvent = {
            eventEmitter: { emit: vi.fn() },
            HIDE_LOADING: 'HIDE_LOADING',
        };

        ACTIONS.testFlow(flowData, callback);
        await flushAsync(mockFetch);

        expect(callback).toHaveBeenCalledWith(result);
    });
});

describe('Package Module - saveData Function', () => {
    let mockBootbox;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
        window.parent = window;
        window.parent.componentEvent = {
            eventEmitter: { emit: vi.fn() },
            SHOW_LOADING: 'SHOW_LOADING',
            HIDE_LOADING: 'HIDE_LOADING',
        };
    });

    afterEach(() => {
        teardownMockBootbox();
        delete window.parent.componentEvent;
        delete window.parent;
        delete global.fetch;
    });

    it('GIVEN valid package data WHEN saveData is called THEN it should generate correct XML and call fetch', async () => {
        const data = [
            {
                id: 'pkg1',
                name: 'Package 1',
                createDate: new Date('2024-01-01T00:00:00'),
                resourceItems: [
                    { name: 'file1.xml', path: '/path/to/file1.xml', version: '1.0' },
                    { name: 'file2.xml', path: '/path/to/file2.xml', version: '2.0' },
                ],
            },
        ];

        const mockFetch = vi.fn(() =>
            Promise.resolve({
                ok: true,
                json: () => Promise.resolve({ status: true }),
            })
        );
        global.fetch = mockFetch;

        ACTIONS.saveData(data, false, 'test-project', [], null, 'pkg1', null);
        await flushAsync(mockFetch);

        expect(global.fetch).toHaveBeenCalled();
        const fetchCall = global.fetch.mock.calls[0];
        const body = JSON.parse(fetchCall[1].body);

        expect(fetchCall[0]).toContain('/packageeditor/saveResourcePackages');
        expect(fetchCall[1].method).toBe('POST');

        const xmlContent = decodeURIComponent(body.xml);
        expect(xmlContent).toContain('<?xml version="1.0" encoding="utf-8"?>');
        expect(xmlContent).toContain('<res-packages>');
        expect(xmlContent).toContain("<res-package id='pkg1' name='Package 1'");
        // Source code has double space before name in res-package-item
        expect(xmlContent).toContain("<res-package-item  name='file1.xml' path='/path/to/file1.xml' version='1.0'/>");
        expect(xmlContent).toContain("<res-package-item  name='file2.xml' path='/path/to/file2.xml' version='2.0'/>");
        expect(xmlContent).toContain('</res-package>');
        expect(xmlContent).toContain('</res-packages>');
    });

    it('GIVEN valid data and callback WHEN saveData is called THEN it should call callback on success', async () => {
        const callback = vi.fn();
        const data = [
            {
                id: 'pkg1',
                name: 'Package 1',
                createDate: new Date(),
                resourceItems: [],
            },
        ];

        const fetchPromise = Promise.resolve({
            ok: true,
            json: () => Promise.resolve({ status: true }),
        });

        global.fetch = vi.fn(() => fetchPromise);

        ACTIONS.saveData(data, false, 'test-project', [], null, 'pkg1', callback);

        await fetchPromise;
        await new Promise(resolve => setTimeout(resolve, 10));

        expect(callback).toHaveBeenCalled();
    });
});

describe('Package Module - Utility Functions', () => {
    describe('buildSimulatorVariableEditorType', () => {
        it('GIVEN variable data with numeric types WHEN buildSimulatorVariableEditorType is called THEN it should set editorType to number', () => {
            const data = [
                {
                    name: 'TestCategory',
                    variables: [
                        { name: 'intVar', type: 'Integer' },
                        { name: 'doubleVar', type: 'Double' },
                        { name: 'longVar', type: 'Long' },
                        { name: 'floatVar', type: 'Float' },
                        { name: 'bigDecimalVar', type: 'BigDecimal' },
                    ],
                },
            ];

            ACTIONS.buildSimulatorVariableEditorType(data);

            data[0].variables.forEach(v => {
                expect(v._editorType).toBe('number');
            });
        });

        it('GIVEN variable data with Boolean type WHEN buildSimulatorVariableEditorType is called THEN it should set editorType to boolean', () => {
            const data = [
                {
                    name: 'TestCategory',
                    variables: [{ name: 'boolVar', type: 'Boolean' }],
                },
            ];

            ACTIONS.buildSimulatorVariableEditorType(data);
            expect(data[0].variables[0]._editorType).toBe('boolean');
        });

        it('GIVEN variable data with Date type WHEN buildSimulatorVariableEditorType is called THEN it should set editorType to date', () => {
            const data = [
                {
                    name: 'TestCategory',
                    variables: [{ name: 'dateVar', type: 'Date' }],
                },
            ];

            ACTIONS.buildSimulatorVariableEditorType(data);
            expect(data[0].variables[0]._editorType).toBe('date');
        });

        it('GIVEN variable data with collection types WHEN buildSimulatorVariableEditorType is called THEN it should set editorType to list', () => {
            const data = [
                {
                    name: 'TestCategory',
                    variables: [
                        { name: 'listVar', type: 'List' },
                        { name: 'setVar', type: 'Set' },
                    ],
                },
            ];

            ACTIONS.buildSimulatorVariableEditorType(data);

            data[0].variables.forEach(v => {
                expect(v._editorType).toBe('list');
            });
        });

        it('GIVEN variable data with other types WHEN buildSimulatorVariableEditorType is called THEN it should set editorType to string', () => {
            const data = [
                {
                    name: 'TestCategory',
                    variables: [
                        { name: 'stringVar', type: 'String' },
                        { name: 'objectVar', type: 'Object' },
                    ],
                },
            ];

            ACTIONS.buildSimulatorVariableEditorType(data);

            data[0].variables.forEach(v => {
                expect(v._editorType).toBe('string');
            });
        });

        it('GIVEN variable data with no variables WHEN buildSimulatorVariableEditorType is called THEN it should not throw error', () => {
            const data = [
                {
                    name: 'TestCategory',
                    variables: null,
                },
            ];

            expect(() => {
                ACTIONS.buildSimulatorVariableEditorType(data);
            }).not.toThrow();
        });
    });
});

describe('Package Module - startApprovalProcess', () => {
    let callback;

    beforeEach(() => {
        callback = vi.fn();
        window.parent = window;
        window.parent.componentEvent = {
            eventEmitter: { emit: vi.fn() },
            SHOW_LOADING: 'SHOW_LOADING',
            HIDE_LOADING: 'HIDE_LOADING',
        };
    });

    afterEach(() => {
        delete window.parent.componentEvent;
        delete window.parent;
        delete global.fetch;
    });

    it('GIVEN valid formData and successful response WHEN startApprovalProcess is called THEN it should call callback', async () => {
        const formData = new FormData();
        formData.append('project', 'test-project');

        const fetchPromise = Promise.resolve({
            ok: true,
            json: () => Promise.resolve({ status: true, message: 'Success' }),
        });

        global.fetch = vi.fn(() => fetchPromise);

        ACTIONS.startApprovalProcess(formData, callback);

        await fetchPromise;
        await new Promise(resolve => setTimeout(resolve, 10));

        expect(callback).toHaveBeenCalledWith({ status: true, message: 'Success' });
    });

    it('GIVEN valid formData and failed response WHEN startApprovalProcess is called THEN it should show alert', async () => {
        const mockBootbox = setupMockBootbox();

        const formData = new FormData();
        formData.append('project', 'test-project');

        const fetchPromise = Promise.resolve({
            ok: true,
            json: () => Promise.resolve({ status: false, message: 'Error occurred' }),
        });

        global.fetch = vi.fn(() => fetchPromise);

        ACTIONS.startApprovalProcess(formData, callback);

        await fetchPromise;
        await new Promise(resolve => setTimeout(resolve, 10));

        expect(mockBootbox.getLastAlertMessage()).toContain('Error occurred');

        teardownMockBootbox();
    });
});
