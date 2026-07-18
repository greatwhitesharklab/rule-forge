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

import * as ACTIONS from './action.js';
import { setupMockServer, teardownMockServer } from '../__test_utils__/mockServer.js';
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
        const action = ACTIONS.add(data as any);

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
        const action = ACTIONS.update(1, data as any);

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

    // V7.21:rl.xml → 决策流 用例已删除(BPMN 决策流入口移除)。

    // V6.20.0:DRL 规则
    it('GIVEN drl WHEN buildType is called THEN it should return DRL 规则', () => {
        expect(ACTIONS.buildType('drl')).toBe('DRL 规则');
    });

    // V6.20.0 P3:DMN / PMML 标准决策模型
    it('GIVEN dmn WHEN buildType is called THEN it should return DMN 决策表(只读)', () => {
        expect(ACTIONS.buildType('dmn')).toBe('DMN 决策表(只读)');
    });

    it('GIVEN pmml WHEN buildType is called THEN it should return PMML 模型(只读)', () => {
        expect(ACTIONS.buildType('pmml')).toBe('PMML 模型(只读)');
    });

    // V7.4/V7.5:V1 库/规则独立文件(统一 .v1xx.json 后缀,跟 v1flow.json 同约定;
    //   case 值必须与 buildData 里 OPEN_CREATE_FILE_DIALOG emit 的 fileType 一致)
    it.each([
        ['v1flow.json', 'V1 决策流'],
        ['v1lib.json', 'V1 库'],
        ['v1rs.json', 'V1 规则集'],
        ['v1dt.json', 'V1 决策表'],
        ['v1sc.json', 'V1 评分卡'],
    ])('GIVEN V1 后缀 %s WHEN buildType THEN return %s', (fileType, expected) => {
        expect(ACTIONS.buildType(fileType)).toBe(expected);
    });

    // V7.7.2:'rp' case 删除 — 老 .rp 知识包废弃

    // V6.20.0 P2:删老 urule 规则类型 — buildType 不再识别它们
    // 老入口已被 DRL/DMN/PMML 取代,前端创建菜单已无这些扩展名,buildType 抛错防误用
    it.each(['rs.xml', 'rsl.xml', 'ul', 'dt.xml', 'dts.xml', 'dtree.xml', 'sc', 'scc', 'ct.xml'])(
        'V6.20.0 P2:GIVEN 已删 type %s WHEN buildType THEN throws',
        (deletedType) => {
            const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
            expect(() => ACTIONS.buildType(deletedType)).toThrow('Unknow file type :' + deletedType);
            alertSpy.mockRestore();
        },
    );

    it('GIVEN file type with colon WHEN buildType is called THEN it should extract Type before colon', () => {
        expect(ACTIONS.buildType('drl:subtype')).toBe('DRL 规则');
        expect(ACTIONS.buildType('vl.xml:v')).toBe('变量库');
    });

    it('GIVEN unknown file type WHEN buildType is called THEN it should alert and throw error', () => {
        const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});

        expect(() => ACTIONS.buildType('unknown')).toThrow('Unknow file type :unknown');

        alertSpy.mockRestore();
    });
});

