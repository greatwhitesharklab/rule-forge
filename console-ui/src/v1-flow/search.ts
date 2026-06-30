import type {Node} from '@xyflow/react';

/** V7.15 — 按 name/id 模糊匹配画布节点(大小写不敏感子串)。空 query 返空。 */
export function searchNodes(nodes: Node[], query: string): Node[] {
    const q = (query || '').trim().toLowerCase();
    if (!q) return [];
    return nodes.filter((n) => {
        const name = ((n.data as any)?.name as string) || '';
        return name.toLowerCase().includes(q) || n.id.toLowerCase().includes(q);
    });
}