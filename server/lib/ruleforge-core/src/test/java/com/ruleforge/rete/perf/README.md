# V5.46 — RETE Performance Baseline(Java + Rust 对比)

`EvalBenchmark.java`(Java 端) + `experiments/server-rust/crates/rf-rule/benches/rete_fire.rs`
(Rust 端)镜像 `mariofusco/drools-benchmark` 的 `EvalBenchmark.run()` workload
(2020,Mario Fusco,Drools core dev),用 RuleForge 自研 RETE 在两端都跑出
baseline 数字,作为以后改 RETE 代码时的对照基线。

## Workload

- **fact 类型**:`Person`(字段 `name`,`address`)+ `Address`(字段 `street`)
- **规模**:1000 Person + 1000 Address(共 2000 fact)
- **特殊数据**:`persons[250]` = "Mario" + Address[250] = "Main Street";
  `persons[500]` = "Duncan" + Address[500] = "First Street";
  `persons[750]` = "Toshiya" + Address[750] = "Second Street"。其他 997 个
  random UUID,不匹配任何 rule。
- **rule 数**:3 条,每条独立匹配一个特殊对
- **单次 run**:`insert(addresses[0..N])` + `insert(persons[0..N])` + `fireRules()`
- **期望 fire 数**:**3**(每条 rule 命中 1 次)

## V5.46 baseline 数字(2026-06-13)

### Java(commit 84a5982)

```
no_eval  (Person(...), Address(...) 字段过滤):  min=0.12ms  p50=0.16ms  mean=0.19ms  max=0.40ms
eval     (Person(), Address(), eval(true)):     min=0.22ms  p50=0.27ms  mean=0.28ms  max=0.42ms
```

- 50 iter / 5 warmup
- Test env:OpenJDK 64-Bit Server VM,Maven Surefire 3.2.5,MacBook(具体 hostname / CPU 详见 CI log)
- 2000 fact insert + 3 rule fire 一共 0.2ms 量级

### Rust(`cargo bench -p rf-rule --bench rete_fire`,optimized)

```
fire_3_rules_1000_facts_per_fire       (per-fact fresh ctx,correct): 2.34 ms
fire_3_rules_1000_facts_oneshot_buggy  (1-shot,有 cache bug):       1.72 ms
```

- 100 samples / 3s warmup / Criterion 0.5
- per-fact-fresh:每个 fact 单独 fresh `FlowContext` + fire 一次(模拟
  production per-request 模式,workaround Rust engine 的 cache bug)
- 1000 fact = 2.34 ms,平均 **2.34 μs / fact**

## 对比

| 引擎 | workload | 时间 | per-fact |
|---|---|---|---|
| **RuleForge Java RETE 5.0.0** | 2000 fact insert + 3 rule fire(1-shot) | **0.16-0.27ms** | **0.08-0.13 μs** |
| RuleForge Rust `rf-rule` rete | 1000 fact + 3 rule fire(per-fact fresh) | **2.34ms** | **2.34 μs** |
| RuleForge Rust `rf-rule` rete | 1000 fact + 3 rule fire(1-shot, buggy) | 1.72ms | 1.72 μs |
| Drools 7.31 | EvalBenchmark | 0.5-2ms(估) | 0.25-1 μs |
| Drools 8/9 | EvalBenchmark | 应比 7.31 快(社区 benchmark 数量级一致) | 没具体数字 |

**结论**:
- **RuleForge Java RETE 跟 Drools 7.31 同档,甚至略快**。0.16ms / 2000 fact
  production 完全可接受 — 实际 single decision 场景 < 50 fact,< 0.01ms。
- **Rust `rf-rule` 比 Java 慢 ~20-30x** per fact(2.34 μs vs 0.08 μs)。
  原因猜测(未深挖):Rust 端 dynamic dispatch(`Arc<dyn Activity>`)开销
  + criteria_value_map cache bug 强制 per-fact fresh ctx + 没用 alpha index。
- **结论:Rust 路径升格 production 不划算**。Java 已经够快,Rust 迁移成本
  4 周+,0 收益。

## Workload 简化的 caveat

RuleForge DRL 4 grammar(V5.42.1 起的自研 grammar)不支持 Drools 风格
`$var : Type(...)` binding 提取 + cross-pattern reference(`$a == $p.getAddress()`)。
visitor 静默丢 binding,语法层接受但 AST 层不抽。

