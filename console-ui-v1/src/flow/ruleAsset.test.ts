import {describe, it, expect} from 'vitest';
import type {RuleAsset} from './ruleAsset';

/**
 * W3-2 — RuleAsset 序列化契约。FlowEditor.toRuleAsset(内部)产出的 JSON
 * 必须跟后端 com.ruleforge.v1.ast 格式对齐(nodes Map + flow.flowElements BPMN 子集)。
 * 这里测 RuleAsset 类型契约 + 一个手工构造的现金贷资产结构正确。
 */
describe('W3-2 RuleAsset 序列化契约', () => {
  it('RuleAsset 含 nodes Map + flow.flowElements BPMN 子集 + version 自识别', () => {
    // 模拟 FlowEditor 导出的最小资产(Start + RuleSet + Decision 线性)
    const asset: RuleAsset = {
      version: '1.0',
      id: 'a1',
      name: 'flow',
      flow: {
        id: 'f1', name: 'Flow', version: '1.0',
        flowElements: [
          {type: 'startEvent', id: 'start', implementation: 'Start:start', position: {x: 0, y: 0}},
          {type: 'serviceTask', id: 'pre', implementation: 'RuleSet:pre', position: {x: 0, y: 100}},
          {type: 'endEvent', id: 'end', implementation: 'Decision:end', position: {x: 0, y: 200}},
          {type: 'sequenceFlow', id: 'e1', sourceRef: 'start', targetRef: 'pre'},
          {type: 'sequenceFlow', id: 'e2', sourceRef: 'pre', targetRef: 'end'},
        ],
      },
      nodes: {
        start: {id: 'start', type: 'Start', name: 'Start', schema: 'LoanApplication'},
        pre: {id: 'pre', type: 'RuleSet', name: '准入', hitPolicy: 'FIRST_MATCH', rules: []},
        end: {id: 'end', type: 'Decision', name: 'Decision', outputs: ['approve', 'reject']},
      },
    };
    // 版本自识别
    expect(asset.version).toBe('1.0');
    // flow 3 节点元素 + 2 边
    const events = asset.flow.flowElements.filter((e) => e.type !== 'sequenceFlow');
    const flows = asset.flow.flowElements.filter((e) => e.type === 'sequenceFlow');
    expect(events).toHaveLength(3);
    expect(flows).toHaveLength(2);
    // implementation 引用 node id
    expect(events[1].implementation).toBe('RuleSet:pre');
    // nodes Map 按 id 引用
    expect(asset.nodes['pre'].type).toBe('RuleSet');
    // 序列化往返稳定(JSON 可往返,后端能解析)
    const json = JSON.stringify(asset);
    const back = JSON.parse(json) as RuleAsset;
    expect(back.nodes['end'].type).toBe('Decision');
    expect(back.flow.flowElements).toHaveLength(5);
  });
});
