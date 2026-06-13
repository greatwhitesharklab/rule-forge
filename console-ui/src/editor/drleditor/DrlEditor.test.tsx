/**
 * V5.45.3 — DRL 编辑器 vitest BDD。
 *
 * <p>锁 3 件事:
 * <ol>
 *   <li>挂载时调 loadDrlFile 拿 content,填进 CodeMirror</li>
 *   <li>顶部 toolbar 显示 "Source format: DRL" badge(用 DEFAULT_DIALECT)</li>
 *   <li>侧栏显示 imports / ruleNames 列表(payload 来的)</li>
 * </ol>
 *
 * <p>本测试用 jsdom + @testing-library/react 渲染组件。CodeMirror 5 在 jsdom
 * 下行为有限(无真实布局),所以只测 React state + props,不测 CodeMirror 实例。
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

// mock CodeMirror — 简化到能 getValue / setValue / onChange 三个 API
vi.mock('./DrlCodeMirror', () => ({
    DrlCodeMirror: class MockCm {
        value = '';
        getValue() { return this.value; }
        setValue(v: string) { this.value = v; }
        onChange(_cb: (v: string) => void) { /* noop */ }
        refresh() { /* noop */ }
    },
}));

// mock loadDrlFile — 返固定 payload
vi.mock('../../api/drlEditor', () => ({
    loadDrlFile: vi.fn(async (_file: string) => ({
        path: '/project/test.drl',
        content: 'rule "R1" when Applicant(age > 18) then end',
        imports: ['libs/variables.drl'],
        ruleNames: ['R1'],
    })),
}));

// mock Utils — getParameter 返 test file 路径
vi.mock('../../Utils', () => ({
    getParameter: vi.fn((_name: string) => '/test.drl'),
    buildProjectNameFromFile: vi.fn((_file: string) => 'test-project'),
}));

import DrlEditor from './index';

describe('V5.45.3 — DRL editor BDD', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('mounts and renders file path + DRL 4 badge in toolbar', async () => {
        render(<DrlEditor file="/test.drl" />);
        await waitFor(() => {
            expect(screen.getByText('/test.drl')).toBeTruthy();
        });
        expect(screen.getByText(/Source format: DRL/)).toBeTruthy();
    });

    it('renders imports and rule names sidebar from payload', async () => {
        render(<DrlEditor file="/test.drl" />);
        await waitFor(() => {
            expect(screen.getByText('libs/variables.drl')).toBeTruthy();
            expect(screen.getByText('R1')).toBeTruthy();
        });
        expect(screen.getByText(/Imports \(1\)/)).toBeTruthy();
        expect(screen.getByText(/Rules \(1\)/)).toBeTruthy();
    });

    it('renders save + save new version buttons in toolbar', async () => {
        render(<DrlEditor file="/test.drl" />);
        // antd Button 文本包在 <span> 内 — 用 document.body.textContent 全文搜索
        await waitFor(() => {
            expect(document.body.textContent).toContain('保存');
            expect(document.body.textContent).toContain('保存新版本');
        });
    });
});
