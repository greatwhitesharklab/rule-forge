import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, screen} from '@testing-library/react';
import {Provider} from 'react-redux';
import {createStore} from 'redux';

// V6.13.5h:PackageEditor 死代码 `document.getElementById('container').clientWidth` 修复回归测试
// — 真实 click 流程:FileTree 点 test003.rp → window.open('/app/editor/package?file=test003.rp')
// → React Router 挂载 PackageEditorRoute → 渲染 PackageEditor
// — SPA 入口 index.html 只有 <div id="root">,没有 #container 元素
// — 死代码 .clientWidth 抛 TypeError → 整个 render 崩 → 用户看到空白页

// 链依赖重(Grid / Splitter / 10+ Dialog / KnowledgeTreeDialog / antd / iconfont),全 mock 掉只验"mount 不崩"
vi.mock('../../grid/component/Grid.tsx', () => ({
    default: () => <div data-testid="mock-grid"/>,
}));
vi.mock('../../splitter/component/Splitter.tsx', () => ({
    default: ({children}: {children: unknown}) => <div data-testid="mock-splitter">{children as never}</div>,
}));
vi.mock('./PackageDialog.jsx', () => ({default: () => null}));
vi.mock('./SimulatorPage.jsx', () => ({default: () => null}));
vi.mock('./ItemDialog.jsx', () => ({default: () => null}));
vi.mock('../../dialog/component/KnowledgeTreeDialog.jsx', () => ({default: () => null}));
vi.mock('./ReteDiagramDialog.jsx', () => ({default: () => null}));
vi.mock('./FlowDialog.jsx', () => ({default: () => null}));
vi.mock('./ImportExcelDataDialog.jsx', () => ({default: () => null}));
vi.mock('./ExportExcelDataDialog.jsx', () => ({default: () => null}));
vi.mock('./ImportExcelErrorDialog.jsx', () => ({default: () => null}));
vi.mock('./BatchTestDialog.jsx', () => ({default: () => null}));
vi.mock('../../grid/component/ChildListDialog.tsx', () => ({default: () => null}));
vi.mock('./VersionsDialog.jsx', () => ({default: () => null}));

// mock PackageEditor 父组件需要的 child dialogs/event/utils
vi.mock('@/utils/modal', () => ({
    alert: vi.fn(),
    confirm: vi.fn(),
}));

// mock event emitter(避免 requireActual 拉一坨)
vi.mock('../event.js', () => ({
    eventEmitter: {emit: vi.fn()},
    OPEN_CREATE_PACKAGE_DIALOG: 'OPEN_CREATE_PACKAGE_DIALOG',
    OPEN_CREATE_PACKAGE_ITEM_DIALOG: 'OPEN_CREATE_PACKAGE_ITEM_DIALOG',
    OPEN_VERSION_DIALOG: 'OPEN_VERSION_DIALOG',
    OPEN_SIMULATOR_DIALOG: 'OPEN_SIMULATOR_DIALOG',
}));

// action.js 在 mount 阶段不会被调(SPA mount 时 store 是空的,PackageEditor 不会主动 dispatch),
// 但 require 引用必须给 stub,否则 import 链断
vi.mock('../action.js', () => ({
    loadSlaveData: vi.fn(),
    save: vi.fn(),
    apply: vi.fn(),
    refreshKnowledgeCache: vi.fn(),
    deleteMaster: vi.fn(),
    deleteSlave: vi.fn(),
}));

import PackageEditor from './PackageEditor';

function makeStore() {
    return createStore(() => ({
        master: {data: [] as never[]},
        slave: {data: {} as never},
        config: {data: {} as never},
    }));
}

describe('PackageEditor V6.13.5h', () => {
    beforeEach(() => {
        // V6.13.5h 锁契约:模拟 SPA 环境 — index.html 只挂 #root,没有 #container 元素
        // 删死代码前:render() 内 `document.getElementById('container')!.clientWidth` 抛 TypeError
        // 删死代码后:render() 不再访问 #container,正常 mount
        document.body.innerHTML = '<div id="root"></div>';
        expect(document.getElementById('container')).toBeNull();
    });

    // Given SPA 路由 mount(无 #container 元素)
    // When PackageEditor render
    // Then 不抛 TypeError,主功能 UI(添加包 / 保存 / 发起审批 / 仿真测试)正常渲染
    it('V6.13.5h:SPA mount(无 #container 元素)render 不崩,主按钮全在', () => {
        // 不应抛 TypeError: Cannot read properties of null (reading 'clientWidth')
        expect(() => {
            render(
                <Provider store={makeStore()}>
                    <PackageEditor project="test003"/>
                </Provider>,
            );
        }).not.toThrow();

        // 主功能按钮全渲染(证明 render() 走完,没崩)
        expect(screen.getByText('添加包')).toBeTruthy();
        expect(screen.getByText('保存')).toBeTruthy();
        expect(screen.getByText('生成版本')).toBeTruthy();
        expect(screen.getByText('发起审批')).toBeTruthy();
        expect(screen.getByText('发布测试')).toBeTruthy();
        expect(screen.getByText('仿真测试')).toBeTruthy();
    });
});
