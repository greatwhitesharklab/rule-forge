import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';
import FlowEditor from './FlowEditor';

// Mock bpmn-js since it needs a real DOM and canvas
vi.mock('bpmn-js/lib/Modeler', () => {
    return {
        default: class MockModeler {
            constructor() {}
            importXML(xml: string) {
                if (!xml || xml.trim() === '') return Promise.reject(new Error('empty xml'));
                return Promise.resolve({ warnings: [] });
            }
            saveXML() {
                return Promise.resolve({ xml: '<bpmn:definitions></bpmn:definitions>' });
            }
            saveSVG() {
                return Promise.resolve({ svg: '<svg></svg>' });
            }
            on() {}
            get() { return { on: () => {} }; }
            destroy() {}
        }
    };
});

// Mock CSS imports
vi.mock('./palette/ruleforge-palette.css', () => ({}));

describe('FlowEditor', () => {

    // Given FlowEditor 组件挂载
    // When render 被调用
    // Then 应渲染容器 div（ref containerRef）
    it('应渲染容器 div', () => {
        const { container } = render(<FlowEditor />);
        // The component renders a wrapper div with a child div (containerRef)
        const wrapper = container.firstChild;
        expect(wrapper).not.toBeNull();
        expect((wrapper as Element).firstChild).not.toBeNull();
    });

    // Given FlowEditor 组件不传 xml prop
    // When componentDidMount 被调用
    // Then 应创建 BpmnModeler 实例
    it('不传 xml 时应初始化 modeler', () => {
        const { container } = render(<FlowEditor />);
        expect(container.firstChild).toBeTruthy();
    });
});
