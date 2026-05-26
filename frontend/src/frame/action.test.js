import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as ACTIONS from './action.js';
import { setupMockServer, teardownMockServer } from '../__test_utils__/mockServer.js';
import { setupMockBootbox, teardownMockBootbox } from '../__test_utils__/mockBootbox.js';

// Mock componentEvent and event modules used by action.js
vi.mock('../components/componentEvent.js', () => ({
    eventEmitter: { emit: vi.fn() },
    SHOW_LOADING: 'SHOW_LOADING',
    HIDE_LOADING: 'HIDE_LOADING',
    TREE_NODE_CLICK: 'tree_node_click',
}));

vi.mock('./event.js', () => ({
    eventEmitter: { emit: vi.fn() },
    CHANGE_CLASSIFY: 'change_classify',
    PROJECT_LIST_CHANGE: 'project_list_change',
    HIDE_RENAME_DIALOG: 'HIDE_RENAME_DIALOG',
    CLOSE_CREATE_FILE_DIALOG: 'close_file_dialog',
    CLOSE_NEW_PROJECT_DIALOG: 'close_new_project_dialog',
    CLOSE_CREATE_FOLDER_DIALOG: 'close_folder_dialog',
    CLOSE_UPDATE_PROJECT_DIALOG: 'CLOSE_UPDATE_PROJECT_DIALOG',
    EXPAND_TREE_NODE: 'expand_tree_node',
    OPEN_SOURCE_DIALOG: 'open_source_dialog',
    OPEN_FILE_VERSION_DIALOG: 'open_file_version_dialog',
}));

vi.mock('../Styles.js', () => ({
    default: {
        frameStyle: {
            getRootIcon: () => 'root-icon',
            getRootIconStyle: () => 'root-style',
            getRuleIcon: () => 'rule-icon',
            getRuleIconStyle: () => 'rule-style',
            getProjectIcon: () => 'project-icon',
            getProjectIconStyle: () => 'project-style',
            getResourceIcon: () => 'resource-icon',
            getResourceIconStyle: () => 'resource-style',
            getFolderIcon: () => 'folder-icon',
            getFolderIconStyle: () => 'folder-style',
            getResourcePackageIcon: () => 'package-icon',
            getResourcePackageIconStyle: () => 'package-style',
            getLibIcon: () => 'lib-icon',
            getLibIconStyle: () => 'lib-style',
            getActionIcon: () => 'action-icon',
            getActionIconStyle: () => 'action-style',
            getParameterIcon: () => 'param-icon',
            getParameterIconStyle: () => 'param-style',
            getConstantIcon: () => 'const-icon',
            getConstantIconStyle: () => 'const-style',
            getVariableIcon: () => 'var-icon',
            getVariableIconStyle: () => 'var-style',
            getRuleLibIcon: () => 'rulelib-icon',
            getRuleLibIconStyle: () => 'rulelib-style',
            getUlIcon: () => 'ul-icon',
            getUlIconStyle: () => 'ul-style',
            getDecisionTableIcon: () => 'dt-icon',
            getDecisionTableIconStyle: () => 'dt-style',
            getDecisionTableLibIcon: () => 'dtlib-icon',
            getDecisionTableLibIconStyle: () => 'dtlib-style',
            getCrossDecisionTableIcon: () => 'ct-icon',
            getCrossDecisionTableIconStyle: () => 'ct-style',
            getDecisionTreeIcon: () => 'tree-icon',
            getDecisionTreeIconStyle: () => 'tree-style',
            getDecisionTreeLibIcon: () => 'treelib-icon',
            getDecisionTreeLibIconStyle: () => 'treelib-style',
            getFlowIcon: () => 'flow-icon',
            getFlowIconStyle: () => 'flow-style',
            getFlowLibIcon: () => 'flowlib-icon',
            getFlowLibIconStyle: () => 'flowlib-style',
            getScorecardIcon: () => 'sc-icon',
            getScorecardIconStyle: () => 'sc-style',
            getScorecardLibIcon: () => 'sclib-icon',
            getScorecardLibIconStyle: () => 'sclib-style',
            getComplexScorecardIcon: () => 'csc-icon',
            getComplexScorecardIconStyle: () => 'csc-style',
            getScriptDecisionTableIcon: () => 'sdt-icon',
            getScriptDecisionTableIconStyle: () => 'sdt-style',
        },
    },
}));

