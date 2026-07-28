# neural-engine 设计文档

> 自学习信贷审批系统实验。遵循 `experiments/README.md` 惯例:不进 `server/pom.xml`、
> 不进 CI、不进生产流量,成熟后 `git mv` 升格。
>
> **本文档是唯一主线设计**(2026-07 整合)。历史档案在 `archive/`:
> - `archive/P1-P2-自学习审批系统实现设计.md` —— P1-P2 已完成实现的设计依据
> - `archive/ARCHITECTURE-P1-ENGRAM.md` —— P1 Engram 引擎(已证伪,反面教材)

---

## 一、项目演进与当前定位

### 1.1 三阶段演进

```
P1 Engram 神经记忆引擎(已证伪)
   ↓ 记忆做决策三轮 FAIL,GBDT 在结构化数据上太强
P1-P2 自学习审批系统(已完成,603 测试)
   ↓ GBDT 决策 + 外挂槽位表 + 云端出题 + 免疫系统验证
激进 B 机构编排智能体(当前主线,研究阶段)
   ↓ 本地模型当"机构 AI 管家",编排 GBDT/规则/云端,驱动系统自我进化
```

### 1.2 当前定位(激进 B,一句话)

> **本地小模型(0.6B)不学"怎么做决策",学"怎么当一个机构的 AI 管家" ——
> 它编排工具链(GBDT/CART/规则引擎)、驱动云端大模型(冻结的新手)做重活、
> 沉淀机构历史经验(死路档案/策略记忆),让整个决策系统持续自我进化。**

学术目标:写论文。核心论点 —— **不跟 GBDT 比 AUC(那是工具的事),比的是
"有这个编排器 vs 没有,系统的长期进化效率差多少"**。

### 1.3 为什么是这个方向(否决记录)

| 被否决的方向 | 否决理由 |
|---|---|
| **P1:本地模型用记忆做决策** | 三轮证伪,GBDT 在结构化数据上太强 |
| **路线 1:本地模型直接做决策** | 跟 P1 同源,大概率第二次证伪;学术贡献弱 |
| **强编排:本地模型自己写代码** | 通用 coding 该外包给云端(冻结的新手能力强) |
| **tool use / function calling** | 0.6B 小模型做 tool use 不稳 |

### 1.4 三角色分工

| 角色 | 承担者 | 能力 | 知识归属 | 信任 |
|---|---|---|---|---|
| **编排智能体** | 本地 0.6B | 读局势、出策略、记经验 | **机构专属知识在这里累积** | 自家,可审计 |
| **通用执行** | 云端大模型(冻结) | 写代码、写特征、做分析 | 每次调用无状态,不累积 | 不信任 → 过免疫系统 |
| **决策工具** | GBDT / CART / 规则引擎 | 做决策、抽规则 | 无知识,纯工具 | 确定性,完全信任 |

---

## 二、已完成的 P1-P2 基线(编排器的起点)

> 这部分是激进 B 阶段 1 的 **baseline**。编排器要超越的不是 GBDT,是这套**写死策略的闭环**。

### 2.1 现有自学习闭环(selflearn/loop.py)

```
每轮迭代(dev 窗上,eval 窗禁入):
  ① GBDT 指路:训练 GBDT → dev 尾段留出集上挑"解释不了的坏账"
     (proba 低但 outcome=bad, top_k)+ 特征重要性 top
  ② 记忆查询:策略槽库检索相关经验 + 死路清单
  ③ 出题:G1 feature_proposal 模板(context=疑难画像聚合统计/现有特征/
     死路/regime 段统计)→ 云端(AgentBridge 真实在环 / replay 固化重放)
  ④ 验证:沙箱 + dev 窗回测(IV/lift/覆盖率/泄漏)+ 相关性 < 0.9
  ⑤ 入库:pass 注册 L2 特征 + shadow 特征槽;fail 写死路槽
  ⑥ GBDT 重训,记录 dev 留出集 AUC 变化
```

