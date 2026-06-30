import type {RuleAsset} from './ruleAsset';

/** V7.11 — V1 RuleAsset 校验。轻量纯函数,可单测,FlowDesigner 发布前 gate。 */
export type ValidationLevel = 'error' | 'warning';

export interface ValidationIssue {
    level: ValidationLevel;
    message: string;
    /** 关联节点 id(画布高亮用),无则 undefined。 */
    nodeId?: string;
}

const elementsOf = (asset: RuleAsset) => asset.flow?.flowElements || [];
const nodesOf = (asset: RuleAsset) => asset.nodes || {};

/** 校验整个 V1 RuleAsset。error 必须修才能发布;warning 是建议(孤立节点、多个 Start)。 */
export function validateRuleAsset(asset: RuleAsset): ValidationIssue[] {
    const issues: ValidationIssue[] = [];
    const elements = elementsOf(asset);
    const nodes = nodesOf(asset);

    // 1. Start 必须存在
    const starts = elements.filter((e) => e.type === 'startEvent');
    if (starts.length === 0) {
        issues.push({level: 'error', message: '缺少 Start 事件(无入口,决策流无法启动)'});
    } else if (starts.length > 1) {
        issues.push({level: 'warning', message: `存在 ${starts.length} 个 Start 事件,通常 1 个`});
    }

    // 2. Start schema 必填(assets 可有顶层 schema;若没有,要求 StartNode.schema 非空)
    const topSchema = asset.schema;
    if (topSchema) {
        if (!topSchema.fields || topSchema.fields.length === 0) {
            issues.push({level: 'error', message: '顶层 schema 定义无字段'});
        }
    } else {
        // 找 Start 节点的 schema 字段
        const startNodes = Object.values(nodes).filter((n) => n.type === 'Start');
        const hasSchema = startNodes.some((n: any) => n.schema && n.schema.trim().length > 0);
        if (!hasSchema && startNodes.length === 0) {
            issues.push({level: 'error', message: 'Start schema 未定义(无 Start 节点)'});
        } else if (!hasSchema) {
            issues.push({level: 'error', message: 'Start 节点 schema 名未填(fact 模型未知)'});
        }
    }

    // 3. EndEvent 必须存在
    const ends = elements.filter((e) => e.type === 'endEvent');
    if (ends.length === 0) {
        issues.push({level: 'error', message: '缺少 End 事件(无出口,flow 不会停)'});
    } else {
        // BFS 从 Start 出发,看是否有 End 可达
        if (starts.length > 0) {
            const reachable = new Set<string>();
            const queue = starts.map((s) => s.id);
            while (queue.length) {
                const cur = queue.shift()!;
                if (reachable.has(cur)) continue;
                reachable.add(cur);
                for (const e of elements) {
                    if (e.type === 'sequenceFlow' && e.sourceRef === cur && e.targetRef && !reachable.has(e.targetRef)) {
                        queue.push(e.targetRef);
                    }
                }
            }
            const reachableEnds = ends.filter((e) => reachable.has(e.id));
            if (reachableEnds.length === 0) {
                issues.push({level: 'error', message: 'End 事件从 Start 不可达(断流)'});
            }
        }
    }

    // 4. Decision outputs 非空(否则 emitDecision 返 null)
    for (const [id, n] of Object.entries(nodes)) {
        if (n.type === 'Decision' && (!n.outputs || n.outputs.length === 0)) {
            issues.push({level: 'error', message: `Decision 节点 "${n.name || id}" outputs 为空(决策返 null)`, nodeId: id});
        }
    }

    // 5. 业务节点孤立:serviceTask implementation 引用不到对应 node
    for (const [id, n] of Object.entries(nodes)) {
        if (n.type === 'Start' || n.type === 'Decision') continue;
        const referenced = elements.some((e) => e.type === 'serviceTask' && typeof e.implementation === 'string' && e.implementation.endsWith(':' + id));
        if (!referenced) {
            issues.push({level: 'warning', message: `业务节点 "${n.name || id}" 未被画布引用(孤立)`, nodeId: id});
        }
    }

    return issues;
}