describe('Frame Module - Action Creators', () => {
    it('GIVEN data WHEN add action is created THEN it should return ADD action with correct payload', () => {
        const data = { id: 1, name: 'test' };
        const action = ACTIONS.add(data);

        expect(action.type).toBe(ACTIONS.ADD);
        expect(action.data).toEqual(data);
    });

    it('GIVEN index WHEN del action is created THEN it should return DEL action with correct index', () => {
        const action = ACTIONS.del(2);

        expect(action.type).toBe(ACTIONS.DEL);
        expect(action.index).toBe(2);
    });

    it('GIVEN index and data WHEN update action is created THEN it should return UPDATE action with correct payload', () => {
        const data = { name: 'updated' };
        const action = ACTIONS.update(1, data);

        expect(action.type).toBe(ACTIONS.UPDATE);
        expect(action.index).toBe(1);
        expect(action.data).toEqual(data);
    });
});

describe('Frame Module - buildType Pure Function', () => {
    it('GIVEN vl.xml WHEN buildType is called THEN it should return 变量库', () => {
        expect(ACTIONS.buildType('vl.xml')).toBe('变量库');
    });

    it('GIVEN cl.xml WHEN buildType is called THEN it should return 常量库', () => {
        expect(ACTIONS.buildType('cl.xml')).toBe('常量库');
    });

    it('GIVEN pl.xml WHEN buildType is called THEN it should return 参数库', () => {
        expect(ACTIONS.buildType('pl.xml')).toBe('参数库');
    });

    it('GIVEN al.xml WHEN buildType is called THEN it should return 动作库', () => {
        expect(ACTIONS.buildType('al.xml')).toBe('动作库');
    });

    it('GIVEN rs.xml WHEN buildType is called THEN it should return 向导式决策集', () => {
        expect(ACTIONS.buildType('rs.xml')).toBe('向导式决策集');
    });

    it('GIVEN rsl.xml WHEN buildType is called THEN it should return 向导式决策库', () => {
        expect(ACTIONS.buildType('rsl.xml')).toBe('向导式决策库');
    });

    it('GIVEN ul WHEN buildType is called THEN it should return 脚本式决策集', () => {
        expect(ACTIONS.buildType('ul')).toBe('脚本式决策集');
    });

    it('GIVEN dt.xml WHEN buildType is called THEN it should return 决策表', () => {
        expect(ACTIONS.buildType('dt.xml')).toBe('决策表');
    });

    it('GIVEN dts.xml WHEN buildType is called THEN it should return 脚本式决策表', () => {
        expect(ACTIONS.buildType('dts.xml')).toBe('脚本式决策表');
    });

    it('GIVEN rl.xml WHEN buildType is called THEN it should return 决策流', () => {
        expect(ACTIONS.buildType('rl.xml')).toBe('决策流');
    });

    it('GIVEN dtree.xml WHEN buildType is called THEN it should return 决策树', () => {
        expect(ACTIONS.buildType('dtree.xml')).toBe('决策树');
    });

    it('GIVEN sc WHEN buildType is called THEN it should return 评分卡', () => {
        expect(ACTIONS.buildType('sc')).toBe('评分卡');
    });

    it('GIVEN scc WHEN buildType is called THEN it should return 复杂评分卡', () => {
        expect(ACTIONS.buildType('scc')).toBe('复杂评分卡');
    });

    it('GIVEN ct.xml WHEN buildType is called THEN it should return 交叉决策表', () => {
        expect(ACTIONS.buildType('ct.xml')).toBe('交叉决策表');
    });

    it('GIVEN rp WHEN buildType is called THEN it should return package', () => {
        expect(ACTIONS.buildType('rp')).toBe('package');
    });

    it('GIVEN file type with colon WHEN buildType is called THEN it should extract Type before colon', () => {
        expect(ACTIONS.buildType('rs.xml:subtype')).toBe('向导式决策集');
        expect(ACTIONS.buildType('ul:variant')).toBe('脚本式决策集');
    });

    it('GIVEN unknown file type WHEN buildType is called THEN it should alert and throw error', () => {
        const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});

        expect(() => ACTIONS.buildType('unknown')).toThrow('Unknow file type :unknown');

        alertSpy.mockRestore();
    });
});

