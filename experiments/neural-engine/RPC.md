# Runtime Parameter Composition(RPC):设计文档

> **Version**: v1.0(2026-07-29)。Paper 1 地基文档。
>
> **核心问题**:Transformer 参数能否在运行时动态组合,
> 而不是预训练后保持静态?
>
> **一句话定位**:Runtime Parameter Composition(RPC)是一种新的计算范式,
> Transformer 参数在推理时从不同生命周期的模块化参数组件动态组合。
> LoRA/Adapter/TTT 都是这个统一框架的特例。

---

## 一、动机:为什么需要 RPC

### 1.1 当前 Transformer 的根本假设

所有 Transformer 都假设:**参数在预训练后静态**。

- 基座模型训完 → 冻结 → 推理时不变
- 微调(LoRA/Adapter)→ 训完 → 冻结 → 推理时不变
- 即使 TTT(Test-Time Training)→ 推理时更新 → 但没有生命周期概念

**问题**:不同知识有不同的"保鲜期":
- 基座语言能力 → 永久
- 行业领域知识(信贷规则)→ 月级更新
- 机构专属知识(死路档案)→ 周级更新
- 单次会话上下文 → 小时级
- 当前 token 的瞬时记忆 → 一次前向传播

当前方案把所有参数混在一起,用同一个时间尺度更新。**这要么导致
知识过时(更新太慢),要么导致灾难遗忘(更新太快)。**

### 1.2 RPC 的回答

**不同生命周期的知识,应该存在不同生命周期的参数模块里,推理时动态组合。**

```
Base Module(永久)     → 语言能力、通用推理
  +
Domain Module(月级)   → 信贷风控领域知识
  +
User Module(周级)     → 机构专属策略
  +
Session Module(小时)  → 本次会话的经验
  +
Token Module(一次)    → 当前输入的瞬时记忆
  =
W_runtime(推理时实际用的参数)
```

---

## 二、形式化定义

### 定义 1:Base Model

$$f(x; W_{base})$$

基座模型,参数 $W_{base}$ 永久冻结。提供语言能力和通用推理。

### 定义 2:Parameter Module(PM)

$$M_i = (\Delta W_i, \tau_i)$$

每个 Parameter Module 包含:
- $\Delta W_i$:参数增量(LoRA 低秩矩阵 / 全量 / attention bias / ...)
- $\tau_i = (\tau_{start}, \tau_{end})$:生命周期(创建时间,过期时间)

PM 是 RPC 的最小组合单元。LoRA 是一种 PM,Adapter 是一种 PM,
Memory Bank 也是一种 PM。

### 定义 3:Parameter Space

$$\mathcal{P} = \{M_1, M_2, \ldots, M_n\}$$

运行时参数空间:所有活跃 PM 的集合(未过期的)。

### 定义 4:Composition

$$W_{runtime} = \Phi(W_{base}, \mathcal{P})$$

$\Phi$ 是**组合函数** —— 这是 RPC 最大的创新对象。

$\Phi$ 的实现从简单到复杂:
- **Level 0(加法)**:$\Phi = W_{base} + \sum \Delta W_i$(多 LoRA 叠加)
- **Level 1(门控)**:$\Phi = W_{base} + \sum g_i \cdot \Delta W_i$
  ($g_i$ 是门控,决定每个 PM 的激活强度)
- **Level 2(投影)**:PM 之间有交互(非独立叠加)
- **Level 3(层次化)**:Base → Domain → User → Session 级联依赖

**LoRA 是 Level 0 的特例**(单个 PM,加法组合)。
**本文(Paper 1)验证 Level 0 + Level 1,后续论文探索 Level 2/3。**

### 定义 5:Lifecycle

$$L(M) = (\tau_{start}, \tau_{end}, \tau_{update})$$

每个 PM 的生命周期三元组:
- $\tau_{start}$:创建时间
- $\tau_{end}$:过期时间(永久 / 天 / 小时 / 一次前向)
- $\tau_{update}$:更新频率(从不 / 每天 / 每会话 / 每步)

| PM 类型 | $\tau_{end}$ | $\tau_{update}$ | 当前实现 |
|---|---|---|---|
| Base | 永久 | 从不 | Qwen3-0.6B 冻结 |
| Domain | 月 | 周度批量 | GRPO 训练的 LoRA |
| Session | 小时 | 每轮闭环 | TTT 更新的 LoRA |
| Token | 一次 | 每步 | (Paper 3) |

---

## 三、跟现有方法的关系(统一框架)

| 方法 | PM 类型 | $\Phi$ | $L(M)$ | RPC 解读 |
|---|---|---|---|---|
| **全量微调** | 1 个全量 PM | 加法 | 永久 | RPC 退化(无生命周期) |
| **LoRA** | 1 个低秩 PM | 加法 | 永久 | RPC 退化(无生命周期) |
| **Adapter** | 1 个瓶颈 PM | 串联 | 永久 | RPC 退化(无生命周期) |
| **TTT** | 1 个动态 PM | 加法 | Session | RPC 的 Session 层 |
| **MoE** | N 个专家 PM | 路由(softmax) | 永久 | RPC 的 Level 1(但无生命周期) |
| **Memory Bank** | 1 个检索 PM | attention 注入 | Session | RPC 的 Session 层(失败) |
| **本文 RPC** | **多层 PM** | **门控组合** | **分层生命周期** | **统一框架** |

