import { describe, it, expect } from 'vitest';
import {validateRuleAsset} from './validation';
import type {RuleAsset, FlowElement} from './ruleAsset';

/** V7.11 — validateRuleAsset BDD。覆盖 5 核心规则 + 正常 happy path。 */

function el(partial: Partial<FlowElement> & {type: FlowElement['type']; id: string}): FlowElement {
    return partial as FlowElement;
}
function asset(elements: FlowElement[], nodes: RuleAsset['nodes'] = {}, schema?: RuleAsset['schema']): RuleAsset {
    return {
        version: '1.0',
        id: 'a1',
        name: 'test',
        flow: {id: 'f1', name: 'F', version: '1.0', flowElements: elements},
        nodes,
        schema,
    };
}

describe('V7.11 — validateRuleAsset', () => {

    it('happy path:Start + End + schema + Decision outputs + 业务节点被引用 → 0 issue', () => {
        const elements: FlowElement[] = [
            el({type: 'startEvent', id: 's1'}),
            el({type: 'serviceTask', id: 't1', implementation: 'RuleSet:rs1'}),
            el({type: 'serviceTask', id: 't2', implementation: 'Decision:d1'}),
            el({type: 'endEvent', id: 'e1'}),
            el({type: 'sequenceFlow', id: 'f1', sourceRef: 's1', targetRef: 't1'}),
            el({type: 'sequenceFlow', id: 'f2', sourceRef: 't1', targetRef: 't2'}),
            el({type: 'sequenceFlow', id: 'f3', sourceRef: 't2', targetRef: 'e1'}),
        ];
        const nodes: RuleAsset['nodes'] = {
            rs1: {id: 'rs1', type: 'RuleSet', name: 'precheck', hitPolicy: 'FIRST_MATCH', rules: []},
            d1: {id: 'd1', type: 'Decision', name: 'final', outputs: ['approve', 'reject']},
        };
        const schema = {name: 'Loan', fields: [{name: 'age', type: 'NUMBER' as const}]};
        const issues = validateRuleAsset(asset(elements, nodes, schema));
        expect(issues).toHaveLength(0);
    });

    it('无 Start → error:缺少 Start 事件', () => {
        const issues = validateRuleAsset(asset([el({type: 'endEvent', id: 'e1'})]));
        expect(issues.some((i) => i.level === 'error' && /Start/.test(i.message))).toBe(true);
    });

    it('顶层 schema 无字段 → error:schema 定义无字段', () => {
        const elements: FlowElement[] = [
            el({type: 'startEvent', id: 's1'}),
            el({type: 'endEvent', id: 'e1'}),
            el({type: 'sequenceFlow', id: 'f1', sourceRef: 's1', targetRef: 'e1'}),
        ];
        const schema = {name: 'X', fields: []};
        const issues = validateRuleAsset(asset(elements, {}, schema));
        expect(issues.some((i) => i.level === 'error' && /schema/.test(i.message))).toBe(true);
    });

    it('无 End → error:缺少 End 事件', () => {
        const elements: FlowElement[] = [el({type: 'startEvent', id: 's1'})];
        const issues = validateRuleAsset(asset(elements));
        expect(issues.some((i) => i.level === 'error' && /End/.test(i.message))).toBe(true);
    });

    it('Start 不到 End(断流)→ error:End 不可达', () => {
        const elements: FlowElement[] = [
            el({type: 'startEvent', id: 's1'}),
            el({type: 'serviceTask', id: 't1', implementation: 'RuleSet:rs1'}),
            el({type: 'endEvent', id: 'e1'}), // 孤立,无入边
            el({type: 'sequenceFlow', id: 'f1', sourceRef: 's1', targetRef: 't1'}),
        ];
        const issues = validateRuleAsset(asset(elements, {
            rs1: {id: 'rs1', type: 'RuleSet', name: 'r', hitPolicy: 'FIRST_MATCH', rules: []},
        }));
        expect(issues.some((i) => i.level === 'error' && /不可达/.test(i.message))).toBe(true);
    });

    it('Decision outputs 为空 → error:outputs 为空', () => {
        const elements: FlowElement[] = [
            el({type: 'startEvent', id: 's1'}),
            el({type: 'serviceTask', id: 't1', implementation: 'Decision:d1'}),
            el({type: 'endEvent', id: 'e1'}),
            el({type: 'sequenceFlow', id: 'f1', sourceRef: 's1', targetRef: 't1'}),
            el({type: 'sequenceFlow', id: 'f2', sourceRef: 't1', targetRef: 'e1'}),
        ];
        const nodes: RuleAsset['nodes'] = {
            d1: {id: 'd1', type: 'Decision', name: 'final', outputs: []},
        };
        const issues = validateRuleAsset(asset(elements, nodes));
        const issue = issues.find((i) => i.nodeId === 'd1');
        expect(issue).toBeDefined();
        expect(issue!.level).toBe('error');
        expect(issue!.message).toMatch(/outputs.*为空/);
    });

    it('业务节点孤立(无 serviceTask 引用)→ warning', () => {
        const elements: FlowElement[] = [
            el({type: 'startEvent', id: 's1'}),
            el({type: 'endEvent', id: 'e1'}),
            el({type: 'sequenceFlow', id: 'f1', sourceRef: 's1', targetRef: 'e1'}),
        ];
        const nodes: RuleAsset['nodes'] = {
            orphan: {id: 'orphan', type: 'RuleSet', name: 'orphan', hitPolicy: 'FIRST_MATCH', rules: []},
        };
        const issues = validateRuleAsset(asset(elements, nodes, {name: 'X', fields: [{name: 'a', type: 'NUMBER' as const}]}));
        const issue = issues.find((i) => i.nodeId === 'orphan');
        expect(issue).toBeDefined();
        expect(issue!.level).toBe('warning');
        expect(issue!.message).toMatch(/孤立/);
    });

    it('多个 Start → warning(允许但提示)', () => {
        const elements: FlowElement[] = [
            el({type: 'startEvent', id: 's1'}),
            el({type: 'startEvent', id: 's2'}),
            el({type: 'endEvent', id: 'e1'}),
            el({type: 'sequenceFlow', id: 'f1', sourceRef: 's1', targetRef: 'e1'}),
        ];
        const issues = validateRuleAsset(asset(elements));
        expect(issues.some((i) => i.level === 'warning' && /Start.*\d+/.test(i.message))).toBe(true);
    });

    it('V7.16:节点名重复(大小写不敏感)→ warning', () => {
        const elements: FlowElement[] = [
            el({type: 'startEvent', id: 's1'}),
            el({type: 'endEvent', id: 'e1'}),
            el({type: 'sequenceFlow', id: 'f1', sourceRef: 's1', targetRef: 'e1'}),
        ];
        const nodes: RuleAsset['nodes'] = {
            a: {id: 'a', type: 'RuleSet', name: 'precheck', hitPolicy: 'FIRST_MATCH', rules: []},
            b: {id: 'b', type: 'RuleSet', name: 'PreCheck', hitPolicy: 'FIRST_MATCH', rules: []},
            c: {id: 'c', type: 'ScoreCard', name: 'other', output: 's', aggregation: 'SUM', cards: []},
        };
        const issues = validateRuleAsset(asset(elements, nodes));
        const issue = issues.find((i) => i.level === 'warning' && /重复/.test(i.message));
        expect(issue).toBeDefined();
        expect(issue!.message).toMatch(/precheck.*PreCheck/);
    });

    it('V7.17:Gateway 全条件出边无 defaultFlow → warning(死路风险)', () => {
        // Given gateway 2 出边都带 conditionExpression,无 defaultFlow
        const elements: FlowElement[] = [
            el({type: 'startEvent', id: 's1'}),
            el({type: 'exclusiveGateway', id: 'gw1'}),
            el({type: 'serviceTask', id: 't1', implementation: 'RuleSet:rs1'}),
            el({type: 'serviceTask', id: 't2', implementation: 'RuleSet:rs2'}),
            el({type: 'endEvent', id: 'e1'}),
            el({type: 'sequenceFlow', id: 'f1', sourceRef: 's1', targetRef: 'gw1'}),
            el({type: 'sequenceFlow', id: 'f2', sourceRef: 'gw1', targetRef: 't1', conditionExpression: 'age >= 18'}),
            el({type: 'sequenceFlow', id: 'f3', sourceRef: 'gw1', targetRef: 't2', conditionExpression: 'age < 18'}),
            el({type: 'sequenceFlow', id: 'f4', sourceRef: 't1', targetRef: 'e1'}),
            el({type: 'sequenceFlow', id: 'f5', sourceRef: 't2', targetRef: 'e1'}),
        ];
        const issues = validateRuleAsset(asset(elements, {
            rs1: {id: 'rs1', type: 'RuleSet', name: 'r1', hitPolicy: 'FIRST_MATCH', rules: []},
            rs2: {id: 'rs2', type: 'RuleSet', name: 'r2', hitPolicy: 'FIRST_MATCH', rules: []},
        }));
        const issue = issues.find((i) => i.nodeId === 'gw1' && /defaultFlow/.test(i.message));
        expect(issue).toBeDefined();
        expect(issue!.level).toBe('warning');
    });

    it('V7.17:Gateway 有 defaultFlow → 不报 warning', () => {
        const elements: FlowElement[] = [
            el({type: 'startEvent', id: 's1'}),
            el({type: 'exclusiveGateway', id: 'gw1', defaultFlow: 'f3'}),
            el({type: 'endEvent', id: 'e1'}),
            el({type: 'sequenceFlow', id: 'f1', sourceRef: 's1', targetRef: 'gw1'}),
            el({type: 'sequenceFlow', id: 'f2', sourceRef: 'gw1', targetRef: 'e1', conditionExpression: 'a > 1'}),
            el({type: 'sequenceFlow', id: 'f3', sourceRef: 'gw1', targetRef: 'e1'}),
        ];
        const issues = validateRuleAsset(asset(elements));
        expect(issues.find((i) => i.nodeId === 'gw1' && /defaultFlow/.test(i.message))).toBeUndefined();
    });
});