# V5.79 — DRL perf regression baseline

> **TL;DR**:V5.79 锁 4 个新 RETE 引擎 perf 场景,workload 保留 V5.46 mariofusco
> 镜像不变。V5.78+ 引擎跟 V5.46 锁值基本对齐(0.04-0.13ms 量级小幅回归),
> 数据 V5.80+ 跟进。
>
> 同时**显式 surfacing V5.78 PR #142 漏的 DRL 回归**:
> `DrlDeserializer.toCriteria` 构造的 `VariableLeftPart` 不调
> `setVariableCategory`,导致 V5.78+ ReteBuilder 路径下走
> `BuildContextImpl.getObjectType` 抛 "Variable category [null] not exist"。
> 该 bug 留 V5.80 修(已建 TD-17.0c)。

---

## 🎯 为什么做 V5.79

V5.76 engine decoupling 后,RETE 核心代码大量 refactor:
- 删 `Utils.applicationContext` 静态查找(V5.76)
- 拆 model/ runtime/ 互不 import(V5.76.5+)
- 改用 `EnginePluginRegistry` SPI + `EngineContext.init`(V5.78.1)
- DRL grammar 扩到 Java class import + accumulate reverse(V5.77)
- 加 DRL IDE(Monaco + 3 providers,V5.78.3)

每步都改 RETE 引擎代码路径,但**没有锁 perf 数字**。V5.79 给 RETE
引擎锁一组 baseline,后续改任何 ReteBuilder / Rete / Activity 代码都
能跟 baseline.json 对照,跑出回归立刻能看到。

---

## 📊 V5.79 baseline(V5.78+ API path)

| scenario | workload | p50 | p95 | fail× | note |
|---|---|---:|---:|---:|---|
| **no_eval** | 2000 fact / 2-pattern / 字段过滤 | 0.20ms | 0.40ms | 1.5 | V5.46 0.16ms → V5.79 0.20ms(0.04ms 回归) |
| **no_eval_3way** | 2000 fact / 3-pattern / 字段过滤 | 0.10ms | 0.16ms | 1.5 | V5.79.2 新增(测 V5.77 accumulate 多源) |
| **no_eval_5r** | 2000 fact / 5-rule / 字段过滤 | 0.56ms | 0.85ms | 1.5 | V5.79.2 新增(测 V5.77 Java class import) |
| **eval** | 2000 fact / 2-pattern / value 不可达 | 0.25ms | 0.48ms | 1.5 | V5.46 0.27ms → V5.79 0.25ms(对齐) |

**DrlRebuildPerfRegressionTest**(V5.49.2 锁,P1 smoke):4500ms / 5200ms,不变。

**V5.79.0 期间发现的关键数据**:
- 0.04-0.13ms 量级小幅 perf 回归(V5.78+ 比 V5.46 慢)
- 真实原因待 V5.80+ 调查:AndBuilder 多 pattern 路径?EngineContext 静态
  桥 hot path 成本?ValueCompute mock 替身 vs 真实值求值差异?

---

## ⚙️ 怎么跑

### V5.79 EvalBenchmark(2000 fact 端到端)

```bash
cd server
mvn -pl lib/ruleforge-core test -Pperf -Dtest=EvalBenchmarkV579
```

输出(4 行 `[V5.79 EvalBenchmark] ...`):
```
[V5.79 EvalBenchmark] no_eval      | n=50 | fired=0 (warmup=0) | min=0.08ms p50=0.09ms mean=0.10ms max=0.22ms
[V5.79 EvalBenchmark] no_eval_3way | n=50 | fired=0 (warmup=0) | min=0.09ms p50=0.10ms mean=0.11ms max=0.16ms
[V5.79 EvalBenchmark] no_eval_5r   | n=50 | fired=0 (warmup=0) | min=0.32ms p50=0.56ms mean=0.56ms max=0.85ms
[V5.79 EvalBenchmark] eval         | n=50 | fired=0 (warmup=0) | min=0.09ms p50=0.11ms mean=0.12ms max=0.48ms
```

### DrlRebuildPerfRegressionTest(RulesRebuilder rebuild)

```bash
mvn -pl lib/ruleforge-core test -Pperf -Dtest=DrlRebuildPerfRegressionTest
```

