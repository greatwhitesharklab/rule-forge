import dagre from 'dagre';
import type {Edge, Node} from '@xyflow/react';

/** V7.13 dagre auto-layout 默认节点尺寸(未测量时)。 */
const DEFAULT_WIDTH = 180;
const DEFAULT_HEIGHT = 60;

/** V7.13 — 用 dagre 对画布节点做 top-to-bottom 自动布局。
 *  纯函数,接受 React Flow nodes/edges → 返回 position 已更新的新 nodes(同 id 顺序)。
 *  不动 edges(布局不影响连线 source/target)。 */
export function autoLayout(nodes: Node[], edges: Edge[]): Node[] {
    const g = new dagre.graphlib.Graph();
    g.setDefaultEdgeLabel(() => ({}));
    // V7.17: align 'UL' 同行节点左对齐(多 Gateway 分支视觉整齐),ranker 'tight-tree' 紧凑
    g.setGraph({rankdir: 'TB', nodesep: 50, ranksep: 80, marginx: 20, marginy: 20, align: 'UL', ranker: 'tight-tree'});
    for (const n of nodes) {
        const w = (n as any).measured?.width ?? (n as any).width ?? DEFAULT_WIDTH;
        const h = (n as any).measured?.height ?? (n as any).height ?? DEFAULT_HEIGHT;
        g.setNode(n.id, {width: w, height: h});
    }
    for (const e of edges) {
        g.setEdge(e.source, e.target);
    }
    dagre.layout(g);
    return nodes.map((n) => {
        const dn = g.node(n.id);
        if (!dn) return n;
        const w = (n as any).measured?.width ?? (n as any).width ?? DEFAULT_WIDTH;
        const h = (n as any).measured?.height ?? (n as any).height ?? DEFAULT_HEIGHT;
        // dagre 返中心坐标,React Flow 要左上角
        return {...n, position: {x: dn.x - w / 2, y: dn.y - h / 2}};
    });
}