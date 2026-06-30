import { describe, it, expect } from 'vitest';
import {searchNodes} from './search';
import type {Node} from '@xyflow/react';

/** V7.15 — searchNodes BDD。覆盖空 query / name / id / 大小写 / 无匹配 5 场景。 */

function n(id: string, name: string): Node {
    return {id, position: {x: 0, y: 0}, data: {name}} as Node;
}

describe('V7.15 — searchNodes', () => {
    const all = [n('rs1', 'precheck'), n('dt1', 'Pricing'), n('sc1', 'riskScore'), n('gw1', 'route')];

    it('空 query → 返空', () => {
        expect(searchNodes(all, '')).toEqual([]);
        expect(searchNodes(all, '   ')).toEqual([]);
    });

    it('按 name 子串匹配(大小写不敏感)', () => {
        const r = searchNodes(all, 'PRIC');
        expect(r.map((x) => x.id)).toEqual(['dt1']);
    });

    it('按 id 子串匹配', () => {
        const r = searchNodes(all, 'rs');
        expect(r.map((x) => x.id)).toEqual(['rs1']);
    });

    it('大小写不敏感:大写 query 命中小写 name', () => {
        const r = searchNodes(all, 'ROUTE');
        expect(r.map((x) => x.id)).toEqual(['gw1']);
    });

    it('无匹配 → 返空', () => {
        expect(searchNodes(all, 'xyz123')).toEqual([]);
    });
});