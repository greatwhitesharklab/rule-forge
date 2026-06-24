import {Handle, Position} from '@xyflow/react';
import type {NodeType} from './ruleAsset';

/** V1 节点 BPMN 子集图标 + 配色。不暴露 BPMN 全集,5 种业务节点。 */
const NODE_STYLE: Record<NodeType, {icon: string; color: string; label: string}> = {
  Start: {icon: '○', color: '#52c41a', label: 'Start'},
  RuleSet: {icon: '⚖', color: '#1677ff', label: 'RuleSet'},
  DecisionTable: {icon: '▦', color: '#722ed1', label: 'DecisionTable'},
  ScoreCard: {icon: '★', color: '#fa8c16', label: 'ScoreCard'},
  Decision: {icon: '◎', color: '#eb2f96', label: 'Decision'},
};

export interface V1NodeData {
  nodeType: NodeType;
  name: string;
  implementation: string;
  [key: string]: unknown;
}

export function V1FlowNode({data}: {data: V1NodeData}) {
  const style = NODE_STYLE[data.nodeType];
  const isStart = data.nodeType === 'Start';
  const isEnd = data.nodeType === 'Decision';
  return (
    <div
      data-testid={`v1-node-${data.nodeType}`}
      style={{
        padding: '10px 16px',
        borderRadius: isStart || isEnd ? 40 : 8,
        border: `2px solid ${style.color}`,
        background: '#fff',
        minWidth: 120,
        textAlign: 'center',
        fontSize: 13,
        boxShadow: '0 2px 6px rgba(0,0,0,0.1)',
      }}
    >
      {!isStart && <Handle type="target" position={Position.Top} style={{background: style.color}}/>}
      <div style={{fontWeight: 600, color: style.color}}>
        {style.icon} {style.label}
      </div>
      <div style={{marginTop: 4, color: '#333'}}>{data.name}</div>
      {!isEnd && <Handle type="source" position={Position.Bottom} style={{background: style.color}}/>}
    </div>
  );
}

export const nodeTypes = {v1: V1FlowNode};

/** palette(用户拖拽的 6 种节点,MVP 线性流程够用)。 */
export const PALETTE: NodeType[] = ['Start', 'RuleSet', 'DecisionTable', 'ScoreCard', 'Decision'];