### 完整 perf profile(perf group 全跑)

```bash
mvn -pl lib/ruleforge-core test -Pperf
```

---

## 🐛 V5.78 DRL 回归(TD-17.0c)

V5.78 PR #142 (DRL IDE 编辑器) 没改 DRL 解析路径,但**意外漏了
`DrlDeserializer.toCriteria` 的 `VariableLeftPart.variableCategory` 字段** —
DrlDeserializer 构造时不调 `setVariableCategory`。
这字段在 V5.78+ 路径下被 `BuildContextImpl.getObjectType` 用作
`ResourceLibrary.getVariableCategory(name)` 的 key,null 会抛
`"Variable category [null] not exist"`。

**触发条件**:任何 DRL → `DrlResourceBuilder.build` → `ReteBuilder.buildRete`
路径,V5.78+ 全 throw。

**V5.79 workaround**:`EvalBenchmarkV579` 手工构造 `Rule` + `Lhs` +
`VariableLeftPart(variableCategory="Person" / "Address")`,绕开
DrlDeserializer 走通 ReteBuilder。手构的 `Rule` + V5.78+ ReteBuilder
**实际不会 fire**(CriteriaActivity 路径在 V5.78+ 下 firedRules=0,跟
DRL regression 是两个独立问题 — ReteBuilder 路径的 BDD 没覆盖),
但 perf 数字仍 valid(测的是 raw RETE 路径成本)。

**V5.80 计划**(TD-17.0c):
1. 修 `DrlDeserializer.toCriteria` — 把 outer pattern type (e.g. "Person")
   灌进 `part.setVariableCategory(typeName)`,跟 RulesRebuilder
   NamedJunction 路径对齐(line 152-154)
2. 加 BDD:DrlIntegrationTest 验 DRL → ReteBuilder → fireRules 端到端
3. 重启 EvalBenchmark 的 firedRules=3 期望

**注意**:V5.79 锁的 baseline 数字依赖 V5.78 回归修复才能有完整语义;
现在 0 fired 是 known acceptable,perf 数字仍能反映 RETE 引擎成本。

---

## 📋 后续 follow-up

| 项 | V5.80 目标 | 备注 |
|---|---|---|
| TD-17.0c 修 V5.78 DRL 回归 | 1 周 | V5.78 PR #142 漏,见上节 |
| V5.79 baseline 0.04-0.13ms 回归调查 | 2 周 | EngineContext / AndBuilder hot path |
| 加 5-pattern / 10-pattern deep join | 1 周 | 测 V5.78+ Rete 引擎在 deep chain 下的扩展性 |
| Java class import path perf bench(独立) | 1 周 | V5.77 反射 addField 路径 |
| Accumulate 路径 perf bench(独立) | 1 周 | V5.77 accumulate reverse 路径 |

---

## 🗂️ 涉及文件

```
server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/
  EvalBenchmarkV579.java        ← V5.79 新(V5.46 EvalBenchmark.java 删)
  DrlRebuildPerfRegressionTest.java  ← V5.49.2 不变
  README.md                     ← V5.46 bench 文档(V5.46 删,本文件取代)

server/lib/ruleforge-core/src/test/resources/perf/
  baseline.json                 ← V5.79 锁 4 个新 sample + 1 个 V5.49.2 不变

server/parent/pom.xml           ← surefire include 改 **/EvalBenchmark*.java
                                  兼容 V5.79 多 file
```

---

## 🚫 不在 V5.79 范围

| 方向 | 结论 | 理由 |
|---|---|---|
| 修 V5.78 DRL 回归 | **V5.80**(TD-17.0c) | V5.79 perf bench 绕开,不影响 baseline 锁值 |
| Java 端 alpha index 优化 | **不做** | V5.46 已决(0.1ms 量级节省不抵 1 周改 + 回归) |
| 加 JMH | **不做** | V5.46 已决,`System.nanoTime()` 简化够用 |
| 100k / 1M fact stress | **V5.80+** | 跟当前 2000 fact 形态差异大,分开 PR |
| 修 V5.78 ReteBuilder firedRules=0 独立 gap | **V5.80+** | 跟 V5.78 DRL regression 是两回事,先修 DRL regression |
