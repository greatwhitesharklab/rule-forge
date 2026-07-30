# Runtime Parameter Composition(RPC):设计文档

> **Version**: v2.0(2026-07-30)。加 User PM + 层级门控 + Φ 比较框架。
>
> **核心问题**:Transformer 参数能否在运行时动态组合,
> 而不是预训练后保持静态?
>
> **一句话定位**:提出一种 Runtime Parameter Composition(RPC)架构,
> 将 Transformer 参数划分为 Base、Domain、User、Session 四种生命周期,
> 并通过可学习的 Compose Function 在推理时动态组合。
> LoRA 只是第一版 Patch 的实现形式,未来可替换成其他 Patch 类型,
> 整个理论框架仍然成立。

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

### 定义 5:Lifecycle(更新 v2.0:四层生命周期)

$$L(M) = (\tau_{start}, \tau_{end}, \tau_{update})$$

每个 PM 的生命周期三元组:
- $\tau_{start}$:创建时间
- $\tau_{end}$:过期时间(永久 / 月 / 周 / 小时 / 一次前向)
- $\tau_{update}$:更新频率(从不 / 每周 / 每天 / 每会话 / 每步)

| PM 类型 | $\tau_{end}$ | $\tau_{update}$ | 知识类型 | 当前实现 |
|---|---|---|---|---|
| Base | 永久 | 从不 | 语言能力、通用推理 | Qwen3-0.6B 冻结 |
| Domain | 月 | 周度批量 | 行业领域知识(信贷风控通用规则) | GRPO 训练的 LoRA |
| User | 周 | 日度 | 研究员个性化偏好(Fred 偏 Device/SIM;另一人偏 Income/Cashflow) | SFT 或 GRPO 训练的 LoRA |
| Session | 小时 | 每轮闭环 | 本次会话经验(死路档案、探索状态) | TTT 更新的 LoRA |
| Token | 一次 | 每步 | 当前输入瞬时记忆 | (Paper 3) |

### 定义 6(v2.0 新增):Parameter 重新分类

传统 Transformer 只有 **Parameter**(一个概念)。RPC 重新定义:

$$\text{Parameter} = \text{Persistent Parameter} + \text{Runtime Parameter}$$

- **Persistent Parameter**:预训练 + 领域训练后的冻结参数(永久)
- **Runtime Parameter**:推理时动态组合的参数(有生命周期)

Runtime Parameter 进一步分层:

$$P(t) = P_{base} + \sum_i \lambda_i(t) \cdot \Delta P_i$$

其中 $\Delta P_i$ 的生命周期不同:
- $\Delta P_{domain}$:TTL = months
- $\Delta P_{user}$:TTL = weeks
- $\Delta P_{session}$:TTL = minutes

### 定义 7(v2.0 新增):门控不是标量

**v1.0 的门控**:$g = \sigma(\alpha)$,全局标量(2 个 gate)。

**v2.0 的门控**:层级门控(per-layer gate)。

$$g_i^{(l)} = \sigma(\alpha_i^{(l)}), \quad l = 1, \ldots, L$$

每个 PM 在每一层有独立的门控。理由:
- 领域知识可能主要作用在最后几层(高层语义)
- 基础语言能力在前面几层(低层语法)
- 不同层需要不同的 Domain/User/Session 组合比例

Paper 1 v2.0 实现层级门控(28 层)。未来可扩展到 head-level 和 token-level。

### 定义 8(v2.0 新增):Φ 不固定为加法

$\Phi$ 是论文最大的创新对象,不应该固定为加法。

论文比较多种 $\Phi$:

| $\Phi$ 实现 | 公式 | 复杂度 | Paper |
|---|---|---|---|
| Add(Level 0) | $W + \sum \Delta W_i$ | 最低(baseline) | Paper 1 对照 |
| Gate Add(Level 1) | $W + \sum g_i \cdot \Delta W_i$ | 低 | **Paper 1 主实验** |
| Attention(Level 2) | $W + \text{Attn}(Q, K_i, V_i)$ | 中 | Paper 2 |
| Tiny MLP(Level 3) | $W + \text{MLP}([\Delta W_1, \ldots])$ | 高 | Paper 2 |
| HyperCompose(Level 4) | $W' = \text{HyperNet}(\{\Delta W_i\})$ | 最高 | Paper 4 |

**Patch(PM)不一定是 LoRA**。未来 $\Delta P$ 可以是:
- LoRA 低秩矩阵(v1.0 实现)
- KV cache delta
- Router delta
- Hidden state delta

论文统一叫 Patch(PM),LoRA 只是第一版的实现形式。

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

---

## 九、Paper 1 实验结果(2026-07-29,PASSED ✅)

### 实验配置
- 数据:CLAB 合成(8 字段)
- 模型:Qwen3-0.6B
- 云端:deepseek-v4-flash
- 轮数:5 轮/组

### 结果

| 组 | 描述 | strong_rate | b_quality | b_strong |
|---|---|---|---|---|
| A | W_base only(baseline) | 0.2143 | 0.2525 | 6 |
| B | W_base + Domain PM(冻结) | 0.2500 | 0.2963 | 7 |
| C | W_base + Session PM(TTT) | 0.2333 | 0.2600 | 7 |
| **D** | **W_base + Domain + Session(RPC)** | **0.4091** | **0.2993** | **9** |

### 核心结论

**D 组(RPC 多层 PM 组合)全面胜出:**
- D vs A:strong_rate 1.91x
- D vs B:strong_rate 1.64x
- D vs C:strong_rate 1.75x

**多层 PM 组合 > 任何单层 PM,Paper 1 假设成立。**

### 论文一句话

> We show that composing Parameter Modules with different lifecycles
> (Domain + Session) at runtime yields 1.64-1.91x higher signal
> discovery rate than any single-module configuration, establishing
> Runtime Parameter Composition as a new paradigm beyond static LoRA.

---

## 十、终极愿景:Router 即 Patch(v2.0 新增)

### 10.1 当前范式 vs RPC 范式

**当前范式**(Prompt Engineering):
```
问题 → "你是一个风控专家..." → 模型 → 答案
```
专业能力靠 prompt 描述。模型不变,变的是 prompt。

**RPC 范式**(Weight Engineering):
```
问题 → Patch Router → 加载: Fraud_PM + Mexico_PM + Device_PM
     → Compose → Forward → 答案
```
专业能力靠**加载对应的 Runtime Weight**。不需要 prompt 描述角色,
模型自己变成专家。

### 10.2 Router 也是 Patch

Router 本身不是固定架构,而是一个 Patch:
- v1.0:Router 是固定门控(σ(α))
- v2.0:Router 是层级门控(28 个 α per PM)
- Paper 2:Router 是可学习的路由网络(model learns to select PMs)
- Paper 4:Router 自主生成新 Patch

### 10.3 为什么这比 Prompt 更强

Prompt 描述"你应该做什么":
> "你是一个信贷风控专家,关注设备指纹和多头借贷..."

但模型实际并没有风控知识 —— 它在**模拟**风控专家的语言。

RPC 加载"你实际是什么":
> 加载 Domain_PM(信贷知识) + User_PM(Fred 的偏好) + Session_PM(本次探索)

模型不是在**模拟**专家,而是**实际具备**专家的参数化知识。

### 10.4 论文的核心价值(一句话)

> 模型的"专业能力"不应该靠 prompt 描述,而应该靠运行时加载对应的参数模块。
> 这就是 Runtime Parameter Composition。
