/**
 * EditorLoadState vitest BDD。
 *
 * <p>锁 4 件事:
 * <ol>
 *   <li>no-file → 引导空态文案(从文件树打开编辑器)</li>
 *   <li>loading → Spin + "加载中…"</li>
 *   <li>error → Result 错误态 + 格式化原因(Response 对象禁止显示成 [object Response])</li>
 *   <li>onRetry → 渲染重试按钮并触发回调;不传 onRetry 则无按钮</li>
 * </ol>
 */
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import EditorLoadState, { formatLoadError } from './EditorLoadState';

describe('EditorLoadState - formatLoadError', () => {
    it('GIVEN a Response object WHEN formatting THEN it should show HTTP status instead of [object Response]', () => {
        const resp = new Response('boom', { status: 404, statusText: 'Not Found' });
        const msg = formatLoadError(resp);
        expect(msg).toContain('HTTP 404');
        expect(msg).toContain('Not Found');
        expect(msg).not.toContain('[object');
    });

    it('GIVEN a backend envelope {status:false,message} WHEN formatting THEN it should show backend message', () => {
        expect(formatLoadError({ status: false, message: '文件不存在' })).toBe('文件不存在');
    });

    it('GIVEN an Error WHEN formatting THEN it should show error message', () => {
        expect(formatLoadError(new Error('network down'))).toBe('network down');
    });

    it('GIVEN a string WHEN formatting THEN it should pass through', () => {
        expect(formatLoadError('加载失败')).toBe('加载失败');
    });

    it('GIVEN null/undefined WHEN formatting THEN it should fall back to 未知错误', () => {
        expect(formatLoadError(null)).toBe('未知错误');
        expect(formatLoadError(undefined)).toBe('未知错误');
    });
});

describe('EditorLoadState - 渲染', () => {
    it('GIVEN no-file status WHEN render THEN it should show 引导空态', () => {
        render(<EditorLoadState status="no-file" />);
        expect(document.body.textContent).toContain('未指定要编辑的文件');
        expect(document.body.textContent).toContain('文件树');
    });

    it('GIVEN loading status WHEN render THEN it should show spin tip', () => {
        render(<EditorLoadState status="loading" />);
        expect(document.body.textContent).toContain('加载中…');
    });

    it('GIVEN error status with Response WHEN render THEN it should show formatted reason, not [object Response]', () => {
        const resp = new Response('boom', { status: 500, statusText: 'Internal Server Error' });
        render(<EditorLoadState status="error" error={resp} />);
        expect(document.body.textContent).toContain('加载失败');
        expect(document.body.textContent).toContain('HTTP 500');
        expect(document.body.textContent).not.toContain('[object Response]');
    });

    it('GIVEN error status with onRetry WHEN clicking 重试 THEN it should invoke callback', () => {
        const onRetry = vi.fn();
        render(<EditorLoadState status="error" error={new Error('x')} onRetry={onRetry} />);
        // antd Button 会在两个汉字间插空格("重 试") — 用正则放宽匹配
        fireEvent.click(screen.getByText(/重\s*试/));
        expect(onRetry).toHaveBeenCalledTimes(1);
    });

    it('GIVEN error status without onRetry WHEN render THEN it should not show 重试 button', () => {
        render(<EditorLoadState status="error" error={new Error('x')} />);
        expect(screen.queryByText(/重\s*试/)).toBeNull();
    });
});
