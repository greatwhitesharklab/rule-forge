# neural-engine 架构设计

> 参照 `experiments/server-rust/ARCHITECTURE.md` 惯例:以设计决策点为主线。
> 前置阅读:`README.md`(定位与 Engram 概念映射)。

## 0. 总览

```
决策路径(读)                          学习路径(写)
─────────────                        ─────────────
申请特征 x
   │
   ▼
┌─────────────────┐
│ 特征交叉器       │  单特征 + 二阶交叉 + 业务模式片段
│ (多粒度 pattern) │
└────────┬────────┘
         ▼
┌─────────────────┐     ┌──────────────────────────┐
│ 多头哈希寻址器   │────▶│ 记忆表 MemoryTable        │◀──── 贷后标签回流
│ K 个哈希头 → 槽ID │     │  slot: 原型向量 + 统计量   │      (写门:准入/衰减/冲突)
└────────┬────────┘     └──────────────────────────┘
         ▼
┌─────────────────┐
│ 读门 ReadGate    │  gate = f(槽置信度, 样本数, 新鲜度)
└────────┬────────┘
         ▼
┌─────────────────┐
│ 推理 backbone    │  小型 MLP: [特征嵌入 ⊕ 加权记忆] → 隐藏层
└────────┬────────┘
         ▼
┌─────────────────┐
│ 决策头 + 审计     │  通过/拒绝/额度 + 命中槽明细(gate 值、槽统计)
└─────────────────┘
```

## 1. 设计决策点

### D1. 寻址:多头哈希,而非学习路由

- 沿用 Engram 的确定性寻址:K 个哈希头(如 K=4),每个头覆盖一组特征子空间的交叉
  (如 头1=年龄段×收入段,头2=职业×地区,头3=多头借贷×查询次数,头4=全量低维投影)。
- 决策时输入确定 → 槽 ID 确定 → 可 prefetch、可缓存、可复现(审计重放能得到同一批槽)。
- **刻意不用**学习式路由(MoE 式 gating network):小额信贷数据量撑不起路由训练,
  且路由不可复现会破坏审计。
- 哈希空间尺寸按头独立配置(如每头 2^16 槽),碰撞处理见 D4。

### D2. 记忆槽 schema:原型 + 统计,而非纯 embedding

Engram 的槽只有 embedding(参数)。信贷槽需要双重内容:

```
MemorySlot {
  proto:   Vector[d]      # 该模式的原型嵌入(参与推理,可微/可更新)
  stats: {
    n:            int     # 累计样本数
    bad_rate:     float   # 违约率 EWMA
    avg_amount:   float   # 平均授信额度(供额度决策参考)
    updated_at:   timestamp
    confidence:   float   # 由 n、方差、新鲜度推出
  }
  audit: {
    pattern_desc: string  # 可解释:该槽主导特征交叉的人类可读描述(如 "25-35岁 ∧ 收入3-8k ∧ 三线城市")
    top_features: list    # 反哈希不可行,用写入样本的主导特征近似
  }
}
```

`stats` 直接服务可解释输出与读门置信度;`proto` 服务推理网络。审计描述在写入时
用样本特征众数近似维护,不做精确反哈希。

### D3. 读门:置信度感知的 gate

- `gate_k = σ(w · [slot.confidence, slot.n_norm, freshness, query_proto_sim])`,
  每头独立 gate,低置信槽自动衰减为「未命中」。
- 全头低置信 → 标记 `memory_miss`,决策权重回落到 backbone 的先验头,
  并支持业务层兜底(转规则引擎/人工)。**未命中必须显式可观测**,不允许静默猜测。
- 读出门控的注入方式:query 向量(特征嵌入)作为 attention 的 Q,命中槽 proto 作为 K/V,
  加权求和后与特征嵌入拼接进 backbone(对齐 Engram 的 context-aware gating 思路)。

### D4. 写门:信贷场景的核心创新

Engram 推理期只读;本引擎推理期可写。写路径分两种:

**即时写(决策时)** — 只更新「该槽被查询过」的轻量痕迹(计数/时间戳),不写知识。
**标签写(贷后回流)** — 真正的经验写入:

1. **准入**:标签须达置信门槛(如账龄 ≥ 3 期才算有效表现样本);拒绝样本无贷后标签,
   单独走「拒绝推断」缓冲区,不直接污染记忆(拒绝推断是信贷老问题,Phase 3 再解)。
2. **更新**:`bad_rate` 用 EWMA(α 可调,概念漂移敏感);`proto` 用动量更新
   (向新样本嵌入方向小步移动),不做全量反传 —— 在线写入必须是 O(1)、无锁友好的。
3. **碰撞**:同槽不同模式(哈希碰撞)靠 `proto` 相似度检测:新样本嵌入与槽 proto
  余弦距离 > 阈值 → 进「溢出区」并计数,溢出饱和触发**槽分裂**(对该哈希头做一次
  细分 rehash,离线任务,不在线做)。
4. **衰减**:槽长期未命中且统计陈旧 → confidence 衰减;整表定期快照,支持回滚到
  任意历史版本(模型/记忆版本化是金融合规底线)。

### D5. 推理 backbone:刻意小

- 输入 = 特征嵌入(类别 embedding + 数值归一化) ⊕ 读门加权记忆向量。
- 结构 = 2-3 层 MLP(隐层 ~256)。小额信贷的复杂度在数据与经验,不在网络深度 ——
  与 Engram「记忆加深网络、解放 backbone」的结论一致。
