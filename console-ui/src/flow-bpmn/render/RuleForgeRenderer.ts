import BaseRenderer from 'diagram-js/lib/draw/BaseRenderer';
import {append as svgAppend, create as svgCreate} from 'tiny-svg';

const HIGH_PRIORITY = 1500;

export default function RuleForgeRenderer(eventBus: any) {
    BaseRenderer.call(this, eventBus, HIGH_PRIORITY);

    (this as any).canRender = function(element: any) {
        if (element.type !== 'bpmn:ServiceTask') return false;
        const bo = element.businessObject;
        return bo && bo.$attrs && bo.$attrs['ruleforge:taskType'] === 'rulesPackage';
    };

    (this as any).drawShape = function(parentNode: SVGElement, element: any) {
        const bo = element.businessObject;
        const name = bo.name || '规则包';
        const width = element.width || 100;
        const height = element.height || 80;

        const group = svgCreate('g');

        const rect = svgCreate('rect', {
            x: 0, y: 0,
            width, height,
            rx: 6, ry: 6,
            fill: '#f0f5ff',
            stroke: '#1677ff',
            'stroke-width': 1.5
        });
        svgAppend(group, rect);

        const doc1 = svgCreate('rect', {
            x: 6, y: 6, width: 20, height: 16,
            rx: 2, ry: 2,
            fill: '#fff', stroke: '#1677ff', 'stroke-width': 1
        });
        svgAppend(group, doc1);

        const doc2 = svgCreate('rect', {
            x: 9, y: 3, width: 20, height: 16,
            rx: 2, ry: 2,
            fill: '#fff', stroke: '#1677ff', 'stroke-width': 1
        });
        svgAppend(group, doc2);

        for (let i = 0; i < 3; i++) {
            const line = svgCreate('line', {
                x1: 12, y1: 8 + i * 4,
                x2: 26, y2: 8 + i * 4,
                stroke: '#91caff', 'stroke-width': 1
            });
            svgAppend(group, line);
        }

        const text = svgCreate('text', {
            x: width / 2, y: height - 12,
            'text-anchor': 'middle',
            'font-size': 11,
            'font-family': '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
            fill: '#1677ff',
            'font-weight': 500
        });
        text.textContent = name.length > 8 ? name.substring(0, 8) + '...' : name;
        svgAppend(group, text);

        svgAppend(parentNode, group);
        return group;
    };

    (this as any).getShapePath = function(element: any) {
        return [['M', element.x, element.y], ['l', element.width, 0],
            ['l', 0, element.height], ['l', -element.width, 0], ['z']];
    };
}

(RuleForgeRenderer as any).$inject = ['eventBus'];

RuleForgeRenderer.prototype = Object.create(BaseRenderer.prototype);
RuleForgeRenderer.prototype.constructor = RuleForgeRenderer;