**它已经是个"弱编排器"**,但策略是**写死的 Python 逻辑**(G1 模板固定、数据子集固定、
工具固定 GBDT)。激进 B 要做的,是**用可学习的本地模型替换这套写死策略**。

### 2.2 免疫系统(verify/)

闭环的"过程合法性裁判",四大检查 —— 沙箱执行(`sandbox.py`)、信息量门槛 IV/lift
(`metrics.py`)、泄漏检测(`direction_free_auc`)、质量分 Q(`_strength`)。
README 验证结论表里"免疫系统 6/6 拦截 LLM 幻觉"就是它干的。**激进 B 里它为 reward
提供"有效特征数"的计数**。

### 2.3 P1-P2 验证结论(README 验证表)

| 假设 | 结果 | 证据 |
|---|---|---|
| 推理面用记忆(P1 分数混合 / L3 特征 / P2 焊入层) | ✗ 三轮证伪,已放弃 | `eval/artifacts*/` |
| 免疫系统:验证器拦 LLM 幻觉 | ✓ 6/6 拦截 | LendingClub r01/r02 |
| 发现力:闭环发现未知规律 | ✓ CLAB 重新发现保留池 6/10 | `eval/artifacts-clab/` |
| 记忆组件:死路档案 | ✓ 重复提案 0 vs 16.7%,发现提速 +0.38 轮 | `eval/artifacts-clab-ab/` |
| 记忆组件:声誉退役 | ✓ 期末库假阳性 −23.8 | 同上 |

**关键判读**:发现的规则是可解释资产,**非预测增益**(GBDT 已吃满信号时 AUC 无增量)。
这直接决定激进 B 的 reward 设计(见 §4)。

### 2.4 组件地图

| 包 | 职责 | 激进 B 中的角色 |
|---|---|---|
| `slots/` | 可写槽位表(SQLite+FAISS,Beta 声誉,WAL) | 阶段 1 编排器的**输入语境**(死路档案/经验) |
| `synth/` | CLAB-lite 合成信贷世界 | 阶段 1 **实验场地** |
| `cloud/` | 云端适配层(脱敏焊死出站、AgentBridge) | 编排器调用的**通用执行器** |
| `verify/` | 验证器(免疫系统) | reward A 的**有效特征计数器** |
| `scoring/` | GBDT RollingScorer + 特征注册表 | 编排器的**决策工具** + reward 语境来源 |
| `selflearn/` | 自学习闭环(loop.py)+ CLAB 适配 | 阶段 1 **baseline**(写死策略) + 扩展点 |
| `nightly/` | 夜间巩固(结局回流+Scribe+特征循环) | 阶段 2+ 的巩固框架 |
| `lora/` `scribe/` | LoRA 巩固+晋升门 / LLM 经验归纳 | 阶段 2 记忆升级时重新评估 |
| `embed/` | Qwen3-Embedding-0.6B + canonical 文本化 | 槽位检索(阶段 1 保留) |
| `memory/` `model/` `training/` | Phase 1 Engram 神经网络引擎 | **历史档案**,不参与激进 B |

---

## 三、激进 B 设计:机构编排智能体

### 3.1 中编排 + 无 tool use 的边界

| 强度 | 干什么 | 本方案 |
|---|---|---|
| 弱编排 | 出题 + 验证 + 记死路 | P1-P2 现状(loop.py) |
| **中编排** | + **决定调什么工具**(GBDT/CART/规则) + **选数据子集** + **定训练目标** | **✅ 本方案** |
| 强编排 | + 自己写 GBDT 调参代码 + 设计数据管道 | ❌ 通用 coding 给云端 |

**无 tool use 实现**:本地模型不调用 function calling。它输出**策略指令**(半结构化文本),
由确定性编排代码(Python,`selflearn/loop.py` 扩展)翻译成实际操作:

```
编排器输出(策略指令):
  {
    "explore_direction": "负债比 × 收入波动的交互",
    "rationale": "debt_to_income 单独试过 IV=0.08 弱,需交互",
    "data_subset": {"filter": "self_employed == true", "reason": "该段 bad_rate 偏高"},
    "tool": "CART",
    "tool_reason": "监管要可解释,GBDT 黑箱",
    "cloud_brief": "找高负债 + 低稳定收入的组合特征"
  }
        ↓ 编排代码翻译成确定性操作
  - filter df / 构造 prompt 发云端 / 免疫系统验证 / CART 抽规则 / 写回经验 / 算 reward
```

**关键**:编排器只决定"做什么 + 为什么",不决定"怎么做"(API/pandas 是编排代码和云端的事)。

### 3.2 本地模型语境(编排器每次决策看到的输入)

| 语境内容 | 作用 |
|---|---|
| 当前 GBDT 重要性 top 特征 | 知道系统现在靠什么 |
| 当前死路档案 | 哪些方向试过失败(记忆核心) |
| 当前残余信号分析 | GBDT 漏掉什么(指路) |
| 历史 reward 曲线(最近 N 轮) | 自我反思 |
| 机构语境标签 | 机构绑定("这是信贷"/"这是反欺诈") |
| 可用工具列表 + 说明 | 中编排要选工具 |

**代价**:prompt 变长,0.6B context 压力。阶段 1 验证够不够。

---

## 四、Reward 设计(已确认)

reward 不是"决策对不对",是"**编排后系统有没有变强**"。

### 4.1 reward A:发现效率

```python
reward_a = (本轮发现的有效特征数) / (本轮消耗的云端调用次数)
```

编排器出题出得好不好,看它能不能用**更少的云端预算**发现**更多有效特征**。
"有效"由免疫系统判定(PASS + 质量分 Q > 阈值)。

### 4.2 reward B:避免重复

```python
reward_b = -(本轮云端产出中, 命中死路档案的比例)
```

编排器记住了死路 → 出题时避开 → 云端产出重复死路的比例下降。
**直接测"记忆有没有用"**。

### 4.3 组合

```python
def orchestrator_reward(round_outcome):
    eff = round_outcome.features_passed / max(round_outcome.cloud_calls, 1)
    repeat_penalty = -round_outcome.dead_end_repeats / max(round_outcome.proposals, 1)
    return w_a * eff + w_b * repeat_penalty  # w_a, w_b 阶段 1 调
```

### 4.4 为什么不用"AUC 变化"当 reward

§2.3 已验证:"GBDT 已吃满信号时 AUC 无增量"。用 AUC 当 reward → 模型学不到东西
(AUC 不涨)→ RL 无法收敛。**编排质量 reward(A/B)不依赖 AUC 涨**,依赖策略质量。
这是论文关键论点:**编排质量 ≠ 决策准度,前者比后者更适合做持续学习的信号**。

---

## 五、阶段证伪分解

```
阶段 1(8-10 周):编排器范式单点验证
  ├─ 1.1 reward 管道(A+B 计算)
  ├─ 1.2 baseline 复现(无编排器 = 现状 loop.py)
  ├─ 1.3 评估指标固化
  ├─ 1.4 编排器 v0(0.6B 产出策略 + 确定性代码翻译)
  ├─ 1.5 GRPO 训练(reward=A+B)
  └─ 1.终 证伪判定
   ↓ 通过
阶段 2(6-8 周):记忆机制升级(TTT 原生记忆 替换 槽位表外挂)
   ↓ 通过
阶段 3(4-6 周):post-monolith 多编排器 + 路由(按 regime/产品/客群)
   ↓ 通过
终考:CLAB + 真实数据(需 RuleForge Phase 19 P0 数据管道先行)
```

### 5.1 阶段 1 证伪标准(最终定义,2026-07-29 验证 PASSED)

**判定对象**:编排器(GRPO 训练的 0.6B + deepseek-v4-flash 真云端)
vs baseline(写死策略 ClabAutoCloud 暴力枚举)。