describe('Frame Module - Thunks', () => {
    let mockServer, dispatch, mockBootbox;

    beforeEach(() => {
        mockServer = setupMockServer();
        mockBootbox = setupMockBootbox();
        dispatch = vi.fn();

        // Mock DOM
        global.document.getElementById = vi.fn(() => ({
            parentElement: {
                querySelectorAll: vi.fn(() => [])
            }
        }));
    });

    afterEach(() => {
        teardownMockServer();
        teardownMockBootbox();
        delete global.document.getElementById;
    });

    // Helper to flush async chains for thunks that don't return promises
    async function flushAsync() {
        if (mockServer.fetchMock.mock.results[0]) {
            await mockServer.fetchMock.mock.results[0].value;
        }
        await new Promise(resolve => setTimeout(resolve, 0));
    }

    it('GIVEN valid params WHEN loadData thunk is dispatched THEN it should fetch and dispatch LOAD_END', async () => {
        const rootFile = {
            id: 'root',
            name: 'root',
            type: 'root',
            children: []
        };
        const publicResource = {
            id: 'public',
            name: 'public',
            type: 'publicResource',
            children: []
        };

        mockServer.mockResponse('/frame/loadProjects', {
            classify: true,
            repo: { rootFile, publicResource, projectNames: [] },
            user: { import: true, export: true }
        });

        const thunk = ACTIONS.loadData(true, 'test', 'all', '');
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({
                type: ACTIONS.LOAD_END,
                data: expect.any(Object)
            })
        );
    });

    it('GIVEN server error WHEN loadData thunk is dispatched THEN it should handle error', async () => {
        mockServer.mockError('/frame/loadProjects', 500);

        const thunk = ACTIONS.loadData(true, 'test', 'all', '');
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: ACTIONS.LOAD_END })
        );
    });

    it('GIVEN parentNodeData and classify WHEN loadChildren thunk is dispatched THEN it should fetch children', async () => {
        const childrenData = [
            { id: 'child1', name: 'Child 1', type: 'rule' },
            { id: 'child2', name: 'Child 2', type: 'rule' }
        ];

        mockServer.mockResponse('/frame/loadProjects', {
            repo: { rootFile: { children: childrenData } }
        });

        const parentNodeData = { fullPath: '/', name: 'root', type: 'root', _level: 0 };
        const thunk = ACTIONS.loadChildren(parentNodeData, true, 'test', 'all');
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({
                type: ACTIONS.LOAD_CHILDREN_END
            })
        );
    });

    it('GIVEN newFileName and fileType WHEN createNewFile thunk is dispatched THEN it should create file and dispatch CREATE_NEW_FILE', async () => {
        const newFileInfo = { id: 'file1', type: 'rule' };
        mockServer.mockResponse('/frame/createFile', newFileInfo);

        const parentNodeData = { fullPath: '/test', name: 'test', type: 'folder' };
        const thunk = ACTIONS.createNewFile('newfile', 'rs.xml', parentNodeData);
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({
                type: ACTIONS.CREATE_NEW_FILE
            })
        );
    });

    it('GIVEN newProjectName WHEN createNewProject thunk is dispatched THEN it should create project and dispatch CREATE_NEW_PROJECT', async () => {
        const newProjectData = { id: 'proj1', name: 'NewProject', type: 'project' };
        mockServer.mockResponse('/frame/createProject', newProjectData);

        const parentNodeData = { fullPath: '/', name: 'root', type: 'root' };
        const thunk = ACTIONS.createNewProject('NewProject', parentNodeData);
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({
                type: ACTIONS.CREATE_NEW_PROJECT
            })
        );
    });

    it('GIVEN newFolderName WHEN createNewFolder thunk is dispatched THEN it should create folder and dispatch CREATE_NEW_FILE', async () => {
        const folderData = { id: 'folder1', type: 'folder' };
        mockServer.mockResponse('/frame/createFolder', folderData);

        const parentNodeData = { fullPath: '/test', name: 'test', type: 'folder' };
        const thunk = ACTIONS.createNewFolder('newfolder', parentNodeData);
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({
                type: ACTIONS.CREATE_NEW_FILE
            })
        );
    });

    it('GIVEN path and newPath WHEN rename thunk is dispatched THEN it should rename and dispatch LOAD_END', async () => {
        const rootFile = { id: 'root', name: 'root', type: 'root' };
        mockServer.mockResponse('/frame/fileRename', { repo: { rootFile } });

        const thunk = ACTIONS.rename('/old/path', '/new/path');
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({
                type: ACTIONS.LOAD_END
            })
        );
    });

    it('GIVEN itemData and newName WHEN fileRename thunk is dispatched THEN it should rename file', async () => {
        const rootFile = { id: 'root', name: 'root', type: 'root' };
        mockServer.mockResponse('/frame/fileRename', { repo: { rootFile } });

        const itemData = { fullPath: '/old/path.xml', name: 'path.xml' };
        const thunk = ACTIONS.fileRename(itemData, 'new.xml');
        thunk(dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalled();
    });
});

