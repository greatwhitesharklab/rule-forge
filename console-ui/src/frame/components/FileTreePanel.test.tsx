import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import {Provider} from 'react-redux';
import {createStore, applyMiddleware} from 'redux';
import thunk from 'redux-thunk';
import rootReducer from '../reducer';

/**
 * V6.13.1 — FileTreePanel unified view 测试。
 *
 * <p>合并文件树视图 + 知识包视图到一个面板,顶部加版本下拉:
 * <ul>
 *   <li>默认 = Working tree (可编辑)</li>
 *   <li>选具体版本 → Tree 进 readOnly 模式,文件 click 走 seeFileSource (看 git snapshot 源码)</li>
 *   <li>切回 Working tree → 恢复编辑模式</li>
 * </ul>
 *
 * <p>Mock 策略:
 * <ul>
 *   <li>Mock 掉真 {@code Tree} 组件,改用 stub 接收 props (readOnly / onFileReadOnlyClick) + 模拟文件 click</li>
 *   <li>Mock 掉 antd {@code Select},改用 stub 输出当前选中版本 + 触发 onChange</li>
 *   <li>{@code PackageNavigator} 不再被引用,无需 mock</li>
 *   <li>{@code seeFileSource} / {@code setCurrentGitTag} 通过 redux-thunk dispatch 真实调用,
 *       mock 掉背后的 {@code formPost} + {@code event.eventEmitter} 验证副作用</li>
 * </ul>
 */

// Mock Tree to expose the props it receives (readOnly + onFileReadOnlyClick)
// and to simulate a file click via the readOnly callback.
const {treeProps, fireReadOnlyFileClick} = vi.hoisted(() => {
    let captured: {
        readOnly?: boolean;
        onFileReadOnlyClick?: (data: TreeNodeData) => void;
    } = {};
    return {
        treeProps: () => captured,
        fireReadOnlyFileClick: (data: TreeNodeData) => captured.onFileReadOnlyClick?.(data),
    };
});

vi.mock('@/components/tree/component/Tree.jsx', () => ({
    default: (props: {readOnly?: boolean; onFileReadOnlyClick?: (data: TreeNodeData) => void; treeType?: string}) => {
        treeProps().readOnly = props.readOnly;
        treeProps().onFileReadOnlyClick = props.onFileReadOnlyClick;
        return <div data-testid="mock-tree" data-readonly={String(!!props.readOnly)} />;
    },
}));

// Mock antd Select to expose value + options and let tests trigger onChange.
const {selectProps, fireSelectChange} = vi.hoisted(() => {
    let captured: {
        value?: string;
        options?: {label: string; value: string}[];
        onChange?: (value: string) => void;
    } = {};
    return {
        selectProps: () => captured,
        fireSelectChange: (value: string) => captured.onChange?.(value),
    };
});

vi.mock('antd', async () => {
    const actual = await vi.importActual<typeof import('antd')>('antd');
    return {
        ...actual,
        Select: (props: {value?: string; options?: {label: string; value: string}[]; onChange?: (v: string) => void; placeholder?: string}) => {
            selectProps().value = props.value;
            selectProps().options = props.options;
            selectProps().onChange = props.onChange;
            return (
                <div data-testid="version-selector" data-value={props.value || ''}>
                    <span data-testid="version-selector-value">{props.value || ''}</span>
                    {props.options?.map((opt) => (
                        <button key={opt.value} data-testid={`version-option-${opt.value}`} onClick={() => props.onChange?.(opt.value)}>
                            {opt.label}
                        </button>
                    ))}
                </div>
            );
        },
    };
});

// Mock formPost used by loadPackages to feed version list.
const {formPost} = vi.hoisted(() => ({
    formPost: vi.fn(),
}));
vi.mock('@/api/client.js', () => ({
    formPost: formPost,
}));

