# V5.79 — DRL perf regression baseline (Java RETE)

> **V5.79 取代 V5.46 EvalBenchmark**:
> V5.46 原版 `EvalBenchmark.java` 已删,改用 `EvalBenchmarkV579.java`
> (V5.78+ API path:EngineContext.init + EnginePluginRegistry SPI +
> AndBuilder + CriteriaBuilder + VariableCategory.ResourceLibrary)。
>
> 完整 V5.79 perf baseline 文档 + V5.78 DRL 回归说明:
> `docs/notes/v579-perf-baseline.md`。

镜像 `mariofusco/drools-benchmark` 的 `EvalBenchmark.run()` workload
(2020, Mario Fusco, Drools core dev),用 RuleForge 自研 RETE 在 Java 端
跑出 V5.79 baseline 数字(Rust 端 V5.46 已停,V5.25 + V5.46.1 决定不升格
production)。

---

## ⚡ TL;DR

> **Java RETE 已够快,Rust 升格 production 0 收益。**
>
> - Java 2000 fact + 3 rule fire:**0.16 ms p50**(单次 decision < 0.01 ms)
> - Rust 同 workload:**2.12 ms / 1000 fact**(per-fact 2.12 μs,比 Java 慢 17-26x)
> - Drools 7.31 同档(0.5-2ms,社区估)
> - V5.46.1 修了 Rust `EvaluationContext` 跨 fact 缓存 bug,1-shot bench 现在量的是真东西
>
> 后续 follow-up(留 V5.47+)只做功能 gap,不做 perf 优化。

---

## 📊 性能数字

### 端到端对比

| 引擎 | workload | 总时间 | per-fact | 备注 |
|---|---|---:|---:|---|
| **RuleForge Java RETE 5.0.0** | 2000 fact + 3 rule fire(1-shot) | **0.16-0.27 ms** | **0.08-0.13 μs** | p50;`no_eval` 0.16ms / `eval` 0.27ms |
| **RuleForge Rust `rf-rule` rete** | 1000 fact + 3 rule fire(1-shot, V5.46.1+) | **2.12 ms** | **2.12 μs** | Criterion 0.5 optimized |
| Drools 7.31 | EvalBenchmark | 0.5-2 ms(估) | 0.25-1 μs | 社区数据,没 own 测 |
| Drools 8/9 | EvalBenchmark | 应比 7.31 快 | 没具体数字 | — |

### Java 详细数字(commit 84a5982)

```
                  workload                              min     p50    mean    max
─────────────────────────────────────────────────────────────────────────────
no_eval   Person(name == X), Address(street == Y)     0.12   0.16    0.19   0.40   ms
eval      Person(), eval(true), Address(), eval(true)  0.22   0.27    0.28   0.42   ms
```

- 50 iter / 5 warmup / OpenJDK 64-Bit Server VM
- 2000 fact insert + 3 rule fire 一共 0.2 ms 量级
- Test env 详见 CI log(MacBook,具体 hostname / CPU 看 log)

### Rust 详细数字(`cargo bench -p rf-rule --bench rete_fire`)

```
benchmark                              time (ms)         per-fact
─────────────────────────────────────────────────────────────────
fire_3_rules_1000_facts_oneshot        [2.10 2.12 2.13]  2.12 μs
```

- 100 samples / 3s warmup / Criterion 0.5
- **V5.46.1 修后**:1-shot(insert 1000 fact 一次 + fire_rules 一次)真 fire 3 条 rule
- 1000 fact 平均 2.12 μs/fact

### 关键差距

| | Java | Rust | 差距 |
|---|---:|---:|---:|
| per-fact | 0.08-0.13 μs | 2.12 μs | **17-26x** |
| 2000 fact | 0.16-0.27 ms | 4.24 ms(估) | ~20x |
| production decision(< 50 fact) | < 0.01 ms | 0.1 ms | ~10-20x |

**Rust 慢的原因**(未深挖):
1. Dynamic dispatch 开销(`Arc<dyn Activity>` vtable lookup)
2. 没用 alpha index(Java 有,1.7x 提速)
3. `HashMap::clean()` per-fact(本来 cache 复用,现在每 fact 重建)

---

## 🧪 Workload 定义

### 数据规模

- **fact 类型**:
  - `Person`(字段 `name`, `address`)
  - `Address`(字段 `street`)
- **规模**:1000 Person + 1000 Address = **2000 fact**
- **特殊数据**(fire 数 = 3):
  - `persons[250]` = "Mario" + `addresses[250]` = "Main Street"
  - `persons[500]` = "Duncan" + `addresses[500]` = "First Street"
  - `persons[750]` = "Toshiya" + `addresses[750]` = "Second Street"
- **其余 997 个 fact**:random UUID,无匹配

### Rule shape(3 条)

| rule | LHS | 命中 fact |
|---|---|---|
| R1 | `Person(name == "Mario"), Address(street == "Main Street")` | persons[250] + addresses[250] |
| R2 | `Person(name == "Duncan"), Address(street == "First Street")` | persons[500] + addresses[500] |
| R3 | `Person(name == "Toshiya"), Address(street == "Second Street")` | persons[750] + addresses[750] |

