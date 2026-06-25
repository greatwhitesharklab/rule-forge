/**
 * V6.20.0 P3:PMML 4.4 只读源查看器 vitest BDD。
 *
 * <p>锁 3 件事:
 * <ol>
 *   <li>挂载调 /frame/fileSource 拿 .pmml 原文,渲染到 pre[data-testid="pmml-source"]</li>
 *   <li>顶部 banner 标注 "PMML 4.4 只读查看器(暂不产生规则)" + file + project name</li>
 *   <li>无 Monaco / 无保存按钮(只读契约;提示用户当前 0 rules emitted)</li>
 * </ol>
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => ({
    ok: true,
    status: 200,
    json: async () => ({ content: '<?xml version="1.0"?><PMML xmlns="http://www.dmg.org/PMML-4_4"><Scorecard/></PMML>' }),
    text: async () => '',
}));
vi.stubGlobal('fetch', fetchMock);

vi.mock('../../Utils', () => ({
    buildProjectNameFromFile: vi.fn((_file: string) => 'test-project'),
}));

import EditorRoute from './EditorRoute';

describe('PMML EditorRoute V6.20.0 P3', () => {
    beforeEach(() => {
        fetchMock.mockClear();
    });

    it('GIVEN file param WHEN mount THEN fetch /frame/fileSource with path=file', async () => {
        render(
            <MemoryRouter initialEntries={['/app/editor/pmml?file=/proj/score.pmml']}>
                <Routes>
                    <Route path="/app/editor/pmml" element={<EditorRoute/>}/>
                </Routes>
            </MemoryRouter>,
        );
        await waitFor(() => {
            expect(fetchMock).toHaveBeenCalled();
        });
        const call = fetchMock.mock.calls[0];
        expect(String(call[0])).toContain('/frame/fileSource');
        const init = call[1] as RequestInit;
        expect(String(init.body)).toContain('path=%2Fproj%2Fscore.pmml');
    });

    it('GIVEN fileSource payload WHEN render THEN pre 显示原文', async () => {
        render(
            <MemoryRouter initialEntries={['/app/editor/pmml?file=/proj/score.pmml']}>
                <Routes>
                    <Route path="/app/editor/pmml" element={<EditorRoute/>}/>
                </Routes>
            </MemoryRouter>,
        );
        const pre = await screen.findByTestId('pmml-source');
        expect(pre.textContent).toContain('PMML');
    });

    it('GIVEN mount WHEN render THEN banner 显式标注 PMML 是 "导入格式"(非核心模型)+ 0 rules + file + project', () => {
        render(
            <MemoryRouter initialEntries={['/app/editor/pmml?file=/proj/score.pmml']}>
                <Routes>
                    <Route path="/app/editor/pmml" element={<EditorRoute/>}/>
                </Routes>
            </MemoryRouter>,
        );
        // 2026-06-24 user 反馈:PMML 是传统银行/SAS 体系遗留标准,本系统只作"导入格式"
        expect(screen.getByText(/仅作导入格式/)).toBeTruthy();
        // "不作为核心执行模型" + "0 rules 触发" 双重警告 — 防止用户误以为 build 后会触发 PMML 求值
        expect(screen.getByText(/0 rules 触发/)).toBeTruthy();
        expect(screen.getByText('/proj/score.pmml')).toBeTruthy();
        expect(screen.getByText('test-project')).toBeTruthy();
    });
});