// Mock event module to observe seeFileSource's OPEN_SOURCE_DIALOG emit.
const {eventEmitSpy} = vi.hoisted(() => ({
    eventEmitSpy: vi.fn(),
}));
vi.mock('../event.js', () => ({
    eventEmitter: {emit: eventEmitSpy, on: vi.fn(), removeAllListeners: vi.fn()},
    OPEN_FILE: 'open_file',
    OPEN_SOURCE_DIALOG: 'open_source_dialog',
    CLOSE_SOURCE_DIALOG: 'close_source_dialog',
}));

// ComponentEvent is referenced by loadChildren thunks in the tree — keep it quiet.
vi.mock('../../components/componentEvent.js', () => ({
    eventEmitter: {emit: vi.fn(), on: vi.fn(), removeAllListeners: vi.fn()},
    SHOW_LOADING: 'SHOW_LOADING',
    HIDE_LOADING: 'HIDE_LOADING',
    TREE_NODE_CLICK: 'tree_node_click',
}));

import FileTreePanel from './FileTreePanel';
import * as ACTIONS from '../action.js';

function makeStore() {
    return createStore(rootReducer, applyMiddleware(thunk));
}

function renderPanel(projectName: string | null = null) {
    const store = makeStore();
    if (projectName) {
        store.dispatch(ACTIONS.setProjectName(projectName));
    }
    return {
        store,
        ...render(
            <Provider store={store}>
                <FileTreePanel store={store as unknown as {dispatch: (a: unknown) => void; getState: () => {ui?: {projectName?: string | null}}}} />
            </Provider>
        ),
    };
}