describe('Frame Module - Helper Functions', () => {
    let mockServer, mockBootbox;

    beforeEach(() => {
        mockServer = setupMockServer();
        mockBootbox = setupMockBootbox();
    });

    afterEach(() => {
        teardownMockServer();
        teardownMockBootbox();
    });

    // Helper to flush async chains
    async function flushAsync() {
        if (mockServer.fetchMock.mock.results[0]) {
            await mockServer.fetchMock.mock.results[0].value;
        }
        await new Promise(resolve => setTimeout(resolve, 0));
    }

    it('GIVEN file path WHEN lockFile is called THEN it should lock the file', async () => {
        const rootFile = { id: 'root', name: 'root', type: 'root' };
        mockServer.mockResponse('/frame/lockFile', { repo: { rootFile } });

        const dispatch = vi.fn();
        ACTIONS.lockFile('/test/file.xml', dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({
                type: ACTIONS.LOAD_END
            })
        );
        expect(mockBootbox.getLastAlertMessage()).toBe('锁定成功!');
    });

    it('GIVEN file path WHEN unlockFile is called THEN it should unlock the file', async () => {
        const rootFile = { id: 'root', name: 'root', type: 'root' };
        mockServer.mockResponse('/frame/unlockFile', { repo: { rootFile } });

        const dispatch = vi.fn();
        ACTIONS.unlockFile('/test/file.xml', dispatch);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({
                type: ACTIONS.LOAD_END
            })
        );
        expect(mockBootbox.getLastAlertMessage()).toBe('解锁成功!');
    });

    it('GIVEN file and content WHEN saveFileSource is called THEN it should save the file source', async () => {
        mockServer.mockResponse('/common/saveFile', {});

        ACTIONS.saveFileSource('/test/file.xml', 'test content');

        await flushAsync();

        expect(mockBootbox.getLastAlertMessage()).toBe('保存成功!');
    });

    it('GIVEN data with fullPath WHEN seeFileSource is called THEN it should fetch file source', async () => {
        mockServer.mockResponse('/frame/fileSource', { content: 'source content' });

        const data = { fullPath: '/test/file.xml', name: 'file.xml' };
        ACTIONS.seeFileSource(data);

        await flushAsync();

        const { eventEmitter } = await import('./event.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith(
            'open_source_dialog',
            '/test/file.xml',
            'source content'
        );
    });

    it('GIVEN data with fullPath and rpp WHEN seeFileVersions is called THEN it should fetch file versions', async () => {
        const files = [
            { version: 1, date: '2023-01-01' },
            { version: 2, date: '2023-01-02' }
        ];

        mockServer.mockResponse('/frame/fileVersions', {
            files,
            count: 2
        });

        const data = { fullPath: '/test/file.xml', rpp: 'project', page: 1 };
        ACTIONS.seeFileVersions(data);

        await flushAsync();

        const { eventEmitter } = await import('./event.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith(
            'open_file_version_dialog',
            { files, data, num: 2 }
        );
    });
});
