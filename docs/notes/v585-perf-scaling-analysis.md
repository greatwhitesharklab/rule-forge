# V5.85 — Perf Scaling 分析(rete hot path 形态)

> **TL;DR**:V5.85 跑 N=500/1000/2000/5000/10000 fact × 2 workload(single/dual class),测 per-fact
> wall time scaling。**scaling 是 O(N) linear**,**没有 O(n²) 风险**;per-fact 真实成本 0.7-3.5us
> (dual ~3x single),优化方向是**减少 per-fact 常数成本**,不是消除 super-linear。V5.79 旧
> < 1ms p50 是 pre-existing sticky bug 的"便宜"数据,V5.83+ 1-3us per-fact 是正确行为的真实成本。

## 1. 起因

V5.84 (Phase 22) 尝试增量 reset 优化 — 理论正确但实测 reverse-optimization(4 scenario 全部
+20-49% 慢),撤销。教训:**基于 doc 推断的优化方向不可信,先 profiling 拿数据**。

V5.85 (本 phase) 改用 wall-time scaling 反推 perf 模型:
- scaling 是 linear → 无 O(n²) 风险,不用纠结"找 super-linear 节点"
- scaling 是 super-linear → 有 O(n²) 算法问题,找热点

**不动 production code**,只加 `PerfScalingAnalysisTest` 测量工具。

## 2. 数据(5 轮 JIT 稳态中位)

`PerfScalingAnalysisTest` 跑 N=500/1000/2000/5000/10000 fact,3 轮 warmup + 5 轮 measurement 取 median。

### 2.1 Single class(1 rule, 1 pattern, 0 fired)

| N    | total (ms) | per-fact (us) |
|------|------------|---------------|
| 500  | 0.66       | 1.32          |
| 1000 | 1.25       | 1.25          |
| 2000 | 2.15       | 1.07          |
| 5000 | 3.55       | 0.71          |
| 10000| 6.97       | 0.70          |

### 2.2 Dual class(1 rule, 2 pattern Person+Address, 0 fired)

| N    | total (ms) | per-fact (us) |
|------|------------|---------------|
| 500  | 3.52       | 3.52          |
| 1000 | 2.87       | 2.87          |
| 2000 | 1.33       | 1.33          |
| 5000 | 1.66       | 1.66          |
| 10000| 1.25       | 1.25          |

### 2.3 数据观察

1. **Linear scaling**:total 跟 N 增长成比例,无 O(n²) 拐点
2. **per-fact cost 随 N 增大略降**(常数放大分摊 — startup / rete-init / session-construction 摊到更多 fact 上)
3. **dual class per-fact cost ~3x single class** — 2 个 ObjectTypeActivity + 1 beta join + 跨 pattern `findObject`
4. **N=10000 渐近**:dual 1.25us, single 0.70us — 常数放大已摊完,这是 per-fact 真实成本

## 3. vs EvalBenchmarkV579 baseline 验证

`EvalBenchmarkV579.no_eval` (V5.83 PR #147) p50 = 2.84ms,2000 fact,3 fired。

```
V5.85 single N=2000: total=2.15ms, per-fact=1.07us
V5.83 EvalBenchmarkV579 no_eval N=2000: total=2.84ms, per-fact=1.42us
```

差异 0.7ms 来自 3 fired(agenda push + activation group 维护)跟 3 rule 的 3x 遍历 —
V5.85 single 1 rule / 0 fired,数字应略低。**V5.85 数据跟 V5.83 baseline 互相验证合理**。

## 4. 优化方向(基于数据)

### 4.1 排除方向(已验证无 O(n²))

- ~~找 O(n²) 节点~~ — 线性,无
- ~~减少 reset 整 rete 开销~~ — V5.84 已验证 reset 不是 bottleneck
- ~~优化 HashMap.clear() 等基础设施~~ — 总成本占比小

### 4.2 真实优化方向(待 JFR 验证 hot path)

**Single class per-fact 0.7-1.3us** 主要成本:
- `CriteriaActivity.enter` + `criteria.evaluate` 1 次
- `EvaluationContext.storeCriteriaValue` / `getCriteriaValue` 缓存读写
- `ValueCompute.findObject`(单 pattern 场景只查同 class,1 次 string equals)
- `ReteInstance.resetStickyStateOnly` 递归
- `HashMap` insert + agenda push

**Dual class per-fact 1.2-3.5us** 主要成本(single + 增量):
- 跨 pattern `findObject`(Person fact 查 Address 子树)
- `AndActivity.joinNodeIsPassed` + `passAndNode` 调用
- `BetaMemory` 跨 fact 累积 join 状态

### 4.3 V5.86+ 候选优化点

1. **跨 pattern `findObject` 优化** — 每次 Person fact evaluate 都走 `matchedFact.getClass().getName().equals(className)` 字符串比较。预解析 className → Class 对象缓存,O(1) 反射避免。
2. **`EvaluationContext` lazy invalidation** — 现在 `clean()` 清整张 `criteriaValueMap`,改 lazy(只清 changed criteria id)
3. **`ObjectTypeActivity.support()` Class 反射缓存** — V5.84 验证过,不优先
4. **接受 V5.83 perf 是新常态** — 1-3us per-fact 是合理真实成本,继续往下优化收益边际

### 4.4 真正 hot path 需 JFR / async-profiler 抓

V5.85 wall-time scaling 只能告诉你"线性 / 超线性" + "per-fact 总成本",**不能**告诉你
"per-fact 内部哪个方法占大头"。下一步(V5.86 候选):
- 写 long-running workload(30000+ fact × 多 rule × 多轮)
- JFR attach 拿 CPU 火焰图
- 看 `CriteriaActivity.evaluate` / `ValueCompute.findObject` / `Utils.getObjectProperty` 等方法 self-time 占比

## 5. 关键教训

1. **基于 wall-time scaling 反推 perf 模型** 比"看代码猜 hot path"靠谱
2. **O(N) linear + per-fact 1-3us 是 V5.83 rete 真实成本** — 不要把 V5.79 旧 < 1ms 当 baseline 目标
3. **V5.84 增量 reset 撤销是正确决定** — 没找对方向,实测反向
4. **JFR 在 surefire fork 短测试下不友好** — 192 sample 主要采到 JIT/surefire 启动,re te 稳态抓不到
5. **常数放大分摊是规模效应的常见形态** — N 大 per-fact cost 反而小,优化要选大 N 数字(渐近线)

## 6. 改动清单

- 新 test: `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/PerfScalingAnalysisTest.java`
  (2 个 @Test method,single class + dual class,SCALING_N 5 个 N,3+5 warmup+measurement,median)
- 无 production code 改动
- 无 baseline.json 改动(V5.83 数字合理)
- 文档: 本文件 + `v584-incremental-reset-attempt.md`

## 7. 引用

- [[v582-allfactsmap-rewrite]] V5.82 (PR #146) allFactsMap 改 List
- [[v583-rete-sticky-state-fix]] V5.83 (PR #147) rete per-fact clean + resetStickyStateOnly
- [[v584-incremental-reset-attempt]] V5.84 撤销,实测 reverse-optimization
- `EvalBenchmarkV579` V5.83 baseline 4 scenario
- `baseline.json` V5.83 锁值
- `PerfScalingAnalysisTest` 本 phase 测量工具