describe('V6.13.1 — FileTreePanel unified view (Tree + version dropdown)', () => {
    beforeEach(() => {
        treeProps().readOnly = undefined;
        treeProps().onFileReadOnlyClick = undefined;
        selectProps().value = undefined;
        selectProps().options = undefined;
        selectProps().onChange = undefined;
        formPost.mockReset();
        eventEmitSpy.mockReset();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('GIVEN no project selected WHEN mounted THEN version selector shows Working tree default and Tree is editable (readOnly=false)', () => {
        formPost.mockResolvedValue({status: true, data: []});
        renderPanel(null);

        // Tree 默认不 readOnly
        expect(screen.getByTestId('mock-tree').getAttribute('data-readonly')).toBe('false');
        // Version selector 默认值 = '__working_tree__' (代表 working tree)
        expect(screen.getByTestId('version-selector-value').textContent).toBe('__working_tree__');
    });

    it('GIVEN project selected and versions loaded WHEN mounted THEN selector lists Working tree + N versions with audit status', async () => {
        // loadPackages 返回 3 个版本:90(已审批), 20(审批中), 0(待审批)
        formPost.mockResolvedValue({
            status: true,
            data: [
                {id: 'v1', name: 'v1.0', version: 'v1.0', auditStatus: 90},
                {id: 'v2', name: 'v0.9', version: 'v0.9', auditStatus: 20},
                {id: 'v3', name: 'v0.8', version: 'v0.8', auditStatus: 0},
            ],
        });
        renderPanel('myproject');

        // loadPackages 触发了 1 次
        await waitFor(() => {
            expect(formPost).toHaveBeenCalledWith('/packageeditor/loadPackages', expect.objectContaining({project: 'myproject'}));
        });

        // 等待 selector 拿到 options
        await waitFor(() => {
            const options = selectProps().options;
            expect(options).toBeDefined();
            expect(options!.length).toBe(4); // Working tree + 3 versions
        });

        const options = selectProps().options!;
        expect(options[0]).toMatchObject({label: 'Working tree', value: '__working_tree__'});
        // audit 90 = green success (label 是 React 元素 <span>name<Tag/></span>,提取 inner text 验证)
        const labelText = (o: {label: unknown}): string => {
            const el = o.label as React.ReactElement<{children?: unknown[]}>;
            if (el && el.props && Array.isArray(el.props.children)) {
                return el.props.children.map((c: unknown) =>
                    typeof c === 'string' ? c : (c as {props?: {children?: unknown}}).props?.children as string || ''
                ).join('');
            }
            return String(o.label);
        };
        expect(labelText(options[1])).toContain('已审批');
        // audit 20 = processing blue
        expect(labelText(options[2])).toContain('审批中');
        // audit 0 = default
        expect(labelText(options[3])).toContain('待审批');
    });

    it('GIVEN user picks a version WHEN version selected THEN dispatch setCurrentGitTag + Tree enters readOnly mode', async () => {
        formPost.mockResolvedValue({
            status: true,
            data: [
                {id: 'v1', name: 'v1.0', version: 'v1.0', auditStatus: 90, gitTag: 'tag-v1'},
            ],
        });
        const {store} = renderPanel('myproject');

        await waitFor(() => {
            expect(selectProps().options).toBeDefined();
        });

        // 触发选 v1
        fireSelectChange('v1');

        // Redux currentGitTag 被 setCurrentGitTag 写
        await waitFor(() => {
            expect((store.getState() as {ui: {currentGitTag: string | null}}).ui.currentGitTag).toBe('tag-v1');
        });

        // Tree 进 readOnly
        expect(screen.getByTestId('mock-tree').getAttribute('data-readonly')).toBe('true');

        // selector value 切到 v1
        expect(screen.getByTestId('version-selector-value').textContent).toBe('v1');
    });

    it('GIVEN readOnly mode and onFileReadOnlyClick exists WHEN simulated file click THEN seeFileSource dispatched (fileSource POST + emit)', async () => {
        formPost.mockResolvedValue({
            status: true,
            data: [{id: 'v1', name: 'v1.0', version: 'v1.0', auditStatus: 90, gitTag: 'tag-v1'}],
        });
        renderPanel('myproject');

        await waitFor(() => {
            expect(selectProps().options).toBeDefined();
        });

        // 切到 v1
        fireSelectChange('v1');
        await waitFor(() => {
            expect(screen.getByTestId('mock-tree').getAttribute('data-readonly')).toBe('true');
        });

        // fileSource mock 返回内容 (formPost 直接 resolve 成 response.json(),见 client.ts)
        formPost.mockResolvedValueOnce({content: 'snapshot content'});

        // 模拟 Tree 的 readOnly file click
        fireReadOnlyFileClick({fullPath: '/myproject/test.xml', name: 'test.xml'} as TreeNodeData);

        // seeFileSource thunk 应触发 /frame/fileSource 请求 + emit OPEN_SOURCE_DIALOG
        await waitFor(() => {
            const lastCall = formPost.mock.calls.find((c) => typeof c[0] === 'string' && (c[0] as string).includes('/frame/fileSource'));
            expect(lastCall).toBeTruthy();
        });
        await waitFor(() => {
            expect(eventEmitSpy).toHaveBeenCalledWith('open_source_dialog', '/myproject/test.xml', 'snapshot content');
        });
    });

    it('GIVEN in readOnly mode WHEN user switches back to Working tree THEN currentGitTag cleared and Tree becomes editable', async () => {
        formPost.mockResolvedValue({
            status: true,
            data: [{id: 'v1', name: 'v1.0', version: 'v1.0', auditStatus: 90, gitTag: 'tag-v1'}],
        });
        const {store} = renderPanel('myproject');

        await waitFor(() => {
            expect(selectProps().options).toBeDefined();
        });

        // 先切到 v1
        fireSelectChange('v1');
        await waitFor(() => {
            expect(screen.getByTestId('mock-tree').getAttribute('data-readonly')).toBe('true');
        });

        // 再切回 Working tree
        fireSelectChange('__working_tree__');
        await waitFor(() => {
            expect(screen.getByTestId('mock-tree').getAttribute('data-readonly')).toBe('false');
        });
        await waitFor(() => {
            expect((store.getState() as {ui: {currentGitTag: string | null}}).ui.currentGitTag).toBeNull();
        });
    });

    it('GIVEN project not selected WHEN loadPackages called THEN request NOT made (skip fetch)', () => {
        formPost.mockResolvedValue({status: true, data: []});
        renderPanel(null);
        // No project → no loadPackages call
        expect(formPost).not.toHaveBeenCalled();
    });
});
