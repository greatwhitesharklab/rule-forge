import {describe, it, expect} from 'vitest';
import {fromRuleAsset} from './fromRuleAsset';
import type {RuleAsset} from './ruleAsset';

/**
 * V7.1-2a — fromRuleAsset Gateway 解析 BDD。
 *
 * <p>exclusiveGateway 是 BPMN flow element(非 nodes{} 业务节点):无 implementation,
 * 靠 BPMN type 映射 nodeType='Gateway';defaultFlow 透传到 rfNode.data.defaultFlow;
 * 出边 conditionExpression 透传到 edge.data(供 toRuleAsset 回写)。
 */
describe('V7.1-2a fromRuleAsset — Gateway 解析', () => {
    const asset: RuleAsset = {
        version: '1.0',
        id: 'gw_asset',
        name: '网关流',
        schema: {name: 'LoanApp', fields: [{name: 'score', type: 'NUMBER'}]},
        flow: {
            id: 'gf', name: 'GF', version: '1.0',
            flowElements: [
                {type: 'startEvent', id: 's', implementation: 'Start:s'},
                {type: 'serviceTask', id: 't1', implementation: 'RuleSet:r1'},
                {type: 'exclusiveGateway', id: 'gw', defaultFlow: 'f_low'},
                {type: 'serviceTask', id: 't2', implementation: 'RuleSet:r2'},
                {type: 'endEvent', id: 'e', implementation: 'Decision:d'},
                {type: 'sequenceFlow', id: 'sf1', sourceRef: 's', targetRef: 't1'},
                {type: 'sequenceFlow', id: 'sf2', sourceRef: 't1', targetRef: 'gw'},
                {type: 'sequenceFlow', id: 'f_high', sourceRef: 'gw', targetRef: 't2', conditionExpression: 'score >= 50'},
                {type: 'sequenceFlow', id: 'f_low', sourceRef: 'gw', targetRef: 'e'},
            ],
        },
        nodes: {
            s: {id: 's', type: 'Start', name: 'Start', schema: 'LoanApp'},
            r1: {id: 'r1', type: 'RuleSet', name: 'R1', hitPolicy: 'FIRST_MATCH', rules: []},
            r2: {id: 'r2', type: 'RuleSet', name: 'R2', hitPolicy: 'FIRST_MATCH', rules: []},
            d: {id: 'd', type: 'Decision', name: 'D', outputs: ['approve'], decisionField: 'decision', defaultOutput: 'approve'},
        },
    };

    // Given 含 exclusiveGateway 的 RuleAsset When fromRuleAsset Then Gateway 解析成 nodeType='Gateway'
    it('exclusiveGateway → nodeType=Gateway(无 implementation,靠 BPMN type)', () => {
        const st = fromRuleAsset(asset);
        const gw = st.nodes.find((n) => n.id === 'gw');
        expect(gw).toBeDefined();
        expect(gw!.data.nodeType).toBe('Gateway');
    });

    // Given gateway.defaultFlow='f_low' When fromRuleAsset Then rfNode.data.defaultFlow 透传
    it('defaultFlow 透传到 Gateway rfNode.data.defaultFlow', () => {
        const st = fromRuleAsset(asset);
        const gw = st.nodes.find((n) => n.id === 'gw');
        expect(gw!.data.defaultFlow).toBe('f_low');
    });

    // Given gateway 出边带 conditionExpression When fromRuleAsset Then edge.data.conditionExpression 透传
    it('Gateway 出边 conditionExpression 透传到 edge.data', () => {
        const st = fromRuleAsset(asset);
        const fHigh = st.edges.find((e) => e.id === 'f_high');
        expect(fHigh).toBeDefined();
        expect((fHigh!.data as {conditionExpression?: string}).conditionExpression).toBe('score >= 50');
        // 普通出边无 condition
        const sf1 = st.edges.find((e) => e.id === 'sf1');
        expect((sf1!.data as {conditionExpression?: string} | undefined)?.conditionExpression).toBeUndefined();
    });

    // Given Gateway 不在 asset.nodes When fromRuleAsset Then nodesMap 不含 Gateway(它是 flow element)
    it('Gateway 不进 nodesMap(它是 flow element 非业务节点)', () => {
        const st = fromRuleAsset(asset);
        expect(st.nodesMap['gw']).toBeUndefined();
        // 业务节点仍在
        expect(st.nodesMap['r1']).toBeDefined();
    });

    it('schemaName 从 asset.schema 解析', () => {
        const st = fromRuleAsset(asset);
        expect(st.schemaName).toBe('LoanApp');
    });
});