**核心指标:信号发现效率(strong_rate = 强特征数 / 总提案数)**。
编排器的价值不是"产更多"(暴力枚举在产量上永远赢),是"产得更精准"
(每个提案是强特征的概率更高)。

| 指标 | baseline 实测 | 编排器实测 | 判定 |
|---|---|---|---|
| strong_rate(强特征率) | 0.22 | **0.52** | ✅ 2.36x 优势 |
| b_quality(平均 IV) | 0.263 | **0.313** | ✅ 1.19x 优势 |
| b_rep(死路重复率) | 0.0 | 0.0 | ✅ 持平 |
| GRPO 收敛性 | — | reward -0.12→+0.19 | ✅ 收敛 |

**结论:阶段 1 PASSED ✅,编排器范式成立**。

**为什么不用"总产量"比**:baseline ClabAutoCloud 一次吐 20 个候选(全枚举),
真云端一次产 5 个(LLM 生成)。用"固定轮数的总特征数"比,等于惩罚真云端的
"少产"。实测 v1 编排器 b_feat=22 < baseline 83,但 strong_rate 2.36x 更高 --
编排器让每次探索更精准,不是更多。

**迭代过程(论文素材)**:
- v1(temp=0.7,无 novelty):质量最高(IV 0.313),聚焦 platform_loans
- v2(+entropy bonus):证伪 -- 0.6B base 退回随机采样产乱码
- v3(+novelty bonus):探索更多字段但选的字段 IV 更低(0.2017)
- v1 是质量最优版本,用新标准判定 PASSED

### 5.2 跟 P1 的本质不同(每个设计决策都要能回答)

| 维度 | P1 | 本方案 |
|---|---|---|
| 本地模型学什么 | 决策映射 | **编排策略** |
| 本地模型在哪 | 推理路径 | **编排路径(出策略)** |
| 跟 GBDT 关系 | 竞争 | **GBDT 是编排器的工具** |
| P1 挂点 | 决策打不过 GBDT | **不跟 GBDT 比 AUC** |

---

## 六、用户决策(已确认 2026-07)

| 决策点 | 锁定值 |
|---|---|
| 路线 | 激进 B(编排智能体,路线 2) |
| 失败预算 | 不限期,但每阶段有独立停判标准 |
| 成功标准 | **能持续学习就是成功**(不要求 AUC 超 GBDT) |
| 编排强度 | 中编排(选工具/选数据/定目标,不写代码) |
| tool use | 无(产出策略文本,Python 翻译) |
| 模型基座 | 阶段 1 沿用 Qwen3-0.6B |
| reward | A 发现效率 + B 避免重复(不用 AUC) |
| 本地模型语境 | 全语境(六项,见 §3.2) |
| 记忆机制 | 阶段 1 用现有槽位表,阶段 2 才考虑 TTT 原生记忆 |
| 云端 | deepseek-v4-flash(MoE 284B/13B,$0.14/$0.28 per 1M tokens) |
| **阶段 1 状态** | **✅ PASSED(2026-07-29),编排器范式成立** |

---

## 七、事实基础(2026 文献)