### 单次 run 协议

```text
insert(addresses[0..N])    // 1000 address fact
insert(persons[0..N])      // 1000 person fact
fireRules()                 // 期望 fire 3 条
```

---

## 🏃 跑法

### Java

```bash
mvn -pl lib/ruleforge-core test -Dtest=EvalBenchmark
```

输出(2 行 `System.out.printf`):
```
[V5.46 EvalBenchmark] no_eval | n=50 | fired=3 (warmup=3) | min=... p50=... mean=... max=...
[V5.46 EvalBenchmark] eval    | n=50 | fired=3 (warmup=3) | min=... p50=... mean=... max=...
```

### Rust

```bash
cd experiments/server-rust
cargo bench -p rf-rule --bench rete_fire
```

输出:
```
fire_3_rules_1000_facts_oneshot
                        time:   [2.10 ms 2.12 ms 2.13 ms]
```

---

## ⚠️ Workload 简化的 caveat

### DRL 4 grammar gap(规则语言简化)

RuleForge DRL 4 grammar(V5.42.1 自研)不支持 Drools 风格:
- `$var : Type(...)` binding 提取(visitor 静默丢)
- cross-pattern reference(`$a == $p.getAddress()`)

所以本 bench 用 "两个 pattern ANDed,各带独立字段过滤" 简化版:
- no_eval: `Person(name == "Mario"), Address(street == "Main Street")`
- eval: `Person(), eval(true), Address(), eval(true)`

**比 Drools 原版少 cross-pattern join**(少一次 beta join)→ 数字略偏低估真实场景。
但**不影响结论**:"Java 跟 Drools 同档" — Drools cross-join 多花也是微秒级。

### Rust 端进一步简化(V5.46 阶段)

| 维度 | Java 端 | Rust 端 | 原因 |
|---|---|---|---|
| AndNode join | ✓ 测过(3 个 special pair 配 3 rule) | ✗ 跳过 | Rust `AndActivity` join 行为跟 Java 端对不上(都测了 0 fired),两边都退到最简 |
| Address fact insert | ✓ 1000 个 | ✗ 跳过 | Address 不参与匹配(Rust 端无 join criteria),徒增 bench 时间 |
| Workload 模式 | 1-shot 1000 + 1000 | 1-shot 1000 | Rust 端无 AndNode 也无 Address criteria |

### V5.46.1 修了什么

| | V5.46 (pre-fix) | V5.46.1 (post-fix) |
|---|---|---|
| **Rust cache bug** | 1-shot fire 0 rules(缓存污染) | 1-shot fire 3 rules(per-fact `clean()`) |
| **Bench workaround** | per-fact-fresh `FlowContext` (2.34ms) | 直接 1-shot (2.12ms) |
| **Workaround 是否需要** | 必需(不 work 测得 0 fired) | 不需要(1-shot 直接 fire 3) |

---

## 📋 已知限制 / 未来扩展

- [x] **Rust baseline**(V5.46 完成)— `benches/rete_fire.rs` criterion dep + 1000-fact / 3-rule + Java vs Rust 对比
- [x] **Rust `EvaluationContext::clean()` bug fix**(V5.46.1 完成)— `ReteRuleEngine::fire_rules` per-fact 内层循环顶部 `eval.clean()`;1-shot bench 从"有 bug 不量"变 2.12ms 正确数字;`cross_impl_test.rs` 加 multi-fact 1-shot BDD 锁行为
- [ ] **Rust alpha index 优化** — 跟 Java 一样为 hot criteria 加 alpha index;可能让 Rust 跑到 0.1-0.3 ms 量级(仍 ~5x 慢于 Java)
- [ ] **大 fact 规模 stress** — 100k / 1M fact,看 O(n) 退化
- [ ] **复杂 rule stress** — 5-alpha-node deep join chain + shared beta memory
- [ ] **加 JMH** — Java 端用 `System.nanoTime()` 是简化,加 JMH dep 走 `mvn jmh:benchmark` 更标准
- [ ] **Drools 真 baseline** — 实际跑 Drools 7.31 jar + EvalBenchmark,出真数字(当前 0.5-2ms 是估的)

---

## 🚫 不在 V5.46 / V5.46.1 范围

| 方向 | 结论 | 理由 |
|---|---|---|
| Java alpha index 优化 | **不做** | 1.7x = 0.1ms 节省,不抵 1 周改 + 回归测试 |
| Rust 升格 production | **不做** | Java 现状 0.1ms 量级,Rust 4 周+ 迁移 0 收益 |
| DRL 4 binding 提取 | **V5.47+ 单独 PR**(2 周) | 跟性能无关,功能 gap |
| Rust alpha index 优化 | **V5.47+ 单独 PR**(1 周) | 2.12ms → 0.3ms 量级,值得做(但仍 ~5x 慢于 Java) |
| Java 端同款 cache bug | **不修** | Java 也有 broken design,但 end-of-cycle `clean()` 掩盖现实问题;scope creep |
| 删 `ruleforge-dsl` module | **V5.47+ 单独 PR** | 跟性能无关,纯 archive cleanup |