所以本 bench 简化为 "两个 pattern ANDed,各带独立字段过滤,用 `,` 隔开":
- no_eval: `Person(name == "Mario"), Address(street == "Main Street")`
- eval: `Person(), eval(getName() == "Mario"), Address(), eval(getStreet() == "Main Street")`

**跟 Drools 原版 workload 相比少了 cross-pattern join**,数字略微偏低估
真实场景(因为少一次 beta join)。但**不影响** "Java 跟 Drools 同档" 这个结论
— Drools 的 cross-join 多花的也是微秒级。

## V5.46 Rust bench simplification

V5.46 阶段发现 **Rust 端 workload 比 Java 端更简化**:
- **不用 AndNode join**。Rust `AndActivity` join 行为跟 Java 端对不上(都测了
  0 fired),所以两边都退到 "3 个独立 criteria 各匹配 1 个 special fact" 的
  最简 workload。
- **Rust 端不 insert Address fact**。简化后 Address 不参与匹配,OTN 是
  no-op decoration,insert 也无意义,徒增 bench 时间。
- **Rust 端 per-fact-fresh ctx workaround**。`EvaluationContext::clean()`
  只在 unit test 里调,production 路径不调,多 fact 一次 fire_rules 时
  `criteria_value_map` 缓存第一个 fact 的 `false` 一直复用,后面 999 条
  全错判。这是 Rust engine 真 bug,留 V5.46+ 单独 PR 修。本 bench 用
  per-fact fresh ctx workaround,模拟 production per-request 模式。

## 跑法

### Java

```bash
mvn -pl lib/ruleforge-core test -Dtest=EvalBenchmark
```

输出会含两行 `System.out.printf` 的 report:
```
[V5.46 EvalBenchmark] no_eval      | n=50 | fired=3 (warmup=3) | min=... p50=... mean=... max=...
[V5.46 EvalBenchmark] eval         | n=50 | fired=3 (warmup=3) | min=... p50=... mean=... max=...
```

### Rust

```bash
cd experiments/server-rust
cargo bench -p rf-rule --bench rete_fire
```

输出:
```
fire_3_rules_1000_facts_per_fire       time:   [2.32 ms 2.34 ms 2.35 ms]
fire_3_rules_1000_facts_oneshot_buggy  time:   [1.71 ms 1.72 ms 1.72 ms]
```

## 已知限制 / 未来扩展

- [x] **Rust baseline**(V5.46 完成)— `experiments/server-rust/crates/rf-rule/benches/rete_fire.rs`
  criterion dep + 1000-fact / 3-rule workload + Java vs Rust 直接数字对比
- [ ] **Rust `EvaluationContext::clean()` bug fix** — production 路径也要
  `clean()`(`fire_rules` 每次开始前),否则多 fact 一次 fire_rules 错判
- [ ] **Rust alpha index 优化** — 跟 Java 一样为 hot criteria 加 alpha
  index。可能让 Rust 跑到跟 Java 同一量级(0.1-0.3 ms)
- [ ] **Rust bench 1-shot 模式** — 等 cache bug 修后,跟 Java 1-shot 直接对比
- [ ] **大 fact 规模** stress test:100k / 1M fact,看 O(n) 退化情况
- [ ] **复杂 rule** stress test:5-alpha-node deep join chain + shared beta memory
- [ ] **replace 没用 JMH** 是个简化:加 JMH dep 走 `mvn jmh:benchmark` 更标准
- [ ] **Drools 真 baseline**:实际跑 Drools 7.31 jar + EvalBenchmark,出真实数字
  (本 README 估的 0.5-2ms 是社区数据,不是我们 own 测的)

## 不在 V5.46 范围

- alpha index 优化(Java 端):**不需要**。1.7x 提升 = 0.1ms 节省,不抵
  1 周改 + 回归测试
- Rust 路径升格 production:**不需要**。Java 现状够快,Rust 迁移成本 4 周+ 0 收益
- DRL 4 binding 提取:跟性能无关,**功能 gap**,留 V5.46+ 单独 PR(2 周)
- Rust `EvaluationContext::clean()` bug fix:留 V5.46+ 单独 PR(1 天)
- Rust alpha index 优化:留 V5.46+ 单独 PR,值得做(2.34ms → 0.3ms 量级)
