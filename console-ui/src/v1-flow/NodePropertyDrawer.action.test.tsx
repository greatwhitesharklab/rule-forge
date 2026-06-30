import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent, screen } from '@testing-library/react';
import { ActionsEditor } from './ActionEditor';
import type { Action } from './ruleAsset';

// antd CSS 重, jsdom 不需要实际样式, mock 掉避免警告 + 加速
vi.mock('ag-grid-community/styles/ag-grid.css', () => ({}));
vi.mock('ag-grid-community/styles/ag-theme-quartz.css', () => ({}));

/** V7.9 — al 动作库(后端 V7.4.1b)INVOKE 动作的 UI 编辑。
 *  Given ActionsEditor 含 INVOKE action
 *  When 用户填 ref / 加 arg / 改 target
 *  Then onChange 收到正确 JSON 形状({type:'INVOKE', ref, args:[{name,value|ref}], target?}),
 *       镜像后端 V7.4.1b InvokeAction 反射调 bean。 */
describe('V7.9 — ActionsEditor INVOKE (al 动作库)', () => {

    it('INVOKE action 渲染 ref/target inputs + +arg 按钮', () => {
        // Given INVOKE action 列表
        const actions: Action[] = [{type: 'INVOKE', ref: '', target: '', args: []}];
        render(<ActionsEditor actions={actions} onChange={() => {}}/>);

        // Then 渲染 ref、target 输入与 +arg 按钮
        expect(screen.getByPlaceholderText('ref (beanId.method)')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('target (写回字段,可空)')).toBeInTheDocument();
        expect(screen.getByText('+ arg')).toBeInTheDocument();
    });

    it('填 ref 触发 onChange, action.ref 正确更新', () => {
        // Given INVOKE action + onChange spy
        const actions: Action[] = [{type: 'INVOKE', ref: '', args: []}];
        let captured: Action[] = [];
        render(<ActionsEditor actions={actions} onChange={(a) => { captured = a; }}/>);

        // When 用户填 ref
        const refInput = screen.getByPlaceholderText('ref (beanId.method)');
        fireEvent.change(refInput, {target: {value: 'calcBean.compute'}});

        // Then onChange 收到 ref='calcBean.compute'
        expect(captured).toHaveLength(1);
        expect(captured[0].type).toBe('INVOKE');
        expect(captured[0].ref).toBe('calcBean.compute');
    });

    it('+arg 增加新 arg 行,name/value/ref inputs 渲染', () => {
        // Given INVOKE + 空 args
        const actions: Action[] = [{type: 'INVOKE', ref: 'b.m', args: []}];
        let captured: Action[] = [];
        render(<ActionsEditor actions={actions} onChange={(a) => { captured = a; }}/>);

        // When 点 +arg
        fireEvent.click(screen.getByText('+ arg'));

        // Then onChange 收到 args:[{name:'', value:''}]
        expect(captured[0].args).toHaveLength(1);
        expect(captured[0].args![0]).toEqual({name: '', value: ''});

        // 重新渲染带 1 个 arg,验证 name/value/ref inputs 在 DOM
        const { rerender } = render(<ActionsEditor actions={captured} onChange={() => {}}/>);
        // 注:+arg 触发后 captured 变化, rerender 用新 actions
        rerender(<ActionsEditor actions={captured} onChange={() => {}}/>);
        // 应该有 2 组 name placeholder(因为两次 render,这里用 queryAllByPlaceholder)
        // 简化:确保至少有一组
        const nameInputs = screen.queryAllByPlaceholderText('name');
        expect(nameInputs.length).toBeGreaterThanOrEqual(1);
        expect(screen.queryAllByPlaceholderText('value(字面量)').length).toBeGreaterThanOrEqual(1);
        expect(screen.queryAllByPlaceholderText('ref(字段引用)').length).toBeGreaterThanOrEqual(1);
    });

    it('填 arg 的 name/value 触发 args 更新', () => {
        // Given INVOKE + 1 arg
        const actions: Action[] = [{type: 'INVOKE', ref: 'b.m', args: [{name: '', value: ''}]}];
        let captured: Action[] = [];
        render(<ActionsEditor actions={actions} onChange={(a) => { captured = a; }}/>);

        // When 填 arg 的 name
        const nameInput = screen.getByPlaceholderText('name');
        fireEvent.change(nameInput, {target: {value: 'x'}});
        expect(captured[0].args![0].name).toBe('x');

        // When 填 arg 的 value
        const valueInput = screen.getByPlaceholderText('value(字面量)');
        fireEvent.change(valueInput, {target: {value: '42'}});
        expect(captured[0].args![0].value).toBe('42');
        // 注意:value 和 ref 二选一(后端 Arg.value 优先于 ref,语义同 SET_VARIABLE),这里只验 shape
    });

    it('填 target 触发 onChange, action.target 正确更新', () => {
        const actions: Action[] = [{type: 'INVOKE', ref: 'b.m', args: []}];
        let captured: Action[] = [];
        render(<ActionsEditor actions={actions} onChange={(a) => { captured = a; }}/>);

        fireEvent.change(screen.getByPlaceholderText('target (写回字段,可空)'), {target: {value: 'result'}});
        expect(captured[0].target).toBe('result');
    });

    it('非 INVOKE action 不渲染 INVOKE 字段', () => {
        // Given SET_VARIABLE action
        const actions: Action[] = [{type: 'SET_VARIABLE', target: 'x', value: '1'}];
        render(<ActionsEditor actions={actions} onChange={() => {}}/>);

        // Then 不应出现 INVOKE 专属 placeholder
        expect(screen.queryByPlaceholderText('ref (beanId.method)')).not.toBeInTheDocument();
        expect(screen.queryByPlaceholderText('target (写回字段,可空)')).not.toBeInTheDocument();
        expect(screen.queryByText('+ arg')).not.toBeInTheDocument();
    });
});