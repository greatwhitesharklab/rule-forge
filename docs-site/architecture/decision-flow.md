---
title: 决策流引擎
---

# 决策流引擎(V1)

RuleForge 的决策流编排基于自研的 **V1 决策流**——极简 6 节点 + CEL 条件表达式,
线性 + 排他网关的图遍历执行。V1 是 V7.21 起的**唯一决策路径**(老 BPMN 引擎已彻底删除)。

> **V7.21 架构变更**:此前项目使用自建 BPMN 2.0 引擎(FlowEngine,V5.21-V5.39 开发)。
> V7.21 彻底删除 BPMN 引擎与 ruleforge-decision 模块,V1 决策流取而代之。
> V1 故意只做"线性 + 排他网关"子集,不承接完整 BPMN(并行 / 异步 / 补偿 SAGA 等复杂编排)。

## V1 节点类型

V1 画布(`react-flow` 实现,`/v1-flow` 路由)支持 6 种业务节点 + Gateway:

| 节点类型 | BPMN 映射 | 功能 |
|---------|-----------|------|
| Start | startEvent | 流程入口,声明输入 fact 的 Schema |
| RuleSet | serviceTask | 规则集(RETE fire),命中策略 FIRST_MATCH / ALL_MATCH |
| DecisionTable | serviceTask | 决策表(DMN 1.3 子集),表格化条件匹配 |
| ScoreCard | serviceTask | 评分卡(PMML 4.4),加权评分 |
| Decision | endEvent | 流程出口,输出决策结果(approve/review/reject 等) |
| Gateway | exclusiveGateway | 排他网关,CEL 条件路由 + defaultFlow 兜底 |

规则节点(RuleSet/DecisionTable/ScoreCard)支持 `ruleRef`——引用独立规则文件
(`.v1ruleset.json` / `.v1decisiontable.json` / `.v1scorecard.json`),而非内嵌,
实现决策流与规则的解耦编辑。

## 执行流程

V1 执行器 `V1FlowRunner`(在 `ruleforge-core` 模块,纯静态方法,零依赖外部模块):

1. 加载 RuleAsset JSON(画布导出格式:flow.flowElements + nodes)
2. 按拓扑顺序遍历:startEvent → serviceTask* → exclusiveGateway(CEL 条件选路)→ endEvent
3. 每个规则节点构建独立 RETE 会话执行规则
4. Gateway 节点用 CEL 求值出边条件,首条命中或 defaultFlow 决定下一节点
5. 到达 endEvent 收集决策结果

```
Start → RuleSet → Gateway ─(条件 A)→ Decision(approve)
                    │
                    └─(default)─→ Decision(reject)
```

## REST 端点

| 端点 | 作用 | 位置 |
|------|------|------|
| `POST /v1/execute` | 执行画布 RuleAsset(demo / 调试,asset 内联) | console `controller/v1/V1ExecutionController` |
| `POST /v1/publish` | 发布决策流(冻结闭包 bundle 入 `rf_v1_publish` + git tag) | console `controller/v1/V1PublishController` |
| `POST /v1/exec` | 生产执行已发布流(从 console 拉冻结 bundle) | executor `executor/v1/V1ExecutionController` |

## 发布模型

V1 发布 = 冻结闭包(bundle = `{asset, libraries, ruleFiles}` 序列化快照),
存入 `rf_v1_publish.publish_bundle`(不可变),版本号 fix 位递增(1.0.0 → 1.0.1)。
executor 经 `GET /v1/publish/bundle` 直取最新版本执行,无审批 / 灰度 / 陪跑(V1 故意砍掉)。

## 与规则引擎的集成

V1 决策流中的规则节点复用 ruleforge-core 的 RETE 引擎:
- RuleSet 节点 → `RuleSetCompiler` 编译 + `KnowledgeSession.fireRules()`
- DecisionTable 节点 → 决策表匹配
- ScoreCard 节点 → 加权评分计算

四库(变量库 vl / 常量库 cl / 参数库 pl / 动作库 al)通过 `V1LibraryResolver` 加载,
CEL 条件表达式经 `CelEngine`(Google CEL)求值。