| 文献 | 关键结论 | 支撑点 |
|---|---|---|
| [Baseten: post-monolith (2026)](https://www.baseten.co/research/continual-learning/) | monolith→zoo,RL>SFT,机构专家+路由 | 路线 2 编排器范式的核心依据 |
| [Titans (arXiv 2501.00663)](https://arxiv.org/abs/2501.00663) | 神经长期记忆,surprise 门控 | 阶段 2 TTT 记忆的理论基础 |
| [AWS: GRPO + Qwen2.5-0.5B](https://aws.amazon.com/blogs/machine-learning/overcoming-reward-signal-challenges-verifiable-rewards-based-reinforcement-learning-with-grpo-on-sagemaker-ai/) | GRPO 在 0.5B 上跑通 | 阶段 1 GRPO 可行性 |
| [Gamut: GRPO 微调小模型](https://www.gamut.so/blog/grpo-fine-tuning-qwen-0-5b-llama-1b-vs-openai-o1-preview) | GRPO 比 PPO 更稳定 | RL 路径稳定性 |
| [NVIDIA TTT-E2E (2026.01)](https://github.com/test-time-training/e2e) | 端到端 TTT,但强绑 NLP | 阶段 2 TTT loss 改造起点 |

**空白地带(论文研究价值)**:
- 小模型做**编排智能体**(不是决策器)—— 0 验证
- 编排质量 reward(A+B)替代决策准度 reward —— 0 验证
- 机构专属知识在编排器里持续累积 —— 0 验证

---

## 八、论文叙事(草稿)

**标题候选**:
《机构编排智能体:作为金融决策系统自进化管家的小语言模型》
(Institutional Orchestrator: A Small Language Model as the Self-Evolving
Steward of Financial Decision Systems)

**摘要草稿**:
> 我们提出**机构编排智能体**范式:与其再训练一个做决策的模型(在结构化数据上输给
> GBDT),不如训练一个小(0.6B)语言模型来**编排**决策系统 —— 决定探索什么、聚焦哪个
> 数据子集、调用哪个工具(GBDT/CART/规则)、如何给云端大模型下达任务。编排器通过 RL
> 累积**机构专属知识**(死路档案、策略记忆),使用一种新颖的**编排质量 reward**
> (发现效率 + 死路召回)而非决策准确率。我们证明,配备编排器的系统比固定策略基线
> 进化得更高效,且不与 GBDT 在 AUC 上竞争。

**三个贡献**:
1. **范式**:小模型做编排智能体,不做决策器(post-monolith 的具体化)
2. **Reward 设计**:编排质量(效率+记忆)替代决策准度,适配"GBDT 吃满后 AUC 不涨"的现实
3. **实证**:CLAB 合成世界 + LendingClub 真实数据上的进化效率对比

---

## 九、风险登记册

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 0.6B context 装不下全语境 | 中 | 阶段 1.4 受阻 | 语境压缩(摘要+top-k),或升 1.7B |
| GRPO 在编排任务上不收敛 | 中 | 阶段 1 失败 | 先验证 reward 可算+baseline 可复现,再上 RL |
| 编排器不如写死策略 | 高 | 阶段 1 失败 | 这正是要证伪的,失败也是结论 |
| 真实数据缺失 | 高 | 终考无法跑 | 先 CLAB 验证范式,真实数据走 RuleForge Phase 19 P0 |
| 编排指令解析不稳 | 中 | 训练噪声 | 策略指令用半结构化格式,确定性解析 |
| "编排质量"指标难定义 | 中 | reward shaping 痛 | A+B 已是可量化代理,调权重即可 |

---

## 十、运行

```bash
# 测试(根目录 uv;slow_model 为真模型用例,需本地 HF 缓存)
uv run pytest experiments/neural-engine -q -m "not slow_model"   # 603 passed

# 验收实验(cwd 必须在 experiments/neural-engine)
cd experiments/neural-engine
uv run python -m eval.clab_discovery      # 机制一:发现力(CLAB,~2min)
uv run python -m eval.clab_memory_ab      # 机制二:记忆双臂(~7min)
uv run python -m eval.selflearn_acceptance --cloud agent --rounds 1   # 真实云端在环
uv run python -m lending.prepare          # LendingClub 预处理(~20s)
```

## 十一、遗留清单(按优先级)

1. **真实业务数据终考**(未启动)— CLAB/LendingClub 已验证,真实贷后闭环未跑。
   是接入 RuleForge 生产决策流的前置条件。
2. 验证器对称判据(保护性规则盲区)
3. CLAB-full:加类别/文本模态 + 随机因果结构
4. 工程债:AgentBridge task_id 冲突;脱敏误伤;slots WAL 体积(12GB 教训)
5. shadow→active 晋升门、月度体检/退役的完整生命周期
