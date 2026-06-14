import {describe, it, expect} from 'vitest';
import {render} from '@testing-library/react';
import {SimulatorCategoryContext} from './SimulatorCategoryContext';

describe('SimulatorCategoryContext — 知识包仿真器分类数据 Context (V5.74.4)', () => {
    it('GIVEN 无 Provider WHEN Cell 类组件访问 context THEN 默认值为 null(等同旧 window 空 fallback)', () => {
        // 旧版 `window.simulatorCategoryData || []` 在 Cell 里 fallback 成空数组。
        // 新版 context 默认为 null,Cell 端 `this.context || []` 同效果。
        // 这条断言锁住 default 是 null(非 undefined),保证 Cell 端判断一致。
        let observed: unknown = 'sentinel';
        render(
            <SimulatorCategoryContext.Consumer>
                {(value) => { observed = value; return <div data-testid="probe">probe</div>; }}
            </SimulatorCategoryContext.Consumer>,
        );
        expect(observed).toBeNull();
    });

    it('GIVEN Provider 注入分类数据 WHEN 渲染 Consumer THEN 读到 Provider 的值(穿透正确)', () => {
        const sample = [
            {clazz: 'Applicant', name: '申请人', variables: [
                {name: 'age', label: '年龄', type: 'number', _editorType: 'number' as const},
            ]},
        ];
        let observed: unknown = null;
        render(
            <SimulatorCategoryContext.Provider value={sample as any}>
                <SimulatorCategoryContext.Consumer>
                    {(value) => { observed = value; return <div data-testid="probe">probe</div>; }}
                </SimulatorCategoryContext.Consumer>
            </SimulatorCategoryContext.Provider>,
        );
        expect(observed).toBe(sample);
    });
});