**关键区别**:
- LoRA/Adapter:**单个 PM,无生命周期** → RPC 的退化特例
- MoE:**多个 PM,但同生命周期 + 路由** → RPC 的 Level 1 但无生命周期
- TTT:**单个 PM,Session 生命周期** → RPC 的 Session 层
- **本文 RPC:多个 PM,不同生命周期,门控组合** → 新范式

---

## 四、Paper 1 实验设计

### 4.1 核心假设

> **多层生命周期 PM 组合 > 单一 PM。**

具体:Domain PM(月级,GRPO 训练)+ Session PM(小时级,TTT 更新)
的组合,比单独 Domain PM 或单独 Session PM 效果更好。

### 4.2 实验组

| 组 | 配置 | PM 组成 | $\Phi$ |
|---|---|---|---|
| A(baseline) | 无 PM | W_base only | — |
| B(Domain only) | 单层 PM | W_base + Domain PM | 加法 |
| C(Session only) | 单层 PM | W_base + Session PM(TTT) | 加法 |
| **D(RPC)** | **双层 PM** | **W_base + Domain PM + Session PM** | **门控** |

### 4.3 评估指标

**编排质量**(沿用阶段 1 的 strong_rate):
- strong_rate = 强特征数 / 总提案数
- b_quality = 平均 IV
- avg_iv 趋势(后半程 > 前半程?)

### 4.4 数据

- CLAB(合成):8 字段,信号丰富(已验证编排器 2.36x 优势)
- Nova(真实):当前 13 字段(信号集中),等扩充三方数据后重跑

### 4.5 预期结果

| 组 | 预期 strong_rate | 理由 |
|---|---|---|
| A | ~0.22(baseline) | 暴力枚举 |
| B | ~0.52(Domain PM) | GRPO 训练的编排器(阶段 1 结果) |
| C | ~0.52(Session PM) | TTT 持续学习(阶段 2 方向 A 结果) |
| **D** | **> 0.52** | **Domain + Session 协同 > 单层** |

**如果 D > B 且 D > C,Paper 1 的核心贡献成立。**

### 4.6 $\Phi$ 的门控实现(Level 1)

```python
# 当前(Level 0 加法):
W_runtime = W_base + Delta_Domain + Delta_Session

# Paper 1(Level 1 门控):
W_runtime = W_base + g_domain * Delta_Domain + g_session * Delta_Session
```

$g_{domain}$ 和 $g_{session}$ 是可学习的门控(sigmoid)。
- $g_{domain}$ 大 → 领域知识主导(稳定场景)
- $g_{session}$ 大 → 会话经验主导(新场景探索)

---

## 五、代码资产映射

| RPC 概念 | 当前代码 | 状态 | Paper 1 要做的 |
|---|---|---|---|
| Base Model | Qwen3-0.6B 冻结 | ✅ | 无改动 |
| Domain PM | `eval/grpo_train.py`(GRPO LoRA) | ✅ | 加载 + 冻结 |
| Session PM | `selflearn/ttt.py`(TTT LoRA) | ✅ | 加载 + 在线更新 |
| $\Phi$(组合) | 无(当前各自独立) | ❌ | **新建:组合两个 PM** |
| 门控 $g$ | `selflearn/memory_bank.py`(gate) | ✅ 可复用 | 适配到 PM 组合 |
| 评估 | `selflearn/metrics.py`(strong_rate) | ✅ | 无改动 |

**核心新代码**:PM 组合器(`ParameterComposer`)—— 加载 Domain PM + Session PM,
用门控组合,推理时产出 W_runtime。

---

## 六、论文路线(四篇规划)

| 论文 | 主题 | 核心 $\Phi$ | 核心创新 |
|---|---|---|---|
| **Paper 1** | **RPC 定义 + 生命周期** | Level 1(门控) | 多层 PM 组合 > 单层 |
| Paper 2 | 自适应参数路由 | Level 2(投影) | 模型自己选 PM |
| Paper 3 | 运行时学习 | 临时 PM 生成 | Session 内生成+销毁 PM |
| Paper 4 | 自进化参数空间 | 自动 PM 创建 | 模型自己产生新 PM |

---

## 七、Paper 1 标题候选

1. **Runtime Parameter Composition: Towards Lifecycle-aware Transformer Architectures**
   (运行时参数组合:一种具有生命周期感知的 Transformer 架构)
2. **Beyond Static Weights: Runtime Parameter Composition for Personalized Transformers**
   (超越静态参数:面向个性化 Transformer 的运行时参数组合)

推荐 **1** —— "Lifecycle-aware" 是 RPC 跟 LoRA/MoE 的核心区别点,
放标题里让审稿人第一眼看到。

---

## 八、风险与对策

| 风险 | 对策 |
|---|---|
| D 不比 B/C 好(多层无优势) | 调门控初始化 + Session PM 更新频率 |
| 审稿人认为 RPC = MoE 换皮 | 强调生命周期(L(M))是 MoE 没有的维度 |
| 审稿人认为 RPC 只是分类法 | Paper 1 实验证明多层 > 单层(不是分类是方法) |
| 0.6B 太小看不出多层优势 | CLAB 上先验证,后续升 1.7B + 真实数据 |
| $\Phi$ 门控学不动 | 跟 MemoryBank 的 gate 一样可能冷启动零梯度;用小随机初始化 |
