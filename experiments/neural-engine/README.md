# neural-engine — 自学习信贷审批系统(实验)

> 状态:P2 完成,机制一/机制二验证完成(2026-07)。本目录遵循 `experiments/README.md` 惯例:
> 不进 `server/pom.xml`、不进 CI、不进生产流量;成熟后 `git mv` 升格。
>
> **文档变更说明**:本目录早期是「Engram 式神经网络决策引擎」实验(memory/model/training,
> 见 `ARCHITECTURE.md`,已标记为历史档案)。经多轮实验证伪与定位修正,当前形态为
> **自学习审批系统**,总设计文档:`自学习审批系统实现设计.md`(v1.1 + 实验回写)。

## 一句话定位

推理用 GBDT,记忆用于训练阶段:**GBDT 管决策,记忆管迭代,云端管构造,验证器管把关**。
本地出题 → 云端大模型写特征代码 → 本地验证 → 晋升入库 → GBDT 重训,
记忆(可写槽位表)记录策略知识:什么特征在什么 regime 有用、什么方向试过已死。

## 架构

```
                        ┌───────────── 推理面(在线,毫秒级)─────────────┐
                        │                                              │
   贷款申请 ──────────▶ │  结构化特征 ──▶ GBDT ──▶ approve/reject      │
                        │  (白名单字段      ▲        (纯结构化,记忆    │
                        │   + L2衍生特征)   │         不在场)          │
                        └───────────────────┼──────────────────────────┘
                                            │ 现役特征库
   ┌───────────── 学习面(夜间/迭代)─────────┼──────────────────────────┐
   │                                        │                          │
   │   贷后结局回流                          │                          │
   │        ▼                               │                          │
   │   GBDT 指路 ──▶ 出题(G1模板) ──▶ 云端大模型                        │
   │   (疑难坏账      (聚合画像+       │  写特征表达式代码               │
   │    残余信号)      死路清单+       │  AgentBridge 文件桥             │
   │        ▲          regime经验)     ▼                               │
   │   ┌────┴─────────┐          验证器(免疫系统)                      │
   │   │ 策略记忆      │          沙箱/IV/泄漏/相关性                   │
   │   │ (可写槽位表)  │      过 ──▶ 入库 ──▶ GBDT 重训 ──────────────┤
   │   │ ·特征×regime │      灭 ──▶ 死路档案(下轮不再试)              │
   │   │ ·死路档案    │                                                │
   │   │ ·Beta声誉    │                                                │
   │   └──────────────┘                                                │
   └───────────────────────────────────────────────────────────────────┘
```

## 验证结论(2026-07,全部有实验产物)

| 假设 | 结果 | 证据 |
|---|---|---|
| 推理面用记忆(P1 分数混合 / L3 特征 / P2 焊入层) | ✗ 三轮证伪,已放弃 | `eval/artifacts*/`(P1)、`artifacts-lending/`、`artifacts-p2.1/` |
| 免疫系统:验证器拦 LLM 幻觉 | ✓ 6/6 拦截 | LendingClub r01/r02 iteration_log |
| 发现力:闭环发现未知规律 | ✓ CLAB 重新发现保留池 6/10 | `eval/artifacts-clab/` |
| 记忆组件:死路档案 | ✓ 重复提案 0 vs 16.7%,发现提速 +0.38 轮(CI 不含 0) | `eval/artifacts-clab-ab/` |
| 记忆组件:声誉退役 | ✓ 期末库假阳性 −23.8 | 同上 |
| 记忆组件:regime 经验 | ➖ 考场太小不可测(候选池 212 方向被盲枚举扫穿) | 同上 |
| 已知盲区:验证门槛只测 bad 富集,保护性规则结构性失明 | 待修(对称判据) | 机制一未发现的 HLD-02/10 |

关键判读:机制二按「累计假阳性更低」判据记 FAIL,但该口径与「发现更快」结构性此消彼长;
按期末库口径(含退役)两臂判据均过。发现的规则是可解释资产,非预测增益(GBDT 已吃满信号时 AUC 无增量)。

## 组件地图

| 包 | 职责 | 状态 |
|---|---|---|
| `slots/` | 可写槽位表(SQLite+FAISS,三操作写路径,Beta 声誉,双向门,WAL) | 现役 |
| `synth/` | CLAB-lite 合成信贷世界(8因子6概念,20+10规则池,regime Geo(0.1),结局延迟) | 现役 |
| `cloud/` | 云端适配层(脱敏焊死出站、任务包契约、成本账本、AgentBridge 文件桥) | 现役 |
| `verify/` | 验证器×3(特征沙箱+回测+泄漏、案例分析、解释文本) | 现役 |
| `prompts/` | G1 提示词模板(经验+死路注入) | 现役 |
| `scoring/` | GBDT RollingScorer + 特征注册表 + 三段决策 | 现役 |
| `embed/` | Qwen3-Embedding-0.6B(CPU,MRL 256)封装 + canonical 文本化 | 现役 |
| `nightly/` | 夜间巩固(结局回流+compete、Scribe 写槽、特征假设循环) | 现役 |
| `selflearn/` | 自学习闭环(指路+残余信号、策略记忆、replay)+ CLAB 适配 | 现役 |
| `llm/` `lora/` `scribe/` | 本地 LLM 封装/LoRA 巩固+晋升门/LLM 经验归纳 | 备用(P2 资产) |
| `lending/` | LendingClub 预处理(时间红线白名单,episode 切分) | 现役 |
| `eval/` | 验收实验(p1/p2/selflearn/lending/clab/clab-ab)+ 判卷器 | 现役 |
| `memory/` `model/` `training/` | Phase 1 Engram 神经网络引擎 | **历史档案**,见 ARCHITECTURE.md |

## 运行

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

## 数据集分层

- **CLAB-lite**(合成,`synth/`):有标准答案的考场——机制验证(发现力/记忆/假阳性率)
- **LendingClub**(真实,`data/lendingclub/`,kaggle `wordsforthewise/lending-club`):
  无标准答案的战场——免疫系统压力测试;特征集已被业界挖掘 10 年,增量枯竭
- **GiveMeSomeCredit**(openml):消融实验证明连续数值特征上静态记忆无增益,已完成使命
- **终考**:真实业务数据(带贷后闭环),未启动

## 遗留清单(按优先级)

1. 验证器对称判据(保护性规则盲区——机制一 HLD-02/10 的教训)
2. CLAB-full:加类别/文本模态 + 随机因果结构(规则对设计者也盲)——LLM 云端与 regime 经验的真考场
3. 工程债:AgentBridge task_id 跨运行冲突;脱敏误伤 p_value 类小数([BANK_CARD] 误判);slots WAL value_vec 体积(12GB 教训)
4. shadow→active 晋升门、月度体检/退役的完整生命周期(部分组件已验证)

## 参考

- 总设计:`自学习审批系统实现设计.md`(v1.1 + v1.2 实验回写)
- Engram 论文:[arXiv:2601.07372](https://arxiv.org/abs/2601.07372)(条件记忆轴,思想来源)
- 历史架构:`ARCHITECTURE.md`(Phase 1 Engram 引擎,已标记归档)
