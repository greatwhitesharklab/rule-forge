/**
 * B1 止血 — CommonDialog Escape 关闭 vitest。
 *
 * <p>背景:antd Modal 曾带 forceRender,所有对话框常驻 @rc-component/portal 的
 * ESC 栈(open 恒 true),Escape 只路由到栈顶(最后挂载的)对话框,可见对话框
 * 收不到 → 走查时创建项目/创建目录对话框按 Escape 关不掉。移除 forceRender 后
 * 只有可见对话框在栈里。
 *
 * <p>锁 2 件事:
 * <ol>
 *   <li>可见时按 Escape → 触发 onClose</li>
 *   <li>不可见时按 Escape → 不触发 onClose(不在 ESC 栈里)</li>
 * </ol>
 */
import {describe, expect, it, vi} from 'vitest';
import {render, fireEvent, screen, waitFor} from '@testing-library/react';
import CommonDialog from './CommonDialog';

describe('CommonDialog - Escape 关闭(B1 止血)', () => {
    it('GIVEN 可见对话框 WHEN 按 Escape THEN 触发 onClose', async () => {
        const onClose = vi.fn();
        render(<CommonDialog visible={true} title='测试对话框' body={<div>内容</div>} onClose={onClose}/>);

        // antd Modal 经 portal 挂到 body,等它出现
        await screen.findByText('测试对话框');

        fireEvent.keyDown(document.body, {key: 'Escape'});

        await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    });

    it('GIVEN 不可见对话框 WHEN 按 Escape THEN 不触发 onClose', () => {
        const onClose = vi.fn();
        render(<CommonDialog visible={false} title='测试对话框' onClose={onClose}/>);

        fireEvent.keyDown(document.body, {key: 'Escape'});

        expect(onClose).not.toHaveBeenCalled();
    });
});
