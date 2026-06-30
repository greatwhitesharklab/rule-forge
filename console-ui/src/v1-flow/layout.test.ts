import { describe, it, expect } from 'vitest';
import {autoLayout} from './layout';
import type {Edge, Node} from '@xyflow/react';

/** V7.13 — autoLayout(dagre) BDD。覆盖线性/分支/空/单节点 4 个场景。 */

function n(id: string, x = 0, y = 0): Node {
    return {id, position: {x, y}, data: {}} as Node;
}
function e(id: string, source: string, target: string): Edge {
    return {id, source, target} as Edge;
}

describe('V7.13 — autoLayout', () => {

    it('线性流 Start→A→B→End → y 沿流递增(TB 布局)', () => {
        // Given 线性 4 节点
        const nodes = [n('s'), n('a'), n('b'), n('e')];
        const edges = [e('e1', 's', 'a'), e('e2', 'a', 'b'), e('e3', 'b', 'e')];
        // When autoLayout
        const laid = autoLayout(nodes, edges);
        const pos = Object.fromEntries(laid.map((nd) => [nd.id, nd.position]));
        // Then y(s) < y(a) < y(b) < y(e)(dagre TB 布局,沿流向下)
        expect(pos.s.y).toBeLessThan(pos.a.y);
        expect(pos.a.y).toBeLessThan(pos.b.y);
        expect(pos.b.y).toBeLessThan(pos.e.y);
    });

    it('分支 s→a / s→b → a 和 b 在同一 rank(y 相同)', () => {
        // Given 分支: s 后分叉到 a 和 b(同 rank)
        const nodes = [n('s'), n('a'), n('b'), n('e')];
        const edges = [e('e1', 's', 'a'), e('e2', 's', 'b'), e('e3', 'a', 'e'), e('e4', 'b', 'e')];
        const laid = autoLayout(nodes, edges);
        const pos = Object.fromEntries(laid.map((nd) => [nd.id, nd.position]));
        // Then a 和 b 同一 rank(y 相同),s 在上层,e 在下层
        expect(pos.a.y).toBeCloseTo(pos.b.y, 0);
        expect(pos.s.y).toBeLessThan(pos.a.y);
        expect(pos.a.y).toBeLessThan(pos.e.y);
    });

    it('空节点列表 → 返空数组', () => {
        expect(autoLayout([], [])).toEqual([]);
    });

    it('单节点 → 返该节点(位置居中)', () => {
        const laid = autoLayout([n('x')], []);
        expect(laid).toHaveLength(1);
        expect(laid[0].id).toBe('x');
        // dagre 给单节点一个位置(不严格,只验证有 position)
        expect(laid[0].position).toBeDefined();
    });
});