- 输出头:approve(二分类)、amount(回归,或分箱分类)、risk_score(校准概率)。
  概率必须经校准(Platt/isotonic),风控不可用未校准分数。

### D6. 训练:离线建表 → 在线微调

- **离线**:历史/生成数据批量过一遍 → 哈希建槽、初始化 proto(stats 直接用历史标签统计),
  backbone 用「检索增强」目标训练(冻结记忆、只训 backbone + gate)。
- **在线**:backbone 原则上冻结或极低频更新(版本化发布),记忆表高频更新(写门)。
  即:**快变量是记忆,慢变量是网络** —— 这是本引擎与「模型重训流水线」的根本区别,
  也是对监管最友好的形态(每次变化可追溯到一个写门事件)。

### D7. 审计与可解释

- 每次决策输出 `MemoryTrace`:命中槽 ID 列表、各头 gate 值、槽的 `pattern_desc` 与
  `bad_rate/n`、memory_miss 标记、最终各输出头贡献分解。
- 持久化形态对齐现有 `rfa_decision_flow_log` 传统(Phase 2 落 ClickHouse/MySQL 均可)。
- 决策理由模板:「该申请命中历史模式『25-35岁 ∧ 收入3-8k』(样本 230,违约率 18%,
  置信度 0.72),记忆贡献占决策权重 61%」。

## 2. 与 RuleForge 的集成路径

调研结论(2026-07,详见附录)给出的可插拔点,按阶段采用:

| 阶段 | 集成形态 | 侵入度 | 说明 |
|---|---|---|---|
| Phase 1 | 无集成,纯离线服务 | 0 | FastAPI + PyTorch,照 `model-service` 先例;数据来自 data-generator / 批测存量 |
| Phase 2 | `DataSourceConnector` 实现(照 `PklModelConnector`) | 低 | 记忆引擎输出(相似客群违约率、置信度)作为变量进 fact,与规则并行影子比对 |
| Phase 3 | V1 新节点 `MemoryNode`(照 `ScoreCardExecutor` 模式:不进 RETE、直接求值写回 fact) | 中 | 需要 `NodeBase` 子类 + `NodeType` 枚举 + `V1FlowRunner.executeNode()` 分发;发布冻结走 `V1PublishedBundle` |

数据前提(必须在 Phase 1-2 之间补):

- 决策日志 writer 已随 ruleforge-decision 删除 → 需在 `V1FlowRunner` 出口恢复
  fact 快照 + 命中明细落库(`rfa_decision_flow_log` schema 现成,加 feature snapshot 列);
- 贷后标签回流通道(还款表现 → 写门)是全新链路,Phase 2 设计、Phase 3 启用。

## 3. 技术选型

- 语言/框架:Python ≥ 3.11 + FastAPI + PyTorch —— 神经网络实验的事实标准,
  且仓内已有 `model-service`(FastAPI + uv)先例可照抄工程结构。
- 记忆表存储:Phase 1 进程内 numpy/torch 张量 + 快照文件;Phase 3 视规模外置
  (Redis/ClickHouse,槽 schema 简单,无向量库需求 —— 寻址是哈希不是 ANN)。
- 服务端口:建议 8601(model-service 8501 顺延),先不占用根 compose。

## 4. 明确不做(防范围蔓延)

- 不做 MoE / 学习式路由(D1);
- 不做深网络 / Transformer backbone(D5);
- 不做在线反向传播 —— 在线只更新记忆统计与 proto(D6);
- 不替代现有规则引擎 —— 长期共存:规则管合规硬约束(监管红线、准入),
  记忆引擎管经验软判断(风险定价、额度),「规则是护栏,记忆是经验」;
- 不解拒绝推断(reject inference)—— Phase 3 单独立项。

## 5. 开放问题(Phase 0 待拍板)

1. 特征交叉的业务模式片段清单:由风控专家给定,还是自动二阶交叉 + 重要性筛选?
2. 写门 EWMA 的 α 与账龄准入门槛:需要历史违约数据标定,还是先用行业先验?
3. 槽分裂/合并的触发运维:离线任务的调度形态(cron? 手动?)与灰度策略。
4. 记忆版本化与模型版本化的对应关系:一次 backbone 发布是否强制记忆快照?

## 附录:本仓引擎调研要点(2026-07)

- V1 决策流是唯一决策路径:`v1/exec/V1FlowRunner.java` 自建图遍历,
  ServiceTask 分发节点执行器,ExclusiveGateway 走 CEL(`v1/cel/CelEngine.java`)。
- fact 是跨节点的工作记忆(Map,原地修改)—— V1 层加 MemoryNode 时,
  记忆读写可建模为 fact 的命名空间分区 + 外部持久化钩子。
- ML 接入现状:`PklModelConnector`(数据源连接器形态,HTTP 调 model-service `/predict`);
  PMML 进程内解析(`ir/pmml/PmmlResourceDispatcher`,子结构未填充,V5.41.3 遗留)。
- 特征注入缺口:V1 生产路径 fact 完全来自请求体,不做数据源注水;
  数据源取数只服务批测输入构造。
- 实验惯例:`experiments/README.md` —— 不进 pom/CI/生产,production 命名,
  升格 = `git mv` + compose 必选 + workflow。
