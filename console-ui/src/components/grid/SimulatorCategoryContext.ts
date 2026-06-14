import {createContext} from 'react';

/**
 * 知识包仿真器(SimulatorPage)产出的「类型分类 → 字段」数据 Context。
 *
 * V5.74.4 之前 SimulatorPage 把这段数据写到 {@code window.simulatorCategoryData},
 * 任意 {@code Cell} 双击 list 编辑器时全局读。问题:其他编辑器(ActionEditor /
 * ConstantEditor / ...)打开 list 弹窗时,读到的是 SimulatorPage 留下的过期/错位
 * 分类列表(那些编辑器有自己的概念,跟仿真器无关)。
 *
 * V5.74.4 改为 React Context:
 * <ul>
 *   <li>Provider 只在 SimulatorPage 打开时挂载</li>
 *   <li>只对它树内的 Cell 可见(通过 static contextType)</li>
 *   <li>非仿真器场景下 Cell 读到 null,回退空数组(保持原"无数据"行为)</li>
 * </ul>
 *
 * 数据由 {@code loadSimulatorCategoryData} 拉取后经
 * {@code buildSimulatorVariableEditorType} 注入 {@code variables[i]._editorType}。
 */
export interface SimulatorCategoryContextValue {
    clazz: string;
    name: string;
    variables: Array<{
        name: string;
        label: string;
        type: string;
        defaultValue?: string;
        _editorType?: 'number' | 'boolean' | 'date' | 'list' | 'string';
        [key: string]: unknown;
    }>;
}

export const SimulatorCategoryContext = createContext<SimulatorCategoryContextValue[] | null>(null);
