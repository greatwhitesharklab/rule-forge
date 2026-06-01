import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import reducer from '../reducer.js';
import * as ACTIONS from '../action.js';
import { PackageState } from '../reducer.js';
import { setupMockBootbox, teardownMockBootbox } from '../../__test_utils__/mockBootbox.js';

describe('Package Module - Combined Reducer', () => {
    beforeEach(() => {
        setupMockBootbox();
        // Mock window.parent.componentEvent to avoid unhandled rejections
        (window as any).parent = {
            componentEvent: {
                eventEmitter: {
                    emit: vi.fn(),
                },
                SHOW_LOADING: 'SHOW_LOADING',
                HIDE_LOADING: 'HIDE_LOADING',
            },
        };
    });

    afterEach(() => {
        teardownMockBootbox();
        delete (window as any).parent;
    });
    const initialState: PackageState = {
        master: {},
        slave: {},
        config: {},
    };

    it('GIVEN an initial state WHEN LOAD_MASTER_COMPLETED action is dispatched THEN it should set master data', () => {
        const masterData = [
            { id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() },
            { id: 'pkg2', name: 'Package 2', resourceItems: [], createDate: new Date() },
        ];
        const action = { type: ACTIONS.LOAD_MASTER_COMPLETED, masterData };
        const newState = reducer(initialState, action as any);

        expect(newState.master.data).toEqual(masterData);
        expect(newState.master.data).toHaveLength(2);
    });

    it('GIVEN an existing state WHEN ADD_MASTER action is dispatched with valid data THEN it should add new package', () => {
        const existingState: PackageState = {
            master: {
                data: [{ id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() }],
            },
            slave: {},
            config: {},
        };
        const newPackage = { id: 'pkg2', name: 'Package 2' };
        const action = { type: ACTIONS.ADD_MASTER, data: newPackage };
        const newState = reducer(existingState, action as any);

        expect(newState.master.data).toHaveLength(2);
        expect(newState.master.data![1].id).toBe('pkg2');
        expect(newState.master.data![1].name).toBe('Package 2');
        expect(newState.master.data![1].resourceItems).toEqual([]);
        expect(newState.master.data![1].createDate).toBeInstanceOf(Date);
    });

    it('GIVEN an existing state WHEN ADD_MASTER action is dispatched with duplicate id THEN it should show alert and return unchanged state', () => {
        const existingState: PackageState = {
            master: {
                data: [{ id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() }],
            },
            slave: {},
            config: {},
        };
        const duplicatePackage = { id: 'pkg1', name: 'Package 1 Duplicate' };
        const action = { type: ACTIONS.ADD_MASTER, data: duplicatePackage };
        const newState = reducer(existingState, action as any);

        expect(window.bootbox.alert).toHaveBeenCalledWith('当前包采用的编码已存在，添加失败.');
        expect(newState).toEqual(existingState);
    });

    it('GIVEN an existing state WHEN DEL_MASTER action is dispatched THEN it should remove package at specified index', () => {
        const existingState: PackageState = {
            master: {
                data: [
                    { id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() },
                    { id: 'pkg2', name: 'Package 2', resourceItems: [], createDate: new Date() },
                    { id: 'pkg3', name: 'Package 3', resourceItems: [], createDate: new Date() },
                ],
            },
            slave: {},
            config: {},
        };
        const action = { type: ACTIONS.DEL_MASTER, rowIndex: 1 };
        const newState = reducer(existingState, action as any);

        expect(newState.master.data).toHaveLength(2);
        expect(newState.master.data![0].id).toBe('pkg1');
        expect(newState.master.data![1].id).toBe('pkg3');
    });

    it('GIVEN an existing state WHEN UPDATE_MASTER action is dispatched THEN it should update package name', () => {
        const existingState: PackageState = {
            master: {
                data: [
                    { id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() },
                    { id: 'pkg2', name: 'Package 2', resourceItems: [], createDate: new Date() },
                ],
            },
            slave: {},
            config: {},
        };
        const updateData = { rowIndex: 0, packageName: 'Updated Package 1' };
        const action = { type: ACTIONS.UPDATE_MASTER, data: updateData };
        const newState = reducer(existingState, action as any);

        expect(newState.master.data![0].name).toBe('Updated Package 1');
        expect(newState.master.data![1].name).toBe('Package 2');
    });

    it('GIVEN an existing state WHEN SAVE action is dispatched THEN it should call saveData and return unchanged state', () => {
        const state: PackageState = {
            master: {
                data: [{ id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() }],
            },
            slave: {},
            config: {},
        };
        const saveDataSpy = vi.spyOn(ACTIONS, 'saveData');
        const action = {
            type: ACTIONS.SAVE,
            newVersion: true,
            project: 'test-project',
            associatedFiles: [],
            versionComment: 'Test comment',
            packageId: 'pkg1',
            callback: vi.fn(),
        };
        const newState = reducer(state, action as any);

        expect(saveDataSpy).toHaveBeenCalledWith(
            state.master.data,
            true,
            'test-project',
            [],
            'Test comment',
            'pkg1',
            action.callback
        );
        expect(newState).toEqual(state);
    });

    it('GIVEN an existing state WHEN APPLY action is dispatched THEN it should call applyNewVersion and return unchanged state', () => {
        const state: PackageState = {
            master: {
                data: [{ id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() }],
            },
            slave: {},
            config: {},
        };
        const applySpy = vi.spyOn(ACTIONS, 'applyNewVersion');
        const action = {
            type: ACTIONS.APPLY,
            project: 'test-project',
            packageConfig: {},
            currentPackage: { id: 'pkg1' },
        };
        const newState = reducer(state, action as any);

        expect(applySpy).toHaveBeenCalledWith(state.master.data, 'test-project', {}, { id: 'pkg1' });
        expect(newState).toEqual(state);
    });

    it('GIVEN an initial state WHEN LOAD_SLAVE_COMPLETE action is dispatched THEN it should set slave data', () => {
        const masterRowData = {
            id: 'pkg1',
            name: 'Package 1',
            resourceItems: [
                { name: 'file1.xml', path: '/path/to/file1.xml', version: '1.0' },
                { name: 'file2.xml', path: '/path/to/file2.xml', version: '1.0' },
            ],
            createDate: new Date(),
        };
        const action = { type: ACTIONS.LOAD_SLAVE_COMPLETE, masterRowData };
        const newState = reducer(initialState, action as any);

        expect(newState.slave.data).toEqual(masterRowData);
        expect(newState.slave.data!.resourceItems).toHaveLength(2);
    });

    it('GIVEN an existing slave state WHEN DEL_SLAVE action is dispatched THEN it should remove resource item at specified index', () => {
        const existingState: PackageState = {
            master: {},
            slave: {
                data: {
                    id: 'pkg1',
                    name: 'Package 1',
                    resourceItems: [
                        { name: 'file1.xml', path: '/path/to/file1.xml', version: '1.0' },
                        { name: 'file2.xml', path: '/path/to/file2.xml', version: '1.0' },
                        { name: 'file3.xml', path: '/path/to/file3.xml', version: '1.0' },
                    ],
                    createDate: new Date(),
                } as any,
            },
            config: {},
        };
        const action = { type: ACTIONS.DEL_SLAVE, rowIndex: 1 };
        const newState = reducer(existingState, action as any);

        expect(newState.slave.data!.resourceItems).toHaveLength(2);
        expect(newState.slave.data!.resourceItems[0].name).toBe('file1.xml');
        expect(newState.slave.data!.resourceItems[1].name).toBe('file3.xml');
    });

    it('GIVEN an existing slave state WHEN ADD_SLAVE action is dispatched THEN it should add new resource item', () => {
        const existingState: PackageState = {
            master: {},
            slave: {
                data: {
                    id: 'pkg1',
                    name: 'Package 1',
                    resourceItems: [{ name: 'file1.xml', path: '/path/to/file1.xml', version: '1.0' }],
                    createDate: new Date(),
                } as any,
            },
            config: {},
        };
        const newResource = { name: 'file2.xml', path: '/path/to/file2.xml', version: '2.0' };
        const action = { type: ACTIONS.ADD_SLAVE, data: newResource };
        const newState = reducer(existingState, action as any);

        expect(newState.slave.data!.resourceItems).toHaveLength(2);
        expect(newState.slave.data!.resourceItems[1]).toEqual(newResource);
    });

    it('GIVEN an existing slave state WHEN UPDATE_SLAVE action is dispatched THEN it should update resource item at specified index', () => {
        const existingState: PackageState = {
            master: {},
            slave: {
                data: {
                    id: 'pkg1',
                    name: 'Package 1',
                    resourceItems: [
                        { name: 'file1.xml', path: '/path/to/file1.xml', version: '1.0' },
                        { name: 'file2.xml', path: '/path/to/file2.xml', version: '1.0' },
                    ],
                    createDate: new Date(),
                } as any,
            },
            config: {},
        };
        const updateData = {
            rowIndex: 1,
            name: 'updated-file2.xml',
            path: '/new/path/to/file2.xml',
            version: '2.0',
        };
        const action = { type: ACTIONS.UPDATE_SLAVE, data: updateData };
        const newState = reducer(existingState, action as any);

        expect(newState.slave.data!.resourceItems[1].name).toBe('updated-file2.xml');
        expect(newState.slave.data!.resourceItems[1].path).toBe('/new/path/to/file2.xml');
        expect(newState.slave.data!.resourceItems[1].version).toBe('2.0');
    });

    it('GIVEN an initial state WHEN LOAD_PACKAGE_CONFIG_COMPLETE action is dispatched THEN it should set config data', () => {
        const config = {
            project: 'test-project',
            version: '1.0',
            description: 'Test configuration',
        };
        const action = { type: ACTIONS.LOAD_PACKAGE_CONFIG_COMPLETE, config };
        const newState = reducer(initialState, action as any);

        expect(newState.config.data).toEqual(config);
        expect(newState.config.data!.project).toBe('test-project');
    });

    it('GIVEN an initial state WHEN multiple actions are dispatched THEN it should maintain separate state slices', () => {
        let state = reducer(initialState, {
            type: ACTIONS.LOAD_MASTER_COMPLETED,
            masterData: [{ id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() }],
        } as any);

        state = reducer(state, {
            type: ACTIONS.LOAD_SLAVE_COMPLETE,
            masterRowData: { id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: new Date() },
        } as any);

        state = reducer(state, {
            type: ACTIONS.LOAD_PACKAGE_CONFIG_COMPLETE,
            config: { project: 'test-project' },
        } as any);

        expect(state.master.data).toEqual([{ id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: expect.any(Date) }]);
        expect(state.slave.data).toEqual({ id: 'pkg1', name: 'Package 1', resourceItems: [], createDate: expect.any(Date) });
        expect(state.config.data).toEqual({ project: 'test-project' });
    });

    it('GIVEN a state WHEN unknown action is dispatched THEN it should return the same state', () => {
        const state: PackageState = {
            master: { data: [] },
            slave: { data: {} as any },
            config: { data: {} as any },
        };
        const action = { type: 'UNKNOWN_ACTION' };
        const newState = reducer(state, action as any);

        expect(newState).toEqual(state);
    });
});