describe('Frame Module - Thunks', () => {
    let mockServer: ReturnType<typeof setupMockServer>, dispatch: ReturnType<typeof vi.fn>;

    // Default getState mock — 返回带默认 ui 过滤参数的 frame store。
    // action.ts 内的 thunk 现在通过 getState() 读 projectName/classify/types/searchFileName
    // (替代历史 window._projectName 等)。
    const getState = () => ({
        ui: {projectName: null, classify: true, types: null, searchFileName: null},
    });

    beforeEach(() => {
        mockServer = setupMockServer();
                clearModalMockState();
        dispatch = vi.fn();

        // Mock DOM
        (global.document.getElementById as any) = vi.fn(() => ({
            parentElement: {
                querySelectorAll: vi.fn(() => [])
            }
        }));
    });

    afterEach(() => {
        teardownMockServer();
        delete (global.document as any).getElementById;
    });

    // Helper to flush async chains for thunks that don't return promises
    async function flushAsync() {
        if ((mockServer.fetchMock as any).mock.results[0]) {
            await (mockServer.fetchMock as any).mock.results[0].value;
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
        thunk(dispatch, getState);

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
        thunk(dispatch, getState);

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
        const thunk = ACTIONS.loadChildren(parentNodeData as any, true, 'test', 'all');
        thunk(dispatch, getState);

        await flushAsync();

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({
                type: ACTIONS.LOAD_CHILDREN_END
            })
        );
    });

    it('GIVEN newFileName and fileType WHEN createNewFile thunk is dispatched THEN it should post to /frame/createFile and dispatch loadData thunk', async () => {
        const newFileInfo = { id: 'file1', type: 'rule' };
        mockServer.mockResponse('/frame/createFile', newFileInfo);

        const parentNodeData = { fullPath: '/test', name: 'test', type: 'folder' };
        const thunk = ACTIONS.createNewFile('newfile', 'rs.xml', parentNodeData as any);
        thunk(dispatch, getState);

        await flushAsync();

        // createNewFile 在 formPost 成功后 dispatch loadData(...) (一个 thunk 函数)
        // 来刷新文件树,而不是直接 dispatch CREATE_NEW_FILE action。
        expect(dispatch).toHaveBeenCalledWith(expect.any(Function));
    });

    // B1 止血:V1 新建文件必须用 .v1xx.json 后缀(后端 FileTypeUtils 按后缀归类,
    //   裸类型名后缀 lib1.V1Library 会导致新文件在文件树里不可见)
    it.each([
        ['v1lib.json', 'V1Library'],
        ['v1rs.json', 'V1RuleSet'],
        ['v1dt.json', 'V1DecisionTable'],
        ['v1sc.json', 'V1ScoreCard'],
    ])('GIVEN V1 fileType %s WHEN createNewFile THEN 文件名带该后缀且服务端 type 为 %s', async (fileType, serverType) => {
        mockServer.mockResponse('/frame/createFile', {});

        const parentNodeData = { fullPath: '/test', name: 'test', type: 'folder' };
        const thunk = ACTIONS.createNewFile('f1', fileType, parentNodeData as any);
        thunk(dispatch, getState);

        await flushAsync();

        const lastCall = (mockServer.fetchMock as any).mock.calls.find(
            (c: unknown[]) => typeof c[0] === 'string' && (c[0] as string).includes('/frame/createFile'),
        );
        expect(lastCall).toBeTruthy();
        const body = decodeURIComponent(String((lastCall[1] as RequestInit).body || ''));
        expect(body).toContain('path=/test/f1.' + fileType);
        expect(body).toContain('type=' + serverType);
    });

    // B1 止血:V1 分类右键菜单回归 — 5 个 V1 容器菜单每项必须有非空 label(name)
    it.each([
        ['v1flowLib', '添加 V1 决策流'],
        ['v1libraryLib', '添加 V1 库'],
        ['v1rulesetLib', '添加 V1 规则集'],
        ['v1decisiontableLib', '添加 V1 决策表'],
        ['v1scorecardLib', '添加 V1 评分卡'],
    ])('GIVEN %s 节点 WHEN loadData THEN 右键菜单两项且 label 非空', async (libType, addLabel) => {
        const libNode = {
            id: 'lib1', name: 'V1分类', type: libType, fullPath: '/test/v1',
            children: [],
        };
        // loadData 会按 projectName 剥掉 root+项目两层,直接展示项目子节点
        const projectNode = {
            id: 'proj', name: 'test', type: 'project', fullPath: '/test',
            children: [libNode],
        };
        const rootFile = {
            id: 'root', name: 'root', type: 'root', fullPath: '/',
            children: [projectNode],
        };
        const publicResource = {
            id: 'public', name: 'public', type: 'publicResource', fullPath: '/public',
            children: [],
        };
        mockServer.mockResponse('/frame/loadProjects', {
            classify: true,
            repo: { rootFile, publicResource, projectNames: [] },
            user: { import: true, export: true },
        });

        const thunk = ACTIONS.loadData(true, 'test', 'all', '');
        thunk(dispatch, getState);

        await flushAsync();

        const loadEnd = dispatch.mock.calls.find(
            (c: unknown[]) => (c[0] as { type?: string }).type === ACTIONS.LOAD_END,
        );
        expect(loadEnd).toBeTruthy();
        const built = (loadEnd[0] as { data: { children: { contextMenu?: { name: string }[] }[] } }).data.children[0];
        const names = (built.contextMenu || []).map((item) => item.name);
        expect(names).toContain('添加目录');
        expect(names).toContain(addLabel);
        names.forEach((name) => expect(name).not.toBe(''));
    });

    it('GIVEN newProjectName WHEN createNewProject thunk is dispatched THEN it should post to /frame/createProject and dispatch loadData thunk', async () => {
        const newProjectData = { id: 'proj1', name: 'NewProject', type: 'project' };
        mockServer.mockResponse('/frame/createProject', newProjectData);

        const thunk = ACTIONS.createNewProject('NewProject');
        thunk(dispatch);

        await flushAsync();

        // createNewProject dispatch 的是 loadData thunk,不是 CREATE_NEW_PROJECT action
        expect(dispatch).toHaveBeenCalledWith(expect.any(Function));
    });

    it('GIVEN newFolderName WHEN createNewFolder thunk is dispatched THEN it should post to /frame/createFolder and dispatch loadData thunk', async () => {
        const folderData = { id: 'folder1', type: 'folder' };
        mockServer.mockResponse('/frame/createFolder', folderData);

        const parentNodeData = { fullPath: '/test', name: 'test', type: 'folder' };
        const thunk = ACTIONS.createNewFolder('newfolder', parentNodeData as any);
        thunk(dispatch, getState);

        await flushAsync();

        // createNewFolder dispatch 的是 loadData thunk,不是 CREATE_NEW_FILE action
        expect(dispatch).toHaveBeenCalledWith(expect.any(Function));
    });

    it('GIVEN path and newPath WHEN rename thunk is dispatched THEN it should rename and dispatch LOAD_END', async () => {
        const rootFile = { id: 'root', name: 'root', type: 'root' };
        mockServer.mockResponse('/frame/fileRename', { repo: { rootFile } });

        const thunk = ACTIONS.rename('/old/path', '/new/path');
        thunk(dispatch, getState);

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
        const thunk = ACTIONS.fileRename(itemData as any, 'new.xml');
        thunk(dispatch, getState);

        await flushAsync();

        expect(dispatch).toHaveBeenCalled();
    });

    it('GIVEN data with fullPath WHEN seeFileSource thunk is dispatched THEN it should fetch file source', async () => {
        mockServer.mockResponse('/frame/fileSource', { content: 'source content' });

        const data = { fullPath: '/test/file.xml', name: 'file.xml' };
        // V5.74.3:seeFileSource 是 thunk,需 dispatch 触发(getState 读 currentGitTag)
        const thunk = ACTIONS.seeFileSource(data as any);
        thunk(dispatch, getState);

        await flushAsync();

        const { eventEmitter } = await import('./event.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith(
            'open_source_dialog',
            '/test/file.xml',
            'source content'
        );
    });

    it('GIVEN data with fullPath and currentGitTag in state WHEN seeFileSource thunk is dispatched THEN it should pass gitTag as request param', async () => {
        mockServer.mockResponse('/frame/fileSource', { content: 'tagged content' });

        const data = { fullPath: '/test/file.xml', name: 'file.xml' };
        const getStateWithTag = () => ({
            ui: {projectName: null, classify: true, types: null, searchFileName: null, currentGitTag: 'v1.2.3'},
        });
        const thunk = ACTIONS.seeFileSource(data as any);
        thunk(dispatch, getStateWithTag);

        await flushAsync();

        const lastCall = (mockServer.fetchMock as any).mock.calls.find(
            (c: unknown[]) => typeof c[0] === 'string' && (c[0] as string).includes('/frame/fileSource'),
        );
        expect(lastCall).toBeTruthy();
        // formPost 用 URLSearchParams 序列化,body 是字符串 "path=...&gitTag=v1.2.3"
        const opts = lastCall[1] as RequestInit;
        const body = String(opts.body || '');
        expect(body).toContain('gitTag=v1.2.3');
        expect(body).toContain('path=%2Ftest%2Ffile.xml');
    });
});

describe('Frame Module - Helper Functions', () => {
    let mockServer: ReturnType<typeof setupMockServer>;

    beforeEach(() => {
        mockServer = setupMockServer();
    });

    afterEach(() => {
        teardownMockServer();
    });

    // Helper to flush async chains
    async function flushAsync() {
        if ((mockServer.fetchMock as any).mock.results[0]) {
            await (mockServer.fetchMock as any).mock.results[0].value;
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
        expect(getLastAlertMessage()).toBe('锁定成功!');
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
        expect(getLastAlertMessage()).toBe('解锁成功!');
    });

    it('GIVEN file and content WHEN saveFileSource is called THEN it should save the file source', async () => {
        mockServer.mockResponse('/common/saveFile', {});

        ACTIONS.saveFileSource('/test/file.xml', 'test content');

        await flushAsync();

        expect(getLastAlertMessage()).toBe('保存成功!');
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
        ACTIONS.seeFileVersions(data as any);

        await flushAsync();

        const { eventEmitter } = await import('./event.js');
        expect(eventEmitter.emit).toHaveBeenCalledWith(
            'open_file_version_dialog',
            { files, data, num: 2 }
        );
    });
});
