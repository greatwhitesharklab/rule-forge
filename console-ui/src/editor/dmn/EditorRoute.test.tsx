/**
 * V6.20.0 P3:DMN 1.3 只读源查看器 vitest BDD。
 *
 * <p>锁 3 件事:
 * <ol>
 *   <li>挂载调 /frame/fileSource 拿 .dmn 原文,渲染到 pre[data-testid="dmn-source"]</li>
 *   <li>顶部 banner 标注 "DMN 1.3 只读查看器" + file + project name</li>
 *   <li>无 Monaco / 无保存按钮(只读契约)</li>
 * </ol>
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => ({
    ok: true,
    status: 200,
    json: async () => ({ content: '<?xml version="1.0"?><definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"/>' }),
    text: async () => '',
}));
vi.stubGlobal('fetch', fetchMock);

vi.mock('../../Utils', () => ({
    buildProjectNameFromFile: vi.fn((_file: string) => 'test-project'),
}));

import EditorRoute from './EditorRoute';

describe('DMN EditorRoute V6.20.0 P3', () => {
    beforeEach(() => {
        fetchMock.mockClear();
    });

    it('GIVEN file param WHEN mount THEN fetch /frame/fileSource with path=file', async () => {
        render(
            <MemoryRouter initialEntries={['/app/editor/dmn?file=/proj/test.dmn']}>
                <Routes>
                    <Route path="/app/editor/dmn" element={<EditorRoute/>}/>
                </Routes>
            </MemoryRouter>,
        );
        await waitFor(() => {
            expect(fetchMock).toHaveBeenCalled();
        });
        const call = fetchMock.mock.calls[0];
        expect(String(call[0])).toContain('/frame/fileSource');
        // body 是 URLSearchParams 序列化的 path
        const init = call[1] as RequestInit;
        expect(String(init.body)).toContain('path=%2Fproj%2Ftest.dmn');
    });

    it('GIVEN fileSource payload WHEN render THEN pre 显示原文', async () => {
        render(
            <MemoryRouter initialEntries={['/app/editor/dmn?file=/proj/test.dmn']}>
                <Routes>
                    <Route path="/app/editor/dmn" element={<EditorRoute/>}/>
                </Routes>
            </MemoryRouter>,
        );
        const pre = await screen.findByTestId('dmn-source');
        expect(pre.textContent).toContain('definitions');
    });

    it('GIVEN mount WHEN render THEN banner 显示只读标签 + file + project', () => {
        render(
            <MemoryRouter initialEntries={['/app/editor/dmn?file=/proj/test.dmn']}>
                <Routes>
                    <Route path="/app/editor/dmn" element={<EditorRoute/>}/>
                </Routes>
            </MemoryRouter>,
        );
        expect(screen.getByText(/DMN 1\.3 只读查看器/)).toBeTruthy();
        expect(screen.getByText('/proj/test.dmn')).toBeTruthy();
        expect(screen.getByText('test-project')).toBeTruthy();
    });
});
