# RuleForge V1 — 统一 AST 设计草案(DRAFT)

> **状态**:DRAFT — 5 块设计待 user 拍板。本文档是讨论锚点,不是最终方案。
>
> **背景**:2026-06-24 user 反馈,V1 要"极简,从零设计"。
> 核心约束:
> - 5 节点 V1(Start / RuleSet / DecisionTable / ScoreCard / Decision),3 节点 MVP 即可上线
> - CEL 只做**条件表达式**,Action 必须**结构化**
> - 主格式 **JSON**,Flow 用 **BPMN 子集**(词汇标准,序列化 JSON)
> - DRL/DMN/PMML 全部降级为 **Import/Export Adapter**,不是核心产品
> - 不存 ReactFlow JSON,存自己的模型
>
> **不参考 urule AST 的原因**:XML 序列化、老 DSL 条件语法、无统一基类。
> **借 urule 的**:4 库概念、RuleSet 三件套、DecisionTable 结构、ScoreCard items/bands。

---

## 目录

1. [Block 1 — AST Schema(5 节点)](#block-1--ast-schema)
2. [Block 2 — Flow 模型(BPMN 子集 JSON)](#block-2--flow-模型bpmn-子集-json)
3. [Block 3 — CEL 边界](#block-3--cel-边界)
4. [Block 4 — Action 结构化](#block-4--action-结构化)
5. [Block 5 — Libraries(4 库)](#block-5--libraries4-库)
6. [打包格式 RuleAsset](#打包格式-ruleasset)
7. [现金贷示例(端到端)](#现金贷示例端到端)
8. [编译到 Rete 的模型](#编译到-rete-的模型)
9. [待拍板的关键决策](#待拍板的关键决策)

---

## Block 1 — AST Schema

### 统一基类

所有节点继承 `NodeBase`。未来 V2 扩 Script/Switch/MLModel/SubFlow,直接 `extends NodeBase` 加 type 即可,执行器用单 dispatch。

```typescript
interface NodeBase {
  id: string;              // 节点内唯一
  type: NodeType;
  name: string;            // 中文名,运营可见
  description?: string;    // 可选备注
}

type NodeType = 'Start' | 'RuleSet' | 'DecisionTable' | 'ScoreCard' | 'Decision';
```

### 1.1 Start — 流程入口 + 输入定义

```typescript
interface StartNode extends NodeBase {
  type: 'Start';
  schema: string;          // 引用 Schema 名(见 Block 5),定义输入 fact 的字段
  inputMapping?: Record<string, string>;  // 可选:fact 字段别名 { "income": "monthlyIncome" }
}
```

JSON 示例:
```json
{
  "id": "start",
  "type": "Start",
  "name": "贷款申请",
  "schema": "LoanApplication"
}
```

### 1.2 RuleSet — 准入 / 反欺诈 / 拒绝 / 运营规则

```typescript
interface RuleSetNode extends NodeBase {
  type: 'RuleSet';
  hitPolicy: 'FIRST_MATCH' | 'ALL_MATCH' | 'PRIORITY';
  rules: Rule[];
}

interface Rule {
  id: string;
  name?: string;
  priority?: number;       // PRIORITY 策略下,数字大先评估;默认 0
  enabled?: boolean;       // 默认 true,可临时禁用
  condition: string;       // CEL,返回 boolean(见 Block 3)
  actions: Action[];       // 结构化,condition 为 true 时执行(见 Block 4)
}
```

JSON 示例(准入拒绝):
```json
{
  "id": "precheck",
  "type": "RuleSet",
  "name": "准入规则",
  "hitPolicy": "FIRST_MATCH",
  "rules": [
    {
      "id": "blacklist",
      "name": "黑名单拒绝",
      "priority": 100,
      "condition": "blacklisted == true",
      "actions": [
        { "type": "reject", "reason": "BLACKLIST" }
      ]
    },
    {
      "id": "underage",
      "name": "未成年拒绝",
      "condition": "age < 18",
      "actions": [
        { "type": "reject", "reason": "UNDERAGE" }
      ]
    }
  ]
}
```

### 1.3 DecisionTable — 额度 / 定价 / 审批矩阵

```typescript
interface DecisionTableNode extends NodeBase {
  type: 'DecisionTable';
  hitPolicy: TableHitPolicy;
  inputs: Column[];
  outputs: Column[];
  rows: TableRow[];
}

type TableHitPolicy = 'FIRST' | 'UNIQUE' | 'PRIORITY' | 'ANY' | 'COLLECT';

interface Column {
  name: string;            // 列名,也是 fact 字段名(AG Grid 表头)
  dataType: 'number' | 'string' | 'boolean';
  direction: 'input' | 'output';
  field?: string;          // 该列绑定的 fact 字段(默认 = name)
}

interface TableRow {
  id: string;
  conditions: string[];    // 每个输入列一个 CEL 表达式;'*' = 通配(任意)
  outputs: CellValue[];    // 每个输出列一个字面量
  annotation?: string;     // 行标签(运营备注)
}

type CellValue = number | string | boolean | null;
```

JSON 示例(定价矩阵):
```json
{
  "id": "pricing",
  "type": "DecisionTable",
  "name": "定价矩阵",
  "hitPolicy": "FIRST",
  "inputs": [
    { "name": "score", "dataType": "number", "direction": "input" },
    { "name": "income", "dataType": "number", "direction": "input" }
  ],
  "outputs": [
    { "name": "decision", "dataType": "string", "direction": "output" }
  ],
  "rows": [
    { "id": "r1", "conditions": ["score < 500", "*"],          "outputs": ["reject"],  "annotation": "低分直接拒" },
    { "id": "r2", "conditions": ["score >= 500 && score < 650", "income > 10000"], "outputs": ["review"], "annotation": "中等分复审" },
    { "id": "r3", "conditions": ["score >= 650", "income > 10000"], "outputs": ["approve"], "annotation": "高分通过" }
  ]
}
```

> **说明**:`'FIRST'` = 第一行命中即停(像 switch);`'COLLECT'` = 所有命中行输出聚合;`'UNIQUE'` = 必须恰好一行命中,否则报错。跟 DMN 命中策略对齐,方便 DMN Adapter。

### 1.4 ScoreCard — 风险分 / 营销分 / 额度分 / 行为分

```typescript
interface ScoreCardNode extends NodeBase {
  type: 'ScoreCard';
  output: string;          // 结果写入的 fact 字段名
  aggregation: 'SUM' | 'AVG' | 'MIN' | 'MAX' | 'WEIGHTED_SUM';
  cards: Card[];           // 每个 card 对一个字段打分
}

interface Card {
  id: string;
  field: string;           // 评估的字段,如 "age"
  weight?: number;         // WEIGHTED_SUM 下的权重,默认 1
  bands: Band[];           // 分段(按顺序,首个命中取分)
}

interface Band {
  id: string;
  condition: string;       // CEL,返回 boolean,如 "age < 25"
  score: number;
  reasonCode?: string;     // 可选理由码(对齐 PMML Scorecard)
}
```

JSON 示例(风险分):
```json
{
  "id": "risk",
  "type": "ScoreCard",
  "name": "风险评分卡",
  "output": "riskScore",
  "aggregation": "SUM",
  "cards": [
    {
      "id": "age_card",
      "field": "age",
      "bands": [
        { "id": "b1", "condition": "age < 25",  "score": 20, "reasonCode": "YOUNG" },
        { "id": "b2", "condition": "age >= 25", "score": 50 }
      ]
    },
    {
      "id": "income_card",
      "field": "income",
      "bands": [
        { "id": "b3", "condition": "income < 5000",  "score": 10 },
        { "id": "b4", "condition": "income >= 5000", "score": 40 }
      ]
    }
  ]
}
```

### 1.5 Decision — 流程出口 / 最终决策

```typescript
interface DecisionNode extends NodeBase {
  type: 'Decision';
  outputs: string[];           // 允许的最终结果集合,如 ["approve","review","reject"]
  decisionField?: string;      // 读取哪个 fact 字段作为决策值,默认 "decision"
  defaultOutput?: string;      // 流程跑完 decisionField 未被 setDecision 设置时的兜底
}
```

**语义**:Decision 是流程终点(endEvent)。它**不自己算**决策 —— 决策值由上游的 `setDecision` action 写入 `decisionField`。Decision 节点的职责是:
1. 校验 `decisionField` 的值 ∈ `outputs`(否则报错)
2. 把该值作为流程最终结果 emit 出去
3. 若 `decisionField` 从未被设置 → 用 `defaultOutput`(或报错)

JSON 示例:
```json
{
  "id": "decision",
  "type": "Decision",
  "name": "最终决策",
  "decisionField": "decision",
  "outputs": ["approve", "review", "reject"],
  "defaultOutput": "review"
}
```

---

## Block 2 — Flow 模型(BPMN 子集 JSON)

### 设计原则

- **词汇用 BPMN**(行业标准,BA/PM 认得)
- **序列化用 JSON**(不是 BPMN XML)
- **5 个元素,跟 V1 节点 1:1 映射**,不暴露 BPMN 2.0 全集(100+ 元素)
- **位置信息单独存**,不混进业务模型;运行时忽略位置

```typescript
interface Flow {
  id: string;
  name: string;
  version: string;             // "1.0"
  flowElements: FlowElement[];
}

type FlowElement =
  | StartEvent | ServiceTask | ExclusiveGateway | EndEvent | SequenceFlow;

interface FlowElementBase {
  $type: string;
  id: string;
  name?: string;
  position?: { x: number; y: number };  // 画布坐标,presentation-only,运行时忽略
}

interface StartEvent extends FlowElementBase {
  $type: 'startEvent';
  implementation: string;      // "Start:<nodeId>"
}

interface ServiceTask extends FlowElementBase {
  $type: 'serviceTask';
  implementation: string;      // "<NodeType>:<nodeId>",如 "RuleSet:precheck"
}

interface ExclusiveGateway extends FlowElementBase {
  $type: 'exclusiveGateway';
  defaultFlow?: string;        // 无 condition 命中时走的出边 id(二选一兜底)
}

interface EndEvent extends FlowElementBase {
  $type: 'endEvent';
  implementation: string;      // "Decision:<nodeId>"
}

interface SequenceFlow extends FlowElementBase {
  $type: 'sequenceFlow';
  sourceRef: string;
  targetRef: string;
  conditionExpression?: string; // CEL,仅 gateway 出边评估(普通出边无条件)
}
```

### V1 palette(用户在画布上看到的)

```
○ Start              (startEvent)
▢ RuleSet            (serviceTask)
▢ DecisionTable      (serviceTask)
▢ ScoreCard          (serviceTask)
◎ Decision           (endEvent)
◇ Gateway            (exclusiveGateway)
```

**6 个图标**,不暴露 BPMN 全集。Gateway V1 可省(MVP 3 节点用线性流程),但留作 V1 完整版的分流手段。

### 复用 ruleforge-decision 引擎

`ruleforge-decision` 已是自建 BPMN 2.0 引擎。V1 的 Flow:
- **保存时**:JSON → 转 BPMN 2.0 XML → 喂给 ruleforge-decision 引擎跑
- **节点映射**:`serviceTask.implementation` 引用 `nodes{}` 里的 V1 节点定义,引擎执行时反查并调用对应节点执行器
- **老 .rl.xml 兼容**:现有 ruleforge-decision 流程继续 work

---

## Block 3 — CEL 边界

### CEL 只能出现在 4 个字段

| 字段 | 语义 |
|---|---|
| `RuleSet.rules[].condition` | 规则命中条件 |
| `DecisionTable.rows[].conditions[]` | 表行每个输入列的命中条件 |
| `ScoreCard.cards[].bands[].condition` | 分段命中条件 |
| `SequenceFlow.conditionExpression` | **仅 gateway 出边**的分流条件 |

### CEL 必须

- 返回 **boolean**(gateway/condition)或 **number/string/boolean**(表行输出、ScoreCard score 这些是字面量字段不是 CEL)
- **只读** fact 字段:`applicant.age`、`score`、`income`
- 算术 / 比较 / 逻辑运算
- 类型转换
- 白名单函数:`contains(list, x)`、`matches(str, regex)`、字符串长度 / 数学函数 / 日期比较

### CEL 禁止

- ❌ 修改变量(CEL 语法本身无赋值,pure expression)
- ❌ 调用副作用函数(无 I/O、无网络)
- ❌ 循环、控制流
- ❌ 出现在 Action 字段(Action 见 Block 4,纯结构化)

### 前端:React Query Builder ↔ CEL 双向

- 运营用 **React Query Builder** 拼可视化条件 → 生成 CEL 字符串存入 `condition`
- 高级用户可切 **Monaco** 直接写 CEL,保存时解析校验语法
- CEL ↔ combinator JSON 双向 codegen(React Query Builder 原生支持)

### V1 函数白名单

```
contains(list, value)          — 列表包含
matches(string, regex)         — 正则匹配
string.length                  — 字符串长度
abs / round / floor / ceil / min / max  — 数学
now() / today()                — 当前时间(只读)
```

不在白名单的 → 编译期拒绝。

---

## Block 4 — Action 结构化

### 设计原则(原话)

> 不要让 CEL 修改变量。Action 应该结构化。

Action **永远不含 CEL**。`value` 是字面量或字段引用(只读)。

```typescript
interface Action {
  type: ActionType;
  target?: string;                       // 写入的 fact 字段名
  value?: number | string | boolean | { $ref: string };  // 字面量 或 字段引用(只读)
  reason?: string;                       // 审计理由
}

type ActionType =
  | 'setVariable'    // 写 fact 字段: { type, target, value }
  | 'addScore'       // 给 score 字段加分: { type, target, value:number }
  | 'setDecision'    // 设最终决策(Decision 节点读): { type, value:string }
  | 'reject'         // 硬终止 + 理由(终端): { type, reason }
  | 'flag';          // 加一条风险标记到结果 flags[]: { type, reason }
```

### V1 五种 Action 覆盖现金贷场景

| Action | 用途 | 现金贷示例 |
|---|---|---|
| `setVariable` | 写入字段 | 定价后写 `rate = 0.18` |
| `addScore` | 加分到 score 字段 | 营销分累加 |
| `setDecision` | 设最终决策值 | 综合判断后 `setDecision "approve"` |
| `reject` | 硬拒(终端) | 准入 `reject "BLACKLIST"` |
| `flag` | 风险标记(非终端) | 触发反欺诈 flag |

JSON 示例:
```json
{ "type": "setVariable", "target": "rate",   "value": 0.18 }
{ "type": "addScore",    "target": "marketingScore", "value": 20 }
{ "type": "setDecision", "value": "approve" }
{ "type": "reject",      "reason": "BLACKLIST" }
{ "type": "flag",        "reason": "DEVICE_FRAUD" }
```

> **`value` 允许字段引用**(`{ $ref: "riskScore" }`)但不允许 CEL 表达式。需要计算 → 放 ScoreCard 或新节点,不塞进 Action。这是"Action 结构化"的硬约束。

---

## Block 5 — Libraries(4 库)

### 借 urule 的 4 库概念

| 库 | 用途 | V1 必要性 |
|---|---|---|
| **Variable Library (vl)** | fact 字段定义(name/type/label)= 输入 schema | **V1 必需**(Start.schema 引用) |
| **Constant Library (cl)** | 命名常量,如 `MAX_LOAN = 500000` | V1 可省(CEL 内联字面量) |
| **Parameter Library (pl)** | 运行时可调参数 / 阈值(不重发版改) | V1 可省(先硬编码) |
| **Action Library (al)** | 自定义 server-side action(超出 5 种内置) | V1 可省(V2 加 Script 节点时再开) |

### V1 Schema 替代 vl(精简)

V1 不引入完整 vl/cl/pl/al 四库系统,只用一个 **Schema** 定义 fact 结构(等价于精简版 vl)。Start.schema 引用它:

```typescript
interface Schema {
  name: string;              // 如 "LoanApplication"
  fields: SchemaField[];
}

interface SchemaField {
  name: string;              // "age"
  type: 'number' | 'string' | 'boolean' | 'list';
  label?: string;            // 中文标签,UI 展示 + CEL 自动补全
  required?: boolean;
}
```

JSON 示例:
```json
{
  "name": "LoanApplication",
  "fields": [
    { "name": "age",         "type": "number",  "label": "年龄",     "required": true },
    { "name": "income",      "type": "number",  "label": "月收入",   "required": true },
    { "name": "score",       "type": "number",  "label": "征信分" },
    { "name": "blacklisted", "type": "boolean", "label": "是否黑名单" },
    { "name": "decision",    "type": "string",  "label": "最终决策" }
  ]
}
```

> **决策**:V1 只做 Schema(vl 精简),cl/pl/al 留 V1.1+。这样 V1 复杂度最小,4 库概念保留但渐进引入。

---

## 打包格式 RuleAsset

一个 RuleForge 资产 = 一个 JSON 文件,后缀直接 **`.json`**(不造 `.rf.json` / `.ruleflow` 等专属后缀,内容靠顶层 `version` 字段自识别;对齐 GoRules/n8n 做法)。

```typescript
interface RuleAsset {
  version: '1.0';
  id: string;                  // 资产 id(全局唯一)
  name: string;
  flow: Flow;                  // 画布编排(BPMN 子集)
  nodes: Record<string, NodeBase>;  // nodeId → 节点定义(平铺,flow 按 id 引用)
  schema?: Schema;             // 输入 fact 结构(替代 vl)
  metadata?: {
    createdAt?: string;
    updatedAt?: string;
    author?: string;
    tags?: string[];
  };
}
```

- **flow 和 nodes 分离**:flow 只管编排(谁连谁),nodes 管业务逻辑(规则内容)。这样 RuleSet 节点可在多个 flow 复用。
- **不存 ReactFlow JSON**:position 是可选 presentation 字段,运行时忽略;ReactFlow 的 node/edge/data/selected 等不持久化,由 RuleAsset 派生。
- **Adapter 入口**:DRL/DMN/PMML Adapter 读取老格式 → 产出 RuleAsset JSON。

---

## 现金贷示例(端到端)

完整 RuleAsset:`loan_approval.json`

```json
{
  "version": "1.0",
  "id": "loan_approval",
  "name": "贷款审批流程",
  "schema": {
    "name": "LoanApplication",
    "fields": [
      { "name": "age", "type": "number", "label": "年龄" },
      { "name": "income", "type": "number", "label": "月收入" },
      { "name": "score", "type": "number", "label": "征信分" },
      { "name": "blacklisted", "type": "boolean", "label": "黑名单" },
      { "name": "riskScore", "type": "number", "label": "风险分" },
      { "name": "decision", "type": "string", "label": "决策" },
      { "name": "flags", "type": "list", "label": "风险标记" }
    ]
  },
  "flow": {
    "id": "loan_approval_flow",
    "name": "Loan Approval",
    "version": "1.0",
    "flowElements": [
      { "$type": "startEvent",      "id": "start",  "name": "开始", "implementation": "Start:start", "position": {"x":50,"y":200} },
      { "$type": "serviceTask",     "id": "t_pre",  "name": "准入", "implementation": "RuleSet:precheck", "position": {"x":220,"y":200} },
      { "$type": "serviceTask",     "id": "t_risk", "name": "风险评分", "implementation": "ScoreCard:risk", "position": {"x":400,"y":200} },
      { "$type": "serviceTask",     "id": "t_dt",   "name": "定价", "implementation": "DecisionTable:pricing", "position": {"x":580,"y":200} },
      { "$type": "endEvent",        "id": "end",    "name": "决策", "implementation": "Decision:decision", "position": {"x":760,"y":200} },
      { "$type": "sequenceFlow",    "id": "f1", "sourceRef": "start",  "targetRef": "t_pre" },
      { "$type": "sequenceFlow",    "id": "f2", "sourceRef": "t_pre",  "targetRef": "t_risk" },
      { "$type": "sequenceFlow",    "id": "f3", "sourceRef": "t_risk", "targetRef": "t_dt" },
      { "$type": "sequenceFlow",    "id": "f4", "sourceRef": "t_dt",   "targetRef": "end" }
    ]
  },
  "nodes": {
    "start":    { "id": "start", "type": "Start", "name": "贷款申请", "schema": "LoanApplication" },
    "precheck": {
      "id": "precheck", "type": "RuleSet", "name": "准入规则", "hitPolicy": "FIRST_MATCH",
      "rules": [
        { "id": "blacklist", "priority": 100, "condition": "blacklisted == true",
          "actions": [ { "type": "reject", "reason": "BLACKLIST" } ] },
        { "id": "underage", "condition": "age < 18",
          "actions": [ { "type": "reject", "reason": "UNDERAGE" } ] }
      ]
    },
    "risk": {
      "id": "risk", "type": "ScoreCard", "name": "风险评分卡", "output": "riskScore", "aggregation": "SUM",
      "cards": [
        { "id": "age_card", "field": "age",
          "bands": [
            { "id": "b1", "condition": "age < 25",  "score": 20 },
            { "id": "b2", "condition": "age >= 25", "score": 50 }
          ] }
      ]
    },
    "pricing": {
      "id": "pricing", "type": "DecisionTable", "name": "定价矩阵", "hitPolicy": "FIRST",
      "inputs": [
        { "name": "riskScore", "dataType": "number", "direction": "input" },
        { "name": "score", "dataType": "number", "direction": "input" }
      ],
      "outputs": [ { "name": "decision", "dataType": "string", "direction": "output" } ],
      "rows": [
        { "id": "r1", "conditions": ["riskScore < 30", "*"], "outputs": ["approve"] },
        { "id": "r2", "conditions": ["*", "*"], "outputs": ["review"] }
      ]
    },
    "decision": {
      "id": "decision", "type": "Decision", "name": "最终决策",
      "decisionField": "decision",
      "outputs": ["approve", "review", "reject"],
      "defaultOutput": "review"
    }
  }
}
```

**执行轨迹**(输入 `{age:30, income:8000, score:700, blacklisted:false}`):
1. `start` → 加载 LoanApplication fact
2. `precheck`(FIRST_MATCH):黑名单 false、age 30 → 无命中,继续
3. `risk`(SUM):age>=25 → riskScore = 50
4. `pricing`(FIRST):riskScore 50 >= 30? r1 条件 `riskScore < 30` 不成立 → r2 命中 → decision = "review"
5. `decision`:decisionField = "review" ∈ outputs → emit "review"

---

## 编译到 Rete 的模型

> User 原话:**"最难的不是前端,而是统一 AST 和编译到 Rete 的模型。"**
>
> **决策(D4):MVP 直接编译到 Rete**(现金贷风控高吞吐场景,性能从第一天就要达标)。

V1 执行策略:**Rete 为主,解释器兜底**

### Rete 编译通道(主路径)

把 V1 AST 的条件-动作编译进现有 `ruleforge-core` RETE 引擎(alpha/beta 网络)。这是 MVP 核心交付,不是可选优化。

| V1 概念 | Rete 映射 |
|---|---|
| `RuleSet.rules[].condition` | RETE criteria(alpha/beta node) |
| `RuleSet.rules[].actions` | RETE action(RHS) |
| `DecisionTable.rows[].conditions` | 同 RuleSet(每行 = 一条 rule,inputs 编成 criteria) |
| `DecisionTable.outputs` | RETE RHS 的 setVariable |
| `hitPolicy` | RETE agenda 策略(FIRST = 单 fire 取首;ALL/PRIORITY = 多 fire 排序;UNIQUE/COLLECT = 编译期 + 运行期校验) |
| `Action`(5 种) | 翻译成 RETE RHS:`setVariable`→值设置,`addScore`→累加,`setDecision`→写 decisionField,`reject`→设拒绝 + 终止信号,`flag`→追加 flags[] |

### 不进 Rete 的(独立执行器)

| V1 概念 | 执行方式 |
|---|---|
| `ScoreCard` | 独立解释器:遍历 cards → 命中 band 取分 → aggregation(SUM/AVG/MIN/MAX/WEIGHTED_SUM)写 output 字段。分段求值不是 RETE 的强项 |
| `Decision` | emit 逻辑:校验 decisionField ∈ outputs,emit 最终结果 |
| `Flow` | `ruleforge-decision` BPMN 引擎编排(每个 serviceTask = 一个 V1 节点执行:RuleSet/DecisionTable 走 Rete 知识包,ScoreCard 走独立解释器) |

### CEL → RETE criteria 翻译(关键)

CEL 表达式(`age >= 18 && score > 600`)需翻译成 RETE 现有 criteria 模型。两种实现路径,MVP 选其一:

- **A. CEL → 老 DSL 中间表示 → RETE criteria**:复用 ruleforge-core 现有 DRL/criteria 解析器,CEL 先降级成老 DSL 的 `Criterion`(Left + Op + Value),再走现有 RETE builder。**复用最大化,MVP 快**。
- **B. CEL → 直接构建 RETE criteria AST**:绕过老 DSL,CEL AST 直接映射 criteria。更干净但要写新桥。

**MVP 选 A**(复用 ruleforge-core 现有解析链,把 CEL 当一个新前端表达式语言接入),B 留作 V1.1 重构。CEL 引擎用 Google `cel-java`(Apache 2.0,reference 实现)。

### 关键风险

Rete 编译通道是 MVP critical path。若 `cel-java` 接入 + criteria 翻译比预期复杂,Week 2 端到端可能受压。**缓解**:Week 1 先打通"硬编码 condition(不经 CEL)→ RETE → fire"最小链路验证引擎可用,再接 CEL。

---

## 关键决策(已拍板)

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D1 | Decision 节点语义 | **只 emit,不算决策** | 决策由 setDecision action 驱动,Decision 校验 decisionField ∈ outputs 并 emit |
| D2 | Action.value 类型 | **字面量 + 字段引用 `{ $ref }`,不含 CEL** | 计算逻辑放节点 |
| D3 | MVP 节点数 | **3 业务节点**(RuleSet + DecisionTable + ScoreCard)+ Start/Decision | 覆盖 30%+40%+30% |
| **D4** | **MVP 执行引擎** | **直接编译到 Rete** | 现金贷高吞吐,性能 day-1 达标;复用 ruleforge-core RETE;CEL 用 cel-java |
| D5 | Flow 词汇 | **BPMN 子集** | 复用 ruleforge-decision |
| D6 | 位置信息 | **flow element 可选 position,presentation-only** | 运行时忽略 |
| D7 | V1 库 | **只 Schema(vl 精简)** | cl/pl/al 留 V1.1+ |
| **D8** | **资产后缀** | **`.json`** | 不造 `.rf.json` / `.ruleflow` 专属后缀,内容靠顶层 `version` 字段自识别;对齐 GoRules/n8n |
| **R1** | **MVP 目标用户** | **现金贷风控团队**(已知场景) | Schema 按贷款申请 fact 设计;3 节点够覆盖 |
| **R2** | **MVP 范围** | **只做"画+编译+执行"** | 陪跑测试/决策日志/版本发布/权限复用现有 RuleForge 基础设施 |

---

下面进入 V1 MVP 实施计划。

---

## V1 MVP 实施计划(3 周)

> **版本**:`V7.0.0`(Major bump — 架构级重设计,V6.x → V7)
> **分支**:`feature/V7.0.0-v1-mvp`
> **范围**:3 业务节点(RuleSet + DecisionTable + ScoreCard)+ Start/Decision + Rete 编译 + React Flow 画布;陪跑/日志/发布/权限复用现有基础设施
> **目标客户**:现金贷风控团队
> **交付物**:能画一个完整现金贷决策流 → 保存 `.json` → Rete 编译执行 → 拿到 decision 结果

### Week 1 — 后端:AST + CEL + Rete 编译核心

| 子任务 | 内容 | 验证 |
|---|---|---|
| W1-1 | V1 AST Java model:`NodeBase` + 5 节点 + `Flow` + `Schema` + `RuleAsset`;Jackson JSON load/save(`.json`) | BDD:load `loan_approval.json` → 各节点字段正确 |
| W1-2 | `cel-java`(dev.cel,Apache 2.0)接入 + CEL 解析校验(必须返回 boolean,白名单函数) | BDD:`age >= 18` parse OK;`x = 1`(赋值)拒绝 |
| W1-3 | **CEL → RETE criteria 编译通道**(路径 A:CEL → 老 DSL `Criterion` 中间表示 → 现有 RETE builder) | BDD:CEL expr 编出的 criteria 跟手写 DRL 等价 RETE 一致 |
| W1-4 | Action → RETE RHS 翻译(5 种:setVariable/addScore/setDecision/reject/flag) | BDD:每个 action 在 fact 上产生预期副作用 |
| W1-5 | 冒烟测试:硬编码 condition → RETE → fire(不依赖 CEL,先验证引擎链路通) | 单测:RET E fire,action 执行 |

**Critical path:W1-3**(CEL → RETE criteria)。风险:cel-java 接入复杂度。缓解:W1-5 先用硬编码 condition 打通链路,W1-3 再接 CEL。

### Week 2 — 后端:节点执行器 + Flow runner + 端到端

| 子任务 | 内容 | 验证 |
|---|---|---|
| W2-1 | RuleSet 编译器:rules → RETE knowledge package,hitPolicy(FIRST_MATCH/ALL_MATCH/PRIORITY)→ RETE agenda | BDD:FIRST_MATCH 取首;PRIORITY 按 priority 排序 |
| W2-2 | DecisionTable 编译器:rows → RETE rules(inputs → criteria,outputs → RHS),hitPolicy(FIRST/UNIQUE/PRIORITY/COLLECT) | BDD:定价矩阵表 → 命中行输出 decision |
| W2-3 | ScoreCard 独立执行器:cards → bands 命中取分 → aggregation(SUM/AVG/MIN/MAX/WEIGHTED_SUM)写 output | BDD:风险分卡 → riskScore 正确 |
| W2-4 | Decision emit + reject 终止信号(decisionField ∈ outputs 校验) | BDD:reject 终止流程;Decision emit 正确值 |
| W2-5 | Flow runner:ruleforge-decision BPMN 编排,serviceTask → 节点执行器(RuleSet/DecisionTable 走 Rete 包,ScoreCard 走独立执行器) | BDD:5 节点线性 flow 按序执行 |
| W2-6 | **端到端集成测试**:`loan_approval.json` → compile → execute → assert decision("review") | BDD:完整现金贷轨迹 |

### Week 3 — 前端 MVP

| 子任务 | 内容 | 验证 |
|---|---|---|
| W3-1 | 脚手架:V1 编辑器路由 + 依赖(`reactflow`/`ag-grid-community`/`react-querybuilder`/`shadcn`) | 依赖装好,空白页 render |
| W3-2 | React Flow 画布:5 节点(BPMN 子集 icons)+ 连线 + position 持久化 → `.json` | Vitest:画 5 节点连线 → 序列化 JSON 含 flowElements |
| W3-3 | AG Grid DecisionTable 编辑器:inputs/outputs columns + rows(CEL 单元格) | Vitest:编辑表行 → 存回 DecisionTable node |
| W3-4 | React Query Builder → CEL(RuleSet condition)+ Monaco CEL 高级模式(切换) | Vitest:可视化条件 → CEL 字符串双向 |
| W3-5 | shadcn/ui 属性 Drawer:节点选中 → 右侧改配置(name/hitPolicy/rules/cards) | Vitest:选中节点 → Drawer 显示配置 |
| W3-6 | **端到端 E2E**(Playwright admin/admin123):画 flow → 保存 → 后端编译执行 → 看 decision | E2E live 通过 |

### 风险 + 缓解

| 风险 | 缓解 |
|---|---|
| **CEL → RETE criteria 编译** 比预期复杂(Week 1 critical path) | W1-5 先硬编码 condition 打通引擎链路;W1-3 再接 cel-java。若 W1 末编译不通,Week 2 前置 W1-3 |
| ruleforge-decision BPMN 接入新 serviceTask 类型 | Week 2 先验证单节点执行器可独立调,再接 BPMN 编排 |
| 前端 3 库(reactflow/ag-grid/rqb)集成量大 | Week 3 拆 5 子任务,每个独立可测;先用最小 mock 数据,后接真实 API |

### 不在 MVP 范围(明确推迟)

- ❌ Gateway 分流节点(V1 完整版再做,现金贷线性流程够用)
- ❌ Script / Switch / MLModel / SubFlow(V2)
- ❌ cl/pl/al 4 库(V1.1+,只做 Schema)
- ❌ DRL/DMN/PMML Import/Export Adapter(V1.1+)
- ❌ 陪跑测试 / 决策日志 / 版本发布 / 权限(复用现有)
- ❌ P4 物理删老编辑器(V1 出来后自然替代,不阻塞)

### 版本边界

- MVP 完成 = `V7.0.0` release
- V1 完整(5 节点 + Gateway)= `V7.1.0`
- Adapter(DRL/DMN/PMML 导入导出)= `V7.2.0`
- 4 库 = `V7.3.0`

---

**确认 plan + 开分支开做,我就建 `feature/V7.0.0-v1-mvp` 开 Week 1。**
