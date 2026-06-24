import FlowDesigner from './FlowDesigner';

/**
 * V1 决策流设计器 SPA 路由入口(V7.0.0)。
 *
 * <p>URL:{@code /app/v1-flow}。从 console-ui-v1 demo 骨架并进 console-ui(单 app + antd)。
 * V1 画布(React Flow + 5 节点 BPMN 子集)+ RuleAsset 序列化(后端 V1FlowRunner 可执行)。
 *
 * <p>MVP:画布 + 序列化完整;节点内容编辑器(AG Grid 决策表 / RQB 规则 / 属性 Drawer)待续。
 */
export default function EditorRoute() {
    return <FlowDesigner/>;
}
